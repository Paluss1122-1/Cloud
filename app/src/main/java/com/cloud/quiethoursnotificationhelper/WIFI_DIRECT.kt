package com.cloud.quiethoursnotificationhelper

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.wifi.WifiManager
import android.util.Log
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import com.cloud.service.MediaPlayerService
import com.cloud.mediaplayer.AlgorithmicPlaylistRegistry
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.cloud.Config
import com.cloud.Config.CLIPBOARD_PORT
import com.cloud.Config.FLASHCARD_RECEIVE_PORT
import com.cloud.Config.LAPTOP_IPS
import com.cloud.Config.SYNC_PORT
import com.cloud.Config.UPDATE_PORT
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.cloud.mediaplayer.ListenSession
import com.cloud.mediaplayer.MediaAnalyticsManager
import com.cloud.mediaplayer.MediaAnalyticsManager.getSessions
import com.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.cloud.service.WhatsAppNotificationListener
import com.cloud.showSimpleNotificationExtern
import com.cloud.vocabtab.Vokabel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.ServerSocket
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

val isLaptopConnectedFlow = MutableStateFlow(false)
val aiResponseFlow = MutableStateFlow<AiResponseEntry?>(null)
val flashcardVokabelnFlow = MutableStateFlow<List<Vokabel>?>(null)

var isLaptopConnected: Boolean
    get() = isLaptopConnectedFlow.value
    set(value) {
        isLaptopConnectedFlow.value = value
    }

private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private val mediaScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

private var listenerJob: Job? = null
private var updateServerSocket: ServerSocket? = null

private var triggerJob: Job? = null
private var triggerServerSocket: ServerSocket? = null

private var aiResponseJob: Job? = null
private var aiResponseServerSocket: ServerSocket? = null

private var flashcardResponseJob: Job? = null
private var flashcardResponseSocket: ServerSocket? = null

private var mediaCommandJob: Job? = null
private var mediaCommandSocket: ServerSocket? = null

private var mediaStateJob: Job? = null
private var mediaStateSocket: ServerSocket? = null

private var wifiLock: WifiManager.WifiLock? = null
private var appContext: Context? = null

private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
private var wifiReceiver: android.content.BroadcastReceiver? = null
private var triggerWakeLock: PowerManager.WakeLock? = null

private const val PREFS_SYNC = "sync_prefs"
private const val KEY_SYNC_ACTIVE = "sync_active"
private const val KEY_SYNC_UNTIL = "sync_until"
private var lastPushedState: String = ""

data class TodoItem(
    val id: Long,
    val text: String,
    val completed: Boolean,
    val timestamp: Long
)

data class AiResponseEntry(
    val text: String,
    val timestamp: Long,
    val dateKey: String
)

fun startTriggerListenerIfHomeWifi(context: Context) {
    checkIfNearLocation(context) { atHome ->
        if (atHome) {
            startTriggerListener(context)
            registerWifiReconnectReceiver(context)
            showSimpleNotificationExtern(
                "📶 WLAN verbunden",
                "✅ Im Heim-WLAN, Trigger Listener gestartet",
                10.seconds,
                context
            )
        }
    }
}

fun registerWifiReconnectReceiver(context: Context) {
    wifiReceiver?.let { context.unregisterReceiver(it) }
    val receiver = object : BroadcastReceiver() {
        @SuppressLint("Wakelock", "WakelockTimeout")
        override fun onReceive(ctx: Context, intent: Intent) {
            checkIfNearLocation(context) { atHome ->
                if (atHome) {
                    if (triggerWakeLock == null || triggerWakeLock?.isHeld == false) {
                        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                        triggerWakeLock = pm.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "TodoSync:TriggerWakeLock"
                        ).apply { acquire() }
                    }
                    if (!isLaptopConnected) {
                        syncScope.launch { syncTodosWithLaptop(ctx, LAPTOP_IPS) }
                    }
                } else {
                    triggerWakeLock?.release()
                    triggerWakeLock = null
                }
            }
        }
    }
    wifiReceiver = receiver
    val filter = android.content.IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
    context.registerReceiver(receiver, filter)
}

