package com.tabslify.tabs

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tabslify.core.TabNavigationViewModel
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.client
import com.tabslify.core.objects.Config.cms
import com.tabslify.core.objects.tNotify
import com.tabslify.core.objects.toast
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class WeatherRequest(
    val lat: Double,
    val lon: Double,
    val days: Int
)

data class HourData(
    val dateFull: String,
    val date: String,
    val time: String,
    val temp: Double,
    val icon: String,
    val condition: String,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    val pressure: Int
)

data class DayData(
    val date: String,
    val avgTemp: Double,
    val icon: String,
    val hours: List<HourData>
)

data class WeatherData(
    val city: String,
    val currentTemp: Double,
    val currentFeelsLike: Double,
    val currentCondition: String,
    val currentIcon: String,
    val days: List<DayData>
)

fun iconToEmoji(icon: String): String {
    val low = icon.lowercase()
    return when {
        "01" in low || "sun" in low -> "☀️"
        "02" in low || "partly" in low || "cloud" in low -> "⛅"
        "03" in low || "04" in low -> "☁️"
        "09" in low || "10" in low || "rain" in low -> "🌧️"
        "11" in low || "storm" in low -> "⛈️"
        "13" in low || "snow" in low -> "❄️"
        "50" in low || "mist" in low || "fog" in low -> "🌫️"
        else -> "🌤️"
    }
}

suspend fun fetchWeatherForecast(lat: Double, lon: Double, days: Int = 7): WeatherData =
    withContext(Dispatchers.IO) {

        val response = client.functions.invoke(
            function = "weather-api",
            body = WeatherRequest(lat = lat, lon = lon, days = days)
        )

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        val location = json["location"]?.jsonObject
        val current = json["current"]?.jsonObject
        val forecast = json["forecast"]?.jsonObject

        val forecastDays = forecast?.get("forecastday")?.jsonArray ?: emptyList()

        val daysList = forecastDays.map { dayEl ->
            val dayObj = dayEl.jsonObject

            val date = dayObj["date"]?.jsonPrimitive?.content ?: ""

            val hourArr = dayObj["hour"]?.jsonArray ?: emptyList()

            val hours = hourArr.map { hEl ->
                val h = hEl.jsonObject
                val condition = h["condition"]?.jsonObject

                val dt = h["time"]?.jsonPrimitive?.content ?: ""

                HourData(
                    dateFull = dt,
                    date = dt.take(10),
                    time = dt.takeLast(5),
                    temp = h["temp_c"]?.jsonPrimitive?.doubleOrNull ?: Double.NaN,
                    icon = condition?.get("icon")?.jsonPrimitive?.content ?: "",
                    condition = condition?.get("text")?.jsonPrimitive?.content ?: "",
                    feelsLike = h["feelslike_c"]?.jsonPrimitive?.doubleOrNull ?: Double.NaN,
                    humidity = h["humidity"]?.jsonPrimitive?.intOrNull ?: 0,
                    wind = h["wind_kph"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    pressure = h["pressure_mb"]?.jsonPrimitive?.intOrNull ?: 0
                )
            }

            val avg = hours.map { it.temp }
                .filter { !it.isNaN() }
                .let { if (it.isNotEmpty()) it.average() else Double.NaN }

            DayData(
                date = date,
                avgTemp = avg,
                icon = hours.firstOrNull()?.icon ?: "",
                hours = hours
            )
        }

        WeatherData(
            city = location?.get("name")?.jsonPrimitive?.content ?: "",
            currentTemp = current?.get("temp_c")?.jsonPrimitive?.doubleOrNull ?: Double.NaN,
            currentFeelsLike = current?.get("feelslike_c")?.jsonPrimitive?.doubleOrNull
                ?: Double.NaN,
            currentCondition = current?.get("condition")?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: "",
            currentIcon = current?.get("condition")?.jsonObject?.get("icon")?.jsonPrimitive?.content
                ?: "",
            days = daysList
        )
    }

suspend fun fetchCoordsForCity(city: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(city, "UTF-8")
        val conn = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "CloudApp/1.0")
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = Json.parseToJsonElement(text).jsonArray
        if (arr.isEmpty()) return@withContext null
        val obj = arr[0].jsonObject
        val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@withContext null
        val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@withContext null
        Pair(lat, lon)
    } catch (_: Exception) {
        null
    }
}

suspend fun getCurrentLocation(context: Context): Location? {
    return withContext(Dispatchers.IO) {
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext null
            }
            var loc: Location? = null
            val task = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            val latch = CountDownLatch(1)
            task.addOnSuccessListener {
                loc = it
                latch.countDown()
            }.addOnFailureListener {
                latch.countDown()
            }
            latch.await()
            loc
        } catch (_: Exception) {
            null
        }
    }
}

suspend fun getLastKnownLocation(context: Context): Location? {
    return withContext(Dispatchers.IO) {
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext null
            }
            var loc: Location? = null
            val task = fused.lastLocation
            val latch = CountDownLatch(1)
            task.addOnSuccessListener {
                loc = it
                latch.countDown()
            }.addOnFailureListener {
                latch.countDown()
            }
            latch.await()
            loc
        } catch (_: Exception) {
            null
        }
    }
}

