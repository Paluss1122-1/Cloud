@file:Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.example.cloud.privatecloudapp

import com.example.cloud.quicksettingsfunctions.BatteryDataRepository
import com.example.cloud.quicksettingsfunctions.showNetworkInfo
import com.example.cloud.quicksettingsfunctions.showSensorsInfo
import com.example.cloud.quicksettingsfunctions.BatteryChartScreen
import com.example.cloud.whatsapptab.WhatsAppTabScreen
import com.example.cloud.browsertab.BrowserTabContent

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.ActivityManager
import android.app.NotificationManager
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cloud.ui.theme.gruen
import com.example.cloud.ui.theme.hellgruen
import io.github.jan.supabase.storage.Storage
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
import android.hardware.display.DisplayManager
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.cloud.objects.NotificationRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.content.edit

// Enum für Menü-Einträge (einfach erweiterbar)
enum class MenuItem(val title: String, val icon: String) {
    PRIVATE_CLOUD("Private Cloud", "☁️"),
    OTHER_BUCKET("Other Bucket", "📂"),
    WHATSAPP("WhatsApp", "💬"),
    BROWSER("Browser", "🌐"),
    QUICK("Schnellzugriff", "⚡"),
    NOTIFICATIONS("Benachrichtigungsverlauf", "⌚")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateCloudApp(storage: Storage) {
    var isFullScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var selectedMenuItem by remember { mutableStateOf(loadLastMenuItem(context)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        BatteryDataRepository.init(context)
    }
    var webViewUrl by remember { mutableStateOf("https://www.google.com") }
    var webViewState by remember { mutableStateOf<WebView?>(null) }

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

    if (isFullScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ✅ Keine erneute Deklaration nötig!
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

                MenuItem.WHATSAPP -> WhatsAppTabScreen()
                MenuItem.QUICK -> QuickSettingsTabContent()
                MenuItem.NOTIFICATIONS -> Notifications()
            }

            // ✅ Back-Handler für Browser-Vollbild
            if (selectedMenuItem == MenuItem.BROWSER) {
                BackHandler(enabled = true) {
                    if (webViewState?.canGoBack() == true) {
                        webViewState?.goBack()
                    } else {
                        isFullScreen = false
                    }
                }
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
                    // ✅ Keine erneute Deklaration nötig!
                    when (selectedMenuItem) {
                        MenuItem.PRIVATE_CLOUD -> MainCloudScreen(storage)
                        MenuItem.WHATSAPP -> WhatsAppTabScreen()
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

                        MenuItem.NOTIFICATIONS -> Notifications()
                    }
                }
            }
        }
    }


    if (isFullScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            var webViewState by remember { mutableStateOf<WebView?>(null) }

            // WebView
            AndroidView(
                factory = { ctx ->
                    val webView = WebView(ctx).apply {
                        webChromeClient = WebChromeClient()
                        settings.apply {
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        loadUrl(webViewUrl)
                    }

                    // CookieManager konfigurieren
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }

                    webView
                },
                update = { webView ->
                    webViewState = webView
                },
                modifier = Modifier.fillMaxSize()
            )

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
                                type = "*/*"  // ✅ CORRECT
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


@Composable
fun Notifications() {
    val context = LocalContext.current
    val notifications = NotificationRepository.notifications

    // Prüfen, ob der Listener aktiviert ist
    val isListenerEnabled = remember {
        Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )?.contains(context.packageName) == true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(12.dp)
    ) {
        if (!isListenerEnabled) {
            Text(
                text = "⚠️ Benachrichtigungszugriff nicht aktiviert",
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
            Button(
                onClick = {
                    context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
            ) {
                Text("Einstellungen öffnen")
            }
            return
        }

        if (notifications.isEmpty()) {
            Text(
                text = "Keine Benachrichtigungen vorhanden",
                color = Color.Gray,
                modifier = Modifier.fillMaxSize(),
                textAlign = TextAlign.Center
            )
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notifications.reversed()) { sbn ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Paketname (App)
                        Text(
                            text = sbn.packageName,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )

                        // Titel und Text (wenn vorhanden)
                        val extras = sbn.notification.extras
                        extras.getString("android.title")?.let { title ->
                            Text(text = title, color = Color.LightGray, fontSize = 13.sp)
                        }
                        extras.getString("android.text")?.let { text ->
                            Text(text = text, color = Color.White, fontSize = 12.sp)
                        }

                        // Zeitstempel
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(sbn.postTime)),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun QuickSettingsTabContent() {
    val context = LocalContext.current
    var showBatteryChart by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                        "Batterie\nDiagramm" to { showBatteryChart = true },
                        "📱\nGeräte\nInfo" to { showDeviceInfo(context) },
                        "🔨\nEntwickler" to { openDeveloperOptions(context) }
                    )
                )
            }
        }
    }
    if (showBatteryChart) {
        BatteryChartScreen(
            onDismiss = { showBatteryChart = false }
        )
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
            ) { Text(text = label, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }
    }
}

