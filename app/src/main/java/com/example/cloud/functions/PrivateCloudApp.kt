@file:Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.example.cloud.functions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.webkit.WebView
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ArrowBack
import android.webkit.WebViewClient
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import com.example.cloud.AesEncryption
import com.example.cloud.objects.FavoriteManager
import com.example.cloud.SupabaseConfig
import com.example.cloud.database.WhatsAppMessage
import com.example.cloud.service.WhatsAppNotificationListener
import com.example.cloud.ui.theme.gruen
import com.example.cloud.ui.theme.hellgruen
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.collections.chunked
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import android.app.NotificationChannel
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.pow

// Enum für Menü-Einträge (einfach erweiterbar)
enum class MenuItem(val title: String, val icon: String) {
    PRIVATE_CLOUD("Private Cloud", "☁️"),
    OTHER_BUCKET("Other Bucket", "📂"),
    WHATSAPP("WhatsApp", "💬"),
    BROWSER("Browser", "🌐"),
    QUICK("Schnellzugriff", "⚡")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateCloudApp(storage: Storage) {
    var isFullScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var selectedMenuItem by remember { mutableStateOf(loadLastMenuItem(context)) }
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed,
        confirmStateChange = { futureValue ->
            // Wenn Vollbild im Lockscreen: Drawer NICHT öffnen
            if (isFullScreen && selectedMenuItem == MenuItem.BROWSER) {
                futureValue == DrawerValue.Closed
            } else {
                true
            }
        }
    )
    val scope = rememberCoroutineScope()
    var webViewUrl by remember { mutableStateOf("https://www.google.com") }
    var webViewState by remember { mutableStateOf<android.webkit.WebView?>(null) }

