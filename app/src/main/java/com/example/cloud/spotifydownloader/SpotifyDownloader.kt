package com.example.cloud.spotifydownloader

import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.URLEncoder
import com.mpatric.mp3agic.Mp3File
import com.mpatric.mp3agic.ID3v24Tag
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileOutputStream

// ─── Data ────────────────────────────────────────────────────────────────────

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int
)

enum class DownloadStatus { IDLE, SEARCHING, DOWNLOADING, TAGGING, DONE, ERROR, SKIPPED }

data class TrackState(
    val track: Track,
    val selected: Boolean = true,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val statusMsg: String = ""
)

private const val SPOTIFY_CLIENT_ID = "ca57e99005f541f98397a9b2515313e8"
private const val SPOTIFY_CLIENT_SECRET = "03900eb819fe47878d872d115179900a"
private const val OUTPUT_DIR = "SpotifyDownloader"

private val http = OkHttpClient()

fun getSpotifyToken(): String {
    val credentials = Base64.encodeToString(
        "$SPOTIFY_CLIENT_ID:$SPOTIFY_CLIENT_SECRET".toByteArray(), Base64.NO_WRAP
    )
    val body = okhttp3.FormBody.Builder().add("grant_type", "client_credentials").build()
    val req = Request.Builder()
        .url("https://accounts.spotify.com/api/token")
        .post(body)
        .header("Authorization", "Basic $credentials")
        .build()
    val res = http.newCall(req).execute()
    return JSONObject(res.body.string()).getString("access_token")
}

fun fetchTracks(url: String, token: String): List<Track> {
    val tracks = mutableListOf<Track>()
    val authHeader = "Bearer $token"

    fun parseTrack(obj: JSONObject): Track {
        val artists = obj.getJSONArray("artists")
        return Track(
            id = obj.getString("id"),
            title = obj.getString("name"),
            artist = artists.getJSONObject(0).getString("name"),
            album = obj.optJSONObject("album")?.optString("name") ?: "",
            trackNumber = obj.optInt("track_number", 0)
        )
    }

    if (url.contains("/track/")) {
        val id = url.substringAfter("/track/").substringBefore("?")
        val req = Request.Builder().url("https://api.spotify.com/v1/tracks/$id")
            .header("Authorization", authHeader).build()
        val obj = JSONObject(http.newCall(req).execute().body.string())
        tracks.add(parseTrack(obj))
    } else {
        val id = url.substringAfterLast("/").substringBefore("?")
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$id/tracks?limit=100"
        while (nextUrl != null) {
            val req = Request.Builder().url(nextUrl)
                .header("Authorization", authHeader).build()
            val body = JSONObject(http.newCall(req).execute().body.string())
            val items: JSONArray = body.getJSONArray("items")
            for (i in 0 until items.length()) {
                val t = items.getJSONObject(i).optJSONObject("track") ?: continue
                tracks.add(parseTrack(t))
            }
            nextUrl =
                if (body.isNull("next")) null else body.optString("next").takeIf { it.isNotEmpty() }
        }
    }
    return tracks
}

fun searchYouTube(track: Track): String? {
    val query = URLEncoder.encode("${track.artist} ${track.title} official audio", "UTF-8")
    val req = Request.Builder()
        .url("https://www.youtube.com/results?search_query=$query")
        .header("User-Agent", "Mozilla/5.0")
        .build()
    val html = http.newCall(req).execute().body!!.string()
    return Regex(""""videoId\":\"([a-zA-Z0-9_-]{11})\"""").find(html)
        ?.groupValues?.get(1)
}

fun getYouTubeAudioUrl(videoId: String): String? {
    val instances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.drgns.space",
        "https://pipedapi.syncpundit.io"
    )

    for (instance in instances) {
        try {
            val req = Request.Builder()
                .url("$instance/streams/$videoId")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = http.newCall(req).execute()
            val bodyStr = response.body!!.string()

            android.util.Log.d("YT_DEBUG", "Instance: $instance, Code: ${response.code}, Body: ${bodyStr.take(500)}")

            if (!response.isSuccessful) continue

            val json = JSONObject(bodyStr)
            val streams = json.optJSONArray("audioStreams") ?: continue

            var bestUrl: String? = null
            var bestBitrate = 0

            for (i in 0 until streams.length()) {
                val s = streams.getJSONObject(i)
                val bitrate = s.optInt("bitrate", 0)
                val mimeType = s.optString("mimeType", "")
                android.util.Log.d("YT_DEBUG", "Stream: mimeType=$mimeType bitrate=$bitrate url=${s.optString("url").take(80)}")
                if (bitrate > bestBitrate) {
                    val url = s.optString("url").takeIf { it.isNotEmpty() }
                    if (url != null) {
                        bestBitrate = bitrate
                        bestUrl = url
                    }
                }
            }
            if (bestUrl != null) return bestUrl
        } catch (e: Exception) {
            android.util.Log.e("YT_DEBUG", "Instance $instance failed: ${e.message}")
            continue
        }
    }
    return null
}

