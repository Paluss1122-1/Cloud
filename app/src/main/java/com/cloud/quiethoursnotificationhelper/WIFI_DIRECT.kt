package com.cloud.quiethoursnotificationhelper

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cloud.core.functions.ERRORINSERTDATA
import com.cloud.core.functions.errorInsert
import com.cloud.core.functions.showSimpleNotificationExtern
import com.cloud.core.objects.Config
import com.cloud.core.objects.Config.FLASHCARD_RECEIVE_PORT
import com.cloud.core.objects.Config.SYNC_PORT
import com.cloud.core.objects.Config.TODOS
import com.cloud.core.objects.Config.UPDATE_PORT
import com.cloud.core.ui.Cloud
import com.cloud.services.MediaPlayerService
import com.cloud.services.OverlayLifecycleOwner
import com.cloud.services.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.cloud.services.WhatsAppNotificationListener
import com.cloud.tabs.AlgorithmicPlaylistRegistry
import com.cloud.tabs.ListenSession
import com.cloud.tabs.MediaAnalyticsManager
import com.cloud.tabs.MediaAnalyticsManager.getSessions
import com.cloud.tabs.Vokabel
import com.cloud.tabs.aitab.ChatMessage
import com.cloud.tabs.authenticator.PasswordDatabase
import com.cloud.tabs.authenticator.TotpGenerator.generateTOTP
import com.cloud.tabs.authenticator.TwoFADatabase
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.BindException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

val isLaptopConnectedFlow = MutableStateFlow(false)
val aiResponseFlow = MutableStateFlow<AiResponseEntry?>(null)
val flashcardVokabelnFlow = MutableStateFlow<List<Vokabel>?>(null)

private val serverMutexes = mutableMapOf<Int, kotlinx.coroutines.sync.Mutex>()

private fun getServerMutex(port: Int) = synchronized(serverMutexes) {
    serverMutexes.getOrPut(port) { kotlinx.coroutines.sync.Mutex() }
}

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
private var mediaStateJob: Job? = null
private var clipboardJob: Job? = null
private var mailNotifyJob: Job? = null
private var executeJob: Job? = null

private val activeServers = mutableListOf<ServerSocket>()

private var cpuWakeLock: PowerManager.WakeLock? = null
private var appContext: Context? = null

private const val PREFS_SYNC = "sync_prefs"
private const val KEY_SYNC_ACTIVE = "sync_active"
private const val KEY_SYNC_UNTIL = "sync_until"
private var lastPushedState: String = ""

private var networkCallback: ConnectivityManager.NetworkCallback? = null
private var pendingSyncJob: Job? = null
private var lastTriggerTime = 0L
private const val MIN_TRIGGER_INTERVAL = 15_000L

@Volatile
private var syncInProgress = false

private fun PowerManager.WakeLock?.safeRelease() {
    if (this != null && isHeld) release()
}

private fun logError(service: String, e: Exception) {
    syncScope.launch {
        errorInsert(
            ERRORINSERTDATA(
                service,
                e.stackTraceToString(),
                Instant.now().toString(),
                "ERROR"
            )
        )
    }
}

private fun launchServer(
    scope: CoroutineScope,
    port: Int,
    errorTag: String,
    handler: suspend CoroutineScope.(Socket) -> Unit
): Job = scope.launch(Dispatchers.IO) {
    val mutex = getServerMutex(port)

    mutex.withLock {
        while (isActive) {
            var server: ServerSocket? = null
            try {
                synchronized(activeServers) {
                    activeServers.find {
                        runCatching { it.localPort }.getOrNull() == port
                    }?.let {
                        activeServers.remove(it)
                        runCatching { it.close() }
                    }
                }

                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                    soTimeout = 100 // Add timeout to allow clean shutdown
                }
                synchronized(activeServers) { activeServers.add(server) }

                while (isActive) {
                    try {
                        val client = server.accept()
                        scope.launch {
                            try {
                                handler(client)
                            } catch (e: Exception) {
                                logError(errorTag, e)
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Normal timeout, check isActive and continue
                        continue
                    } catch (_: SocketException) {
                        // Socket closed, exit inner loop
                        break
                    }
                }
            } catch (e: BindException) {
                // Port still in use, wait longer before retry
                logError("$errorTag-bind", e)
                delay(5000)
            } catch (e: Exception) {
                logError(errorTag, e)
            } finally {
                server?.let {
                    synchronized(activeServers) { activeServers.remove(it) }
                    runCatching { it.close() }
                }
            }
            if (isActive) delay(2000)
        }
    }
}

private fun todosToJsonArray(todos: List<TodoItem>): JSONArray = JSONArray().apply {
    todos.forEach { todo ->
        put(JSONObject().apply {
            put("id", todo.id)
            put("text", todo.text)
            put("completed", todo.completed)
            put("timestamp", todo.timestamp)
        })
    }
}

private suspend fun callNvidiaApi(model: String, messagesJson: JSONArray): String? =
    withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesJson)
                put("temperature", 0.3)
                put("max_tokens", 1024)
                put("stream", false)
            }
            val connection = (URL("https://integrate.api.nvidia.com/v1/chat/completions")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer ${Config.NVIDIA}")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            connection.outputStream.use {
                it.write(
                    requestBody.toString().toByteArray(Charsets.UTF_8)
                )
            }
            if (connection.responseCode != 200) return@withContext null
            JSONObject(connection.inputStream.bufferedReader().readText())
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
                .ifBlank { null }
        } catch (e: Exception) {
            logError("callNvidiaApi", e)
            null
        }
    }

private object ConnectionGuard {
    private var lastFailedAttempt: Long = 0L
    private var consecutiveFailures: Int = 0
    private var lastSuccessfulConnection: Long = 0L
    private var isWifiConnected: Boolean = false
    private var isAtHomeLocation: Boolean = false