    if (isFullScreen) {
        // Nur den Inhalt anzeigen, OHNE Drawer
        Box(modifier = Modifier.fillMaxSize()) {
            var messages by remember { mutableStateOf<List<WhatsAppMessage>>(emptyList()) }
            var webViewUrl by remember { mutableStateOf("https://www.google.com") }
            var webViewState by remember { mutableStateOf<android.webkit.WebView?>(null) }
            scope.launch {
                messages = WhatsAppNotificationListener.getMessages()
            }
            when (selectedMenuItem) {
                MenuItem.PRIVATE_CLOUD -> MainCloudScreen(storage = storage)
                MenuItem.BROWSER -> BrowserTabContent(
                    url = webViewUrl,
                    onUrlChange = { webViewUrl = it },
                    onEnterFullScreen = { isFullScreen = true },
                    webViewState = webViewState
                )

                MenuItem.OTHER_BUCKET -> OtherBucketViewer(
                    storage = storage,
                    onBackPressed = {
                        selectedMenuItem = MenuItem.PRIVATE_CLOUD
                        saveLastMenuItem(context, MenuItem.PRIVATE_CLOUD)
                    }
                )

                MenuItem.WHATSAPP -> WhatsAppTabContent(messages)
                MenuItem.QUICK -> QuickSettingsTabContent()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color.DarkGray
                ) {
                    // App-Titel
                    Text(
                        text = "Cloud App",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Divider(color = Color.Gray, thickness = 1.dp)

                    Spacer(Modifier.height(8.dp))

                    // Menü-Einträge (automatisch aus Enum generiert)
                    MenuItem.entries.forEach { item ->
                        NavigationDrawerItem(
                            icon = {
                                Text(
                                    text = item.icon,
                                    fontSize = 24.sp
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    color = Color.White
                                )
                            },
                            selected = selectedMenuItem == item,
                            onClick = {
                                selectedMenuItem = item
                                saveLastMenuItem(context, item)
                                scope.launch {
                                    drawerState.close()
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFF4CAF50),
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = Color.White,
                                unselectedTextColor = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = selectedMenuItem.title,
                                color = Color.White
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menü öffnen",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF2A2A2A)
                        )
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    var messages by remember { mutableStateOf<List<WhatsAppMessage>>(emptyList()) }
                    var webViewUrl by remember { mutableStateOf("https://www.google.com") }
                    var webViewState by remember { mutableStateOf<android.webkit.WebView?>(null) }
                    scope.launch {
                        messages = WhatsAppNotificationListener.getMessages()
                    }
                    when (selectedMenuItem) {
                        MenuItem.PRIVATE_CLOUD -> MainCloudScreen(storage)
                        MenuItem.WHATSAPP -> WhatsAppTabContent(messages)
                        MenuItem.BROWSER -> BrowserTabContent(
                            url = webViewUrl,
                            onUrlChange = { webViewUrl = it },
                            onEnterFullScreen = { isFullScreen = true },
                            webViewState = webViewState
                        )

                        MenuItem.QUICK -> QuickSettingsTabContent()
                        MenuItem.OTHER_BUCKET -> OtherBucketViewer(
                            storage = storage,
                            onBackPressed = {
                                selectedMenuItem = MenuItem.PRIVATE_CLOUD
                                saveLastMenuItem(context, MenuItem.PRIVATE_CLOUD)
                            }
                        )
                    }

                }
            }
        }
    }


    if (isFullScreen) {
        Box(modifier = Modifier.fillMaxSize()) {

            var canGoBack by remember { mutableStateOf(false) }
            var webViewState by remember { mutableStateOf<WebView?>(null) }

            // WebViewClient, der canGoBack aktualisiert
            val webViewClient = remember {
                object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        canGoBack = view?.canGoBack() == true
                    }
                }
            }

            // WebView
            AndroidView(
                factory = { ctx ->
                    val webView = WebView(ctx).apply {
                        webChromeClient = WebChromeClient()
                        this.webViewClient = webViewClient
                        settings.apply {
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        loadUrl("https://example.com")
                    }

                    // CookieManager konfigurieren
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            setAcceptThirdPartyCookies(webView, true)
                        }
                    }

                    webView
                },
                update = { webView ->
                    webViewState = webView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Zurück-Button (klein, oben links)
            IconButton(
                onClick = {
                    webViewState?.goBack()
                },
                modifier = Modifier
                    .padding(8.dp)
                    .size(64.dp)
                    .align(Alignment.TopStart),
                enabled = canGoBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.Blue
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Blue
                )
            }

            // Hardware-Back-Handler
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

// Der Original-Cloud-Screen (ohne Navigation)
@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun MainCloudScreen(storage: Storage) {
    var fileList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("Alle") }
    var sortOption by remember { mutableStateOf("A-Z") }
    var showUploadProgress by remember { mutableStateOf(false) }
    var showDownloadProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var favoriteFiles by remember {
        mutableStateOf(FavoriteManager.loadFavorites(context))
    }
    var expanded by remember { mutableStateOf(false) }
    var fullscreenImageDialogData by remember { mutableStateOf<Triple<String?, String, Long>?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    suspend fun loadFiles() {
        try {
            val files = withContext(Dispatchers.IO) {
                storage.from(SupabaseConfig.SUPABASE_BUCKET).list()
            }

            fileList = files
                .filter { it.name != ".emptyFolderPlaceholder" }
                .map { "${it.name}|${it.updatedAt}|${it.metadata?.get("size") ?: 0}" }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Laden: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    suspend fun deleteFile(fileWithMetadata: String) {
        try {
            val fileName = fileWithMetadata.substringBefore("|")
            withContext(Dispatchers.IO) {
                storage.from(SupabaseConfig.SUPABASE_BUCKET).delete(fileName)
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
                        val inputStream = context.contentResolver.openInputStream(uri)!!
                        val rawData = inputStream.readBytes()
                        inputStream.close()

                        val dataToUpload = if (isImageFile(fileName)) {
                            rawData
                        } else {
                            AesEncryption.encrypt(rawData)
                        }

                        withContext(Dispatchers.IO) {
                            storage.from(SupabaseConfig.SUPABASE_BUCKET)
                                .upload(fileName, dataToUpload)
                        }
                    }
                    Toast.makeText(
                        context,
                        "✅ Hochgeladen!",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadFiles()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(
                        context,
                        "Fehler beim Upload: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
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

    val gradient = Brush.linearGradient(
        colors = listOf(hellgruen, gruen, hellgruen),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp)
            .background(gradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Alle", "Dateien", "Bilder", "Favoriten").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = {
                            selectedFilter = filter
                        },
                        label = {
                            Text(
                                filter,
                                color = if (selectedFilter == filter) Color.Black else Color.White
                            )
                        })
                }
            }
            val preFilteredFileList = remember(fileList, selectedFilter) {
                when (selectedFilter) {
                    "Bilder" -> fileList.filter { isImageFile(it.substringBefore("|")) }
                    "Dateien" -> fileList.filter { !isImageFile(it.substringBefore("|")) }
                    else -> fileList
                }
            }

            if (preFilteredFileList.isEmpty()) {
                Text(
                    "Keine ${if (selectedFilter == "Alle") "Dateien" else selectedFilter} vorhanden",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
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
                                .menuAnchor()
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
                        val fileName = file.substringBefore("|")
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
                        "A-Z" -> filtered.sortedBy { it.substringBefore("|").lowercase() }
                        "Größte Datei" -> filtered.sortedByDescending {
                            it.split("|").getOrNull(2)?.toLongOrNull() ?: 0L
                        }

                        "Zuletzt hochgeladen" -> filtered.sortedByDescending {
                            it.split("|").getOrNull(1) ?: ""
                        }

                        else -> filtered
                    }
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
                                    val parts = file.split("|")
                                    val fileName = parts.getOrNull(0) ?: "Unbekannt"
                                    val fileDate =
                                        parts.getOrNull(1)?.replace("T", " ")
                                            ?.substringBefore(".")
                                            ?: "Unbekannt"
                                    val filesize =
                                        parts.getOrNull(2)?.replace("T", " ")
                                            ?.substringBefore(".")
                                            ?: "Unbekannt"
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
                                                val sizeBytes = filesize.toLongOrNull() ?: -1L
                                                fullscreenImageDialogData =
                                                    Triple(publicUrl, fileName, sizeBytes)
                                            }
                                        }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LaunchedEffect(fileName) {
                                                try {
                                                    val signed = withContext(Dispatchers.IO) {
                                                        storage.from(SupabaseConfig.SUPABASE_BUCKET)
                                                            .createSignedUrl(fileName, 600.seconds)
                                                    }
                                                    publicUrl = signed
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }

                                            if (publicUrl != null) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(publicUrl),
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
                                                    val remoteSize =
                                                        filesize.toLongOrNull() ?: -1L
                                                    val imageAlreadyDownloaded =
                                                        remoteSize >= 0 && fileExistsLocallyWithSameSize(
                                                            fileName,
                                                            remoteSize
                                                        )
                                                    val isFavorite =
                                                        favoriteFiles.contains(fileName)


                                                    val sizeBytes =
                                                        filesize.toLongOrNull() ?: 0L
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
                                                                isDownloading = file
                                                                scope.launch {
                                                                    showDownloadProgress = true
                                                                    try {
                                                                        val data =
                                                                            withContext(Dispatchers.IO) {
                                                                                storage.from(
                                                                                    SupabaseConfig.SUPABASE_BUCKET
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
                                                                            File(dcimDir, "Cloud")
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
                                                                        val mediaScanIntent =
                                                                            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                                                        mediaScanIntent.data =
                                                                            Uri.fromFile(outputFile)
                                                                        context.sendBroadcast(
                                                                            mediaScanIntent
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
                                                                        showDownloadProgress = false
                                                                    }
                                                                }
                                                            },
                                                            enabled = isDownloading != file,
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            if (isDownloading == file) {
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
                            val parts = file.split("|")
                            val fileName = parts.getOrNull(0) ?: "Unbekannt"
                            val fileDate =
                                parts.getOrNull(1)?.replace("T", " ")?.substringBefore(".")
                                    ?: "Unbekannt"
                            val filesize =
                                parts.getOrNull(2)?.replace("T", " ")?.substringBefore(".")
                                    ?: "Unbekannt"
                            val remoteSize = filesize.toLongOrNull() ?: -1L
                            val showOpenButton =
                                remoteSize >= 0 && fileExistsLocallyWithSameSize(
                                    fileName,
                                    remoteSize
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

                                            val sizeBytes = filesize.toLongOrNull() ?: 0L
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
                                                getLocalFileWithPath(fileName, remoteSize)
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
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                                    isDownloading = file
                                                    scope.launch {
                                                        showDownloadProgress = true
                                                        try {
                                                            val data = withContext(Dispatchers.IO) {
                                                                storage.from(SupabaseConfig.SUPABASE_BUCKET)
                                                                    .downloadAuthenticated(fileName)
                                                            }
                                                            val dcimDir =
                                                                Environment.getExternalStoragePublicDirectory(
                                                                    Environment.DIRECTORY_DCIM
                                                                )
                                                            val appFolder = File(dcimDir, "Cloud")
                                                            if (!appFolder.exists()) {
                                                                appFolder.mkdirs()
                                                            }
                                                            val outputFile =
                                                                File(appFolder, fileName)

                                                            withContext(Dispatchers.IO) {
                                                                FileOutputStream(outputFile).use { fos ->
                                                                    fos.write(data)
                                                                }
                                                            }
                                                            val mediaScanIntent =
                                                                Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                                            mediaScanIntent.data =
                                                                Uri.fromFile(outputFile)
                                                            context.sendBroadcast(mediaScanIntent)
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
                                                            showDownloadProgress = false
                                                        }
                                                    }
                                                },
                                                enabled = isDownloading != file,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                if (isDownloading == file) {
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
                    horizontalArrangement = Arrangement.SpaceEvenly,
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
                        enabled = !isUploading
                    ) {
                        Text("Datei auswählen & hochladen", fontSize = 12.sp)
                    }

                    Button(onClick = {
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
                    }) {
                        Text("🔄 Aktualisieren", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        "content://downloads/my_downloads".toUri(),
                                        "vnd.android.document/directory"
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val downloadsUri =
                                        "content://com.android.externalstorage.documents/document/primary:Download".toUri()
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            downloadsUri,
                                            "vnd.android.document/directory"
                                        )
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Downloads-Ordner konnte nicht geöffnet werden",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
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
                            storage.from(SupabaseConfig.SUPABASE_BUCKET)
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
                        context.sendBroadcast(
                            Intent(
                                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.fromFile(outputFile)
                            )
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
}

// ========== LOCKSCREEN FRAME SCREEN (ohne Lockscreen-Funktionalität) ==========
@Composable
fun LockscreenFrameScreen(
    onIsFullScreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf<List<WhatsAppMessage>>(emptyList()) }

    var isFullScreen by remember { mutableStateOf(false) }
    var webViewUrl by remember { mutableStateOf("https://www.google.com") }
    var webViewState by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // Broadcast Receiver für neue Nachrichten
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch {
                    messages = WhatsAppNotificationListener.getMessages()
                }
            }
        }

        val filter = IntentFilter("WHATSAPP_MESSAGE_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }

        // Initial laden
        scope.launch {
            messages = WhatsAppNotificationListener.getMessages()

        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF2A2A2A)),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    LaunchedEffect(isFullScreen) {
        onIsFullScreenChange(isFullScreen)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Tab-Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { selectedTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) Color(0xFF4CAF50) else Color(
                            0xFF666666
                        )
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("💬 WhatsApp")
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = { selectedTab = 1 },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) Color(0xFF4CAF50) else Color(
                            0xFF666666
                        )
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🌐 Browser")
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = { selectedTab = 2 },

                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 2) Color(0xFF4CAF50) else Color(
                            0xFF666666
                        )
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("⚡ Quick")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFF2A2A2A)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    when (selectedTab) {
                        0 -> WhatsAppTabContent(messages = messages)
                        1 -> BrowserTabContent(
                            url = webViewUrl,
                            onUrlChange = { webViewUrl = it },
                            onEnterFullScreen = { isFullScreen = true },
                            webViewState = webViewState
                        )

                        2 -> QuickSettingsTabContent()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            fun isNotificationListenerEnabled(context: Context): Boolean {
                val enabledListeners = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
                val packageName = context.packageName
                return enabledListeners?.contains(packageName) == true
            }

            var hasPermission by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

            if (!hasPermission) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📱 Benachrichtigungs-Berechtigung erteilen")
                }
            }
        }
    }
}

@Composable
fun WhatsAppTabContent(messages: List<WhatsAppMessage>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (messages.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Color(0xFF2A2A2A)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "📱", fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Keine WhatsApp-Nachrichten",
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "💡 Tipp: Erlaube Benachrichtigungen und\nschreib dir selbst eine Testnachricht!",
                fontSize = 12.sp,
                color = Color.LightGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                MessageCard(message = message, context = context, scope = scope)
            }
        }
    }
}

@Composable
fun MessageCard(message: WhatsAppMessage, context: Context, scope: CoroutineScope) {
    var replyText by remember { mutableStateOf("") }
    var showAllMessagesDialog by remember { mutableStateOf(false) }
    var allMessagesText by remember { mutableStateOf("") }

    // AlertDialog für alle Nachrichten
    if (showAllMessagesDialog) {
        AlertDialog(
            onDismissRequest = { showAllMessagesDialog = false },
            title = { Text("Alle Nachrichten von ${message.sender}") },
            text = {
                LazyColumn {
                    item {
                        Text(allMessagesText, color = Color.White)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAllMessagesDialog = false }) {
                    Text("OK")
                }
            },
            containerColor = Color(0xFF2A2A2A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Sender
            Text(
                text = message.sender,
                fontSize = 18.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )

            // Message
            Text(
                text = message.text,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Reply Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Antworten...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF333333),
                        unfocusedContainerColor = Color(0xFF333333)
                    ),
                    singleLine = true
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            scope.launch {
                                val success = WhatsAppNotificationListener.sendReply(
                                    message.sender,
                                    replyText,
                                    context
                                )
                                if (success) {
                                    replyText = ""
                                }
                            }
                        }
                    },
                    enabled = replyText.isNotBlank()
                ) {
                    Text("📤")
                }
            }

            // Button für alle Nachrichten
            TextButton(
                onClick = {
                    scope.launch {
                        val allMessages =
                            WhatsAppNotificationListener.getMessagesBySender(message.sender)
                        allMessagesText = allMessages.joinToString("\n\n") {
                            val time = java.text.SimpleDateFormat(
                                "dd.MM. HH:mm",
                                java.util.Locale.getDefault()
                            )
                                .format(java.util.Date(it.timestamp))
                            "[$time] ${it.sender}: ${it.text}"
                        }
                        showAllMessagesDialog = true
                    }
                }
            ) {
                Text("Alle Nachrichten anzeigen", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}


@Composable
fun BrowserTabContent(
    url: String,
    onUrlChange: (String) -> Unit,
    onEnterFullScreen: () -> Unit,
    webViewState: android.webkit.WebView?,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, // zentriert alle Kinder horizontal
        verticalArrangement = Arrangement.Center // optional: zentriert auch vertikal im gesamten Column
    ) {
        TextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp), // optional: feste Höhe für konsistentes Aussehen
            singleLine = true,
            placeholder = { Text("URL hier eingeben", color = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedContainerColor = Color(0xFF333333),
                unfocusedContainerColor = Color(0xFF333333),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    val newUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                        url
                    } else {
                        "https://$url"
                    }
                    webViewState?.loadUrl(newUrl)
                    keyboardController?.hide()
                }
            )
        )

        Spacer(modifier = Modifier.height(16.dp)) // Abstand zwischen Input und Button

        Button(
            onClick = {
                val newUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }
                webViewState?.loadUrl(newUrl)
                keyboardController?.hide()
                onEnterFullScreen()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth() // oder .defaultMinSize(minWidth = ...) je nach Wunsch
                .height(56.dp)
        ) {
            Text("Öffnen", fontSize = 20.sp)
        }
    }
}


