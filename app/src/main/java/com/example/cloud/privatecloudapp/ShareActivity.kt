package com.example.cloud.privatecloudapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.cloud.AesEncryption
import com.example.cloud.SupabaseConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareActivity : ComponentActivity() {
    lateinit var storage : Storage
    private val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.SUPABASE_URL,
            supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
        ) {
            install(Storage)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = supabase.storage

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                handleSingleShare(intent)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleMultipleShare(intent)
            }
            else -> {
                Toast.makeText(this, "Ungültiger Share-Intent", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handleSingleShare(intent: Intent) {
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) {
            showBucketSelectionDialog(listOf(uri))
        } else {
            Toast.makeText(this, "Keine Datei zum Hochladen", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handleMultipleShare(intent: Intent) {
        val uris =intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (uris != null && uris.isNotEmpty()) {
            showBucketSelectionDialog(uris)
        } else {
            Toast.makeText(this, "Keine Dateien zum Hochladen", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showBucketSelectionDialog(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                BucketSelectionScreen(
                    fileCount = uris.size,
                    onBucketSelected = { targetPath ->
                        uploadFilesToBucket(uris, targetPath)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun uploadFilesToBucket(uris: List<Uri>, targetPath: String) {
        // targetPath ist z.B. "Other/1", "Other/videos", "Files"
        val bucketName = if (targetPath.contains("/")) {
            targetPath.substringBefore("/")
        } else {
            targetPath
        }

        val folder = if (targetPath.contains("/")) {
            targetPath.substringAfter("/")
        } else {
            ""
        }

        setContent {
            MaterialTheme {
                UploadProgressScreen(
                    fileCount = uris.size,
                    targetName = targetPath
                )
            }
        }

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            try {
                for (uri in uris) {
                    try {
                        val fileName = getFileNameFromUri(uri, this@ShareActivity) ?: "unnamed_file_${System.currentTimeMillis()}"
                        val inputStream = contentResolver.openInputStream(uri)

                        if (inputStream != null) {
                            val rawData = inputStream.readBytes()
                            inputStream.close()

                            // Verschlüsseln, wenn es kein Bild/Video ist
                            val dataToUpload = if (isImageFile(fileName) || isVideoFile(fileName)) {
                                rawData
                            } else {
                                AesEncryption.encrypt(rawData)
                            }

                            // Upload mit korrektem Pfad
                            val uploadPath = if (folder.isNotEmpty()) {
                                "$folder/$fileName"
                            } else {
                                fileName
                            }

                            withContext(Dispatchers.IO) {
                                storage.from(bucketName).upload(uploadPath, dataToUpload)
                            }

                            successCount++
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
                        failCount == 0 -> "✅ ${if (successCount == 1) "Datei" else "$successCount Dateien"} erfolgreich hochgeladen!"
                        successCount == 0 -> "❌ Upload fehlgeschlagen"
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
}

@Composable
fun BucketSelectionScreen(
    fileCount: Int,
    onBucketSelected: (String) -> Unit,
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
                    text = "☁️ In Cloud hochladen",
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
                    thickness = DividerDefaults.Thickness, color = Color.Gray
                )

                Text(
                    text = "Ziel auswählen:",
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )

                // Bucket/Ordner-Buttons
                BucketButton(
                    icon = "📁",
                    name = "Files",
                    description = "Dokumente & Dateien",
                    onClick = { onBucketSelected(SupabaseConfig.SUPABASE_BUCKET) }
                )

                BucketButton(
                    icon = "🖼️",
                    name = "Other - Bilder",
                    description = "Bilder (Ordner 1)",
                    onClick = { onBucketSelected("Other/1") }
                )

                BucketButton(
                    icon = "🗂️",
                    name = "Other - Ordner 2",
                    description = "Bilder (Ordner 2)",
                    onClick = { onBucketSelected("Other/2") }
                )

                BucketButton(
                    icon = "🎥",
                    name = "Other - Videos",
                    description = "Video-Dateien",
                    onClick = { onBucketSelected("Other/videos") }
                )

                Spacer(modifier = Modifier.height(8.dp))

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
fun BucketButton(
    icon: String,
    name: String,
    description: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF444444)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = icon,
                fontSize = 32.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun UploadProgressScreen(
    fileCount: Int,
    targetName: String
) {
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
                    text = "Hochladen...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (fileCount == 1) "1 Datei" else "$fileCount Dateien",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )

                Text(
                    text = "→ $targetName",
                    fontSize = 14.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}