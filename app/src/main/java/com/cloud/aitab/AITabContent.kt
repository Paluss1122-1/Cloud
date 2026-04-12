package com.cloud.aitab

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.cloud.privatecloudapp.PloppingButton
import com.cloud.privatecloudapp.isOnline
import com.cloud.quiethoursnotificationhelper.askServer
import com.cloud.quiethoursnotificationhelper.sendNvidiaChatMessageAITab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    data class Model(
        val realname: String,
        val vision: Boolean = false,
        val name: String = realname.substringAfter("/", realname)
    )

    var currentMsg by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val nvidiaModels = listOf(
        Model("meta/llama-3.1-8b-instruct"),
        Model("utter-project/eurollm-9b-instruct"),
        Model("google/gemma-2-9b-it"),
        Model("openai/gpt-oss-120b"),
        Model("openai/gpt-oss-20b"),
        Model("minimaxai/minimax-m2.5"),
        Model("bigcode/starcoder2-7b"),
        Model("nvidia/nemotron-3-nano-30b-a3b"),
        Model("nvidia/nemoretriever-ocr-v1", true),
        Model("meta/llama-3.2-90b-vision-instruct", true),
        Model("meta/llama-4-maverick-17b-128e-instruct", true),
        Model("qwen/qwen3.5-397b-a17b", true),
    )
    val serverModels = listOf(
        Model("qwen2.5:7b"),
        Model("qwen2.5-coder:3b"),
        Model("qwen2.5-coder:7b"),
        Model("qwen2.5-coder:14b"),
        Model("qwen3-coder-next:cloud"),
        Model("qwen3-vl:235b-cloud", true),
        Model("llava:13b", true),
        Model("llama3.2-vision:11b", true),
    )

    val availableModels = when (currentMode) {
        "Nvidia" -> nvidiaModels
        "Server" -> serverModels
        else -> {
            emptyList()
        }
    }

    var selectedModel by remember { mutableStateOf(availableModels[0]) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<ChatMessage>() }
    var historyLoaded by remember { mutableStateOf(false) }
    var showAiModels by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        selectedModel = availableModels[0]
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

    suspend fun send(txt: String, pic: String? = null): String {
        if (!isOnline(context)) return "Kein Netzwerk"
        return when (currentMode) {
            "Nvidia" -> sendNvidiaChatMessageAITab(history, txt, selectedModel.realname, pic)
                ?: "Fehler"
            "Server" -> askServer(history, txt, selectedModel.realname, pic)
            else -> "Wähle einen Modus"
        }
    }

    val alpha = remember { Animatable(0f) }

    val sendmsg = {
        val userText = currentMsg.trim()
        if (userText.isNotEmpty() || selectedImageUri != null) {
            val modeAtSend = currentMode

            val imageBase64 = selectedImageUri?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        val bytes = java.io.ByteArrayOutputStream().also {
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
                        }.toByteArray()
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            val userMsg = ChatMessage(
                text = userText.ifEmpty { "Beschreibe das Bild" },
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
                        send(
                            userText.ifEmpty { "Beschreibe das Bild" },
                            if (selectedModel.vision && selectedImageUri != null) imageBase64 else null
                        )
                    }
                    selectedImageUri = null

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

    LaunchedEffect(Unit) {
        delay(100)
        alpha.animateTo(
            1f, animationSpec = tween(
                durationMillis = 150,
                easing = FastOutSlowInEasing
            )
        )
    }

    LaunchedEffect(currentMode) {
        selectedModel = availableModels[0]
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
            .alpha(alpha.value)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Nvidia", "Server").forEachIndexed { index, mode ->
                        val containerColor by animateColorAsState(
                            targetValue = if (currentMode == mode) Color(0xFF555555) else Color(
                                0xFF333333
                            ),
                            animationSpec = tween(durationMillis = 300),
                            label = "containerColor"
                        )
                        Box {
                            PloppingButton(
                                onClick = {
                                    if (currentMode == mode) showAiModels = true
                                    currentMode = mode
                                },
                                colors = buttonColors(containerColor = containerColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(mode, color = White)
                            }

                            if (index == 0) {
                                DropdownMenu(
                                    expanded = showAiModels,
                                    onDismissRequest = { showAiModels = false },
                                    containerColor = Color(0xFF333333),
                                    shape = RoundedCornerShape(30.dp),
                                    modifier = Modifier.padding(10.dp, 0.dp)
                                ) {
                                    var showedDiv = false
                                    availableModels.forEach {
                                        if (it.vision && !showedDiv) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 16.dp),
                                                thickness = 1.dp,
                                                color = White.copy(alpha = 0.3f)
                                            )
                                            showedDiv = true
                                        }
                                        val containerColorModel by animateColorAsState(
                                            targetValue = if (selectedModel == it) Color(
                                                0xFF555555
                                            ) else Color(0xFF333333),
                                            animationSpec = tween(durationMillis = 300),
                                            label = "containerColor"
                                        )
                                        PloppingButton(
                                            onClick = {
                                                selectedModel = it
                                                if (!selectedModel.vision) selectedImageUri = null
                                            },
                                            onFinishedClick = { showAiModels = false },
                                            colors = buttonColors(containerColor = containerColorModel),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val sizeRegex = Regex("""-\d+b""")
                                            val sizeRegex1 = Regex("""\d+b""")
                                            val name = it.name
                                                .replace(sizeRegex, "")
                                                .replace("-", " ")
                                                .substringBeforeLast(":")
                                            val sizeString: String? =
                                                if (currentMode == "Nvidia") sizeRegex1.find(it.name)?.value else it.name.substringAfter(
                                                    ":"
                                                )
                                            val size =
                                                if (sizeString != null && sizeString != "null") {
                                                    " (${sizeString.replace("-", " ")})"
                                                } else {
                                                    ""
                                                }
                                            Text(
                                                "$name$size",
                                                textAlign = TextAlign.Left,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                                color = White,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .background(
                                        if (isUser) MaterialTheme.colorScheme.primary else Color(
                                            0xFF444444
                                        ),
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
                                color = White,
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .background(Color(0xFF444444), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            val uploadButtonWeight by animateDpAsState(
                targetValue = if (selectedModel.vision) 48.dp else 0.dp,
                animationSpec = tween(durationMillis = 300),
                label = "textFieldWeight"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PloppingButton(
                    onClick = {
                        imagePickerLauncher.launch("image/*")
                    },
                    modifier = Modifier
                        .size(uploadButtonWeight)
                        .background(
                            if (selectedImageUri != null) Color(0xFF555555) else Color(0xFF333333),
                            RoundedCornerShape(50)
                        )
                ) {
                    Text(
                        if (selectedImageUri != null) "✓" else "📷",
                        color = White,
                        fontSize = 16.sp
                    )
                }

                TextField(
                    value = currentMsg,
                    onValueChange = { currentMsg = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("Nachricht eingeben...", color = Color(0xFF888888)) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        cursorColor = White,
                        focusedContainerColor = Color(0xFF2A2A2A),
                        unfocusedContainerColor = Color(0xFF2A2A2A),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            sendmsg()
                        }
                    )
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF333333), RoundedCornerShape(50))
                        .combinedClickable(
                            onClick = {
                                sendmsg()
                            },
                            onLongClick = {
                                history.clear()
                                saveHistory(context, scope, history)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = White, fontSize = 16.sp)
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