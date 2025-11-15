package com.example.cloud.whatsapptab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.cloud.service.WhatsAppNotificationListener

@Composable
fun WhatsAppTabScreen() {
    val context = LocalContext.current
    var messages by remember { mutableStateOf(WhatsAppNotificationListener.getMessages()) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                messages = WhatsAppNotificationListener.getMessages()
            }
        }
        val filter = IntentFilter("WHATSAPP_MESSAGE_RECEIVED")
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    WhatsAppTabContent(messages)
}