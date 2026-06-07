package com.tabslify.quiethoursnotificationhelper

import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.DEF_GEMINI
import com.tabslify.tabs.aitab.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun buildSystemPrompt(target: String = ""): String {
    val aiTab = if (target == "AITab") " in einem Tab namens AITab" else ""
    val aiTabInfo =
        if (target == "AITab") " Der Nutzer kann zwischen verschiedenen NVIDIA-Modellen und verschiedenen Gemini-Modellen auswählen – und hat sich für DICH entschieden." else ""
    val notif = if (target == "notif") " von einem Reply System" else ""
    var str = ""
    if (target.isEmpty()) {
        str = buildString {
            append(
                """Du wirst per API aus einer Multifunktions-Android-App (names Tabslify)${aiTab}${notif} aufgerufen.${aiTabInfo} Deine Aufgabe ist es, die Frage des Nutzers zu beantworten.
                    Wichtige Hinweise:
                        * Antworte kurz, klar und auf Deutsch.
                        * Sei ein hilfsbereiter Chat-Assistent."""
            )
            if (target == "AITab") {
                append(
                    """* Nutze Markdown für Formatierungen (Überschriften, Listen, Fettschrift etc.).
                        * Nutze die folgenden Callouts für einprägsame Informationen (immer in einem eigenen Blockquote):
                          - [!TIP] oder [!HINT] oder [!IMPORTANT] für Tipps und wichtige Hinweise
                          - [!WARNING] oder [!CAUTION] oder [!ATTENTION] für einprägsame Informationen
                          - [!INFO] für allgemeine Informationen
                          - [!NOTE] für Notizen
                          - [!SUCCESS] oder [!CHECK] oder [!DONE] für Erfolgsmeldungen
                          - [!DANGER] oder [!ERROR] für Fehler""${'"'}"""
                )
            }
        }
    }
    return str
}

suspend fun sendGeminiRequest(
    history: List<ChatMessage> = emptyList(),
    userMessage: String,
    pic: String? = null,
    audioUri: android.net.Uri? = null,
    ctx: android.content.Context? = null,
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

    val promptText = buildGeminiPrompt(history, userMessage)
    val bmp = pic?.let { Base64.decode(it, Base64.NO_WRAP) }
        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    val (audioBytes, audioMimeType) = if (audioUri != null && ctx != null) {
        val mimeType = ctx.contentResolver.getType(audioUri) ?: "audio/mp3"
        val bytes = ctx.contentResolver.openInputStream(audioUri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.toByteArray()
        }
        bytes to mimeType
    } else {
        null to null
    }

    val requestContent = content {
        bmp?.let { image(it) }
        audioBytes?.let {
            if (audioMimeType != null) {
                inlineData(it, audioMimeType)
            }
        }
        text(promptText)
    }

    return try {
        if (onToken != null) {
            val sb = StringBuilder()
            generativeModel.generateContentStream(requestContent).collect { chunk ->
                val delta = chunk.text ?: ""
                if (delta.isNotEmpty()) {
                    sb.append(delta)
                    withContext(Dispatchers.Main) { onToken(delta) }
                }
            }
            sb.toString().ifBlank { null }
        } else {
            generativeModel.generateContent(requestContent).text
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
                connection.outputStream.use {
                    it.write(
                        requestBody.toString().toByteArray(Charsets.UTF_8)
                    )
                }

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