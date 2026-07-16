package com.tabslify.tabs.mediaplayer

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabslify.core.objects.Config
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.FeedCard
import com.tabslify.services.MediaPlayerService
import com.tabslify.spotifydownloader_own.data.DownloadRepositoryImpl
import com.tabslify.spotifydownloader_own.domain.DownloadState
import com.tabslify.spotifydownloader_own.ui.DownloadViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.XMLConstants
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
    val publishDate: Long = 0L,
)

data class SearchResult(
    val feed: PodcastFeed
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastTab() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    val httpClient = remember {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    }
                )
            }
        }
    }

    val factory = remember(context, httpClient) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = DownloadRepositoryImpl(httpClient, context.applicationContext as Context)
                return DownloadViewModel(repo, context.applicationContext as Context) as T
            }
        }
    }

    val vm: DownloadViewModel = viewModel(factory = factory)
    val downloadState by vm.downloadState.collectAsState()

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedFeedUrl by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var loadingEpisodes by remember { mutableStateOf<String?>(null) }
    var feedToUnfav by remember { mutableStateOf<PodcastFeed?>(null) }
    var newEpisodesState by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val svc = com.tabslify.services.QuietHoursNotificationService()
                val found = svc.checkPodcastsAndNotify(context, true)
                newEpisodesState = found
            } catch (_: Exception) {
            }
        }
    }

    val isUrl = remember(query) {
        android.util.Patterns.WEB_URL.matcher(query).matches() || query.startsWith("http")
    }

    suspend fun search(q: String) {
        if (q.isBlank()) return
        if (isUrl) {
            vm.startDownload(q)
            return
        }
        isSearching = true
        hasSearched = true
        error = null
        results = emptyList()
        try {
            val requestBody = JSONObject().apply {
                put("action", "podcastindex")
                put("payload", JSONObject().apply {
                    put(
                        "url",
                        "https://api.podcastindex.org/api/1.0/search/byterm?q=${
                            java.net.URLEncoder.encode(
                                q,
                                "UTF-8"
                            )
                        }"
                    )
                })
                put("apiKey", Config.userApiKey(context, "podcastindex"))
                put("apiSecret", Config.userApiKey(context, "podcastindex_secret"))
            }.toString()

            val conn = Config.openApiProxyConnection(context) ?: run {
                error = "App-Signatur konnte nicht validiert werden"
                return
            }
            withContext(Dispatchers.IO) {
                conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            }
            val responseCode = withContext(Dispatchers.IO) { conn.responseCode }
            if (responseCode != 200) {
                val errorText = withContext(Dispatchers.IO) {
                    conn.errorStream?.bufferedReader()?.readText() ?: "No error body"
                }
                android.util.Log.e(
                    "PodcastDownloaderTab",
                    "Podcast Index proxy failed: Code $responseCode, Body: $errorText"
                )
                error = "API Fehler: Code $responseCode"
                return
            }
            val json = withContext(Dispatchers.IO) { conn.inputStream.bufferedReader().readText() }
            val arr = JSONObject(json).getJSONArray("feeds")
            val podcastResults = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                SearchResult(
                    PodcastFeed(
                        title = f.optString("title"),
                        author = f.optString("author").ifEmpty { f.optString("ownerName") },
                        image = f.optString("image"),
                        feedUrl = f.optString("url"),
                    )
                )
            }
            results = podcastResults
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
            val doc = withContext(Dispatchers.IO) {
                val conn = URL(feedUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept-Charset", "UTF-8")
                DocumentBuilderFactory.newInstance()
                    .apply {
                        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        setFeature("http://xml.org/sax/features/external-general-entities", false)
                        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                        isXIncludeAware = false
                        isExpandEntityReferences = false
                    }
                    .newDocumentBuilder()
                    .parse(conn.inputStream)
            }
            val items = doc.getElementsByTagName("item")
            val list = (0 until minOf(items.length, 50)).mapNotNull { i ->
                val item = items.item(i)
                val children = item.childNodes
                var title = ""
                var audioUrl = ""
                var pubDate = ""
                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName) {
                        "title" -> title = node.textContent.trim()
                        "enclosure" -> audioUrl =
                            node.attributes?.getNamedItem("url")?.nodeValue ?: ""

                        "pubDate" -> pubDate = node.textContent.trim()
                        "isoDate" -> if (pubDate.isEmpty()) pubDate = node.textContent.trim()
                    }
                }
                if (audioUrl.isEmpty()) null else {
                    val timestamp = try {
                        java.text.SimpleDateFormat(
                            "EEE, dd MMM yyyy HH:mm:ss Z",
                            java.util.Locale.ENGLISH
                        ).parse(pubDate)?.time
                            ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        try {
                            java.time.Instant.parse(pubDate).toEpochMilli()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    }
                    Episode(title.ifEmpty { "Ohne Titel" }, audioUrl, timestamp)
                }
            }.sortedByDescending { it.publishDate }
            episodes = episodes + (feedUrl to list)
        } catch (_: Exception) {
            episodes = episodes + (feedUrl to emptyList())
        } finally {
            loadingEpisodes = null
        }
    }

    fun downloadEpisode(audioUrl: String, title: String, showName: String) {
        val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val filename = "$safeTitle.mp3"
        val subPath = "Tabslify/$filename"

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val alreadyDone = dm.query(
            DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
        )?.use { cursor ->
            val col = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            while (cursor.moveToNext()) {
                if (cursor.getString(col) == filename) return@use true
            }
            false
        } ?: false

        if (alreadyDone) {
            Toast.makeText(context, "Datei existiert bereits", Toast.LENGTH_SHORT).show()
            return
        }

        val request = DownloadManager.Request(audioUrl.toUri()).apply {
            setTitle(filename)
            setDescription("Podcast wird heruntergeladen…")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_PODCASTS, subPath)
            setAllowedOverMetered(true)
            addRequestHeader("User-Agent", "Mozilla/5.0")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        }
        val downloadId = dm.enqueue(request)

        val prefs = context.getSharedPreferences("podcast_downloads", Context.MODE_PRIVATE)
        prefs.edit {
            putString("pending_$downloadId", JSONObject().apply {
                put("safeTitle", safeTitle)
                put("showName", showName)
            }.toString())
        }

        Toast.makeText(context, "Download gestartet", Toast.LENGTH_SHORT).show()
    }

    fun streamEpisode(audioUrl: String) {
        MediaPlayerService.streamRemote(context, audioUrl)
    }

    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("podcast_favs", Context.MODE_PRIVATE)

    fun loadFavs(): Map<String, PodcastFeed> {
        val raw = prefs.getString("favs", null) ?: return emptyMap()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                val f = PodcastFeed(
                    o.getString("title"),
                    o.getString("author"),
                    o.getString("image"),
                    o.getString("feedUrl")
                )
                f.feedUrl to f
            }
        } catch (_: Exception) {
            emptyMap()
        }
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

    feedToUnfav?.let { feed ->
        AlertDialogTabslify(
            onConfirm = {
                favorites = favorites - feed.feedUrl
                saveFavs(favorites)
                feedToUnfav = null
            },
            onDismiss = { feedToUnfav = null },
            title = "Aus Favoriten entfernen?",
            text = "\"${feed.title}\" wird aus deinen Lieblings-Podcasts entfernt.",
            confirmText = "Entfernen"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it; if (query.isEmpty()) {
                results = emptyList(); isSearching = false
            }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            placeholder = { Text("Podcast suchen oder URL eingeben…") },
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
            if (!hasSearched && !query.isNotBlank()) {
                Text(
                    "Fehler: $it",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        if (isUrl) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { vm.startDownload(query) },
                    enabled = downloadState is DownloadState.Idle || downloadState is DownloadState.Success || downloadState is DownloadState.Error
                ) {
                    Text("Download")
                }

                Spacer(modifier = Modifier.height(32.dp))

                when (val state = downloadState) {
                    is DownloadState.Idle -> Text("URL eingeben und Download starten")
                    is DownloadState.Searching -> CircularProgressIndicator()
                    is DownloadState.Downloading -> {
                        val progress = state.progress
                        LinearProgressIndicator(progress = { progress / 100f })
                        Text("Downloading: $progress%")
                    }

                    is DownloadState.Converting -> {
                        CircularProgressIndicator()
                        Text("Converting to MP3...")
                    }

                    is DownloadState.Success -> Text("Download Complete!")
                    is DownloadState.Error -> Text(
                        "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (results.isEmpty() && !isSearching) {
            if (hasSearched && query.isNotBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Keine Podcast Shows gefunden", color = Color.White.copy(0.5f))
                        Text(
                            "Versuche es mit einem anderen Suchbegriff",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.3f)
                        )
                    }
                }
            } else if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "🎙️ Podcast suchen oder URL zum Download eingeben",
                        color = Color.White.copy(0.5f)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "Lieblings-Podcasts", fontSize = 13.sp, color = Color(0xFF7A7880),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(favorites.values.toList()) { feed ->
                        val isExpanded = expandedFeedUrl == feed.feedUrl
                        val feedEpisodes = episodes[feed.feedUrl]
                        val hasNew = newEpisodesState.any { it.optString("showName") == feed.title }

                        Box {
                            FeedCard(
                                feed = feed, isExpanded = isExpanded, feedEpisodes = feedEpisodes,
                                loadingEpisodes = loadingEpisodes, isFavorite = true,
                                onToggleExpand = {
                                    if (isExpanded) expandedFeedUrl = null
                                    else {
                                        expandedFeedUrl = feed.feedUrl
                                        scope.launch { loadEpisodes(feed.feedUrl) }
                                        newEpisodesState =
                                            newEpisodesState.filterNot { it.optString("showName") == feed.title }
                                    }
                                },
                                onToggleFav = { feedToUnfav = feed },
                                onDownload = { url, title ->
                                    downloadEpisode(
                                        url,
                                        title,
                                        feed.title
                                    )
                                },
                                onStream = { url -> streamEpisode(url) }
                            )
                            if (hasNew && !isExpanded) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(12.dp)
                                        .background(
                                            Color.Red,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { result ->
                    val feed = result.feed
                    val isExpanded = expandedFeedUrl == feed.feedUrl
                    val feedEpisodes = episodes[feed.feedUrl]
                    FeedCard(
                        feed = feed,
                        isExpanded = isExpanded,
                        feedEpisodes = feedEpisodes,
                        loadingEpisodes = loadingEpisodes,
                        isFavorite = favorites.containsKey(feed.feedUrl),
                        onToggleExpand = {
                            if (isExpanded) expandedFeedUrl = null
                            else {
                                expandedFeedUrl =
                                    feed.feedUrl; scope.launch { loadEpisodes(feed.feedUrl) }
                            }
                        },
                        onToggleFav = {
                            if (favorites.containsKey(feed.feedUrl)) feedToUnfav = feed
                            else {
                                favorites = favorites + (feed.feedUrl to feed)
                                saveFavs(favorites)
                            }
                        },
                        onDownload = { url, title -> downloadEpisode(url, title, feed.title) },
                        onStream = { url -> streamEpisode(url) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
