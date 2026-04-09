package com.cloud

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cloud.service.QuietHoursNotificationService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, QuietHoursNotificationService::class.java)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: ForegroundServiceStartNotAllowedException) {
            try {
                context.startService(serviceIntent)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
            try {
                context.startService(serviceIntent)
            } catch (_: Exception) {
            }
        }
    }
}
