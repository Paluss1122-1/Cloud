package com.example.cloud.privatecloudapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent)
            else -> {
                Toast.makeText(this, "Ungültiger Share-Intent", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun handleSingleShare(intent: Intent) {
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) {
            showConfirmationDialog(listOf(uri))
        } else {
            Toast.makeText(this, "Keine Datei gefunden", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handleMultipleShare(intent: Intent) {
        val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (uris != null && uris.isNotEmpty()) {
            showConfirmationDialog(uris)
        } else {
            Toast.makeText(this, "Keine Dateien gefunden", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showConfirmationDialog(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                SaveToPrivateStorageScreen(
                    fileCount = uris.size,
                    onConfirm = {
                        saveFilesToPrivateStorage(uris)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "unknown_file_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        }
        return fileName
    }

    private fun getPrivateStorageDirectory(): File {
        // filesDir ist der private App-Speicher, nicht für andere Apps zugänglich
        // Alternative: getExternalFilesDir(null) für privaten externen Speicher
        val privateDir = File(filesDir, "shared_files")
        if (!privateDir.exists()) {
            privateDir.mkdirs()
        }
        return privateDir
    }

    private fun saveFilesToPrivateStorage(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                ProcessingScreen(fileCount = uris.size)
            }
        }

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            try {
                val privateDir = getPrivateStorageDirectory()

                for (uri in uris) {
                    try {
                        val fileName = getFileNameFromUri(uri)

                        // Sicherstellen, dass der Dateiname eindeutig ist
                        val targetFile = getUniqueFile(privateDir, fileName)

                        // Datei einlesen und in privaten Speicher kopieren
                        val success = withContext(Dispatchers.IO) {
                            copyUriToFile(uri, targetFile)
                        }

                        if (success) {
                            Log.d("ShareActivity", "Datei gespeichert: ${targetFile.absolutePath}")
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        Log.e("ShareActivity", "Fehler beim Speichern: ${e.message}", e)
                        failCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = when {
                        failCount == 0 -> "✅ ${if (successCount == 1) "Datei" else "$successCount Dateien"} gespeichert!"
                        successCount == 0 -> "❌ Speichern fehlgeschlagen"
                        else -> "⚠️ $successCount erfolgreich, $failCount fehlgeschlagen"
                    }
                    Toast.makeText(this@ShareActivity, message, Toast.LENGTH_LONG).show()
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ShareActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun getUniqueFile(directory: File, fileName: String): File {
        var file = File(directory, fileName)
        var counter = 1

        // Wenn Datei bereits existiert, füge Nummer hinzu
        while (file.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val extension = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
            file = File(directory, "${nameWithoutExt}_$counter$extension")
            counter++
        }

        return file
    }

    private fun copyUriToFile(uri: Uri, targetFile: File): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("ShareActivity", "Fehler beim Kopieren: ${e.message}", e)
            false
        }
    }
}

@Composable
fun SaveToPrivateStorageScreen(
    fileCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🔒 Privat speichern",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (fileCount == 1) "1 Datei" else "$fileCount Dateien",
                    fontSize = 16.sp,
                    color = Color.LightGray
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = DividerDefaults.Thickness,
                    color = Color.Gray
                )

                Text(
                    text = "Die Dateien werden in einem privaten Speicherbereich gespeichert:",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF333333)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoRow("🔒 Sichtbarkeit:", "Nur diese App")
                        InfoRow("📁 Speicherort:", "App-interner Speicher")
                        InfoRow("🚫 Gallery-Zugriff:", "Nein")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Speichern",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Abbrechen",
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.LightGray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProcessingScreen(fileCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = Color(0xFF4CAF50)
                )

                Text(
                    text = "Speichere...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (fileCount == 1) "1 Datei wird gespeichert" else "$fileCount Dateien werden gespeichert",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}