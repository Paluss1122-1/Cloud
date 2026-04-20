package com.cloud.tabs

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.cloud.core.objects.Config.cms
import com.cloud.core.ui.PloppingButton
import com.cloud.core.ui.showBatteryInfo
import com.cloud.quicksettingsfunctions.BatteryChartScreen
import com.cloud.quicksettingsfunctions.showNetworkInfo
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

@Suppress("AssignedValueIsNeverRead")
@Composable
fun QuickSettingsTabContent() {
    val context = LocalContext.current
    var showBatteryChart by remember { mutableStateOf(false) }

    val sharedPrefs = context.getSharedPreferences("quick_settings_prefs", Context.MODE_PRIVATE)
    var savedNumber by remember {
        mutableStateOf(
            sharedPrefs.getString("saved_number", "21") ?: "21"
        )
    }
    var savedNumber1 by remember {
        mutableStateOf(
            sharedPrefs.getString("saved_number_start", "7") ?: "7"
        )
    }
    var showNumberDialog by remember { mutableStateOf(false) }
    var showNumberDialogSave by remember { mutableStateOf(false) }

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(100)
        alpha.animateTo(
            1f, animationSpec = tween(
                durationMillis = 150,
                easing = FastOutSlowInEasing
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(7.dp)
            .alpha(alpha.value),
        verticalArrangement = Arrangement.Center
    ) {
        QuickSettingRow(
            listOf(
                "Netzwerk\nInfos" to { showNetworkInfo(context) },
                "Display\nInfos" to { showDisplayInfo(context) },
                "Uptime" to { showNumberDialogSave = true }
            ))
        Spacer(Modifier.height(8.dp))
        QuickSettingRow(
            listOf(
                "🔋\nBatterie\nInfo" to { showBatteryInfo(context) },
                "Batterie\nDiagramm" to { showBatteryChart = true },
                "📱\nGeräte\nInfo" to { showDeviceInfo(context) }
            )
        )
        Spacer(Modifier.height(8.dp))
        QuickSettingRow(
            listOf(
                "Downtime" to { showNumberDialog = true }
            )
        )

    }
    if (showBatteryChart) {
        BatteryChartScreen(
            onDismiss = { showBatteryChart = false }
        )
    }
    if (showNumberDialog) {
        NumberInputDialog(
            currentNumber = savedNumber,
            onDismiss = { showNumberDialog = false },
            onSave = { number ->
                savedNumber = number
                saveNumber(context, number)
                showNumberDialog = false
            }
        )
    }
    if (showNumberDialogSave) {
        NumberInputDialog(
            currentNumber = savedNumber1,
            onDismiss = { showNumberDialogSave = false },
            onSave = { number ->
                savedNumber1 = number
                saveNumber1(context, number)
                showNumberDialogSave = false
            }
        )
    }
}

private fun saveNumber(context: Context, number: String) {
    val sharedPrefs = context.getSharedPreferences("quick_settings_prefs", Context.MODE_PRIVATE)
    sharedPrefs.edit(commit = true) { putString("saved_number", number) }
}

private fun saveNumber1(context: Context, number: String) {
    val sharedPrefs = context.getSharedPreferences("quick_settings_prefs", Context.MODE_PRIVATE)
    sharedPrefs.edit(commit = true) { putString("saved_number_start", number) }
}

@Composable
fun QuickSettingRow(buttons: List<Pair<String, () -> Unit>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        buttons.forEach { (label, action) ->
            PloppingButton(
                onClick = action,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
            ) { Text(text = label, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }
    }
}

@SuppressLint("NewApi")
fun showDisplayInfo(context: Context) {
    val display: Display = context.display

    val realWidth: Int
    val realHeight: Int
    val xdpi: Float
    val ydpi: Float
    val densityDpi: Int

    val usableWidth: Int
    val usableHeight: Int

    val displayMetrics = context.resources.displayMetrics
    realWidth = displayMetrics.widthPixels
    realHeight = displayMetrics.heightPixels
    xdpi = displayMetrics.xdpi
    ydpi = displayMetrics.ydpi
    densityDpi = displayMetrics.densityDpi

    val windowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
    val bounds = windowMetrics.bounds
    usableWidth = bounds.width()
    usableHeight = bounds.height()

    val info = StringBuilder()

    info.append("📏 Phys. Auflösung: $realWidth × $realHeight px\n")

    if (usableWidth != realWidth || usableHeight != realHeight) {
        info.append("📦 Nutzbare Fläche: $usableWidth × $usableHeight px\n")
    }

    val densityStr = when {
        densityDpi <= 120 -> "ldpi (120 dpi)"
        densityDpi <= 160 -> "mdpi (160 dpi)"
        densityDpi <= 240 -> "hdpi (240 dpi)"
        densityDpi <= 320 -> "xhdpi (320 dpi)"
        densityDpi <= 480 -> "xxhdpi (480 dpi)"
        else -> "xxxhdpi (≥ $densityDpi dpi)"
    }
    info.append("🔍 Dichte: $densityDpi dpi ($densityStr)\n")

    try {
        val widthInches = realWidth / xdpi.toDouble()
        val heightInches = realHeight / ydpi.toDouble()
        val diagonalInches =
            sqrt(widthInches * widthInches + heightInches * heightInches)
        info.append("📐 Bildschirmgröße: ${String.format(Locale.US, "%.1f", diagonalInches)} Zoll\n")
    } catch (_: Exception) {
        info.append("📐 Bildschirmgröße: N/A\n")
    }

    try {
        val refreshRate = display.refreshRate
        info.append("🔄 Refresh Rate: ${String.format(Locale.US, "%.1f", refreshRate)} Hz\n")
    } catch (_: Exception) {
        info.append("🔄 Refresh Rate: N/A\n")
    }

    val rotation = display.rotation
    val orientationStr = when (rotation) {
        Surface.ROTATION_0 -> "Hochformat (0°)"
        Surface.ROTATION_90 -> "Querformat (90°)"
        Surface.ROTATION_180 -> "Hochformat (180°, umgekehrt)"
        Surface.ROTATION_270 -> "Querformat (270°, umgekehrt)"
        else -> "Unbekannt"
    }
    info.append("🧭 Ausrichtung: $orientationStr\n")

    val channelId = "display_info_channel"
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_menu_gallery)
        .setContentTitle("🖥️ Display-Info")
        .setContentText("Auflösung, Dichte, Größe, Refresh Rate")
        .setStyle(NotificationCompat.BigTextStyle().bigText(info.toString()))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(cms(), builder.build())
    }
}

