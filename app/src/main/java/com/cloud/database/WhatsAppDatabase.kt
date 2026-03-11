package com.cloud.database

import kotlinx.coroutines.flow.MutableStateFlow

data class WhatsAppMessage(
    val id: Int = 0,
    val sender: String,
    val text: String,
    val timestamp: Long
)

class WhatsAppMessageRepository {
    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())

    fun insert(message: WhatsAppMessage) {
        val newMessages = _messages.value.toMutableList().apply {
            add(message)
            while (size > 100) removeAt(0)
        }
        _messages.value = newMessages
    }

    fun getAllFiltered(): List<WhatsAppMessage> {
        return _messages.value
            .filter { it.sender !in listOf("Ich", "Du") }
            .sortedByDescending { it.timestamp }
            .take(100)
    }

    fun getAll(): List<WhatsAppMessage> {
        return _messages.value
            .sortedByDescending { it.timestamp }
            .take(100)
    }

    fun getMessagesBySender(sender: String): List<WhatsAppMessage> {
        return _messages.value
            .filter { it.sender == sender }
            .sortedBy { it.timestamp }
    }
}