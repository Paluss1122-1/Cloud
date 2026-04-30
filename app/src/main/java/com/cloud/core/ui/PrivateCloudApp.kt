@file:Suppress("AssignedValueIsNeverRead")

package com.cloud.core.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.cloud.R
import com.cloud.core.TabNavigationViewModel
import com.cloud.core.objects.Config
import com.cloud.core.objects.Config.cms
import com.cloud.core.objects.FavoriteManager
import com.cloud.privatecloudapp.FileIcon
import com.cloud.privatecloudapp.FullscreenImageDialog
import com.cloud.privatecloudapp.fileExistsInDCIM
import com.cloud.privatecloudapp.fileExistsLocallyWithSameSize
import com.cloud.privatecloudapp.getFileNameFromUri
import com.cloud.privatecloudapp.getLocalFileWithPath
import com.cloud.privatecloudapp.getMimeType
import com.cloud.privatecloudapp.isImageFile
import com.cloud.privatecloudapp.isOnline
import com.cloud.tabs.AiResponseHistorySheet
import com.cloud.tabs.BrowserTabContent
import com.cloud.tabs.CalendarTabContent
import com.cloud.tabs.ContactsRepository
import com.cloud.tabs.ContactsTabContent
import com.cloud.tabs.ContactsViewModel
import com.cloud.tabs.DateCalculatorContent
import com.cloud.tabs.GalleryTab
import com.cloud.tabs.GmailTabContent
import com.cloud.tabs.MediaAnalyticsManager
import com.cloud.tabs.MediaRecorderContent
import com.cloud.tabs.MediaTab
import com.cloud.tabs.MovieDiscoveryTabContent
import com.cloud.tabs.NotizenApp
import com.cloud.tabs.OtherBucketViewer
import com.cloud.tabs.PodcastTab
import com.cloud.tabs.QuickSettingsTabContent
import com.cloud.tabs.RemoteDesktopTabContent
import com.cloud.tabs.SpotifyDownloaderApp
import com.cloud.tabs.SpotifyDownloaderTab
import com.cloud.tabs.VocabTab
import com.cloud.tabs.WeatherTabContent
import com.cloud.tabs.aitab.AITabContent
import com.cloud.tabs.audiorecordertab.AudioRecorderContent
import com.cloud.tabs.authenticator.AuthenticatorTab
import com.cloud.tabs.exploretab.ExploreTabContent
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

const val KEY_RECENT_TABS = "recent_tabs"
const val MAX_RECENT_TABS = 5

