package com.example.cloud.whatsapptab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.cloud.database.WhatsAppMessage
import com.example.cloud.service.WhatsAppNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SentMessage(
    val recipient: String,
    val text: String,
    val timestamp: Long
)

object SentMessageTracker {
    private val sentMessages = mutableListOf<SentMessage>()

    fun addSentMessage(recipient: String, text: String) {
        sentMessages.add(0, SentMessage(recipient, text, System.currentTimeMillis()))
        // Behalte nur die letzten 20 Nachrichten
        if (sentMessages.size > 20) {
            sentMessages.removeAt(sentMessages.size - 1)
        }
    }

    fun getSentMessages(): List<SentMessage> = sentMessages.toList()
}

@Composable
fun WhatsAppTabScreen() {
    val context = LocalContext.current
    var messages by remember { mutableStateOf(WhatsAppNotificationListener.getMessages()) }
    var sentMessages by remember { mutableStateOf(SentMessageTracker.getSentMessages()) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                messages = WhatsAppNotificationListener.getMessages()
                sentMessages = SentMessageTracker.getSentMessages()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
    ) {

        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFF444444)))

        // Empfangene Nachrichten
        WhatsAppTabContent(messages) { updated -> }
    }
}

@Composable
fun WhatsAppTabContent(
    messages: List<WhatsAppMessage>,
    onMessageSent: (List<SentMessage>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (messages.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2A2A2A)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "📱", fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Keine WhatsApp-Nachrichten",
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "💡 Tipp: Erlaube Benachrichtigungen und\nschreib dir selbst eine Testnachricht!",
                fontSize = 12.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                MessageCard(
                    message = message,
                    context = context,
                    scope = scope,
                    onMessageSent = onMessageSent
                )
            }
        }
    }
}

@Composable
fun MessageCard(
    message: WhatsAppMessage,
    context: Context,
    scope: CoroutineScope,
    onMessageSent: (List<SentMessage>) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var showAllMessagesDialog by remember { mutableStateOf(false) }
    var allMessagesText by remember { mutableStateOf("") }

    if (showAllMessagesDialog) {
        AlertDialog(
            onDismissRequest = { showAllMessagesDialog = false },
            title = { Text("Alle Nachrichten von ${message.sender}") },
            text = {
                LazyColumn {
                    item {
                        Text(allMessagesText, color = Color.White)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAllMessagesDialog = false }) {
                    Text("OK")
                }
            },
            containerColor = Color(0xFF2A2A2A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message.sender,
                fontSize = 18.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message.text,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Antworten...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF333333),
                        unfocusedContainerColor = Color(0xFF333333)
                    ),
                    singleLine = true
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            scope.launch {
                                val success = WhatsAppNotificationListener.sendReply(
                                    message.sender,
                                    replyText,
                                    context
                                )
                                if (success) {
                                    SentMessageTracker.addSentMessage(message.sender, replyText)
                                    onMessageSent(SentMessageTracker.getSentMessages())
                                    replyText = ""
                                }
                            }
                        }
                    },
                    enabled = replyText.isNotBlank()
                ) {
                    Text("📤")
                }
            }

            TextButton(
                onClick = {
                    scope.launch {
                        val allMessages =
                            WhatsAppNotificationListener.getMessagesBySender(message.sender)
                        allMessagesText = allMessages.joinToString("\n\n") {
                            val time = SimpleDateFormat(
                                "dd.MM. HH:mm",
                                Locale.getDefault()
                            )
                                .format(Date(it.timestamp))
                            "[$time] ${it.sender}: ${it.text}"
                        }
                        showAllMessagesDialog = true
                    }
                }
            ) {
                Text("Alle Nachrichten anzeigen", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}