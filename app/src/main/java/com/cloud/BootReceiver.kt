package com.cloud

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.cloud.service.QuietHoursNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.time.Clock

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val EXPECTED_ACTION = "android.intent.action.BOOT_COMPLETED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (EXPECTED_ACTION != intent.action) {
            return
        }
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
            context.startForegroundService(serviceIntent)
        }

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
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    startAt,
                    startPending
                )
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            "Boot Receiver", "Fehler bei alarmanager: canScheduleExactAlarms",
                            Clock.System.now().toString(), "ERROR"
                        )
                    )
                }
            }

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
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, stopPending)
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "Boot Receiver",
                        "Failed to schedule alarms for QuietHoursNotificationService: ${e.message}",
                        Clock.System.now().toString(),
                        "ERROR"
                    )
                )
            }
        }
    }
}