    fun updateWifiStatus(connected: Boolean) {
        isWifiConnected = connected
        if (!connected) consecutiveFailures = 0
    }

    fun updateLocationStatus(atHome: Boolean) {
        isAtHomeLocation = atHome
        if (!atHome) consecutiveFailures = 0
    }

    fun canAttemptConnection(): Boolean {
        return !(!isWifiConnected && !isAtHomeLocation)
    }

    fun recordFailure() {
        lastFailedAttempt = System.currentTimeMillis()
        consecutiveFailures++
    }

    fun recordSuccess() {
        lastSuccessfulConnection = System.currentTimeMillis()
        consecutiveFailures = 0
        lastFailedAttempt = 0L
    }

    fun quickPingTest(ip: String, port: Int): Boolean {
        return try {
            val addr = Inet4Address.getByName(ip)  // forces IPv4
            Socket().use { socket ->
                socket.connect(InetSocketAddress(addr, port), 1500)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

private const val KEY_LAPTOP_IP = "laptop_ip"

var laptopIp: String
    get() = appContext?.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
        ?.getString(KEY_LAPTOP_IP, "") ?: ""
    set(value) {
        appContext?.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)?.edit {
            putString(KEY_LAPTOP_IP, value)
        }
    }

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
        ConnectionGuard.updateLocationStatus(atHome)
        syncScope.launch {
            laptopIp = fetchLaptopIpFromSupabase() ?: ""
        }
        startTriggerListener(context)
        registerWifiReconnectReceiver(context)
    }
}

fun registerWifiReconnectReceiver(context: Context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    networkCallback?.let {
        try {
            cm.unregisterNetworkCallback(it)
        } catch (_: Exception) {
        }
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            ConnectionGuard.updateWifiStatus(true)
            fun getLocalIp(): String {
                return try {
                    NetworkInterface.getNetworkInterfaces()
                        ?.asSequence()
                        ?.flatMap { it.inetAddresses.asSequence() }
                        ?.firstOrNull { addr ->
                            !addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false
                        }
                        ?.hostAddress ?: "Unbekannt"
                } catch (e: Exception) {
                    Log.e("CLOUDSA", "[NET] IP-Fehler", e)
                    "Unbekannt"
                }
            }

            val localIp = getLocalIp()
            if (localIp != "Unbekannt") {
                syncScope.launch { insertMobileIpToSupabase(localIp) }
            }

            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < MIN_TRIGGER_INTERVAL) return
            lastTriggerTime = now
            checkIfNearLocation(context) { atHome ->
                ConnectionGuard.updateLocationStatus(atHome)
                if (!atHome) {
                    return@checkIfNearLocation
                }
                if (ConnectionGuard.canAttemptConnection() && !isLaptopConnected) {
                    syncTodosWithLaptop(context)
                }
            }
        }

        override fun onLost(network: Network) {
            ConnectionGuard.updateWifiStatus(false)
            syncInProgress = false
            pendingSyncJob?.cancel()
            stopAllSyncServices(context)
        }

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties
        ) {
            Log.d("CLOUDSA", "${linkProperties.linkAddresses}")
            val localIp = linkProperties.linkAddresses
                .map { it.address.hostAddress }
                .firstOrNull { ip ->
                    ip != null && (ip.startsWith("192.168.") || ip.startsWith("10."))
                }

            if (localIp != null) {
                syncScope.launch {
                    insertMobileIpToSupabase(localIp)
                }
            }
        }
    }

    networkCallback = callback

    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    cm.registerNetworkCallback(request, callback)
}

