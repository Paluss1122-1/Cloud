package com.example.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import com.example.cloud.ui.theme.gruen
import com.example.cloud.ui.theme.hellgruen
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.seconds
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    private lateinit var supabase: io.github.jan.supabase.SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supabase = createSupabaseClient(
            supabaseUrl = SupabaseConfig.SUPABASE_URL,
            supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
        ) {
            install(Storage)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrivateCloudApp(supabase.storage)
                }
            }
        }
    }

    @Composable
    fun PrivateCloudApp(storage: Storage) {
        var fileList by remember { mutableStateOf<List<String>>(emptyList()) }
        var isUploading by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf<String?>(null) }
        var selectedFilter by remember { mutableStateOf("Alle") }
        var refreshTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) } // NEU!
        var sortOption by remember { mutableStateOf("A-Z") }
        var uploadProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        var downloadProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        var showUploadProgress by remember { mutableStateOf(false) }
        var showDownloadProgress by remember { mutableStateOf(false) }
        var favoriteFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        data class SelectedFile(
            val uri: Uri,
            val originalName: String,
            var editableName: String,
        )

        fun isImageFile(fileName: String): Boolean {
            return fileName.lowercase().let {
                it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                        it.endsWith(".png") || it.endsWith(".gif") ||
                        it.endsWith(".webp")
            }
        }

        fun fileExistsInDownloads(fileName: String): File? {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            return if (file.exists()) file else null
        }

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

        fun getFileNameFromUri(uri: Uri, context: Context): String? {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) {
                    return it.getString(nameIndex)
                }
            }
            return null
        }

        val filePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val clip = result.data?.clipData
                val dataUri = result.data?.data
                val newSelectedFiles = mutableListOf<SelectedFile>()

                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        val uri = clip.getItemAt(i).uri
                        val name = getFileNameFromUri(uri, context)
                            ?: "Datei$i"
                        newSelectedFiles += SelectedFile(uri, name, name)
                    }
                } else if (dataUri != null) {
                    val name = getFileNameFromUri(dataUri, context)
                        ?: "Datei"  // <-- hier Context mitgeben
                    newSelectedFiles += SelectedFile(dataUri, name, name)
                }
            }
        }

        suspend fun deleteFile(fileName: String) {
            try {
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

        val filteredFileList =
            remember(fileList, selectedFilter, refreshTrigger) { // refreshTrigger hinzufügen
                when (selectedFilter) {
                    "Bilder" -> fileList.filter {
                        val fileName = it.substringBefore("|")
                        isImageFile(fileName)
                    }

                    "Dateien" -> fileList.filter {
                        val fileName = it.substringBefore("|")
                        !isImageFile(fileName)
                    }

                    else -> fileList
                }
            }

        LaunchedEffect(Unit) { loadFiles() }

        fun fileExistsInDCIM(fileName: String): File? {
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val appFolder = File(dcimDir, "Cloud") // z.B. "CloudApp"
            val file = File(appFolder, fileName)
            return if (file.exists()) file else null
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
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    filter,
                                    color = if (selectedFilter == filter) Color.Black else Color.White
                                )
                            })
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("A-Z", "Größte Datei", "Zuletzt hochgeladen").forEach { sort ->
                        FilterChip(
                            selected = sortOption == sort,
                            onClick = { sortOption = sort },
                            label = {
                                Text(
                                    sort,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = if (sortOption == sort) Color.Black else Color.White
                                )
                            }
                        )
                    }
                }

                if (filteredFileList.isEmpty()) {
                    Text(
                        "Keine ${if (selectedFilter == "Alle") "Dateien" else selectedFilter} vorhanden",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }

                var searchQuery by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()  // <- NUR fillMaxWidth!
                        .weight(1f)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Suchleiste oben
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Suche", color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true
                    )

                    // Filtered & searched list
                    val filteredFileList = remember(
                        fileList,
                        selectedFilter,
                        searchQuery,
                        sortOption,
                        refreshTrigger,
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
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f),
                                            onClick = {
                                                val localImageFile = fileExistsInDCIM(fileName)
                                                if (localImageFile != null) {
                                                    // Bild öffnen
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
                                                }
                                            }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                val publicUrl = remember(fileName) {
                                                    runBlocking {
                                                        storage.from(SupabaseConfig.SUPABASE_BUCKET)
                                                            .createSignedUrl(
                                                                fileName,
                                                                (60 * 10).seconds
                                                            )
                                                    }
                                                }
                                                Image(
                                                    painter = rememberAsyncImagePainter(publicUrl),
                                                    contentDescription = fileName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
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
                                                        val localImageFile =
                                                            fileExistsInDCIM(fileName)
                                                        val imageAlreadyDownloaded =
                                                            localImageFile != null
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
                                                                    // Bild herunterladen
                                                                    isDownloading = file
                                                                    scope.launch {
                                                                        showDownloadProgress = true
                                                                        downloadProgress = 0f
                                                                        try {
                                                                            // Simuliere Progress
                                                                            for (i in 0..10) {
                                                                                downloadProgress =
                                                                                    i / 10f
                                                                                kotlinx.coroutines.delay(
                                                                                    100
                                                                                )
                                                                            }

                                                                            val data = withContext(
                                                                                Dispatchers.IO
                                                                            ) {
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
                                                                                File(
                                                                                    dcimDir,
                                                                                    "Cloud"
                                                                                )
                                                                            if (!appFolder.exists()) {
                                                                                appFolder.mkdirs() // Ordner erstellen falls nicht vorhanden
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
                                                                                Uri.fromFile(
                                                                                    outputFile
                                                                                )
                                                                            context.sendBroadcast(
                                                                                mediaScanIntent
                                                                            )
                                                                            Toast.makeText(
                                                                                context,
                                                                                "Bild gespeichert ✅",
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()
                                                                            downloadProgress = 1f
                                                                        } catch (e: Exception) {
                                                                            val clipboard =
                                                                                context.getSystemService(
                                                                                    CLIPBOARD_SERVICE
                                                                                ) as ClipboardManager
                                                                            val clip =
                                                                                ClipData.newPlainText(
                                                                                    "code",
                                                                                    e.message
                                                                                )
                                                                            Toast.makeText(
                                                                                context,
                                                                                "Fehler: ${e.message}",
                                                                                Toast.LENGTH_LONG
                                                                            ).show()
                                                                            clipboard.setPrimaryClip(
                                                                                clip
                                                                            )
                                                                        } finally {
                                                                            isDownloading = null
                                                                            kotlinx.coroutines.delay(
                                                                                500
                                                                            )
                                                                            showDownloadProgress =
                                                                                false
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
                                                                        tint = Color.White
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    deleteFile(
                                                                        file
                                                                    )
                                                                }
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
                                val localFile = fileExistsInDownloads(fileName)
                                val showOpenButton = localFile != null
                                val fileDate =
                                    parts.getOrNull(1)?.replace("T", " ")?.substringBefore(".")
                                        ?: "Unbekannt"
                                val filesize =
                                    parts.getOrNull(2)?.replace("T", " ")?.substringBefore(".")
                                        ?: "Unbekannt"

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.DarkGray)
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = fileName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 20.sp
                                                ),
                                                color = Color.White
                                            )

                                            Text(
                                                text = "Hochgeladen: $fileDate",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )

                                            val sizeBytes = filesize.toLongOrNull() ?: 0L
                                            val sizeText = when {
                                                sizeBytes >= 1_000_000_000 -> "Dateigröße: %.2f GB".format(
                                                    sizeBytes / 1_000_000_000.0
                                                )

                                                sizeBytes >= 1_000_000 -> "Dateigröße: %.2f MB".format(
                                                    sizeBytes / 1_000_000.0
                                                )

                                                else -> "Dateigröße: %.1f KB".format(sizeBytes / 1_000.0)
                                            }

                                            Text(
                                                text = sizeText,
                                                fontSize = 14.sp,
                                                color = Color.Gray
                                            )
                                            val isFavorite = favoriteFiles.contains(fileName)
                                            // Favoriten-Button
                                            IconButton(
                                                onClick = {
                                                    favoriteFiles = if (isFavorite) {
                                                        favoriteFiles - fileName
                                                    } else {
                                                        favoriteFiles + fileName
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                    contentDescription = "Favorit",
                                                    tint = if (isFavorite) Color.Yellow else Color.White
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.align(Alignment.CenterEnd),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (showOpenButton && !isImageFile(file)) {
                                                IconButton(onClick = {
                                                    val fileUri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        localFile
                                                    )
                                                    val mimeType =
                                                        context.contentResolver.getType(fileUri)
                                                            ?: getMimeType(file)
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(fileUri, mimeType)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                }) {
                                                    Icon(
                                                        Icons.Filled.ArrowDropDown,
                                                        contentDescription = "Öffnen",
                                                        tint = Color.White
                                                    )
                                                }
                                            } else if (isImageFile(file) && fileExistsInDCIM(file) != null) {
                                                IconButton(onClick = {
                                                    val imageFile = fileExistsInDCIM(file)!!
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
                                                }) {
                                                    Icon(
                                                        Icons.Filled.ArrowDropDown,
                                                        contentDescription = "Öffnen",
                                                        tint = Color.White
                                                    )
                                                }
                                            } else {
                                                IconButton(onClick = {
                                                    isDownloading = file
                                                    scope.launch {
                                                        showDownloadProgress = true
                                                        downloadProgress = 0f
                                                        try {
                                                            // Simuliere Progress
                                                            for (i in 0..10) {
                                                                downloadProgress = i / 10f
                                                                kotlinx.coroutines.delay(100)
                                                            }
                                                            val data = withContext(Dispatchers.IO) {
                                                                storage.from(SupabaseConfig.SUPABASE_BUCKET)
                                                                    .downloadAuthenticated(
                                                                        file.substringBefore(
                                                                            "|"
                                                                        )
                                                                    )
                                                            }

                                                            val isImage = isImageFile(fileName)

                                                            val targetDir = if (isImage) {
                                                                val dcimDir =
                                                                    Environment.getExternalStoragePublicDirectory(
                                                                        Environment.DIRECTORY_DCIM
                                                                    )
                                                                val appFolder =
                                                                    File(dcimDir, "Cloud")
                                                                if (!appFolder.exists()) {
                                                                    appFolder.mkdirs()
                                                                }
                                                                appFolder
                                                            } else {
                                                                Environment.getExternalStoragePublicDirectory(
                                                                    Environment.DIRECTORY_DOWNLOADS
                                                                )
                                                            }

                                                            val outputFile =
                                                                File(targetDir, fileName)
                                                            withContext(Dispatchers.IO) {
                                                                FileOutputStream(outputFile).use { fos ->
                                                                    fos.write(data)
                                                                }
                                                            }
                                                            if (isImage) {
                                                                val mediaScanIntent =
                                                                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                                                mediaScanIntent.data =
                                                                    Uri.fromFile(outputFile)
                                                                context.sendBroadcast(
                                                                    mediaScanIntent
                                                                )
                                                            }
                                                            Toast.makeText(
                                                                context,
                                                                "Datei gespeichert in ${if (isImage) "DCIM (Galerie)" else "Downloads"} ✅",
                                                                Toast.LENGTH_SHORT
                                                            ).show()

                                                            downloadProgress = 1f

                                                            if (file.endsWith(".apk")) {
                                                                val apkUri =
                                                                    FileProvider.getUriForFile(
                                                                        context,
                                                                        "${context.packageName}.fileprovider",
                                                                        outputFile
                                                                    )
                                                                val installIntent =
                                                                    Intent(Intent.ACTION_VIEW).apply {
                                                                        setDataAndType(
                                                                            apkUri,
                                                                            "application/vnd.android.package-archive"
                                                                        )
                                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }
                                                                context.startActivity(installIntent)
                                                                return@launch
                                                            }

                                                            val fileUri =
                                                                FileProvider.getUriForFile(
                                                                    context,
                                                                    "${context.packageName}.fileprovider",
                                                                    outputFile
                                                                )
                                                            val mimeType =
                                                                context.contentResolver.getType(
                                                                    fileUri
                                                                )
                                                                    ?: getMimeType(file)
                                                            val openIntent =
                                                                Intent(Intent.ACTION_VIEW).apply {
                                                                    setDataAndType(
                                                                        fileUri,
                                                                        mimeType
                                                                    )
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                            context.startActivity(openIntent)
                                                        } catch (e: Exception) {
                                                            val clipboard =
                                                                context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                                            val clip =
                                                                ClipData.newPlainText(
                                                                    "code",
                                                                    e.message
                                                                )
                                                            Toast.makeText(
                                                                context,
                                                                "Fehler: ${e.message}",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            clipboard.setPrimaryClip(clip)
                                                        } finally {
                                                            isDownloading = null
                                                            kotlinx.coroutines.delay(500)
                                                            showDownloadProgress = false
                                                        }
                                                    }
                                                }) {
                                                    if (isDownloading == file) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(
                                                                20.dp
                                                            )
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Filled.ArrowDropDown,
                                                            contentDescription = "Download"
                                                        )
                                                    }
                                                }
                                            }

                                            IconButton(onClick = {
                                                scope.launch { deleteFile(file) }
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Löschen",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
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
                            filePicker.launch(intent)
                        },
                        enabled = !isUploading
                    ) {
                        Text("Datei auswählen & hochladen", fontSize = 12.sp)
                    }

                    Button(onClick = {
                        scope.launch {
                            loadFiles()
                            refreshTrigger++ // NEU! Erzwingt Recomposition
                            Toast.makeText(context, "Liste aktualisiert ✅", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }) {
                        Text("🔄 Aktualisieren", fontSize = 12.sp)
                    }

                    IconButton(onClick = {
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
                            // Fallback: Öffne Downloads-Ordner über Files-App
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
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        // Upload Progress Overlay
        if (showUploadProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Wird hochgeladen...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = uploadProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${(uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
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
                        .fillMaxWidth(0.8f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Wird heruntergeladen...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun getMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "txt" -> "text/plain"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}
