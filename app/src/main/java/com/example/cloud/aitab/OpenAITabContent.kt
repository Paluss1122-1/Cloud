package com.example.cloud.aitab

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.AIConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(
    val role: String,
    val content: String
)

@Composable
fun AITabContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val userInput = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val apiProvider = remember { mutableStateOf("claude") } // "claude", "openai", "huggingface"
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
    ) {
        // Header mit Provider-Auswahl
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Chat",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { apiProvider.value = "claude" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (apiProvider.value == "claude") Color(0xFFCC785C) else Color(0xFF444444)
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Claude", fontSize = 11.sp)
                }

                Button(
                    onClick = { apiProvider.value = "openai" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (apiProvider.value == "openai") Color(0xFF10A37F) else Color(0xFF444444)
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("OpenAI", fontSize = 11.sp)
                }

                Button(
                    onClick = { apiProvider.value = "huggingface" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (apiProvider.value == "huggingface") Color(0xFFFFD21E) else Color(0xFF444444)
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("DeepSeek", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message, apiProvider.value)
            }

            if (isLoading.value) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = when (apiProvider.value) {
                                "claude" -> Color(0xFFCC785C)
                                "openai" -> Color(0xFF10A37F)
                                else -> Color(0xFFFFD21E)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = userInput.value,
                onValueChange = { userInput.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nachricht eingeben...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF2A2A2A),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 4
            )

            Button(
                onClick = {
                    if (userInput.value.isNotBlank() && !isLoading.value) {
                        val prompt = userInput.value
                        userInput.value = ""

                        messages.add(ChatMessage("user", prompt))

                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                            isLoading.value = true

                            val response = when (apiProvider.value) {
                                "claude" -> callClaude(context, messages)
                                "openai" -> callOpenAI(context, messages)
                                "huggingface" -> callHuggingFace(context, messages)
                                else -> "❌ Unbekannter Provider"
                            }
                            messages.add(ChatMessage("assistant", response))
                            listState.animateScrollToItem(messages.size - 1)

                            isLoading.value = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (apiProvider.value) {
                        "claude" -> Color(0xFFCC785C)
                        "openai" -> Color(0xFF10A37F)
                        else -> Color(0xFFFFD21E)
                    },
                    contentColor = Color.White
                ),
                enabled = !isLoading.value && userInput.value.isNotBlank()
            ) {
                Text("Senden")
            }
        }

        Button(
            onClick = { messages.clear() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF444444)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Chat löschen")
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, provider: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.role == "user") Color(0xFF2B5278) else Color(0xFF2A2A2A),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.role == "user") "Du" else "AI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (message.role == "user") Color(0xFF87CEEB) else {
                        when (provider) {
                            "claude" -> Color(0xFFCC785C)
                            "openai" -> Color(0xFF10A37F)
                            else -> Color(0xFFFFD21E)
                        }
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

suspend fun callClaude(context: Context, messages: List<ChatMessage>): String {
    return withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("cloud_app_prefs", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("claude_api_key", "")

            if (apiKey.isNullOrBlank()) {
                return@withContext "❌ Kein Claude API Key gefunden.\n\nSpeichere ihn unter 'claude_api_key' in SharedPreferences.\n\nHole dir einen Key auf: console.anthropic.com"
            }

            val url = URL("https://api.anthropic.com/v1/messages")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val messagesArray = JSONArray()
            messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("role", msg.role)
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
            }

            val requestBody = JSONObject()
            requestBody.put("model", "claude-3-5-sonnet-20241022")
            requestBody.put("max_tokens", 1024)
            requestBody.put("messages", messagesArray)

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                val content = jsonResponse.getJSONArray("content")

                if (content.length() > 0) {
                    val text = content.getJSONObject(0).getString("text")
                    return@withContext text.trim()
                } else {
                    return@withContext "❌ Keine Antwort erhalten"
                }
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.readText()
                errorReader.close()

                return@withContext "❌ Claude API Fehler ($responseCode)\n\n$errorResponse"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "❌ Fehler: ${e.message}"
        }
    }
}

suspend fun callOpenAI(context: Context, messages: List<ChatMessage>): String {
    return withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("cloud_app_prefs", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("openai_api_key", "")

            if (apiKey.isNullOrBlank()) {
                return@withContext "❌ Kein OpenAI API Key gefunden.\n\nSpeichere ihn unter 'openai_api_key' in SharedPreferences.\n\nWichtig: OpenAI benötigt bezahltes Guthaben!"
            }

            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val messagesArray = JSONArray()
            messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("role", msg.role)
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
            }

            val requestBody = JSONObject()
            requestBody.put("model", "gpt-3.5-turbo")
            requestBody.put("messages", messagesArray)
            requestBody.put("max_tokens", 800)

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")

                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    val content = message.getString("content")
                    return@withContext content.trim()
                } else {
                    return@withContext "❌ Keine Antwort erhalten"
                }
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.readText()
                errorReader.close()

                return@withContext "❌ OpenAI API Fehler ($responseCode)\n\n$errorResponse"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "❌ Fehler: ${e.message}"
        }
    }
}

suspend fun callHuggingFace(context: Context, messages: List<ChatMessage>): String {
    return withContext(Dispatchers.IO) {
        try {
            val apiKey = AIConfig.APIKEY

            val url = URL("https://router.huggingface.co/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 45000
            connection.readTimeout = 45000

            val messagesArray = JSONArray()
            messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("role", msg.role)
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
            }

            val requestBody = JSONObject()
            requestBody.put("model", "google/embeddinggemma-300m")
            requestBody.put("messages", messagesArray)
            requestBody.put("max_tokens", 1024)

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")

                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    val content = message.getString("content")
                    return@withContext content.trim()
                } else {
                    return@withContext "❌ Keine Antwort erhalten"
                }
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.readText()
                errorReader.close()

                return@withContext "❌ HuggingFace API Fehler ($responseCode)\n\n$errorResponse"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "❌ Fehler: ${e.message}"
        }
    }
}