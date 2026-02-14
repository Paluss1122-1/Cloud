package com.example.cloud.quiethoursnotificationhelper

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import androidx.core.content.edit
import com.example.cloud.service.QuietHoursNotificationService
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_CHANGE_END
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_CHANGE_START
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_EXECUTE_COMMAND
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_MARK_PARTS_READ
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_MESSAGE_SENT
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_NOTIFICATION_DISMISSED
import com.example.cloud.service.QuietHoursNotificationService.Companion.EXTRA_MESSAGE_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.EXTRA_SENDER
import com.example.cloud.service.QuietHoursNotificationService.Companion.NOTIFICATION_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.readMessageIds
import com.example.cloud.showSimpleNotificationExtern

class QuietActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)

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

class QuietHoursAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("QuietHoursAlarmReceiver", "⏰ Alarm triggered!")

        val serviceIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = "com.example.cloud.ACTION_CHECK_QUIET_HOURS"
        }

        context.startForegroundService(serviceIntent)
    }
}

val markReadReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_MARK_PARTS_READ) {
            val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
            if (messageId != null && context != null) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, messageId, Toast.LENGTH_LONG).show()
                    Toast.makeText(context, "$readMessageIds", Toast.LENGTH_LONG).show() // 1447, 47514
                    markMessageAsRead(
                        messageId,
                        readMessageIds,
                        context
                    )
                }
            }
        }
    }
}

val messageSentReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_MESSAGE_SENT) {
            val sender = intent.getStringExtra(EXTRA_SENDER) ?: return

            val replyText = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence("key_text_reply")?.toString()

            setResultCode(Activity.RESULT_OK)

            if (replyText != null && context != null) {
                Handler(Looper.getMainLooper()).post {
                    handleMessageSent(sender, replyText, context)
                }
            }
        }
    }
}

val notificationDismissReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
            val notificationId = intent.getIntExtra("notification_id", -1)

            if (notificationId == NOTIFICATION_ID) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val serviceIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
                        action = "ACTION_RESTORE_NOTIFICATION"
                    }
                    context.startForegroundService(serviceIntent)
                }, 100)
            }
        }
    }
}

val timeChangeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val newTime = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence("key_time_input")?.toString()

        if (newTime != null) {
            val timeValue = newTime.toIntOrNull()

            if (timeValue != null && timeValue in 0..23) {
                val prefs = context.getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)

                when (intent.action) {
                    ACTION_CHANGE_START -> {
                        prefs.edit(commit = true) {
                            putString(
                                "saved_number_start",
                                timeValue.toString()
                            )
                        }
                        showSimpleNotificationExtern(
                            "✓ Startzeit geändert",
                            "Neue Startzeit: $timeValue:00 Uhr",
                            context = context
                        )
                    }

                    ACTION_CHANGE_END -> {
                        prefs.edit(commit = true) {
                            putString(
                                "saved_number",
                                timeValue.toString()
                            )
                        }
                        showSimpleNotificationExtern(
                            "✓ Endzeit geändert",
                            "Neue Endzeit: $timeValue:00 Uhr",
                            context = context
                        )
                    }
                }

                setResultCode(Activity.RESULT_OK)
            } else {
                showSimpleNotificationExtern(
                    "❌ Ungültige Zeit",
                    "Bitte eine Zahl zwischen 0 und 23 eingeben",
                    context = context
                )
            }
        }
    }
}

val commandReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_EXECUTE_COMMAND) {
            val commandText = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence("key_command_input")?.toString()

            val serviceIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = "ACTION_RESTORE_NOTIFICATION"
            }
            context.startForegroundService(serviceIntent)

            if (commandText != null) {
                Handler(Looper.getMainLooper()).post {
                    executeCommand(commandText.trim(), context)
                }
            }
        }
    }
}