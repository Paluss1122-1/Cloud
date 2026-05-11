package com.cloud.tabs.school

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.cloud.core.objects.Config
import com.cloud.core.objects.toast
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MaterialUploadState(
    val fileName: String,
    val status: UploadStatus
)

enum class UploadStatus { PENDING, UPLOADING, DONE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialienScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    var uploads by remember { mutableStateOf<List<MaterialUploadState>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var foldersLoading by remember { mutableStateOf(true) }
    var folderFiles by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var folderFilesLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val files = Config.client.storage.from("school").list()
            toast(context, "$files")
            Log.d("CLOUDSA", "$files")
            folders = files.map { it.name }.filter { it.isNotBlank() }.sortedDescending()
        } catch (e: Exception) {
            errorMsg = e.localizedMessage
        } finally {
            foldersLoading = false
        }
    }
    LaunchedEffect(selectedDate) {
        val date = selectedDate ?: return@LaunchedEffect
        if (folderFiles.containsKey(date)) return@LaunchedEffect
        folderFilesLoading = true
        try {
            val files = Config.client.storage.from("school").list(date)
            folderFiles = folderFiles + (date to files.map { it.name })
        } catch (e: Exception) {
            errorMsg = e.localizedMessage
        } finally {
            folderFilesLoading = false
        }
    }

    BackHandler {
        if (selectedDate != null) selectedDate = null else onBack()
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val newUploads = uris.map { uri ->
            MaterialUploadState(
                fileName = getFileName(context, uri),
                status = UploadStatus.PENDING
            )
        }
        uploads = uploads + newUploads

        uris.forEachIndexed { i, uri ->
            val state = newUploads[i]
            scope.launch {
                uploads = uploads.map {
                    if (it.fileName == state.fileName) it.copy(status = UploadStatus.DONE) else it
                }
                if (selectedDate != null && !folders.contains(selectedDate)) {
                    folders = (listOf(selectedDate!!) + folders).sortedDescending()
                }
                try {
                    val rawBytes = context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Datei nicht lesbar")
                    val (uploadBytes, uploadName) = compressToJpgIfImage(rawBytes, state.fileName)
                    val path = "$selectedDate/$uploadName"
                    Config.client.storage.from("school").upload(path, uploadBytes) { upsert = true }
                    uploads = uploads.map {
                        if (it.fileName == state.fileName) it.copy(status = UploadStatus.DONE) else it
                    }
                    folderFiles = folderFiles + (selectedDate!! to (folderFiles[selectedDate] ?: emptyList()) + uploadName)
                } catch (e: Exception) {
                    errorMsg = e.localizedMessage
                    uploads = uploads.map {
                        if (it.fileName == state.fileName) it.copy(status = UploadStatus.ERROR) else it
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
                        selectedDate = date
                        uploads = emptyList()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = { if (selectedDate != null) selectedDate = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Materialien", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedDate != null) "School / $selectedDate" else "School",
                    color = TextTertiary, fontSize = 12.sp
                )
            }
            if (selectedDate != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSurface)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(selectedDate!!, color = AccentViolet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(errorMsg != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚠️", fontSize = 14.sp)
                Text(errorMsg ?: "", color = Color(0xFFEF9A9A), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("✕", color = TextTertiary, modifier = Modifier.clickable { errorMsg = null })
            }
        }

        if (selectedDate == null) {
            // Ordner-Grid
            if (foldersLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentViolet)
                }
            } else if (folders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🗂️", fontSize = 48.sp)
                        Text("Keine Ordner vorhanden", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Erstelle einen neuen Ordner via +", color = TextTertiary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(folders) { folder ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(BgSurface)
                                .clickable { selectedDate = folder; uploads = emptyList() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("📁", fontSize = 42.sp)
                                Text(
                                    folder,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.align(Alignment.End).padding(20.dp),
                containerColor = AccentViolet,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Add, "Ordner erstellen")
            }
        } else {
            if (folderFilesLoading) {
                Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AccentViolet)
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (uploads.isEmpty() && (folderFiles[selectedDate] ?: emptyList()).isEmpty() && !folderFilesLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📂", fontSize = 48.sp)
                                Text("Noch keine Dateien", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Tippe + um Dateien hochzuladen", color = TextTertiary, fontSize = 13.sp)
                            }
                        }
                    }
                }
                val existingFiles = (folderFiles[selectedDate] ?: emptyList())
                    .filter { name -> uploads.none { it.fileName == name } }

                items(existingFiles) { fileName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(fileEmoji(fileName), fontSize = 22.sp)
                        Text(fileName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("✓", color = Color(0xFF66BB6A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                items(uploads) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(fileEmoji(item.fileName), fontSize = 22.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.fileName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                when (item.status) {
                                    UploadStatus.PENDING -> "Warte..."
                                    UploadStatus.UPLOADING -> "Hochladen..."
                                    UploadStatus.DONE -> "✓ Fertig"
                                    UploadStatus.ERROR -> "Fehler"
                                },
                                color = when (item.status) {
                                    UploadStatus.DONE -> Color(0xFF66BB6A)
                                    UploadStatus.ERROR -> Color(0xFFEF5350)
                                    else -> TextTertiary
                                },
                                fontSize = 12.sp
                            )
                        }
                        when (item.status) {
                            UploadStatus.UPLOADING -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentViolet
                            )
                            UploadStatus.DONE -> Icon(
                                Icons.Default.CheckCircle, null,
                                tint = Color(0xFF66BB6A), modifier = Modifier.size(20.dp)
                            )
                            else -> {}
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            FloatingActionButton(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.align(Alignment.End).padding(20.dp),
                containerColor = AccentViolet,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Add, "Datei hinzufügen")
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "datei_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
    }
    return name
}

private fun fileEmoji(name: String) = when {
    name.endsWith(".pdf") -> "📄"
    name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") -> "🖼️"
    name.endsWith(".mp4") || name.endsWith(".mov") -> "🎬"
    name.endsWith(".mp3") || name.endsWith(".m4a") -> "🎵"
    name.endsWith(".docx") || name.endsWith(".doc") -> "📝"
    name.endsWith(".pptx") || name.endsWith(".ppt") -> "📊"
    else -> "📎"
}

private fun compressToJpgIfImage(
    bytes: ByteArray,
    fileName: String,
    quality: Int = 75,
    maxSize: Int = 2048
): Pair<ByteArray, String> {
    val lower = fileName.lowercase()
    val isImage = lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp")
    if (!isImage) return bytes to fileName

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return bytes to fileName

    val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        bitmap.scale((bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt())
    } else bitmap

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)

    val jpgName = fileName.substringBeforeLast(".") + ".jpg"
    return out.toByteArray() to jpgName
}