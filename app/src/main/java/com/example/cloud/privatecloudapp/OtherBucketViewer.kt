package com.example.cloud.privatecloudapp

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.disk.DiskCache
import coil.memory.MemoryCache.Builder
import com.example.cloud.ui.theme.gruen
import com.example.cloud.ui.theme.hellgruen
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun OtherBucketViewer(
    storage: Storage,
    onBackPressed: () -> Unit
) {
    var fileList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isDownloading by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDownloadProgress by remember { mutableStateOf(false) }
    var showFullscreenImage by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var showVideoPlayer by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "videos", "images"
    val context = LocalContext.current
    var currentVideoIndex by rememberSaveable { mutableIntStateOf(0) }
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache { Builder(context).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }

    BackHandler {
        onBackPressed()
    }

    imageLoader.memoryCache?.clear()

    val cacheDirectory = context.cacheDir.resolve("image_cache")
    cacheDirectory.deleteRecursively()
    val urlCache = remember { mutableMapOf<String, String>() }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var ispictureupload by remember { mutableStateOf(false) }

    suspend fun loadFiles() {
        try {
            // Lade alle Dateien aus dem 'Other' Bucket mit Unterordnern
            val videoFiles = withContext(Dispatchers.IO) {
                storage.from("Other").list("videos")
            }

            val videoFiles1 = withContext(Dispatchers.IO) {
                storage.from("Other").list("videos2")
            }

            val folder1Files = withContext(Dispatchers.IO) {
                storage.from("Other").list("1")
            }

            val folder2Files = withContext(Dispatchers.IO) {
                storage.from("Other").list("2")
            }

            // Format: "folder|fileName|updatedAt|size"
            fileList = (
                    videoFiles.map { "videos|${it.name}|${it.updatedAt}|${it.metadata?.get("size") ?: 0}" } +
                            videoFiles1.map { "videos2|${it.name}|${it.updatedAt}|${it.metadata?.get("size") ?: 0}" } +
                            folder1Files.map { "1|${it.name}|${it.updatedAt}|${it.metadata?.get("size") ?: 0}" } +
                            folder2Files.map { "2|${it.name}|${it.updatedAt}|${it.metadata?.get("size") ?: 0}" }
                    ).filter { !it.contains(".emptyFolderPlaceholder") }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Laden: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isUploading = true
                uploadProgress = Pair(0, uris.size)

                try {
                    var successCount = 0
                    var failCount = 0

                    uris.forEachIndexed { index, uri ->
                        try {
                            uploadImage(context, storage, uri)
                            successCount++
                            uploadProgress = Pair(index + 1, uris.size)
                            delay(100)
                        } catch (e: Exception) {
                            failCount++
                            e.printStackTrace()
                        }
                    }

                    val message = when {
                        failCount == 0 -> "✅ Alle $successCount ${if(ispictureupload) "Bilder" else "Videos"} hochgeladen!"
                        successCount == 0 -> "❌ Upload fehlgeschlagen"
                        else -> "⚠️ $successCount hochgeladen, $failCount fehlgeschlagen"
                    }

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    loadFiles()

                } catch (e: Exception) {
                    Toast.makeText(context, "Fehler beim Upload: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploading = false
                    uploadProgress = null
                }
            }
        }
    }

    suspend fun deleteFile(fileWithMetadata: String) {
        try {
            val parts = fileWithMetadata.split("|")
            val folder = parts.getOrNull(0) ?: return
            val fileName = parts.getOrNull(1) ?: return

            fileList = fileList.filter { it != fileWithMetadata }

            withContext(Dispatchers.IO) {
                // Lösche aus dem 'Other' Bucket mit dem richtigen Pfad
                storage.from("Other").delete("$folder/$fileName")
            }

            delay(300)
            loadFiles()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Löschen: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
            loadFiles()
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
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterButton(
                    text = "Alle",
                    isSelected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    modifier = Modifier.weight(1f)
                )
                FilterButton(
                    text = "Videos",
                    isSelected = selectedFilter == "videos",
                    onClick = { selectedFilter = "videos" },
                    modifier = Modifier.weight(1f)
                )
                FilterButton(
                    text = "Bilder",
                    isSelected = selectedFilter == "images",
                    onClick = { selectedFilter = "images" },
                    modifier = Modifier.weight(1f)
                )
            }

            if (fileList.isEmpty()) {
                Text(
                    "Keine Dateien vorhanden",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            } else {
                val filteredList = when (selectedFilter) {
                    "videos" -> fileList.filter { it.startsWith("videos|") || it.startsWith("videos2|") }
                    "images" -> fileList.filter { it.startsWith("1|") || it.startsWith("2|") }
                    else -> fileList
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList.chunked(2)) { rowFiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFiles.forEach { file ->
                                val parts = file.split("|")
                                val folder = parts.getOrNull(0) ?: "1"
                                val fileName = parts.getOrNull(1) ?: "Unbekannt"
                                val fileDate = parts.getOrNull(2)?.replace("T", " ")?.substringBefore(".") ?: "Unbekannt"
                                val filesize = parts.getOrNull(3) ?: "0"
                                val fullPath = "$folder/$fileName"

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    onClick = {
                                        if (!isUploading && isDownloading == null) {
                                            if (isVideoFile(fileName)) {
                                                val videoFiles = when (selectedFilter) {
                                                    "videos" -> filteredList.filter { it.startsWith("videos|") || it.startsWith("videos2|") }
                                                    "images" -> emptyList()
                                                    else -> filteredList.filter { it.startsWith("videos|") || it.startsWith("videos2|") }
                                                }
                                                currentVideoIndex = videoFiles.indexOfFirst { it == file }.coerceAtLeast(0)
                                                showVideoPlayer = Pair(fullPath, file)
                                            } else {
                                                showFullscreenImage = Pair(fullPath, file)
                                            }
                                        }
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (isVideoFile(fileName)) {
                                            // Zeige Video-Thumbnail oder Icon
                                            val localVideoFile = videoExistsInPrivateStorage(context, fileName)

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Video",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(48.dp)
                                                )

                                                // Zeige Indikator wenn Video lokal gespeichert ist
                                                if (localVideoFile != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
                                                            .size(12.dp)
                                                            .background(gruen, shape = MaterialTheme.shapes.small)
                                                    )
                                                }
                                            }
                                        } else {
                                            var publicUrl by remember(fullPath) { mutableStateOf<String?>(null) }

                                            LaunchedEffect(fullPath) {
                                                try {
                                                    publicUrl = urlCache.getOrPut(fullPath) {
                                                        storage.from("Other").createSignedUrl(fullPath, 600.seconds)
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }

                                            Image(
                                                painter = rememberAsyncImagePainter(
                                                    model = publicUrl,
                                                    imageLoader = imageLoader
                                                ),
                                                contentDescription = fileName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
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
                                                val remoteSize = filesize.toLongOrNull() ?: -1L
                                                val imageAlreadyDownloaded = remoteSize >= 0 &&
                                                        fileExistsLocallyWithSameSize(fileName, remoteSize)

                                                val sizeBytes = filesize.toLongOrNull() ?: 0L
                                                val sizeText = when {
                                                    sizeBytes >= 1_000_000_000 -> "%.2f GB".format(sizeBytes / 1_000_000_000.0)
                                                    sizeBytes >= 1_000_000 -> "%.2f MB".format(sizeBytes / 1_000_000.0)
                                                    else -> "%.1f KB".format(sizeBytes / 1_000.0)
                                                }

                                                Text(
                                                    text = "$fileName\n$fileDate\n$sizeText",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                    color = Color.White,
                                                    maxLines = 3
                                                )

                                                if (!imageAlreadyDownloaded && !isVideoFile(fileName)) {
                                                    IconButton(
                                                        onClick = {
                                                            isDownloading = file
                                                            scope.launch {
                                                                showDownloadProgress = true
                                                                try {
                                                                    val data = withContext(Dispatchers.IO) {
                                                                        storage.from("Other").downloadAuthenticated(fullPath)
                                                                    }

                                                                    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                                                                    val appFolder = File(dcimDir, "Cloud")
                                                                    if (!appFolder.exists()) appFolder.mkdirs()
                                                                    val outputFile = File(appFolder, fileName)

                                                                    withContext(Dispatchers.IO) {
                                                                        FileOutputStream(outputFile).use { fos ->
                                                                            fos.write(data)
                                                                        }
                                                                    }

                                                                    MediaScannerConnection.scanFile(
                                                                        context,
                                                                        arrayOf(outputFile.absolutePath),
                                                                        null
                                                                    ) { _, _ ->
                                                                        // Scan complete
                                                                    }

                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
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
                                                                imageVector = Icons.Default.ArrowDropDown,
                                                                contentDescription = "Download",
                                                                tint = Color.White
                                                            )
                                                        }
                                                    }
                                                } else if (imageAlreadyDownloaded) {
                                                    IconButton(
                                                        onClick = {
                                                            val localImageFile = fileExistsInDCIM(fileName)!!
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
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        scope.launch { deleteFile(file) }
                                                    },
                                                    modifier = Modifier.size(40.dp).padding(4.dp),
                                                    enabled = !isUploading && isDownloading == null
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Löschen",
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(24.dp)
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
                }
            }
        }

        var lastTapTime by remember { mutableLongStateOf(0L) }
        var singleTapJob by remember { mutableStateOf<Job?>(null) }
        val doubleTapTimeout = 300L

        FloatingActionButton(
            onClick = {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime <= doubleTapTimeout) {
                    singleTapJob?.cancel()
                    singleTapJob = null
                    ispictureupload = false
                    imagePickerLauncher.launch("video/*")
                    lastTapTime = 0L
                } else {
                    lastTapTime = currentTime
                    singleTapJob?.cancel()
                    singleTapJob = scope.launch {
                        delay(doubleTapTimeout)
                        ispictureupload = true
                        imagePickerLauncher.launch("image/*")
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            singleTapJob?.cancel()
                            singleTapJob = null
                            imagePickerLauncher.launch("video/*")
                        }
                    )
                },
            containerColor = gruen
        ) {
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Hochladen",
                    tint = Color.White
                )
            }
        }

        uploadProgress?.let { (current, total) ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.padding(32.dp).fillMaxWidth(0.8f)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${if(ispictureupload) "Bilder" else "Videos"} werden hochgeladen",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(text = "$current von $total", style = MaterialTheme.typography.bodyLarge)
                        LinearProgressIndicator(
                        progress = { current.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = gruen,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                        Text(
                            text = "${((current.toFloat() / total.toFloat()) * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        if (showDownloadProgress) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.padding(16.dp).size(100.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = gruen, modifier = Modifier.size(40.dp))
                    }
                }
            }
        }
    }

    showFullscreenImage?.let { (fullPath, fileWithMetadata) ->
        val parts = fileWithMetadata.split("|")
        val fileName = parts.getOrNull(1) ?: fullPath
        val remoteSize = parts.getOrNull(3)?.toLongOrNull() ?: -1L
        val isDownloaded = remoteSize >= 0 && fileExistsLocallyWithSameSize(fileName, remoteSize)

        var imageUrl by remember(fullPath) { mutableStateOf<String?>(null) }

        LaunchedEffect(fullPath) {
            try {
                imageUrl = storage.from("Other").createSignedUrl(fullPath, 600.seconds)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        FullscreenImageDialog(
            imageUrl = imageUrl,
            fileName = fileName,
            isDownloaded = isDownloaded,
            onDismiss = { showFullscreenImage = null },
            onDownload = {
                scope.launch {
                    isDownloading = fileWithMetadata
                    showDownloadProgress = true
                    try {
                        val data = withContext(Dispatchers.IO) {
                            storage.from("Other").downloadAuthenticated(fullPath)
                        }

                        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        val appFolder = File(dcimDir, "Cloud")
                        if (!appFolder.exists()) appFolder.mkdirs()
                        val outputFile = File(appFolder, fileName)

                        withContext(Dispatchers.IO) {
                            FileOutputStream(outputFile).use { fos -> fos.write(data) }
                        }

                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(outputFile.absolutePath),
                            null
                        ) { _, _ ->
                            // Scan complete
                        }

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        showFullscreenImage = null
                        loadFiles()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isDownloading = null
                        delay(500)
                        showDownloadProgress = false
                    }
                }
            },
            onOpenInGallery = {
                val localImageFile = fileExistsInDCIM(fileName)
                if (localImageFile != null) {
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
                    showFullscreenImage = null
                }
            }
        )
    }

    showVideoPlayer?.let { (_, _) ->
        val videoFiles = when (selectedFilter) {
            "videos" -> fileList.filter { it.startsWith("videos|") || it.startsWith("videos2|") }
            "images" -> emptyList()
            else -> fileList.filter { it.startsWith("videos|") || it.startsWith("videos2|") }
        }

        VideoPlayerDialog(
            storage = storage,
            videoFiles = videoFiles,
            initialIndex = currentVideoIndex,
            onDismiss = { showVideoPlayer = null },
            onIndexChanged = { newIndex -> currentVideoIndex = newIndex }
        )
    }
}

suspend fun uploadImage(context: Context, storage: Storage, uri: Uri) {
    withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes != null) {
            val mimeType = context.contentResolver.getType(uri)
            val isVideo = mimeType?.startsWith("video/") == true

            val extension = when {
                isVideo -> when {
                    mimeType.contains("mp4") -> ".mp4"
                    mimeType.contains("quicktime") -> ".mov"
                    mimeType.contains("avi") -> ".avi"
                    mimeType.contains("mkv") -> ".mkv"
                    mimeType.contains("3gpp") -> ".3gp"
                    else -> ".mp4"
                }
                else -> ".jpg"
            }

            val prefix = if (isVideo) "VID" else "IMG"
            val fileName = "${prefix}_${System.currentTimeMillis()}${extension}"

            // Speichere Videos in 'Other/videos/' und Bilder in 'Other/1/'
            val targetPath = if (isVideo) "videos2/$fileName" else "1/$fileName"

            storage.from("Other").upload(targetPath, bytes)
        }
    }
}

// NEUE FUNKTIONEN FÜR PRIVATE VIDEO-SPEICHERUNG

// Prüft ob ein Video im privaten App-Ordner existiert
fun videoExistsInPrivateStorage(context: Context, fileName: String): File? {
    val privateVideoDir = File(context.filesDir, "videos")
    val file = File(privateVideoDir, fileName)
    return if (file.exists()) file else null
}

// Lädt ein Video in den privaten App-Ordner herunter
suspend fun downloadVideoToPrivateStorage(
    context: Context,
    storage: Storage,
    fullPath: String,
    fileName: String
): File? {
    return withContext(Dispatchers.IO) {
        try {
            val data = storage.from("Other").downloadAuthenticated(fullPath)

            // Privater App-Ordner (nur für diese App zugänglich)
            val privateVideoDir = File(context.filesDir, "videos")
            if (!privateVideoDir.exists()) {
                privateVideoDir.mkdirs()
            }

            val outputFile = File(privateVideoDir, fileName)

            FileOutputStream(outputFile).use { fos ->
                fos.write(data)
            }

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(
    storage: Storage,
    videoFiles: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIndexChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    var currentIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
    var isLoading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()

    // ExoPlayer außerhalb von remember, damit er bei URL-Änderung aktualisiert wird
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    BackHandler {
        onDismiss()
    }

    suspend fun loadVideoAtIndex(index: Int) {
        if (index !in videoFiles.indices) return

        isLoading = true
        downloadProgress = null

        try {
            val fileWithMetadata = videoFiles[index]
            val parts = fileWithMetadata.split("|")
            val folder = parts.getOrNull(0) ?: return
            val fileName = parts.getOrNull(1) ?: return
            val fullPath = "$folder/$fileName"

            // Prüfe ob Video bereits lokal existiert
            val localFile = videoExistsInPrivateStorage(context, fileName)

            if (localFile != null) {
                // Video ist bereits heruntergeladen - spiele lokal ab
                exoPlayer.apply {
                    stop()
                    clearMediaItems()
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(localFile)))
                    prepare()
                    playWhenReady = true
                }
            } else {
                // Video muss heruntergeladen werden
                downloadProgress = 0f
                Toast.makeText(context, "⬇️ Video wird heruntergeladen...", Toast.LENGTH_SHORT).show()

                val downloadedFile = downloadVideoToPrivateStorage(context, storage, fullPath, fileName)

                if (downloadedFile != null) {
                    // Video erfolgreich heruntergeladen - spiele lokal ab
                    exoPlayer.apply {
                        stop()
                        clearMediaItems()
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(downloadedFile)))
                        prepare()
                        playWhenReady = true
                    }
                    Toast.makeText(context, "✅ Video gespeichert und wird abgespielt", Toast.LENGTH_SHORT).show()
                } else {
                    // Fehler beim Download - spiele Stream ab
                    val url = withContext(Dispatchers.IO) {
                        storage.from("Other").createSignedUrl(fullPath, 600.seconds)
                    }

                    exoPlayer.apply {
                        stop()
                        clearMediaItems()
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        playWhenReady = true
                    }
                    Toast.makeText(context, "⚠️ Download fehlgeschlagen - Streaming", Toast.LENGTH_LONG).show()
                }
            }

            currentIndex = index
            onIndexChanged(index)
            isLoading = false
            downloadProgress = null
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
            downloadProgress = null
            Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(initialIndex) {
        loadVideoAtIndex(initialIndex)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var isSwiping by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isSwiping = false },
                    onDragEnd = { isSwiping = false },
                    onDragCancel = { isSwiping = false }
                ) { _, dragAmount ->
                    if (!isSwiping) {
                        if (dragAmount > 20 && currentIndex > 0) {
                            isSwiping = true
                            scope.launch {
                                loadVideoAtIndex(currentIndex - 1)
                                playerView?.hideController()
                            }
                        } else if (dragAmount < -20 && currentIndex < videoFiles.size - 1) {
                            isSwiping = true
                            scope.launch {
                                loadVideoAtIndex(currentIndex + 1)
                                playerView?.hideController()
                            }
                        }
                    }
                }
            }) {
        // PlayerView wird nur einmal erstellt
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = false // Controller nur bei Touch zeigen
                    controllerHideOnTouch = true
                    playerView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))

                    downloadProgress?.let { progress ->
                        Card(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Video wird heruntergeladen...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = gruen,
                                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Video Counter
        if (videoFiles.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val currentFile = videoFiles.getOrNull(currentIndex)
                val fileName = currentFile?.split("|")?.getOrNull(1) ?: ""
                val isLocallyStored = videoExistsInPrivateStorage(context, fileName) != null

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentIndex + 1} / ${videoFiles.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (isLocallyStored) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(gruen, shape = MaterialTheme.shapes.small)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) gruen else Color.DarkGray.copy(alpha = 0.5f)
                )
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}