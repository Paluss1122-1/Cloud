package com.cloud.spotifydownloadertab

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spotifydownloader.data.DownloadRepositoryImpl
import com.example.spotifydownloader.ui.DownloadScreen
import com.example.spotifydownloader.ui.DownloadViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@Composable
fun SpotifyDownloaderTab() {
    val context = LocalContext.current

    val httpClient = remember {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    }
                )
            }
        }
    }

    val factory = remember(context, httpClient) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = DownloadRepositoryImpl(httpClient, context.applicationContext as Context)
                return DownloadViewModel(repo) as T
            }
        }
    }

    val vm: DownloadViewModel = viewModel(factory = factory)
    DownloadScreen(viewModel = vm)
}

