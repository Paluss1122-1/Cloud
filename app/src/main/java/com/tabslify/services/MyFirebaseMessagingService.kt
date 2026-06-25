package com.tabslify.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tabslify.core.activities.Tabslify
import com.tabslify.core.activities.fetchAndRun
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseMessaging.getInstance()
            .subscribeToTopic("all_users")
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val scriptName = remoteMessage.data["script_name"] ?: return
        Tabslify.serviceScope.launch {
            try {
                fetchAndRun(scriptName, applicationContext)
            } catch (_: Exception) {}
        }
    }
}
