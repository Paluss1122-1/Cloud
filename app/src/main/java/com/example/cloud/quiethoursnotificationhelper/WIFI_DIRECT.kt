package com.example.cloud.quiethoursnotificationhelper

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.example.cloud.Config.CLIPBOARD_PORT
import com.example.cloud.Config.LAPTOP_IPS
import com.example.cloud.Config.SYNC_PORT
import com.example.cloud.Config.UPDATE_PORT
import com.example.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.example.cloud.showSimpleNotificationExtern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

private var updateServerSocket: ServerSocket? = null
private var listenerJob: Job? = null

var isLaptopConnected = false

private var triggerServerSocket: ServerSocket? = null
private var triggerJob: Job? = null

private var wifiLock: WifiManager.WifiLock? = null

private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

data class TodoItem(
    val id: Long,
    val text: String,
    val completed: Boolean,
    val timestamp: Long
)

private var appContext: Context? = null

private const val PREFS_SYNC = "sync_prefs"
private const val KEY_SYNC_ACTIVE = "sync_active"
private const val KEY_SYNC_UNTIL = "sync_until"

private fun saveSyncState(context: Context, durationMinutes: Int = 0) {
    context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE).edit {
        putBoolean(KEY_SYNC_ACTIVE, true)
        putLong(KEY_SYNC_UNTIL, System.currentTimeMillis() + durationMinutes * 60_000L)
    }
}

fun startTriggerListenerIfHomeWifi(context: Context) {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ssid = wifiManager.connectionInfo.ssid.trim('"')
    val homeSSIDs = listOf("FRITZ!Box 5590 XO") // anpassen

    if (ssid in homeSSIDs) {
        startTriggerListener(context)
        showSimpleNotificationExtern(
            "📶 WLAN verbunden",
            "✅ Im Heim-WLAN ($ssid), Trigger Listener gestartet",
            10.seconds,
            context
        )
    } else {
        Log.d("TodoSync", "⚠️ Nicht im Heim-WLAN ($ssid), kein Trigger Listener")
    }
}

fun startTriggerListener(context: Context) {
    triggerJob?.cancel()
    triggerJob = syncScope.launch(Dispatchers.IO) {
        try {
            triggerServerSocket = ServerSocket(8893)
            Log.d("TodoSync", "🎯 Trigger Listener aktiv auf Port 8893")
            while (isActive) {
                try {
                    val client = triggerServerSocket?.accept() ?: break
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val command = reader.readLine()
                    client.close()

                    if (command == "CONNECT") {
                        Log.d("TodoSync", "📡 CONNECT-Befehl empfangen, starte Sync...")
                        syncScope.launch(Dispatchers.Main) {
                            syncTodosWithLaptop(context)
                        }
                    }
                } catch (e: Exception) {
                    if (e !is java.net.SocketException) Log.e("TodoSync", "Trigger Fehler", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TodoSync", "Trigger Listener Fehler", e)
        }
    }
}

fun stopTriggerListener() {
    triggerJob?.cancel(); triggerJob = null
    triggerServerSocket?.close(); triggerServerSocket = null
}

fun restoreSyncIfNeeded(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
    val syncActive = prefs.getBoolean(KEY_SYNC_ACTIVE, false)
    val syncUntil = prefs.getLong(KEY_SYNC_UNTIL, 0L)
    val remainingMs = syncUntil - System.currentTimeMillis()

    if (syncActive && remainingMs > 0) {
        val remainingMinutes = (remainingMs / 60_000L).toInt().coerceAtLeast(1)
        Log.d("TodoSync", "🔁 Auto-Restore: Listener für noch ${remainingMinutes}min")
        startUpdateListener(context, remainingMinutes)
        showSimpleNotificationExtern(
            "🔁 Sync wiederhergestellt",
            "Listener läuft noch $remainingMinutes min",
            10.seconds, context
        )
    }
}

fun getTodos(context: Context): List<TodoItem> {
    val json = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)
        .getString("todos", "[]") ?: "[]"
    return parseTodosFromJson(json)
}

fun saveTodos(context: Context, todos: List<TodoItem>) {
    val prefs = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)

    try {
        val jsonArray = org.json.JSONArray()
        todos.forEach { todo ->
            val obj = org.json.JSONObject().apply {
                put("id", todo.id)
                put("text", todo.text)
                put("completed", todo.completed)
                put("timestamp", todo.timestamp)
            }
            jsonArray.put(obj)
        }

        prefs.edit {
            putString("todos", jsonArray.toString()).apply()
        }
    } catch (e: Exception) {
        Log.e("TodoManager", "Error saving todos", e)
    }
}

fun addTodo(text: String, context: Context) {
    val todos = getTodos(context).toMutableList()
    val newTodo = TodoItem(
        id = System.currentTimeMillis(),
        text = text,
        completed = false,
        timestamp = System.currentTimeMillis()
    )
    todos.add(newTodo)
    saveTodos(context, todos)

    showSimpleNotificationExtern(
        "✅ To-do hinzugefügt",
        "\"$text\"\n\nGesamt: ${todos.size} To-dos",
        10.seconds,
        context
    )
}

fun completeTodo(index: Int, context: Context) {
    val todos = getTodos(context).toMutableList()

    if (index in todos.indices) {
        todos[index] = todos[index].copy(completed = true)
        saveTodos(context, todos)

        showSimpleNotificationExtern(
            "✓ Erledigt",
            "\"${todos[index].text}\"",
            10.seconds,
            context
        )
    } else {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "To-do #${index + 1} existiert nicht",
            10.seconds,
            context
        )
    }
}