@SuppressLint("Wakelock", "WakelockTimeout")
fun startTriggerListener(context: Context) {
    appContext = context.applicationContext

    triggerJob?.cancel()
    triggerServerSocket?.close()
    triggerServerSocket = null

    triggerJob = syncScope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                triggerServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(Config.TRIGGER_PORT))
                }

                while (isActive) {
                    try {
                        val client = triggerServerSocket?.accept() ?: break
                        val pm =
                            context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                        val wl = pm.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "TodoSync:AcceptWakeLock"
                        )
                        wl.acquire(30_000L)

                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val command = reader.readLine()
                            client.close()

                            when {
                                command.startsWith("CONNECT") -> {
                                    laptopIp = command.substringAfter("CONNECT:", "")
                                    ConnectionGuard.recordSuccess()

                                    showSimpleNotificationExtern(
                                        "📡 CONNECT empfangen",
                                        "Starte Sync...",
                                        10.seconds,
                                        context
                                    )

                                    val syncWl = pm.newWakeLock(
                                        PowerManager.PARTIAL_WAKE_LOCK,
                                        "TodoSync:SyncWakeLock"
                                    )
                                    syncWl.acquire(60_000L)

                                    syncScope.launch {
                                        try {
                                            syncTodosWithLaptop(context)
                                        } finally {
                                            syncWl.release()
                                        }
                                    }
                                }

                                command == "REQUEST_SESSIONS" -> {
                                    syncScope.launch { sendSessionDataToLaptop(context) }
                                }

                                command == "DISCONNECT" -> {
                                    stopAllSyncServices(context)
                                }
                            }
                        } finally {
                            wl.safeRelease()
                        }
                    } catch (_: SocketException) {
                        break
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                if (e !is SocketException) {
                    logError("startTriggerListener", e)
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
    appContext = context.applicationContext
    val prefs = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
    val syncActive = prefs.getBoolean(KEY_SYNC_ACTIVE, false)
    val syncUntil = prefs.getLong(KEY_SYNC_UNTIL, 0L)
    val remainingMs = syncUntil - System.currentTimeMillis()

    if (syncActive && remainingMs > 0) {
        val remainingMinutes = (remainingMs / 60_000L).toInt().coerceAtLeast(1)
        isLaptopConnected = true
        startUpdateListener(context, remainingMinutes)
        syncScope.launch {
            delay(5_000)
            syncTodosWithLaptop(context)
        }
        showSimpleNotificationExtern(
            "🔁 Sync wiederhergestellt",
            "Listener läuft noch $remainingMinutes min",
            10.seconds,
            context
        )
    }
}

fun stopAllSyncServices(context: Context) {
    appContext = null
    stopUpdateListener(false)

    listOf(
        mediaCommandJob, mediaStateJob, aiResponseJob, flashcardResponseJob,
        clipboardJob, mailNotifyJob, executeJob
    ).forEach { it?.cancel() }

    mediaCommandJob = null; mediaStateJob = null; aiResponseJob = null
    flashcardResponseJob = null; clipboardJob = null; mailNotifyJob = null; executeJob = null

    aiResponseServerSocket?.close(); aiResponseServerSocket = null
    flashcardResponseSocket?.close(); flashcardResponseSocket = null

    synchronized(activeServers) {
        activeServers.forEach { runCatching { it.close() } }
        activeServers.clear()
    }

    synchronized(serverMutexes) {
        serverMutexes.clear()
    }

    cpuWakeLock.safeRelease(); cpuWakeLock = null

    isLaptopConnected = false

    showSimpleNotificationExtern(
        "📴 Laptop getrennt",
        "Alle Sync-Services gestoppt",
        10.seconds,
        context
    )
}

fun syncTodosWithLaptop(context: Context) {
    if (syncInProgress) return
    syncInProgress = true

    syncScope.launch {
        try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return@launch
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@launch

            val localip = linkProperties.linkAddresses
                .map { it.address.hostAddress }
                .firstOrNull { ip ->
                    ip != null && (ip.startsWith("192.") || ip.startsWith("10."))
                }
            if (localip != null) {
                insertMobileIpToSupabase(localip)
            }
            var resolvedIp = laptopIp

            if (resolvedIp.isEmpty()) {
                resolvedIp = withTimeoutOrNull(5000L) {
                    fetchLaptopIpFromSupabase()
                } ?: ""

                if (resolvedIp.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showSimpleNotificationExtern(
                            "❌ Keine IP gefunden",
                            "Supabase lieferte keine IP",
                            10.seconds,
                            context
                        )
                    }
                    return@launch
                }

                laptopIp = resolvedIp
            }

            val pingSuccess = ConnectionGuard.quickPingTest(resolvedIp, SYNC_PORT)

            if (!pingSuccess) {
                withContext(Dispatchers.Main) {
                    showSimpleNotificationExtern(
                        "❌ Ping fehlgeschlagen",
                        "Laptop nicht erreichbar: $resolvedIp",
                        10.seconds, context
                    )
                }
                return@launch  // NICHT nochmal fetchen
            }

            val todos = getTodos(context)
            val socket = Socket()
            socket.connect(InetSocketAddress(Inet4Address.getByName(resolvedIp), SYNC_PORT), 3000)
            socket.soTimeout = 8000

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.println(todosToJsonArray(todos).toString())
            writer.flush()

            val response = reader.readLine() ?: throw IOException("Keine Antwort vom Server")
            socket.close()

            when (response) {
                "OK" -> {
                    ConnectionGuard.recordSuccess()
                    isLaptopConnected = true

                    startMediaCommandListener(context)
                    startExecuteListener(context)
                    startMediaStateServer(context)
                    startClipboardListener(context)

                    if (listenerJob == null || listenerJob?.isActive == false) {
                        startUpdateListener(context, 60)
                    }

                    withContext(Dispatchers.Main) {
                        WhatsAppNotificationListener.forwardNotificationsToLaptop1()
                        showSimpleNotificationExtern(
                            "✅ Sync erfolgreich",
                            "${todos.size} To-dos übertragen",
                            10.seconds, context, silent = false
                        )
                    }

                    pushMediaStateToLaptop(context)
                }

                "EMPTY" -> throw IOException("Server erhielt leere Daten")
                "TIMEOUT" -> throw IOException("Server-Timeout beim Lesen")
                "ERROR" -> throw IOException("Server konnte Daten nicht verarbeiten")
                else -> throw IOException("Unbekannte Antwort: $response")
            }
        } catch (_: ConnectException) {
            ConnectionGuard.recordFailure()
        } catch (_: SocketTimeoutException) {
            ConnectionGuard.recordFailure()
        } catch (e: Exception) {
            val msg = e.message
            if (msg == null || !msg.contains("Connection reset")) {
                ConnectionGuard.recordFailure()
                logError("syncTodosWithLaptop", e)
                withContext(Dispatchers.Main) {
                    showSimpleNotificationExtern(
                        "❌ Sync Fehler",
                        msg ?: "Unbekannter Fehler",
                        10.seconds, context
                    )
                }
            }
        } finally {
            syncInProgress = false
        }
    }
}

fun startUpdateListener(context: Context, durationMinutes: Int = 60) {
    stopUpdateListener(true)
    appContext = context.applicationContext
    saveSyncState(context, durationMinutes)

    val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    cpuWakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TodoSync:UpdateWakeLock")
    cpuWakeLock?.acquire(durationMinutes * 60_000L)

    listenerJob = syncScope.launch(Dispatchers.IO) {
        try {
            updateServerSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(UPDATE_PORT))
            }

            val timeoutJob = launch {
                delay(durationMinutes * 60_000L)
                stopUpdateListener(false)
                withContext(Dispatchers.Main) {
                    showSimpleNotificationExtern(
                        "⏸️ Sync-Listener gestoppt",
                        "Nach $durationMinutes min automatisch beendet.",
                        15.seconds,
                        context
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
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    logError("startUpdateListener", e)
                }
            }

            timeoutJob.cancel()
        } catch (e: Exception) {
            logError("startUpdateListener", e)
        }
    }
}

