package com.cloud.audiorecorder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

private fun reportAudioRecorderError(
    serviceName: String,
    message: String,
    throwable: Throwable,
    severity: String = "ERROR",
    scope: CoroutineScope? = null
) {
    val targetScope = scope ?: CoroutineScope(Dispatchers.IO)
    targetScope.launch {
        ERRORINSERT(
            ERRORINSERTDATA(
                service_name = serviceName,
                error_message = "$message: ${throwable::class.simpleName} - ${throwable.message ?: "ohne Nachricht"}",
                created_at = Instant.now().toString(),
                severity = severity
            )
        )
    }
}

@Composable
fun AudioRecorderContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    if (!hasPermission) {
        Toast.makeText(context, "Keine Mikrofon Berechtigung", Toast.LENGTH_SHORT).show()
        return
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            scope.launch {
                ERRORINSERT(
                    data = ERRORINSERTDATA(
                        "AudioRecorderContent.permissionCheck",
                        "Keine Mikrofon Berechtigung",
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
        }
    }

    var isRecording by remember { mutableStateOf(false) }
    var audioFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }

    var showShareDialog by remember { mutableStateOf(false) }
    var shareFile by remember { mutableStateOf<File?>(null) }
    var shareRange by remember { mutableStateOf(0f..0f) }
    var isProcessing by remember { mutableStateOf(false) }

    fun refreshFiles() {
        val dir = context.getExternalFilesDir(null)
        audioFiles = dir?.listFiles()
            ?.filter { it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshFiles()
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    currentPosition = it.currentPosition.toFloat()
                }
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRecording) "🎙️ Aufnahme läuft..." else "🎤 Audio Recorder",
            fontSize = 24.sp,
            color = if (isRecording) Color.Red else Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isRecording) {
                    stopAudioService(context)
                    isRecording = false
                    scope.launch {
                        delay(500)
                        refreshFiles()
                    }
                } else {
                    val file = createAudioFile(context)
                    startAudioService(context, file.absolutePath)
                    isRecording = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(
                text = if (isRecording) "⏹️ Aufnahme Beenden" else "⏺️ Aufnahme Starten",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        selectedFile?.let { file ->
            PlayerSection(
                file = file,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onPlayPause = {
                    try {
                        if (isPlaying) {
                            mediaPlayer?.pause()
                            isPlaying = false
                        } else {
                            if (mediaPlayer == null) {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(file.absolutePath)
                                    prepare()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        currentPosition = 0f
                                    }
                                }
                                duration = mediaPlayer?.duration?.toFloat() ?: 0f
                            }
                            mediaPlayer?.start()
                            isPlaying = true
                        }
                    } catch (e: Exception) {
                        reportAudioRecorderError(
                            "AudioRecorderContent.onPlayPause",
                            "Fehler bei Player Play/Pause",
                            e,
                            scope = scope
                        )
                    }
                },
                onStop = {
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        isPlaying = false
                        currentPosition = 0f
                    } catch (e: Exception) {
                        reportAudioRecorderError(
                            "AudioRecorderContent.onStop",
                            "Fehler bei Player Stop",
                            e,
                            scope = scope
                        )
                    }
                },
                onSeek = { pos ->
                    try {
                        mediaPlayer?.seekTo(pos.toInt())
                        currentPosition = pos
                    } catch (e: Exception) {
                        reportAudioRecorderError(
                            "AudioRecorderContent.onSeek",
                            "Fehler bei Player Seek",
                            e,
                            scope = scope
                        )
                    }
                },
                onClose = {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        isPlaying = false
                        selectedFile = null
                    } catch (e: Exception) {
                        reportAudioRecorderError(
                            "AudioRecorderContent.onClose",
                            "Fehler bei Player Close",
                            e,
                            scope = scope
                        )
                    }
                },
                onShare = {
                    shareFile = file
                    shareRange = 0f..duration
                    showShareDialog = true
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "📁 Aufnahmen (${audioFiles.size})",
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .align(Alignment.Start)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(audioFiles) { file ->
                FileItem(
                    file = file,
                    isSelected = selectedFile == file,
                    onClick = {
                        try {
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                            currentPosition = 0f
                            selectedFile = file
                        } catch (e: Exception) {
                            reportAudioRecorderError(
                                "AudioRecorderContent.fileItemOnClick",
                                "Fehler beim Dateiwechsel",
                                e,
                                scope = scope
                            )
                        }
                    },
                    onShareDirect = {
                        try {
                            val mp = MediaPlayer()
                            mp.setDataSource(file.absolutePath)
                            mp.prepare()
                            val dur = mp.duration.toFloat()
                            mp.release()

                            shareFile = file
                            shareRange = 0f..dur
                            showShareDialog = true
                        } catch (e: Exception) {
                            reportAudioRecorderError(
                                "AudioRecorderContent.onShareDirect",
                                "Fehler bei Share-Vorbereitung",
                                e,
                                scope = scope
                            )
                        }
                    }
                )
            }
        }
    }

    if (showShareDialog && shareFile != null) {
        ShareDialog(
            initialRange = shareRange,
            maxDuration = if (shareRange.endInclusive > 0) shareRange.endInclusive else 1f,
            isProcessing = isProcessing,
            onDismiss = { if (!isProcessing) showShareDialog = false },
            onShare = { range ->
                isProcessing = true
                shareAudioToWhatsApp(
                    context = context,
                    sourceFile = shareFile!!,
                    startMs = range.start.toLong(),
                    endMs = range.endInclusive.toLong(),
                    onComplete = {
                        isProcessing = false
                        showShareDialog = false
                    },
                    onError = {
                        isProcessing = false
                        scope.launch {
                            ERRORINSERT(
                                data = ERRORINSERTDATA(
                                    "AudioRecorderContent.shareDialog.onError",
                                    it,
                                    Instant.now().toString(),
                                    "ERROR"
                                )
                            )
                        }
                    }
                )
            }
        )
    }
}


