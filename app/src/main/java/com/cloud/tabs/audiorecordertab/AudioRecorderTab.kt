@file:Suppress("AssignedValueIsNeverRead")

package com.cloud.tabs.audiorecordertab

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun AudioRecorderContent(
    modifier: Modifier = Modifier,
    vm: AudioRecorderTabViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (!vm.hasPermission) {
        Toast.makeText(context, "Keine Mikrofon Berechtigung", Toast.LENGTH_SHORT).show()
        return
    }

    LaunchedEffect(Unit) {
        vm.refreshFiles()
    }

    LaunchedEffect(vm.isPlaying) {
        vm.updatePos()
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.mediaPlayer?.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                vm.handleButtonClick(scope)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(
                text = if (vm.isRecording) "⏹️ Aufnahme Beenden" else "⏺️ Aufnahme Starten",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        vm.selectedFile?.let { file ->
            PlayerSection(
                file = file,
                isPlaying = vm.isPlaying,
                currentPosition = vm.currentPosition,
                duration = vm.duration,
                onPlayPause = { vm.onPlayPause(file) },
                onStop = { vm.onStop() },
                onSeek = { pos -> vm.onSeek(pos) },
                onClose = { vm.onClose() },
                onShare = { vm.onShare(file) },
                vm = vm
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "📁 Aufnahmen (${vm.audioFiles.size})",
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .align(Alignment.Start)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(vm.audioFiles) { file ->
                FileItem(
                    file = file,
                    isSelected = vm.selectedFile == file,
                    onClick = {
                        vm.onSelect(file)
                    },
                    onShareDirect = { vm.onShareDirect(file) }
                )
            }
        }
    }

    if (vm.showShareDialog && vm.shareFile != null) {
        ShareDialog(
            initialRange = vm.shareRange,
            maxDuration = if (vm.shareRange.endInclusive > 0) vm.shareRange.endInclusive else 1f,
            isProcessing = vm.isProcessing,
            onDismiss = { vm.onDismiss() },
            onShare = { range -> vm.onFinalShare(range, scope)},
            vm = vm
        )
    }
}


@Composable
fun PlayerSection(
    file: File,
    isPlaying: Boolean,
    currentPosition: Float,
    duration: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    vm: AudioRecorderTabViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF333333))
            .padding(16.dp)
    ) {
        Text("▶️ Player: ${file.name}", color = Color.White)

        Slider(
            value = currentPosition,
            onValueChange = onSeek,
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4CAF50),
                activeTrackColor = Color(0xFF4CAF50)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(vm.formatTime(currentPosition.toInt()), color = Color.Gray, fontSize = 12.sp)
            Text(vm.formatTime(duration.toInt()), color = Color.Gray, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = onPlayPause, colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) {
                Text(if (isPlaying) "⏸️" else "▶️")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(Color.Red)) {
                Text("⏹️")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onShare, colors = ButtonDefaults.buttonColors(Color(0xFF25D366))) {
                Text("📤")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(Color.Gray)) {
                Text("✖️")
            }
        }
    }
}

@Composable
fun FileItem(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onShareDirect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (isSelected) Color(0xFF444444) else Color(0xFF333333))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = Color.White)
            Text(
                SimpleDateFormat(
                    "dd.MM.yyyy HH:mm",
                    Locale.getDefault()
                ).format(Date(file.lastModified())),
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        Row {
            Text(
                "📤", fontSize = 20.sp, modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable { onShareDirect() })
        }
    }
}

@Composable
fun ShareDialog(
    initialRange: ClosedFloatingPointRange<Float>,
    maxDuration: Float,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onShare: (ClosedFloatingPointRange<Float>) -> Unit,
    vm: AudioRecorderTabViewModel
) {
    var currentRange by remember { mutableStateOf(initialRange) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio teilen") },
        text = {
            Column {
                Text("Bereich auswählen:", modifier = Modifier.padding(bottom = 16.dp))
                RangeSlider(
                    value = currentRange,
                    onValueChange = { currentRange = it },
                    valueRange = 0f..maxDuration,
                    enabled = !isProcessing,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4CAF50),
                        activeTrackColor = Color(0xFF4CAF50)
                    )
                )
                Text(
                    "Dauer: ${vm.formatTime((currentRange.endInclusive - currentRange.start).toInt())}",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (isProcessing) Text(
                    "⏳ Verarbeite...",
                    color = Color(0xFFFFA500),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onShare(currentRange) },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(Color(0xFF25D366))
            ) { Text("WhatsApp") }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(Color.Gray)
            ) {
                Text("Abbrechen")
            }
        }
    )
}