fun stopUpdateListener(boolean: Boolean = false) {
    try {
        appContext?.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)?.edit {
            putBoolean(KEY_SYNC_ACTIVE, false)
        }
        isLaptopConnected = boolean
        cpuWakeLock.safeRelease(); cpuWakeLock = null
        listenerJob?.cancel(); listenerJob = null
        updateServerSocket?.close(); updateServerSocket = null
    } catch (e: Exception) {
        logError("stopUpdateListener", e)
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

    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -2)
    val twoDaysAgoStart = cal.timeInMillis
    val previousSessions =
        getSessions().filter { it.startedAt in twoDaysAgoStart..<lastAiTimestamp }

    fun buildJsonArray(list: List<ListenSession>): JSONArray = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("label", s.label)
                put("type", s.type)
                put("listenedMs", s.listenedMs)
                put("startedAt", s.startedAt)
                put("repeatCount", s.repeatCount)
            })
        }
    }

    val payload = JSONObject().apply {
        put("today", buildJsonArray(sessions))
        put("previous_2_days", buildJsonArray(previousSessions))
    }

    try {
        if (laptopIp == "") return
        Socket().use { socket ->
            socket.connect(InetSocketAddress(laptopIp, Config.SESSION_PORT), 3000)
            PrintWriter(socket.getOutputStream(), true).apply {
                println(payload.toString())
                flush()
            }
        }
    } catch (e: Exception) {
        logError("sendSessionDataToLaptop", e)
    }
}

fun getTodos(context: Context): List<TodoItem> {
    val json = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)
        .getString("todos", "[]") ?: "[]"
    return parseTodosFromJson(json)
}

fun showOpenTodos(context: Context) {
    val todos = getTodos(context).filter { !it.completed }

    if (todos.isEmpty()) {
        showSimpleNotificationExtern(
            "📝 To-dos",
            "Keine To-dos vorhanden",
            10.seconds,
            context = context
        )
        return
    }

    val notificationManager = context.getSystemService(NotificationManager::class.java)

    todos.forEachIndexed { index, todoItem ->
        notificationManager.notify(
            TODOS + index, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(todoItem.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(todoItem.text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup("todos")
                .build()
        )
    }

    notificationManager.notify(
        TODOS + 150, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Erledigte Todos")
            .setContentText("${todos.size} Erledigte Todos")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("todos")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
    )
}

fun saveTodos(context: Context, todos: List<TodoItem>) {
    val prefs = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)
    try {
        prefs.edit { putString("todos", todosToJsonArray(todos).toString()).apply() }
    } catch (e: Exception) {
        logError("saveTodos", e)
    }
}

fun addTodo(text: String, context: Context) {
    val todos = getTodos(context).toMutableList()
    todos.add(
        TodoItem(
            id = System.currentTimeMillis(),
            text = text,
            completed = false,
            timestamp = System.currentTimeMillis()
        )
    )
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
        showSimpleNotificationExtern("✓ Erledigt", "\"${todos[index].text}\"", 10.seconds, context)
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
        showSimpleNotificationExtern("🗑️ Gelöscht", "\"${removed.text}\"", 10.seconds, context)
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
            "Keine To-dos vorhanden",
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
            activeTodos.forEach { append("${todoIndexMap[it.id]}. ${it.text}\n") }
        }
        if (completedTodos.isNotEmpty()) {
            if (activeTodos.isNotEmpty()) append("\n")
            append("✓ ERLEDIGT (${completedTodos.size}):\n")
            completedTodos.forEach { append("${todoIndexMap[it.id]}. ${it.text}\n") }
        }
    }

    val chunks = splitText(todoText)
    val notificationManager = context.getSystemService(NotificationManager::class.java)

    chunks.forEachIndexed { index, chunk ->
        notificationManager.notify(
            TODOS + index, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("📝 To-do Liste (${todos.size})")
                .setStyle(NotificationCompat.BigTextStyle().bigText(chunk))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup("todos")
                .build()
        )
    }

    notificationManager.notify(
        TODOS + 50, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Alle Todos")
            .setContentText("${chunks.size} Todos")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("todos")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
    )
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
        val jsonArray = JSONArray(jsonData)
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
        logError("parseTodosFromJson", e)
        emptyList()
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
                bind(InetSocketAddress(Config.AI_RECEIVE_PORT))
            }
            while (isActive) {
                try {
                    val client = aiResponseServerSocket?.accept() ?: break
                    val text = client.inputStream.readBytes().toString(Charsets.UTF_8)
                    client.close()

                    saveAiResponse(context, text)
                    aiResponseFlow.emit(
                        AiResponseEntry(
                            text = text,
                            timestamp = System.currentTimeMillis(),
                            dateKey = getTodayKey()
                        )
                    )
                    showSimpleNotificationExtern(
                        "🤖 AI Antwort",
                        text.take(100),
                        30.seconds,
                        context
                    )
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    logError("startAiResponseListener", e)
                }
            }
        } catch (e: Exception) {
            logError("startAiResponseListener", e)
        }
    }
}

fun saveAiResponse(context: Context, text: String) {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val dateKey = getTodayKey()
    val timestamp = System.currentTimeMillis()

    val arr = JSONArray(prefs.getString("all_entries", "[]") ?: "[]")
    arr.put(JSONObject().apply {
        put("text", text)
        put("timestamp", timestamp)
        put("dateKey", dateKey)
    })

    prefs.edit {
        putString("all_entries", arr.toString())
        putString("entry_$dateKey", text)
        putLong("timestamp_$dateKey", timestamp)
    }
}

