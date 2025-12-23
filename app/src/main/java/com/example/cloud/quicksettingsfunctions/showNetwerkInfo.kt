package com.example.cloud.quicksettingsfunctions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import com.example.cloud.privatecloudapp.showNetworkNotificationNow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.Executors


@SuppressLint("MissingPermission") // Berechtigungen werden zur Laufzeit geprüft
fun showNetworkInfo(context: Context) {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val info = StringBuilder()

    // === 1. Aktives Netzwerk ===
    val activeNetwork = connectivityManager.activeNetwork
    val networkCapabilities = if (activeNetwork != null) {
        connectivityManager.getNetworkCapabilities(activeNetwork)
    } else null

    if (networkCapabilities == null) {
        info.append("🌐 Keine aktive Netzwerkverbindung\n\n")
    } else {
        val transport = when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Unbekannt"
        }

        val isInternet =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        info.append("📡 Aktive Verbindung: $transport\n")
        info.append("✅ Internet verfügbar: ${if (isInternet && isValidated) "Ja" else "Nein (kein Zugriff)"}\n\n")

        // === 2. WLAN-Details (wenn WLAN) ===
        if (transport == "WLAN") {
            var ssid: String
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                ssid = wifiInfo?.ssid?.trim()?.removeSurrounding("\"") ?: ""
            } else {
                ssid = "<Standortberechtigung fehlt>"
            }
            info.append("📶 WLAN-Name (SSID): $ssid\n")

            // Signalstärke (RSSI → dBm)
            val rssi = wifiManager.connectionInfo.rssi
            val level = wifiManager.calculateSignalLevel(rssi)
            val bars = "▂▄▆█".substring(0, level.coerceIn(0, 4))
            info.append("📡 Signalstärke: $rssi dBm ($bars)\n\n")
        }

        // === 3. Mobilfunk-Details (wenn Mobil) ===
        if (transport == "Mobilfunk") {
            try {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val networkType = when (telephonyManager.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                    else -> "Mobil (${telephonyManager.dataNetworkType})"
                }
                info.append("📶 Mobilfunktyp: $networkType\n")
                // Signalstärke bei Mobil ist komplex → optional, hier weggelassen für Stabilität
            } catch (_: Exception) {
                info.append("📶 Mobilfunktyp: N/A\n")
            }
            info.append("\n")
        }
    }

    // === 4. Lokale IP-Adresse ===
    try {
        var localIp = "N/A"
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress  && inetAddress.hostAddress?.contains(":") == false) {
                    localIp = inetAddress.hostAddress
                    break
                }
            }
            if (localIp != "N/A") break
        }
        info.append("🏠 Lokale IP: $localIp\n")
    } catch (_: Exception) {
        info.append("🏠 Lokale IP: N/A\n")
    }

    // === 5. Öffentliche IP (asynchron, da Netzwerkaufruf) ===
    info.append("🌍 Öffentliche IP: Wird abgerufen…\n")

    // Benachrichtigung VOR async-IP-Abruf anzeigen
    showNetworkNotificationNow(context, info.toString())

    // Öffentliche IP im Hintergrund laden
    Executors.newSingleThreadExecutor().execute {
        var publicIp: String
        try {
            val url = URL("https://api.ipify.org")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            publicIp = reader.readLine() ?: "Fehler"
            reader.close()
            conn.disconnect()
        } catch (_: Exception) {
            publicIp = "Offline / Timeout"
        }

        // Benachrichtigung mit aktualisierter öffentlicher IP
        val updatedInfo = info.toString()
            .replace("🌍 Öffentliche IP: Wird abgerufen…", "🌍 Öffentliche IP: $publicIp")
        showNetworkNotificationNow(context, updatedInfo, final = true)
    }
}