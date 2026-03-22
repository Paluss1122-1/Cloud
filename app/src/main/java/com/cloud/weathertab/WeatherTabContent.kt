// File: WeatherScreenFull.kt
package com.cloud.weathertab

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.cloud.Config.WEATHERAPI_KEY
import com.cloud.Config.cms
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// -----------------------------
// Models
// -----------------------------
data class HourData(
    val dateFull: String, // "yyyy-MM-dd HH:mm"
    val date: String, // "yyyy-MM-dd"
    val time: String, // "HH:mm"
    val temp: Double,
    val icon: String,
    val condition: String,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    val pressure: Int
)

data class DayData(
    val date: String, // "yyyy-MM-dd"
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

// -----------------------------
// Utilities
// -----------------------------
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

// -----------------------------
// Network + Parser (WeatherAPI forecast.json)
// -----------------------------
suspend fun fetchWeatherForecast(lat: Double, lon: Double, days: Int = 7): WeatherData =
    withContext(Dispatchers.IO) {
        val url =
            "https://api.weatherapi.com/v1/forecast.json?key=$WEATHERAPI_KEY&q=$lat,$lon&days=$days&aqi=no&alerts=no&lang=de"
        val raw = URL(url).readText()
        val root = JSONObject(raw)

        val location = root.getJSONObject("location")
        val current = root.getJSONObject("current")
        val forecast = root.getJSONObject("forecast")
        val forecastDays = forecast.getJSONArray("forecastday")

        val daysList = mutableListOf<DayData>()

        for (i in 0 until forecastDays.length()) {
            val dayObj = forecastDays.getJSONObject(i)
            val date = dayObj.getString("date") // yyyy-MM-dd
            val hourArr = dayObj.getJSONArray("hour")
            val hours = mutableListOf<HourData>()
            for (j in 0 until hourArr.length()) {
                val h = hourArr.getJSONObject(j)
                val dt = h.getString("time") // "yyyy-MM-dd HH:mm"
                val datePart = dt.substring(0, 10)
                val timePart = dt.substring(11, 16)
                val condition = h.getJSONObject("condition")
                hours.add(
                    HourData(
                        dateFull = dt,
                        date = datePart,
                        time = timePart,
                        temp = h.optDouble("temp_c", Double.NaN),
                        icon = condition.optString("icon", ""),
                        condition = condition.optString("text", ""),
                        feelsLike = h.optDouble("feelslike_c", Double.NaN),
                        humidity = h.optInt("humidity", 0),
                        wind = h.optDouble("wind_kph", 0.0),
                        pressure = h.optInt("pressure_mb", 0)
                    )
                )
            }
            val avg = hours.map { it.temp }.filter { !it.isNaN() }.let { if (it.isNotEmpty()) it.average() else Double.NaN }
            val icon = hours.firstOrNull()?.icon ?: ""
            daysList.add(DayData(date = date, avgTemp = avg, icon = icon, hours = hours))
        }

        WeatherData(
            city = location.getString("name"),
            currentTemp = current.optDouble("temp_c", Double.NaN),
            currentFeelsLike = current.optDouble("feelslike_c", Double.NaN),
            currentCondition = current.getJSONObject("condition").optString("text", ""),
            currentIcon = current.getJSONObject("condition").optString("icon", ""),
            days = daysList
        )
    }

// -----------------------------
// Location helper (simple)
// -----------------------------
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
            // We can't await here nicely without additional libs; do quick blocking-ish wait
            val latch = java.util.concurrent.CountDownLatch(1)
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

// -----------------------------
// Compose UI: full screen with tabs + animations
// -----------------------------
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WeatherTabContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedHour by remember { mutableStateOf<HourData?>(null) }
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    val refreshWeather: () -> Unit = {
        scope.launch {
            isLoading = true
            error = null
            val loc = getLastKnownLocation(ctx)
            if (loc == null) {
                error = "Standort nicht verfügbar"
                isLoading = false
                return@launch
            }
            try {
                weather = fetchWeatherForecast(loc.latitude, loc.longitude, days = 14)
            } catch (e: Exception) {
                error = "Fehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Automatisch beim Start laden
    LaunchedEffect(Unit) {
        refreshWeather()
    }

    BackHandler(enabled = selectedHour != null || selectedDayIndex != null) {
        if (selectedHour != null) {
            selectedHour = null
        } else {
            selectedDayIndex = null
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            selectedHour = null
            selectedDayIndex = null
            refreshWeather()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("☁️ Wetter", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (error != null) {
                Text(error!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
            }

            AnimatedContent(
                targetState = Triple(weather, selectedHour, selectedDayIndex),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                }
            ) { (data, selHour, selDayIdx) ->
                if (data == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isLoading) {
                            Text("Keine Daten verfügbar", color = Color.LightGray)
                        }
                    }
                } else {
                    when {
                        selHour != null -> {
                            // Detail-Ansicht einer Stunde
                            SelectedHourView(selHour)
                        }
                        selDayIdx != null -> {
                            // Stunden-Ansicht eines Tages
                            val days = data.days
                            if (selDayIdx in days.indices) {
                                DayHoursView(days[selDayIdx], onHourSelected = { selectedHour = it })
                            }
                        }
                        else -> {
                            // Haupt-Ansicht: Heute groß + alle Tage untereinander
                            MainView(
                                data = data,
                                onDaySelected = { selectedDayIndex = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------
// Main View: Heute groß + alle Tage untereinander
// -----------------------------
@Composable
fun MainView(data: WeatherData, onDaySelected: (Int) -> Unit) {
    Column {
        // Heute - groß dargestellt
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

                    // Quick Info Row
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

        // Alle anderen Tage untereinander
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
            // Datum
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

            // Icon & Temperatur
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

// -----------------------------
// Day Hours View: Stunden eines Tages
// -----------------------------
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
            // Zeit
            Text(
                hour.time,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Bedingung
            Text(
                hour.condition,
                color = Color(0xFF8B8B9F),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )

            // Icon & Temperatur
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

// -----------------------------
// Selected Hour View: Detailansicht
// -----------------------------
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

            // Detaillierte Informationen in Grid
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
            Text(icon, fontSize = 24.sp)
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

// -----------------------------
// Alte Funktionen bleiben erhalten für Kompatibilität
// -----------------------------
@Composable
fun TodayView(data: WeatherData, onDaySelected: (Int) -> Unit, onHourSelected: (HourData) -> Unit) {
    val today = data.days.firstOrNull()
    if (today == null) {
        Text("Keine Tagesdaten", color = Color.White)
        return
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF22222A))) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${data.city}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("${data.currentTemp.toInt()}°C  ${iconToEmojiShort(data.currentIcon())}", color = Color.White, fontSize = 40.sp)
            Text(data.currentCondition, color = Color.LightGray)
            Spacer(Modifier.height(12.dp))

            Text("Stundenvorhersage", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                today.hours.forEach { hour ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .width(110.dp)
                            .clickable { onHourSelected(hour) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2F2F3A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(hour.time, color = Color.White)
                            Text(iconToEmojiShort(hour.icon), fontSize = 28.sp)
                            Text("${hour.temp.toInt()}°C", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun WeatherData.currentIcon() = currentIcon
private fun iconToEmojiShort(icon: String) = iconToEmoji(icon)

@Composable
fun SmallHourCard(hour: HourData) {
    Column(modifier = Modifier.padding(8.dp).width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(hour.time, color = Color.White)
        Text(iconToEmojiShort(hour.icon), fontSize = 24.sp)
        Text("${hour.temp.toInt()}°", color = Color.White)
    }
}

@Composable
fun DaysOverview(days: List<DayData>, onDayClick: (Int) -> Unit) {
    Column {
        Text("Tage", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            days.forEachIndexed { idx, d ->
                Card(
                    modifier = Modifier
                        .padding(8.dp)
                        .width(120.dp)
                        .clickable { onDayClick(idx) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3A))
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.date, color = Color.White)
                        Text(iconToEmojiShort(d.icon), fontSize = 32.sp)
                        Text("${d.avgTemp.toInt()}°C", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDetailSmall(icon: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 18.sp)
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}

// -----------------------------
// Small 'widget' preview composable
// -----------------------------
@Composable
fun WeatherWidgetPreview(data: WeatherData) {
    Card(modifier = Modifier.width(200.dp).height(120.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3A)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.SpaceBetween) {
            Text(data.city, color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${data.currentTemp.toInt()}°C", color = Color.White, fontSize = 20.sp)
                Text(iconToEmojiShort(data.currentIcon()), fontSize = 20.sp)
            }
        }
    }
}

// Erweiterte Weathernot-Funktion mit Notification-Support
// Füge diese Funktion am Ende deiner WeatherScreenFull.kt-Datei hinzu

// Diese Funktion sollte die bestehende leere Weathernot-Funktion ersetzen
suspend fun weathernot(context: Context, day: String, hour: String, weatherData: WeatherData?) {
    if (weatherData == null) {
        return
    }

    // Konvertiere day String zu Index
    val dayIndex = when (day.lowercase()) {
        "heute" -> 0
        "morgen" -> 1
        "übermorgen" -> 2
        else -> day.toIntOrNull()?.minus(1) ?: return
    }

    // Validiere Tag-Index
    if (dayIndex < 0 || dayIndex >= weatherData.days.size) {
        return
    }

    // Konvertiere Stunde zu Int (1-24)
    val hourInt = hour.toIntOrNull() ?: return
    if (hourInt < 1 || hourInt > 24) {
        return
    }

    // Finde die entsprechende Stunde (0-23 für Array-Index)
    val selectedDay = weatherData.days[dayIndex]
    val hourIndex = hourInt // Konvertiere 1-24 zu 0-23

    if (hourIndex >= selectedDay.hours.size) {
        return
    }

    val selectedHour = selectedDay.hours[hourIndex]

    // Erstelle Notification
    withContext(Dispatchers.Main) {
        createWeatherNotification(context, day, selectedHour)
    }
}

private fun createWeatherNotification(context: Context, dayName: String, hourData: HourData) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "weather_notifications"

    val channel = NotificationChannel(
        channelId,
        "Wetter Benachrichtigungen",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Benachrichtigungen für Wettervorhersagen"
    }
    notificationManager.createNotificationChannel(channel)

    // Erstelle die Notification
    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info) // Ersetze mit deinem eigenen Icon
        .setContentTitle("☁️ Wetter für $dayName um ${hourData.time} Uhr")
        .setContentText("${hourData.temp.toInt()}°C - ${hourData.condition}")
        .setStyle(
            androidx.core.app.NotificationCompat.BigTextStyle()
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
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(cms(), notification)
}