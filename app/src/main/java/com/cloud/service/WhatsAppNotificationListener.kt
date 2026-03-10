package com.example.cloud.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.cloud.Config.LAPTOP_IPS
import com.example.cloud.Config.NOTIFICATION_PORT
import com.example.cloud.database.WhatsAppMessage
import com.example.cloud.database.WhatsAppMessageRepository
import com.example.cloud.objects.NotificationRepository
import com.example.cloud.quiethoursnotificationhelper.isLaptopConnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private val repository = WhatsAppMessageRepository()
        val replyActions = mutableMapOf<String, ReplyData>()
        val messagesByContact = mutableMapOf<String, MutableList<ChatMessage>>()

        // Unterstützte Apps
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES =
            setOf("org.telegram.messenger", "org.telegram.messenger.web")

        data class ChatMessage(
            val text: String,
            val timestamp: Long = System.currentTimeMillis(),
            val isOwnMessage: Boolean,
            val imageUri: Uri? = null
        )

        data class ReplyData(
            val pendingIntent: android.app.PendingIntent,
            val remoteInput: RemoteInput,
            val originalResultKey: String,
            val timestamp: Long = System.currentTimeMillis()
        )

        fun getMessages(): List<WhatsAppMessage> {
            return repository.getAllFiltered()
        }

        private val forwardScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var lastForwardTime = 0L
        private const val FORWARD_DEBOUNCE_MS = 500L
        private var pendingForwardJob: Job? = null

        fun forwardNotificationsToLaptop(
            notifications: Array<StatusBarNotification>,
            packageManager: PackageManager
        ) {
            if (!isLaptopConnected) return
            pendingForwardJob?.cancel()
            pendingForwardJob = forwardScope.launch {
                val now = System.currentTimeMillis()
                val timeSinceLast = now - lastForwardTime
                if (timeSinceLast < FORWARD_DEBOUNCE_MS) {
                    delay(FORWARD_DEBOUNCE_MS - timeSinceLast)
                }
                lastForwardTime = System.currentTimeMillis()
                val jsonArray = org.json.JSONArray()

                notifications
                    .filter { !it.isOngoing && it.packageName !== "com.example.cloud" && it.packageName != "com.android.systemui" }
                    .sortedByDescending { it.postTime }
                    .forEach { sbn ->
                        val extras = sbn.notification.extras
                        val title = extras.getCharSequence("android.title")?.toString() ?: ""
                        val text = extras.getCharSequence("android.text")?.toString() ?: ""
                        if (title.isBlank() && text.isBlank()) return@forEach

                        val appName = try {
                            packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(
                                    sbn.packageName,
                                    PackageManager.GET_META_DATA
                                )
                            ).toString()
                        } catch (_: Exception) {
                            sbn.packageName
                        }

                        jsonArray.put(org.json.JSONObject().apply {
                            put("app", appName)
                            put("title", title)
                            put("text", text)
                            put("time", sbn.postTime)
                        })
                    }

                forwardScope.launch {
                    for (ip in LAPTOP_IPS) {
                        try {
                            val socket = java.net.Socket()
                            socket.connect(java.net.InetSocketAddress(ip, NOTIFICATION_PORT), 3000)
                            java.io.PrintWriter(socket.getOutputStream(), true).apply {
                                println(jsonArray.toString())
                                flush()
                            }
                            socket.close()
                            Log.d(
                                "NotifForwarder",
                                "✅ ${jsonArray.length()} Notifs gesendet an $ip"
                            )
                            break
                        } catch (e: Exception) {
                            Log.w("NotifForwarder", "❌ $ip: ${e.message}")
                        }
                    }
                }
            }
        }

        private fun isSupportedApp(packageName: String): Boolean {
            return WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(
                packageName
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (sbn.packageName != "com.example.cloud") {
            activeNotifications?.let { forwardNotificationsToLaptop(it, packageManager) }
        }

        if (!isSupportedApp(sbn.packageName)) {
            return
        }

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

        // System-Nachrichten filtern
        if (title.contains("Backup", ignoreCase = true) ||
            title.contains("Du", ignoreCase = true) ||
            title.contains("WhatsApp", ignoreCase = true) ||
            title.contains("Telegram", ignoreCase = true)
        ) {
            return
        }

        // Duplikate vermeiden
        val existingMessages = messagesByContact[title] ?: mutableListOf()
        val lastMessage = existingMessages.lastOrNull()

        if (lastMessage != null &&
            !lastMessage.isOwnMessage &&
            lastMessage.text == text &&
            (System.currentTimeMillis() - lastMessage.timestamp) < 5000
        ) {
            Log.d("MessageListener", "Duplicate message detected from $title, ignoring")
            return
        }

        Toast.makeText(this, title, Toast.LENGTH_SHORT).show()

        // Reply-Action extrahieren
        notification.actions?.forEach { action ->
            action.remoteInputs?.firstOrNull()?.let { systemRemoteInput ->
                val remoteInput = RemoteInput.Builder(systemRemoteInput.resultKey)
                    .setLabel(systemRemoteInput.label)
                    .setChoices(systemRemoteInput.choices)
                    .setAllowFreeFormInput(systemRemoteInput.allowFreeFormInput)
                    .build()

                replyActions[title] = ReplyData(
                    pendingIntent = action.actionIntent,
                    remoteInput = remoteInput,
                    originalResultKey = systemRemoteInput.resultKey,
                    timestamp = System.currentTimeMillis()
                )
                Log.d(
                    "MessageListener",
                    "Saved reply action for $title with originalResultKey: ${systemRemoteInput.resultKey}"
                )
            }
        }

        // Empfangene Nachricht zur Liste hinzufügen
        messagesByContact.getOrPut(title) { mutableListOf() }.add(
            ChatMessage(text, isOwnMessage = false)
        )

        // In Datenbank speichern
        CoroutineScope(Dispatchers.IO).launch {
            val exists = repository.getAll().any {
                it.sender == title && it.text == text
            }
            if (!exists && !title.contains("Du")) {
                repository.insert(
                    WhatsAppMessage(
                        sender = title,
                        text = text,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val broadcastIntent = Intent("WHATSAPP_MESSAGE_RECEIVED").apply {
                    setPackage(applicationContext.packageName)
                }
                sendBroadcast(broadcastIntent)
            }
        }

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val quietEnd = prefs.getString("saved_number", null)?.toIntOrNull() ?: 21
        val quietStart = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 7
        if ((hour in quietStart..<quietEnd)) {
            return
        }

        QuietHoursNotificationService.updateSingleSenderNotification(this, title)

        // Notification Channel erstellen
        val channelId = "whatsapp_listener_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Messenger Listener",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Notification mit Reply-Action erstellen
        val replyData = replyActions[title]
        if (replyData != null) {
            val action = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Antworten",
                replyData.pendingIntent
            )
                .addRemoteInput(replyData.remoteInput)
                .setShowsUserInterface(false)
                .build()

            val messages = messagesByContact[title] ?: mutableListOf()

            val messagingStyle = NotificationCompat.MessagingStyle("Du")
                .setConversationTitle(title)

            val recentMessages = messages.takeLast(5)

            recentMessages.forEach { msg ->
                messagingStyle.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        msg.text,
                        msg.timestamp,
                        if (msg.isOwnMessage) "Du" else title
                    )
                )
            }

            val newNotification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(messagingStyle)
                .addAction(action)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(title.hashCode(), newNotification)
            } else {
                Log.w("MessageListener", "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        if (
            sbn.packageName != "com.example.cloud" &&
            sbn.packageName != "com.android.systemui"
        ) {
            activeNotifications?.let { forwardNotificationsToLaptop(it, packageManager) }
        }

        if (isSupportedApp(sbn.packageName)) {
            val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE)
            title?.let {
                replyActions.remove(it)
                messagesByContact.remove(it)
                Log.d("MessageListener", "Removed reply action for $title")
            }
        }

        NotificationRepository.removeNotification(sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationRepository.clear()
    }
}