enum class MenuItem(
    val title: String,
    val icon: String,
    val content: @Composable (setGesturesEnabled: (Boolean) -> Unit) -> Unit
) {
    PRIVATE_CLOUD(
        "Private Cloud",
        "☁️",
        {}
    ),
    AITAB(
        "AI Tab",
        "☁️",
        { AITabContent() }
    ),
    BROWSER(
        "Browser",
        "🌐",
        {}
    ),
    QUICK(
        "Schnellzugriff",
        "⚡",
        { QuickSettingsTabContent() }
    ),
    GALLERY(
        "Gallerie",
        "🖼️",
        { setGesturesEnabled ->
            LaunchedEffect(Unit) { setGesturesEnabled(true) }
            GalleryTab()
        }
    ),
    AUTHENTICATOR(
        "Authenticator",
        "🔒",
        { AuthenticatorTab() }
    ),
    WEATHER(
        "Wetter",
        "🌡️",
        { }
    ),
    CONTACTS(
        "Kontakte",
        "🧍",
        {
            val context = LocalContext.current
            val repository = remember { ContactsRepository(context) }
            val viewModel = remember { ContactsViewModel(repository) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.all { it.value }) {
                    viewModel.loadContacts()
                }
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS
                    )
                )
            }

            ContactsTabContent(
                state = viewModel.state,
                onLoadContacts = { viewModel.loadContacts() },
                onSaveContact = { contact -> viewModel.saveContact(contact) },
                onDeleteContact = { id -> viewModel.deleteContact(id) }
            )
        }
    ),
    RECORDER(
        "Recorder",
        "🎙️",
        { AudioRecorderContent() }
    ),
    DATECALCULATOR(
        "Date Calculator",
        "📅",
        { DateCalculatorContent() }
    ),
    MOVIEDISCOVER(
        "Filme Discovery",
        "📺",
        { MovieDiscoveryTabContent() }
    ),
    NOTES(
        "Notizen",
        "📖",
        { NotizenApp() }
    ),
    MEDIARECORDER(
        "Media Recorder",
        "🎵",
        { MediaRecorderContent() }
    ),
    SPOTIFYDOWNLOADER(
        "Spotify Downloader",
        "🎧",
        { SpotifyDownloaderTab() }
    ),
    MEDIAPLAYERTAB(
        "Media Player",
        "️️🎶",
        { MediaTab() }
    ),
    GMAIL(
        "Gmail",
        "️️✉️",
        { GmailTabContent() }
    ),
    Vocabs(
        "Vokabeln",
        "️️🏫️",
        { VocabTab() }
    ),
    SPOTIY(
        "Spotify 2.0",
        "️️🏫️",
        { SpotifyDownloaderApp() }
    ),
    EXPLORE(
        "Erkunden",
        "🗺️",
        { setGesturesEnabled ->
            ExploreTabContent(setGesturesEnabled)
        }
    ),
    CALENDAR(
        "Kalender",
        "📅",
        { CalendarTabContent() }
    ),
    REMOTEDESKTOP(
        "Remote Desktop",
        "🖥️",
        { RemoteDesktopTabContent() }
    ),
    PODCAST(
        "Podcasts",
        "🎙️",
        { PodcastTab() }
    ),
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateCloudApp(
    storage: Storage,
    startTarget: String?,
    initialMenuItem: MenuItem,
    onMenuClick: (() -> Unit)? = null,
    viewModel: TabNavigationViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedMenuItem by rememberSaveable {
        mutableStateOf(initialMenuItem)
    }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var webViewUrl by rememberSaveable { mutableStateOf("https://www.google.com") }
    var currentUrl by rememberSaveable { mutableStateOf(webViewUrl) }
    var webViewState by remember { mutableStateOf<WebView?>(null) }
    var isDesktopMode by rememberSaveable { mutableStateOf(false) }
    val navigationState by viewModel.navigationState.collectAsState()
    var gesturesEnabled by rememberSaveable { mutableStateOf(true) }

    val setGesturesEnabled: (Boolean) -> Unit = { enabled ->
        gesturesEnabled = enabled
    }

    LaunchedEffect(startTarget) {
        if (startTarget == "weather") {
            selectedMenuItem = MenuItem.WEATHER
        }
    }

    val activity = context as? Activity
    LaunchedEffect(isFullScreen) {
        val controller = activity?.window?.insetsController ?: return@LaunchedEffect
        if (isFullScreen) {
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            (activity as? AppCompatActivity)?.supportActionBar?.hide()
        } else {
            controller.show(android.view.WindowInsets.Type.systemBars())
        }
    }

    LaunchedEffect(selectedMenuItem) {
        gesturesEnabled = when (selectedMenuItem) {
            MenuItem.EXPLORE -> false
            MenuItem.GALLERY -> false
            else -> true
        }
    }

    LaunchedEffect(currentUrl) {
        saveLastUrl(context, currentUrl)
    }

    if (isFullScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedMenuItem) {
                MenuItem.WEATHER -> WeatherTabContent(viewModel = viewModel)
                MenuItem.BROWSER -> {
                    BrowserTabContent(
                        url = webViewUrl,
                        onUrlChange = { webViewUrl = it },
                        onEnterFullScreen = { isFullScreen = true }
                    )
                }

                MenuItem.PRIVATE_CLOUD -> {
                    MainCloudScreen(storage = storage)
                }

                else -> selectedMenuItem.content(setGesturesEnabled)
            }
        }

        if (selectedMenuItem == MenuItem.BROWSER) {
            BackHandler(enabled = true) {
                if (webViewState?.canGoBack() == true) {
                    webViewState?.goBack()
                } else {
                    isFullScreen = false
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Cloud.copy(0.8f))
                .then(
                    if (gesturesEnabled) {
                        Modifier.pointerInput(navigationState.canNavigateBack) {
                            var gestureHandled = false
                            detectHorizontalDragGestures(
                                onDragStart = { gestureHandled = false },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (!gestureHandled && dragAmount > 20f) {
                                        gestureHandled = true
                                        if (navigationState.canNavigateBack) {
                                            viewModel.triggerBack()
                                        } else {
                                            onMenuClick?.invoke()
                                        }
                                    }
                                },
                                onDragEnd = { gestureHandled = false },
                                onDragCancel = { gestureHandled = false }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val bgpicture = remember {
                when (currentHour) {
                    in 11..16 -> R.drawable.mittag
                    else -> R.drawable.night
                }
            }
            Image(
                painter = painterResource(id = bgpicture),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Cloud.copy(0.5f))
            )
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedMenuItem.icon} ${selectedMenuItem.title}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            if (onMenuClick != null) {
                                IconButton(onClick = { onMenuClick() }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menü öffnen",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menü öffnen",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .alpha(0f)
                                        .size(48.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        windowInsets = WindowInsets.statusBars
                    )
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    when (selectedMenuItem) {
                        MenuItem.WEATHER -> WeatherTabContent(viewModel = viewModel)
                        MenuItem.BROWSER -> BrowserTabContent(
                            url = webViewUrl,
                            onUrlChange = { webViewUrl = it },
                            onEnterFullScreen = { isFullScreen = true }
                        )

                        MenuItem.PRIVATE_CLOUD -> MainCloudScreen(storage = storage)
                        else -> selectedMenuItem.content(setGesturesEnabled)
                    }
                }
            }
        }
    }

    if (isFullScreen) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val webView = remember {
                WebView(context).apply {
                    webChromeClient = object : WebChromeClient() {
                        private var customView: View? = null
                        private var customViewCallback: CustomViewCallback? = null

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            (context as? Activity)?.let { activity ->
                                val decor = activity.window.decorView as FrameLayout
                                decor.addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                customView = view
                                customViewCallback = callback

                                activity.window.insetsController?.apply {
                                    hide(android.view.WindowInsets.Type.systemBars())
                                    systemBarsBehavior =
                                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                }
                            }
                        }

                        override fun onHideCustomView() {
                            (context as? Activity)?.let { activity ->
                                val decor = activity.window.decorView as FrameLayout
                                decor.removeView(customView)
                                customView = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                                isFullScreen = false
                                activity.window.insetsController?.show(
                                    android.view.WindowInsets.Type.systemBars()
                                )
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) {
                                currentUrl = url
                                WebViewCookieBackup.saveCookies(context, url)
                            }
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true

                        allowFileAccess = true
                        allowContentAccess = true

                        loadsImagesAutomatically = true
                        blockNetworkLoads = false

                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        useWideViewPort = true
                        loadWithOverviewMode = true

                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)

                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)

                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false

                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    cookieManager.flush()

                    WebViewCookieBackup.restoreCookies(context, webViewUrl)

                    setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->

                        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)

                        // Zuerst normal in Downloads/ runterladen
                        val request = DownloadManager.Request(url.toUri()).apply {
                            setMimeType(mimetype)
                            setTitle(filename)
                            setDescription("Wird heruntergeladen…")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                            addRequestHeader("User-Agent", userAgent)
                            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                        }

                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val downloadId = dm.enqueue(request)

                        // BroadcastReceiver: wird gefeuert wenn Download fertig
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                if (id != downloadId) return

                                // Receiver sofort wieder abmelden
                                ctx.unregisterReceiver(this)

                                val query = DownloadManager.Query().setFilterById(downloadId)
                                val cursor = dm.query(query)

                                if (cursor.moveToFirst()) {
                                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                    val status = cursor.getInt(statusCol)

                                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                        val src = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            filename
                                        )
                                        val destDir = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            "cloud/podcasts"
                                        )
                                        destDir.mkdirs()
                                        val dest = File(destDir, filename)

                                        val moved = src.renameTo(dest)

                                        Toast.makeText(
                                            ctx,
                                            if (moved) "Gespeichert in cloud/podcasts/" else "Download OK, Verschieben fehlgeschlagen",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                cursor.close()
                            }
                        }

                        context.registerReceiver(
                            receiver,
                            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                            Context.RECEIVER_NOT_EXPORTED
                        )

                        Toast.makeText(context, "Download gestartet", Toast.LENGTH_SHORT).show()
                    }

                    loadUrl(webViewUrl)
                }
            }
            DisposableEffect(isFullScreen) {
                onDispose {
                    if (!isFullScreen) {
                        webView.stopLoading()
                        webView.onPause()
                        webView.destroy()
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    update = { webViewState = it },
                    modifier = Modifier.fillMaxSize()
                )

                Button(
                    onClick = {
                        isDesktopMode = !isDesktopMode
                        val currentUrl =
                            webView.url ?: return@Button  // falls keine URL geladen ist

                        webView.settings.apply {
                            if (isDesktopMode) {
                                // Desktop-Modus (Windows 11 PC)
                                userAgentString =
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            } else {
                                // Mobile-Modus (wie vorher)
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                        }

                        val desktopUA =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

                        webView.evaluateJavascript(
                            """
            Object.defineProperty(navigator, 'userAgent', {
                value: '${if (isDesktopMode) desktopUA else webView.settings.userAgentString}',
                configurable: true
            });
            Object.defineProperty(navigator, 'platform', {
                value: '${if (isDesktopMode) "Win32" else "Linux armv8l"}',
                configurable: true
            });
        """.trimIndent(), null
                        )

                        webView.loadUrl(currentUrl)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = if (isDesktopMode) Icons.Filled.Laptop else Icons.Filled.Phone,
                        contentDescription = if (isDesktopMode) "Desktop-Modus" else "Mobile-Modus",
                        tint = Color.White
                    )
                }
            }

            BackHandler(enabled = true) {
                if (webViewState?.canGoBack() == true) {
                    webViewState?.goBack()
                } else {
                    isFullScreen = false
                }
            }
        }

        return
    }
}

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun MainCloudScreen(storage: Storage) {
    data class CloudFileMeta(
        val name: String,
        val updatedAt: String,
        val size: Long,
    )

    var fileList by remember { mutableStateOf<List<CloudFileMeta>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("Alle") }
    var sortOption by remember { mutableStateOf("A-Z") }
    var showUploadProgress by remember { mutableStateOf(false) }
    var showDownloadProgress by remember { mutableStateOf(false) }
    var favoritesClickCount by remember { mutableIntStateOf(0) }
    var showOtherBucket by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var favoriteFiles by remember {
        mutableStateOf(FavoriteManager.loadFavorites(context))
    }
    var expanded by remember { mutableStateOf(false) }
    var fullscreenImageDialogData by remember { mutableStateOf<Triple<String?, String, Long>?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pendingOtherBucket by remember { mutableStateOf(false) }
    val otherBucketLayer = rememberGraphicsLayer()
    var otherBucketBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val otherBucketScale = remember { Animatable(0f) }
    val otherBucketAlpha = remember { Animatable(0f) }
    var shouldshow by remember { mutableStateOf(false) }

    BackHandler(true) { }

    suspend fun loadFiles() {
        try {
            val files = withContext(Dispatchers.IO) {
                storage.from(Config.SUPABASE_BUCKET).list()
            }

            val groupedFiles: List<CloudFileMeta> = files
                .filter { it.name != ".emptyFolderPlaceholder" }
                .groupBy { file ->
                    if (file.name.contains(".part")) {
                        file.name.substringBefore(".part")
                    } else {
                        file.name
                    }
                }
                .map { (baseName, chunks) ->
                    if (chunks.size > 1 || chunks.first().name.contains(".part")) {
                        val totalSize = chunks.sumOf { chunk ->
                            when (chunk.metadata?.get("size")) {
                                else -> 0L
                            }
                        }
                        val latestDate = chunks.mapNotNull { it.updatedAt }.maxOrNull()
                        val updatedAtString = latestDate?.toString() ?: ""

                        CloudFileMeta(
                            name = baseName,
                            updatedAt = updatedAtString,
                            size = totalSize,
                        )
                    } else {
                        val file = chunks.first()
                        val localDate = file.updatedAt
                            ?.let {
                                Instant.ofEpochMilli(it.toEpochMilliseconds())
                                    .atZone(ZoneId.systemDefault())
                            }
                            ?.toString() ?: ""

                        CloudFileMeta(
                            name = file.name,
                            updatedAt = localDate,
                            size = 0L,
                        )
                    }
                }

            fileList = groupedFiles
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Laden: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    suspend fun deleteFile(file: CloudFileMeta) {
        try {
            val fileName = file.name
            withContext(Dispatchers.IO) {
                storage.from(Config.SUPABASE_BUCKET).delete(fileName)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "🗑️ '$fileName' gelöscht!", Toast.LENGTH_SHORT).show()
            }
            loadFiles()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Löschen: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
            e.printStackTrace()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val clip = result.data?.clipData
            val dataUri = result.data?.data
            val uris = mutableListOf<Uri>()
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    uris += clip.getItemAt(i).uri
                }
            } else if (dataUri != null) {
                uris += dataUri
            }

            scope.launch {
                isUploading = true
                showUploadProgress = true
                try {
                    for (uri in uris) {
                        val fileName = getFileNameFromUri(uri, context) ?: "unnamed_file"
                        val file = File(context.cacheDir, fileName)

                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val fileSize = file.length()
                        val maxChunkSize = 20 * 1024 * 1024L

                        if (fileSize <= maxChunkSize) {
                            val bytes = file.readBytes()
                            storage.from(Config.SUPABASE_BUCKET)
                                .upload(fileName, bytes)
                        } else {
                            val inputStream = file.inputStream()
                            var chunkIndex = 0
                            val buffer = ByteArray(maxChunkSize.toInt())
                            var bytesRead: Int

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                val chunkData = buffer.copyOf(bytesRead)
                                val chunkFileName = "${fileName}.part${chunkIndex + 1}"
                                storage.from(Config.SUPABASE_BUCKET)
                                    .upload(chunkFileName, chunkData)
                                chunkIndex++
                            }
                            inputStream.close()
                        }

                        file.delete()
                    }

                    Toast.makeText(context, "✅ Hochgeladen!", Toast.LENGTH_SHORT).show()
                    loadFiles()
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Fehler beim Upload: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    isUploading = false
                    showUploadProgress = false
                }
            }

        }
    }

    LaunchedEffect(Unit) {
        if (!isOnline(context)) {
            Toast.makeText(context, "🚫 Keine Internetverbindung", Toast.LENGTH_LONG).show()
        } else {
            loadFiles()
        }
    }

    if (!showOtherBucket) {
        val alpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            delay(100)
            alpha.animateTo(
                1f, animationSpec = tween(
                    durationMillis = 150,
                    easing = FastOutSlowInEasing
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
                .background(Color.Transparent)
                .alpha(alpha.value)
        )
        {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp, 0.dp, 16.dp, 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Alle", "Dateien", "Bilder", "Favoriten").forEach { filter ->
                        val containerColor by animateColorAsState(
                            targetValue = if (selectedFilter == filter) Color(0xFF555555) else Color(
                                0xFF333333
                            ),
                            animationSpec = tween(durationMillis = 300),
                            label = "containerColor"
                        )
                        PloppingButton(
                            onClick = {
                                if (filter == "Favoriten") {
                                    favoritesClickCount++
                                    if (favoritesClickCount >= 5) {
                                        pendingOtherBucket = true
                                        favoritesClickCount = 0
                                    }
                                } else {
                                    favoritesClickCount = 0
                                }
                                selectedFilter = filter
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = filter,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                val preFilteredFileList = remember(fileList, selectedFilter) {
                    when (selectedFilter) {
                        "Bilder" -> fileList.filter { isImageFile(it.name) }
                        "Dateien" -> fileList.filter { !isImageFile(it.name) }
                        else -> fileList
                    }
                }

                var searchQuery by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Suche",
                                    color = Color.White
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        val sortOptions = listOf("A-Z", "Größte Datei", "Zuletzt hochgeladen")
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.width(150.dp)
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = sortOption,
                                onValueChange = {},
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Sortierung öffnen",
                                        tint = Color.White
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.DarkGray,
                                    focusedContainerColor = Color.DarkGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color.DarkGray)
                            ) {
                                sortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color.White) },
                                        onClick = {
                                            sortOption = option
                                            expanded = false
                                        },
                                        contentPadding = PaddingValues(16.dp),
                                        colors = MenuDefaults.itemColors(
                                            textColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val filteredFileList = remember(
                        fileList,
                        selectedFilter,
                        searchQuery,
                        sortOption,
                        favoriteFiles
                    ) {
                        val filtered = fileList.filter { file ->
                            val fileName = file.name
                            val matchesFilter = when (selectedFilter) {
                                "Bilder" -> isImageFile(fileName)
                                "Dateien" -> !isImageFile(fileName)
                                "Favoriten" -> favoriteFiles.contains(fileName)
                                else -> true
                            }
                            val matchesSearch = fileName.contains(searchQuery, ignoreCase = true)
                            matchesFilter && matchesSearch
                        }

                        when (sortOption) {
                            "A-Z" -> filtered.sortedBy { it.name.lowercase() }
                            "Größte Datei" -> filtered.sortedByDescending {
                                it.size
                            }

                            "Zuletzt hochgeladen" -> filtered.sortedByDescending {
                                it.updatedAt
                            }

                            else -> filtered
                        }
                    }

                    if (preFilteredFileList.isEmpty()) {
                        Text(
                            "Keine ${if (selectedFilter == "Alle") "Dateien" else selectedFilter} vorhanden",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedFilter == "Bilder") {
                            items(filteredFileList.chunked(2)) { rowFiles ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowFiles.forEach { file ->
                                        val fileName = file.name
                                        val fileDate =
                                            file.updatedAt.replace("T", " ")
                                                .substringBefore(".")
                                                .ifEmpty { "Unbekannt" }
                                        val sizeBytes = file.size
                                        var publicUrl by remember(fileName) {
                                            mutableStateOf<String?>(
                                                null
                                            )
                                        }
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f),
                                            onClick = {
                                                if (publicUrl != null) {
                                                    fullscreenImageDialogData =
                                                        Triple(publicUrl, fileName, sizeBytes)
                                                }
                                            }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                LaunchedEffect(fileName) {
                                                    try {
                                                        val signed = withContext(Dispatchers.IO) {
                                                            storage.from(Config.SUPABASE_BUCKET)
                                                                .createSignedUrl(
                                                                    fileName,
                                                                    600.seconds
                                                                )
                                                        }
                                                        publicUrl = signed
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }

                                                if (publicUrl != null) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(
                                                            publicUrl
                                                        ),
                                                        contentDescription = fileName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(24.dp),
                                                            color = Color.White
                                                        )
                                                    }
                                                }

                                                Column(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .fillMaxWidth()
                                                        .background(Color.Black.copy(alpha = 0.7f))
                                                        .padding(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        val imageAlreadyDownloaded =
                                                            sizeBytes >= 0 && fileExistsLocallyWithSameSize(
                                                                fileName,
                                                                sizeBytes
                                                            )
                                                        val isFavorite =
                                                            favoriteFiles.contains(fileName)

                                                        val sizeText = when {
                                                            sizeBytes >= 1_000_000_000 -> "Dateigröße: %.2f GB".format(
                                                                sizeBytes / 1_000_000_000.0
                                                            )

                                                            sizeBytes >= 1_000_000 -> "Dateigröße: %.2f MB".format(
                                                                sizeBytes / 1_000_000.0
                                                            )

                                                            else -> "Dateigröße: %.1f KB".format(
                                                                sizeBytes / 1_000.0
                                                            )
                                                        }
                                                        Text(
                                                            text = "$fileName\n $fileDate\n $sizeText",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 9.sp
                                                            ),
                                                            color = Color.White,
                                                            maxLines = 3
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                favoriteFiles = if (isFavorite) {
                                                                    favoriteFiles - fileName
                                                                } else {
                                                                    favoriteFiles + fileName
                                                                }
                                                                FavoriteManager.saveFavorites(
                                                                    context,
                                                                    favoriteFiles
                                                                )
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                                contentDescription = "Favorit",
                                                                tint = if (isFavorite) Color.Yellow else Color.White
                                                            )
                                                        }
                                                        if (!imageAlreadyDownloaded) {
                                                            IconButton(
                                                                onClick = {
                                                                    isDownloading = fileName
                                                                    scope.launch {
                                                                        showDownloadProgress = true
                                                                        try {
                                                                            val data =
                                                                                withContext(
                                                                                    Dispatchers.IO
                                                                                ) {
                                                                                    storage.from(
                                                                                        Config.SUPABASE_BUCKET
                                                                                    )
                                                                                        .downloadAuthenticated(
                                                                                            fileName
                                                                                        )
                                                                                }

                                                                            val dcimDir =
                                                                                Environment.getExternalStoragePublicDirectory(
                                                                                    Environment.DIRECTORY_DCIM
                                                                                )
                                                                            val appFolder =
                                                                                File(
                                                                                    dcimDir,
                                                                                    "Cloud"
                                                                                )
                                                                            if (!appFolder.exists()) {
                                                                                appFolder.mkdirs()
                                                                            }

                                                                            val outputFile = File(
                                                                                appFolder,
                                                                                fileName
                                                                            )

                                                                            withContext(Dispatchers.IO) {
                                                                                FileOutputStream(
                                                                                    outputFile
                                                                                ).use { fos ->
                                                                                    fos.write(data)
                                                                                }
                                                                            }

                                                                            MediaScannerConnection.scanFile(
                                                                                context,
                                                                                arrayOf(outputFile.absolutePath),
                                                                                null,
                                                                                null
                                                                            )

                                                                            Toast.makeText(
                                                                                context,
                                                                                "Bild gespeichert ✅",
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()

                                                                            haptic.performHapticFeedback(
                                                                                HapticFeedbackType.LongPress
                                                                            )
                                                                        } catch (e: Exception) {
                                                                            Toast.makeText(
                                                                                context,
                                                                                "Fehler: ${e.message}",
                                                                                Toast.LENGTH_LONG
                                                                            ).show()
                                                                        } finally {
                                                                            isDownloading = null
                                                                            delay(500)
                                                                            showDownloadProgress =
                                                                                false
                                                                        }
                                                                    }
                                                                },
                                                                enabled = isDownloading != fileName,
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                if (isDownloading == fileName) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(16.dp),
                                                                        color = Color.White
                                                                    )
                                                                } else {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.ArrowDropDown,
                                                                        contentDescription = "Download",
                                                                        tint = Color.Black
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            IconButton(
                                                                onClick = {
                                                                    val localImageFile =
                                                                        fileExistsInDCIM(fileName)!!
                                                                    val fileUri =
                                                                        FileProvider.getUriForFile(
                                                                            context,
                                                                            "${context.packageName}.fileprovider",
                                                                            localImageFile
                                                                        )
                                                                    val intent =
                                                                        Intent(Intent.ACTION_VIEW).apply {
                                                                            setDataAndType(
                                                                                fileUri,
                                                                                "image/*"
                                                                            )
                                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                        }
                                                                    context.startActivity(intent)
                                                                    haptic.performHapticFeedback(
                                                                        HapticFeedbackType.LongPress
                                                                    )
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                                    contentDescription = "Öffnen",
                                                                    tint = Color.White
                                                                )
                                                            }
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    deleteFile(file)
                                                                }
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Löschen",
                                                                tint = Color.Red
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (rowFiles.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(filteredFileList) { file ->
                                val fileName = file.name
                                val fileDate =
                                    file.updatedAt
                                        .replace("T", " ")
                                        .substringBefore(".")
                                        .ifEmpty { "Unbekannt" }
                                val sizeBytes = file.size
                                val showOpenButton =
                                    sizeBytes >= 0 && fileExistsLocallyWithSameSize(
                                        fileName,
                                        sizeBytes
                                    )

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.DarkGray)
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FileIcon(
                                                fileName = fileName,
                                                storage = storage,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .padding(end = 12.dp)
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fileName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 16.sp
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 1
                                                )

                                                Text(
                                                    text = "Hochgeladen: $fileDate",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp
                                                )

                                                val sizeText = when {
                                                    sizeBytes >= 1_000_000_000 -> "%.2f GB".format(
                                                        sizeBytes / 1_000_000_000.0
                                                    )

                                                    sizeBytes >= 1_000_000 -> "%.2f MB".format(
                                                        sizeBytes / 1_000_000.0
                                                    )

                                                    else -> "%.1f KB".format(sizeBytes / 1_000.0)
                                                }

                                                Text(
                                                    text = sizeText,
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }

                                            val isFavorite = favoriteFiles.contains(fileName)
                                            IconButton(
                                                onClick = {
                                                    favoriteFiles = if (isFavorite) {
                                                        favoriteFiles - fileName
                                                    } else {
                                                        favoriteFiles + fileName
                                                    }
                                                    FavoriteManager.saveFavorites(
                                                        context,
                                                        favoriteFiles
                                                    )
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                    contentDescription = "Favorit",
                                                    tint = if (isFavorite) Color.Yellow else Color.White
                                                )
                                            }

                                            if (showOpenButton && !isImageFile(fileName)) {
                                                val localFile =
                                                    getLocalFileWithPath(fileName, sizeBytes)
                                                if (localFile != null) {
                                                    IconButton(onClick = {
                                                        val fileUri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            localFile
                                                        )
                                                        val mimeType =
                                                            context.contentResolver.getType(fileUri)
                                                                ?: getMimeType(fileName)
                                                        val intent =
                                                            Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(fileUri, mimeType)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                        context.startActivity(intent)
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    }) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.OpenInNew,
                                                            contentDescription = "Öffnen",
                                                            tint = Color.White
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.size(32.dp))
                                                }
                                            } else if (isImageFile(fileName) && fileExistsInDCIM(
                                                    fileName
                                                ) != null
                                            ) {
                                                IconButton(onClick = {
                                                    val imageFile = fileExistsInDCIM(fileName)!!
                                                    val fileUri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        imageFile
                                                    )
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(fileUri, "image/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.OpenInNew,
                                                        contentDescription = "Öffnen",
                                                        tint = Color.White
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = {
                                                        isDownloading = fileName
                                                        scope.launch {
                                                            showDownloadProgress = true
                                                            try {
                                                                val allFiles =
                                                                    withContext(Dispatchers.IO) {
                                                                        storage.from(Config.SUPABASE_BUCKET)
                                                                            .list()
                                                                    }

                                                                val chunks = allFiles.filter {
                                                                    it.name.startsWith(fileName) && it.name.contains(
                                                                        ".part"
                                                                    )
                                                                }.sortedBy {
                                                                    val partMatch =
                                                                        Regex("part(\\d+)of").find(
                                                                            it.name
                                                                        )
                                                                    partMatch?.groupValues?.get(1)
                                                                        ?.toIntOrNull() ?: 0
                                                                }
                                                                val targetBaseFolder =
                                                                    if (isImageFile(fileName) || fileName.endsWith(
                                                                            ".mp4",
                                                                            ignoreCase = true
                                                                        )
                                                                    ) {
                                                                        Environment.getExternalStoragePublicDirectory(
                                                                            Environment.DIRECTORY_DCIM
                                                                        )
                                                                    } else {
                                                                        Environment.getExternalStoragePublicDirectory(
                                                                            Environment.DIRECTORY_DOWNLOADS
                                                                        )
                                                                    }

                                                                val appFolder =
                                                                    File(targetBaseFolder, "Cloud")
                                                                if (!appFolder.exists()) {
                                                                    appFolder.mkdirs()
                                                                }

                                                                val cleanFileName =
                                                                    fileName.substringBefore(".part")
                                                                val outputFile =
                                                                    File(appFolder, cleanFileName)

                                                                withContext(Dispatchers.IO) {
                                                                    FileOutputStream(outputFile).use { fos ->
                                                                        if (chunks.isNotEmpty()) {
                                                                            for (chunk in chunks) {
                                                                                val chunkData =
                                                                                    storage.from(
                                                                                        Config.SUPABASE_BUCKET
                                                                                    )
                                                                                        .downloadAuthenticated(
                                                                                            chunk.name
                                                                                        )

                                                                                fos.write(
                                                                                    chunkData
                                                                                )
                                                                            }
                                                                        } else {
                                                                            val downloadedData =
                                                                                storage.from(
                                                                                    Config.SUPABASE_BUCKET
                                                                                )
                                                                                    .downloadAuthenticated(
                                                                                        fileName
                                                                                    )

                                                                            fos.write(downloadedData)
                                                                        }
                                                                    }
                                                                }

                                                                if (isImageFile(fileName) || fileName.endsWith(
                                                                        ".mp4",
                                                                        ignoreCase = true
                                                                    )
                                                                ) {
                                                                    MediaScannerConnection.scanFile(
                                                                        context,
                                                                        arrayOf(outputFile.absolutePath),
                                                                        null,
                                                                        null
                                                                    )
                                                                }

                                                                Toast.makeText(
                                                                    context,
                                                                    "Datei gespeichert ✅",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()

                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                                Toast.makeText(
                                                                    context,
                                                                    "Fehler: ${e.message}",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            } finally {
                                                                isDownloading = null
                                                                delay(500)
                                                                showDownloadProgress = false
                                                            }
                                                        }
                                                    },
                                                    enabled = isDownloading != fileName,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    if (isDownloading == fileName) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            color = Color.White
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Filled.ArrowDropDown,
                                                            contentDescription = "Download",
                                                            tint = Color.Black
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                uploadLauncher.launch(intent)
                            },
                            enabled = !isUploading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Datei auswählen & hochladen",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    if (!isOnline(context)) {
                                        Toast.makeText(
                                            context,
                                            "🚫 Keine Internetverbindung",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        loadFiles()
                                        Toast.makeText(
                                            context,
                                            "Liste aktualisiert ✅",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(0.64f)
                        ) {
                            Text(
                                "🔄 Aktualisieren",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (showUploadProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(50.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            if (showDownloadProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(50.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    fullscreenImageDialogData?.let { (url, name, size) ->
        val imageAlreadyDownloaded = fileExistsLocallyWithSameSize(name, size)
        FullscreenImageDialog(
            imageUrl = url,
            fileName = name,
            isDownloaded = imageAlreadyDownloaded,
            onDismiss = {
                fullscreenImageDialogData = null
            },
            onDownload = {
                isDownloading = name
                scope.launch {
                    showDownloadProgress = true
                    try {
                        val data = withContext(Dispatchers.IO) {
                            storage.from(Config.SUPABASE_BUCKET)
                                .downloadAuthenticated(name)
                        }
                        val dcimDir =
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        val appFolder = File(dcimDir, "Cloud")
                        if (!appFolder.exists()) appFolder.mkdirs()
                        val outputFile = File(appFolder, name)
                        withContext(Dispatchers.IO) {
                            FileOutputStream(outputFile).use { fos -> fos.write(data) }
                        }
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(outputFile.absolutePath),
                            null,
                            null
                        )
                        Toast.makeText(context, "Bild gespeichert ✅", Toast.LENGTH_SHORT).show()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isDownloading = null
                        delay(500)
                        showDownloadProgress = false
                        fullscreenImageDialogData = null
                    }
                }
            },
            onOpenInGallery = {
                val localImageFile = fileExistsInDCIM(name)!!
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localImageFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                fullscreenImageDialogData = null
            }
        )
    }
    if (pendingOtherBucket || showOtherBucket) {
        if (pendingOtherBucket) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = 10000f }
                    .drawWithContent {
                        otherBucketLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(otherBucketLayer)
                    }
            ) {
                OtherBucketViewer(onBackPressed = {
                    showOtherBucket = false
                })
            }

            LaunchedEffect(Unit) {
                repeat(5) { withFrameNanos { } }

                val captured = otherBucketLayer.toImageBitmap()
                otherBucketBitmap = captured

                snapshotFlow { otherBucketBitmap }
                    .filter { it != null }
                    .first()

                showOtherBucket = true
                otherBucketScale.snapTo(0.05f)
                otherBucketAlpha.snapTo(1f)

                otherBucketScale.animateTo(
                    1f,
                    tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )

                pendingOtherBucket = false
                shouldshow = true

                delay(80)
                otherBucketAlpha.animateTo(0f, tween(durationMillis = 200))
                otherBucketScale.snapTo(0f)
                otherBucketBitmap = null
            }

            if (otherBucketBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = otherBucketScale.value
                            scaleY = otherBucketScale.value
                            alpha = otherBucketAlpha.value
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                ) {
                    Image(
                        bitmap = otherBucketBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }

        if (shouldshow) {
            OtherBucketViewer(
                onBackPressed = {
                    shouldshow = false
                }
            )
        }
    }
}

private fun getDirSize(dir: File): Long {
    var size: Long = 0
    if (dir.isDirectory) {
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
    } else {
        size = dir.length()
    }
    return size
}

fun showBatteryInfo(context: Context) {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    if (batteryIntent != null) {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = (level / scale.toFloat() * 100).toInt()

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Netzteil (AC)"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Nicht angeschlossen"
        }

        val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000.0

        val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Gut"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Überhitzt"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Defekt"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Überspannung"
            BatteryManager.BATTERY_HEALTH_COLD -> "Zu kalt"
            else -> "Unbekannt"
        }

        val channelId = "battery_info_channel"
        val channel = NotificationChannel(
            channelId,
            "Batterie-Informationen",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Zeigt detaillierte Batterie-Informationen an"
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔋 Batterie-Info")
            .setContentText("Ladezustand: $percentage%")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        """
                    Ladezustand: $percentage%
                    Status: ${if (isCharging) "🔌 Lädt" else "🔋 Entlädt"}
                    Ladetyp: $chargingType
                    Temperatur: $temperature°C
                    Spannung: $voltage V
                    Gesundheit: $healthText
                """.trimIndent()
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(cms(), builder.build())
        } else {
            Toast.makeText(
                context,
                "Ladezustand: $percentage% | Temp: $temperature°C",
                Toast.LENGTH_LONG
            ).show()
        }
    } else {
        Toast.makeText(context, "Batterie-Info nicht verfügbar", Toast.LENGTH_SHORT).show()
    }
}


const val PREFS_NAME = "cloud_app_prefs"

private const val KEY_LAST_URL = "last_browser_url"

fun saveLastUrl(context: Context, url: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putString(KEY_LAST_URL, url)
        }
}

fun loadLastUrl(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_LAST_URL, "https://www.google.com") ?: "https://www.google.com"
}

object WebViewCookieBackup {

    fun saveCookies(context: Context, url: String) {
        val cookies = CookieManager.getInstance().getCookie(url) ?: return
        context.getSharedPreferences("webview_cookies", Context.MODE_PRIVATE)
            .edit { putString("cookies_${url.hashCode()}", cookies) }
    }

    fun restoreCookies(context: Context, url: String) {
        val cookies = context.getSharedPreferences("webview_cookies", Context.MODE_PRIVATE)
            .getString("cookies_${url.hashCode()}", null) ?: return
        val cm = CookieManager.getInstance()
        cookies.split(";").forEach { cookie ->
            cm.setCookie(url, cookie.trim())
        }
        cm.flush()
    }
}

@Composable
fun GoodNightScreen(ai: String) {
    val context = LocalContext.current
    MediaAnalyticsManager.init(context)
    var showStats by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val spaceMono = FontFamily(
            Font(R.font.smb, FontWeight.Normal),
            Font(R.font.smb, FontWeight.Bold)
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(0.8f)) {
                Row {
                    Text(
                        "Guck wie du heute abgeschnitten hast:",
                        color = Color.White,
                        fontFamily = spaceMono
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .clip(RoundedCornerShape(20))
                        .background(Cloud)
                        .padding(5.dp)
                        .clickable(onClick = {
                            showStats = true
                        })
                ) {
                    Text(
                        "$ai...",
                        fontFamily = spaceMono,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
    if (showStats) {
        AiResponseHistorySheet(context = context, onDismiss = { showStats = false })
    }
}