fun deleteAiResponse(context: Context, timestamp: Long) {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val arr = JSONArray(prefs.getString("all_entries", "[]") ?: "[]")
    val newArr = JSONArray()

    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        if (obj.getLong("timestamp") != timestamp) newArr.put(obj)
    }

    prefs.edit {
        putString("all_entries", newArr.toString())
        val dateKey = run {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getLong("timestamp") == timestamp) return@run obj.getString("dateKey")
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
        return AiResponseEntry(todayText, prefs.getLong("timestamp_$todayKey", 0L), todayKey)
    }

    val yesterdayText = prefs.getString("entry_$yesterdayKey", null)
    if (yesterdayText != null) {
        return AiResponseEntry(
            yesterdayText,
            prefs.getLong("timestamp_$yesterdayKey", 0L),
            yesterdayKey
        )
    }

    return null
}

fun loadAllAiResponses(context: Context): List<AiResponseEntry> {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    return try {
        val arr = JSONArray(prefs.getString("all_entries", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).run {
                AiResponseEntry(getString("text"), getLong("timestamp"), getString("dateKey"))
            }
        }.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        logError("loadAllAiResponses", e)
        emptyList()
    }
}

fun getTodayKey(): String {
    val tz = java.util.TimeZone.getTimeZone("Europe/Berlin")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply {
        timeZone = tz
    }
    return sdf.format(Date())
}

private fun getYesterdayKey(): String {
    val tz = java.util.TimeZone.getTimeZone("Europe/Berlin")
    val cal = Calendar.getInstance(tz)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply {
        timeZone = tz
    }
    return sdf.format(cal.time)
}

suspend fun trySendImageToLaptop(imageBytes: ByteArray): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            var resolvedIp = laptopIp

            if (resolvedIp.isEmpty()) {
                resolvedIp = fetchLaptopIpFromSupabase() ?: return@withContext false
                laptopIp = resolvedIp
            }

            flashcardResponseSocket?.close()
            val serverSocket = ServerSocket().apply {
                reuseAddress = true
                soTimeout = 30_000
                bind(InetSocketAddress(FLASHCARD_RECEIVE_PORT))
            }
            flashcardResponseSocket = serverSocket
            startFlashcardResponseListener(serverSocket)

            Socket().apply {
                connect(InetSocketAddress(resolvedIp, Config.FLASHCARD_SEND_PORT), 3000)
                getOutputStream().write(imageBytes)
                shutdownOutput()
                close()
            }
            true
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: ConnectException) {
            false
        } catch (e: Exception) {
            logError("trySendImageToLaptop", e)
            flashcardVokabelnFlow.emit(null)
            false
        }
    }
}

private fun startFlashcardResponseListener(boundSocket: ServerSocket) {
    flashcardResponseJob?.cancel()
    flashcardVokabelnFlow.value = null

    flashcardResponseJob = syncScope.launch(Dispatchers.IO) {
        try {
            val client = boundSocket.accept()
            val vokabeln =
                parseVokabelnFromJson(client.inputStream.readBytes().toString(Charsets.UTF_8))
            client.close()
            flashcardVokabelnFlow.emit(vokabeln.ifEmpty { null })
        } catch (_: SocketTimeoutException) {
            flashcardVokabelnFlow.emit(null)
        } catch (e: Exception) {
            logError("startFlashcardResponseListener", e)
            flashcardVokabelnFlow.emit(null)
        } finally {
            flashcardResponseSocket?.close()
            flashcardResponseSocket = null
        }
    }
}

private fun parseVokabelnFromJson(json: String): List<Vokabel> = try {
    val arr = JSONArray(json)
    Log.d("TOTOTO", "$arr")
    (0 until arr.length()).map { i ->
        arr.getJSONObject(i).run { Vokabel(getString("latein"), getString("deutsch"), i) }
    }
} catch (e: Exception) {
    logError("parseVokabelnFromJson", e)
    emptyList()
}

fun startMediaCommandListener(context: Context) {
    if (mediaCommandJob?.isActive == true) return
    mediaCommandJob =
        launchServer(mediaScope, Config.MEDIA_COMMAND_PORT, "startMediaCommandListener") { client ->
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line)
            client.close()
            handleMediaCommand(context, JSONObject(sb.toString()))
        }
}

suspend fun sendNvidiaChatMessage(
    history: List<WhatsAppNotificationListener.Companion.ChatMessage>,
    userMessage: String
): String? {
    val messages = JSONArray().apply {
        put(JSONObject().apply {
            put("role", "system")
            put(
                "content",
                "Du bist ein hilfreicher Chat-Assistent. Antworte kurz, klar und auf Deutsch und verwende keine Markdown Syntax."
            )
        })

        history.forEach { msg ->
            put(JSONObject().apply {
                put("role", if (msg.isOwnMessage) "user" else "assistant")
                put("content", msg.text)
            })
        }

        put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
    }
    return callNvidiaApi("meta/llama-3.1-8b-instruct", messages)
}

suspend fun sendNvidiaChatMessageAITab(
    history: List<ChatMessage>,
    userMessage: String,
    model: String = "nvidia/nemotron-3-nano-30b-a3b",
    pic: String? = null
): String? {
    val messages = JSONArray().apply {
        put(JSONObject().apply {
            put("role", "system")
            put(
                "content",
                "Du bist ein hilfreicher Chat-Assistent. Antworte kurz, klar und auf Deutsch."
            )
        })

        history.forEach { msg ->
            put(JSONObject().apply {
                put("role", if (msg.own) "user" else "assistant")
                put("content", msg.text)
            })
        }

        put(JSONObject().apply { put("role", "user"); put("content", userMessage) })

        if (pic != null) {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$pic")
                        })
                    })
                })
            })
        }
    }
    return callNvidiaApi(model, messages)
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
                sendIntent(if (mode == "music") "com.cloud.ACTION_MUSIC_PAUSE" else "com.cloud.ACTION_PODCAST_PAUSE")
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
            sendIntent("com.cloud.ACTION_SEEK") {
                putExtra("SEEK_POSITION_MS", json.optLong("positionMs", 0L))
            }
        }

        else -> {}
    }
}

