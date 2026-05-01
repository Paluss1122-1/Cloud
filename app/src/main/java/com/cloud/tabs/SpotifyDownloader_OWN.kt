package com.cloud.tabs

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloud.core.objects.Config
import com.cloud.core.objects.toast
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

data class UrlEntry(
    val id: Long = System.currentTimeMillis(),
    val url: String,
    val tracks: List<TrackState> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val expanded: Boolean = true
) {
    val label: String
        get() = when {
            url.contains("/playlist/") -> "Playlist"
            url.contains("/album/") -> "Album"
            url.contains("/track/") -> "Track"
            else -> "Spotify"
        }
}

object SpotifyQueue {
    val entries: SnapshotStateList<UrlEntry> = mutableStateListOf()

    private val SPOTIFY_REGEX = Regex(
        "https://open\\.spotify\\.com(/intl-[a-z]{2})?/(track|playlist|album)/[A-Za-z0-9]+(\\?[^ ]*)?"
    )

    fun push(rawText: String) {
        SPOTIFY_REGEX.findAll(rawText).forEach { match ->
            val url = match.value.trim()
            if (entries.none { it.url == url }) {
                entries.add(UrlEntry(url = url))
            }
        }
    }

    fun remove(id: Long) {
        entries.removeAll { it.id == id }
    }

    fun updateEntry(id: Long, block: (UrlEntry) -> UrlEntry) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx != -1) entries[idx] = block(entries[idx])
    }

    fun updateTrack(entryId: Long, trackIdx: Int, block: (TrackState) -> TrackState) {
        updateEntry(entryId) { entry ->
            entry.copy(
                tracks = entry.tracks.toMutableList().also { it[trackIdx] = block(it[trackIdx]) })
        }
    }
}

private const val OUTPUT_DIR = "Cloud"

private val http = OkHttpClient.Builder()
    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
    .build()

private suspend fun fetchTracksFromServer(spotifyUrl: String): List<Track> =
    withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("url", spotifyUrl) }
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("http://$laptopIp:${Config.SPOTIFY_PORT}/spotify/tracks")
            .post(body).build()
        val arr = JSONArray(http.newCall(req).execute().body.string())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Track(
                id = "${spotifyUrl.hashCode()}_$i",
                title = o.getString("title"),
                artist = o.getString("artist"),
                album = o.optString("album", ""),
                trackNumber = o.optInt("track_number", 0),
                durationMs = o.optInt("duration_ms", 0)
            )
        }
    }

private suspend fun fetchStreamUrl(track: Track): String? =
    withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("artist", track.artist)
            put("title", track.title)
            put("duration_ms", track.durationMs)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("http://$laptopIp:${Config.SPOTIFY_PORT}/spotify/stream")
            .post(body).build()
        JSONObject(http.newCall(req).execute().body.string())
            .optString("url").takeIf { it.isNotEmpty() }
    }

suspend fun testDownload(url: String, context: Context) = withContext(Dispatchers.IO) {
    try {
        // clen direkt aus URL parsen
        val clen = Regex("clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        println("🎯 Erwartete Bytes laut clen: $clen")

        val req = Request.Builder()
            .url(url)
            .build()

        http.newCall(req).execute().use { response ->
            println("📡 Status: ${response.code}")
            println("📡 Content-Type: ${response.header("Content-Type")}")
            println("📡 Content-Length Header: ${response.header("Content-Length")}")

            val body = response.body ?: run { println("❌ Body null"); return@withContext }
            val file = File(context.getExternalFilesDir(null), "test_download.webm")
            if (file.exists()) file.delete()

            var downloaded = 0L
            val limit = clen ?: Long.MAX_VALUE

            body.byteStream().use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(32768)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        println("⬇️ $downloaded / $limit bytes")
                        if (downloaded >= limit) {
                            println("✅ clen erreicht, breche ab")
                            break
                        }
                    }
                    out.flush()
                }
            }

            println("✅ Datei: ${file.absolutePath}")
            println("✅ Größe: ${file.length()} bytes")
        }
    } catch (e: Exception) {
        println("❌ ${e.javaClass.simpleName}: ${e.message}")
    }
}

