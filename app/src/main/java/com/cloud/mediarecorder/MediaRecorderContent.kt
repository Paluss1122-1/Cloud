package com.cloud.mediarecorder

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.os.Environment
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.cloud.ui.theme.c
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
fun MediaRecorderContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var musicFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var audioRecorder by remember { mutableStateOf<AudioRecorder?>(null) }

    var showTrimDialog by remember { mutableStateOf(false) }
    var trimFile by remember { mutableStateOf<File?>(null) }
    var trimRange by remember { mutableStateOf(0f..0f) }
    var trimDuration by remember { mutableFloatStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }

    var trimMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isTrimPlaying by remember { mutableStateOf(false) }
    var trimCurrentPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        loadMusicFiles { files ->
            musicFiles = files
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    currentPosition = it.currentPosition.toFloat()
                }
            }
            kotlinx.coroutines.delay(100)
        }
    }

    LaunchedEffect(isTrimPlaying) {
        while (isTrimPlaying) {
            trimMediaPlayer?.let {
                if (it.isPlaying) {
                    val pos = it.currentPosition.toFloat()
                    trimCurrentPosition = pos

                    if (pos >= trimRange.endInclusive) {
                        it.pause()
                        it.seekTo(trimRange.start.toInt())
                        isTrimPlaying = false
                        trimCurrentPosition = trimRange.start
                    }
                }
            }
            kotlinx.coroutines.delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            trimMediaPlayer?.release()
        }
    }

    if (showTrimDialog && trimFile != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    trimMediaPlayer?.release()
                    trimMediaPlayer = null
                    isTrimPlaying = false
                    trimCurrentPosition = 0f
                    showTrimDialog = false
                }
            },
            title = { Text("Musik zuschneiden") },
            text = {
                Column {
                    Text(
                        text = "Wähle den Bereich, den du speichern möchtest:",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    RangeSlider(
                        value = trimRange,
                        onValueChange = { newRange ->
                            trimRange = newRange
                            if (isTrimPlaying) {
                                trimMediaPlayer?.seekTo(newRange.start.toInt())
                                trimCurrentPosition = newRange.start
                            }
                        },
                        valueRange = 0f..trimDuration,
                        enabled = !isProcessing,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1DB954),
                            activeTrackColor = Color(0xFF1DB954),
                            inactiveTrackColor = Color.Gray
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Start: ${formatTime(trimRange.start.toInt())}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Ende: ${formatTime(trimRange.endInclusive.toInt())}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Text(
                        text = "Dauer: ${formatTime((trimRange.endInclusive - trimRange.start).toInt())}",
                        fontSize = 12.sp,
                        color = Color(0xFF1DB954),
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                if (isTrimPlaying) {
                                    trimMediaPlayer?.pause()
                                    isTrimPlaying = false
                                } else {
                                    if (trimMediaPlayer == null) {
                                        trimMediaPlayer = MediaPlayer().apply {
                                            setDataSource(trimFile?.absolutePath)
                                            prepare()
                                        }
                                    }
                                    trimMediaPlayer?.seekTo(trimRange.start.toInt())
                                    trimMediaPlayer?.start()
                                    isTrimPlaying = true
                                    trimCurrentPosition = trimRange.start
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954)
                            )
                        ) {
                            Text(
                                if (isTrimPlaying) "⏸️ Pause" else "▶️ Preview",
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                trimMediaPlayer?.pause()
                                trimMediaPlayer?.seekTo(trimRange.start.toInt())
                                isTrimPlaying = false
                                trimCurrentPosition = trimRange.start
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray
                            )
                        ) {
                            Text("⏹️ Stop", fontSize = 14.sp)
                        }
                    }

                    if (isTrimPlaying) {
                        Text(
                            text = "Position: ${formatTime(trimCurrentPosition.toInt())}",
                            fontSize = 12.sp,
                            color = Color(0xFF1DB954),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (isProcessing) {
                        Text(
                            text = "⏳ Konvertiere zu MP3...",
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
                        trimMediaPlayer?.release()
                        trimMediaPlayer = null
                        isTrimPlaying = false

                        isProcessing = true
                        scope.launch(Dispatchers.Default) {
                            try {
                                val outputFile = File(
                                    Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                    ),
                                    "Cloud"
                                ).apply { mkdirs() }

                                val timestamp = SimpleDateFormat(
                                    "yyyyMMdd_HHmmss",
                                    Locale.getDefault()
                                ).format(Date())
                                val mp3File = File(outputFile, "music_$timestamp.mp3")

                                trimAudioToMp3(
                                    trimFile!!,
                                    mp3File,
                                    trimRange.start.toLong(),
                                    trimRange.endInclusive.toLong()
                                )

                                loadMusicFiles { files ->
                                    musicFiles = files
                                }
                                isProcessing = false
                                showTrimDialog = false
                            } catch (e: Exception) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    ERRORINSERT(
                                        ERRORINSERTDATA(
                                            "MediaRecorderContent",
                                            "Fehler bei Konvertieren von m4a zu mp3: ${e.message}",
                                            Instant.now().toString(),
                                            "ERROR"
                                        )
                                    )
                                }
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DB954)
                    )
                ) {
                    Text("💾 Als MP3 speichern")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showTrimDialog = false },
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
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if (isRecording) "🎵 Aufnahme läuft..." else "",
            fontSize = 24.sp,
            color = if (isRecording) Color(0xFF1DB954) else Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasPermission) {
            Text(
                text = "⚠️ Audio-Berechtigung erforderlich",
                fontSize = 16.sp,
                color = Color.Yellow,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            return
        }

        Button(
            onClick = {
                if (isRecording) {
                    audioRecorder?.stopRecording()
                    audioRecorder = null
                    isRecording = false
                    loadMusicFiles { files ->
                        musicFiles = files
                    }
                } else {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val timestamp = SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                Locale.getDefault()
                            ).format(Date())
                            val audioFile = File(
                                Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                                ),
                                "Cloud/recording_$timestamp.wav"
                            ).apply { parentFile?.mkdirs() }

                            isRecording = true
                            audioRecorder = AudioRecorder()
                            audioRecorder?.startRecording(audioFile)
                        } catch (e: Exception) {
                            CoroutineScope(Dispatchers.IO).launch {
                                ERRORINSERT(
                                    ERRORINSERTDATA(
                                        "MediaRecorderContent",
                                        "Fehler bei Starten von Recording: ${e.message}",
                                        Instant.now().toString(),
                                        "ERROR"
                                    )
                                )
                            }
                            isRecording = false
                            audioRecorder = null
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else c(),
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

        if (selectedFile != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(16.dp)
            ) {
                Text(
                    text = "▶️ Player: ${selectedFile?.name}",
                    fontSize = 14.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = currentPosition,
                    onValueChange = { newPosition ->
                        currentPosition = newPosition
                        mediaPlayer?.seekTo(newPosition.toInt())
                    },
                    valueRange = 0f..duration,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1DB954),
                        activeTrackColor = Color(0xFF1DB954),
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
                            containerColor = Color(0xFF1DB954)
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
                            val mp = MediaPlayer()
                            mp.setDataSource(selectedFile?.absolutePath)
                            mp.prepare()
                            val fileDuration = mp.duration.toFloat()
                            mp.release()

                            trimFile = selectedFile
                            trimDuration = fileDuration
                            trimRange = 0f..fileDuration
                            showTrimDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954)
                        )
                    ) {
                        Text("✂️ Schneiden", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "🎵 Aufnahmen (${musicFiles.size})",
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(musicFiles) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selectedFile == file) Color(0xFF282828) else Color(
                                0xFF1E1E1E
                            )
                        )
                        .padding(12.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                    currentPosition = 0f
                                    duration = 0f
                                    selectedFile = file
                                },
                                onLongPress = {
                                    val mp = MediaPlayer()
                                    mp.setDataSource(file.absolutePath)
                                    mp.prepare()
                                    val fileDuration = mp.duration.toFloat()
                                    mp.release()

                                    trimFile = file
                                    trimDuration = fileDuration
                                    trimRange = 0f..fileDuration
                                    showTrimDialog = true
                                }
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
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
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

