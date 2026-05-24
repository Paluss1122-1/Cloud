package com.cloud.quiethoursnotificationhelper

import android.graphics.BitmapFactory
import android.util.Base64
import com.cloud.core.objects.Config
import com.cloud.core.objects.Config.DEF_GEMINI
import com.cloud.tabs.aitab.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun buildSystemPrompt(target: String = ""):String {
    val aiTab = if (target == "AITab") " in einem Tab namens AITab" else ""
    val aiTabInfo = if (target == "AITab") " Der Nutzer kann zwischen verschiedenen NVIDIA-Modellen und verschiedenen Gemini-Modellen auswählen – und hat sich für DICH entschieden." else ""
    val notif = if (target == "notif") " von einem Reply System" else ""
    var str = ""
    if (target.isEmpty()) {
        str = buildString {
            append(
                """Du wirst per API aus einer Multifunktions-Android-App (names Cloud)${aiTab}${notif} aufgerufen.${aiTabInfo} Deine Aufgabe ist es, die Frage des Nutzers zu beantworten.
                    Wichtige Hinweise:
                        * Antworte kurz, klar und auf Deutsch.
                        * Sei ein hilfsbereiter Chat-Assistent."""
            )
        }
    }
    return str
}

suspend fun sendGeminiRequest(
    history: List<ChatMessage>,
    userMessage: String,
    pic: String? = null,
    anlytic: Boolean = false,
    model: String = DEF_GEMINI,
    onToken: ((String) -> Unit)? = null,
    target: String = "AITab"
): String? {
    fun buildGeminiPrompt(history: List<ChatMessage>, userMessage: String) = buildString {
        if (anlytic) {
            append("Du bist ein cooler Musik-Assistent. Antworte auf Deutsch, total locker und umgangssprachlich, wie ein Kumpel. Mach 3-5 super knappe Sätze. Verwende Ausdrücke wie 'krass', 'geil', 'richtig lange', 'am Stück'. Red von 'heute' wenn es passt.\n\n")
        } else {
            append(buildSystemPrompt(target))
        }
        history.forEach { msg ->
            append(if (msg.own) "User: " else "Assistant: ")
            append(msg.text)
            append("\n")
        }
        append("\nUser: $userMessage")
    }

    val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(model)

    return try {
        if (pic != null) {
            val imageBytes = Base64.decode(pic, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            if (onToken != null) {
                val sb = StringBuilder()
                generativeModel.generateContentStream(
                    content {
                        image(bmp)
                        text(buildGeminiPrompt(history, userMessage))
                    }
                ).collect { chunk ->
                    val delta = chunk.text ?: ""
                    if (delta.isNotEmpty()) {
                        sb.append(delta)
                        withContext(Dispatchers.Main) { onToken(delta) }
                    }
                }
                sb.toString().ifBlank { null }
            } else {
                val response = generativeModel.generateContent(
                    content {
                        image(bmp)
                        text(buildGeminiPrompt(history, userMessage))
                    }
                )
                response.text
            }
        } else {
            if (onToken != null) {
                val sb = StringBuilder()
                generativeModel.generateContentStream(
                    buildGeminiPrompt(history, userMessage)
                ).collect { chunk ->
                    val delta = chunk.text ?: ""
                    if (delta.isNotEmpty()) {
                        sb.append(delta)
                        withContext(Dispatchers.Main) { onToken(delta) }
                    }
                }
                sb.toString().ifBlank { null }
            } else {
                generativeModel.generateContent(
                    buildGeminiPrompt(history, userMessage)
                ).text
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun buildNvidiaAITabMessages(
    history: List<ChatMessage>,
    userMessage: String,
    pic: String?
): JSONArray = JSONArray().apply {
    put(JSONObject().apply {
        put("role", "system")
        put(
            "content",
            "Du bist ein hilfreicher Chat-Assistent. Antworte kurz, klar und auf Deutsch und verwende keine Markdown Syntax."
        )
    })

    history.forEach { msg ->
        put(JSONObject().apply {
            put("role", if (msg.own) "user" else "assistant")
            put("content", msg.text)
        })
    }

    put(JSONObject().apply {
        put("role", "user")
        if (pic != null) {
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", userMessage)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$pic")
                    })
                })
            })
        } else {
            put("content", userMessage)
        }
    })
}

suspend fun sendNvidiaChatMessageAITab(
    history: List<ChatMessage>,
    userMessage: String,
    model: String,
    pic: String? = null,
    onToken: ((String) -> Unit)? = null
): String? {
    val messages = buildNvidiaAITabMessages(history, userMessage, pic)
    return if (onToken != null) {
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            var connection: HttpURLConnection? = null
            try {
                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("temperature", 0.3)
                    put("max_tokens", 1024)
                    put("stream", true)
                }
                connection = (URL("https://integrate.api.nvidia.com/v1/chat/completions")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer ${Config.NVIDIA}")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                }
                connection.outputStream.use { it.write(requestBody.toString().toByteArray(Charsets.UTF_8)) }

                if (connection.responseCode != 200) return@withContext ""

                connection.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val delta = JSONObject(data)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("delta")
                                .optString("content", "")
                            if (delta.isNotEmpty()) {
                                sb.append(delta)
                                withContext(Dispatchers.Main) { onToken(delta) }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            } finally {
                connection?.disconnect()
            }
            sb.toString()
        }
    } else {
        callNvidiaApi(model, messages)
    }
}