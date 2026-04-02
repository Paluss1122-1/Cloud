package com.cloud

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
import com.cloud.quiethoursnotificationhelper.isLaptopConnected
import com.cloud.quiethoursnotificationhelper.isLaptopConnectedFlow
import com.cloud.quiethoursnotificationhelper.laptopIp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        val syncActive = prefs.getBoolean("sync_active", false)
        val syncUntil = prefs.getLong("sync_until", 0L)
        if (syncActive && syncUntil > System.currentTimeMillis()) {
            isLaptopConnected = true
        }

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
        if (!uris.isNullOrEmpty()) {
            showConfirmationDialog(uris)
        } else {
            Toast.makeText(this, "Keine Dateien gefunden", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showConfirmationDialog(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                val laptopConnected by isLaptopConnectedFlow.collectAsState()
                SaveToPrivateStorageScreen(
                    fileCount = uris.size,
                    isLaptopConnected = laptopConnected,
                    onSaveLocally = { saveFilesToPrivateStorage(uris) },
                    onSendToLaptop = { sendImagesToLaptop(uris) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun sendImagesToLaptop(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                ProcessingScreen(fileCount = uris.size)
            }
        }

        lifecycleScope.launch {
            var successCount = 0

            for (uri in uris) {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.readBytes()
                    } ?: continue

                    val fileName = getFileNameFromUri(uri)
                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

                    val sent = withContext(Dispatchers.IO) {
                        trySendImageToLaptop(bytes, fileName, mimeType)
                    }

                    if (sent) successCount++
                } catch (e: Exception) {
                    Log.e("ShareActivity", "Fehler beim Senden: ${e.message}", e)
                }
            }

            withContext(Dispatchers.Main) {
                val message = if (successCount > 0)
                    "📲 ${if (successCount == 1) "Bild" else "$successCount Bilder"} an Laptop gesendet!"
                else
                    "❌ Senden fehlgeschlagen"
                Toast.makeText(this@ShareActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun trySendImageToLaptop(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Boolean {
        if (laptopIp == "") return false
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(laptopIp, Config.IMAGE_SHARE_PORT), 3000)
            val out = socket.getOutputStream()

            val header = "$fileName|$mimeType\n".toByteArray(Charsets.UTF_8)
            out.write(header)
            out.write(bytes)
            out.flush()
            socket.close()
            return true
        } catch (_: Exception) {
        }
        return false
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

                        val targetFile = getUniqueFile(privateDir, fileName)

                        val success = withContext(Dispatchers.IO) {
                            copyUriToFile(uri, targetFile)
                        }

                        if (success) {
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (_: Exception) {
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
                    Toast.makeText(this@ShareActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
            }
        }
    }

    private fun getUniqueFile(directory: File, fileName: String): File {
        var file = File(directory, fileName)
        var counter = 1

        while (file.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val extension =
                if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
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
    isLaptopConnected: Boolean,
    onSaveLocally: () -> Unit,
    onSendToLaptop: () -> Unit,
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🔒 Datei speichern",
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
                    color = Color.Gray
                )

                if (isLaptopConnected) {
                    Button(
                        onClick = onSendToLaptop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("💻 An Laptop senden", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("💻 Laptop nicht verbunden", fontSize = 16.sp, color = Color.White)
                    }
                }

                Button(
                    onClick = onSaveLocally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📁 Lokal speichern", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Abbrechen", color = Color.Red, fontSize = 16.sp)
                }
            }
        }
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