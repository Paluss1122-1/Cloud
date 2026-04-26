package com.cloud.core.objects

import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

object NotificationRepository {
    private val _notifications = mutableStateListOf<StatusBarNotification>()

    fun removeNotification(sbn: StatusBarNotification) {
        _notifications.removeAll { it.key == sbn.key }
    }

    fun clear() {
        _notifications.clear()
    }
}