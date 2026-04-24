package com.cloud.tabs.aitab

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloud.core.ui.NeonBox
import com.cloud.core.ui.PloppingButton

@Composable
fun AITabContent(vm: AITabViewModel = viewModel()) {
    val listState = rememberLazyListState()
    val alpha = remember { Animatable(0f) }

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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
            .alpha(alpha.value)
    )
    {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Nvidia", "Server").forEachIndexed { index, mode ->
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
                                        if (model.vision && !showedDiv) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 16.dp),
                                                thickness = 1.dp,
                                                color = White.copy(alpha = 0.3f)
                                            )
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
                                                if (vm.currentMode == "Nvidia") sizeRegex1.find(model.name)?.value
                                                else model.name.substringAfter(":")
                                            val size =
                                                if (sizeString != null && sizeString != "null") {
                                                    " (${sizeString.replace("-", " ")})"
                                                } else ""
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
                items(vm.history) { msg ->
                    val isUser = msg.own

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        if (isUser) {
                            // === Eigene Nachricht – klassisch & clean ===
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(
                                        start = 40.dp,
                                        end = 8.dp
                                    )
                            ) {
                                Text(
                                    text = msg.text,
                                    color = White,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary,   // oder Color(0xFF0066FF) etc.
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            NeonBox(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(
                                        start = 8.dp,
                                        end = 40.dp
                                    ),
                                cornerRadius = 14.dp,
                                backgroundAlpha = 0.91f,
                                borderWidth = 2.8.dp,
                                neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)), // schönes Cyan → Blau
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = msg.text,
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

                if (vm.isLoading) {
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
                            if (vm.selectedImageUri != null) Color(0xFF555555) else Color(0xFF333333),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        if (vm.selectedImageUri != null) Icons.Default.CameraAlt else Icons.Default.Check,
                        "Bild Anhängen",
                        tint = Color.Black
                    )
                }

                TextField(
                    value = vm.currentMsg,
                    onValueChange = { vm.currentMsg = it },
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
                    Text("+", color = White, fontSize = 16.sp)
                }
            }
        }
    }

    LaunchedEffect(vm.history.size, vm.isLoading) {
        if (vm.history.isNotEmpty()) listState.animateScrollToItem(vm.history.lastIndex + if (vm.isLoading) 1 else 0)
    }
}