@SuppressLint("Wakelock", "WakelockTimeout")
fun startTriggerListener(context: Context) {
    triggerJob?.cancel()
    triggerServerSocket?.close()
    triggerServerSocket = null
    checkIfNearLocation(context) { atHome ->
        if (atHome) {
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            triggerWakeLock?.release()
            triggerWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TodoSync:TriggerWakeLock"
            ).apply { acquire() }
        } else {
            triggerWakeLock?.release()
            triggerWakeLock = null
        }
    }

    triggerJob = syncScope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                triggerServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(8893))
                }
                while (isActive) {
                    try {
                        val client = triggerServerSocket?.accept() ?: break
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val command = reader.readLine()
                        val clientIp = client.inetAddress.hostAddress
                        client.close()


                        if (command.startsWith("CONNECT")) {
                            val laptopIp = command.substringAfter("CONNECT:", "")
                            showSimpleNotificationExtern(
                                "📡 CONNECT empfangen",
                                "Starte Sync...",
                                10.seconds,
                                context
                            )
                            syncScope.launch {
                                syncTodosWithLaptop(context, listOf(laptopIp))
                            }
                        } else if (command == "REQUEST_SESSIONS") {
                            syncScope.launch {
                                sendSessionDataToLaptop(context)
                            }
                        } else if (command == "DISCONNECT") {
                            stopAllSyncServices(context)
                        }
                    } catch (_: java.net.SocketException) {
                        break
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                syncScope.launch {
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            service_name = "startTriggerListener",
                            error_message = e.stackTraceToString(),
                            created_at = Instant.now().toString(),
                            severity = "ERROR"
                        )
                    )
                }
            } finally {
                triggerServerSocket?.close()
                triggerServerSocket = null
            }
            if (isActive) delay(1000)
        }
    }
}

fun restoreSyncIfNeeded(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
    val syncActive = prefs.getBoolean(KEY_SYNC_ACTIVE, false)
    val syncUntil = prefs.getLong(KEY_SYNC_UNTIL, 0L)
    val remainingMs = syncUntil - System.currentTimeMillis()

    if (syncActive && remainingMs > 0) {
        val remainingMinutes = (remainingMs / 60_000L).toInt().coerceAtLeast(1)
        isLaptopConnected = true
        Log.d("NotifDebug1", "LS1 $isLaptopConnected")
        startUpdateListener(context, remainingMinutes)
        syncTodosWithLaptop(context)
        showSimpleNotificationExtern(
            "🔁 Sync wiederhergestellt",
            "Listener läuft noch $remainingMinutes min",
            10.seconds, context
        )
    }
}

fun stopAllSyncServices(context: Context) {
    stopUpdateListener(false)
    stopClipboardSync(context)
    mediaCommandJob?.cancel(); mediaCommandJob = null
    mediaCommandSocket?.close(); mediaCommandSocket = null
    mediaStateJob?.cancel(); mediaStateJob = null
    mediaStateSocket?.close(); mediaStateSocket = null
    aiResponseJob?.cancel(); aiResponseJob = null
    aiResponseServerSocket?.close(); aiResponseServerSocket = null
    flashcardResponseJob?.cancel(); flashcardResponseJob = null
    flashcardResponseSocket?.close(); flashcardResponseSocket = null
    wifiReceiver?.let {
        try {
            context.unregisterReceiver(it)
        } catch (_: Exception) {
        }
    }
    triggerWakeLock?.release(); triggerWakeLock = null
    wifiReceiver = null
    isLaptopConnected = false
    showSimpleNotificationExtern(
        "📴 Laptop getrennt",
        "Alle Sync-Services gestoppt",
        10.seconds,
        context
    )
    startTriggerListenerIfHomeWifi(context)
}

fun syncTodosWithLaptop(context: Context, ips: List<String> = LAPTOP_IPS) {
    showSimpleNotificationExtern(
        "🔄 Sync gestartet",
        "Verbinde mit Laptop...",
        10.seconds,
        context
    )

    startMediaCommandListener(context)
    startMediaStateServer(context)

    if (listenerJob == null || listenerJob?.isActive == false) {
        startUpdateListener(context, 60)
    }

    syncScope.launch {
        try {
            val todos = getTodos(context)
            val todosJson = JSONArray()

            todos.forEach { todo ->
                val obj = JSONObject().apply {
                    put("id", todo.id)
                    put("text", todo.text)
                    put("completed", todo.completed)
                    put("timestamp", todo.timestamp)
                }
                todosJson.put(obj)
            }

            var lastException: Exception? = null
            var connected = false

            for (ip in ips) {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ip, SYNC_PORT), 3000)

                    val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                    writer.println(todosJson.toString())
                    writer.flush()
                    socket.close()

                    connected = true
                    isLaptopConnected = true
                    Log.d("NotifDebug1", "LS2 $isLaptopConnected")
                    break
                } catch (_: ConnectException) {
                } catch (e: Exception) {
                    lastException = e
                    syncScope.launch {
                        ERRORINSERT(
                            ERRORINSERTDATA(
                                service_name = "syncTodosWithLaptop",
                                error_message = e.stackTraceToString(),
                                created_at = Instant.now().toString(),
                                severity = "ERROR"
                            )
                        )
                    }
                }
            }

            if (connected) {
                withContext(Dispatchers.Main) {
                    WhatsAppNotificationListener.forwardNotificationsToLaptop1()
                    showSimpleNotificationExtern(
                        "✅ Sync erfolgreich",
                        "${todos.size} To-dos übertragen\n🔄 Listener aktiv für 60min",
                        10.seconds, context
                    )
                }
            } else {
                throw lastException ?: Exception("Alle IPs fehlgeschlagen")
            }
            pushMediaStateToLaptop(context)
        } catch (e: Exception) {
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
    stopUpdateListener(true)
    appContext = context.applicationContext
    saveSyncState(context, durationMinutes)

    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    wifiLock =
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "TodoSync:WifiLock")
    wifiLock?.acquire()

    startClipboardSync(context)

    listenerJob = syncScope.launch(Dispatchers.IO) {
        try {
            updateServerSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(UPDATE_PORT))
            }

            val timeoutJob = launch {
                delay(durationMinutes * 60_000L)
                stopUpdateListener(false)
                isLaptopConnected = true
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
                    syncScope.launch {
                        ERRORINSERT(
                            ERRORINSERTDATA(
                                service_name = "startUpdateListener",
                                error_message = e.stackTraceToString(),
                                created_at = Instant.now().toString(),
                                severity = "ERROR"
                            )
                        )
                    }
                }
            }

            timeoutJob.cancel()
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "startUpdateListener",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
        }
    }
}

