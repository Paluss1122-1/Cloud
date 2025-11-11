package com.example.cloud.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WhatsAppMessage(
    val id: Int = 0,
    val sender: String,
    val text: String,
    val timestamp: Long
)

class WhatsAppMessageRepository {
    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    val messages: StateFlow<List<WhatsAppMessage>> = _messages.asStateFlow()

    fun insert(message: WhatsAppMessage) {
        val newMessages = _messages.value.toMutableList().apply {
            add(message)
            // Optional: auf 100 begrenzen (wie bei Room)
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

    fun deleteAll() {
        _messages.value = emptyList()
    }
}