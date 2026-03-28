package com.cloud.errorreports

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.cloud.Config.cms
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.cloud.SupabaseConfigALT
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class ErrorReport(
    val service_name: String,
    val error_message: String,
    val created_at: String,
    val severity: String = "ERROR"
)

class ErrorNotificationManager(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "error_reports_channel"
        private const val CHANNEL_NAME = "Error Reports"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            importance
        ).apply {
            description = "Benachrichtigungen für neue Fehlerberichte"
            enableVibration(true)
            enableLights(true)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showErrorNotification(errorReport: ErrorReport) {
        try {
             val severityEmoji = if (errorReport.severity == "ERROR") {
                "🔴"
            } else {
                "ℹ️"
            }

            val uri = if (errorReport.service_name == "SB -> Cloud") "https://dashboard.gitguardian.com" else "https://supabase.com/dashboard/project/oulgglfvobyjmfongnil/editor/94945?schema=public"

            val intent = Intent(
                Intent.ACTION_VIEW,
                uri.toUri()
            )

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("$severityEmoji Neuer Fehler: ${errorReport.service_name}")
                .setContentText(errorReport.error_message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setColorized(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify(cms(), notification)
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "NotificationManager",
                        "❌ Fehler bei Anzeigen von Error Notification: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
        }
    }
}

class ErrorMonitorService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: ErrorNotificationManager
    private var isListenerActive = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "error_monitor_service"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            notificationManager = ErrorNotificationManager(this)
            createServiceNotificationChannel()
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "ErrorMonitorService",
                        "Fehler in onCreate(): ${e.message}",
                        Instant.now().toString(),
                        "Error"
                    )
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createServiceNotification())
        try {
            startRealtimeListener()
            return START_STICKY
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "ErrorMonitorService",
                        "Fehler in onStartCommand(): ${e.message}",
                        Instant.now().toString(),
                        "Error"
                    )
                )
            }
            return START_NOT_STICKY
        }
    }

    private fun createServiceNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Error Monitor Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createServiceNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Error Monitor aktiv")
            .setContentText("Überwacht Fehlerberichte im Hintergrund")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun startRealtimeListener() {
        if (isListenerActive) {
            return // ✅ Verhindere doppeltes Starten
        }
        isListenerActive = true
        serviceScope.launch {
            try {
                val tableName = "error_reports"

                val channel = SupabaseConfigALT.client.channel("realtime:$tableName")

                val changeFlow = channel
                    .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = tableName
                    }

                changeFlow
                    .onEach { change ->
                        handleNewError(change.record)
                        Log.d("ErrorMonitor", "${change.record}")
                    }
                    .launchIn(this)

                channel.subscribe()
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.IO).launch {
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            "ErrorMonitorService",
                            "❌ Realtime Fehler: ${e.message}",
                            Instant.now().toString(),
                            "Error"
                        )
                    )
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun handleNewError(errorData: Map<String, Any>) {
        try {
            val errorReport = ErrorReport(
                service_name = errorData["service_name"]?.toString()
                    ?: "Unknown",  // ✅ toString() statt as String
                error_message = errorData["error_message"]?.toString()
                    ?: "Unknown",  // ✅ toString() statt as String
                created_at = errorData["created_at"]?.toString()
                    ?: "Unknown",  // ✅ toString() statt as String
                severity = errorData["severity"]?.toString()?.removeSurrounding("\"") ?: "Unknown"
            )

            withContext(Dispatchers.Main) {
                notificationManager.showErrorNotification(errorReport)
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "ErrorMonitorService",
                        "Fehler beim Verarbeiten des Errors: ${e.message}",
                        Instant.now().toString(),
                        "Error"
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        isListenerActive = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}