private fun downloadFromUrl(url: String, track: Track, outputDir: File): Boolean {
    return try {
        val clen = Regex("clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        val ext = if (url.contains("mime=audio%2Fwebm")) "webm" else "m4a"

        val safe = { s: String -> s.replace(Regex("[/\\\\:*?\"<>|]"), "_") }
        val fileName = "${safe(track.artist)} - ${safe(track.title)}.$ext"

        val file = File(outputDir, fileName)

        if (file.exists()) file.delete()

        Log.d("Download", "Start: ${track.artist} - ${track.title}")

        val startTime = System.currentTimeMillis()

        val request = Request.Builder().url(url).build()

        var downloaded = 0L

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("Download", "HTTP Fehler: ${response.code}")
                return false
            }

            val body = response.body ?: return false
            val limit = clen ?: Long.MAX_VALUE

            val buf = ByteArray(128 * 1024)

            body.byteStream().use { input ->
                FileOutputStream(file).buffered(128 * 1024).use { out ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (downloaded >= limit) break
                    }
                }
            }
        }

        val totalSec = (System.currentTimeMillis() - startTime) / 1000.0
        val avgKbs = if (totalSec > 0) downloaded / 1024.0 / totalSec else 0.0

        Log.d(
            "Download",
            "Fertig: ${downloaded / 1024 / 1024.0} MB in ${totalSec}s (${avgKbs.toInt()} KB/s) → $fileName"
        )

        downloaded > 10_000
    } catch (e: Exception) {
        Log.e("Download", "Exception", e)
        false
    }
}

