package com.example.cloud.privatecloudapp

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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

    private fun getFileFromUri(uri: Uri): File? {
        // Nur wenn es eine Downloads-URI ist
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, arrayOf("_data"), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val path = it.getString(0)
                    if (path != null) return File(path)
                }
            }
        } else if (uri.scheme == "file") {
            return File(uri.path!!)
        }
        return null
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
                MetadataUpdateScreen(
                    fileCount = uris.size,
                    onConfirm = {
                        updateFileMetadata(uris)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "unknown_file"
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

    private fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }

    private fun updateFileMetadata(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                ProcessingScreen(fileCount = uris.size)
            }
        }

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            try {
                for (uri in uris) {
                    try {
                        val fileName = getFileNameFromUri(uri)
                        val currentTime = getCurrentTimestamp()

                        // Datei einlesen
                        val inputStream = contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val fileData = inputStream.readBytes()
                            inputStream.close()

                            // Metadaten aktualisieren und Datei überschreiben
                            val success = withContext(Dispatchers.IO) {
                                updateFileWithMetadata(uri, fileData, currentTime)
                            }

                            if (success) {
                                successCount++
                            } else {
                                failCount++
                            }
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = when {
                        failCount == 0 -> "✅ ${if (successCount == 1) "Datei" else "$successCount Dateien"} aktualisiert!"
                        successCount == 0 -> "❌ Aktualisierung fehlgeschlagen"
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

    private fun updateFileWithMetadata(uri: Uri, fileData: ByteArray, timestamp: Long): Boolean {
        return try {
            Log.d("ShareActivity", "Versuche Datei zu aktualisieren: $uri")

            val file = getFileFromUri(uri)
            if (file != null) {
                Log.d("ShareActivity", "Gefundener Pfad: ${file.absolutePath}")
                if (file.canWrite()) {
                    Log.d("ShareActivity", "Datei beschreibbar, schreibe Daten...")
                    file.writeBytes(fileData)
                    Log.d("ShareActivity", "Datei erfolgreich überschrieben")
                } else {
                    Log.w("ShareActivity", "Datei nicht beschreibbar: ${file.absolutePath}")
                    return false
                }
            } else {
                Log.w("ShareActivity", "Kein echter Pfad gefunden für URI: $uri")
                return false
            }

            // Optional: Metadaten setzen, falls MediaStore-Zugriff möglich
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                    put(MediaStore.MediaColumns.DATE_TAKEN, timestamp)
                }
                val updatedRows = contentResolver.update(uri, values, null, null)
                Log.d("ShareActivity", "MediaStore update: $updatedRows Zeilen aktualisiert")
            } catch (e: Exception) {
                Log.w("ShareActivity", "MediaStore update fehlgeschlagen: ${e.message}")
            }

            true
        } catch (e: Exception) {
            Log.e("ShareActivity", "Fehler beim Überschreiben der Datei: ${e.message}", e)
            false
        }
    }

}

@Composable
fun MetadataUpdateScreen(
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
                    text = "📝 Metadaten aktualisieren",
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
                    text = "Die folgenden Metadaten werden aktualisiert:",
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
                        MetadataRow("📅 Änderungsdatum:", "Jetzt")
                        MetadataRow("🕐 Zeitstempel:", "Aktuell")
                        MetadataRow("💾 Speicherort:", "Original überschreiben")
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
                        text = "Aktualisieren",
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
fun MetadataRow(label: String, value: String) {
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
                    text = "Verarbeite...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (fileCount == 1) "1 Datei wird aktualisiert" else "$fileCount Dateien werden aktualisiert",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}