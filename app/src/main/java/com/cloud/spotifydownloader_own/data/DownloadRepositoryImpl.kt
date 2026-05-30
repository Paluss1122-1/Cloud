package com.cloud.spotifydownloader_own.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.cloud.core.objects.Config.RAPID_API_KEY
import com.cloud.spotifydownloader_own.domain.DownloadRepository
import com.cloud.spotifydownloader_own.domain.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder

class DownloadRepositoryImpl(
    private val httpClient: HttpClient,
    private val context: Context
) : DownloadRepository {

    override fun downloadTrack(spotifyUrl: String): Flow<DownloadState> = channelFlow {
        send(DownloadState.Searching)

        try {
            val trackId = spotifyUrl.split("/track/").getOrNull(1)?.split("?")?.getOrNull(0)
            if (trackId == null) {
                send(DownloadState.Error("Ungültige Spotify URL"))
                return@channelFlow
            }
            val cleanUrl = "https://open.spotify.com/track/$trackId"
            val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
            val apiUrl =
                "https://spotify-downloader9.p.rapidapi.com/downloadSong?songId=$encodedUrl"

            send(DownloadState.Downloading(5))
            val response: HttpResponse = httpClient.get(apiUrl) {
                timeout {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 60_000
                    socketTimeoutMillis = 120_000
                }

                headers {
                    append("x-rapidapi-key", RAPID_API_KEY)
                    append("x-rapidapi-host", "spotify-downloader9.p.rapidapi.com")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                send(DownloadState.Error("API Fehler: ${response.status}"))
                return@channelFlow
            }

            val jsonBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val success = jsonBody["success"]?.jsonPrimitive?.content?.toBoolean() ?: false

            if (!success) {
                send(DownloadState.Error("Track konnte nicht gefunden werden"))
                return@channelFlow
            }

            val dataObject = jsonBody["data"]?.jsonObject
            val downloadUrl = dataObject?.get("downloadLink")?.jsonPrimitive?.content
            val trackTitle = dataObject?.get("title")?.jsonPrimitive?.content ?: "Track"
            val artist = dataObject?.get("artist")?.jsonPrimitive?.content ?: "Unknown"

            if (downloadUrl == null) {
                send(DownloadState.Error("Kein Download-Link erhalten"))
                return@channelFlow
            }

            val album = dataObject["album"]?.jsonPrimitive?.content ?: ""
            val coverUrl = dataObject["cover"]?.jsonPrimitive?.content

            val safeArtist = artist.replace(" ", "_")
            val safeTitle = trackTitle.replace(" ", "_")
            val fileName = "${safeArtist}_${safeTitle}.mp3"

            if (fileExistsInMediaStore(fileName)) {
                send(DownloadState.Error("Datei existiert bereits lokal"))
                return@channelFlow
            }

            send(DownloadState.Downloading(20))
            val audioResponse: HttpResponse = httpClient.get(downloadUrl)
            val channel: ByteReadChannel = audioResponse.bodyAsChannel()
            val contentLength = audioResponse.contentLength() ?: 0L

            val coverBytes: ByteArray? = coverUrl?.let {
                try { URL(it).readBytes() } catch (_: Exception) { null }
            }

            val fileUri = saveFileFromChannel(fileName, channel, contentLength, "audio/mpeg", trackId, trackTitle, artist, album, coverBytes) { progress ->
                send(DownloadState.Downloading(20 + (progress * 0.7).toInt()))
            }

            send(DownloadState.Converting)
            delay(500)
            send(DownloadState.Success(trackId, trackTitle, artist, album, fileName, fileUri))
        } catch (e: Exception) {
            send(DownloadState.Error("Download fehlgeschlagen: ${e.localizedMessage}"))
        }
    }
        .flowOn(Dispatchers.IO)

    private suspend fun saveFileFromChannel(
        fileName: String,
        channel: ByteReadChannel,
        contentLength: Long,
        mimeType: String,
        trackId: String,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?,
        onProgress: suspend (Int) -> Unit
    ): android.net.Uri {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cloud")

            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Konnte MediaStore Eintrag nicht erstellen")

        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    val id3Tag = buildId3Tag(trackId, title, artist, album, coverBytes)
                    outputStream.write(id3Tag)

                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var id3Skipped = false
                    var id3SkipBytes = 0

                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break

                        var writeOffset = 0
                        var writeLength = read

                        if (!id3Skipped) {
                            if (id3SkipBytes == 0 && read >= 10 &&
                                buffer[0] == 0x49.toByte() &&
                                buffer[1] == 0x44.toByte() &&
                                buffer[2] == 0x33.toByte()
                            ) {
                                val tagSize = ((buffer[6].toInt() and 0x7F) shl 21) or
                                    ((buffer[7].toInt() and 0x7F) shl 14) or
                                    ((buffer[8].toInt() and 0x7F) shl 7) or
                                    (buffer[9].toInt() and 0x7F)
                                id3SkipBytes = 10 + tagSize
                            }
                            if (id3SkipBytes > 0) {
                                val skip = minOf(id3SkipBytes, read)
                                id3SkipBytes -= skip
                                writeOffset = skip
                                writeLength = read - skip
                                if (id3SkipBytes == 0) id3Skipped = true
                            } else {
                                id3Skipped = true
                            }
                        }

                        if (writeLength > 0) outputStream.write(buffer, writeOffset, writeLength)
                        totalBytesRead += read
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                    outputStream.flush()
                }
            }

            val updateValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
            }
            resolver.update(uri, updateValues, null, null)

        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun buildId3Tag(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray {
        val frames = ByteArrayOutputStream()

        fun writeFrameHeader(id: String, size: Int) {
            frames.write(id.toByteArray(Charsets.ISO_8859_1))
            frames.write(byteArrayOf(
                ((size shr 24) and 0xFF).toByte(),
                ((size shr 16) and 0xFF).toByte(),
                ((size shr 8) and 0xFF).toByte(),
                (size and 0xFF).toByte(),
                0x00, 0x00
            ))
        }
        fun encodeTextFrame(id: String, text: String) {
            val textBytes = byteArrayOf(0x03) + text.toByteArray(Charsets.UTF_8)
            writeFrameHeader(id, textBytes.size)
            frames.write(textBytes)
        }

        encodeTextFrame("TIT2", title)
        encodeTextFrame("TPE1", artist)
        encodeTextFrame("TALB", album)

        val ufidOwner = "http://www.spotify.com".toByteArray(Charsets.ISO_8859_1)
        val ufidId = "spotify:track:$trackId".toByteArray(Charsets.ISO_8859_1)
        val ufidContent = ufidOwner + byteArrayOf(0x00) + ufidId
        writeFrameHeader("UFID", ufidContent.size)
        frames.write(ufidContent)

        if (coverBytes != null) {
            val mimeBytes = "image/jpeg".toByteArray(Charsets.ISO_8859_1)

            val apicHeader = ByteArrayOutputStream().apply {
                write(0x00)
                write(mimeBytes)
                write(0x00)
                write(0x03)
                write(0x00)
            }

            val apicContent = apicHeader.toByteArray() + coverBytes
            writeFrameHeader("APIC", apicContent.size)
            frames.write(apicContent)
        }

        val framesBytes = frames.toByteArray()
        val tagSize = framesBytes.size

        fun toSyncsafe(n: Int) = byteArrayOf(
            ((n shr 21) and 0x7F).toByte(),
            ((n shr 14) and 0x7F).toByte(),
            ((n shr 7) and 0x7F).toByte(),
            (n and 0x7F).toByte()
        )

        val header = byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00) + toSyncsafe(tagSize)
        return header + framesBytes
    }

    private fun HttpResponse.contentLength(): Long? = headers["Content-Length"]?.toLongOrNull()

    private suspend fun fileExistsInMediaStore(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            val exists = (cursor?.count ?: 0) > 0
            cursor?.close()
            exists
        } catch (_: Exception) {
            false
        }
    }
}