fun removeTodo(index: Int, context: Context) {
    val todos = getTodos(context).toMutableList()

    if (index in todos.indices) {
        val removed = todos.removeAt(index)
        saveTodos(context, todos)

        showSimpleNotificationExtern(
            "🗑️ Gelöscht",
            "\"${removed.text}\"",
            10.seconds,
            context
        )
    } else {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "To-do #${index + 1} existiert nicht",
            10.seconds,
            context
        )
    }
}

fun showAllTodos(context: Context) {
    val todos = getTodos(context)

    if (todos.isEmpty()) {
        showSimpleNotificationExtern(
            "📝 To-dos",
            "Keine To-dos vorhanden\n\nErstelle eins mit: * \"deine aufgabe\"",
            10.seconds,
            context = context
        )
        return
    }

    val activeTodos = todos.filter { !it.completed }
    val completedTodos = todos.filter { it.completed }
    val todoIndexMap = todos.mapIndexed { index, todo -> todo.id to (index + 1) }.toMap()

    val todoText = buildString {
        if (activeTodos.isNotEmpty()) {
            append("📌 OFFEN (${activeTodos.size}):\n")
            activeTodos.forEachIndexed { _, todo ->
                append("${todoIndexMap[todo.id]}. ${todo.text}\n")
            }
        }

        if (completedTodos.isNotEmpty()) {
            if (activeTodos.isNotEmpty()) append("\n")
            append("✓ ERLEDIGT (${completedTodos.size}):\n")
            completedTodos.forEachIndexed { _, todo ->
                append("${todoIndexMap[todo.id]}. ${todo.text}\n")
            }
        }
    }

    val chunks = splitText(todoText)

    val notificationManager = context.getSystemService(NotificationManager::class.java)

    chunks.forEachIndexed { index, chunk ->
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("📝 To-do Liste (${todos.size})")
            .setStyle(NotificationCompat.BigTextStyle().bigText(chunk))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(60000 + index, notification)
    }
}

fun syncTodosWithLaptop(context: Context) {
    showSimpleNotificationExtern(
        "🔄 Sync gestartet",
        "Verbinde mit Laptop...",
        10.seconds,
        context
    )

    syncScope.launch {
        try {
            val todos = getTodos(context)
            val todosJson = org.json.JSONArray()

            todos.forEach { todo ->
                val obj = org.json.JSONObject().apply {
                    put("id", todo.id)
                    put("text", todo.text)
                    put("completed", todo.completed)
                    put("timestamp", todo.timestamp)
                }
                todosJson.put(obj)
            }

            var lastException: Exception? = null
            var connected = false

            for (ip in LAPTOP_IPS) {
                try {
                    Log.d("TodoSync", "Versuche Verbindung zu $ip...")
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ip, SYNC_PORT), 3000)

                    val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                    writer.println(todosJson.toString())
                    writer.flush()
                    socket.close()

                    connected = true
                    isLaptopConnected = true
                    Log.d("TodoSync", "✅ Verbunden über $ip")
                    break
                } catch (e: Exception) {
                    Log.w("TodoSync", "❌ $ip fehlgeschlagen: ${e.message}")
                    lastException = e
                }
            }

            if (connected) {
                withContext(Dispatchers.Main) {
                    startUpdateListener(context, 60)
                    showSimpleNotificationExtern(
                        "✅ Sync erfolgreich",
                        "${todos.size} To-dos übertragen\n🔄 Listener aktiv für 60min",
                        10.seconds, context
                    )
                }
            } else {
                throw lastException ?: Exception("Alle IPs fehlgeschlagen")
            }
        } catch (e: Exception) {
            Log.e("TodoSync", "Sync failed", e)
            withContext(Dispatchers.Main) {
                showSimpleNotificationExtern(
                    "❌ Sync fehlgeschlagen",
                    "Laptop nicht erreichbar: ${e.message}\n\nStelle sicher, dass das Python-Script läuft",
                    10.seconds, context
                )
            }
        }
    }
}

