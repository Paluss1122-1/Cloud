package com.example.cloud.quicksettingsfunctions

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.text.ifEmpty


fun showSensorsInfo(context: Context) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)

    if (sensorList.isEmpty()) {
        showSensorNotification(context, "Keine Sensoren gefunden")
        return
    }

    val info = StringBuilder()
    info.append("📱 **Gefundene Sensoren**: ${sensorList.size}\n\n")

    // Gruppiert nach Typ für bessere Lesbarkeit
    sensorList.forEachIndexed { index, sensor ->
        val typeStr = getSensorTypeString(sensor.type)
        val vendor = sensor.vendor.ifEmpty { "N/A" }
        val version = sensor.version
        val resolution = sensor.resolution
        val maxRange = sensor.maximumRange
        val power = sensor.power // mA
        val minDelay =
            if (sensor.minDelay > 0) "${sensor.minDelay / 1000} ms" else "kein Livestream"

        info.append("[$index] **${sensor.name}**\n")
        info.append("   Typ: $typeStr\n")
        info.append("   Hersteller: $vendor\n")
        info.append("   Version: $version\n")
        info.append("   Reichweite: ±$maxRange\n")
        info.append("   Auflösung: $resolution\n")
        info.append("   Min. Verzögerung: $minDelay\n")
        info.append("   Stromverbrauch: ${String.format("%.2f", power)} mA\n\n")
    }

    info.append("ℹ️ Hinweis: Werte sind statisch. Keine Live-Daten.")

    showSensorNotification(context, info.toString())
}

// Hilfsfunktion: Typ in lesbaren String umwandeln
private fun getSensorTypeString(type: Int): String {
    return when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Beschleunigungssensor"
        Sensor.TYPE_GYROSCOPE -> "Gyroskop"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetfeld (Kompass)"
        Sensor.TYPE_LIGHT -> "Umgebungslicht"
        Sensor.TYPE_PROXIMITY -> "Näherungssensor"
        Sensor.TYPE_PRESSURE -> "Luftdruck (Barometer)"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotationsvektor"
        Sensor.TYPE_GRAVITY -> "Schwerkraft"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Lineare Beschleunigung"
        Sensor.TYPE_ORIENTATION -> "Orientierung (veraltet)"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Umgebungstemperatur"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Luftfeuchtigkeit"
        Sensor.TYPE_HEART_RATE -> "Herzfrequenz"
        Sensor.TYPE_STEP_DETECTOR -> "Schritterkennung"
        Sensor.TYPE_STEP_COUNTER -> "Schrittzähler"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetischer Rotationsvektor"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Spiel-Rotationsvektor"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Bedeutende Bewegung"
        Sensor.TYPE_HINGE_ANGLE -> "Klappwinkel (Foldables)"
        Sensor.TYPE_POSE_6DOF -> "6DOF Pose"
        Sensor.TYPE_MOTION_DETECT -> "Bewegungserkennung"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Unkalibriertes Magnetfeld"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Unkalibrierte Beschleunigung"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Unkalibriertes Gyroskop"
        Sensor.TYPE_HEART_BEAT -> "Herzschlag (Rhythmus)"
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "KörpereRKennung (Low Latency)"
        Sensor.TYPE_ACCELEROMETER_LIMITED_AXES -> "Beschleunigung (limitierte Achsen)"
        Sensor.TYPE_GYROSCOPE_LIMITED_AXES -> "Gyroskop (limitierte Achsen)"
        else -> "Unbekannt ($type)"
    }
}

// Einheitliche Benachrichtigung
private fun showSensorNotification(context: Context, content: String) {
    val channelId = "sensors_info_channel"
    val notificationId = 1040
    val channel = NotificationChannel(
        channelId,
        "Sensor-Informationen",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Listet alle verfügbaren Sensoren auf"
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(channel)

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("📡 Sensoren-Info")
        .setContentText("Anzahl und Details aller Sensoren")
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    } else {
        // Fallback: Anzahl + erstes Beispiel
        val lines = content.lines()
        val preview = lines.take(4).joinToString("\n")
        Toast.makeText(context, "Sensoren:\n$preview", Toast.LENGTH_LONG).show()
    }
}