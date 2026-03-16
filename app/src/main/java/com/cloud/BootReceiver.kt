package com.cloud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloud.service.QuietHoursNotificationService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, QuietHoursNotificationService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
