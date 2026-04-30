package com.cloud.tabs

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloud.core.objects.Config
import com.cloud.quiethoursnotificationhelper.laptopIp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream


data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val durationMs: Int = 0
)

enum class DownloadStatus { IDLE, SEARCHING, DOWNLOADING, TAGGING, DONE, ERROR, SKIPPED }

data class TrackState(
    val track: Track,
    val selected: Boolean = true,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val statusMsg: String = ""
)

private const val OUTPUT_DIR = "SpotifyDownloader"

private val http = OkHttpClient.Builder()
    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
    .build()
private suspend fun fetchTracksFromServer(spotifyUrl: String): List<Track> = withContext(Dispatchers.IO) {
    val body = JSONObject().apply { put("url", spotifyUrl) }
        .toString().toRequestBody("application/json".toMediaType())
    val req = Request.Builder()
        .url("http://$laptopIp:${Config.API_SERVER}/spotify/tracks")
        .post(body).build()
    val arr = JSONArray(http.newCall(req).execute().body.string())
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Track(
            id = i.toString(),
            title = o.getString("title"),
            artist = o.getString("artist"),
            album = o.optString("album", ""),
            trackNumber = o.optInt("track_number", 0),
            durationMs = o.optInt("duration_ms", 0)
        )
    }
}

private suspend fun fetchStreamUrl(track: Track): String? = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
        put("artist", track.artist)
        put("title", track.title)
        put("duration_ms", track.durationMs)
    }.toString().toRequestBody("application/json".toMediaType())
    val req = Request.Builder()
        .url("http://$laptopIp:${Config.API_SERVER}/spotify/stream")
        .post(body).build()
    JSONObject(http.newCall(req).execute().body.string())
        .optString("url").takeIf { it.isNotEmpty() }
}

private fun downloadFromUrl(url: String, track: Track, outputDir: File): Boolean = try {
    val bytes = http.newCall(
        Request.Builder().url(url)
            .header("User-Agent", "com.google.android.youtube/17.31.35")
            .build()
    ).execute().body.bytes()
    FileOutputStream(File(outputDir, "${track.artist} - ${track.title}.m4a")).use { it.write(bytes) }
    true
} catch (_: Exception) { false }

@Composable
fun SpotifyDownloaderApp() {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var trackStates by remember { mutableStateOf<List<TrackState>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Spotify Downloader", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Spotify URL (Playlist oder Track)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (trackStates.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val allSelected = trackStates.all { it.selected }
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { v -> trackStates = trackStates.map { it.copy(selected = v) } }
                )
                Text("Alle auswählen (${trackStates.count { it.selected }}/${trackStates.size})")
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (url.isBlank() || isRunning) return@Button
                    isRunning = true
                    phase = "Lade Tracks vom Server..."
                    scope.launch {
                        try {
                            val tracks = fetchTracksFromServer(url)
                            trackStates = tracks.map { TrackState(it) }
                            phase = "${tracks.size} Tracks geladen"
                        } catch (e: Exception) {
                            phase = "❌ ${e.message}"
                        }
                        isRunning = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isRunning
            ) { Text("Laden") }

            Button(
                onClick = {
                    if (isRunning || trackStates.none { it.selected }) return@Button
                    isRunning = true
                    scope.launch {
                        val outputDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            OUTPUT_DIR
                        ).also { it.mkdirs() }

                        val selected = trackStates.indices.filter { trackStates[it].selected }
                        selected.forEachIndexed { n, i ->
                            val ts = trackStates[i]
                            phase = "[${n + 1}/${selected.size}] ${ts.track.artist} – ${ts.track.title}"

                            trackStates = trackStates.toMutableList().also {
                                it[i] = it[i].copy(status = DownloadStatus.SEARCHING, statusMsg = "Suche YouTube...")
                            }
                            val streamUrl = fetchStreamUrl(ts.track)
                            if (streamUrl == null) {
                                trackStates = trackStates.toMutableList().also {
                                    it[i] = it[i].copy(status = DownloadStatus.SKIPPED, statusMsg = "Nicht gefunden")
                                }
                                return@forEachIndexed
                            }

                            trackStates = trackStates.toMutableList().also {
                                it[i] = it[i].copy(status = DownloadStatus.DOWNLOADING, statusMsg = "Lade herunter...")
                            }
                            val ok = withContext(Dispatchers.IO) { downloadFromUrl(streamUrl, ts.track, outputDir) }
                            trackStates = trackStates.toMutableList().also {
                                it[i] = it[i].copy(
                                    status = if (ok) DownloadStatus.DONE else DownloadStatus.ERROR,
                                    statusMsg = if (ok) "Fertig" else "Fehlgeschlagen"
                                )
                            }
                            delay(300)
                        }
                        phase = "✨ Fertig!"
                        isRunning = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isRunning && trackStates.any { it.selected }
            ) { Text(if (isRunning) "Läuft..." else "Download") }
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