@Composable
fun PlayerSection(
    file: File,
    isPlaying: Boolean,
    currentPosition: Float,
    duration: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF333333))
            .padding(16.dp)
    ) {
        Text("▶️ Player: ${file.name}", color = Color.White)

        Slider(
            value = currentPosition,
            onValueChange = onSeek,
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4CAF50),
                activeTrackColor = Color(0xFF4CAF50)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentPosition.toInt()), color = Color.Gray, fontSize = 12.sp)
            Text(formatTime(duration.toInt()), color = Color.Gray, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = onPlayPause, colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) {
                Text(if (isPlaying) "⏸️" else "▶️")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(Color.Red)) {
                Text("⏹️")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onShare, colors = ButtonDefaults.buttonColors(Color(0xFF25D366))) {
                Text("📤")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(Color.Gray)) {
                Text("✖️")
            }
        }
    }
}

@Composable
fun FileItem(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onShareDirect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (isSelected) Color(0xFF444444) else Color(0xFF333333))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = Color.White)
            Text(
                SimpleDateFormat(
                    "dd.MM.yyyy HH:mm",
                    Locale.getDefault()
                ).format(Date(file.lastModified())),
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        Row {
            Text(
                "📤", fontSize = 20.sp, modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable { onShareDirect() })
        }
    }
}

@Composable
fun ShareDialog(
    initialRange: ClosedFloatingPointRange<Float>,
    maxDuration: Float,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onShare: (ClosedFloatingPointRange<Float>) -> Unit
) {
    var currentRange by remember { mutableStateOf(initialRange) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio teilen") },
        text = {
            Column {
                Text("Bereich auswählen:", modifier = Modifier.padding(bottom = 16.dp))
                RangeSlider(
                    value = currentRange,
                    onValueChange = { currentRange = it },
                    valueRange = 0f..maxDuration,
                    enabled = !isProcessing,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4CAF50),
                        activeTrackColor = Color(0xFF4CAF50)
                    )
                )
                Text(
                    "Dauer: ${formatTime((currentRange.endInclusive - currentRange.start).toInt())}",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (isProcessing) Text(
                    "⏳ Verarbeite...",
                    color = Color(0xFFFFA500),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onShare(currentRange) },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(Color(0xFF25D366))
            ) { Text("WhatsApp") }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(Color.Gray)
            ) {
                Text("Abbrechen")
            }
        }
    )
}


fun createAudioFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(context.getExternalFilesDir(null), "audio_$timestamp.m4a")
}

fun startAudioService(context: Context, path: String) {
    try {
        val intent = Intent(context, AudioForegroundService::class.java).apply {
            putExtra("filePath", path)
        }
        ContextCompat.startForegroundService(context, intent)
    } catch (e: Exception) {
        reportAudioRecorderError(
            "AudioRecorderContent.startAudioService",
            "Fehler beim Starten des Audio Services",
            e
        )
    }
}

fun stopAudioService(context: Context) {
    try {
        context.stopService(Intent(context, AudioForegroundService::class.java))
    } catch (e: Exception) {
        reportAudioRecorderError(
            "AudioRecorderContent.stopAudioService",
            "Fehler beim Stoppen des Audio Services",
            e
        )
    }
}