@Composable
fun QuickSettingsTabContent() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF2A2A2A))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Zeile 2: Medien & Display
            item {
                QuickSettingRow(
                    listOf(
                        "Netzwerk\nInfos" to { showNetworkInfo(context) },
                        "Sensoren\nInfos" to { showSensorsInfo(context) },
                        "Display\nInfos" to { showDisplayInfo(context) }
                    )
                )
            }

            // Zeile 3: Apps
            item {
                QuickSettingRow(
                    listOf(
                        "⚙️\nEinstellungen" to { openSettings(context) },
                        "📊\nDaten\nnutzung" to { openDataUsage(context) },
                        "💾\nSpeicher\nInfo" to { showDetailedStorageInfo(context) }
                    )
                )
            }

            // Zeile 4: Batterie (OHNE ROOT)
            item {
                QuickSettingRow(
                    listOf(
                        "🔋\nBatterie\nInfo" to { showBatteryInfo(context) },
                        "💾\nSpeicher" to { openStorageSettings(context) },
                        "⚡\nBatterie\nEinstellungen" to { openBatterySettings(context) }
                    )
                )
            }

            // Zeile 6: Erweitert
            item {
                QuickSettingRow(
                    listOf(
                        "📱\nGeräte\nInfo" to { showDeviceInfo(context) },
                        "🔨\nEntwickler" to { openDeveloperOptions(context) }
                    )
                )
            }
        }
    }
}

