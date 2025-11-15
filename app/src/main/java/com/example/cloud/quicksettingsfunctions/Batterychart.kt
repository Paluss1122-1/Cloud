package com.example.cloud.quicksettingsfunctions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.edit
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

@Serializable
data class BatterySample(
    val timestamp: Long,
    val level: Int,
    val temperature: Float,
    val voltage: Int // in Millivolt
)

@SuppressLint("StaticFieldLeak")
object BatteryDataRepository {
    private lateinit var context: Context
    private val json = Json { ignoreUnknownKeys = true }

    private val _samples = MutableStateFlow<List<BatterySample>>(emptyList())
    val samples = _samples.asStateFlow()

    fun init(context: Context) {
        this.context = context.applicationContext
        _samples.value = loadSamples()
    }

    fun addSample(sample: BatterySample) {
        val currentSamples = _samples.value

        // Prüfen, ob Temperaturänderung >= 1°C seit letztem Sample
        val lastTemp = currentSamples.lastOrNull()?.temperature
        if (lastTemp != null && (sample.temperature - lastTemp).absoluteValue < 1f) {
            return // Änderung zu klein, nicht speichern
        }

        val updatedList = (currentSamples + sample).takeLast(100)
        _samples.value = updatedList
        saveSamples(updatedList)
    }

    private fun loadSamples(): List<BatterySample> {
        return try {
            val prefs = context.getSharedPreferences("battery_data", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("samples", null)
            if (jsonStr == null) {
                emptyList()
            } else {
                val list = json.decodeFromString<List<BatterySample>>(jsonStr)
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSamples(samples: List<BatterySample>) {
        try {
            val prefs = context.getSharedPreferences("battery_data", Context.MODE_PRIVATE)
            val jsonStr = json.encodeToString(samples)
            prefs.edit { putString("samples", jsonStr) }
        } catch (_: Exception) {
        }
    }
}

class BatterySamplingWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val intent = applicationContext.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return Result.failure()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val temp = if (tempRaw != -1) tempRaw / 10f else return Result.failure()
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        if (level >= 0 && voltage > 0) {
            BatteryDataRepository.addSample(
                BatterySample(System.currentTimeMillis(), level, temp, voltage)
            )
        }

        // Hintergrund-Resampling nach 10 Minuten
        scheduleNextBatterySampling(applicationContext, 10)
        return Result.success()
    }
}

fun scheduleNextBatterySampling(context: Context, delayMinutes: Long = 10) {
    val workRequest = OneTimeWorkRequestBuilder<BatterySamplingWorker>()
        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "BatterySampling",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}

@Composable
fun BatteryChartScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        BatteryDataRepository.init(context)
        scheduleNextBatterySampling(context, 10) // Hintergrund
    }

    // 🔁 Foreground-Messung
    LaunchedEffect(Unit) {
        while (true) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val tempRaw = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val temp = if (tempRaw != -1) tempRaw / 10f else null
                val voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                if (level >= 0 && temp != null && voltage > 0) {
                    BatteryDataRepository.addSample(
                        BatterySample(System.currentTimeMillis(), level, temp, voltage)
                    )
                }
            }
            kotlinx.coroutines.delay(10_000L) // alle 10 Sekunden
        }
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onDismiss() }
                .padding(10.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .sizeIn(maxWidth = 800.dp, maxHeight = 600.dp)
                    .background(Color(0xFF2A2A2A))
                    .padding(20.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    BatteryChartScreenContent(onClose = onDismiss)
                }
            }
        }
    }
}

