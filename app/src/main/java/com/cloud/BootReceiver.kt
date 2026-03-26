package com.cloud

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.cloud.service.QuietHoursNotificationService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
         val serviceIntent = Intent(context, QuietHoursNotificationService::class.java)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Foreground-Service nach Boot nicht erlaubt, Fallback auf startService()", e)
            try {
                context.startService(serviceIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Service-Start nach Boot fehlgeschlagen", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService nach Boot fehlgeschlagen", e)
            try {
                context.startService(serviceIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Service-Start nach Boot fehlgeschlagen", e2)
            }
        }
    }

    private companion object {
        private const val TAG = "BootReceiver"
    }
}