@Composable
fun QuickSettingRow(buttons: List<Pair<String, () -> Unit>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        buttons.forEach { (label, action) ->
            Button(
                onClick = action,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF444444)
                )
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

fun showSensorsInfo(context: Context) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)

    if (sensorList.isEmpty()) {
        showSensorNotification(context, "Keine Sensoren gefunden", 1040)
        return
    }

    val info = StringBuilder()
    info.append("📱 **Gefundene Sensoren**: ${sensorList.size}\n\n")

    // Gruppiert nach Typ für bessere Lesbarkeit
    sensorList.forEachIndexed { index, sensor ->
        val typeStr = getSensorTypeString(sensor.type)
        val vendor = sensor.vendor.ifEmpty { "N/A" }
        val version = sensor.version
        val resolution = sensor.resolution
        val maxRange = sensor.maximumRange
        val power = sensor.power // mA
        val minDelay =
            if (sensor.minDelay > 0) "${sensor.minDelay / 1000} ms" else "kein Livestream"

        info.append("[$index] **${sensor.name}**\n")
        info.append("   Typ: $typeStr\n")
        info.append("   Hersteller: $vendor\n")
        info.append("   Version: $version\n")
        info.append("   Reichweite: ±$maxRange\n")
        info.append("   Auflösung: $resolution\n")
        info.append("   Min. Verzögerung: $minDelay\n")
        info.append("   Stromverbrauch: ${String.format("%.2f", power)} mA\n\n")
    }

    info.append("ℹ️ Hinweis: Werte sind statisch. Keine Live-Daten.")

    showSensorNotification(context, info.toString(), 1040)
}