data class SelectionState(val hour: HourData?, val dayIndex: Int?)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WeatherTabContent(
    viewModel: TabNavigationViewModel,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectionState by remember { mutableStateOf(SelectionState(hour = null, dayIndex = null)) }
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var directedToSettings by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val animIconbg = remember { Animatable(1f) }
    val animIconbgColor = remember { Animatable(Color(0xFF001FBB)) }

    val blink = {
        scope.launch {
            animIconbgColor.animateTo(Color.Red)
            animIconbg.animateTo(0.3f, animationSpec = tween(durationMillis = 400))
            delay(300.milliseconds)
            animIconbg.animateTo(0.8f, animationSpec = tween(durationMillis = 400))
            delay(300.milliseconds)
            animIconbg.animateTo(0.4f, animationSpec = tween(durationMillis = 400))
            delay(300.milliseconds)
            animIconbg.animateTo(1f, animationSpec = tween(durationMillis = 400))
        }
    }

    LaunchedEffect(selectionState.hour, selectionState.dayIndex) {
        val canGoBack = selectionState.hour != null || selectionState.dayIndex != null
        viewModel.updateBackState(
            canNavigateBack = canGoBack,
            onNavigateBack = {
                selectionState = if (selectionState.hour != null) {
                    selectionState.copy(hour = null)
                } else {
                    selectionState.copy(dayIndex = null)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.reset()
        }
    }

    BackHandler(enabled = selectionState.hour != null || selectionState.dayIndex != null) {
        selectionState = if (selectionState.hour != null) {
            selectionState.copy(hour = null)
        } else {
            selectionState.copy(dayIndex = null)
        }
    }

    var refreshWeather: () -> Unit = {}
    val manualLoc = {
        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            refreshWeather()
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", ctx.packageName, null)
            }
            ctx.startActivity(intent)
            val locale = Resources.getSystem().configuration.locales[0]
            val language = locale.language

            if (language == "de") {
                toast(ctx, "Berechtigungen -> Standort -> Immer erlauben")
            } else {
                toast(ctx, "Permissions -> Location -> Allow all the time")
            }
            directedToSettings = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            refreshWeather()
        }
    }

    refreshWeather = {
        scope.launch {
            isLoading = true
            error = null
            if (ActivityCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                isLoading = false
                blink()
                Config.requestPermission("loc", permissionLauncher, ctx)
                return@launch
            }
            val loc = getCurrentLocation(ctx)
            if (loc == null) {
                blink()
                isLoading = false
                return@launch
            }
            try {
                weather = fetchWeatherForecast(loc.latitude, loc.longitude, days = 14)
            } catch (e: Exception) {
                error = "Fehler: ${e.message}"
            } finally {
                isLoading = false
                animIconbgColor.animateTo(
                    Color(0xFF001FBB),
                    animationSpec = tween(durationMillis = 500)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshWeather()
    }

    val searchWeather: (String) -> Unit = { query ->
        scope.launch {
            isLoading = true
            error = null
            selectionState = SelectionState(hour = null, dayIndex = null)
            try {
                val coords = fetchCoordsForCity(query)
                if (coords == null) {
                    error = "Ort nicht gefunden: $query"
                } else {
                    weather = fetchWeatherForecast(coords.first, coords.second, days = 14)
                }
            } catch (e: Exception) {
                error = "Fehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(weather?.city) {
        weather?.city?.takeIf { it.isNotEmpty() }?.let { searchText = it }
    }

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(100.milliseconds)
        alpha.animateTo(
            1f, animationSpec = tween(
                durationMillis = 150,
                easing = FastOutSlowInEasing
            )
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && directedToSettings) {
                refreshWeather()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = {
            selectionState = SelectionState(hour = null, dayIndex = null)
            refreshWeather()
        },
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value),
        indicator = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (error != null) {
                Text(error!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ort suchen...", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6B6BFF),
                        unfocusedBorderColor = Color(0xFF44444F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF6B6BFF)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchWeather(searchText) })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { searchWeather(searchText) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                if (ActivityCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                animIconbgColor.value.copy(alpha = animIconbg.value),
                                shape = RoundedCornerShape(100)
                            )
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        IconButton(onClick = { manualLoc() }) {
                            Icon(
                                Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = Triple(weather, selectionState.hour, selectionState.dayIndex),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(
                        animationSpec = tween(
                            300
                        )
                    )
                }
            ) { (data, selHour, selDayIdx) ->
                if (data == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isLoading) {
                            Text("Keine Daten verfügbar", color = Color.LightGray)
                        }
                    }
                } else {
                    when {
                        selHour != null -> {
                            SelectedHourView(hour = selHour)
                        }

                        selDayIdx != null -> {
                            val days = data.days
                            if (selDayIdx in days.indices) {
                                DayHoursView(
                                    days[selDayIdx],
                                    onHourSelected = { hourData ->
                                        selectionState = selectionState.copy(hour = hourData)
                                    }
                                )
                            }
                        }

                        else -> {
                            MainView(
                                data = data,
                                onDaySelected = { idx ->
                                    selectionState = selectionState.copy(dayIndex = idx)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainView(data: WeatherData, onDaySelected: (Int) -> Unit) {
    Column {
        data.days.firstOrNull()?.let { today ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDaySelected(0) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Heute",
                        color = Color(0xFF8B8B9F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        data.city,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        iconToEmoji(data.currentIcon),
                        fontSize = 64.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${data.currentTemp.toInt()}°C",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        data.currentCondition,
                        color = Color(0xFFB8B8C7),
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickInfoItem("Gefühlt", "${data.currentFeelsLike.toInt()}°C")
                        QuickInfoItem("Ø Tag", "${today.avgTemp.toInt()}°C")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Vorschau",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        data.days.drop(1).take(6).forEachIndexed { index, day ->
            val actualIndex = index + 1
            DayCard(
                day = day,
                dayIndex = actualIndex,
                onClick = { onDaySelected(actualIndex) }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun QuickInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Color(0xFF8B8B9F),
            fontSize = 12.sp
        )
        Text(
            value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DayCard(day: DayData, dayIndex: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    getDayName(dayIndex),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    day.date,
                    color = Color(0xFF8B8B9F),
                    fontSize = 12.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    iconToEmoji(day.icon),
                    fontSize = 32.sp
                )
                Text(
                    "${day.avgTemp.toInt()}°C",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun getDayName(index: Int): String {
    return when (index) {
        1 -> "Morgen"
        2 -> "Übermorgen"
        else -> "Tag ${index + 1}"
    }
}

@Composable
fun DayHoursView(day: DayData, onHourSelected: (HourData) -> Unit) {
    Column {
        Text(
            "Stunden für ${day.date}",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        day.hours.forEach { hour ->
            HourCard(hour = hour, onClick = { onHourSelected(hour) })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun HourCard(hour: HourData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                hour.time,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                hour.condition,
                color = Color(0xFF8B8B9F),
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    iconToEmoji(hour.icon),
                    fontSize = 28.sp
                )
                Text(
                    "${hour.temp.toInt()}°C",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SelectedHourView(hour: HourData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                hour.time,
                color = Color(0xFF8B8B9F),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                iconToEmoji(hour.icon),
                fontSize = 72.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "${hour.temp.toInt()}°C",
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                hour.condition,
                color = Color(0xFFB8B8C7),
                fontSize = 18.sp
            )

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherDetailBox("🌡️", "Gefühlt", "${hour.feelsLike.toInt()}°C")
                    WeatherDetailBox("💧", "Luftfeuchtigkeit", "${hour.humidity}%")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherDetailBox("💨", "Wind", "${hour.wind.toInt()} km/h")
                    WeatherDetailBox("🔽", "Luftdruck", "${hour.pressure} hPa")
                }
            }
        }
    }
}

@Composable
fun WeatherDetailBox(icon: String, label: String, value: String) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A35)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            //Text(icon, fontSize = 24.sp)
            Icon(Icons.Default.Thermostat, contentDescription = "Thermostat")
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = Color(0xFF8B8B9F),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

suspend fun weathernot(context: Context, day: String, hour: String, weatherData: WeatherData?) {
    if (weatherData == null) {
        return
    }

    val dayIndex = when (day.lowercase()) {
        "heute" -> 0
        "morgen" -> 1
        "übermorgen" -> 2
        else -> day.toIntOrNull()?.minus(1) ?: return
    }

    if (dayIndex < 0 || dayIndex >= weatherData.days.size) {
        return
    }

    val hourInt = hour.toIntOrNull() ?: return
    if (hourInt !in 1..24) {
        return
    }

    val selectedDay = weatherData.days[dayIndex]

    if (hourInt >= selectedDay.hours.size) {
        return
    }

    val selectedHour = selectedDay.hours[hourInt]

    withContext(Dispatchers.Main) {
        createWeatherNotification(context, day, selectedHour)
    }
}

private fun createWeatherNotification(context: Context, dayName: String, hourData: HourData) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "weather_notifications"

    val channel = NotificationChannel(
        channelId,
        "Wetter Benachrichtigungen",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Benachrichtigungen für Wettervorhersagen"
    }
    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_dialog_info)
        .setContentTitle("☁️ Wetter für $dayName um ${hourData.time} Uhr")
        .setContentText("${hourData.temp.toInt()}°C - ${hourData.condition}")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(
                    """
                    ${iconToEmoji(hourData.icon)} ${hourData.condition}
                    
                    🌡️ Temperatur: ${hourData.temp.toInt()}°C
                    🌡️ Gefühlt: ${hourData.feelsLike.toInt()}°C
                    💧 Luftfeuchtigkeit: ${hourData.humidity}%
                    💨 Wind: ${hourData.wind.toInt()} km/h
                    🔽 Luftdruck: ${hourData.pressure} hPa
                    """.trimIndent()
                )
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    tNotify(context, cms(), notification)
}