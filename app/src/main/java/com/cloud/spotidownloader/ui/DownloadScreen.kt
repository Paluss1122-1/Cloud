package com.cloud.spotidownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloud.spotidownloader.domain.DownloadState

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.downloadState.collectAsState()
    var url by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Spotify URL") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.startDownload(url) },
            enabled = state is DownloadState.Idle || state is DownloadState.Success || state is DownloadState.Error
        ) {
            Text("Download")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        when (state) {
            is DownloadState.Idle -> Text("Enter a URL to start")
            is DownloadState.Searching -> CircularProgressIndicator()
            is DownloadState.Downloading -> {
                val progress = (state as DownloadState.Downloading).progress
                LinearProgressIndicator(progress = { progress / 100f })
                Text("Downloading: $progress%")
            }
            is DownloadState.Converting -> {
                CircularProgressIndicator()
                Text("Converting to MP3...")
            }
            is DownloadState.Success -> Text("Download Complete!")
            is DownloadState.Error -> Text("Error: ${(state as DownloadState.Error).message}")
        }
    }
}
