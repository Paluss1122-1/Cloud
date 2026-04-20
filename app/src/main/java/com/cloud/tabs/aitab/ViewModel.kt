package com.cloud.tabs.aitab

import android.app.Application
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloud.privatecloudapp.isOnline
import com.cloud.quiethoursnotificationhelper.askServer
import com.cloud.quiethoursnotificationhelper.sendNvidiaChatMessageAITab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@Serializable
data class ChatMessage(
    val text: String,
    val ts: Long,
    val own: Boolean,
    val mode: String? = null
)

class AITabViewModel(application: Application) : AndroidViewModel(application) {
    var currentMode by mutableStateOf("Nvidia")
    var currentMsg by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var selectedImageUri by mutableStateOf<Uri?>(null)
    var showAiModels by mutableStateOf(false)

    val history = mutableStateListOf<ChatMessage>()

    val availableModels
        get() = when (currentMode) {
            "Nvidia" -> nvidiaModels
            "Server" -> serverModels
            else -> emptyList()
        }

    var selectedModel by mutableStateOf(availableModels[0])

    var historyLoaded = false

    fun loadHistory() {
        if (historyLoaded) return
        historyLoaded = true
        val ctx = getApplication<Application>()
        val json = ctx.getSharedPreferences("ai_prefs", MODE_PRIVATE)
            .getString("ai_history", null) ?: return
        try {
            history.addAll(Json.decodeFromString<List<ChatMessage>>(json))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMode(mode: String) {
        if (currentMode == mode) return
        currentMode = mode
        selectedModel = availableModels[0]
    }

    fun selectModel(model: Model) {
        selectedModel = model
        if (!model.vision) selectedImageUri = null
    }

    fun clearHistory() {
        history.clear()
        persistHistory()
    }

    fun sendMessage() {
        val ctx = getApplication<Application>()
        val userText = currentMsg.trim()
        if (userText.isEmpty() && selectedImageUri == null) return

        val modeAtSend = currentMode
        val imageBase64 = selectedImageUri?.let { encodeImage(ctx, it) }

        history.add(
            ChatMessage(
                text = userText.ifEmpty { "Beschreibe das Bild" },
                ts = System.currentTimeMillis(),
                own = true
            )
        )
        persistHistory()
        currentMsg = ""
        isLoading = true

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    send(
                        ctx, userText.ifEmpty { "Beschreibe das Bild" },
                        if (selectedModel.vision && selectedImageUri != null) imageBase64 else null
                    )
                }
                selectedImageUri = null
                history.add(ChatMessage(response, System.currentTimeMillis(), false, modeAtSend))
            } catch (e: Exception) {
                history.add(
                    ChatMessage(
                        "Fehler: ${e.message}",
                        System.currentTimeMillis(),
                        false,
                        modeAtSend
                    )
                )
            } finally {
                isLoading = false
                persistHistory()
            }
        }
    }

    private suspend fun send(ctx: Context, txt: String, pic: String?): String {
        if (!isOnline(ctx)) return "Kein Netzwerk"
        return when (currentMode) {
            "Nvidia" -> sendNvidiaChatMessageAITab(history, txt, selectedModel.realname, pic)
                ?: "Fehler"

            "Server" -> askServer(history, txt, selectedModel.realname, pic)
            else -> "Wähle einen Modus"
        }
    }

    private fun persistHistory() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val json = Json.encodeToString(history.toList())
            ctx.getSharedPreferences("ai_prefs", MODE_PRIVATE)
                .edit { putString("ai_history", json) }
        }
    }

    private fun encodeImage(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val bmp = BitmapFactory.decodeStream(input)
            val bytes = ByteArrayOutputStream().also {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    } catch (_: Exception) {
        null
    }

    suspend fun animateAlpha(alpha: Animatable<Float, AnimationVector1D>) {
        delay(100)
        alpha.animateTo(
            1f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        )
    }
}