fun startMediaStateServer(context: Context) {
    if (mediaStateJob?.isActive == true) return
    mediaStateJob =
        launchServer(mediaScope, Config.MEDIA_STATE_PORT, "startMediaStateServer") { client ->
            val command =
                BufferedReader(InputStreamReader(client.getInputStream())).readLine()?.trim()
                    ?: ""
            val response = when (command) {
                "GET_MEDIA_STATE" -> buildMediaStateJson(context)
                "GET_PLAYLISTS" -> buildPlaylistsJson(context)
                else -> "{}"
            }
            client.getOutputStream().apply {
                write(response.toByteArray(Charsets.UTF_8))
                flush()
            }
            client.close()
        }
}

private fun buildMediaStateJson(context: Context): String {
    val musicPrefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)

    if (!MediaPlayerService.isServiceActive()) {
        return JSONObject().apply {
            put("mode", "music")
            put("isPlaying", false)
            put("currentSong", "")
            put("currentPlaylist", "")
            put("songIndex", 0)
            put("isFavorite", false)
            put("activePlaylistId", "")
            put("activeAlgoPlaylistId", "")
            put("positionMs", 0)
            put("durationMs", 0)
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
            ?.mapNotNull { entry -> entry.split(":::").takeIf { it.isNotEmpty() }?.get(0) }
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
        put("isPlaying", isPlaying)
        put("currentSong", songName)
        put("currentPlaylist", playlistName)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("songIndex", songIndex)
        put("isFavorite", isFavorite)
        put("activePlaylistId", activePlaylistId)
        put("activeAlgoPlaylistId", activeAlgoPlaylistId)
    }.toString()
}

private fun buildPlaylistsJson(context: Context): String {
    val musicPrefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)

    val playlistsJson = JSONArray()
    try {
        musicPrefs.getString("playlists_json", null)
            ?.split("\n---\n")?.filter { it.isNotBlank() }?.forEach { line ->
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
        logError("buildPlaylistsJson", e)
    }

    val algoJson = JSONArray()
    AlgorithmicPlaylistRegistry.all.forEach { source ->
        algoJson.put(JSONObject().apply {
            put("id", source.id)
            put("name", source.name)
            put("description", source.description)
            put("icon", source.icon)
        })
    }

    val songsJson = JSONArray()
    try {
        val proj = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE
        )
        var index = 0
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
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
        logError("buildPlaylistsJson", e)
    }

    return JSONObject().apply {
        put("playlists", playlistsJson)
        put("algorithmicPlaylists", algoJson)
        put("algo_playlists", algoJson)
        put("songs", songsJson)
    }.toString()
}

var lastPushTime: Long = 0L

fun pushMediaStateToLaptop(context: Context) {
    if (!isLaptopConnected) return
    val now = System.currentTimeMillis()
    if (now - lastPushTime < 5000L) return
    val state = buildMediaStateJson(context)
    if (state == lastPushedState) return
    lastPushedState = state
    lastPushTime = now
    syncScope.launch(Dispatchers.IO) {
        try {
            if (laptopIp == "") return@launch
            Socket().apply {
                connect(InetSocketAddress(laptopIp, 8901), 2000)
                getOutputStream().apply {
                    write(state.toByteArray(Charsets.UTF_8))
                    flush()
                }
                close()
            }
        } catch (_: SocketTimeoutException) {
        } catch (_: ConnectException) {
        } catch (e: Exception) {
            logError("pushMediaStateToLaptop", e)
        }
    }
}

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
        .setMaxUpdates(1)
        .build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                callback(
                    distanceBetween(
                        location.latitude,
                        location.longitude,
                        targetLat,
                        targetLon
                    ) <= radiusMeters
                )
            } else {
                callback(false)
            }
            fusedLocationClient.removeLocationUpdates(this)
        }
    }

    syncScope.launch(Dispatchers.Main) {
        delay(10000)
        runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    @SuppressLint("MissingPermission")
    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        context.mainLooper
    )
}

fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(
            dLon / 2
        ).pow(
            2.0
        )
    return (earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))).toFloat()
}

suspend fun askServer(
    history: List<ChatMessage>,
    question: String,
    model: String,
    pic: String?
): String {
    return withContext(Dispatchers.IO) {
        try {
            if (laptopIp == "") throw Exception("Keine laptopIp is vorhanden")
            val request =
                "MODEL=$model PICTURE=${pic ?: "NONE"} ${question}. Here is the chat history:$history "
            val sock = Socket(laptopIp, Config.AI_PORT)
            sock.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            sock.shutdownOutput()
            val response = sock.getInputStream().readBytes().toString(Charsets.UTF_8)
            sock.close()
            response
        } catch (e: Exception) {
            var msg = ""
            e.message?.let {
                msg =
                    if (it.contains("failed to connect")) "Keine Verbindung mit Server möglich" else "Fehler: ${e.message}"
            }
            msg
        }
    }
}

fun buildSessionStatsText(sessions: List<ListenSession>): String {
    val totals = mutableMapOf<String, Triple<Long, String, Int>>()
    for (s in sessions) {
        val cur = totals[s.label] ?: Triple(0L, s.type, 0)
        totals[s.label] = Triple(cur.first + s.listenedMs, s.type, cur.third + 1)
    }

    val sorted = totals.entries.sortedByDescending { it.value.first }.take(15)
    val allTimes = sessions.map { it.startedAt }.sorted()
    val fmt = SimpleDateFormat("HH:mm", Locale.GERMANY)

    val lines = mutableListOf(
        "Zeitraum: ${fmt.format(allTimes.first())} – ${fmt.format(allTimes.last())}",
        "Tracks gesamt: ${totals.size}"
    )

    for ((label, info) in sorted) {
        val mins = info.first / 1000.0 / 60.0
        val typ = if (info.second == "music") "Musik" else "Podcast"
        lines += "- [$typ] $label: ${"%.1f".format(mins)} min, ${info.third}x"
    }

    return """Hör-Stats von heute (bereits berechnet):
${lines.joinToString("\n")}

Schreib jetzt 3-5 lockere Sätze auf Deutsch. Musik UND Podcast erwähnen wenn beides da ist. Nur fließender Text, keine Liste, kein Markdown."""
}