// Hilfsfunktion: Typ in lesbaren String umwandeln
private fun getSensorTypeString(type: Int): String {
    return when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Beschleunigungssensor"
        Sensor.TYPE_GYROSCOPE -> "Gyroskop"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetfeld (Kompass)"
        Sensor.TYPE_LIGHT -> "Umgebungslicht"
        Sensor.TYPE_PROXIMITY -> "Näherungssensor"
        Sensor.TYPE_PRESSURE -> "Luftdruck (Barometer)"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotationsvektor"
        Sensor.TYPE_GRAVITY -> "Schwerkraft"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Lineare Beschleunigung"
        Sensor.TYPE_ORIENTATION -> "Orientierung (veraltet)"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Umgebungstemperatur"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Luftfeuchtigkeit"
        Sensor.TYPE_HEART_RATE -> "Herzfrequenz"
        Sensor.TYPE_STEP_DETECTOR -> "Schritterkennung"
        Sensor.TYPE_STEP_COUNTER -> "Schrittzähler"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetischer Rotationsvektor"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Spiel-Rotationsvektor"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Bedeutende Bewegung"
        Sensor.TYPE_HINGE_ANGLE -> "Klappwinkel (Foldables)"
        Sensor.TYPE_POSE_6DOF -> "6DOF Pose"
        Sensor.TYPE_MOTION_DETECT -> "Bewegungserkennung"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Unkalibriertes Magnetfeld"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Unkalibrierte Beschleunigung"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Unkalibriertes Gyroskop"
        Sensor.TYPE_HEART_BEAT -> "Herzschlag (Rhythmus)"
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "KörpereRKennung (Low Latency)"
        Sensor.TYPE_ACCELEROMETER_LIMITED_AXES -> "Beschleunigung (limitierte Achsen)"
        Sensor.TYPE_GYROSCOPE_LIMITED_AXES -> "Gyroskop (limitierte Achsen)"
        else -> "Unbekannt ($type)"
    }
}

// Einheitliche Benachrichtigung
private fun showSensorNotification(context: Context, content: String, notificationId: Int) {
    val channelId = "sensors_info_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Sensor-Informationen",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Listet alle verfügbaren Sensoren auf"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("📡 Sensoren-Info")
        .setContentText("Anzahl und Details aller Sensoren")
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    } else {
        // Fallback: Anzahl + erstes Beispiel
        val lines = content.lines()
        val preview = lines.take(4).joinToString("\n")
        Toast.makeText(context, "Sensoren:\n$preview", Toast.LENGTH_LONG).show()
    }
}

