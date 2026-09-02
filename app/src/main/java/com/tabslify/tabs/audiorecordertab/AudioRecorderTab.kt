package com.tabslify.tabs.audiorecordertab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.toast
import com.tabslify.core.ui.APP_BLUE
import com.tabslify.core.ui.AlertDialogTabslify
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
private fun MicPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialogTabslify(
        onDismiss = onDismiss,
        title = stringResource(R.string.mikrofonzugriff_benotigt),
        text = stringResource(R.string.dieser_tab_benotigt_zugriff_auf) +
                stringResource(R.string.bitte_erlaube_den_zugriff_in) +
                stringResource(R.string.berechtigungen_mikrofon_option_1_oder),
        confirmText = stringResource(R.string.zu_den_einstellungen),
        onConfirm = onConfirm
    )
}

@Composable
fun AudioRecorderTab(
    modifier: Modifier = Modifier,
    vm: AudioRecorderTabViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var restartKey by remember { mutableIntStateOf(0) }
    var directedToSettings by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(vm.hasPermission) }  // NEU
    val micPermissionNeededMsg = stringResource(R.string.dieser_tab_benotigt_die_mikrofon)
    val micPermissionNotGrantedMsg = stringResource(R.string.dieser_tab_benotigt_die_nicht)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (granted) {
            hasPermission = true
            vm.hasPermission = true
            restartKey++
        }
    }

    LaunchedEffect(Unit) {
        if (!vm.hasPermission) {
            val canAskDirectly = Config.requestPermission("mic", launcher)
            if (!canAskDirectly || ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showPermissionDialog = true
            }
        }
    }

    if (showPermissionDialog) {
        val string = stringResource(R.string.berechtigungen_mikrofon_option_1_oder)
        MicPermissionDialog(
            onConfirm = {
                showPermissionDialog = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
                toast(context, string)
                directedToSettings = true
            },
            onDismiss = {
                showPermissionDialog = false
                toast(context, micPermissionNeededMsg)
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && directedToSettings) {
                showPermissionDialog = false
                directedToSettings = false
                val permissionGranted = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (permissionGranted) {
                    hasPermission = true
                    vm.hasPermission = true
                    restartKey++
                } else {
                    toast(
                        context,
                        micPermissionNotGrantedMsg
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasPermission) {
        key(restartKey) {
            AudioRecorderTabContent(modifier = modifier, vm = vm)
        }
    } else {
        Box(Modifier
            .fillMaxSize()
            .padding(40.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.dieser_tab_benotigt_die_nicht_2),
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 30.sp,
                lineHeight = 40.sp
            )
        }
    }
}

@Composable
private fun AudioRecorderTabContent(
    modifier: Modifier,
    vm: AudioRecorderTabViewModel
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            vm.handleButtonClick(scope, true, result.resultCode, data)
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshFiles()
    }

    LaunchedEffect(vm.isPlaying) {
        vm.updatePos()
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.onStop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var isMediaRecorderMode by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF333333),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { isMediaRecorderMode = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isMediaRecorderMode) APP_BLUE else Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.mikrofon))
            }
            Button(
                onClick = { isMediaRecorderMode = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMediaRecorderMode) APP_BLUE else Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.onscreen))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (!vm.isRecording && isMediaRecorderMode) {
                    val manager = context.getSystemService(MediaProjectionManager::class.java)
                    val captureIntent = manager?.createScreenCaptureIntent()
                    if (captureIntent != null) {
                        projectionLauncher.launch(captureIntent)
                    }
                } else {
                    vm.handleButtonClick(scope, isMediaRecorderMode)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (vm.isRecording) Color.Red else Color(0xFF1DB954),
                contentColor = Color.White
            )
        ) {
            Text(
                text = if (vm.isRecording) stringResource(R.string.aufnahme_beenden) else stringResource(R.string.aufnahme_starten),
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
            text = stringResource(R.string.aufnahmen, vm.audioFiles.size),
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
                    onClick = { vm.onSelect(file) },
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
            onShare = { range -> vm.onFinalShare(range) },
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
        Text(stringResource(R.string.player, file.name), color = Color.White)

        Slider(
            value = currentPosition,
            onValueChange = onSeek,
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = APP_BLUE,
                activeTrackColor = APP_BLUE
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
            Button(onClick = onPlayPause, colors = ButtonDefaults.buttonColors(APP_BLUE)) {
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
                    LocalLocale.current.platformLocale
                ).format(Date(file.lastModified())),
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        Row {
            Text(
                "📤", fontSize = 20.sp, modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable { onShareDirect() }
            )
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
        title = { Text(stringResource(R.string.audio_teilen)) },
        text = {
            Column {
                Text(stringResource(R.string.bereich_auswahlen), modifier = Modifier.padding(bottom = 16.dp))
                RangeSlider(
                    value = currentRange,
                    onValueChange = { currentRange = it },
                    valueRange = 0f..maxDuration,
                    enabled = !isProcessing,
                    colors = SliderDefaults.colors(
                        thumbColor = APP_BLUE,
                        activeTrackColor = APP_BLUE
                    )
                )
                Text(
                    stringResource(R.string.dauer, vm.formatTime((currentRange.endInclusive - currentRange.start).toInt())),
                    color = APP_BLUE,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (isProcessing) Text(
                    stringResource(R.string.verarbeite),
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
                Text(stringResource(R.string.abbrechen))
            }
        }
    )
}