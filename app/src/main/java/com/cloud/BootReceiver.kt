package com.example.cloud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Calendar
import android.content.SharedPreferences
import android.widget.Toast
import com.example.cloud.service.ChatService
import com.example.cloud.service.QuietHoursNotificationService
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "BootReceiver triggered with intent: ${intent.action}")

        val prefs: SharedPreferences =
            context.getSharedPreferences("quick_settings_prefs", Context.MODE_PRIVATE)
        val quietStart = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 22
        val quietEnd = prefs.getString("saved_number", null)?.toIntOrNull() ?: 7

        fun isQuietNow(): Boolean {
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)

            return if (quietStart <= quietEnd) {
                hour in quietStart until quietEnd
            } else {
                hour !in quietEnd..<quietStart
            }
        }

        if (isQuietNow()) {
            val serviceIntent = Intent(context, QuietHoursNotificationService::class.java)
            Log.d(
                "BootReceiver",
                "Starting QuietHoursNotificationService as it is currently quiet hours."
            )
            context.startForegroundService(serviceIntent)
        }

        // Schedule next start and stop alarms so the QuietHours service only runs during the window
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            fun nextOccurrence(hour: Int): Long {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= Calendar.getInstance().timeInMillis) cal.add(
                    Calendar.DAY_OF_YEAR,
                    1
                )
                return cal.timeInMillis
            }

            val startIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = QuietHoursNotificationService.ACTION_SCHEDULED_START
            }
            val startPending = PendingIntent.getService(
                context,
                1001,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val startAt = nextOccurrence(quietStart)
            Log.d("BootReceiver", "Scheduling QuietHoursNotificationService start at: $startAt")
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAt, startPending)

            val stopIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = QuietHoursNotificationService.ACTION_SCHEDULED_STOP
            }
            val stopPending = PendingIntent.getService(
                context,
                1002,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopAt = nextOccurrence(quietEnd)
            Log.d("BootReceiver", "Scheduling QuietHoursNotificationService stop at: $stopAt")
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, stopPending)
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to schedule alarms for QuietHoursNotificationService", e)
        }
    }
}