fun startClipboardListener(context: Context) {
    if (clipboardJob?.isActive == true) return
    clipboardJob =
        launchServer(syncScope, Config.CLIPBOARD_PORT, "startClipboardListener") { client ->
            val text = client.inputStream.bufferedReader().readText()
            client.close()
            if (text.startsWith("CLIPBOARD:")) {
                val content = text.removePrefix("CLIPBOARD:")
                withContext(Dispatchers.Main) {
                    val cm =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("sync", content))
                }
            }
        }
}

fun startMailNotifyListener(context: Context) {
    mailNotifyJob?.cancel()
    mailNotifyJob =
        launchServer(syncScope, Config.MAIL_NOTIFY_PORT, "startMailNotifyListener") { client ->
            val text = client.inputStream.readBytes().toString(Charsets.UTF_8)
            client.close()
            val parts = text.split("|", limit = 4)
            val sender = parts.getOrNull(1)?.trim() ?: "Unbekannt"
            val subject = parts.getOrNull(2)?.trim() ?: "(kein Betreff)"
            val summary = parts.getOrNull(3)?.trim() ?: text
            val senderShort = sender.substringBefore("<").trim().ifEmpty { sender }
            withContext(Dispatchers.Main) {
                showSimpleNotificationExtern(
                    title = "📧 $senderShort",
                    text = "**$subject**\n$summary",
                    duration = 60.seconds,
                    context = context
                )
            }
        }
}

fun startExecuteListener(context: Context) {
    if (executeJob?.isActive == true) return
    executeJob =
        launchServer(syncScope, Config.EXECUTE_PORT, "startExecuteListener") { client ->
            val json = JSONObject(client.inputStream.readBytes().toString(Charsets.UTF_8))
            client.close()
            handleExecuteCommand(context, json)
        }
}

private fun handleExecuteCommand(context: Context, json: JSONObject) {
    val tool = json.optString("tool")
    val args = json.optJSONObject("args") ?: JSONObject()

    when (tool) {
        "show_toast" -> {
            syncScope.launch(Dispatchers.Main) {
                Toast.makeText(context, args.optString("message", ""), Toast.LENGTH_LONG).show()
            }
        }

        "show_notification" -> {
            showSimpleNotificationExtern(
                args.optString("title", "AI"),
                args.optString("text", ""),
                10.seconds,
                context
            )
        }

        "add_todo" -> addTodo(args.optString("text", ""), context)

        "play_music" -> {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                this.action = when (args.optString("action", "play")) {
                    "pause" -> "com.cloud.ACTION_MUSIC_PAUSE"
                    "next" -> "com.cloud.ACTION_MUSIC_NEXT"
                    "previous" -> "com.cloud.ACTION_MUSIC_PREVIOUS"
                    else -> "com.cloud.ACTION_MUSIC_PLAY"
                }
            }
            context.startService(intent)
        }

        "set_alarm" -> {
            val parts = args.optString("time", "").split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: return
                val minutes = parts[1].toIntOrNull() ?: return
                context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Alarm")
                })
            }
        }

        "send_whatsapp" -> {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = "https://wa.me/${
                    args.getString("phone_number").replace("+", "")
                }?text=${Uri.encode(args.getString("message"))}".toUri()
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        "get_contacts" -> {
            val query = args.optString("query", "").lowercase()
            val results = JSONArray()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameCol =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val number = cursor.getString(numberCol) ?: continue
                    if (query.isEmpty() || name.lowercase().contains(query)) {
                        results.put(JSONObject().apply {
                            put("name", name)
                            put("number", number.replace(" ", "").replace("-", ""))
                        })
                    }
                }
            }
            syncScope.launch(Dispatchers.IO) {
                try {
                    Socket().use { sock ->
                        sock.connect(
                            InetSocketAddress(laptopIp, Config.EXECUTE_RESPONSE_PORT),
                            3000
                        )
                        sock.getOutputStream()
                            .write(results.toString().toByteArray(Charsets.UTF_8))
                        sock.getOutputStream().flush()
                    }
                } catch (_: Exception) {
                }
            }
        }

        "lookup_credentials" -> {
            syncScope.launch(Dispatchers.IO) {
                val queries = args.optJSONArray("queries")
                    ?.let { arr ->
                        (0 until arr.length()).map {
                            arr.getString(it).lowercase()
                        }
                    }
                    ?: emptyList()

                val allPasswords = PasswordDatabase.getDatabase(context).passwordDao().getAll()
                val allTwoFa = TwoFADatabase.getDatabase(context).twoFADao().getAll()

                val matchedPasswords = allPasswords.filter { entry ->
                    queries.any { q ->
                        entry.name.contains(
                            q,
                            ignoreCase = true
                        ) || entry.username.contains(q, ignoreCase = true)
                    }
                }
                val matchedTwoFa = allTwoFa.filter { entry ->
                    queries.any { q -> entry.name.contains(q, ignoreCase = true) }
                }

                val response = JSONObject().apply {
                    put("matchedCredentials", JSONArray(matchedPasswords.map { entry ->
                        JSONObject().apply {
                            put("id", entry.id)
                            put("name", entry.name)
                            put("username", entry.username)
                        }
                    }))
                    put("matchedTwoFaCodes", JSONArray(matchedTwoFa.map { entry ->
                        JSONObject().apply {
                            put("id", entry.id)
                            put("name", entry.name)
                        }
                    }))
                }

                val jsonBytes = response.toString().toByteArray(Charsets.UTF_8)
                val sizePrefix = ByteBuffer.allocate(4).putInt(jsonBytes.size).array()

                try {
                    Socket().use { sock ->
                        sock.connect(
                            InetSocketAddress(laptopIp, Config.EXECUTE_RESPONSE_PORT),
                            3000
                        )
                        sock.getOutputStream().write(sizePrefix + jsonBytes)
                        sock.getOutputStream().flush()
                    }
                } catch (_: Exception) {
                }
            }
        }

        "reveal_credentials" -> {
            syncScope.launch(Dispatchers.IO) {
                val credentialId = args.optInt("credential_id", -1)
                val twofaId = args.optInt("twofa_id", -1)

                if (credentialId == -1) {
                    return@launch
                }

                val entry = PasswordDatabase.getDatabase(context).passwordDao().getAll()
                    .firstOrNull { it.id == credentialId }

                if (entry == null) {
                    showSimpleNotificationExtern(
                        "❌ Zugangsdaten",
                        "Eintrag #$credentialId nicht gefunden",
                        10.seconds,
                        context
                    )
                    return@launch
                }

                val totpCode: String? = if (twofaId != -1) {
                    TwoFADatabase.getDatabase(context).twoFADao().getAll()
                        .firstOrNull { it.id == twofaId }
                        ?.let { runCatching { generateTOTP(it.secret) }.getOrNull() }
                } else null

                showCredentialsOverlay(context, entry.username, entry.password, totpCode ?: "")
            }
        }

        else -> {}
    }
}