@SuppressLint("HardwareIds")
fun showDeviceInfo(context: Context) {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    val systemInfo = """
        ▶ System:
        Gerätename: ${
        Settings.Global.getString(
            context.contentResolver,
            Settings.Global.DEVICE_NAME
        )
    }
        Modell: ${Build.MODEL}
        Hersteller: ${Build.MANUFACTURER}
        Marke: ${Build.BRAND}
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Sicherheitspatch: ${Build.VERSION.SECURITY_PATCH}
        Build: ${Build.DISPLAY}
        Build-Typ: ${Build.TYPE}
        Build-Zeit: ${Date(Build.TIME)}
    """.trimIndent()

    val hardwareInfo = """
        ▶ Hardware:
        CPU-Architektur: ${Build.SUPPORTED_ABIS.joinToString()}
        Board: ${Build.BOARD}
        Bootloader: ${Build.BOOTLOADER}
        Fingerprint: ${Build.FINGERPRINT}
        Display: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} @ ${context.resources.displayMetrics.densityDpi}dpi
        RAM (verfügbar/gesamt): ${(memInfo.availMem / (1024 * 1024))}MB / ${(memInfo.totalMem / (1024 * 1024))}MB
    """.trimIndent()

    val appInfo = """
        ▶ App:
        App-Version: ${packageInfo.versionName} (${packageInfo.longVersionCode})
        Installiert am: ${Date(packageInfo.firstInstallTime)}
        Zuletzt aktualisiert: ${Date(packageInfo.lastUpdateTime)}
    """.trimIndent()

    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val network = connectivityManager.activeNetwork
    val linkProperties = connectivityManager.getLinkProperties(network)

    val ipAddress = linkProperties?.linkAddresses
        ?.firstOrNull { it.address is Inet4Address }
        ?.address
        ?.hostAddress

    val caps = connectivityManager.getNetworkCapabilities(network)

    val ssid = if (
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    ) {
        val wifiInfo = caps.transportInfo as? WifiInfo
        wifiInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")
    } else {
        null
    }

    val macAddress = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    val networkInfo = """
        ▶ Netzwerk:
        WLAN SSID: $ssid
        IP-Adresse: $ipAddress
        MAC-Adresse: $macAddress
    """.trimIndent()

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "device_info_channel"

    val channel = NotificationChannel(
        channelId,
        "Geräte-Infos",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Zeigt System-, Hardware-, App- und Netzwerk-Infos an"
    }
    notificationManager.createNotificationChannel(channel)

    val categories = listOf(
        "📱 System" to systemInfo,
        "⚙️ Hardware" to hardwareInfo,
        "📦 App" to appInfo,
        "🌐 Netzwerk" to networkInfo
    )

    for ((title, content) in categories) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(cms(), notification)
    }
}

@Composable
fun NumberInputDialog(
    currentNumber: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var numberText by remember { mutableStateOf(currentNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A2A),
        title = {
            Text(
                text = "Nummer eingeben",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (currentNumber.isNotEmpty()) {
                    Text(
                        text = "Gespeicherte Nummer: $currentNumber",
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = numberText,
                    onValueChange = { numberText = it },
                    label = { Text("Nummer", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave(numberText) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(numberText) }
            ) {
                Text("Speichern", color = Color(0xFF4CAF50))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = Color.Gray)
            }
        }
    )
}