package com.cloud.tabs

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.cloud.core.objects.Config.PODCASTINDEX_API_KEY
import com.cloud.core.objects.Config.PODCASTINDEX_API_SECRET
import com.cloud.core.objects.Config.PODCAST_DOWNLOAD_PROXY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.net.URL
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

private data class PodcastFeed(
    val title: String,
    val author: String,
    val image: String,
    val feedUrl: String,
)

private data class Episode(
    val title: String,
    val audioUrl: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastTab() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var feeds by remember { mutableStateOf<List<PodcastFeed>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedFeedUrl by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var loadingEpisodes by remember { mutableStateOf<String?>(null) }

    suspend fun search(q: String) {
        if (q.isBlank()) return
        isSearching = true
        error = null
        feeds = emptyList()
        try {
            val now = System.currentTimeMillis() / 1000
            val hash = MessageDigest.getInstance("SHA-1")
                .digest("$PODCASTINDEX_API_KEY$PODCASTINDEX_API_SECRET$now".toByteArray())
                .joinToString("") { "%02x".format(it) }

            val conn = withContext(Dispatchers.IO) {
                val u = URL("https://api.podcastindex.org/api/1.0/search/byterm?q=${java.net.URLEncoder.encode(q, "UTF-8")}")
                (u.openConnection() as java.net.HttpURLConnection).apply {
                    setRequestProperty("X-Auth-Key", PODCASTINDEX_API_KEY)
                    setRequestProperty("X-Auth-Date", now.toString())
                    setRequestProperty("Authorization", hash)
                    setRequestProperty("User-Agent", "CloudApp/1.0")
                    connect()
                }
            }
            val json = withContext(Dispatchers.IO) {
                conn.inputStream.bufferedReader().readText()
            }
            val arr = JSONObject(json).getJSONArray("feeds")
            feeds = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                PodcastFeed(
                    title = f.optString("title"),
                    author = f.optString("author").ifEmpty { f.optString("ownerName") },
                    image = f.optString("image"),
                    feedUrl = f.optString("url"),
                )
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isSearching = false
        }
    }

    suspend fun loadEpisodes(feedUrl: String) {
        if (episodes.containsKey(feedUrl)) return
        loadingEpisodes = feedUrl
        try {
            val xml = withContext(Dispatchers.IO) {
                URL(feedUrl).readText()
            }
            val doc = withContext(Dispatchers.IO) {
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(InputSource(StringReader(xml)))
            }
            val items = doc.getElementsByTagName("item")
            val list = (0 until minOf(items.length, 50)).mapNotNull { i ->
                val item = items.item(i)
                val children = item.childNodes
                var title = ""
                var audioUrl = ""
                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName) {
                        "title" -> title = node.textContent.trim()
                        "enclosure" -> audioUrl = node.attributes?.getNamedItem("url")?.nodeValue ?: ""
                    }
                }
                if (audioUrl.isEmpty()) null else Episode(title.ifEmpty { "Ohne Titel" }, audioUrl)
            }
            episodes = episodes + (feedUrl to list)
        } catch (_: Exception) {
            episodes = episodes + (feedUrl to emptyList())
        } finally {
            loadingEpisodes = null
        }
    }

    fun downloadEpisode(audioUrl: String, title: String) {
        val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(100)
        val filename = "$safeTitle.mp3"
        val proxyUrl = "$PODCAST_DOWNLOAD_PROXY?url=${java.net.URLEncoder.encode(audioUrl, "UTF-8")}&filename=${java.net.URLEncoder.encode(filename, "UTF-8")}"

        val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "cloud/podcasts")
        destDir.mkdirs()
        val destFile = File(destDir, filename)

        val request = DownloadManager.Request(proxyUrl.toUri()).apply {
            setTitle(filename)
            setDescription("Podcast wird heruntergeladen…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(destFile.toUri())
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(proxyUrl) ?: "")
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                ctx.unregisterReceiver(this)

                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    Toast.makeText(
                        ctx,
                        if (status == DownloadManager.STATUS_SUCCESSFUL) "Gespeichert in cloud/podcasts/" else "Download fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                cursor.close()
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        Toast.makeText(context, "Download gestartet", Toast.LENGTH_SHORT).show()
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            placeholder = { Text("Podcast suchen…") },
            singleLine = true,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = {
                        keyboard?.hide()
                        scope.launch { search(query) }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                scope.launch { search(query) }
            }),
            shape = RoundedCornerShape(12.dp),
        )

        error?.let {
            Text("Fehler: $it", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (feeds.isEmpty() && !isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🎙️ Gib einen Podcast-Namen ein", color = Color.White.copy(0.5f))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(feeds) { feed ->
                    val isExpanded = expandedFeedUrl == feed.feedUrl
                    val feedEpisodes = episodes[feed.feedUrl]

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = feed.image.ifEmpty { null },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2A2A32)),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(feed.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (feed.author.isNotEmpty()) {
                                        Text(feed.author, fontSize = 12.sp, color = Color(0xFF7A7880), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (isExpanded) {
                                            expandedFeedUrl = null
                                        } else {
                                            expandedFeedUrl = feed.feedUrl
                                            scope.launch { loadEpisodes(feed.feedUrl) }
                                        }
                                    }
                                ) {
                                    if (loadingEpisodes == feed.feedUrl) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFE8622A))
                                    } else {
                                        Icon(
                                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color(0xFFE8622A)
                                        )
                                    }
                                }
                            }

                            if (isExpanded) {
                                HorizontalDivider(color = Color.White.copy(0.07f))
                                when {
                                    feedEpisodes == null -> {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFE8622A))
                                        }
                                    }
                                    feedEpisodes.isEmpty() -> {
                                        Text("Keine Episoden gefunden", modifier = Modifier.padding(16.dp), color = Color(0xFF7A7880), fontSize = 13.sp)
                                    }
                                    else -> {
                                        feedEpisodes.forEachIndexed { idx, ep ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text("${idx + 1}", fontSize = 11.sp, color = Color(0xFF4A4850), modifier = Modifier.width(24.dp))
                                                Text(ep.title, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                OutlinedIconButton(
                                                    onClick = { downloadEpisode(ep.audioUrl, ep.title) },
                                                    modifier = Modifier.size(36.dp),
                                                    border = BorderStroke(1.dp, Color.White.copy(0.15f))
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Download", tint = Color(0xFFE8622A), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            if (idx < feedEpisodes.lastIndex) {
                                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White.copy(0.04f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}