fun showCredentialsOverlay(context: Context, us: String, pw: String, totp: String) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Overlay-Berechtigung fehlt!", Toast.LENGTH_LONG).show()
        return
    }

    val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
    val overlayLifecycle = OverlayLifecycleOwner().also {
        it.onCreate()
        it.onResume()
    }
    var testOverlayView: ComposeView? = null
    var testOverlayLifecycle: OverlayLifecycleOwner? =
        OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }

    testOverlayView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(testOverlayLifecycle)
        setViewTreeSavedStateRegistryOwner(testOverlayLifecycle)
        setViewTreeViewModelStoreOwner(testOverlayLifecycle)
        setContent {
            Box(
                modifier = Modifier
                    .height(210.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Cloud),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    listOf(us, pw, totp).forEach { value ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "username",
                                            value
                                        )
                                    )
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = value,
                                textAlign = TextAlign.Center,
                                color = Color.White,
                                fontSize = 30.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        try {
                            testOverlayView?.let { windowManager.removeView(it) }
                        } catch (_: Exception) {
                        }
                        try {
                            testOverlayLifecycle?.onDestroy()
                        } catch (_: Exception) {
                        }
                        testOverlayView = null
                        testOverlayLifecycle = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = Color.White
                    )
                }
            }
        }
    }

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.CENTER }

    try {
        windowManager.addView(testOverlayView, params)
    } catch (_: Exception) {
        overlayLifecycle.onDestroy()
    }
}

fun sendAiExecuteCommand(context: Context, userInput: String) {
    if (laptopIp.isEmpty()) {
        showSimpleNotificationExtern(
            "❌ AI Execute",
            "Laptop nicht verbunden",
            10.seconds,
            context
        )
        return
    }

    syncScope.launch(Dispatchers.IO) {
        try {
            Socket().use { sock ->
                sock.connect(
                    InetSocketAddress(laptopIp, Config.EXECUTE_PORT_SEND_FROM_HANDY),
                    3000
                )
                sock.getOutputStream()
                    .write(
                        JSONObject().apply { put("prompt", userInput) }.toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                sock.shutdownOutput()
            }
        } catch (e: Exception) {
            logError("sendAiExecuteCommand", e)
        }
    }
}

private suspend fun fetchLaptopIpFromSupabase(): String? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val url =
            "${Config.SUPABASE_URL}/rest/v1/device_ips?device_id=eq.laptop&select=ip_address"

        connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("apikey", Config.SUPABASE_PUBLISHABLE_KEY)
        connection.setRequestProperty(
            "Authorization",
            "Bearer ${Config.SUPABASE_PUBLISHABLE_KEY}"
        )

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)

            return@withContext if (jsonArray.length() > 0) {
                jsonArray.getJSONObject(0).optString("ip_address", "")
            } else {
                null
            }
        }
        null
    } catch (e: Exception) {
        Log.e("CLOUDSA", "Error fetching laptop IP: ${e.message}", e)
        logError("SupabaseFetch", e)
        null
    } finally {
        connection?.disconnect()
    }
}

private suspend fun insertMobileIpToSupabase(ipAddress: String): Boolean =
    withContext(Dispatchers.IO) {
        if (!Config.realDevice) return@withContext true
        var connection: HttpURLConnection? = null
        try {
            val url = "${Config.SUPABASE_URL}/rest/v1/device_ips"

            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", Config.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty(
                "Authorization",
                "Bearer ${Config.SUPABASE_PUBLISHABLE_KEY}"
            )
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates,upsert=true")

            val jsonPayload = JSONObject().apply {
                put("device_id", "handy")
                put("ip_address", ipAddress)
                put("last_seen", Instant.now().toString())
            }

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            return@withContext if (connection.responseCode == 201) {
                Log.d("CLOUDSA", "Mobile IP successfully inserted: $ipAddress")
                true
            } else {
                val errorBody = if (connection.responseCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error body"
                } else ""

                Log.e(
                    "CLOUDSA",
                    "Failed to insert mobile IP. Code: ${connection.responseCode}, Body: $errorBody"
                )
                false
            }
        } catch (e: Exception) {
            Log.e("CLOUDSA", "Error inserting mobile IP: ${e.message}", e)
            logError("SupabaseInsert", e)
            false
        } finally {
            connection?.disconnect()
        }
    }