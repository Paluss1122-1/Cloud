package com.example.cloud.audiorecorder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AudioRecorderContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    // Player States
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var audioFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    // Teilen Dialog States
    var showShareDialog by remember { mutableStateOf(false) }
    var shareFile by remember { mutableStateOf<File?>(null) }
    var shareRange by remember { mutableStateOf(0f..0f) }
    var shareDuration by remember { mutableFloatStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }

    // Lade alle Audiodateien
    LaunchedEffect(Unit) {
        val dir = context.getExternalFilesDir(null)
        audioFiles = dir?.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    // Update Position während Wiedergabe
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
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                release()
            }
            mediaPlayer?.release()
        }
    }

    // Share Dialog
    if (showShareDialog && shareFile != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showShareDialog = false
                }
            },
            title = { Text("Audio-Ausschnitt wählen") },
            text = {
                Column {
                    Text(
                        text = "Wähle den Bereich, den du teilen möchtest:",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    RangeSlider(
                        value = shareRange,
                        onValueChange = { newRange ->
                            shareRange = newRange
                        },
                        valueRange = 0f..shareDuration,
                        enabled = !isProcessing,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4CAF50),
                            activeTrackColor = Color(0xFF4CAF50),
                            inactiveTrackColor = Color.Gray
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Start: ${formatTime(shareRange.start.toInt())}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Ende: ${formatTime(shareRange.endInclusive.toInt())}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Text(
                        text = "Dauer: ${formatTime((shareRange.endInclusive - shareRange.start).toInt())}",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (isProcessing) {
                        Text(
                            text = "⏳ Verarbeite Audio...",
                            fontSize = 14.sp,
                            color = Color(0xFFFFA500),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessing = true
                        shareAudioToWhatsApp(
                            context = context,
                            sourceFile = shareFile!!,
                            startMs = shareRange.start.toLong(),
                            endMs = shareRange.endInclusive.toLong(),
                            onComplete = {
                                isProcessing = false
                                showShareDialog = false
                            },
                            onError = { error ->
                                isProcessing = false
                                println("Fehler beim Teilen: $error")
                            }
                        )
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366)
                    )
                ) {
                    Text("📤 Auf WhatsApp teilen")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showShareDialog = false },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if (isRecording) "🎙️ Aufnahme läuft..." else "🎤 Audio Recorder",
            fontSize = 24.sp,
            color = if (isRecording) Color.Red else Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasPermission) {
            Text(
                text = "⚠️ Mikrofon-Berechtigung erforderlich",
                fontSize = 16.sp,
                color = Color.Yellow,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Aufnahme Button
        Button(
            onClick = {
                if (isRecording) {
                    // Aufnahme beenden
                    mediaRecorder?.apply {
                        try {
                            stop()
                            release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    mediaRecorder = null
                    isRecording = false

                    // Liste aktualisieren
                    val dir = context.getExternalFilesDir(null)
                    audioFiles = dir?.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
                } else {
                    // Aufnahme starten
                    try {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val audioFile = File(context.getExternalFilesDir(null), "audio_$timestamp.m4a")
                        currentFilePath = audioFile.absolutePath
                        startForegroundAudioService(context, currentFilePath!!)
                        isRecording = true

                        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder(context)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaRecorder()
                        }.apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(audioFile.absolutePath)
                            prepare()
                            start()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else Color(0xFF4CAF50),
                contentColor = Color.White
            ),
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

        // Player Sektion
        if (selectedFile != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF333333))
                    .padding(16.dp)
            ) {
                Text(
                    text = "▶️ Player: ${selectedFile?.name}",
                    fontSize = 14.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Timeline Slider
                Slider(
                    value = currentPosition,
                    onValueChange = { newPosition ->
                        currentPosition = newPosition
                        mediaPlayer?.seekTo(newPosition.toInt())
                    },
                    valueRange = 0f..duration,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4CAF50),
                        activeTrackColor = Color(0xFF4CAF50),
                        inactiveTrackColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition.toInt()),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = formatTime(duration.toInt()),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer?.pause()
                                isPlaying = false
                            } else {
                                if (mediaPlayer == null) {
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(selectedFile?.absolutePath)
                                        prepare()
                                        duration = this.duration.toFloat()
                                        setOnCompletionListener {
                                            isPlaying = false
                                            currentPosition = 0f
                                        }
                                    }
                                }
                                mediaPlayer?.start()
                                isPlaying = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text(if (isPlaying) "⏸️ Pause" else "▶️ Play", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                            currentPosition = 0f
                            duration = 0f
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Text("⏹️ Stop", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // Teilen-Dialog öffnen
                            shareFile = selectedFile
                            shareDuration = duration
                            shareRange = 0f..duration
                            showShareDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366)
                        )
                    ) {
                        Text("📤 Teilen", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            selectedFile = null
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                            currentPosition = 0f
                            duration = 0f
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray
                        )
                    ) {
                        Text("✖️", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Liste der Aufnahmen
        Text(
            text = "📁 Aufnahmen (${audioFiles.size})",
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(audioFiles) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selectedFile == file) Color(0xFF444444) else Color(0xFF333333))
                        .clickable {
                            // Player zurücksetzen und neue Datei laden
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                            currentPosition = 0f
                            duration = 0f
                            selectedFile = file
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                .format(Date(file.lastModified())),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Row {
                        Text(
                            text = "📤",
                            fontSize = 20.sp,
                            color = Color(0xFF25D366),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable {
                                    // Direkt teilen ohne Auswahl
                                    val mp = MediaPlayer()
                                    mp.setDataSource(file.absolutePath)
                                    mp.prepare()
                                    val fileDuration = mp.duration.toFloat()
                                    mp.release()

                                    shareFile = file
                                    shareDuration = fileDuration
                                    shareRange = 0f..fileDuration
                                    showShareDialog = true
                                }
                        )
                        Text(
                            text = "▶️",
                            fontSize = 20.sp,
                            color = if (selectedFile == file) Color(0xFF4CAF50) else Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

fun formatTime(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
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
        // Erstelle temporäre Datei mit .opus Extension (WhatsApp-kompatibel)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputFile = File(context.cacheDir, "voice_message_$timestamp.opus")

        // Trimme die Audio-Datei
        trimAudioFile(sourceFile, outputFile, startMs, endMs)

        // Teile via Intent
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            outputFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/ogg"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(shareIntent)
            onComplete()
        } catch (e: Exception) {
            // Fallback: Alle Apps anzeigen
            val chooserIntent = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/ogg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Audio teilen"
            )
            context.startActivity(chooserIntent)
            onComplete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onError(e.message ?: "Unbekannter Fehler")
    }
}

fun trimAudioFile(
    sourceFile: File,
    outputFile: File,
    startMs: Long,
    endMs: Long
) {
    // Für WhatsApp-Kompatibilität: verwende M4A statt OPUS
    // WhatsApp akzeptiert auch AAC in M4A Container als Sprachnachricht
    val tempOutputFile = File(outputFile.parent, outputFile.nameWithoutExtension + ".m4a")

    val extractor = MediaExtractor()
    extractor.setDataSource(sourceFile.absolutePath)

    var audioTrack = -1
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
            audioTrack = i
            break
        }
    }

    if (audioTrack == -1) {
        throw IllegalArgumentException("Keine Audio-Spur gefunden")
    }

    extractor.selectTrack(audioTrack)
    val format = extractor.getTrackFormat(audioTrack)

    // Verwende MPEG_4 Container (M4A) für AAC Audio
    val muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    // Füge Audio-Track hinzu
    val muxerTrack = muxer.addTrack(format)
    muxer.start()

    // Springe zum Startpunkt
    extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

    val buffer = ByteBuffer.allocate(1024 * 1024)
    val bufferInfo = MediaCodec.BufferInfo()

    while (true) {
        val sampleSize = extractor.readSampleData(buffer, 0)
        if (sampleSize < 0) break

        val presentationTimeUs = extractor.sampleTime

        // Stoppe bei Endzeit
        if (presentationTimeUs > endMs * 1000) break

        // Schreibe nur Samples ab Startzeit
        if (presentationTimeUs >= startMs * 1000) {
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = presentationTimeUs - (startMs * 1000)

            // Konvertiere MediaExtractor flags zu MediaCodec flags
            val sampleFlags = extractor.sampleFlags
            bufferInfo.flags = when {
                (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0 -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                else -> 0
            }

            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
        }

        extractor.advance()
        buffer.clear()
    }

    muxer.stop()
    muxer.release()
    extractor.release()

    // Benenne in finale Datei um
    if (tempOutputFile.exists()) {
        tempOutputFile.copyTo(outputFile, overwrite = true)
        tempOutputFile.delete()
    }
}

fun startForegroundAudioService(context: Context, filePath: String) {
    val intent = Intent(context, AudioForegroundService::class.java).apply {
        putExtra("filePath", filePath)
    }
    ContextCompat.startForegroundService(context, intent)
}

class AudioForegroundService : Service() {
    private var recorder: MediaRecorder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra("filePath") ?: return START_NOT_STICKY

        val channel = NotificationChannel(
            "audio_channel",
            "Audio Aufnahme",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground-Service für Audioaufnahme"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        startForeground(
            1,
            NotificationCompat.Builder(this, "audio_channel")
                .setContentTitle("🎙️ Aufnahme läuft")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
        )

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(filePath)
            prepare()
            start()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            release()
        }
    }

    override fun onBind(intent: Intent?) = null
}