@Composable
fun BatteryChartScreenContent(onClose: () -> Unit) {
    var selectedDay by remember { mutableStateOf<String?>(null) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    var showDayDropdown by remember { mutableStateOf(false) }
    var showHourDropdown by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        BatteryLineChart(
            modifier = Modifier.fillMaxSize(),
            selectedDay = selectedDay,
            selectedHour = selectedHour
        )

        // Filter-Steuerung oben links
        Row(
            Modifier
                .padding(16.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tag-Auswahl
            Box {
                Box(
                    Modifier
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                        .clickable { showDayDropdown = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = selectedDay ?: "Alle Tage",
                        color = Color.White
                    )
                }

                if (showDayDropdown) {
                    DayDropdown(
                        onDaySelected = { day ->
                            selectedDay = day
                            selectedHour = null
                            showDayDropdown = false
                        },
                        onDismiss = { showDayDropdown = false }
                    )
                }
            }

            // Stunden-Auswahl (nur wenn Tag ausgewählt)
            if (selectedDay != null) {
                Box {
                    Box(
                        Modifier
                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                            .clickable { showHourDropdown = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = selectedHour?.let { "${it}:00 Uhr" } ?: "Alle Stunden",
                            color = Color.White
                        )
                    }

                    if (showHourDropdown) {
                        HourDropdown(
                            selectedDay = selectedDay,
                            onHourSelected = { hour ->
                                selectedHour = hour
                                showHourDropdown = false
                            },
                            onDismiss = { showHourDropdown = false }
                        )
                    }
                }
            }
        }

        IconButton(onClick = onClose, Modifier
            .padding(16.dp)
            .align(Alignment.TopEnd)) {
            Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}

@Composable
fun BatteryLineChart(
    modifier: Modifier = Modifier,
    selectedDay: String? = null,
    selectedHour: Int? = null
) {
    val allSamples by BatteryDataRepository.samples.collectAsState()
    var selectedSample by remember { mutableStateOf<BatterySample?>(null) }

    // 🔍 Neu: Zoom und horizontales Scrollen
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    // Filtern der Samples basierend auf Auswahl
    val samples = remember(allSamples, selectedDay, selectedHour) {
        var filtered = allSamples

        if (selectedDay != null) {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            filtered = filtered.filter { sample ->
                sdf.format(Date(sample.timestamp)) == selectedDay
            }
        }

        if (selectedHour != null) {
            filtered = filtered.filter { sample ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = sample.timestamp
                cal.get(Calendar.HOUR_OF_DAY) == selectedHour
            }
        }

        filtered
    }

    if (samples.isEmpty()) {
        Text("Keine Daten", color = Color.White)
        return
    }

    val temperatures = samples.map { it.temperature }
    val maxY = (temperatures.maxOrNull() ?: 30f) + 5f
    val minY = (temperatures.minOrNull() ?: 20f) - 5f
    val padding = 50f

    Box(modifier = modifier) {
        // ⬇️ Ersetze den Bereich innerhalb des Canvas-Blocks in BatteryLineChart durch Folgendes:

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x

                        val chartWidth = (size.width - padding * 2) * scale
                        val visibleWidth = size.width - padding * 2

                        val maxOffsetLeft = 0f
                        val maxOffsetRight = -(chartWidth - visibleWidth)
                        offsetX = offsetX.coerceIn(maxOffsetRight, maxOffsetLeft)
                    }
                }
                .pointerInput(samples) {
                    detectTapGestures { tapOffset ->
                        val chartWidth = (size.width - padding * 2) * scale
                        val chartHeight = size.height - padding * 2
                        val xOffset = offsetX
                        val pointRadius = 25f

                        val found = samples.find { sample ->
                            val x = padding + ((sample.timestamp - samples.first().timestamp).toFloat() /
                                    (samples.last().timestamp - samples.first().timestamp)) * chartWidth + xOffset
                            val y = size.height - padding - chartHeight *
                                    (sample.temperature - minY) / (maxY - minY)
                            (tapOffset.x - x).absoluteValue < pointRadius &&
                                    (tapOffset.y - y).absoluteValue < pointRadius
                        }

                        selectedSample = found
                    }
                }
        ) {
            val chartWidth = (size.width - padding * 2) * scale
            val chartHeight = size.height - padding * 2
            val xOffset = offsetX

            // Achsen
            drawLine(Color.White, Offset(padding, padding), Offset(padding, size.height - padding), strokeWidth = 3f)
            drawLine(Color.White, Offset(padding, size.height - padding), Offset(size.width - padding, size.height - padding), strokeWidth = 3f)

            val stepCount = 5
            val range = maxY - minY
            val stepValue = if (range == 0f) 1f else range / stepCount
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            // Y-Achse + Rasterlinien
            val gridColor = Color(0x22FFFFFF)
            for (i in 0..stepCount) {
                val yValue = minY + i * stepValue
                val yPos = size.height - padding - (chartHeight * (yValue - minY) / range.coerceAtLeast(1f))

                // Text
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f°C".format(yValue),
                    padding - 10,
                    yPos + 10,
                    textPaint
                )

                // horizontale Rasterlinie
                drawLine(
                    color = gridColor,
                    start = Offset(padding, yPos),
                    end = Offset(size.width - padding, yPos),
                    strokeWidth = 1f
                )
            }

            // X-Achse Zeitstempel + vertikale Rasterlinien
            if (samples.size > 1) {
                val earliest = samples.first().timestamp
                val latest = samples.last().timestamp
                val timeSpan = latest - earliest
                val stepXCount = 4
                for (i in 0..stepXCount) {
                    val timestamp = earliest + i * timeSpan / stepXCount
                    val xPos = padding + chartWidth * (timestamp - earliest).toFloat() / timeSpan + xOffset
                    if (xPos in padding..(size.width - padding)) {
                        val label = android.text.format.DateFormat.format("HH:mm:ss", timestamp).toString()
                        drawContext.canvas.nativeCanvas.drawText(label, xPos, size.height - padding + 30, textPaint)

                        // vertikale Rasterlinie
                        drawLine(
                            color = gridColor,
                            start = Offset(xPos, padding),
                            end = Offset(xPos, size.height - padding),
                            strokeWidth = 1f
                        )
                    }
                }
            }

            // Temperaturkurve
            clipRect(left = padding, top = padding, right = size.width - padding, bottom = size.height - padding) {
                if (samples.size > 1) {
                    val path = android.graphics.Path()
                    samples.forEachIndexed { index, sample ->
                        val x = padding + ((sample.timestamp - samples.first().timestamp).toFloat() /
                                (samples.last().timestamp - samples.first().timestamp)) * chartWidth + xOffset
                        val y = size.height - padding - chartHeight * (sample.temperature - minY) / (maxY - minY)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(Color.Cyan, radius = 8f, center = Offset(x, y))
                    }
                    drawPath(path.asComposePath(), Color.Red, style = Stroke(width = 4f, cap = StrokeCap.Round))
                }
            }
        }

        // Popup mit Messwerten anzeigen, falls Punkt angetippt
        selectedSample?.let { sample ->
            BatteryInfoPopup(sample) {
                selectedSample = null
            }
        }
    }
}