fun writePcmToWav(
    outputFile: File,
    pcmData: List<ByteArray>,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int
) {
    val totalSize = pcmData.sumOf { it.size }

    outputFile.outputStream().use { fos ->
        fos.write("RIFF".toByteArray())
        fos.write(
            intToLittleEndian(36 + totalSize)
        )
        fos.write("WAVE".toByteArray())

        fos.write("fmt ".toByteArray())
        fos.write(intToLittleEndian(16))
        fos.write(shortToLittleEndian(1)) // PCM
        fos.write(shortToLittleEndian(channels.toShort()))
        fos.write(intToLittleEndian(sampleRate))
        fos.write(intToLittleEndian(sampleRate * channels * bitsPerSample / 8))
        fos.write(shortToLittleEndian((channels * bitsPerSample / 8).toShort()))
        fos.write(shortToLittleEndian(bitsPerSample.toShort()))

        fos.write("data".toByteArray())
        fos.write(intToLittleEndian(totalSize))

        pcmData.forEach { fos.write(it) }
    }
}

fun trimAudioToMp3(
    sourceFile: File,
    outputFile: File,
    startMs: Long,
    endMs: Long
) {
    val tempWavFile = File(outputFile.parent, "temp_${outputFile.nameWithoutExtension}.wav")

    val mediaExtractor = android.media.MediaExtractor()
    mediaExtractor.setDataSource(sourceFile.absolutePath)

    var audioTrackIndex = -1
    for (i in 0 until mediaExtractor.trackCount) {
        val format = mediaExtractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            break
        }
    }

    if (audioTrackIndex == -1) {
        throw IllegalArgumentException("Keine Audio-Spur gefunden")
    }

    mediaExtractor.selectTrack(audioTrackIndex)
    val audioFormat = mediaExtractor.getTrackFormat(audioTrackIndex)

    val muxer = MediaMuxer(tempWavFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val muxerTrack = muxer.addTrack(audioFormat)
    muxer.start()

    mediaExtractor.seekTo(startMs * 1000, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

    val buffer = ByteBuffer.allocate(1024 * 256)
    val bufferInfo = MediaCodec.BufferInfo()

    while (true) {
        val sampleSize = mediaExtractor.readSampleData(buffer, 0)
        if (sampleSize < 0) break

        val presentationTimeUs = mediaExtractor.sampleTime
        if (presentationTimeUs > endMs * 1000) break

        if (presentationTimeUs >= startMs * 1000) {
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = presentationTimeUs - (startMs * 1000)
            bufferInfo.flags =
                if ((mediaExtractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }

            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
        }

        mediaExtractor.advance()
        buffer.clear()
    }

    muxer.stop()
    muxer.release()
    mediaExtractor.release()

    if (tempWavFile.exists()) {
        tempWavFile.copyTo(outputFile, overwrite = true)
        tempWavFile.delete()
    }
}

fun loadMusicFiles(callback: (List<File>) -> Unit) {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val cloudDir = File(downloadDir, "Cloud")

    val files = if (cloudDir.exists()) {
        cloudDir.listFiles()
            ?.filter { it.extension in listOf("wav", "mp3", "m4a", "aac") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    } else {
        emptyList()
    }

    callback(files)
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

fun intToLittleEndian(value: Int): ByteArray {
    return byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )
}

fun shortToLittleEndian(value: Short): ByteArray {
    return byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}