@SuppressLint("NewApi")
fun showDisplayInfo(context: Context) {
    val display: Display = context.display


    val metrics = DisplayMetrics()
    display.getRealMetrics(metrics) // inkl. Systemleisten

    val usableMetrics = DisplayMetrics()
    display.getMetrics(usableMetrics) // nutzbarer Bereich

    val info = StringBuilder()

    // === 1. Physikalische Auflösung (echte Pixel) ===
    val realWidth = metrics.widthPixels
    val realHeight = metrics.heightPixels
    info.append("📏 Phys. Auflösung: $realWidth × $realHeight px\n")

    // === 2. Nutzbare Auflösung (ohne Systemleisten) ===
    val usableWidth = usableMetrics.widthPixels
    val usableHeight = usableMetrics.heightPixels
    if (usableWidth != realWidth || usableHeight != realHeight) {
        info.append("📦 Nutzbare Fläche: $usableWidth × $usableHeight px\n")
    }

    // === 3. Dichte (dpi) ===
    val densityDpi = metrics.densityDpi
    val densityStr = when {
        densityDpi <= 120 -> "ldpi (120 dpi)"
        densityDpi <= 160 -> "mdpi (160 dpi)"
        densityDpi <= 240 -> "hdpi (240 dpi)"
        densityDpi <= 320 -> "xhdpi (320 dpi)"
        densityDpi <= 480 -> "xxhdpi (480 dpi)"
        else -> "xxxhdpi (≥ $densityDpi dpi)"
    }
    info.append("🔍 Dichte: $densityDpi dpi ($densityStr)\n")

    // === 4. Bildschirmgröße (Zoll, berechnet) ===
    try {
        val widthInches = realWidth / metrics.xdpi.toDouble()
        val heightInches = realHeight / metrics.ydpi.toDouble()
        val diagonalInches =
            sqrt(widthInches * widthInches + heightInches * heightInches)
        info.append("📐 Bildschirmgröße: ${String.format("%.1f", diagonalInches)} Zoll\n")
    } catch (_: Exception) {
        info.append("📐 Bildschirmgröße: N/A\n")
    }

    // === 5. Bildwiederholfrequenz (Refresh Rate) – ab Android 11 ===
    try {
        val refreshRate = display.refreshRate
        info.append("🔄 Refresh Rate: ${String.format("%.1f", refreshRate)} Hz\n")
    } catch (_: Exception) {
        info.append("🔄 Refresh Rate: N/A\n")
    }

    // === 6. HDR-Unterstützung (optional) ===
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val supportsHdr =
        displayManager.getDisplay(display.displayId)?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    info.append("🖼️ HDR: ${if (supportsHdr) "Ja" else "Nein"}\n")

    // === 7. Ausrichtung ===
    val rotation = display.rotation
    val orientationStr = when (rotation) {
        Surface.ROTATION_0 -> "Hochformat (0°)"
        Surface.ROTATION_90 -> "Querformat (90°)"
        Surface.ROTATION_180 -> "Hochformat (180°, umgekehrt)"
        Surface.ROTATION_270 -> "Querformat (270°, umgekehrt)"
        else -> "Unbekannt"
    }
    info.append("🧭 Ausrichtung: $orientationStr\n")

    // === Notification ===
    val channelId = "display_info_channel"
    val channel = NotificationChannel(
        channelId,
        "Display-Informationen",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Zeigt technische Display-Daten an"
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(channel)

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_menu_gallery)
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
                "Dichte: $densityDpi dpi\n" +
                "Größe: ${
                    String.format(
                        "%.1f",
                        sqrt(
                            (realWidth / metrics.xdpi).pow(2) + (realHeight / metrics.ydpi).pow(2)
                        )
                    )
                }\""
        Toast.makeText(context, fallback, Toast.LENGTH_LONG).show()
    }
}

// Hilfsfunktion: Sofortige Benachrichtigung (wird 1–2× aufgerufen)
fun showNetworkNotificationNow(context: Context, content: String, final: Boolean = false) {
    val channelId = "network_info_channel"
    val channel = NotificationChannel(
        channelId,
        "Netzwerk-Informationen",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Zeigt aktuelle Netzwerkdetails an"
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(channel)

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_menu_compass)
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
    } catch (_: Exception) {
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
    } catch (_: Exception) {
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
    val channel =
        NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
    channel.description = "Speicherinformation"
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(channel)

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_menu_info_details)
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

        // Notification erstellen
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
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
        flags = Intent.FLAG_ACTIVITY_NEW_TASK  // ✅ CORRECT
    })
}

/*
 * Zeigt Geräte-Info an
 */
@SuppressLint("HardwareIds")
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
        Build-Zeit: ${Date(Build.TIME)}
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
        Installiert am: ${Date(packageInfo.firstInstallTime)}
        Zuletzt aktualisiert: ${Date(packageInfo.lastUpdateTime)}
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

    val channel = NotificationChannel(
        channelId,
        "Geräte-Infos",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Zeigt System-, Hardware-, App- und Netzwerk-Infos an"
    }
    notificationManager.createNotificationChannel(channel)

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
            .setSmallIcon(R.drawable.ic_dialog_info)
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
 * Öffnet Speicher-Einstellungen
 */
fun openStorageSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Speicher-Einstellungen nicht verfügbar", Toast.LENGTH_SHORT).show()
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
        .edit {
            putString(KEY_LAST_MENU_ITEM, menuItem.name)
        }
}

fun loadLastMenuItem(context: Context): MenuItem {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedName = prefs.getString(KEY_LAST_MENU_ITEM, MenuItem.PRIVATE_CLOUD.name)
    return try {
        MenuItem.valueOf(savedName ?: MenuItem.PRIVATE_CLOUD.name)
    } catch (_: Exception) {
        MenuItem.PRIVATE_CLOUD
    }
}