fun formatTime(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

fun shareAudioToWhatsApp(
    context: Context,
    sourceFile: File,
    startMs: Long,
    endMs: Long,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val outputFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.m4a")
        trimAudioFile(sourceFile, outputFile, startMs, endMs)

        android.util.Log.d("AudioShare", "Output file: ${outputFile.absolutePath}")
        android.util.Log.d("AudioShare", "File exists: ${outputFile.exists()}")
        android.util.Log.d("AudioShare", "Authority: ${context.packageName}.provider")

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            outputFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(shareIntent)
            onComplete()
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioRecorderContent.shareAudioToWhatsApp.startActivity",
                "WhatsApp Share fehlgeschlagen, versuche Fallback",
                e
            )
            android.util.Log.e("AudioShare", "WhatsApp not found", e)
            val chooser = Intent.createChooser(shareIntent.apply { setPackage(null) }, "Teilen")
            context.startActivity(chooser)
            onComplete()
        }
    } catch (e: Exception) {
        reportAudioRecorderError(
            "AudioRecorderContent.shareAudioToWhatsApp",
            "Share fehlgeschlagen",
            e
        )
        android.util.Log.e("AudioShare", "Share failed", e)
        onError(e.message ?: "Unbekannter Fehler")
    }
}

fun trimAudioFile(sourceFile: File, outputFile: File, startMs: Long, endMs: Long) {
    var extractor: MediaExtractor? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false

    try {
        extractor = MediaExtractor()
        extractor.setDataSource(sourceFile.absolutePath)

        val trackIndex = (0 until extractor.trackCount).find {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("Kein Audio-Track")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerTrackIndex = muxer.addTrack(format)
        muxer.start()
        muxerStarted = true

        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val timeUs = extractor.sampleTime
            if (timeUs > endMs * 1000) break

            if (timeUs >= startMs * 1000) {
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = timeUs - (startMs * 1000)
                bufferInfo.flags =
                    if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else 0
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            }

            extractor.advance()
        }
    } catch (e: Exception) {
        reportAudioRecorderError(
            "AudioRecorderContent.trimAudioFile",
            "Fehler beim Trimmen",
            e
        )
        throw e
    } finally {
        try {
            if (muxerStarted) {
                muxer?.stop()
            }
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioRecorderContent.trimAudioFile.stopMuxer",
                "Fehler beim Stoppen des Muxers",
                e
            )
        }
        try {
            muxer?.release()
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioRecorderContent.trimAudioFile.releaseMuxer",
                "Fehler beim Freigeben des Muxers",
                e
            )
        }
        try {
            extractor?.release()
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioRecorderContent.trimAudioFile.releaseExtractor",
                "Fehler beim Freigeben des Extractors",
                e
            )
        }
    }
}

class AudioForegroundService : Service() {
    private var recorder: MediaRecorder? = null
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra("filePath") ?: return START_NOT_STICKY
        try {
            createNotificationChannel()
            startForeground(1, createNotification())
            startRecording(filePath)
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioForegroundService.onStartCommand",
                "Fehler beim Start des Foreground Services",
                e,
                scope = serviceScope
            )
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startRecording(path: String) {
        try {
            recorder = (MediaRecorder(this)).apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(48000)
                setAudioChannels(2)

                setOutputFile(path)
                prepare()
                start()
            }
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioForegroundService.startRecording",
                "Fehler beim Starten der Aufnahme",
                e,
                scope = serviceScope
            )
            try {
                recorder?.release()
            } catch (releaseException: Exception) {
                reportAudioRecorderError(
                    "AudioForegroundService.startRecording.release",
                    "Fehler beim Freigeben nach Startfehler",
                    releaseException,
                    scope = serviceScope
                )
            } finally {
                recorder = null
            }
            throw e
        }
    }

    private fun createNotification(): Notification {
        val launchCloudIntent =
            packageManager.getLaunchIntentForPackage("com.cloud")?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val launchCloudPendingIntent = PendingIntent.getActivity(
            this,
            1002,
            launchCloudIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "audio_channel")
            .setContentTitle("🎙️ Aufnahme läuft")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(launchCloudPendingIntent)
            .build()

        return builder
    }

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                "audio_channel",
                "Audio Aufnahme",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioForegroundService.createNotificationChannel",
                "Fehler beim Erstellen vom Notification Channel",
                e,
                scope = serviceScope
            )
            throw e
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            recorder?.stop()
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioForegroundService.onDestroy.stop",
                "Fehler bei Service beenden",
                e,
                "Info",
                scope = serviceScope
            )
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            reportAudioRecorderError(
                "AudioForegroundService.onDestroy.release",
                "Fehler beim Freigeben vom Recorder",
                e,
                scope = serviceScope
            )
        } finally {
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
