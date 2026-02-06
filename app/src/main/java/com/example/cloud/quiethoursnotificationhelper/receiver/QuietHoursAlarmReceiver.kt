package com.example.cloud.quiethoursnotificationhelper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.cloud.service.QuietHoursNotificationService

class QuietHoursAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("QuietHoursAlarmReceiver", "⏰ Alarm triggered!")

        val serviceIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = "com.example.cloud.ACTION_CHECK_QUIET_HOURS"
        }

        context.startForegroundService(serviceIntent)
    }
}