@SuppressLint("NewApi")
fun showDisplayInfo(context: Context) {
    val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display ?: windowManager.defaultDisplay
    } else {
        windowManager.defaultDisplay
    }

    val metrics = DisplayMetrics()
    display.getRealMetrics(metrics) // inkl. Systemleisten

    val usableMetrics = DisplayMetrics()
    display.getMetrics(usableMetrics) // nutzbarer Bereich

    val info = StringBuilder()

    // === 1. Physikalische Auflösung (echte Pixel) ===
    val realWidth = metrics.widthPixels
    val realHeight = metrics.heightPixels
    info.append("📏 Phys. Auflösung: ${realWidth} × ${realHeight} px\n")

    // === 2. Nutzbare Auflösung (ohne Systemleisten) ===
    val usableWidth = usableMetrics.widthPixels
    val usableHeight = usableMetrics.heightPixels
    if (usableWidth != realWidth || usableHeight != realHeight) {
        info.append("📦 Nutzbare Fläche: ${usableWidth} × ${usableHeight} px\n")
    }

    // === 3. Dichte (dpi) ===
    val densityDpi = metrics.densityDpi
    val densityStr = when {
        densityDpi <= 120 -> "ldpi (120 dpi)"
        densityDpi <= 160 -> "mdpi (160 dpi)"
        densityDpi <= 240 -> "hdpi (240 dpi)"
        densityDpi <= 320 -> "xhdpi (320 dpi)"
        densityDpi <= 480 -> "xxhdpi (480 dpi)"
        else -> "xxxhdpi (≥ ${densityDpi} dpi)"
    }
    info.append("🔍 Dichte: $densityDpi dpi ($densityStr)\n")

    // === 4. Bildschirmgröße (Zoll, berechnet) ===
    try {
        val widthInches = realWidth / metrics.xdpi.toDouble()
        val heightInches = realHeight / metrics.ydpi.toDouble()
        val diagonalInches =
            kotlin.math.sqrt(widthInches * widthInches + heightInches * heightInches)
        info.append("📐 Bildschirmgröße: ${String.format("%.1f", diagonalInches)} Zoll\n")
    } catch (e: Exception) {
        info.append("📐 Bildschirmgröße: N/A\n")
    }

    // === 5. Bildwiederholfrequenz (Refresh Rate) – ab Android 11 ===
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val refreshRate = display.refreshRate
            info.append("🔄 Refresh Rate: ${String.format("%.1f", refreshRate)} Hz\n")
        } catch (e: Exception) {
            info.append("🔄 Refresh Rate: N/A\n")
        }
    } else {
        // Alternative für ältere Versionen (ungenau, aber möglich)
        info.append("🔄 Refresh Rate: ≤ 60 Hz (Android < 11)\n")
    }

    // === 6. HDR-Unterstützung (optional) ===
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val supportsHdr =
            displayManager.getDisplay(display.displayId)?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        info.append("🖼️ HDR: ${if (supportsHdr) "Ja" else "Nein"}\n")
    }

    // === 7. Ausrichtung ===
    val rotation = display.rotation
    val orientationStr = when (rotation ?: android.view.Surface.ROTATION_0) {
        android.view.Surface.ROTATION_0 -> "Hochformat (0°)"
        android.view.Surface.ROTATION_90 -> "Querformat (90°)"
        android.view.Surface.ROTATION_180 -> "Hochformat (180°, umgekehrt)"
        android.view.Surface.ROTATION_270 -> "Querformat (270°, umgekehrt)"
        else -> "Unbekannt"
    }
    info.append("🧭 Ausrichtung: $orientationStr\n")

    // === Notification ===
    val channelId = "display_info_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Display-Informationen",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt technische Display-Daten an"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_gallery)
        .setContentTitle("🖥️ Display-Info")
        .setContentText("Auflösung, Dichte, Größe, Refresh Rate")
        .setStyle(NotificationCompat.BigTextStyle().bigText(info.toString()))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(1030, builder.build())
    } else {
        // Fallback: Zeige wichtige Werte als Toast
        val fallback = "Auflösung: ${realWidth}×${realHeight}\n" +
                "Dichte: ${densityDpi} dpi\n" +
                "Größe: ${
                    String.format(
                        "%.1f",
                        kotlin.math.sqrt(
                            (realWidth / metrics.xdpi).pow(2) + (realHeight / metrics.ydpi).pow(2)
                        )
                    )
                }\""
        Toast.makeText(context, fallback, Toast.LENGTH_LONG).show()
    }
}

@SuppressLint("MissingPermission") // Berechtigungen werden zur Laufzeit geprüft
fun showNetworkInfo(context: Context) {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val info = StringBuilder()

    // === 1. Aktives Netzwerk ===
    val activeNetwork = connectivityManager.activeNetwork
    val networkCapabilities = if (activeNetwork != null) {
        connectivityManager.getNetworkCapabilities(activeNetwork)
    } else null

    if (networkCapabilities == null) {
        info.append("🌐 Keine aktive Netzwerkverbindung\n\n")
    } else {
        val transport = when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Unbekannt"
        }

        val isInternet =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        info.append("📡 Aktive Verbindung: $transport\n")
        info.append("✅ Internet verfügbar: ${if (isInternet && isValidated) "Ja" else "Nein (kein Zugriff)"}\n\n")

        // === 2. WLAN-Details (wenn WLAN) ===
        if (transport == "WLAN") {
            var ssid = "<unbekannt>"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Ab Android 11: Zugriff auf SSID nur mit Location-Berechtigung
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val wifiInfo = wifiManager.connectionInfo
                    ssid = wifiInfo.ssid.trim().removeSurrounding("\"")
                } else {
                    ssid = "<Standortberechtigung fehlt>"
                }
            } else {
                // Unter Android 11: SSID oft lesbar ohne Location (je nach Hersteller)
                try {
                    val wifiInfo = wifiManager.connectionInfo
                    ssid = wifiInfo.ssid.trim().removeSurrounding("\"")
                } catch (e: Exception) {
                    ssid = "<nicht lesbar>"
                }
            }
            info.append("📶 WLAN-Name (SSID): $ssid\n")

            // Signalstärke (RSSI → dBm)
            val rssi = wifiManager.connectionInfo.rssi
            val level = WifiManager.calculateSignalLevel(rssi, 5) // 0–4
            val bars = "▂▄▆█".substring(0, level.coerceIn(0, 4))
            info.append("📡 Signalstärke: $rssi dBm ($bars)\n\n")
        }

        // === 3. Mobilfunk-Details (wenn Mobil) ===
        if (transport == "Mobilfunk") {
            try {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val networkType = when (telephonyManager.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                    else -> "Mobil (${telephonyManager.dataNetworkType})"
                }
                info.append("📶 Mobilfunktyp: $networkType\n")
                // Signalstärke bei Mobil ist komplex → optional, hier weggelassen für Stabilität
            } catch (e: Exception) {
                info.append("📶 Mobilfunktyp: N/A\n")
            }
            info.append("\n")
        }
    }

    // === 4. Lokale IP-Adresse ===
    try {
        var localIp = "N/A"
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress?.contains(":") == false) {
                    localIp = inetAddress.hostAddress
                    break
                }
            }
            if (localIp != "N/A") break
        }
        info.append("🏠 Lokale IP: $localIp\n")
    } catch (e: Exception) {
        info.append("🏠 Lokale IP: N/A\n")
    }

    // === 5. Öffentliche IP (asynchron, da Netzwerkaufruf) ===
    info.append("🌍 Öffentliche IP: Wird abgerufen…\n")

    // Benachrichtigung VOR async-IP-Abruf anzeigen
    showNetworkNotificationNow(context, info.toString())

    // Öffentliche IP im Hintergrund laden
    Executors.newSingleThreadExecutor().execute {
        var publicIp = "N/A"
        try {
            val url = URL("https://api.ipify.org")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            publicIp = reader.readLine() ?: "Fehler"
            reader.close()
            conn.disconnect()
        } catch (e: Exception) {
            publicIp = "Offline / Timeout"
        }

        // Benachrichtigung mit aktualisierter öffentlicher IP
        val updatedInfo = info.toString()
            .replace("🌍 Öffentliche IP: Wird abgerufen…", "🌍 Öffentliche IP: $publicIp")
        showNetworkNotificationNow(context, updatedInfo, final = true)
    }
}

