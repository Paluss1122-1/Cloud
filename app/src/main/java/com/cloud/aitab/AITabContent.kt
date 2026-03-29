package com.cloud.aitab

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.cloud.privatecloudapp.isOnline
import com.cloud.quiethoursnotificationhelper.askServer
import com.cloud.quiethoursnotificationhelper.sendNvidiaChatMessageAITab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatMessage(
    val text: String,
    val ts: Long,
    val own: Boolean,
    val mode: String? = null
)

private fun saveHistory(context: Context, scope: CoroutineScope, history: List<ChatMessage>) {
    val listToSave = history.toList()
    scope.launch {
        val jsonString = Json.encodeToString(listToSave)
        context.getSharedPreferences("ai_prefs", MODE_PRIVATE).edit {
            putString("ai_history", jsonString)
        }
    }
}

@Composable
fun AITabContent() {
    var currentMode by remember { mutableStateOf("Nvidia") }
    var currentMsg by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<ChatMessage>() }
    var historyLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (historyLoaded) return@LaunchedEffect
        historyLoaded = true

        val jsonString = context.getSharedPreferences("ai_prefs", MODE_PRIVATE)
            .getString("ai_history", null) ?: return@LaunchedEffect

        try {
            val loadedList = Json.decodeFromString<List<ChatMessage>>(jsonString)
            history.addAll(loadedList)
        } catch (e: Exception) {
            e.printStackTrace()

        }
    }

    suspend fun send(txt: String): String {
        if (!isOnline(context)) return "Kein Netzwerk"
        return when (currentMode) {
            "Nvidia" -> sendNvidiaChatMessageAITab(history, txt) ?: "Fehler"
            "Server" -> askServer(history, txt)
            else -> "Wähle einen Modus"
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Nvidia", "Server").forEach { mode ->
                    Button(
                        onClick = { currentMode = mode },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == mode) Color(0xFF555555) else Color(
                                0xFF333333
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(mode, color = Color.White)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(history) { msg ->
                    val isUser = msg.own
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                            Text(
                                text = msg.text,
                                color = Color.White,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .background(
                                        if (isUser) MaterialTheme.colorScheme.primary else Color(0xFF444444),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .widthIn(max = 280.dp)
                            )
                            if (!msg.own && msg.mode != null) {
                                Text(
                                    text = msg.mode,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "…",
                                color = Color.White,
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .background(Color(0xFF444444), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = currentMsg,
                    onValueChange = { currentMsg = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("Nachricht eingeben...", color = Color(0xFF888888)) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2A2A),
                        unfocusedContainerColor = Color(0xFF2A2A2A),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val userText = currentMsg.trim()
                            if (userText.isNotEmpty()) {
                                val modeAtSend = currentMode

                                val userMsg = ChatMessage(
                                    text = userText,
                                    ts = System.currentTimeMillis(),
                                    own = true
                                )
                                history.add(userMsg)
                                saveHistory(context, scope, history)
                                currentMsg = ""
                                isLoading = true

                                scope.launch {
                                    try {
                                        val responseText = withContext(Dispatchers.IO) {
                                            send(userText)
                                        }

                                        val aiMsg = ChatMessage(
                                            text = responseText,
                                            ts = System.currentTimeMillis(),
                                            own = false,
                                            mode = modeAtSend
                                        )

                                        history.add(aiMsg)
                                        isLoading = false
                                        saveHistory(context, scope, history)
                                    } catch (e: Exception) {
                                        isLoading = false
                                        history.add(
                                            ChatMessage(
                                                "Fehler: ${e.message}",
                                                System.currentTimeMillis(),
                                                false,
                                                modeAtSend
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF333333), RoundedCornerShape(50))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                history.clear()
                                saveHistory(context, scope, history)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color(0xFF888888), fontSize = 16.sp)
                }
            }
        }
    }

    LaunchedEffect(history.size, isLoading) {
        if (history.isNotEmpty()) listState.animateScrollToItem(
            history.lastIndex + if (isLoading) 1 else 0
        )
    }
}