fun decodeCipher(cipher: String): String? {
    // Einfaches URL-Decode für signatureCipher
    if (cipher.isBlank()) return null
    val params = cipher.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        k to java.net.URLDecoder.decode(v, "UTF-8")
    }
    return params["url"]
}

fun downloadAndTag(track: Track, outputDir: File, onStatus: (DownloadStatus, String) -> Unit) {
    onStatus(DownloadStatus.SEARCHING, "Suche auf YouTube...")
    val videoId = searchYouTube(track)
    if (videoId == null) {
        onStatus(DownloadStatus.SKIPPED, "Nicht gefunden")
        return
    }

    onStatus(DownloadStatus.DOWNLOADING, "Hole Audio-Stream...")
    val audioUrl = getYouTubeAudioUrl(videoId)
    if (audioUrl == null) {
        onStatus(DownloadStatus.ERROR, "Kein Audio-Stream gefunden")
        return
    }

    // Stream direkt als .m4a speichern (kein ffmpeg nötig)
    val outFile = File(outputDir, "${track.artist} - ${track.title}.m4a")
    try {
        val req = Request.Builder().url(audioUrl)
            .header("User-Agent", "com.google.android.youtube/17.31.35")
            .build()
        val bytes = http.newCall(req).execute().body!!.bytes()
        FileOutputStream(outFile).use { it.write(bytes) }
    } catch (e: Exception) {
        onStatus(DownloadStatus.ERROR, "Download fehlgeschlagen: ${e.message}")
        return
    }

    onStatus(DownloadStatus.DONE, "✅ Fertig (${outFile.name})")
}

// ─── Zentrale Auslösefunktion ─────────────────────────────────────────────────

suspend fun startDownload(
    spotifyUrl: String,
    selectedIndices: Set<Int>,          // leeres Set = alle
    onTracksLoaded: (List<Track>) -> Unit,
    onTrackStatus: (Int, DownloadStatus, String) -> Unit
) = withContext(Dispatchers.IO) {
    val token = getSpotifyToken()
    val tracks = fetchTracks(spotifyUrl, token)
    withContext(Dispatchers.Main) { onTracksLoaded(tracks) }

    val indices = selectedIndices.ifEmpty { tracks.indices.toSet() }
    val outputDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        OUTPUT_DIR
    ).also { it.mkdirs() }

    indices.forEach { i ->
        downloadAndTag(tracks[i], outputDir) { status, msg ->
            runBlocking(Dispatchers.Main) { onTrackStatus(i, status, msg) }
        }
        delay(1500)
    }
}

@Composable
fun SpotifyDownloaderApp() {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var trackStates by remember { mutableStateOf<List<TrackState>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }

    MaterialTheme {
        Column(Modifier
            .fillMaxSize()
            .padding(16.dp)) {

            Text("Spotify Downloader", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Spotify URL (Playlist oder Track)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            // Alle auswählen / abwählen
            if (trackStates.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val allSelected = trackStates.all { it.selected }
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { v ->
                            trackStates = trackStates.map { it.copy(selected = v) }
                        }
                    )
                    Text("Alle auswählen")
                }
            }

            Button(
                onClick = {
                    if (url.isBlank() || isRunning) return@Button
                    isRunning = true
                    phase = "Lade Playlist..."
                    scope.launch {
                        startDownload(
                            spotifyUrl = url,
                            selectedIndices = trackStates
                                .mapIndexedNotNull { i, t -> if (t.selected) i else null }
                                .toSet(),
                            onTracksLoaded = { tracks ->
                                trackStates = tracks.map { TrackState(it) }
                                phase = "Starte Downloads..."
                            },
                            onTrackStatus = { i, status, msg ->
                                trackStates = trackStates.toMutableList().also {
                                    it[i] = it[i].copy(status = status, statusMsg = msg)
                                }
                                if (i == trackStates.lastIndex && status in listOf(
                                        DownloadStatus.DONE,
                                        DownloadStatus.ERROR,
                                        DownloadStatus.SKIPPED
                                    )
                                ) {
                                    isRunning = false
                                    phase = "✨ Fertig!"
                                }
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
            ) {
                Text(if (isRunning) "Läuft..." else "Download starten")
            }

            if (phase.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(phase, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(trackStates) { i, ts ->
                    TrackRow(ts, onToggle = {
                        trackStates = trackStates.toMutableList().also {
                            it[i] = it[i].copy(selected = !it[i].selected)
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun TrackRow(ts: TrackState, onToggle: () -> Unit) {
    val icon = when (ts.status) {
        DownloadStatus.IDLE -> "⬜"
        DownloadStatus.SEARCHING -> "🔍"
        DownloadStatus.DOWNLOADING -> "⬇️"
        DownloadStatus.TAGGING -> "🏷️"
        DownloadStatus.DONE -> "✅"
        DownloadStatus.ERROR -> "❌"
        DownloadStatus.SKIPPED -> "⚠️"
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = ts.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                "${ts.track.artist} – ${ts.track.title}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (ts.statusMsg.isNotEmpty())
                Text("$icon ${ts.statusMsg}", style = MaterialTheme.typography.bodySmall)
        }
    }
}