// Hilfsfunktion: Sofortige Benachrichtigung (wird 1–2× aufgerufen)
private fun showNetworkNotificationNow(context: Context, content: String, final: Boolean = false) {
    val channelId = "network_info_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Netzwerk-Informationen",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt aktuelle Netzwerkdetails an"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setContentTitle("📡 Netzwerk-Info")
        .setContentText(content.lines().firstOrNull() ?: "Netzwerkinfo")
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(final) // Nur letzte Benachrichtigung automatisch schließen

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(1020, builder.build())
    } else {
        // Fallback: Nur bei erster Anzeige (sonst Spam)
        if (!final) {
            val preview = content.lines().take(2).joinToString("\n")
            Toast.makeText(context, "Netzwerk:\n$preview", Toast.LENGTH_LONG).show()
        }
    }
}

fun showDetailedStorageInfo(context: Context) {
    // === 1. Interner Speicher ===
    try {
        val internalStat = StatFs("/")
        val blockSize = internalStat.blockSizeLong
        val total = internalStat.blockCountLong * blockSize
        val available = internalStat.availableBlocksLong * blockSize
        val used = total - available

        val content = """
            Gesamt: ${formatBytes(total)}
            Genutzt: ${formatBytes(used)}
            Verfügbar: ${formatBytes(available)}
        """.trimIndent()

        showStorageNotification(
            context,
            "storage_internal",
            "Interner Speicher",
            1010,
            "📁 Interner Speicher (Gerät)",
            content
        )
    } catch (e: Exception) {
        showStorageNotification(
            context,
            "storage_internal",
            "Interner Speicher",
            1010,
            "📁 Interner Speicher",
            "N/A"
        )
    }

    // === 2. Externer Speicher ===
    try {
        val externalDir = Environment.getExternalStorageDirectory()
        val externalStat = StatFs(externalDir.path)
        val blockSize = externalStat.blockSizeLong
        val total = externalStat.blockCountLong * blockSize
        val available = externalStat.availableBlocksLong * blockSize
        val used = total - available

        val content = """
            Pfad: ${externalDir.absolutePath}
            Gesamt: ${formatBytes(total)}
            Genutzt: ${formatBytes(used)}
            Verfügbar: ${formatBytes(available)}
        """.trimIndent()

        showStorageNotification(
            context,
            "storage_external",
            "Externer Speicher",
            1011,
            "📱 Externer Speicher (geteilt)",
            content
        )
    } catch (e: Exception) {
        showStorageNotification(
            context,
            "storage_external",
            "Externer Speicher",
            1011,
            "📱 Externer Speicher",
            "N/A"
        )
    }
}

// === Hilfsfunktion: Einheitliche Benachrichtigung ===
private fun showStorageNotification(
    context: Context,
    channelId: String,
    channelName: String,
    id: Int,
    title: String,
    content: String
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        channel.description = "Speicherinformation"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle(title)
        .setContentText(content.lines().firstOrNull() ?: content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(id, builder.build())
    } else {
        // Fallback: Toast mit Titel + erster Zeile
        Toast.makeText(
            context,
            "$title\n${content.lines().firstOrNull() ?: content}",
            Toast.LENGTH_LONG
        ).show()
    }
}

// === Hilfsfunktionen (wie in deinem Original) ===
private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000_000L -> "${String.format("%.2f", bytes / 1_000_000_000_000.0)} TB"
        bytes >= 1_000_000_000L -> "${String.format("%.2f", bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000L -> "${String.format("%.2f", bytes / 1_000_000.0)} MB"
        bytes >= 1_000L -> "${String.format("%.2f", bytes / 1_000.0)} KB"
        else -> "$bytes B"
    }
}

