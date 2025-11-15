package com.example.cloud.privatecloudapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.cloud.SupabaseConfig
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

// Optional: Define this as a top-level function or inside the composable if preferred

@OptIn(SupabaseExperimental::class)
@Composable
fun FileIcon(
    fileName: String,
    storage: Storage, // ⬅️ Nimm Storage direkt
    modifier: Modifier = Modifier
) {
    val isImage = isImageFile(fileName)

    if (isImage) {
        var publicUrl by remember(fileName) { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(fileName) {
            isLoading = true
            try {
                val signedUrl = withContext(Dispatchers.IO) {
                    storage
                        .from(SupabaseConfig.SUPABASE_BUCKET)
                        .createSignedUrl(fileName, 600.seconds)
                }
                publicUrl = signedUrl
            } catch (e: Exception) {
                e.printStackTrace()
                publicUrl = null
            } finally {
                isLoading = false
            }
        }

        if (!isLoading && publicUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(publicUrl),
                contentDescription = fileName,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    } else {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val (icon, tint) = when (ext) {
            "apk" -> Icons.Default.Settings to Color(0xFF3DDC84)
            "pdf" -> Icons.Default.Description to Color.Red
            "mp4", "avi", "mkv", "mov" -> Icons.Default.Movie to Color(0xFFFF6B6B)
            "mp3", "wav", "flac", "aac" -> Icons.Default.Audiotrack to Color(0xFF9B59B6)
            "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Archive to Color(0xFFFFD700)
            "txt" -> Icons.AutoMirrored.Filled.TextSnippet to Color.White
            "doc", "docx" -> Icons.Default.Description to Color(0xFF2B579A)
            "xls", "xlsx" -> Icons.Default.Description to Color(0xFF217346)
            "jpg", "jpeg", "png", "gif", "webp" -> Icons.Default.Image to Color.Gray
            else -> Icons.Default.Description to Color.Gray
        }

        Icon(
            imageVector = icon,
            contentDescription = fileName,
            tint = tint,
            modifier = modifier
        )
    }
}