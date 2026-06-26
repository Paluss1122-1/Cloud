package com.tabslify.tabs.aitab

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabslify.core.objects.prvt
import com.tabslify.privatetabslifyapp.isOnline
import com.tabslify.quiethoursnotificationhelper.askServer
import com.tabslify.quiethoursnotificationhelper.sendGeminiRequest
import com.tabslify.quiethoursnotificationhelper.sendNvidiaChatMessageAITab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Calendar

const val DAILY_LIMIT = 30
private const val USAGE_RESET_MS = 6 * 60 * 60 * 1000L

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
    var selectedAudioUri by mutableStateOf<Uri?>(null)
    var showAiModels by mutableStateOf(false)
    var editIndex: Int? by mutableStateOf(null)
    var isEditMode by mutableStateOf(false)
    var currentEditMsg by mutableStateOf("")
    var selectedMsg: Int? by mutableStateOf(null)
    var lastSelectedMsg: Int? by mutableStateOf(null)
    val history = mutableStateListOf<ChatMessage>()

    val availableModels
        get() = when (currentMode) {
            "Nvidia" -> nvidiaModels
            "Server" -> serverModels
            "Gemini" -> geminiModels
            else -> emptyList()
        }

    var selectedModel by mutableStateOf(availableModels[0])

    var todayUsage by mutableIntStateOf(0)
    var usageResetAt by mutableLongStateOf(0L)

    var historyLoaded = false

    var showLimitReached by mutableStateOf(false)

    fun loadHistory() {
        if (historyLoaded) return
        historyLoaded = true
        val ctx = getApplication<Application>()
        checkUsageResetIfNeeded(ctx)
        val json = ctx.getSharedPreferences("ai_prefs", MODE_PRIVATE)
            .getString("ai_history", null) ?: return
        try {
            history.addAll(Json.decodeFromString<List<ChatMessage>>(json))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTodayUsage(count: Int) {
        todayUsage = count
        setStoredUsage(count, usageResetAt)
    }

    private fun setStoredUsage(count: Int, resetAt: Long) {
        val ctx = getApplication<Application>()
        val prefs = ctx.getSharedPreferences("ai_usage", MODE_PRIVATE)
        prefs.edit {
            putInt("usage_count", count)
            putLong("usage_reset_at", resetAt)
        }
    }

    fun checkUsageResetIfNeeded(ctx: Context) {
        val prefs = ctx.getSharedPreferences("ai_usage", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val savedResetAt = prefs.getLong("usage_reset_at", 0L)
        val shouldReset = savedResetAt == 0L || now - savedResetAt >= USAGE_RESET_MS

        if (shouldReset) {
            usageResetAt = now
            todayUsage = 0
            setStoredUsage(0, now)
        } else {
            usageResetAt = savedResetAt
            todayUsage = prefs.getInt("usage_count", 0)
        }
    }

    fun getUsageProgress(): Float = (todayUsage.toFloat() / DAILY_LIMIT).coerceIn(0f, 1f)

    fun getUsageResetText(): String {
        val now = System.currentTimeMillis()
        val target = usageResetAt + USAGE_RESET_MS
        if (target <= now) return "Wird gleich zurückgesetzt"
        val remaining = target - now
        val hours = remaining / 3_600_000
        val minutes = (remaining % 3_600_000) / 60_000
        val resetTime = Calendar.getInstance().apply { timeInMillis = target }
        val hh = resetTime.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = resetTime.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "Reset um $hh:$mm (${hours}h ${minutes}m)"
    }

    fun setMode(mode: String) {
        if (currentMode == mode) return
        currentMode = mode
        selectedModel = availableModels[0]
    }

    fun selectModel(model: Model) {
        selectedModel = model
        if (!model.vision) selectedImageUri = null
        if (!model.audio) selectedAudioUri = null
    }

    fun clearHistory() {
        history.clear()
        persistHistory()
    }

    fun sendMessage() {
        val ctx = getApplication<Application>()
        val userText = if (isEditMode) currentEditMsg else currentMsg.trim()
        val fetchedEditIndex = editIndex
        if (isEditMode && fetchedEditIndex != null) {
            val toRemove = history.drop(fetchedEditIndex)
            history.removeAll(toRemove)
        }
        isEditMode = false
        if (userText.isEmpty() && selectedImageUri == null) return

        val isServer = currentMode == "Server"
        val isPrivate = prvt()

        if (!isServer && !isPrivate) {
            checkUsageResetIfNeeded(ctx)
            if (todayUsage + selectedModel.weight > DAILY_LIMIT) {
                showLimitReached = true
                return
            }
        }

        val modeAtSend = currentMode

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

        if (!isServer && !isPrivate) {
            updateTodayUsage(todayUsage + selectedModel.weight)
        }

        val placeholderTs = System.currentTimeMillis()
        history.add(ChatMessage("", placeholderTs, false, modeAtSend))
        val placeholderIndex = history.lastIndex

        val onToken: (String) -> Unit = { delta ->
            val current = history.getOrNull(placeholderIndex)?.text ?: ""
            history[placeholderIndex] = ChatMessage(
                text = current + delta,
                ts = placeholderTs,
                own = false,
                mode = modeAtSend
            )
        }

        viewModelScope.launch {
            try {
                val effectivePic = if (selectedModel.vision && selectedImageUri != null) {
                    selectedImageUri?.let { encodeImage(ctx, it) }
                } else null
                val effectiveAudio = if (currentMode != "Gemini" && selectedModel.audio && selectedAudioUri != null) {
                    selectedAudioUri?.let { encodeAudio(ctx, it) }
                } else null
                val response = withContext(Dispatchers.IO) {
                    send(ctx, userText.ifEmpty { "Beschreibe das Bild" }, effectivePic, effectiveAudio, onToken)
                }
                selectedImageUri = null
                selectedAudioUri = null

                sendAITabBackgroundNotification(
                    ctx,
                    title = "AITab answer",
                    message = response
                )
            } catch (e: Exception) {
                if (placeholderIndex < history.size) {
                    history[placeholderIndex] = ChatMessage(
                        "Fehler: ${e.message}",
                        placeholderTs,
                        false,
                        modeAtSend
                    )
                }
            } finally {
                isLoading = false
                persistHistory()
            }
        }
    }

    private suspend fun send(ctx: Context, txt: String, pic: String?, audio: String?, onToken: (String) -> Unit): String {
        if (!isOnline(ctx)) return "Kein Netzwerk"
        return when (currentMode) {
            "Nvidia" -> sendNvidiaChatMessageAITab(ctx, history, txt, selectedModel.realname, pic, onToken = onToken) ?: "Fehler"
            "Server" -> askServer(history, txt, selectedModel.realname, pic)
            "Gemini" -> sendGeminiRequest(history, txt, pic, audioUri = selectedAudioUri,
                ctx = ctx, model = selectedModel.realname, onToken = onToken) ?: "Fehler"
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
            val output = ByteArrayOutputStream()
            android.util.Base64OutputStream(output, Base64.NO_WRAP).use { base64Out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, base64Out)
            }
            output.toString("UTF-8")
        }
    } catch (_: Exception) {
        null
    }

    private fun encodeAudio(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            android.util.Base64OutputStream(output, Base64.NO_WRAP).use { base64Out ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    base64Out.write(buffer, 0, bytesRead)
                }
            }
            output.toString("UTF-8")
        }
    } catch (_: Exception) { null }

    suspend fun animateAlpha(alpha: Animatable<Float, AnimationVector1D>) {
        delay(100)
        alpha.animateTo(
            1f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        )
    }
}