fun stopUpdateListener(boolean: Boolean = false) {
    try {
        appContext?.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)?.edit {
            putBoolean(KEY_SYNC_ACTIVE, false)
        }
        isLaptopConnected = boolean
        wifiLock?.release(); wifiLock = null
        listenerJob?.cancel(); listenerJob = null
        updateServerSocket?.close(); updateServerSocket = null
    } catch (e: Exception) {
        syncScope.launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    service_name = "stopUpdateListener",
                    error_message = e.stackTraceToString(),
                    created_at = Instant.now().toString(),
                    severity = "ERROR"
                )
            )
        }
    }
}

private fun saveSyncState(context: Context, durationMinutes: Int = 0) {
    context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE).edit {
        putBoolean(KEY_SYNC_ACTIVE, true)
        putLong(KEY_SYNC_UNTIL, System.currentTimeMillis() + durationMinutes * 60_000L)
    }
}

private fun sendSessionDataToLaptop(context: Context) {
    MediaAnalyticsManager.init(context)

    val lastAiTimestamp = loadTodayOrYesterdayEntry(context)?.timestamp ?: 0L

    val sessions = getSessions().filter { it.startedAt >= lastAiTimestamp }

    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, -2)
    val twoDaysAgoStart = cal.timeInMillis
    val previousSessions =
        getSessions().filter { it.startedAt in twoDaysAgoStart..<lastAiTimestamp }

    fun buildJsonArray(list: List<ListenSession>): JSONArray {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("label", s.label)
                put("type", s.type)
                put("listenedMs", s.listenedMs)
                put("startedAt", s.startedAt)
                put("repeatCount", s.repeatCount)
            })
        }
        return arr
    }

    val payload = JSONObject().apply {
        put("today", buildJsonArray(sessions))
        put("previous_2_days", buildJsonArray(previousSessions))
    }
    val jsonString = payload.toString()

    LAPTOP_IPS.forEach { ip ->
        try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ip, 8894), 3000)
                val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                writer.println(jsonString)
                writer.flush()
                socket.close()
            }
            return
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "sendSessionDataToLaptop",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
        }
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
        val jsonArray = JSONArray()
        todos.forEach { todo ->
            val obj = JSONObject().apply {
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
        syncScope.launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    service_name = "saveTodos",
                    error_message = e.stackTraceToString(),
                    created_at = Instant.now().toString(),
                    severity = "ERROR"
                )
            )
        }
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
        syncScope.launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    service_name = "parseTodosFromJson",
                    error_message = e.stackTraceToString(),
                    created_at = Instant.now().toString(),
                    severity = "ERROR"
                )
            )
        }
        emptyList()
    }

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
}

fun stopClipboardSync(context: Context) {
    if (clipboardListener != null) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        clipboardListener = null
    }
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
            } catch (e: Exception) {
                syncScope.launch {
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            service_name = "sendClipboardtoLaptop",
                            error_message = e.stackTraceToString(),
                            created_at = Instant.now().toString(),
                            severity = "ERROR"
                        )
                    )
                }
            }
        }
    }
}

