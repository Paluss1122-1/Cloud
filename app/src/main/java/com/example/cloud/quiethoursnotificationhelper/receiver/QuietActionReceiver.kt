package com.example.cloud.quiethoursnotificationhelper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

class QuietActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("quick_settings_prefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "SET_START" -> {
                prefs.edit(commit = true) { putString("saved_number_start", "21") }
            }

            "SET_END" -> {
                prefs.edit(commit = true) { putString("saved_number", "7") }
            }
        }
    }
}