@Composable
fun BatteryInfoPopup(sample: BatterySample, onDismiss: () -> Unit) {
    val formattedTime = remember(sample.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(sample.timestamp))
    }

    Popup(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Text("Zeitpunkt: $formattedTime", color = Color.White)
                Text("Batteriestand: ${sample.level}%", color = Color.White)
                Text("Temperatur: ${sample.temperature} °C", color = Color.White)
                Text("Spannung: ${sample.voltage} mV", color = Color.White)
            }
        }
    }
}

@Composable
fun DayDropdown(
    onDaySelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val samples by BatteryDataRepository.samples.collectAsState()
    val availableDays = remember(samples) {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        samples.map { sdf.format(Date(it.timestamp)) }.distinct().sortedDescending()
    }

    Popup(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .widthIn(min = 150.dp, max = 250.dp)
                    .heightIn(max = 400.dp)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // "Alle Tage" Option
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDaySelected(null)
                            onDismiss()
                        }
                        .padding(12.dp)
                ) {
                    Text("Alle Tage", color = Color.White)
                }

                // Verfügbare Tage
                availableDays.forEach { day ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDaySelected(day)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        Text(day, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun HourDropdown(
    selectedDay: String?,
    onHourSelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val samples by BatteryDataRepository.samples.collectAsState()
    val availableHours = remember(samples, selectedDay) {
        if (selectedDay == null) return@remember emptyList()

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val filteredSamples = samples.filter { sample ->
            sdf.format(Date(sample.timestamp)) == selectedDay
        }

        val cal = Calendar.getInstance()
        filteredSamples.map { sample ->
            cal.timeInMillis = sample.timestamp
            cal.get(Calendar.HOUR_OF_DAY)
        }.distinct().sorted()
    }

    Popup(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .offset(x = 160.dp)
                    .widthIn(min = 150.dp, max = 200.dp)
                    .heightIn(max = 400.dp)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // "Alle Stunden" Option
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onHourSelected(null)
                            onDismiss()
                        }
                        .padding(12.dp)
                ) {
                    Text("Alle Stunden", color = Color.White)
                }

                // Verfügbare Stunden
                availableHours.forEach { hour ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onHourSelected(hour)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        Text("${hour}:00 Uhr", color = Color.White)
                    }
                }
            }
        }
    }
}