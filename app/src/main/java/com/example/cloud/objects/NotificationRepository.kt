package com.example.cloud.objects

import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

object NotificationRepository {
    private val _notifications = mutableStateListOf<StatusBarNotification>()
    val notifications: List<StatusBarNotification> get() = _notifications

    fun addNotification(sbn: StatusBarNotification) {
        // Entferne ggf. Duplikate (anhand der eindeutigen `key`)
        _notifications.removeAll { it.key == sbn.key }
        _notifications.add(sbn)
    }

    fun removeNotification(sbn: StatusBarNotification) {
        _notifications.removeAll { it.key == sbn.key }
    }

    fun clear() {
        _notifications.clear()
    }
}