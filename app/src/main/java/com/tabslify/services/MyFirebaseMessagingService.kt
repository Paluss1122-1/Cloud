package com.tabslify.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tabslify.core.activities.Tabslify
import com.tabslify.core.activities.fetchAndRun
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.toast
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseMessaging.getInstance()
            .subscribeToTopic("all_users")
        if (prvt()) {
            FirebaseMessaging.getInstance()
                .subscribeToTopic("emails")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        println("Message received: $remoteMessage")
        if (remoteMessage.from?.endsWith("emails") == true && prvt()) {
            println("Email notification received")
            val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: return
            val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
            println("Title: $title, Body: $body")
            showSimpleNotificationExtern(
                title = title,
                text = body,
                context = applicationContext,
                silent = false
            )
            return
        }
        val scriptName = remoteMessage.data["script_name"] ?: return
        Tabslify.serviceScope.launch {
            try {
                fetchAndRun(scriptName, applicationContext)
            } catch (_: Exception) {
            }
        }
    }
}
