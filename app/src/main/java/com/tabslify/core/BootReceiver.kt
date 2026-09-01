package com.tabslify.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.tabs.exploretab.ExploreLocationTracker
import com.tabslify.tabs.focusguard.FocusGuardService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("services_master", true)) {
            return
        }

        if (prefs.getBoolean("service_qhns", false)) {
            val serviceIntent = Intent(context, QuietHoursNotificationService::class.java)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (_: Exception) {
                try {
                    context.startService(serviceIntent)
                } catch (_: Exception) {
                }
            }
        }

        if (prefs.getBoolean("service_focusguard", false)) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FocusGuardService::class.java)
                )
            } catch (_: Exception) {
                try {
                    context.startService(Intent(context, FocusGuardService::class.java))
                } catch (_: Exception) {
                }
            }
            try {
                com.tabslify.tabs.focusguard.monitoring.scheduleFocusGuardWorkers(context)
                com.tabslify.tabs.focusguard.monitoring.scheduleFocusGuardDailySummary(context)
            } catch (_: Exception) {
            }
        }

        try {
            ExploreLocationTracker.start(context)
        } catch (_: Exception) {
        }
    }
}