fun startUpdateListener(context: Context, durationMinutes: Int = 60) {
    stopUpdateListener()
    appContext = context.applicationContext
    saveSyncState(context, durationMinutes)

    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    wifiLock =
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "TodoSync:WifiLock")
    wifiLock?.acquire()

    startClipboardSync(context)

    Log.d("TodoSync", "Starte Update Listener für $durationMinutes Minuten")

    listenerJob = syncScope.launch(Dispatchers.IO) {
        try {
            updateServerSocket = ServerSocket(UPDATE_PORT)
            //updateServerSocket!!.soTimeout = 2000  // alle 2s aufwachen

            val timeoutJob = launch {
                delay(durationMinutes * 60_000L)
                Log.d("TodoSync", "⏰ $durationMinutes Minuten abgelaufen")
                stopUpdateListener()
                withContext(Dispatchers.Main) {
                    stopClipboardSync(context)
                    showSimpleNotificationExtern(
                        "⏸️ Sync-Listener gestoppt",
                        "Nach $durationMinutes min automatisch beendet.",
                        15.seconds, context
                    )
                }
            }

            while (isActive) {
                try {
                    val client = updateServerSocket?.accept() ?: break
                    Log.d("TodoSync", "📥 Update empfangen von ${client.inetAddress}")

                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val jsonData = reader.readLine()
                    client.close()

                    if (jsonData != null) {
                        val updatedTodos = parseTodosFromJson(jsonData)
                        saveTodos(context, updatedTodos)

                        withContext(Dispatchers.Main) {
                            showSimpleNotificationExtern(
                                "🔄 To-dos aktualisiert",
                                "Änderungen vom Laptop empfangen",
                                10.seconds,
                                context
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e !is java.net.SocketException) {
                        Log.e("TodoSync", "Fehler beim Empfangen", e)
                    }
                }
            }

            timeoutJob.cancel()
        } catch (e: Exception) {
            if (e !is java.net.SocketException) {
                Log.e("TodoSync", "Update Listener Fehler", e)
            }
        }
    }
}

fun stopUpdateListener() {
    try {
        appContext?.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)?.edit {
            putBoolean(KEY_SYNC_ACTIVE, false)
        }
        isLaptopConnected = false
        wifiLock?.release(); wifiLock = null
        listenerJob?.cancel(); listenerJob = null
        updateServerSocket?.close(); updateServerSocket = null
        Log.d("TodoSync", "🛑 Update Listener gestoppt")
    } catch (e: Exception) {
        Log.e("TodoSync", "Fehler beim Schließen", e)
    }
}

private fun parseTodosFromJson(jsonData: String): List<TodoItem> =
    try {
        val jsonArray = org.json.JSONArray(jsonData)
        (0 until jsonArray.length()).map { i ->
            jsonArray.getJSONObject(i).run {
                TodoItem(
                    getLong("id"),
                    getString("text"),
                    getBoolean("completed"),
                    getLong("timestamp")
                )
            }
        }
    } catch (e: Exception) {
        Log.e("TodoSync", "Fehler beim Parsen", e)
        emptyList()
    }

private fun sendClipboardToLaptop(text: String) {
    syncScope.launch(Dispatchers.IO) {
        LAPTOP_IPS.forEach { ip ->
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(ip, CLIPBOARD_PORT), 3000)

                val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                writer.println("CLIPBOARD:$text")
                writer.flush()
                socket.close()

                Log.d("ClipboardSync", "📋 Zwischenablage an Laptop $ip gesendet")
            } catch (e: Exception) {
                Log.e("ClipboardSync", "Fehler beim Senden an $ip", e)
            }
        }
    }
}

private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

fun startClipboardSync(context: Context) {
    stopClipboardSync(context)
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty()) {
                sendClipboardToLaptop(text)
            }
        }
    }

    clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    Log.d("ClipboardSync", "📋 Clipboard Sync aktiviert")
}

fun stopClipboardSync(context: Context) {
    if (clipboardListener != null) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        clipboardListener = null
        Log.d("ClipboardSync", "📋 Clipboard Sync deaktiviert")
    }
}

private fun splitText(text: String): List<String> {
    val parts = mutableListOf<String>()
    var start = 0

    while (start < text.length) {
        val end = minOf(start + 200, text.length)
        parts.add(text.substring(start, end))
        start = end
    }
    return parts
}