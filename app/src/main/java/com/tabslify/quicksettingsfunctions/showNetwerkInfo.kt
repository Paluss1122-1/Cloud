package com.tabslify.quicksettingsfunctions

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
import androidx.core.app.NotificationCompat
import com.tabslify.R
import com.tabslify.core.objects.Config.cms
import com.tabslify.core.objects.tNotify
import com.tabslify.services.QuietHoursNotificationService.Companion.SSN_CHANNEL_ID
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import com.tabslify.core.activities.Tabslify.Companion.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@SuppressLint("MissingPermission")
fun showNetworkInfo(context: Context) {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val info = StringBuilder()

    val activeNetwork = connectivityManager.activeNetwork
    val networkCapabilities = if (activeNetwork != null) {
        connectivityManager.getNetworkCapabilities(activeNetwork)
    } else null

    if (networkCapabilities == null) {
        info.append(context.getString(R.string.keine_aktive_netzwerkverbindung))
    } else {
        val transport = when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Unbekannt"
        }

        val transportLabel = when (transport) {
            "Mobilfunk" -> context.getString(R.string.mobilfunk)
            "Ethernet" -> context.getString(R.string.ethernet)
            "Bluetooth" -> context.getString(R.string.bluetooth)
            "Unbekannt" -> context.getString(R.string.unbekannt)
            else -> transport
        }

        val isInternet =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        info.append(context.getString(R.string.aktive_verbindung, transportLabel))
        info.append(
            context.getString(
                R.string.internet_verfugbar,
                if (isInternet && isValidated) context.getString(R.string.internet_ja)
                else context.getString(R.string.internet_nein)
            ) + "\n\n"
        )

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
                ssid = context.getString(R.string.standortberechtigung_fehlt)
            }
            info.append(context.getString(R.string.wlan_name_ssid, ssid))

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val rssi =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.signalStrength
                    ?: 0
            val level = wifiManager.calculateSignalLevel(rssi)
            val bars = "▂▄▆█".substring(0, level.coerceIn(0, 4))
            info.append(context.getString(R.string.signalstarke_dbm, rssi, bars))
        }

        if (transport == "Mobilfunk") {
            try {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val networkType = when (telephonyManager.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                    else -> context.getString(R.string.mobil, telephonyManager.dataNetworkType)
                }
                info.append(context.getString(R.string.mobilfunktyp, networkType))
            } catch (_: Exception) {
                info.append(context.getString(R.string.mobilfunktyp_n_a))
            }
            info.append("\n")
        }
    }

    try {
        var localIp = "N/A"
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress?.contains(":") == false) {
                    localIp = inetAddress.hostAddress ?: "N/A"
                    break
                }
            }
            if (localIp != "N/A") break
        }
        info.append(context.getString(R.string.lokale_ip, localIp))
    } catch (_: Exception) {
        info.append(context.getString(R.string.lokale_ip_n_a))
    }

    info.append(context.getString(R.string.offentliche_ip_wird_abgerufen))

    val notId = cms()

    appScope.launch(Dispatchers.IO) {
        var publicIp: String
        try {
            val url = URL("https://api.ipify.org")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            publicIp = reader.readLine() ?: context.getString(R.string.fehler)
            reader.close()
            conn.disconnect()
        } catch (_: Exception) {
            publicIp = context.getString(R.string.offline_timeout)
        }

        val updatedInfo = info.toString()
            .replace(
                context.getString(R.string.offentliche_ip_wird_abgerufen_2),
                context.getString(R.string.offentliche_ip, publicIp)
            )
        showNetworkNotificationNow(context, updatedInfo, notId)
    }
}

fun showNetworkNotificationNow(context: Context, content: String, notId: Int) {
    val builder = NotificationCompat.Builder(context, SSN_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setContentTitle(context.getString(R.string.netzwerk_info))
        .setContentText(content.lines().firstOrNull() ?: context.getString(R.string.netzwerkinfo))
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_LOW)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        tNotify(context, notId, builder)
    }
}