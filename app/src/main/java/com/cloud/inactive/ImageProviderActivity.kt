@file:Suppress("AssignedValueIsNeverRead")

package com.cloud.inactive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class ImageProviderActivity : ComponentActivity() {

    private val imageUrls = listOf(
        "https://picsum.photos/id/10/600/600",
        "https://picsum.photos/id/20/600/600",
        "https://picsum.photos/id/30/600/600",
        "https://picsum.photos/id/40/600/600",
        "https://picsum.photos/id/50/600/600",
        "https://picsum.photos/id/60/600/600",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action != Intent.ACTION_GET_CONTENT) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            MaterialTheme {
                ImagePickerScreen(
                    images = imageUrls,
                    onImageSelected = { url -> pickImage(url) }
                )
            }
        }
    }

    private fun pickImage(url: String) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { downloadToCache(url) }
            if (uri != null) {
                setResult(RESULT_OK, Intent().apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } else {
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }

    private fun downloadToCache(url: String): Uri? = try {
        val file = File(cacheDir, "picked_${System.currentTimeMillis()}.jpg")
        URL(url).openStream().use { input ->
            FileOutputStream(file).use { input.copyTo(it) }
        }
        FileProvider.getUriForFile(this, "${packageName}.provider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
private fun ImagePickerScreen(images: List<String>, onImageSelected: (String) -> Unit) {
    var loading by remember { mutableStateOf(false) }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(Modifier
        .fillMaxSize()
        .padding(8.dp)) {
        items(images) { url ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { loading = true; onImageSelected(url) }
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}