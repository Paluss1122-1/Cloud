package com.cloud.tabs.aitab

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloud.core.objects.prvt
import com.cloud.core.ui.AlertDialogCloud
import com.cloud.core.ui.BgSurface
import com.cloud.core.ui.NeonBox
import com.cloud.core.ui.PloppingButton
import com.cloud.core.ui.SharedViewModel
import com.cloud.core.ui.TextPrimary
import com.cloud.core.ui.c

@Composable
fun AITabContent(vm: AITabViewModel = viewModel(), svm: SharedViewModel = viewModel()) {
    val listState = rememberLazyListState()
    val alpha = remember { Animatable(0f) }
    val context = LocalContext.current

    val msgBounds = remember { mutableStateMapOf<Int, Float>() }
    val view = LocalView.current
    val contextMenuY1 =
        msgBounds[vm.selectedMsg]?.toInt() ?: msgBounds[vm.lastSelectedMsg]?.toInt() ?: 0
    val overlayAlpha = animateFloatAsState(
        targetValue = if (vm.selectedMsg != null) 1f else 0f,
        animationSpec = tween(400),
        label = "overlay"
    )
    LaunchedEffect(vm.history.size) {
        val sel = vm.selectedMsg
        if (sel != null && sel >= vm.history.size) {
            vm.selectedMsg = null
            svm.fireEvent(false)
        }
    }

    val density = LocalDensity.current
    val screenHeightPx = LocalWindowInfo.current.containerSize.height

    val menuGoesUp = contextMenuY1 > screenHeightPx * 0.6f

    BackHandler(vm.isEditMode) {
        vm.isEditMode = false
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        vm.selectedImageUri = uri
    }

    LaunchedEffect(Unit) {
        vm.selectedModel = vm.availableModels[0]
        vm.loadHistory()
        vm.animateAlpha(alpha)
    }

    if (vm.showLimitReached) {
        AlertDialogCloud(
            onDismiss = { vm.showLimitReached = false },
            onConfirm = { vm.showLimitReached = false },
            title = "Tageslimit erreicht",
            text = "Du hast dein tägliches Limit für AI-Nachrichten erreicht!",
            confirmText = "OK",
            oneButton = true,
        )
    }

    val cs = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Box(
        Modifier
            .fillMaxSize()
            .background(Transparent)
            .imePadding()
            .alpha(alpha.value)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPrivate = prvt()

                    listOfNotNull(
                        "Nvidia",
                        "Gemini",
                        if (isPrivate) "Server" else null
                    ).forEachIndexed { index, mode ->
                        val containerColor by animateColorAsState(
                            targetValue = if (vm.currentMode == mode) Color(0xFF555555) else Color(
                                0xFF333333
                            ),
                            animationSpec = tween(durationMillis = 300),
                            label = "containerColor"
                        )
                        Box {
                            PloppingButton(
                                onClick = {
                                    if (vm.currentMode == mode) vm.showAiModels = true
                                    vm.setMode(mode)
                                },
                                colors = buttonColors(containerColor = containerColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(mode, color = White)
                            }

                            if (index == 0) {
                                DropdownMenu(
                                    expanded = vm.showAiModels,
                                    onDismissRequest = { vm.showAiModels = false },
                                    containerColor = Color(0xFF333333),
                                    shape = RoundedCornerShape(30.dp),
                                    modifier = Modifier.padding(10.dp, 0.dp)
                                ) {
                                    var showedDiv = false
                                    vm.availableModels.forEach { model ->
                                        if (model.vision && !showedDiv && !vm.availableModels[0].vision) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(vertical = 16.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                HorizontalDivider(
                                                    modifier = Modifier.weight(1f),
                                                    thickness = 1.dp,
                                                    color = White.copy(alpha = 0.3f)
                                                )

                                                Text(
                                                    text = "VISION MODELS:",
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )

                                                HorizontalDivider(
                                                    modifier = Modifier.weight(1f),
                                                    thickness = 1.dp,
                                                    color = White.copy(alpha = 0.3f)
                                                )
                                            }
                                            showedDiv = true
                                        }
                                        val containerColorModel by animateColorAsState(
                                            targetValue = if (vm.selectedModel == model) Color(
                                                0xFF555555
                                            ) else Color(0xFF333333),
                                            animationSpec = tween(durationMillis = 300),
                                            label = "containerColor"
                                        )
                                        PloppingButton(
                                            onClick = {
                                                vm.selectModel(model)
                                            },
                                            onFinishedClick = { vm.showAiModels = false },
                                            colors = buttonColors(containerColor = containerColorModel),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val sizeRegex = Regex("""-\d+b""")
                                            val sizeRegex1 = Regex("""\d+b""")
                                            val name = model.name
                                                .replace(sizeRegex, "")
                                                .replace("-", " ")
                                                .substringBeforeLast(":")
                                            val sizeString: String? =
                                                when (vm.currentMode) {
                                                    "Nvidia" -> sizeRegex1.find(
                                                        model.name
                                                    )?.value

                                                    "Gemini" -> null
                                                    else -> model.name.substringAfter(":")
                                                }
                                            val size =
                                                if (sizeString != null && sizeString != "null") {
                                                    " (${sizeString.replace("-", " ")})"
                                                } else ""
                                            val weightInfo =
                                                if (model.weight > 1) " [${model.weight}x]" else ""
                                            Text(
                                                "$name$size$weightInfo",
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

                if (vm.currentMode != "Server" && !prvt()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { vm.getUsageProgress() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = Color(0xFF00FFAA),
                            trackColor = Color(0xFF444444),
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                vm.getUsageResetText(),
                                color = White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                            Text(
                                "${vm.todayUsage}/$DAILY_LIMIT",
                                color = White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        vm.history.size,
                        key = { index -> "${vm.history[index].ts}_${vm.history[index].own}" }) { index ->
                        val msg = vm.history[index]
                        val isUser = msg.own

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(1f)
                                .onGloballyPositioned { coords ->
                                    val rootBounds = coords.boundsInRoot()
                                    val windowInsets =
                                        WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets)
                                    val statusBarHeight =
                                        windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                                    msgBounds[index] = rootBounds.bottom - statusBarHeight
                                },
                            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            if (isUser) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .padding(
                                            start = 40.dp,
                                            end = 8.dp
                                        )
                                ) {
                                    if (vm.isEditMode && index == vm.editIndex) {
                                        TextField(
                                            value = vm.currentEditMsg,
                                            onValueChange = { vm.currentEditMsg = it },
                                            textStyle = TextStyle(
                                                color = White,
                                                fontSize = 15.sp,
                                                lineHeight = 21.sp
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            placeholder = {
                                                Text(
                                                    "Nachricht bearbeiten...",
                                                    color = Color(0xFF888888)
                                                )
                                            },
                                            colors = TextFieldDefaults.colors(
                                                focusedTextColor = White,
                                                unfocusedTextColor = White,
                                                cursorColor = White,
                                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.primary,
                                                focusedIndicatorColor = Transparent,
                                                unfocusedIndicatorColor = Transparent
                                            ),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                            keyboardActions = KeyboardActions(onGo = { vm.sendMessage() }),
                                            modifier = Modifier
                                                .clip(
                                                    RoundedCornerShape(
                                                        topEnd = 10.dp,
                                                        bottomEnd = 20.dp,
                                                        bottomStart = 10.dp,
                                                        topStart = 2.dp
                                                    )
                                                )
                                                .zIndex(1000000f)
                                                .combinedClickable(
                                                    onLongClick = {
                                                        vm.selectedMsg = vm.history.indexOf(msg)
                                                        svm.fireEvent(true)
                                                        vm.lastSelectedMsg = vm.selectedMsg
                                                    },
                                                    onClick = {}
                                                )
                                        )
                                    } else {
                                        Text(
                                            text = msg.text,
                                            color = White,
                                            fontSize = 15.sp,
                                            lineHeight = 21.sp,
                                            modifier = Modifier
                                                .clip(
                                                    RoundedCornerShape(
                                                        topEnd = 2.dp,
                                                        bottomEnd = 10.dp,
                                                        bottomStart = 10.dp,
                                                        topStart = 10.dp
                                                    )
                                                )
                                                .background(MaterialTheme.colorScheme.primary)
                                                .zIndex(1000000f)
                                                .combinedClickable(
                                                    onLongClick = {
                                                        vm.selectedMsg = vm.history.indexOf(msg)
                                                        svm.fireEvent(true)
                                                        vm.lastSelectedMsg = vm.selectedMsg
                                                    },
                                                    onClick = {}
                                                )
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            } else {
                                NeonBox(
                                    modifier = Modifier
                                        .combinedClickable(
                                            onLongClick = {
                                                vm.selectedMsg = vm.history.indexOf(msg)
                                                svm.fireEvent(true)
                                                vm.lastSelectedMsg = vm.selectedMsg
                                            },
                                            onClick = {}
                                        )
                                        .widthIn(max = 380.dp)
                                        .padding(
                                            start = 8.dp,
                                            end = 40.dp
                                        ),
                                    cornerRadius = RoundedCornerShape(2.dp, 10.dp, 10.dp, 10.dp),
                                    backgroundAlpha = 0.91f,
                                    borderWidth = 2.8.dp,
                                    neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 14.dp,
                                            vertical = 10.dp
                                        )
                                    ) {
                                        Text(
                                            text = if (vm.isLoading && msg.text.isEmpty()) "..." else msg.text,
                                            color = Black,
                                            fontSize = 15.sp,
                                            lineHeight = 21.sp
                                        )

                                        if (msg.mode != null) {
                                            Text(
                                                text = msg.mode,
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val uploadButtonWeight by animateDpAsState(
                    targetValue = if (vm.selectedModel.vision) 48.dp else 0.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "uploadButtonWeight"
                )

                val uploadButtonAlpha by animateFloatAsState(
                    targetValue = if (vm.selectedModel.vision) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "uploadButtonWeight"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(uploadButtonWeight)
                            .alpha(uploadButtonAlpha)
                            .background(
                                if (vm.selectedImageUri != null) Color(0xFF555555) else Color(
                                    0xFF333333
                                ),
                                RoundedCornerShape(50)
                            )
                    ) {
                        Icon(
                            if (vm.selectedImageUri == null) Icons.Default.CameraAlt else Icons.Default.Check,
                            "Bild Anhängen",
                            tint = Black
                        )
                    }

                    TextField(
                        value = vm.currentMsg,
                        onValueChange = { vm.currentMsg = it; vm.isEditMode = false },
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
                            focusedIndicatorColor = Transparent,
                            unfocusedIndicatorColor = Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.sendMessage() })
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF333333), RoundedCornerShape(50))
                            .combinedClickable(
                                onClick = { vm.sendMessage() },
                                onLongClick = {
                                    vm.clearHistory()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                    }
                }
            }

            // Context Menu
            if (overlayAlpha.value > 0) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .alpha(overlayAlpha.value)
                        .alpha(alpha.value)
                ) {
                    var message: ChatMessage? by remember { mutableStateOf(null) }
                    message = vm.selectedMsg?.let { vm.history.getOrNull(it) }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Black.copy(0.6f))
                            .imePadding()
                            .then(
                                if (vm.selectedMsg != null && overlayAlpha.value > 0.5f) Modifier.clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    vm.selectedMsg = null
                                    svm.fireEvent(false)
                                } else Modifier
                            )
                    )

                    val menuHeightPx = with(density) { 260.dp.roundToPx() }

                    Row(
                        Modifier
                            .offset {
                                IntOffset(
                                    0,
                                    if (menuGoesUp) contextMenuY1 - menuHeightPx else contextMenuY1 - 150
                                )
                            }
                            .align(
                                if (message?.own ?: return) Alignment.TopEnd else Alignment.TopStart
                            )
                            .padding(
                                end = if (message?.own ?: false) 10.dp else 0.dp,
                                start = if (message?.own ?: false) 0.dp else 10.dp
                            )
                            .width(200.dp)
                            .clip(
                                if (menuGoesUp) {
                                    RoundedCornerShape(
                                        topEnd = 10.dp,
                                        bottomEnd = 2.dp,
                                        bottomStart = 10.dp,
                                        topStart = 20.dp
                                    )
                                } else if (message?.own == false) {
                                    RoundedCornerShape(
                                        bottomStart = 10.dp,
                                        topStart = 2.dp,
                                        topEnd = 10.dp,
                                        bottomEnd = 20.dp
                                    )
                                } else {
                                    RoundedCornerShape(
                                        topStart = 10.dp,
                                        topEnd = 2.dp,
                                        bottomEnd = 10.dp,
                                        bottomStart = 20.dp
                                    )
                                }
                            )
                            .background(BgSurface)
                            .height(150.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c())
                                    .clickable {
                                        val text = message?.text ?: return@clickable
                                        val clip = ClipData(
                                            ClipDescription(
                                                "AITab Message",
                                                arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
                                            ),
                                            ClipData.Item(text)
                                        )
                                        cs.setPrimaryClip(clip)
                                        vm.selectedMsg = null
                                        svm.fireEvent(false)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Kopieren",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 22.sp
                                )
                            }

                            if (message?.own ?: false) {
                                Spacer(Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c())
                                        .clickable {
                                            val msgIndex = vm.selectedMsg ?: return@clickable
                                            vm.currentEditMsg = vm.history[msgIndex].text
                                            vm.editIndex = msgIndex
                                            vm.isEditMode = true
                                            vm.selectedMsg = null
                                            svm.fireEvent(false)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        "Bearbeiten",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 22.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(vm.history.size, vm.isLoading) {
            if (vm.history.isNotEmpty()) listState.animateScrollToItem(vm.history.lastIndex + if (vm.isLoading) 1 else 0)
        }
    }
}
