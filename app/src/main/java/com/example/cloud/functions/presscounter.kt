@file:Suppress("DEPRECATION")

package com.example.cloud.functions

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
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
    var uploadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) } // current, total
    var showDownloadProgress by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    suspend fun loadFiles() {
        try {
            val bucketName = "Other"

            val files = withContext(Dispatchers.IO) {
                storage.from(bucketName).list()
            }

            fileList = files
                .filter { it.name != ".emptyFolderPlaceholder" }
                .filter { isImageFile(it.name) }
                .map { "${it.name}|${it.updatedAt}|${it.metadata?.get("size") ?: 0}" }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Laden: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // Launcher für mehrere Bilder auswählen
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
                            delay(100) // Kurze Pause zwischen Uploads
                        } catch (e: Exception) {
                            failCount++
                            e.printStackTrace()
                        }
                    }

                    val message = when {
                        failCount == 0 -> "✅ Alle $successCount Bilder hochgeladen!"
                        successCount == 0 -> "❌ Upload fehlgeschlagen"
                        else -> "⚠️ $successCount hochgeladen, $failCount fehlgeschlagen"
                    }

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    loadFiles()

                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Fehler beim Upload: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    isUploading = false
                    uploadProgress = null
                }
            }
        }
    }

    suspend fun deleteFile(fileWithMetadata: String) {
        try {
            val fileName = fileWithMetadata.substringBefore("|")

            // Erst aus der Liste entfernen für sofortiges visuelles Feedback
            fileList = fileList.filter { it != fileWithMetadata }

            withContext(Dispatchers.IO) {
                storage.from("Other").delete(fileName)
            }

            // Liste neu laden um sicherzustellen dass es synchron ist
            delay(300)
            loadFiles()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fehler beim Löschen: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
            e.printStackTrace()
            // Bei Fehler Liste neu laden
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
            if (fileList.isEmpty()) {
                Text(
                    "Keine Bilder vorhanden",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fileList.chunked(2)) { rowFiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFiles.forEach { file ->
                                val parts = file.split("|")
                                val fileName = parts.getOrNull(0) ?: "Unbekannt"
                                val fileDate = parts.getOrNull(1)?.replace("T", " ")
                                    ?.substringBefore(".") ?: "Unbekannt"
                                val filesize = parts.getOrNull(2) ?: "0"

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    onClick = {
                                        if (!isUploading && isDownloading == null) {
                                            showFullscreenImage = Pair(fileName, file)
                                        }
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        var publicUrl by remember(fileName) {
                                            mutableStateOf<String?>(
                                                null
                                            )
                                        }

                                        LaunchedEffect(fileName) {
                                            try {
                                                publicUrl = storage.from("Other")
                                                    .createSignedUrl(fileName, 600.seconds)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }

                                        Image(
                                            painter = rememberAsyncImagePainter(publicUrl),
                                            contentDescription = fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Transparent)
                                        )

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
                                                        fileExistsLocallyWithSameSize(
                                                            fileName,
                                                            remoteSize
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
                                                    text = "$fileName\n$fileDate\n$sizeText",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 9.sp
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 3
                                                )

                                                if (!imageAlreadyDownloaded) {
                                                    IconButton(
                                                        onClick = {
                                                            isDownloading = file
                                                            scope.launch {
                                                                showDownloadProgress = true
                                                                try {
                                                                    val data =
                                                                        withContext(Dispatchers.IO) {
                                                                            storage.from("Other")
                                                                                .downloadAuthenticated(
                                                                                    fileName
                                                                                )
                                                                        }

                                                                    val dcimDir = Environment
                                                                        .getExternalStoragePublicDirectory(
                                                                            Environment.DIRECTORY_DCIM
                                                                        )
                                                                    val appFolder =
                                                                        File(dcimDir, "Cloud")
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

                                                                    val mediaScanIntent = Intent(
                                                                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
                                                                    )
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
                                                                imageVector = Icons.Default.ArrowDropDown,
                                                                contentDescription = "Download",
                                                                tint = Color.White
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
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        scope.launch { deleteFile(file) }
                                                    },
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .padding(4.dp),
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

        FloatingActionButton(
            onClick = {
                imagePickerLauncher.launch("image/*")
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = gruen
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Bilder hochladen",
                    tint = Color.White
                )
            }
        }

        // Upload Progress Overlay
        uploadProgress?.let { (current, total) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(0.8f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Bilder werden hochgeladen",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "$current von $total",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        LinearProgressIndicator(
                            progress = current.toFloat() / total.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            color = gruen
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(100.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = gruen,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }

    showFullscreenImage?.let { (displayFileName, fileWithMetadata) ->
        val parts = fileWithMetadata.split("|")
        val actualFileName = parts.getOrNull(0) ?: displayFileName
        val remoteSize = parts.getOrNull(2)?.toLongOrNull() ?: -1L
        val isDownloaded = remoteSize >= 0 && fileExistsLocallyWithSameSize(actualFileName, remoteSize)

        var imageUrl by remember(actualFileName) { mutableStateOf<String?>(null) }

        LaunchedEffect(actualFileName) {
            try {
                imageUrl = storage.from("Other").createSignedUrl(actualFileName, 600.seconds)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        FullscreenImageDialog(
            imageUrl = imageUrl,
            fileName = actualFileName,
            isDownloaded = isDownloaded,
            onDismiss = { showFullscreenImage = null },
            onDownload = {
                scope.launch {
                    isDownloading = fileWithMetadata
                    showDownloadProgress = true
                    try {
                        val data = withContext(Dispatchers.IO) {
                            storage.from("Other").downloadAuthenticated(actualFileName)
                        }

                        val dcimDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DCIM
                        )
                        val appFolder = File(dcimDir, "Cloud")
                        if (!appFolder.exists()) {
                            appFolder.mkdirs()
                        }
                        val outputFile = File(appFolder, actualFileName)

                        withContext(Dispatchers.IO) {
                            FileOutputStream(outputFile).use { fos ->
                                fos.write(data)
                            }
                        }

                        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        mediaScanIntent.data = Uri.fromFile(outputFile)
                        context.sendBroadcast(mediaScanIntent)

                        Toast.makeText(context, "Bild gespeichert ✅", Toast.LENGTH_SHORT).show()
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
                val localImageFile = fileExistsInDCIM(actualFileName)
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
}

suspend fun uploadImage(context: android.content.Context, storage: Storage, uri: Uri) {
    withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes != null) {
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            storage.from("Other").upload(fileName, bytes)
        }
    }
}