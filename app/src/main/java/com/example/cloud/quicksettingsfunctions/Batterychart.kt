package com.example.cloud.quicksettingsfunctions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

@Serializable
data class BatterySample(
    val timestamp: Long,
    val level: Int,
    val temperature: Float,
    val voltage: Int
)

@SuppressLint("StaticFieldLeak")
object BatteryDataRepository {
    private lateinit var context: Context
    private val json = Json { ignoreUnknownKeys = true }
    private val _samples = MutableStateFlow<List<BatterySample>>(emptyList())
    val samples = _samples.asStateFlow()

    fun init(ctx: Context) {
        context = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { _samples.value = loadSamples() }
    }

    fun addSample(sample: BatterySample) {
        _samples.value.lastOrNull()?.let {
            if ((sample.temperature - it.temperature).absoluteValue < 1f) return
        }
        val updated = _samples.value + sample
        _samples.value = updated
        saveSamples(updated)
    }

    private suspend fun loadSamples(): List<BatterySample> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences("battery_data", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("samples", null)
            if (jsonStr != null) json.decodeFromString<List<BatterySample>>(jsonStr) else emptyList()
        }.getOrElse { emptyList() }
    }

    private fun saveSamples(samples: List<BatterySample>) = CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            context.getSharedPreferences("battery_data", Context.MODE_PRIVATE)
                .edit { putString("samples", json.encodeToString(samples)) }
        }
    }
}

fun readBatterySample(context: Context): BatterySample? {
    val intent =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val temp =
        intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).takeIf { it != -1 }?.div(10f)
            ?: return null
    val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
    return BatterySample(System.currentTimeMillis(), level, temp, voltage)
}

class BatterySamplingWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        readBatterySample(applicationContext)?.let { BatteryDataRepository.addSample(it) }
        return Result.success()
    }
}

fun startBatteryWorker(context: Context) {
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "BatterySampling", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<BatterySamplingWorker>(10, TimeUnit.MINUTES).build()
    )
}

@Composable
fun BatteryChartScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { BatteryDataRepository.init(context); startBatteryWorker(context) }
    LaunchedEffect(Unit) {
        while (true) {
            readBatterySample(context)?.let { BatteryDataRepository.addSample(it) }; kotlinx.coroutines.delay(
                10_000L
            )
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
                BatteryChartScreenContent(onClose = onDismiss)
            }
        }
    }
}

@Composable
fun BatteryChartScreenContent(onClose: () -> Unit) {
    var selectedDay by remember { mutableStateOf(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize()) {
        BatteryLineChart(Modifier.fillMaxSize(), selectedDay, selectedHour)
        BatteryChartFilters(
            selectedDay,
            selectedHour,
            { if (it != null) {
                selectedDay = it
            }; selectedHour = null },
            { selectedHour = it })
        IconButton(
            onClick = onClose,
            Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) { Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White) }
    }
}

@Composable
fun BatteryChartFilters(
    selectedDay: String?,
    selectedHour: Int?,
    onDaySelected: (String?) -> Unit,
    onHourSelected: (Int?) -> Unit
) {
    var showDay by remember { mutableStateOf(false) }
    var showHour by remember { mutableStateOf(false) }
    val samples by BatteryDataRepository.samples.collectAsState()
    val sdf = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            DropdownButton(selectedDay ?: "Alle Tage") { showDay = true }
            if (showDay) SimpleDropdown(
                samples.map { sdf.format(Date(it.timestamp)) }.distinct().sortedDescending(),
                { it },
                "Alle Tage"
            ) { onDaySelected(it); showDay = false }
        }
        if (selectedDay != null) Box {
            DropdownButton(selectedHour?.let { "$it:00 Uhr" } ?: "Alle Stunden") { showHour = true }
            if (showHour) {
                SimpleDropdown(
                    samples.filter { sdf.format(Date(it.timestamp)) == selectedDay }
                        .map {
                            Calendar.getInstance().apply { timeInMillis = it.timestamp }
                                .get(Calendar.HOUR_OF_DAY)
                        }
                        .distinct().sorted(),
                    { "$it:00 Uhr" }, "Alle Stunden"
                ) { onHourSelected(it); showHour = false }
            }
        }
    }
}

@Composable
fun DropdownButton(text: String, onClick: () -> Unit) =
    Box(Modifier
        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
        .clickable { onClick() }
        .padding(12.dp)) { Text(text, color = Color.White) }

