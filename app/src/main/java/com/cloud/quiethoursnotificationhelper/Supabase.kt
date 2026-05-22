package com.cloud.quiethoursnotificationhelper

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.cloud.core.activities.Cloud.Companion.appScope
import com.cloud.core.functions.canNotify
import com.cloud.core.objects.Config
import com.cloud.core.objects.Config.cms
import com.cloud.core.objects.reportError
import com.cloud.services.QuietHoursNotificationService.Companion.ERROR_REPORTS_CHANNEL_ID
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.time.Instant

data class ErrorReport(
    val serviceName: String,
    val errorMessage: String,
    val createdAt: String,
    val severity: String = "ERROR"
)

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
private fun showErrorNotification(errorReport: ErrorReport, context: Context) {
    try {
        val severityEmoji = if (errorReport.severity == "ERROR") {
            "🔴"
        } else {
            "ℹ️"
        }

        val uri =
            if (errorReport.serviceName == "SB -> Cloud") "https://dashboard.gitguardian.com" else "https://supabase.com/dashboard/project/oulgglfvobyjmfongnil/editor/94945?schema=public"

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

        val notification = NotificationCompat.Builder(context, ERROR_REPORTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$severityEmoji Neuer Fehler: ${errorReport.serviceName}")
            .setContentText(errorReport.errorMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColorized(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(cms(), notification)
    } catch (e: Exception) {
        reportError(
            "NotificationManager",
            "❌ Fehler bei Anzeigen von Error Notification: ${e.message}",
            Instant.now().toString(),
            "ERROR"
        )
    }
}

@SuppressLint("MissingPermission")
fun fetchNewErrors(context: Context) {
    appScope.launch {
        try {
            val prefs = context.getSharedPreferences("error_fetch_prefs", MODE_PRIVATE)
            val lastChecked = prefs.getString("last_error_check", "1970-01-01T00:00:00Z")!!

            val results = Config.client
                .from("error_reports")
                .select {
                    filter { gt("createdAt", lastChecked) }
                    order("createdAt", Order.ASCENDING)
                }
                .decodeList<ErrorReport>()

            if (results.isEmpty()) return@launch

            prefs.edit { putString("last_error_check", Instant.now().toString()) }

            if (canNotify(context)) {
                results.forEach { showErrorNotification(it, context) }
            }
        } catch (e: Exception) {
            reportError("fetchNewErrors", "$e", Instant.now().toString(), "ERROR")
        }
    }
}