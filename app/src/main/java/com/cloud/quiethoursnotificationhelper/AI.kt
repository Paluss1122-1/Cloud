package com.cloud.quiethoursnotificationhelper

import android.graphics.BitmapFactory
import android.util.Base64
import com.cloud.core.objects.Config.DEF_GEMINI
import com.cloud.tabs.aitab.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import org.json.JSONArray
import org.json.JSONObject

suspend fun sendGeminiRequest(
    history: List<ChatMessage>,
    userMessage: String,
    pic: String? = null,
    anlytic: Boolean = false
): String? {
    fun buildGeminiPrompt(history: List<ChatMessage>, userMessage: String) = buildString {
        if (anlytic) {
            append("Du bist ein cooler Musik-Assistent. Antworte auf Deutsch, total locker und umgangssprachlich, wie ein Kumpel. Mach 3-5 super knappe Sätze. Verwende Ausdrücke wie 'krass', 'geil', 'richtig lange', 'am Stück'. Red von 'heute' wenn es passt.\n\n")
        } else {
            append("Du bist ein hilfreicher Chat-Assistent. Antworte kurz, klar und auf Deutsch.\n\n")
        }
        history.forEach { msg ->
            append(if (msg.own) "User: " else "Assistant: ")
            append(msg.text)
            append("\n")
        }
        append("\nUser: $userMessage")
    }

    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(DEF_GEMINI)

    return try {
        if (pic != null) {
            val imageBytes = Base64.decode(pic, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val response = model.generateContent(
                content {
                    image(bmp)
                    text(buildGeminiPrompt(history, userMessage))
                }
            )
            response.text
        } else {
            model.generateContent(buildGeminiPrompt(history, userMessage)).text
        }
    } catch (_: Exception) {
        null
    }
}

suspend fun sendNvidiaChatMessageAITab(
    history: List<ChatMessage>,
    userMessage: String,
    model: String = "nvidia/nemotron-3-nano-30b-a3b",
    pic: String? = null
): String? {
    val messages = JSONArray().apply {
        put(JSONObject().apply {
            put("role", "system")
            put(
                "content",
                "Du bist ein hilfreicher Chat-Assistent. Antworte kurz, klar und auf Deutsch."
            )
        })

        history.forEach { msg ->
            put(JSONObject().apply {
                put("role", if (msg.own) "user" else "assistant")
                put("content", msg.text)
            })
        }

        put(JSONObject().apply { put("role", "user"); put("content", userMessage) })

        if (pic != null) {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$pic")
                        })
                    })
                })
            })
        }
    }
    return callNvidiaApi(model, messages)
}