package com.cloud.spotidownloader.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.cloud.Config.RAPID_API_KEY
import com.cloud.spotidownloader.domain.DownloadRepository
import com.cloud.spotidownloader.domain.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
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
import java.net.URLEncoder
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context
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
            val artist = dataObject?.get("artists")?.jsonPrimitive?.content ?: "Unknown"

            if (downloadUrl == null) {
                send(DownloadState.Error("Kein Download-Link erhalten"))
                return@channelFlow
            }

            send(DownloadState.Downloading(20))
            val audioResponse: HttpResponse = httpClient.get(downloadUrl)
            val channel: ByteReadChannel = audioResponse.bodyAsChannel()
            val contentLength = audioResponse.contentLength() ?: 0L

            val ext = "m4a"
            val mimeType = "audio/mp4"

            val fileName = "${artist.replace(" ", "_")}_-_${trackTitle.replace(" ", "_")}.$ext"

            saveFileFromChannel(fileName, channel, contentLength, mimeType) { progress ->
                send(DownloadState.Downloading(20 + (progress * 0.7).toInt()))
            }

            send(DownloadState.Converting)
            delay(500)
            send(DownloadState.Success)
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
        onProgress: suspend (Int) -> Unit
    ) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cloud")
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Konnte MediaStore Eintrag nicht erstellen")

        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { outputStream ->
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
                                buffer[0] == 0x49.toByte() && // 'I'
                                buffer[1] == 0x44.toByte() && // 'D'
                                buffer[2] == 0x33.toByte()    // '3'
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
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun HttpResponse.contentLength(): Long? = headers["Content-Length"]?.toLongOrNull()
}