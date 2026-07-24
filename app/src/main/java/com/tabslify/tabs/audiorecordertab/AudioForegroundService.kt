package com.tabslify.tabs.audiorecordertab

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tabslify.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val PLAYBACK_SAMPLE_RATE = 48000
private const val PLAYBACK_CHANNEL_COUNT = 2
private const val PLAYBACK_BYTES_PER_FRAME = 4

class AudioForegroundService : Service() {
    private var recorder: MediaRecorder? = null

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var playbackJob: Job? = null
    @Volatile
    private var capturingPlayback = false
    private var muxerTrackIndex = -1

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra("filePath") ?: return START_NOT_STICKY
        val isMediaRecorderMode = intent.getBooleanExtra("isMediaRecorderMode", false)
        try {
            createNotificationChannel()
            if (isMediaRecorderMode) {
                val resultCode = intent.getIntExtra("resultCode", 0)
                val data = intent.getParcelableExtra("data", Intent::class.java)
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                startPlaybackCapture(resultCode, data, filePath)
            } else {
                startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                startRecording(filePath)
            }
        } catch (_: Exception) {
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
            try {
                recorder?.release()
            } catch (_: Exception) {
            } finally {
                recorder = null
            }
            throw e
        }
    }

    private fun startPlaybackCapture(resultCode: Int, data: Intent, path: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data)
        if (projection == null) {
            stopSelf()
            return
        }
        mediaProjection = projection

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                capturingPlayback = false
                stopSelf()
            }
        }
        mediaProjectionCallback = callback
        projection.registerCallback(callback, null)

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(PLAYBACK_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val minBufferSize = AudioRecord.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val record = try {
            AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 4)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        } catch (_: SecurityException) {
            stopSelf()
            return
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val encoderFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            PLAYBACK_SAMPLE_RATE,
            PLAYBACK_CHANNEL_COUNT
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize)
        }
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            record.startRecording()
        } catch (_: SecurityException) {
            record.release()
            encoder.release()
            muxer.release()
            stopSelf()
            return
        }
        encoder.start()
        capturingPlayback = true

        playbackJob = serviceScope.launch {
            runPlaybackCaptureLoop(record, encoder, muxer, minBufferSize)
        }
    }

    private fun runPlaybackCaptureLoop(
        record: AudioRecord,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        chunkSize: Int
    ) {
        val pcmBuffer = ByteArray(chunkSize)
        var totalFramesRead = 0L

        try {
            while (capturingPlayback) {
                val read = record.read(pcmBuffer, 0, pcmBuffer.size)
                if (read > 0) {
                    val presentationTimeUs = totalFramesRead * 1_000_000L / PLAYBACK_SAMPLE_RATE
                    totalFramesRead += read / PLAYBACK_BYTES_PER_FRAME
                    feedEncoder(encoder, pcmBuffer, read, presentationTimeUs, endOfStream = false)
                    drainEncoder(encoder, muxer)
                }
            }

            val presentationTimeUs = totalFramesRead * 1_000_000L / PLAYBACK_SAMPLE_RATE
            feedEncoder(encoder, ByteArray(0), 0, presentationTimeUs, endOfStream = true)
            drainEncoder(encoder, muxer, untilEndOfStream = true)
        } finally {
            try {
                record.stop()
            } catch (_: Exception) {
            }
            try {
                record.release()
            } catch (_: Exception) {
            }
            try {
                encoder.stop()
            } catch (_: Exception) {
            }
            try {
                encoder.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerTrackIndex >= 0) muxer.stop()
            } catch (_: Exception) {
            }
            try {
                muxer.release()
            } catch (_: Exception) {
            }
            muxerTrackIndex = -1
        }
    }

    private fun feedEncoder(
        encoder: MediaCodec,
        data: ByteArray,
        length: Int,
        presentationTimeUs: Long,
        endOfStream: Boolean
    ) {
        val inputIndex = encoder.dequeueInputBuffer(10_000)
        if (inputIndex < 0) return
        val inputBuffer = encoder.getInputBuffer(inputIndex) ?: return
        inputBuffer.clear()
        if (length > 0) inputBuffer.put(data, 0, length)
        val flags = if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        encoder.queueInputBuffer(inputIndex, 0, length, presentationTimeUs, flags)
    }

    private fun drainEncoder(encoder: MediaCodec, muxer: MediaMuxer, untilEndOfStream: Boolean = false) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, if (untilEndOfStream) 10_000 else 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEndOfStream) return
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                }

                outputIndex >= 0 -> {
                    if (bufferInfo.size > 0 && muxerTrackIndex >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                        }
                    }
                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (isEos) return
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val launchTabslifyIntent =
            packageManager.getLaunchIntentForPackage("com.paluss1122.tabslify")?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val launchTabslifyPendingIntent = PendingIntent.getActivity(
            this,
            1002,
            launchTabslifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "audio_channel")
            .setContentTitle(getString(R.string.aufnahme_lauft))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(launchTabslifyPendingIntent)
            .build()

        return builder
    }

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                "audio_channel",
                getString(R.string.audio_aufnahme),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        } catch (e: Exception) {
            throw e
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        capturingPlayback = false
        try {
            mediaProjectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        } catch (_: Exception) {
        }
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        } finally {
            mediaProjection = null
            mediaProjectionCallback = null
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
