package com.cloud.tabs.audiorecordertab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
        } finally {
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}