@file:Suppress("AssignedValueIsNeverRead")

package com.cloud.privatecloudapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.disk.DiskCache
import coil.memory.MemoryCache.Builder
import com.cloud.ui.theme.gruen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.ExperimentalTime

data class LocalFileInfo(
    val file: File,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val isVideo: Boolean,
    var isFavorite: Boolean = false
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
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    val thumbnailCache = remember { mutableMapOf<String, Bitmap?>() }
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                Builder(context)
                    .maxSizePercent(0.4) // Erhöht von 0.25
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1) // Erhöht von 0.02
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
                        isVideo = isVideo,
                        isFavorite = favorites.contains(file.absolutePath)
                    )
                }.sortedByDescending { it.lastModified }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fehler beim Laden: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    suspend fun loadFavorites() {
        withContext(Dispatchers.IO) {
            try {
                val favFile = File(context.filesDir, "video_favorites.txt")
                if (favFile.exists()) {
                    favorites = favFile.readLines().toSet()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun saveFavorites() {
        withContext(Dispatchers.IO) {
            try {
                val favFile = File(context.filesDir, "video_favorites.txt")
                favFile.writeText(favorites.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun toggleFavorite(fileInfo: LocalFileInfo) {
        if (!fileInfo.isVideo) return

        favorites = if (favorites.contains(fileInfo.file.absolutePath)) {
            favorites - fileInfo.file.absolutePath
        } else {
            favorites + fileInfo.file.absolutePath
        }

        saveFavorites()
        loadFilesFromPrivateStorage()
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
                Toast.makeText(context, "Fehler beim Löschen: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
            e.printStackTrace()
            loadFilesFromPrivateStorage()
        }
    }

    LaunchedEffect(Unit) {
        loadFavorites()
        loadFilesFromPrivateStorage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
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
                FilterButton(
                    text = "⭐ (${fileList.count { it.isFavorite && it.isVideo }})",
                    isSelected = selectedFilter == "favorites",
                    onClick = { selectedFilter = "favorites" },
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
                    "favorites" -> fileList.filter { it.isFavorite && it.isVideo }
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
                                val dateFormat =
                                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
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
                                            var thumbnail by remember(fileInfo.file.absolutePath) {
                                                mutableStateOf<Bitmap?>(null)
                                            }
                                            var isLoading by remember { mutableStateOf(true) }

                                            LaunchedEffect(fileInfo.file.absolutePath) {
                                                // Zuerst im Memory-Cache prüfen
                                                val cachedInMemory = thumbnailCache[fileInfo.file.absolutePath]
                                                if (cachedInMemory != null) {
                                                    thumbnail = cachedInMemory
                                                    isLoading = false
                                                    return@LaunchedEffect
                                                }

                                                // Dann im Disk-Cache prüfen
                                                val cachedThumbnail = loadThumbnailFromCache(context, fileInfo.file.absolutePath)
                                                if (cachedThumbnail != null) {
                                                    thumbnail = cachedThumbnail
                                                    thumbnailCache[fileInfo.file.absolutePath] = cachedThumbnail
                                                    isLoading = false
                                                    return@LaunchedEffect
                                                }

                                                // Wenn nicht gecached, neu generieren
                                                withContext(Dispatchers.IO) {
                                                    val loadedThumbnail = getVideoFirstFrame(fileInfo.file)
                                                    if (loadedThumbnail != null) {
                                                        // Im Disk-Cache speichern
                                                        saveThumbnailToCache(context, fileInfo.file.absolutePath, loadedThumbnail)
                                                        // Im Memory-Cache speichern
                                                        thumbnailCache[fileInfo.file.absolutePath] = loadedThumbnail
                                                    }
                                                    thumbnail = loadedThumbnail
                                                    isLoading = false
                                                }
                                            }

                                            if (thumbnail != null) {
                                                Image(
                                                    bitmap = thumbnail!!.asImageBitmap(),
                                                    contentDescription = "Video Thumbnail",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.DarkGray),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (isLoading) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(32.dp),
                                                                color = Color.White
                                                            )
                                                        }
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Video",
                                                            tint = Color.White.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Existing image code bleibt gleich
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
                                                    fileInfo.fileSize >= 1_000_000_000 -> "%.2f GB".format(
                                                        fileInfo.fileSize / 1_000_000_000.0
                                                    )

                                                    fileInfo.fileSize >= 1_000_000 -> "%.2f MB".format(
                                                        fileInfo.fileSize / 1_000_000.0
                                                    )

                                                    else -> "%.1f KB".format(fileInfo.fileSize / 1_000.0)
                                                }

                                                Text(
                                                    text = "${fileInfo.fileName}\n$fileDate\n$sizeText",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 9.sp
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 3,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        scope.launch { deleteFile(fileInfo) }
                                                    },
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .padding(4.dp),
                                                    enabled = !isUploading
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Löschen",
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                if (fileInfo.isVideo) {
                                                    IconButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                            )
                                                            scope.launch { toggleFavorite(fileInfo) }
                                                        },
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .padding(4.dp),
                                                        enabled = !isUploading
                                                    ) {
                                                        Icon(
                                                            imageVector = if (fileInfo.isFavorite)
                                                                Icons.Default.Star else Icons.Default.StarBorder,
                                                            contentDescription = "Favorit",
                                                            tint = if (fileInfo.isFavorite) Color.Yellow else Color.White,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Export-Button
            if (fileList.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val (success, fail) = exportAllFiles(fileList)
                            val msg = when {
                                fail == 0 -> "✅ $success Dateien nach Downloads/Other exportiert"
                                success == 0 -> "❌ Export fehlgeschlagen"
                                else -> "⚠️ $success exportiert, $fail fehlgeschlagen"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    containerColor = gruen
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Alle exportieren",
                        tint = Color.White
                    )
                }
            }

            FloatingActionButton(
                onClick = { imagePickerLauncher.launch("*/*") },
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
        }

        // Upload-Fortschritt
        uploadProgress?.let { (current, total) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(0.8f)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Dateien werden gespeichert",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$current von $total",
                            style = MaterialTheme.typography.bodyLarge
                        )
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
            "favorites" -> emptyList() // Favoriten zeigt nur Videos
            else -> fileList.filter { !it.isVideo }
        }

        FullscreenImageDialogLocal(
            fileInfo = imageFiles[currentImageIndex],
            allImages = imageFiles,
            currentIndex = currentImageIndex,
            onDismiss = { showFullscreenImage = null },
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
            "favorites" -> fileList.filter { it.isFavorite && it.isVideo }
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
                        val nameIndex =
                            cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                }

                val privateDir = File(context.filesDir, "shared_files")
                if (!privateDir.exists()) {
                    privateDir.mkdirs()
                }

                var targetFile = File(privateDir, fileName)
                var counter = 1
                while (targetFile.exists()) {
                    val nameWithoutExt = fileName.substringBeforeLast(".")
                    val extension =
                        if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
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

suspend fun exportAllFiles(fileList: List<LocalFileInfo>): Pair<Int, Int> {
    return withContext(Dispatchers.IO) {
        val targetDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Other"
        )
        if (!targetDir.exists()) targetDir.mkdirs()

        var success = 0
        var fail = 0

        fileList.forEach { fileInfo ->
            try {
                var targetFile = File(targetDir, fileInfo.fileName)
                var counter = 1
                while (targetFile.exists()) {
                    val nameWithoutExt = fileInfo.fileName.substringBeforeLast(".")
                    val ext = if (fileInfo.fileName.contains(".")) ".${fileInfo.fileName.substringAfterLast(".")}" else ""
                    targetFile = File(targetDir, "${nameWithoutExt}_$counter$ext")
                    counter++
                }
                fileInfo.file.copyTo(targetFile)
                success++
            } catch (e: Exception) {
                e.printStackTrace()
                fail++
            }
        }

        Pair(success, fail)
    }
}

@Composable
fun FullscreenImageDialogLocal(
    fileInfo: LocalFileInfo,
    allImages: List<LocalFileInfo>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onIndexChanged: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentIndex) {
        scale = 1f
        offset = Offset.Zero
    }

    BackHandler {
        if (scale > 1f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(currentIndex) {
                awaitEachGesture {
                    var swipeTotalDx = 0f
                    var swipeTotalDy = 0f
                    var swipeConsumed = false

                    awaitFirstDown(requireUnconsumed = false)

                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                        scale = newScale

                        if (scale > 1f) {
                            offset += panChange
                            swipeConsumed = true
                        } else {
                            offset = Offset.Zero
                            if (!swipeConsumed) {
                                swipeTotalDx += panChange.x
                                swipeTotalDy += panChange.y
                                val absDx = kotlin.math.abs(swipeTotalDx)
                                val absDy = kotlin.math.abs(swipeTotalDy)

                                if (absDy > absDx && absDy > 150f) {
                                    swipeConsumed = true
                                    scope.launch { onDismiss() }
                                } else if (absDx > absDy && absDx > 50f) {
                                    swipeConsumed = true
                                    if (swipeTotalDx > 0f && currentIndex > 0) {
                                        onIndexChanged(currentIndex - 1)
                                    } else if (swipeTotalDx < 0f && currentIndex < allImages.size - 1) {
                                        onIndexChanged(currentIndex + 1)
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = fileInfo.file),
            contentDescription = fileInfo.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
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
                var totalDx = 0f
                var totalDy = 0f
                val verticalThreshold = 150f
                val horizontalThreshold = 50f

                detectDragGestures(
                    onDragStart = {
                        totalDx = 0f
                        totalDy = 0f
                        isSwiping = false
                    },
                    onDrag = { _, dragAmount ->
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y

                        val absDx = kotlin.math.abs(totalDx)
                        val absDy = kotlin.math.abs(totalDy)

                        if (!isSwiping) {
                            if (absDy > absDx && absDy > verticalThreshold) {
                                isSwiping = true
                                scope.launch { onDismiss() }
                            } else if (absDx > absDy && absDx > horizontalThreshold) {
                                isSwiping = true
                                if (totalDx > 0f && currentIndex > 0) {
                                    scope.launch {
                                        loadVideoAtIndex(currentIndex - 1)
                                        playerView?.hideController()
                                    }
                                } else if (totalDx < 0f && currentIndex < videoFiles.size - 1) {
                                    scope.launch {
                                        loadVideoAtIndex(currentIndex + 1)
                                        playerView?.hideController()
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = { isSwiping = false },
                    onDragCancel = { isSwiping = false }
                )
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

        // In VideoPlayerDialogLocal - Box mit Video-Info erweitern
        if (videoFiles.size > 1 || videoFiles.getOrNull(currentIndex)?.isFavorite == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (videoFiles.getOrNull(currentIndex)?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.Yellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (videoFiles.size > 1) {
                        Text(
                            text = "${currentIndex + 1} / ${videoFiles.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
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

suspend fun saveThumbnailToCache(context: Context, videoPath: String, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "video_thumbnails")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val fileName = videoPath.hashCode().toString() + ".jpg"
            val cacheFile = File(cacheDir, fileName)

            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun loadThumbnailFromCache(context: Context, videoPath: String): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "video_thumbnails")
            val fileName = videoPath.hashCode().toString() + ".jpg"
            val cacheFile = File(cacheDir, fileName)

            if (cacheFile.exists()) {
                BitmapFactory.decodeFile(cacheFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}