fun startAiResponseListener(context: Context) {
    aiResponseJob?.cancel()
    aiResponseServerSocket?.close()

    syncScope.launch {
        val existing = loadTodayOrYesterdayEntry(context)
        if (existing != null) aiResponseFlow.emit(existing)
    }

    aiResponseJob = syncScope.launch(Dispatchers.IO) {
        try {
            aiResponseServerSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(8895))
            }
            while (isActive) {
                try {
                    val client = aiResponseServerSocket?.accept() ?: break
                    val bytes = client.inputStream.readBytes()
                    client.close()

                    val text = bytes.toString(Charsets.UTF_8)

                    saveAiResponse(context, text)

                    val entry = AiResponseEntry(
                        text = text,
                        timestamp = System.currentTimeMillis(),
                        dateKey = getTodayKey()
                    )
                    aiResponseFlow.emit(entry)

                    showSimpleNotificationExtern(
                        "🤖 AI Antwort",
                        text.take(100),
                        30.seconds,
                        context
                    )
                } catch (_: java.net.SocketException) {
                    break
                } catch (e: Exception) {
                    syncScope.launch {
                        ERRORINSERT(
                            ERRORINSERTDATA(
                                service_name = "startAiREsponseListener",
                                error_message = e.stackTraceToString(),
                                created_at = Instant.now().toString(),
                                severity = "ERROR"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "startAiResponseListener",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
        }
    }
}

fun saveAiResponse(context: Context, text: String) {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val dateKey = getTodayKey()
    val timestamp = System.currentTimeMillis()

    val allJson = prefs.getString("all_entries", "[]") ?: "[]"
    val arr = org.json.JSONArray(allJson)

    val obj = JSONObject().apply {
        put("text", text)
        put("timestamp", timestamp)
        put("dateKey", dateKey)
    }
    arr.put(obj)

    prefs.edit {
        putString("all_entries", arr.toString())
        putString("entry_$dateKey", text)
        putLong("timestamp_$dateKey", timestamp)
    }
}

fun deleteAiResponse(context: Context, timestamp: Long) {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val allJson = prefs.getString("all_entries", "[]") ?: "[]"
    val arr = org.json.JSONArray(allJson)
    val newArr = JSONArray()

    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        if (obj.getLong("timestamp") != timestamp) {
            newArr.put(obj)
        }
    }

    prefs.edit {
        putString("all_entries", newArr.toString())
        val dateKey = arr.let {
            for (i in 0 until it.length()) {
                val obj = it.getJSONObject(i)
                if (obj.getLong("timestamp") == timestamp)
                    return@let obj.getString("dateKey")
            }
            null
        }
        if (dateKey != null) {
            val stillExists = (0 until newArr.length()).any {
                newArr.getJSONObject(it).getString("dateKey") == dateKey
            }
            if (!stillExists) {
                remove("entry_$dateKey")
                remove("timestamp_$dateKey")
            } else {
                val latest = (0 until newArr.length())
                    .map { newArr.getJSONObject(it) }
                    .filter { it.getString("dateKey") == dateKey }
                    .maxByOrNull { it.getLong("timestamp") }
                if (latest != null) {
                    putString("entry_$dateKey", latest.getString("text"))
                    putLong("timestamp_$dateKey", latest.getLong("timestamp"))
                }
            }
        }
    }
}

fun loadTodayOrYesterdayEntry(context: Context): AiResponseEntry? {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val todayKey = getTodayKey()
    val yesterdayKey = getYesterdayKey()

    val todayText = prefs.getString("entry_$todayKey", null)
    if (todayText != null) {
        return AiResponseEntry(
            text = todayText,
            timestamp = prefs.getLong("timestamp_$todayKey", 0L),
            dateKey = todayKey
        )
    }

    val yesterdayText = prefs.getString("entry_$yesterdayKey", null)
    if (yesterdayText != null) {
        return AiResponseEntry(
            text = yesterdayText,
            timestamp = prefs.getLong("timestamp_$yesterdayKey", 0L),
            dateKey = yesterdayKey
        )
    }

    return null
}

fun loadAllAiResponses(context: Context): List<AiResponseEntry> {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val allJson = prefs.getString("all_entries", "[]") ?: "[]"
    return try {
        val arr = org.json.JSONArray(allJson)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).run {
                AiResponseEntry(
                    text = getString("text"),
                    timestamp = getLong("timestamp"),
                    dateKey = getString("dateKey")
                )
            }
        }.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        syncScope.launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    service_name = "loadAllAiResponses",
                    error_message = e.stackTraceToString(),
                    created_at = Instant.now().toString(),
                    severity = "ERROR"
                )
            )
        }
        emptyList()
    }
}

private fun getTodayKey(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY)
    return sdf.format(java.util.Date())
}

private fun getYesterdayKey(): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY)
    return sdf.format(cal.time)
}

