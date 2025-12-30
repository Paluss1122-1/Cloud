package com.example.cloud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.cloud.service.ChatService
import com.example.cloud.service.QuietHoursNotificationService
import com.example.cloud.service.WhatsAppNotificationListener

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, QuietHoursNotificationService::class.java)
        context.startForegroundService(serviceIntent)

        val serviceIntent1 = Intent(context, WhatsAppNotificationListener::class.java)
        context.startForegroundService(serviceIntent1)

        val serviceIntent2 = Intent(context, ChatService::class.java)
        context.startForegroundService(serviceIntent2)
    }
}