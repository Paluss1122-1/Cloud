package com.cloud.tabs.audiorecordertab

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.lifecycle.AndroidViewModel
import com.cloud.core.functions.errorInsert
import com.cloud.core.functions.ERRORINSERTDATA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

class AudioRecorderTabViewModel(application: Application) : AndroidViewModel(application) {
    var hasPermission by mutableStateOf(
        ContextCompat.checkSelfPermission(
            getApplication<Application>().applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PermissionChecker.PERMISSION_GRANTED
    )

    var isRecording by mutableStateOf(false)
    var audioFiles by mutableStateOf<List<File>>(emptyList())
    var mediaPlayer by mutableStateOf<MediaPlayer?>(null)
    var selectedFile by mutableStateOf<File?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)
    var showShareDialog by mutableStateOf(false)
    var shareFile by mutableStateOf<File?>(null)
    var shareRange by mutableStateOf(0f..0f)
    var isProcessing by mutableStateOf(false)

    fun refreshFiles() {
        val dir = getApplication<Application>().applicationContext.getExternalFilesDir(null)
        audioFiles = dir?.listFiles()
            ?.filter { it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun updatePos() {
        while (isPlaying) {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    currentPosition = it.currentPosition.toFloat()
                }
            }
            delay(100)
        }
    }

    fun handleButtonClick(scope: CoroutineScope) {
        if (isRecording) {
            stopAudioService(getApplication<Application>().applicationContext)
            isRecording = false
            scope.launch {
                delay(500)
                refreshFiles()
            }
        } else {
            val file = createAudioFile(getApplication<Application>().applicationContext)
            startAudioService(getApplication<Application>().applicationContext, file.absolutePath)
            isRecording = true
        }
    }

    /** Player Selection**/
    fun onPlayPause(file: File) {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        } else {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        this@AudioRecorderTabViewModel.isPlaying = false
                        this@AudioRecorderTabViewModel.currentPosition = 0f
                    }
                }
                duration = mediaPlayer?.duration?.toFloat() ?: 0f
            }
            mediaPlayer?.start()
            isPlaying = true
        }
    }

    fun onStop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentPosition = 0f
    }

    fun onSelect(file: File) {
        onStop()
        selectedFile = file
    }

    fun onClose() {
        onStop()
        selectedFile = null
    }

    fun onSeek(pos: Float) {
        mediaPlayer?.seekTo(pos.toInt())
        currentPosition = pos
    }

    fun onShare(file: File) {
        shareFile = file
        shareRange = 0f..duration
        showShareDialog = true
    }

    fun onShareDirect(file: File) {
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.prepare()
        val dur = mp.duration.toFloat()
        mp.release()

        shareFile = file
        shareRange = 0f..dur
        showShareDialog = true
    }

    /** Share Dialog*/
    fun onFinalShare(range: ClosedFloatingPointRange<Float>, scope: CoroutineScope) {
        isProcessing = true
        shareAudioToWhatsApp(
            context = getApplication<Application>().applicationContext,
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
                    errorInsert(
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

    fun onDismiss() {
        if (!isProcessing) showShareDialog = false
    }

    /** Core */
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
        } catch (_: Exception) {
        }
    }

    fun stopAudioService(context: Context) {
        try {
            context.stopService(Intent(context, AudioForegroundService::class.java))
        } catch (_: Exception) {
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
            } catch (_: Exception) {
                val chooser = Intent.createChooser(shareIntent.apply { setPackage(null) }, "Teilen")
                context.startActivity(chooser)
                onComplete()
            }
        } catch (e: Exception) {
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
            throw e
        } finally {
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
        }
    }
}

