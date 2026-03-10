package com.example.cloud.gallery

import android.app.Activity
import android.content.ContentUris
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.cloud.ERRORINSERT
import com.example.cloud.ERRORINSERTDATA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class GalleryMediaItem(
    val uri: String,
    val isVideo: Boolean,
    val dateAdded: Long
)

@Composable
fun GalleryTab() {
    val mediaItems = remember { mutableStateOf(listOf<GalleryMediaItem>()) }
    val context = LocalContext.current

    var showFullscreenMedia by remember { mutableStateOf(false) }
    var fullscreenMediaUri by remember { mutableStateOf<String?>(null) }
    var isFullscreenVideo by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fullscreenMediaUri?.let { uriString ->
                mediaItems.value = mediaItems.value.filter { it.uri != uriString }
                showFullscreenMedia = false
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val images = loadImagesFromMediaStore(context)
            val videos = loadVideosFromMediaStore(context)
            mediaItems.value = (images + videos).sortedByDescending {
                it.dateAdded
            }
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color.DarkGray, Color.Black, Color.DarkGray)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        if (mediaItems.value.isEmpty()) {
            Text("Keine Medien gefunden", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(8.dp)
            ) {
                items(mediaItems.value) { mediaItem ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                    ) {
                        AsyncImage(
                            model = mediaItem.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    showFullscreenMedia = true
                                    fullscreenMediaUri = mediaItem.uri
                                    isFullscreenVideo = mediaItem.isVideo
                                }
                        )

                        if (mediaItem.isVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullscreenMedia) {
        if (isFullscreenVideo) {
            VideoPlayerFullscreen(
                videoUri = fullscreenMediaUri ?: "",
                onDismiss = { showFullscreenMedia = false },
                onDelete = {
                    fullscreenMediaUri?.let { uriString ->
                        val uri = uriString.toUri()
                        val pendingIntent = MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(uri)
                        )
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                }
            )
        } else {
            ImageFullscreen(
                imageUri = fullscreenMediaUri ?: "",
                onDismiss = { showFullscreenMedia = false },
                onDelete = {
                    fullscreenMediaUri?.let { uriString ->
                        val uri = uriString.toUri()
                        val pendingIntent = MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(uri)
                        )
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun ImageFullscreen(
    imageUri: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BackHandler { onDismiss() }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceAtLeast(1f)

        val maxX = (scale - 1) * 500f
        val maxY = (scale - 1) * 500f
        offset += offsetChange
        offset = Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = state)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Fullscreen",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Button(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("Löschen")
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerFullscreen(
    videoUri: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(ExoMediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("Löschen")
        }
    }
}

fun loadImagesFromMediaStore(context: android.content.Context): List<GalleryMediaItem> {
    val images = mutableListOf<GalleryMediaItem>()

    try {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        if (query == null) {
            return images
        }

        query.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                images.add(GalleryMediaItem(uri.toString(), isVideo = false, dateAdded = dateAdded))
            }
        }
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "GalleryTab",
                    "Fehler bei Laden von Bildern: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
        }
    }

    return images
}

fun loadVideosFromMediaStore(context: android.content.Context): List<GalleryMediaItem> {
    val videos = mutableListOf<GalleryMediaItem>()

    try {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        if (query == null) {
            return videos
        }

        query.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                videos.add(GalleryMediaItem(uri.toString(), isVideo = true, dateAdded = dateAdded))
            }
        }
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "GalleryTab",
                    "Fehler bei Laden von Videos: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
        }
    }

    return videos
}