@Composable
fun SpotifyDownloaderApp() {
    val scope = rememberCoroutineScope()
    val entries = SpotifyQueue.entries
    val context = LocalContext.current

    var manualInput by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var globalPhase by remember { mutableStateOf("") }

    LaunchedEffect(entries.size) {
        entries.filter { it.tracks.isEmpty() && !it.loading && it.error == null }.forEach { entry ->
            scope.launch {
                SpotifyQueue.updateEntry(entry.id) { it.copy(loading = true, error = null) }
                try {
                    val tracks = fetchTracksFromServer(entry.url)
                    SpotifyQueue.updateEntry(entry.id) {
                        it.copy(loading = false, tracks = tracks.map { t -> TrackState(t) })
                    }
                } catch (e: Exception) {
                    SpotifyQueue.updateEntry(entry.id) {
                        it.copy(loading = false, error = e.message ?: "Fehler")
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Spotify Downloader", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = { Text("Spotify URL hinzufügen") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(
                onClick = {
                    SpotifyQueue.push(manualInput.trim())
                    manualInput = ""
                },
                enabled = manualInput.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
            }
        }

        val totalSelected = entries.sumOf { e -> e.tracks.count { it.selected } }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (isDownloading) return@Button
                isDownloading = true
                globalPhase = ""
                scope.launch {
                    val outputDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        OUTPUT_DIR
                    ).also { it.mkdirs() }

                    val work = entries.flatMap { entry ->
                        entry.tracks.indices
                            .filter { entry.tracks[it].selected }
                            .map { entry.id to it }
                    }

                    work.forEachIndexed { n, (entryId, trackIdx) ->
                        val ts = SpotifyQueue.entries
                            .firstOrNull { it.id == entryId }
                            ?.tracks?.getOrNull(trackIdx) ?: return@forEachIndexed

                        globalPhase =
                            "[${n + 1}/${work.size}] ${ts.track.artist} – ${ts.track.title}"

                        SpotifyQueue.updateTrack(entryId, trackIdx) {
                            it.copy(status = DownloadStatus.SEARCHING, statusMsg = "Suche YouTube…")
                        }

                        val streamUrl = fetchStreamUrl(ts.track)
                        toast(context, streamUrl ?: "")
                        println(streamUrl)
                        if (streamUrl == null) {
                            SpotifyQueue.updateTrack(entryId, trackIdx) {
                                it.copy(
                                    status = DownloadStatus.SKIPPED,
                                    statusMsg = "Nicht gefunden"
                                )
                            }
                            return@forEachIndexed
                        }

                        SpotifyQueue.updateTrack(entryId, trackIdx) {
                            it.copy(
                                status = DownloadStatus.DOWNLOADING,
                                statusMsg = "Lade herunter…"
                            )
                        }

                        val ok = withContext(Dispatchers.IO) {
                            downloadFromUrl(streamUrl, ts.track, outputDir)
                        }

                        SpotifyQueue.updateTrack(entryId, trackIdx) {
                            it.copy(
                                status = if (ok) DownloadStatus.DONE else DownloadStatus.ERROR,
                                statusMsg = if (ok) "Fertig" else "Fehlgeschlagen"
                            )
                        }
                        delay(200)
                    }

                    globalPhase = "✅ Fertig! ${work.size} Tracks verarbeitet"
                    isDownloading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDownloading && totalSelected > 0
        ) {
            Text(
                if (isDownloading) "Läuft…"
                else if (totalSelected > 0) "Alle $totalSelected Tracks herunterladen"
                else "Keine Tracks ausgewählt"
            )
        }

        if (globalPhase.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(globalPhase, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Text(
                "Füge Spotify-Links oben ein. Du kannst auch einen Text mit mehreren Links gleichzeitig einfügen!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(entries, key = { _, e -> e.id }) { _, entry ->
                    UrlEntryCard(
                        entry = entry,
                        isDownloading = isDownloading,
                        onRemove = { SpotifyQueue.remove(entry.id) },
                        onToggleExpand = {
                            SpotifyQueue.updateEntry(entry.id) { it.copy(expanded = !it.expanded) }
                        },
                        onToggleTrack = { idx ->
                            SpotifyQueue.updateTrack(
                                entry.id,
                                idx
                            ) { it.copy(selected = !it.selected) }
                        },
                        onToggleAll = { allOn ->
                            SpotifyQueue.updateEntry(entry.id) { e ->
                                e.copy(tracks = e.tracks.map { it.copy(selected = allOn) })
                            }
                        },
                        onRetry = {
                            scope.launch {
                                SpotifyQueue.updateEntry(entry.id) {
                                    it.copy(loading = true, error = null, tracks = emptyList())
                                }
                                try {
                                    val tracks = fetchTracksFromServer(entry.url)
                                    SpotifyQueue.updateEntry(entry.id) {
                                        it.copy(
                                            loading = false,
                                            tracks = tracks.map { t -> TrackState(t) })
                                    }
                                } catch (e: Exception) {
                                    SpotifyQueue.updateEntry(entry.id) {
                                        it.copy(loading = false, error = e.message ?: "Fehler")
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UrlEntryCard(
    entry: UrlEntry,
    isDownloading: Boolean,
    onRemove: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleTrack: (Int) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        entry.url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.tracks.isNotEmpty()) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (entry.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    enabled = !isDownloading,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Entfernen")
                }
            }

            when {
                entry.loading -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "Lade Tracks…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                entry.error != null -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "❌ ${entry.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) { Text("Erneut versuchen") }
                }

                entry.tracks.isNotEmpty() && entry.expanded -> {
                    Spacer(Modifier.height(4.dp))
                    val allSelected = entry.tracks.all { it.selected }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { onToggleAll(it) }
                        )
                        Text(
                            "Alle (${entry.tracks.count { it.selected }}/${entry.tracks.size})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    entry.tracks.forEachIndexed { idx, ts ->
                        TrackRow(ts = ts, onToggle = { onToggleTrack(idx) })
                    }
                }

                entry.tracks.isNotEmpty() && !entry.expanded -> {
                    Text(
                        "${entry.tracks.size} Tracks • ${entry.tracks.count { it.selected }} ausgewählt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackRow(ts: TrackState, onToggle: () -> Unit) {
    val icon = when (ts.status) {
        DownloadStatus.IDLE -> ""
        DownloadStatus.SEARCHING -> "🔍"
        DownloadStatus.DOWNLOADING -> "⬇️"
        DownloadStatus.TAGGING -> "🏷️"
        DownloadStatus.DONE -> "✅"
        DownloadStatus.ERROR -> "❌"
        DownloadStatus.SKIPPED -> "⚠️"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = ts.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                "${ts.track.artist} – ${ts.track.title}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (ts.statusMsg.isNotEmpty())
                Text(
                    "$icon ${ts.statusMsg}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
    }
}