suspend fun trySendOcrToLaptop(ocrRawText: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val apiKey = Config.NVIDIA
            val model = "meta/llama-3.1-8b-instruct"
            val prompt = """
                Du bekommst einen rohen OCR-Text aus einem Lateinvokabelbuch (3 Spalten: Latein | Deutsch | Wortverwandtschaften).
                Extrahiere alle Lateinisch-Deutsch-Vokabelpaare und gib sie als JSON-Array zurück.
                Behalte im Hinterkopf das es Latein Vokabeln sind für die Formatierung.
                Regeln:
                - Ignoriere Überschriften (z.B. "Lernwörter"), Seitenzahlen und die dritte Spalte
                - Mehrzeilige Lateineinträge (Verbformen) mit Komma zusammenfassen
                - Klammerausdrücke wie "(ā m. Abl.)" beim Latein behalten, italic-Zusätze bei Deutsch weglassen
                - Antworte NUR mit dem JSON-Array, kein Text davor oder danach, keine Markdown-Backticks
                Format: [{"latein": "...", "deutsch": "..."}, ...]
                OCR-Text:
                $ocrRawText
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.2)
                put("max_tokens", 4096)
                put("stream", false)
            }

            val url = java.net.URL("https://integrate.api.nvidia.com/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use {
                it.write(
                    requestBody.toString().toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) return@withContext false

            val raw = connection.inputStream.bufferedReader().readText()
            val content = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            val cleaned = if (content.startsWith("```")) {
                content.substringAfter("\n").substringBeforeLast("```").trim()
            } else content

            val vokabeln = parseVokabelnFromJson(cleaned)
            flashcardVokabelnFlow.emit(vokabeln.ifEmpty { null })
            true
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "trySendOcrToLaptop",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
            flashcardVokabelnFlow.emit(null)
            false
        }
    }
}

private suspend fun bindFlashcardResponseSocket(): ServerSocket? {
    return withContext(Dispatchers.IO) {
        try {
            flashcardResponseSocket?.close()
            ServerSocket().apply {
                reuseAddress = true
                soTimeout = 600_000
                bind(java.net.InetSocketAddress(FLASHCARD_RECEIVE_PORT))
            }.also { flashcardResponseSocket = it }
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "bindFlashcardResponseSocket",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
        }
        null
    }
}

private fun startFlashcardResponseListener(boundSocket: ServerSocket) {
    flashcardResponseJob?.cancel()
    flashcardVokabelnFlow.value = null

    flashcardResponseJob = syncScope.launch(Dispatchers.IO) {
        try {
            val client = boundSocket.accept()
            val bytes = client.inputStream.readBytes()
            client.close()

            val json = bytes.toString(Charsets.UTF_8)
            val vokabeln = parseVokabelnFromJson(json)
            flashcardVokabelnFlow.emit(vokabeln.ifEmpty { null })
        } catch (_: java.net.SocketTimeoutException) {
            flashcardVokabelnFlow.emit(null)
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "startFlashcardResponseListener",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
            flashcardVokabelnFlow.emit(null)
        } finally {
            flashcardResponseSocket?.close()
            flashcardResponseSocket = null
        }
    }
}

private fun parseVokabelnFromJson(json: String): List<Vokabel> = try {
    val arr = org.json.JSONArray(json)
    (0 until arr.length()).map { i ->
        arr.getJSONObject(i).run {
            Vokabel(getString("latein"), getString("deutsch"))
        }
    }
} catch (e: Exception) {
    syncScope.launch {
        ERRORINSERT(
            ERRORINSERTDATA(
                service_name = "parseVokabelnFromJson",
                error_message = e.stackTraceToString(),
                created_at = Instant.now().toString(),
                severity = "ERROR"
            )
        )
    }
    emptyList()
}

// Media

fun startMediaCommandListener(context: Context) {
    if (mediaCommandJob?.isActive == true && mediaCommandSocket?.isClosed == false) return
    mediaCommandSocket?.close()

    mediaCommandJob = mediaScope.launch {
        while (isActive) {
            try {
                mediaCommandSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(8899))
                }

                while (isActive) {
                    try {
                        val client = mediaCommandSocket?.accept() ?: break
                        mediaScope.launch {
                            try {
                                val reader =
                                    BufferedReader(InputStreamReader(client.getInputStream()))
                                val sb = StringBuilder()
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    sb.append(line)
                                }
                                client.close()

                                val json = JSONObject(sb.toString())
                                handleMediaCommand(context, json)
                            } catch (e: Exception) {
                                syncScope.launch {
                                    ERRORINSERT(
                                        ERRORINSERTDATA(
                                            service_name = "startMediaCommandListener",
                                            error_message = e.stackTraceToString(),
                                            created_at = Instant.now().toString(),
                                            severity = "ERROR"
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: java.net.SocketException) {
                        syncScope.launch {
                            ERRORINSERT(
                                ERRORINSERTDATA(
                                    service_name = "startMediaCommandListener",
                                    error_message = e.stackTraceToString(),
                                    created_at = Instant.now().toString(),
                                    severity = "ERROR"
                                )
                            )
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                syncScope.launch {
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            service_name = "startMediaCommandListener",
                            error_message = e.stackTraceToString(),
                            created_at = Instant.now().toString(),
                            severity = "ERROR"
                        )
                    )
                }
            } finally {
                mediaCommandSocket?.close()
                mediaCommandSocket = null
            }
            if (isActive) delay(2000)
        }
    }
}

suspend fun sendNvidiaChatMessage(
    history: List<WhatsAppNotificationListener.Companion.ChatMessage>,
    userMessage: String
): String? {
    return withContext(Dispatchers.IO) {
        try {
            val apiKey = Config.NVIDIA
            val model = "meta/llama-3.1-8b-instruct"

            val messagesJson = JSONArray()

            messagesJson.put(
                JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        "Du bist ein hilfreicher Chat-Assistent. Antworte kurz, klar und auf Deutsch."
                    )
                }
            )

            history.forEach { msg ->
                val role = if (msg.isOwnMessage) "user" else "assistant"
                messagesJson.put(
                    JSONObject().apply {
                        put("role", role)
                        put("content", msg.text)
                    }
                )
            }

            messagesJson.put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                }
            )

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesJson)
                put("temperature", 0.3)
                put("max_tokens", 1024)
                put("stream", false)
            }

            val url = java.net.URL("https://integrate.api.nvidia.com/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use {
                it.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return@withContext null
            }

            val raw = connection.inputStream.bufferedReader().readText()
            val content = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            content.ifBlank { null }
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "sendNvidiaChatMessage",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
            null
        }
    }
}

private fun handleMediaCommand(context: Context, json: JSONObject) {
    val action = json.optString("action", "")

    fun sendIntent(intentAction: String, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(context, MediaPlayerService::class.java).apply {
            this.action = intentAction
            extras?.invoke(this)
        }
        context.startService(intent)
    }

    when (action) {
        "togglePlayPause" -> {
            val prefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)
            val isPlaying = prefs.getBoolean("is_playing", false)
            val mode = prefs.getString("current_mode", "music") ?: "music"
            if (isPlaying) {
                val pauseAction = if (mode == "music")
                    "com.cloud.ACTION_MUSIC_PAUSE"
                else
                    "com.cloud.ACTION_PODCAST_PAUSE"
                sendIntent(pauseAction)
            } else {
                if (mode == "music") MediaPlayerService.sendMusicPlayAction(context)
                else MediaPlayerService.sendPodcastPlayAction(context)
            }
        }

        "play" -> MediaPlayerService.sendMusicPlayAction(context)
        "pause" -> sendIntent("com.cloud.ACTION_MUSIC_PAUSE")
        "next" -> sendIntent("com.cloud.ACTION_MUSIC_NEXT")
        "previous" -> sendIntent("com.cloud.ACTION_MUSIC_PREVIOUS")

        "toggleRepeat" -> sendIntent("com.cloud.ACTION_TOGGLE_REPEAT")
        "toggleFavorite" -> sendIntent("com.cloud.ACTION_TOGGLE_FAVORITE")

        "playSongAtIndex" -> {
            val index = json.optInt("index", 0)
            MediaPlayerService.playFromAllSongs(context, index)
        }

        "activatePlaylist" -> {
            val playlistId = json.optString("playlistId", "")
            val songIndex = json.optInt("index", 0)
            if (playlistId.isNotEmpty()) {
                sendIntent("com.cloud.ACTION_ACTIVATE_PLAYLIST") {
                    putExtra("PLAYLIST_ID", playlistId)
                    putExtra(MediaPlayerService.EXTRA_SONG_INDEX, songIndex)
                }
            }
        }

        "activateAlgorithmicPlaylist" -> {
            val playlistId = json.optString("playlistId", "")
            val songIndex = json.optInt("index", 0)
            if (playlistId.isNotEmpty()) {
                MediaPlayerService.activateAlgorithmicPlaylist(context, playlistId, songIndex)
            }
        }

        "seek" -> {
            val posMs = json.optLong("positionMs", 0L)
            sendIntent("com.cloud.ACTION_SEEK") {
                putExtra("SEEK_POSITION_MS", posMs)
            }
        }

        else -> Log.w("MEDIA_CMD", "Unbekannte Aktion: $action")
    }
}

fun startMediaStateServer(context: Context) {
    if (mediaStateJob?.isActive == true) return  // Already running
    mediaStateSocket?.close()

    mediaStateJob = mediaScope.launch {
        while (isActive) {
            try {
                if (mediaStateSocket != null) mediaStateSocket?.close()
                mediaStateSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(8900))
                }
                Log.d("MEDIA_STATE", "🎵 Media State Server gestartet (Port 8900)")

                while (isActive) {
                    try {
                        val client = mediaStateSocket?.accept() ?: break
                        mediaScope.launch {
                            try {
                                val reader =
                                    BufferedReader(InputStreamReader(client.getInputStream()))
                                val command = reader.readLine()?.trim() ?: ""

                                val response = when (command) {
                                    "GET_MEDIA_STATE" -> buildMediaStateJson(context)
                                    "GET_PLAYLISTS" -> buildPlaylistsJson(context)
                                    else -> "{}"
                                }

                                val out = client.getOutputStream()
                                out.write(response.toByteArray(Charsets.UTF_8))
                                out.flush()
                                client.close()

                                Log.d("MEDIA_STATE", "📤 Antwort gesendet für: $command")
                            } catch (e: Exception) {
                                syncScope.launch {
                                    ERRORINSERT(
                                        ERRORINSERTDATA(
                                            service_name = "startMediaStateServer",
                                            error_message = e.stackTraceToString(),
                                            created_at = Instant.now().toString(),
                                            severity = "ERROR"
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: java.net.SocketException) {
                        syncScope.launch {
                            ERRORINSERT(
                                ERRORINSERTDATA(
                                    service_name = "startMediaStateServer",
                                    error_message = e.stackTraceToString(),
                                    created_at = Instant.now().toString(),
                                    severity = "ERROR"
                                )
                            )
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                syncScope.launch {
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            service_name = "startMediaStateServer",
                            error_message = e.stackTraceToString(),
                            created_at = Instant.now().toString(),
                            severity = "ERROR"
                        )
                    )
                }
            } finally {
                mediaStateSocket?.close()
                mediaStateSocket = null
            }
            if (isActive) delay(2000)
        }
    }
}

private fun buildMediaStateJson(context: Context): String {
    val musicPrefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)
    val isServiceRunning = MediaPlayerService.isServiceActive()

    if (!isServiceRunning) {
        return JSONObject().apply {
            put("mode", "music")
            put("is_playing", false)
            put("song_name", "")
            put("title", "Spielt nichts")
            put("playlist_name", "")
            put("song_index", 0)
            put("is_favorite", false)
            put("active_playlist_id", "")
            put("active_algo_playlist_id", "")
            put("position_ms", 0)
            put("duration_ms", 0)
        }.toString()
    }
    val podcastPrefs = context.getSharedPreferences("podcast_player_prefs", MODE_PRIVATE)

    val mode = musicPrefs.getString("current_mode", "music") ?: "music"
    val isPlaying = musicPrefs.getBoolean("is_playing", false)

    val songName: String
    val playlistName: String
    val songIndex: Int
    val isFavorite: Boolean
    val activePlaylistId: String
    val activeAlgoPlaylistId: String
    val positionMs: Long
    val durationMs: Long

    if (mode == "music") {
        songName = musicPrefs.getString("current_song_name", "") ?: ""
        val activePl = musicPrefs.getString("active_playlist_id", "") ?: ""
        val activeAlgoPl = musicPrefs.getString("active_algorithmic_playlist_id", "") ?: ""
        songIndex = musicPrefs.getInt("current_song_index", 0)
        val favorites = musicPrefs.getString("favorite_songs", null)
            ?.split("|||")
            ?.mapNotNull { entry ->
                val parts = entry.split(":::")
                if (parts.isNotEmpty()) parts[0] else null
            }
            ?.toSet() ?: emptySet()
        isFavorite = favorites.contains(songName)
        activePlaylistId = activePl
        activeAlgoPlaylistId = activeAlgoPl
        positionMs = 0L
        durationMs = 0L
        playlistName = when {
            activeAlgoPl.isNotEmpty() -> activeAlgoPl
            activePl.isNotEmpty() -> activePl
            else -> "Alle Songs"
        }
    } else {
        val path = podcastPrefs.getString("current_podcast_path", null)
        songName = path?.substringAfterLast("/")?.substringBeforeLast(".") ?: ""
        positionMs = if (path != null) podcastPrefs.getLong(
            "podcast_position_${path.hashCode()}",
            0L
        ) else 0L
        durationMs = 0L
        playlistName = "Podcast"
        songIndex = 0
        isFavorite = false
        activePlaylistId = ""
        activeAlgoPlaylistId = ""
    }

    return JSONObject().apply {
        put("mode", mode)
        put("is_playing", isPlaying)
        put("song_name", songName)
        put("title", songName)
        put("playlist_name", playlistName)
        put("song_index", songIndex)
        put("is_favorite", isFavorite)
        put("active_playlist_id", activePlaylistId)
        put("active_algo_playlist_id", activeAlgoPlaylistId)
        put("position_ms", positionMs)
        put("duration_ms", durationMs)
    }.toString()
}

private fun buildPlaylistsJson(context: Context): String {
    val musicPrefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)

    // Normal Playlists
    val playlistsJson = JSONArray()
    try {
        val raw = musicPrefs.getString("playlists_json", null)
        raw?.split("\n---\n")?.filter { it.isNotBlank() }?.forEach { line ->
            val parts = line.split(":::", limit = 4)
            if (parts.size >= 3) {
                val items = if (parts.size > 3 && parts[3].isNotEmpty())
                    parts[3].split("|~~|") else emptyList()
                playlistsJson.put(JSONObject().apply {
                    put("id", parts[0])
                    put("name", parts[1])
                    put("type", parts[2])
                    put("song_count", items.size)
                })
            }
        }
    } catch (e: Exception) {
        syncScope.launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    service_name = "buildPlaylistsJson",
                    error_message = e.stackTraceToString(),
                    created_at = Instant.now().toString(),
                    severity = "ERROR"
                )
            )
        }
    }

    // Algorithmic Playlists
    val algoJson = JSONArray()
    AlgorithmicPlaylistRegistry.all.forEach { source ->
        algoJson.put(JSONObject().apply {
            put("id", source.id)
            put("name", source.name)
            put("description", source.description)
            put("icon", source.icon)
        })
    }

    // All Songs (only names + index)
    val songsJson = JSONArray()
    try {
        val cr = context.contentResolver
        val proj = arrayOf(
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.TITLE
        )
        var index = 0
        cr.query(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            proj, null, null,
            "${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameCol =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
            val titleCol =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val data = cursor.getString(dataCol) ?: continue
                val norm = data.lowercase()
                val inCloud = norm.contains("/download/cloud/") ||
                        norm.contains("/downloads/cloud/") ||
                        data.contains("/Cloud/", ignoreCase = true)
                if (inCloud && !norm.contains("/podcast")) {
                    val name = cursor.getString(nameCol) ?: continue
                    val title = cursor.getString(titleCol)
                    val displayName = if (!title.isNullOrBlank() && title != "<unknown>") title
                    else name.substringBeforeLast('.')
                    songsJson.put(JSONObject().apply {
                        put("index", index++)
                        put("name", displayName)
                    })
                }
            }
        }
    } catch (e: Exception) {
        syncScope.launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    service_name = "buildPlaylistsJson",
                    error_message = e.stackTraceToString(),
                    created_at = Instant.now().toString(),
                    severity = "ERROR"
                )
            )
        }
    }

    return JSONObject().apply {
        put("playlists", playlistsJson)
        put("algo_playlists", algoJson)
        put("songs", songsJson)
    }.toString()
}

fun pushMediaStateToLaptop(context: Context) {
    if (!isLaptopConnected) return
    val state = buildMediaStateJson(context)
    if (state == lastPushedState) return
    lastPushedState = state
    syncScope.launch(Dispatchers.IO) {
        LAPTOP_IPS.forEach { ip ->
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(ip, 8901), 2000)
                socket.getOutputStream().apply {
                    write(state.toByteArray(Charsets.UTF_8))
                    flush()
                }
                socket.close()
                return@launch
            } catch (e: Exception) {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "pushMediaStateToLaptop",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
        }
    }
}

private var discoveryJob: Job? = null

fun startDiscoveryListener() {
    discoveryJob?.cancel()
    discoveryJob = syncScope.launch(Dispatchers.IO) {
        try {
            val socket = java.net.DatagramSocket(8892, java.net.InetAddress.getByName("0.0.0.0"))
            socket.broadcast = true

            val buf = ByteArray(1024)

            while (isActive) {
                val packet = java.net.DatagramPacket(buf, buf.size)
                socket.receive(packet)

                val message = String(packet.data, 0, packet.length)

                if (message == "DISCOVER_PHONE") {
                    val response = "PHONE_HERE"
                    val responsePacket = java.net.DatagramPacket(
                        response.toByteArray(),
                        response.length,
                        packet.address,
                        packet.port
                    )
                    socket.send(responsePacket)
                }
            }
        } catch (e: Exception) {
            syncScope.launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "startDiscoveryListener",
                        error_message = e.stackTraceToString(),
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            }
        }
    }
}

/*suspend fun trySendOcrToLaptop(context: Context, ocrRawText: String): Boolean {
    return withContext(Dispatchers.IO) {
        val socket = bindFlashcardResponseSocket() ?: return@withContext false

        startFlashcardResponseListener(socket)

        for (ip in LAPTOP_IPS) {
            try {
                val s = java.net.Socket()
                s.connect(java.net.InetSocketAddress(ip, FLASHCARD_SEND_PORT), 3000)
                s.getOutputStream().write(ocrRawText.toByteArray(Charsets.UTF_8))
                s.shutdownOutput()
                s.close()
                return@withContext true
            } catch (_: Exception) {}
        }

        socket.close()
        flashcardResponseSocket = null
        false
    }
}*/

fun checkIfNearLocation(
    context: Context,
    targetLat: Double = Config.LAT,
    targetLon: Double = Config.LON,
    radiusMeters: Float = 150f,
    callback: (Boolean) -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) {
        callback(false)
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
        .setMinUpdateIntervalMillis(500L)
        .setMaxUpdateDelayMillis(2000L)
        .build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                Log.d("CLOUD", "$location")
                val distance = distanceBetween(
                    location.latitude, location.longitude,
                    targetLat, targetLon
                )
                callback(distance <= radiusMeters)
            } else {
                callback(false)
            }
            fusedLocationClient.removeLocationUpdates(this) // nur einmal prüfen
        }
    }

    @SuppressLint("MissingPermission")
    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
}

// Hilfsfunktion: Distanz zwischen zwei GPS-Koordinaten in Metern
fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371000.0 // in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(
            2.0
        )
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).toFloat()
}