private fun formatDirSize(dir: File?): String {
    if (dir == null || !dir.exists()) return "0 B"
    return try {
        formatBytes(getDirSize(dir))
    } catch (e: Exception) {
        "N/A"
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

        // Notification Channel erstellen (für Android 8.0+)
        val channelId = "battery_info_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Batterie-Informationen",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Zeigt detaillierte Batterie-Informationen an"
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Notification erstellen
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔋 Batterie-Info")
            .setContentText("Ladezustand: $percentage%")
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle()
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
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Notification anzeigen
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(1001, builder.build())
        } else {
            // Fallback zu Toast wenn keine Notification-Berechtigung
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

/**
 * Aktiviert/Deaktiviert Energiesparmodus (benötigt Android 5.1+)
 */
fun openBatterySettings(context: Context) {
    context.startActivity(Intent("android.intent.action.POWER_USAGE_SUMMARY").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
}

/*
 * Zeigt Geräte-Info an
 */
fun showDeviceInfo(context: Context) {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    val systemInfo = """
        ▶ System:
        Gerätename: ${
        Settings.Global.getString(
            context.contentResolver,
            Settings.Global.DEVICE_NAME
        )
    }
        Modell: ${Build.MODEL}
        Hersteller: ${Build.MANUFACTURER}
        Marke: ${Build.BRAND}
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Sicherheitspatch: ${Build.VERSION.SECURITY_PATCH}
        Build: ${Build.DISPLAY}
        Build-Typ: ${Build.TYPE}
        Build-Zeit: ${java.util.Date(Build.TIME)}
    """.trimIndent()

    val hardwareInfo = """
        ▶ Hardware:
        CPU-Architektur: ${Build.SUPPORTED_ABIS.joinToString()}
        Board: ${Build.BOARD}
        Bootloader: ${Build.BOOTLOADER}
        Fingerprint: ${Build.FINGERPRINT}
        Display: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} @ ${context.resources.displayMetrics.densityDpi}dpi
        RAM (verfügbar/gesamt): ${(memInfo.availMem / (1024 * 1024))}MB / ${(memInfo.totalMem / (1024 * 1024))}MB
    """.trimIndent()

    val appInfo = """
        ▶ App:
        App-Version: ${packageInfo.versionName} (${packageInfo.longVersionCode})
        Installiert am: ${java.util.Date(packageInfo.firstInstallTime)}
        Zuletzt aktualisiert: ${java.util.Date(packageInfo.lastUpdateTime)}
    """.trimIndent()

    val networkInfo = """
        ▶ Netzwerk:
        WLAN SSID: ${wifiManager.connectionInfo.ssid}
        IP-Adresse: ${Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)}
        MAC-Adresse: ${wifiManager.connectionInfo.macAddress}
    """.trimIndent()

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "device_info_channel"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Geräte-Infos",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt System-, Hardware-, App- und Netzwerk-Infos an"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val categories = listOf(
        "📱 System" to systemInfo,
        "⚙️ Hardware" to hardwareInfo,
        "📦 App" to appInfo,
        "🌐 Netzwerk" to networkInfo
    )

    var id = 1001
    for ((title, content) in categories) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id++, notification)
    }
}


fun openDeveloperOptions(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Entwickleroptionen nicht aktiviert", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Schaltet Bildschirm-Helligkeit (öffnet Einstellungen)
 */
fun adjustBrightness(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Helligkeits-Einstellungen nicht verfügbar", Toast.LENGTH_SHORT)
            .show()
    }
}

/**
 * Öffnet Datennutzung
 */
fun openDataUsage(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Datennutzung nicht verfügbar", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Öffnet App-Info der eigenen App
 */
fun openAppInfo(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "App-Info nicht verfügbar", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Öffnet Standort-Einstellungen
 */
fun openLocationSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Standort-Einstellungen nicht verfügbar", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Öffnet Speicher-Einstellungen
 */
fun openStorageSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Speicher-Einstellungen nicht verfügbar", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Startet Taschenlampe-Toggle (falls verfügbar)
 */
fun toggleFlashlight(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val cameraManager =
                context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]

            // Hinweis: Toggle-Funktion erfordert mehr State-Management
            Toast.makeText(context, "💡 Taschenlampe über Quick Settings nutzen", Toast.LENGTH_SHORT)
                .show()

            // Alternative: Öffne Quick Settings
            try {
                context.sendBroadcast(Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"))
            } catch (e: Exception) {
                // Ignorieren
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Taschenlampe nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(
            context,
            "Funktion nicht verfügbar (Android 6.0+ benötigt)",
            Toast.LENGTH_SHORT
        ).show()
    }
}

// Quick Settings Funktionen
fun openWiFiSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openMobileDataSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openCamera(context: Context) {
    try {
        context.startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Kamera nicht verfügbar", Toast.LENGTH_SHORT).show()
    }
}

fun openDisplaySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openSoundSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openCalculator(context: Context) {
    try {
        val intent =
            context.packageManager.getLaunchIntentForPackage("com.google.android.calculator")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.calculator2")
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Taschenrechner nicht gefunden", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openAlarm(context: Context) {
    try {
        context.startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Wecker nicht verfügbar", Toast.LENGTH_SHORT).show()
    }
}

fun openSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// === Speicherung des zuletzt ausgewählten Menüs ===
private const val PREFS_NAME = "cloud_app_prefs"
private const val KEY_LAST_MENU_ITEM = "last_menu_item"

fun saveLastMenuItem(context: Context, menuItem: MenuItem) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LAST_MENU_ITEM, menuItem.name)
        .apply()
}

fun loadLastMenuItem(context: Context): MenuItem {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedName = prefs.getString(KEY_LAST_MENU_ITEM, MenuItem.PRIVATE_CLOUD.name)
    return try {
        MenuItem.valueOf(savedName ?: MenuItem.PRIVATE_CLOUD.name)
    } catch (e: Exception) {
        MenuItem.PRIVATE_CLOUD
    }
}