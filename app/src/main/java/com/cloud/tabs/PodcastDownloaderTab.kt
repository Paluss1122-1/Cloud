package com.cloud.tabs

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.cloud.core.objects.Config.PODCASTINDEX_API_KEY
import com.cloud.core.objects.Config.PODCASTINDEX_API_SECRET
import com.cloud.core.ui.FeedCard
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

data class PodcastFeed(
    val title: String,
    val author: String,
    val image: String,
    val feedUrl: String,
)

data class Episode(
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
        val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "cloud/podcasts")
        destDir.mkdirs()

        val request = DownloadManager.Request(audioUrl.toUri()).apply {
            setTitle(filename)
            setDescription("Podcast wird heruntergeladen…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(File(destDir, filename).toUri())
            setAllowedOverMetered(true)
            addRequestHeader("User-Agent", "Mozilla/5.0")
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
    val prefs = context.getSharedPreferences("podcast_favs", Context.MODE_PRIVATE)

    fun loadFavs(): Map<String, PodcastFeed> {
        val raw = prefs.getString("favs", null) ?: return emptyMap()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                val f = PodcastFeed(o.getString("title"), o.getString("author"), o.getString("image"), o.getString("feedUrl"))
                f.feedUrl to f
            }
        } catch (_: Exception) { emptyMap() }
    }

    fun saveFavs(favs: Map<String, PodcastFeed>) {
        val arr = org.json.JSONArray()
        favs.values.forEach { f ->
            arr.put(JSONObject().apply {
                put("title", f.title); put("author", f.author)
                put("image", f.image); put("feedUrl", f.feedUrl)
            })
        }
        prefs.edit { putString("favs", arr.toString()) }
    }

    var favorites by remember { mutableStateOf(loadFavs()) }

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
            if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("🎙️ Gib einen Podcast-Namen ein", color = Color.White.copy(0.5f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("Favoriten", fontSize = 13.sp, color = Color(0xFF7A7880),
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(favorites.values.toList()) { feed ->
                        val isExpanded = expandedFeedUrl == feed.feedUrl
                        val feedEpisodes = episodes[feed.feedUrl]
                        FeedCard(
                            feed = feed, isExpanded = isExpanded, feedEpisodes = feedEpisodes,
                            loadingEpisodes = loadingEpisodes, isFavorite = true,
                            onToggleExpand = {
                                if (isExpanded) expandedFeedUrl = null
                                else { expandedFeedUrl = feed.feedUrl; scope.launch { loadEpisodes(feed.feedUrl) } }
                            },
                            onToggleFav = {
                                favorites = favorites - feed.feedUrl
                                saveFavs(favorites)
                            },
                            onDownload = { url, title -> downloadEpisode(url, title) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(feeds) { feed ->
                    val isExpanded = expandedFeedUrl == feed.feedUrl
                    val feedEpisodes = episodes[feed.feedUrl]
                    FeedCard(
                        feed = feed, isExpanded = isExpanded, feedEpisodes = feedEpisodes,
                        loadingEpisodes = loadingEpisodes, isFavorite = favorites.containsKey(feed.feedUrl),
                        onToggleExpand = {
                            if (isExpanded) expandedFeedUrl = null
                            else { expandedFeedUrl = feed.feedUrl; scope.launch { loadEpisodes(feed.feedUrl) } }
                        },
                        onToggleFav = {
                            favorites = if (favorites.containsKey(feed.feedUrl)) favorites - feed.feedUrl
                            else favorites + (feed.feedUrl to feed)
                            saveFavs(favorites)
                        },
                        onDownload = { url, title -> downloadEpisode(url, title) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}