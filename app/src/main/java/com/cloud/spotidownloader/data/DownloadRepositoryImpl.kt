package com.cloud.spotidownloader.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import kotlinx.coroutines.flow.flow
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
                    append("x-rapidapi-key", "6947ddb4f8msheeef82984a5c52ap164eb8jsnea0900d669b1")
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

            val fileName = "${artist.replace(" ", "_")}_-_${trackTitle.replace(" ", "_")}.mp3"

            saveFileFromChannel(fileName, channel, contentLength) { progress ->
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
        onProgress: suspend (Int) -> Unit
    ) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cloud")
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Konnte MediaStore Eintrag nicht erstellen")

        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        outputStream.write(buffer, 0, read)
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
