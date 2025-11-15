package com.example.cloud.whatsapptab

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.database.WhatsAppMessage
import com.example.cloud.service.WhatsAppNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun MessageCard(message: WhatsAppMessage, context: Context, scope: CoroutineScope) {
    var replyText by remember { mutableStateOf("") }
    var showAllMessagesDialog by remember { mutableStateOf(false) }
    var allMessagesText by remember { mutableStateOf("") }

    // AlertDialog für alle Nachrichten
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
            // Sender
            Text(
                text = message.sender,
                fontSize = 18.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )

            // Message
            Text(
                text = message.text,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Reply Section
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

            // Button für alle Nachrichten
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