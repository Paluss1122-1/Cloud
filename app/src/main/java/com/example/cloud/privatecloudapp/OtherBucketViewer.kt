@file:Suppress("AssignedValueIsNeverRead")

package com.example.cloud.privatecloudapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.ExperimentalTime

data class LocalFileInfo(
    val file: File,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val isVideo: Boolean
)

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun OtherBucketViewer(
    onBackPressed: () -> Unit
) {
    var fileList by remember { mutableStateOf<List<LocalFileInfo>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showFullscreenImage by rememberSaveable { mutableStateOf<LocalFileInfo?>(null) }
    var showVideoPlayer by rememberSaveable { mutableStateOf<LocalFileInfo?>(null) }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "videos", "images"
    val context = LocalContext.current
    var currentVideoIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentImageIndex by rememberSaveable { mutableIntStateOf(0) }
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

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Funktion zum Laden der Dateien aus dem privaten Speicher
    suspend fun loadFilesFromPrivateStorage() {
        withContext(Dispatchers.IO) {
            try {
                val privateDir = File(context.filesDir, "shared_files")
                if (!privateDir.exists()) {
                    privateDir.mkdirs()
                }

                val files = privateDir.listFiles()?.filter { it.isFile } ?: emptyList()

                fileList = files.map { file ->
                    val isVideo = isVideoFile(file.name)
                    LocalFileInfo(
                        file = file,
                        fileName = file.name,
                        fileSize = file.length(),
                        lastModified = file.lastModified(),
                        isVideo = isVideo
                    )
                }.sortedByDescending { it.lastModified } // Neueste zuerst

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fehler beim Laden: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                            saveFileToPrivateStorage(context, uri)
                            successCount++
                            uploadProgress = Pair(index + 1, uris.size)
                            delay(100)
                        } catch (e: Exception) {
                            failCount++
                            e.printStackTrace()
                        }
                    }

                    val message = when {
                        failCount == 0 -> "✅ Alle $successCount Dateien gespeichert!"
                        successCount == 0 -> "❌ Speichern fehlgeschlagen"
                        else -> "⚠️ $successCount gespeichert, $failCount fehlgeschlagen"
                    }

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    loadFilesFromPrivateStorage()

                } catch (e: Exception) {
                    Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploading = false
                    uploadProgress = null
                }
            }
        }
    }

    suspend fun deleteFile(fileInfo: LocalFileInfo) {
        try {
            withContext(Dispatchers.IO) {
                fileInfo.file.delete()
            }

            fileList = fileList.filter { it.file.absolutePath != fileInfo.file.absolutePath }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "✅ Datei gelöscht", Toast.LENGTH_SHORT).show()
            }

            delay(300)
            loadFilesFromPrivateStorage()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Löschen: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
            loadFilesFromPrivateStorage()
        }
    }

    LaunchedEffect(Unit) {
        loadFilesFromPrivateStorage()
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
                    text = "Alle (${fileList.size})",
                    isSelected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    modifier = Modifier.weight(1f)
                )
                FilterButton(
                    text = "Videos (${fileList.count { it.isVideo }})",
                    isSelected = selectedFilter == "videos",
                    onClick = { selectedFilter = "videos" },
                    modifier = Modifier.weight(1f)
                )
                FilterButton(
                    text = "Bilder (${fileList.count { !it.isVideo }})",
                    isSelected = selectedFilter == "images",
                    onClick = { selectedFilter = "images" },
                    modifier = Modifier.weight(1f)
                )
            }

            if (fileList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "📁 Keine Dateien vorhanden",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            "Tippe auf + um Dateien hinzuzufügen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                val filteredList = when (selectedFilter) {
                    "videos" -> fileList.filter { it.isVideo }
                    "images" -> fileList.filter { !it.isVideo }
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
                            rowFiles.forEach { fileInfo ->
                                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                val fileDate = dateFormat.format(Date(fileInfo.lastModified))

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    onClick = {
                                        if (!isUploading) {
                                            if (fileInfo.isVideo) {
                                                val videoFiles = filteredList.filter { it.isVideo }
                                                currentVideoIndex = videoFiles.indexOfFirst {
                                                    it.file.absolutePath == fileInfo.file.absolutePath
                                                }.coerceAtLeast(0)
                                                showVideoPlayer = fileInfo
                                            } else {
                                                val imageFiles = filteredList.filter { !it.isVideo }
                                                currentImageIndex = imageFiles.indexOfFirst {
                                                    it.file.absolutePath == fileInfo.file.absolutePath
                                                }.coerceAtLeast(0)
                                                showFullscreenImage = fileInfo
                                            }
                                        }
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (fileInfo.isVideo) {
                                            // Video-Thumbnail
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
                                            }
                                        } else {
                                            // Bild anzeigen
                                            Image(
                                                painter = rememberAsyncImagePainter(
                                                    model = fileInfo.file,
                                                    imageLoader = imageLoader
                                                ),
                                                contentDescription = fileInfo.fileName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        // Datei-Informationen
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
                                                val sizeText = when {
                                                    fileInfo.fileSize >= 1_000_000_000 -> "%.2f GB".format(fileInfo.fileSize / 1_000_000_000.0)
                                                    fileInfo.fileSize >= 1_000_000 -> "%.2f MB".format(fileInfo.fileSize / 1_000_000.0)
                                                    else -> "%.1f KB".format(fileInfo.fileSize / 1_000.0)
                                                }

                                                Text(
                                                    text = "${fileInfo.fileName}\n$fileDate\n$sizeText",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                    color = Color.White,
                                                    maxLines = 3,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                // Öffnen-Button (nur für Bilder)
                                                if (!fileInfo.isVideo) {
                                                    IconButton(
                                                        onClick = {
                                                            val fileUri = FileProvider.getUriForFile(
                                                                context,
                                                                "${context.packageName}.fileprovider",
                                                                fileInfo.file
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

                                                // Löschen-Button
                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        scope.launch { deleteFile(fileInfo) }
                                                    },
                                                    modifier = Modifier.size(40.dp).padding(4.dp),
                                                    enabled = !isUploading
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

        // FAB für Datei-Upload
        FloatingActionButton(
            onClick = {
                imagePickerLauncher.launch("*/*") // Alle Dateitypen
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = gruen
        ) {
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Datei hinzufügen",
                    tint = Color.White
                )
            }
        }

        // Upload-Fortschritt
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
                            text = "Dateien werden gespeichert",
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
    }

    showFullscreenImage?.let { _ ->
        val imageFiles = when (selectedFilter) {
            "videos" -> emptyList()
            "images" -> fileList.filter { !it.isVideo }
            else -> fileList.filter { !it.isVideo }
        }

        FullscreenImageDialogLocal(
            fileInfo = imageFiles[currentImageIndex],
            allImages = imageFiles,
            currentIndex = currentImageIndex,
            onDismiss = { showFullscreenImage = null },
            onOpenInGallery = {
                val currentFile = imageFiles[currentImageIndex]
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    currentFile.file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                showFullscreenImage = null
            },
            onIndexChanged = { newIndex ->
                currentImageIndex = newIndex
            }
        )
    }

    // Video Player Dialog
    showVideoPlayer?.let { _ ->
        val videoFiles = when (selectedFilter) {
            "videos" -> fileList.filter { it.isVideo }
            "images" -> emptyList()
            else -> fileList.filter { it.isVideo }
        }

        VideoPlayerDialogLocal(
            videoFiles = videoFiles,
            initialIndex = currentVideoIndex,
            onDismiss = { showVideoPlayer = null },
            onIndexChanged = { newIndex -> currentVideoIndex = newIndex }
        )
    }
}

// Funktion zum Speichern von Dateien im privaten Speicher
suspend fun saveFileToPrivateStorage(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                // Dateinamen extrahieren
                var fileName = "file_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                }

                val privateDir = File(context.filesDir, "shared_files")
                if (!privateDir.exists()) {
                    privateDir.mkdirs()
                }

                // Eindeutigen Dateinamen sicherstellen
                var targetFile = File(privateDir, fileName)
                var counter = 1
                while (targetFile.exists()) {
                    val nameWithoutExt = fileName.substringBeforeLast(".")
                    val extension = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
                    targetFile = File(privateDir, "${nameWithoutExt}_$counter$extension")
                    counter++
                }

                FileOutputStream(targetFile).use { fos ->
                    fos.write(bytes)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}

@Composable
fun FullscreenImageDialogLocal(
    fileInfo: LocalFileInfo,
    allImages: List<LocalFileInfo>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onOpenInGallery: () -> Unit,
    onIndexChanged: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSwiping by remember { mutableStateOf(false) }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(currentIndex) { // Key geändert, damit bei jedem Index-Wechsel neu initialisiert wird
                detectHorizontalDragGestures(
                    onDragStart = { isSwiping = false },
                    onDragEnd = {
                        scope.launch {
                            delay(100) // Kurze Verzögerung vor dem Reset
                            isSwiping = false
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            delay(100)
                            isSwiping = false
                        }
                    }
                ) { _, dragAmount ->
                    if (!isSwiping) {
                        if (dragAmount > 20 && currentIndex > 0) {
                            isSwiping = true
                            onIndexChanged(currentIndex - 1)
                        } else if (dragAmount < -20 && currentIndex < allImages.size - 1) {
                            isSwiping = true
                            onIndexChanged(currentIndex + 1)
                        }
                    }
                }
            }
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = fileInfo.file),
            contentDescription = fileInfo.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Dateiname und Counter oben
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (allImages.size > 1) {
                    Text(
                        text = "${currentIndex + 1} / ${allImages.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = fileInfo.fileName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Öffnen-Button unten
        FloatingActionButton(
            onClick = onOpenInGallery,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = gruen
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "In Gallery öffnen",
                tint = Color.White
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialogLocal(
    videoFiles: List<LocalFileInfo>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIndexChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    var currentIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
    val scope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    BackHandler {
        onDismiss()
    }

    fun loadVideoAtIndex(index: Int) {
        if (index !in videoFiles.indices) return

        val fileInfo = videoFiles[index]

        exoPlayer.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(Uri.fromFile(fileInfo.file)))
            prepare()
            playWhenReady = true
        }

        currentIndex = index
        onIndexChanged(index)
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
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = false
                    controllerHideOnTouch = true
                    playerView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (videoFiles.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${currentIndex + 1} / ${videoFiles.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
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