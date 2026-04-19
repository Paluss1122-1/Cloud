package com.cloud.tabs

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cloud.core.functions.ERRORINSERT
import com.cloud.core.functions.ERRORINSERTDATA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.abs
import androidx.media3.common.MediaItem as ExoMediaItem

data class GalleryMediaItem(
    val uri: String,
    val isVideo: Boolean,
    val dateAdded: Long
)

private fun getVideoFirstFrame(uri: String, context: Context): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri.toUri())
        val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bitmap
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryTab() {
    val mediaItems = remember { mutableStateOf(listOf<GalleryMediaItem>()) }
    val context = LocalContext.current

    var showFullscreenMedia by remember { mutableStateOf(false) }
    var fullscreenMediaUri by remember { mutableStateOf<String?>(null) }
    var isFullscreenVideo by remember { mutableStateOf(false) }
    val thumbnailCache = remember { mutableStateMapOf<String, Bitmap>() }

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
            mediaItems.value = (images + videos).sortedByDescending { it.dateAdded }
        }
    }

    val gridState = rememberLazyGridState()

    SharedTransitionLayout {
        AnimatedContent(targetState = showFullscreenMedia, label = "gallery_transition") { isFullscreen ->
            if (!isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    if (mediaItems.value.isEmpty()) {
                        Text("Keine Medien gefunden", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
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
                                            .sharedElement(
                                                rememberSharedContentState(key = mediaItem.uri),
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                            .clickable {
                                                showFullscreenMedia = true
                                                fullscreenMediaUri = mediaItem.uri
                                                isFullscreenVideo = mediaItem.isVideo
                                            }
                                    )

                                    if (mediaItem.isVideo) {
                                        val thumbnail by produceState<Bitmap?>(thumbnailCache[mediaItem.uri], mediaItem.uri) {
                                            if (value == null) {
                                                value = withContext(Dispatchers.IO) {
                                                    val bmp = getVideoFirstFrame(mediaItem.uri, context)
                                                    if (bmp != null) {
                                                        saveThumbnailToCache(context, mediaItem.uri, bmp)
                                                        thumbnailCache[mediaItem.uri] = bmp
                                                    }
                                                    bmp
                                                }
                                            }
                                        }
                                        thumbnail?.let {
                                            AsyncImage(
                                                model = it,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
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
                        animatedVisibilityScope = this@AnimatedContent,
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
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ImageFullscreen(
    imageUri: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BackHandler {
        scale = 1f
        offset = Offset.Zero
        onDismiss()
    }

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
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = state)

                .pointerInput(Unit) {
                    awaitEachGesture {
                        var swipeTotalDx = 0f
                        var swipeTotalDy = 0f
                        var swipeConsumed = false

                        awaitFirstDown(requireUnconsumed = false)

                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale

                            if (scale > 1f) {
                                offset += panChange
                                swipeConsumed = true
                            } else {
                                offset = Offset.Zero
                                if (!swipeConsumed) {
                                    swipeTotalDx += panChange.x
                                    swipeTotalDy += panChange.y
                                    val absDx = abs(swipeTotalDx)
                                    val absDy = abs(swipeTotalDy)

                                    if (absDy > absDx && absDy > 150f) {
                                        swipeConsumed = true
                                        onDismiss()
                                    }/* else if (absDx > absDy && absDx > 50f) {
                                        swipeConsumed = true
                                        if (swipeTotalDx > 0f && currentIndex > 0) {
                                            onIndexChanged(currentIndex - 1)
                                        } else if (swipeTotalDx < 0f && currentIndex < allImages.size - 1) {
                                            onIndexChanged(currentIndex + 1)
                                        }
                                    }*/
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .then(
                    if (scale > 1f) Modifier.graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ) else Modifier
                )
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Fullscreen",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedElement(
                        rememberSharedContentState(key = imageUri),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
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

@androidx.annotation.OptIn(UnstableApi::class)
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
        onDispose { exoPlayer.release() }
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

fun loadImagesFromMediaStore(context: Context): List<GalleryMediaItem> {
    val images = mutableListOf<GalleryMediaItem>()
    try {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                images.add(GalleryMediaItem(uri.toString(), isVideo = false, dateAdded = cursor.getLong(dateColumn)))
            }
        }
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(ERRORINSERTDATA("GalleryTab", "Fehler bei Laden von Bildern: ${e.message}", Instant.now().toString(), "ERROR"))
        }
    }
    return images
}

fun loadVideosFromMediaStore(context: Context): List<GalleryMediaItem> {
    val videos = mutableListOf<GalleryMediaItem>()
    try {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                videos.add(GalleryMediaItem(uri.toString(), isVideo = true, dateAdded = cursor.getLong(dateColumn)))
            }
        }
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(ERRORINSERTDATA("GalleryTab", "Fehler bei Laden von Videos: ${e.message}", Instant.now().toString(), "ERROR"))
        }
    }
    return videos
}