@Composable
fun <T> SimpleDropdown(
    items: List<T>,
    labelMapper: (T) -> String = { it.toString() },
    allLabel: String,
    onSelect: (T?) -> Unit
) =
    Popup(onDismissRequest = { onSelect(null) }) {
        Column(
            Modifier
                .widthIn(min = 150.dp, max = 250.dp)
                .heightIn(max = 300.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(Modifier
                .fillMaxWidth()
                .clickable { onSelect(null) }
                .padding(12.dp)) { Text(allLabel, color = Color.White) }
            items.forEach {
                Box(Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(it) }
                    .padding(12.dp)) {
                    Text(
                        labelMapper(it),
                        color = Color.White
                    )
                }
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
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val samples = remember(allSamples, selectedDay, selectedHour) {
        allSamples.filter {
            (selectedDay == null || SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(
                Date(
                    it.timestamp
                )
            ) == selectedDay) &&
                    (selectedHour == null || Calendar.getInstance()
                        .apply { timeInMillis = it.timestamp }
                        .get(Calendar.HOUR_OF_DAY) == selectedHour)
        }
    }

    if (samples.isEmpty()) {
        Text("Keine Daten", color = Color.White); return
    }

    val minY = samples.minOf { it.temperature } - 5f
    val maxY = samples.maxOf { it.temperature } + 5f
    val padding = 50f

    Box(modifier) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(samples) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f); offsetX =
                        (offsetX + pan.x).coerceIn(
                            -((size.width - padding * 2) * scale - (size.width - padding * 2)),
                            0f
                        )
                    }
                }
                .pointerInput(samples) {
                    detectTapGestures { tap ->
                        selectedSample = samples.find {
                            val x =
                                padding + ((it.timestamp - samples.first().timestamp).toFloat() / (samples.last().timestamp - samples.first().timestamp)) * (size.width - padding * 2) * scale + offsetX
                            val y =
                                size.height - padding - (size.height - padding * 2) * (it.temperature - minY) / (maxY - minY)
                            (tap.x - x) * (tap.x - x) + (tap.y - y) * (tap.y - y) <= 25f * 25f
                        }
                    }
                }
        ) {
            val chartWidth = (size.width - padding * 2) * scale
            val chartHeight = size.height - padding * 2
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE; textSize = 30f; textAlign =
                android.graphics.Paint.Align.RIGHT
            }

            drawLine(
                Color.White,
                Offset(padding, padding),
                Offset(padding, size.height - padding),
                3f
            )
            drawLine(
                Color.White,
                Offset(padding, size.height - padding),
                Offset(size.width - padding, size.height - padding),
                3f
            )

            val stepCount = 5
            val step = (maxY - minY) / stepCount
            for (i in 0..stepCount) {
                val y = size.height - padding - chartHeight * (i * step) / (maxY - minY)
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f°C".format(minY + i * step),
                    padding - 10,
                    y + 10,
                    textPaint
                )
                drawLine(Color(0x22FFFFFF), Offset(padding, y), Offset(size.width - padding, y), 1f)
            }

            if (samples.size > 1) {
                val earliest = samples.first().timestamp
                val timeSpan = samples.last().timestamp - earliest
                for (i in 0..4) {
                    val t = earliest + i * timeSpan / 4
                    val x = padding + chartWidth * (t - earliest) / timeSpan + offsetX
                    if (x in padding..(size.width - padding)) {
                        drawContext.canvas.nativeCanvas.drawText(
                            android.text.format.DateFormat.format(
                                "HH:mm:ss",
                                t
                            ).toString(), x, size.height - padding + 30, textPaint
                        )
                        drawLine(
                            Color(0x22FFFFFF),
                            Offset(x, padding),
                            Offset(x, size.height - padding),
                            1f
                        )
                    }
                }
            }

            clipRect(
                left = padding,
                top = padding,
                right = size.width - padding,
                bottom = size.height - padding
            ) {
                if (samples.size > 1) {
                    val path = android.graphics.Path()
                    samples.forEachIndexed { i, sample ->
                        val x =
                            padding + ((sample.timestamp - samples.first().timestamp).toFloat() / (samples.last().timestamp - samples.first().timestamp)) * chartWidth + offsetX
                        val y =
                            size.height - padding - chartHeight * (sample.temperature - minY) / (maxY - minY)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(Color.Cyan, 8f, Offset(x, y))
                    }
                    drawPath(
                        path.asComposePath(),
                        Color.Red,
                        style = Stroke(4f, cap = StrokeCap.Round)
                    )
                }
            }
        }

        selectedSample?.let { BatteryInfoPopup(it) { selectedSample = null } }
    }
}

@Composable
fun BatteryInfoPopup(sample: BatterySample, onDismiss: () -> Unit) {
    val time = remember(sample.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(
            Date(sample.timestamp)
        )
    }
    Popup(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(
                        Color(0xFF1E1E1E),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(20.dp)
            ) {
                Text("Zeitpunkt: $time", color = Color.White)
                Text("Batteriestand: ${sample.level}%", color = Color.White)
                Text("Temperatur: ${sample.temperature} °C", color = Color.White)
                Text("Spannung: ${sample.voltage} mV", color = Color.White)
            }
        }
    }
}