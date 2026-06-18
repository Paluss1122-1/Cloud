package com.tabslify.quiethoursnotificationhelper

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.BatteryManager
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
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.tabslify.core.activities.Tabslify.Companion.coroutineExceptionHandler
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.FLASHCARD_RECEIVE_PORT
import com.tabslify.core.objects.Config.INFO_PORT
import com.tabslify.core.objects.Config.SYNC_PORT
import com.tabslify.core.objects.Config.TODOS
import com.tabslify.core.objects.Config.UPDATE_PORT
import com.tabslify.core.ui.APP_COLOR
import com.tabslify.services.MediaPlayerService
import com.tabslify.services.MediaPlayerService.Companion.ACTION_TOGGLE_REPEAT
import com.tabslify.services.OverlayLifecycleOwner
import com.tabslify.services.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.tabslify.services.WhatsAppNotificationListener
import com.tabslify.tabs.aitab.ChatMessage
import com.tabslify.tabs.authenticator.PasswordDatabase
import com.tabslify.tabs.authenticator.TotpGenerator.generateTOTP
import com.tabslify.tabs.authenticator.TwoFADatabase
import com.tabslify.tabs.mediaplayer.AlgorithmicPlaylistRegistry
import com.tabslify.tabs.mediaplayer.ListenSession
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.getSessions
import com.tabslify.tabs.school.Vokabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private data class Cache(
    var batteryLevel: String = "0",
    var temperature: String = "0",
    var plugged: Boolean = false,
    var lastSync: Long = 0
)

private var cache = Cache()
private val akkuReceiver = AkkuReceiver()

val isLaptopConnectedFlow = MutableStateFlow(false)
val aiResponseFlow = MutableStateFlow<AiResponseEntry?>(null)
val flashcardVokabelnFlow = MutableStateFlow<List<Vokabel>?>(null)

private val serverMutexes = mutableMapOf<Int, Mutex>()

private fun getServerMutex(port: Int) = synchronized(serverMutexes) {
    serverMutexes.getOrPut(port) { Mutex() }
}

var isLaptopConnected: Boolean
    get() = isLaptopConnectedFlow.value
    set(value) {
        isLaptopConnectedFlow.value = value
    }

private val syncScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler
)
private val mediaScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler
)

private var listenerJob: Job? = null
private var updateServerSocket: ServerSocket? = null

private var triggerJob: Job? = null

private var aiResponseJob: Job? = null
private var aiResponseServerSocket: ServerSocket? = null

private var flashcardResponseJob: Job? = null
private var flashcardResponseSocket: ServerSocket? = null

private var mediaCommandJob: Job? = null
private var mediaStateJob: Job? = null
private var clipboardJob: Job? = null
private var mailNotifyJob: Job? = null
private var executeJob: Job? = null
private var smsJob: Job? = null

private val activeServers = mutableListOf<ServerSocket>()

private var cpuWakeLock: PowerManager.WakeLock? = null
private var appContext: Context? = null

private const val PREFS_SYNC = "sync_prefs"
private const val KEY_SYNC_ACTIVE = "sync_active"
private const val KEY_LAST_SYNC = "last_sync"
private var lastPushedState: String = ""

private var networkCallback: ConnectivityManager.NetworkCallback? = null
private var pendingSyncJob: Job? = null
private var lastTriggerTime = 0L
private const val MIN_TRIGGER_INTERVAL = 15_000L

private val syncInProgress = AtomicBoolean(false)

private fun PowerManager.WakeLock?.safeRelease() {
    if (this != null && isHeld) release()
}

private fun logError(service: String, e: Exception) {
    errorInsert(
        service,
        e.stackTraceToString(),
        Instant.now().toString(),
        "ERROR"

    )
}

fun ensureReadyForConnect(context: Context) {
    // 1. syncInProgress zurücksetzen (könnte von abgebrochenem Versuch stuck sein)
    syncInProgress.set(false)
    pendingSyncJob?.cancel()
    pendingSyncJob = null

    // 2. triggerJob force-restart — egal ob isActive oder nicht
    triggerJob?.cancel()
    triggerJob = null
    startTriggerListener(context)

    // 3. networkCallback neu registrieren (bei WiFi-Toggle kann er verloren gehen)
    registerWifiReconnectReceiver(context)

    // 4. frische laptopIp holen
    syncScope.launch {
        val ip = fetchIpFromSupabase()
        if (!ip.isNullOrEmpty()) {
            laptopIp = ip
            showSimpleNotificationExtern(
                "✅ Bereit",
                "TriggerListener aktiv: ${triggerJob?.isActive} | IP: $ip",
                10.seconds,
                context
            )
        } else {
            showSimpleNotificationExtern(
                "⚠️ Kein Laptop-IP",
                "Supabase hat keine IP geliefert",
                10.seconds,
                context
            )
        }
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
                            client.use {
                                try {
                                    handler(it)
                                } catch (e: Exception) {
                                    logError(errorTag, e)
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (_: SocketException) {
                        break
                    }
                }
            } catch (_: BindException) {
                continue
            } catch (e: Exception) {
                logError(errorTag, e)
            } finally {
                server?.let {
                    synchronized(activeServers) { activeServers.remove(it) }
                    runCatching { it.close() }
                }
            }
            if (isActive) delay(2000.milliseconds)
        }
    }
}

private fun todosToJsonArray(todos: List<TodoItem>): JSONArray = JSONArray().apply {
    todos.forEach { todo ->
        put(JSONObject().apply {
            put("id", todo.id)
            put("text", todo.text)
            put("completed", todo.completed)
        })
    }
}

suspend fun callNvidiaApi(model: String, messagesJson: JSONArray): String? =
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
            val response = JSONObject(connection.inputStream.bufferedReader().readText())
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
                .ifBlank { null }
            connection.disconnect()
            response
        } catch (e: Exception) {
            logError("callNvidiaApi", e)
            null
        }
    }

suspend fun callNvidiaVisionApi(
    bmp: Bitmap,
    onError: (String) -> Unit,
    onProgress: (String) -> Unit,
    onVocabChange: (List<Vokabel>) -> Unit
) {
    fun Bitmap.scaleForApi(maxPx: Int = 1280): Bitmap {
        val scale = maxPx.toFloat() / maxOf(width, height)
        if (scale >= 1f) return this
        return this.scale((width * scale).toInt(), (height * scale).toInt())
    }

    val scaledBmp = bmp.scaleForApi(1280)

    val prompt = """
        Look at this vocabulary list image carefully.
        There are TWO columns: Latin on the LEFT, German on the RIGHT.
        Each row is one vocabulary entry.
        
        Rules:
        - Match each Latin entry with the German entry on the SAME vertical position
        - Latin entries are on the left half of the image
        - German entries are on the right half
        - Ignore page numbers (like "116")
        - Ignore any repeated/duplicate blocks at bottom
        
        Return ONLY a JSON array like this (one object per row):
        [{"latein":"salūtem dīcere (m. Dat.)","deutsch":"(jdn.) grüßen, begrüßen"},{"latein":"gaudium","deutsch":"die Freude"},...]
        
        No markdown, no explanation, ONLY the JSON array.
    """.trimIndent()

    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel("gemini-3-flash-preview")

    val result = try {
        val sb = StringBuilder()
        model.generateContentStream(content { image(scaledBmp); text(prompt) })
            .collect { chunk ->
                val delta = chunk.text ?: ""
                if (delta.isNotEmpty()) {
                    sb.append(delta)
                    withContext(Dispatchers.Main) { onProgress(sb.toString()) }
                }
            }
        sb.toString().trim()
    } catch (_: Exception) {
        onError("API keine Antwort – prüfe Key & Netzwerk")
        return
    }

    if (result.isNotEmpty()) {
        try {
            val startIdx = result.indexOf('[')
            val endIdx = result.lastIndexOf(']')
            if (startIdx != -1 && endIdx > startIdx) {
                val arr = JSONArray(result.substring(startIdx, endIdx + 1))
                onVocabChange((0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Vokabel(o.getString("latein"), o.getString("deutsch"), it)
                })
            }
        } catch (e: Exception) {
            onError("Parse-Fehler: ${e.message}\nRaw: ${result.take(300)}")
        }
    } else {
        onError("API keine Antwort – prüfe Key & Netzwerk")
    }
}

@Volatile
var laptopIp: String = ""

data class TodoItem(
    val id: Long,
    val text: String,
    val completed: Boolean
)

data class AiResponseEntry(
    val text: String,
    val timestamp: Long,
    val dateKey: String
)

fun startTriggerListenerIfHomeWifi(context: Context) {
    startTriggerListener(context)
    registerWifiReconnectReceiver(context)

    checkIfNearLocation(context) { }

    syncScope.launch {
        val ip = fetchIpFromSupabase()
        if (!ip.isNullOrEmpty()) laptopIp = ip
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
                if (atHome && !isLaptopConnected) {
                    syncTodosWithLaptop(context)
                }
            }
        }

        override fun onLost(network: Network) {
            syncInProgress.set(false)
            pendingSyncJob?.cancel()
            if (isLaptopConnected) {
                stopAllSyncServices(context)
            }
            // Nach Connection Verlust: Trigger Listener wieder starten
            syncScope.launch {
                delay(2000.milliseconds)
                if (triggerJob?.isActive != true) {
                    startTriggerListener(context)
                }
            }
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
    if (triggerJob?.isActive == true) return
    appContext = context.applicationContext

    triggerJob = launchServer(syncScope, Config.TRIGGER_PORT, "startTriggerListener") { client ->
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TodoSync:AcceptWakeLock")
        wl.acquire(30_000L)

        try {
            val command = BufferedReader(InputStreamReader(client.getInputStream())).readLine()
            client.close()

            when {
                command.startsWith("CONNECT") -> {
                    laptopIp = command.substringAfter("CONNECT:", "")
                    showSimpleNotificationExtern("📡 CONNECT", "Starte Sync...", 10.seconds, context)

                    val syncWl =
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TodoSync:SyncWakeLock")
                    syncWl.acquire(60_000L)
                    syncScope.launch {
                        try {
                            syncTodosWithLaptop(context, true)
                        } finally {
                            syncWl.safeRelease()
                        }
                    }
                }

                command == "REQUEST_SESSIONS" -> syncScope.launch { sendSessionDataToLaptop(context) }
                command == "DISCONNECT" -> stopAllSyncServices(context)
            }
        } finally {
            wl.safeRelease()
        }
    }
}

fun restoreSyncIfNeeded(context: Context) {
    appContext = context.applicationContext
    val prefs = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
    val syncActive = prefs.getBoolean(KEY_SYNC_ACTIVE, false)
    val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
    val thirtyMinutesMs = 30 * 60 * 1000L
    val timeSinceLastSync = System.currentTimeMillis() - lastSync

    if (syncActive && timeSinceLastSync <= thirtyMinutesMs) {
        val minutesAgo = (timeSinceLastSync / 60_000L).toInt().coerceAtLeast(1)
        isLaptopConnected = true
        startUpdateListener(context)
        startMediaCommandListener(context)
        startExecuteListener(context)
        startMediaStateServer(context)
        startClipboardListener(context)
        startSmsListener(context)
        context.registerReceiver(akkuReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        syncScope.launch {
            delay(5_000.milliseconds)
            syncTodosWithLaptop(context)
        }
        showSimpleNotificationExtern(
            "🔁 Sync wiederhergestellt",
            "Letzter Sync vor $minutesAgo min",
            10.seconds,
            context
        )
    }
}

fun stopUpdateListener(b: Boolean = true) {
    try {
        appContext?.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)?.edit {
            putBoolean(KEY_SYNC_ACTIVE, false)
        }
        isLaptopConnected = b
        cpuWakeLock.safeRelease(); cpuWakeLock = null
        listenerJob?.cancel(); listenerJob = null
        updateServerSocket?.close(); updateServerSocket = null
    } catch (e: Exception) {
        logError("stopUpdateListener", e)
    }
}

fun stopAllSyncServices(context: Context) {
    stopUpdateListener()

    listOf(
        mediaCommandJob, mediaStateJob, aiResponseJob, flashcardResponseJob,
        clipboardJob, mailNotifyJob, executeJob, smsJob
    ).forEach { it?.cancel() }

    mediaCommandJob = null; mediaStateJob = null; aiResponseJob = null
    flashcardResponseJob = null; clipboardJob = null; mailNotifyJob = null; executeJob = null

    aiResponseServerSocket?.close(); aiResponseServerSocket = null
    flashcardResponseSocket?.close(); flashcardResponseSocket = null

    synchronized(activeServers) {
        activeServers.removeAll { server ->
            val isTriggerServer =
                runCatching { server.localPort }.getOrNull() == Config.TRIGGER_PORT
            if (!isTriggerServer) {
                runCatching { server.close() }
            }
            !isTriggerServer
        }
    }

    cpuWakeLock.safeRelease(); cpuWakeLock = null
    context.unregisterReceiver(akkuReceiver)
    cache = Cache()

    isLaptopConnected = false

    showSimpleNotificationExtern(
        "📴 Laptop getrennt",
        "Alle Sync-Services gestoppt",
        10.seconds,
        context
    )

    if (triggerJob?.isActive != true) {
        startTriggerListener(context)
    }
}

fun syncTodosWithLaptop(context: Context, connected: Boolean = false) {
    if (triggerJob?.isActive != true) {
        startTriggerListener(context)
    }
    if (syncInProgress.getAndSet(true) || !Config.realDevice) {
        showSimpleNotificationExtern(
            "syncTodosWithLaptop RETURN",
            "syncInProgress: ${syncInProgress.get()}, realDevice: ${Config.realDevice}",
            10.seconds, context
        )
        if (!Config.realDevice) syncInProgress.set(false)
        return
    }

    syncScope.launch {
        try {
            val localip = getHotspotIp()
            if (localip != null) insertMobileIpToSupabase(localip)

            if (!connected) {
                val fetched = withTimeoutOrNull(35_000L.milliseconds) {
                    fetchIpFromSupabase()
                }
                if (fetched.isNullOrEmpty()) {
                    showSimpleNotificationExtern(
                        "❌ Keine IP gefunden", "Supabase lieferte keine IP", 10.seconds, context
                    )
                    return@launch
                }
                laptopIp = fetched
                val insertedIp = fetchIpFromSupabase(true)
                showSimpleNotificationExtern(
                    "Fetched laptopIp, inserted ip",
                    "$fetched, $insertedIp",
                    10.seconds,
                    context
                )
            }

            val currentIp = laptopIp
            if (currentIp.isEmpty()) {
                showSimpleNotificationExtern(
                    "❌ Keine IP gefunden", "Supabase lieferte keine IP", 10.seconds, context
                )
                return@launch
            }

            val todos = getTodos(context)
            val response = Socket().use { socket ->
                socket.connect(InetSocketAddress(Inet4Address.getByName(currentIp), SYNC_PORT), 3000)
                socket.soTimeout = 8000

                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                writer.println(todosToJsonArray(todos).toString())
                writer.flush()

                reader.readLine() ?: throw IOException("Keine Antwort vom Server")
            }

            when (response) {
                "OK" -> {
                    isLaptopConnected = true

                    // Save last sync time
                    val prefs = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
                    prefs.edit {
                        putBoolean(KEY_SYNC_ACTIVE, true)
                        putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    }

                    startMediaCommandListener(context)
                    startExecuteListener(context)
                    startMediaStateServer(context)
                    startClipboardListener(context)
                    startSmsListener(context)
                    if (listenerJob == null || listenerJob?.isActive == false) {
                        startUpdateListener(context)
                    }
                    withContext(Dispatchers.Main) {
                        WhatsAppNotificationListener.forwardNotificationsToLaptop1()
                        showSimpleNotificationExtern(
                            "✅ Sync erfolgreich", "${todos.size} To-dos übertragen",
                            10.seconds, context, silent = false
                        )
                    }
                    pushMediaStateToLaptop(context)
                    cache = Cache()
                    context.registerReceiver(
                        akkuReceiver,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    )
                }

                "EMPTY" -> throw IOException("Server erhielt leere Daten")
                "TIMEOUT" -> throw IOException("Server-Timeout beim Lesen")
                "ERROR" -> throw IOException("Server konnte Daten nicht verarbeiten")
                else -> throw IOException("Unbekannte Antwort: $response")
            }
        } catch (e: ConnectException) {
            showSimpleNotificationExtern("Error", "${e.message}", 10.seconds, context)
        } catch (e: SocketTimeoutException) {
            showSimpleNotificationExtern("Error", "${e.message}", 10.seconds, context)
        } catch (e: Exception) {
            val msg = e.message
            if (msg == null || !msg.contains("Connection reset")) {
                logError("syncTodosWithLaptop", e)
                withContext(Dispatchers.Main) {
                    showSimpleNotificationExtern(
                        "❌ Sync Fehler",
                        msg ?: "Unbekannter Fehler",
                        10.seconds,
                        context
                    )
                }
            }
        } finally {
            syncInProgress.set(false)
        }
    }
}

fun startUpdateListener(context: Context) {
    stopUpdateListener(true)
    appContext = context.applicationContext

    listenerJob = syncScope.launch(Dispatchers.IO) {
        try {
            updateServerSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(UPDATE_PORT))
            }

            while (isActive) {
                try {
                    val client = updateServerSocket?.accept() ?: break
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val jsonData = reader.readLine()
                    client.close()

                    if (!jsonData.isNullOrBlank()) {
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
        } catch (e: Exception) {
            logError("startUpdateListener", e)
        }
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
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(payload.toString())
            writer.flush()
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
                .setSmallIcon(R.drawable.ic_menu_agenda)
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
            .setSmallIcon(R.drawable.ic_menu_info_details)
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
            completed = false
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
                .setSmallIcon(R.drawable.ic_menu_agenda)
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
            .setSmallIcon(R.drawable.ic_menu_info_details)
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
                    getBoolean("completed")
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
    return loadLatestAiResponse(context)
}

fun loadLatestAiResponse(context: Context): AiResponseEntry? {
    val allResponses = loadAllAiResponses(context)
    return allResponses.firstOrNull()
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
    val tz = TimeZone.getTimeZone("Europe/Berlin")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply {
        timeZone = tz
    }
    return sdf.format(Date())
}

suspend fun trySendImageToLaptop(imageBytes: ByteArray): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            var resolvedIp = laptopIp

            if (resolvedIp.isEmpty()) {
                resolvedIp = fetchIpFromSupabase() ?: return@withContext false
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
                sendIntent(if (mode == "music") "com.tabslify.ACTION_MUSIC_PAUSE" else "com.tabslify.ACTION_PODCAST_PAUSE")
            } else {
                if (mode == "music") MediaPlayerService.sendMusicPlayAction(context)
                else MediaPlayerService.sendPodcastPlayAction(context)
            }
        }

        "play" -> MediaPlayerService.sendMusicPlayAction(context)
        "pause" -> sendIntent("com.tabslify.ACTION_MUSIC_PAUSE")
        "next" -> sendIntent("com.tabslify.ACTION_MUSIC_NEXT")
        "previous" -> sendIntent("com.tabslify.ACTION_MUSIC_PREVIOUS")
        "toggleRepeat" -> sendIntent(ACTION_TOGGLE_REPEAT)
        "toggleFavorite" -> sendIntent("com.tabslify.ACTION_TOGGLE_FAVORITE")

        "activatePlaylist" -> {
            val playlistId = json.optString("playlistId", "")
            val songIndex = json.optInt("index", 0)
            if (playlistId.isNotEmpty()) {
                sendIntent("com.tabslify.ACTION_ACTIVATE_PLAYLIST") {
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
            sendIntent("com.tabslify.ACTION_SEEK") {
                putExtra("SEEK_POSITION_MS", json.optLong("positionMs", 0L))
            }
        }

        "setVolume" -> {
            val level = json.optInt("level", -1)
            if (level in 0..100) {
                val audioManager =
                    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVol =
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (level / 100.0 * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    targetVol,
                    0
                )
            }
        }

        "stopMediaPlayerService" -> {
            MediaPlayerService.stopService(context)
        }

        "deleteNotif" -> {
            println(json.toString())
            println("CALLL")
            println(json.optJSONObject("extras"))
            val extras = json.optJSONObject("extras")
            val idValue = extras?.opt("id")
            println("idValue: $idValue")

            val nm = context.getSystemService(NotificationManager::class.java)
            val notificationId = when (idValue) {
                is Int -> idValue
                is Long -> idValue.toInt()
                is String -> idValue.toIntOrNull()
                else -> null
            }
            notificationId?.let { nm.cancel(it) }
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
                val inTabslify = norm.contains("/music/tabslify/", ignoreCase = true)
                if (inTabslify && !norm.contains("/podcast")) {
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

@SuppressLint("MissingPermission")
fun checkIfNearLocation(
    context: Context,
    targetLat: Double = Config.LAT,
    targetLon: Double = Config.LON,
    radiusMeters: Float = 550f,
    callback: (Boolean) -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) {
        callback(true)
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
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
                callback(true)
            }
        }
        .addOnFailureListener { callback(true) }
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
            Socket(laptopIp, Config.AI_PORT).use { sock ->
                sock.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
                sock.shutdownOutput()
                sock.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
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
    return
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
                    "pause" -> "com.tabslify.ACTION_MUSIC_PAUSE"
                    "next" -> "com.tabslify.ACTION_MUSIC_NEXT"
                    "previous" -> "com.tabslify.ACTION_MUSIC_PREVIOUS"
                    else -> "com.tabslify.ACTION_MUSIC_PLAY"
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
                    if (laptopIp.isEmpty()) return@launch
                    Socket().use { sock ->
                        sock.connect(
                            InetSocketAddress(laptopIp, Config.EXECUTE_RESPONSE_PORT),
                            3000
                        )
                        sock.getOutputStream().use { output ->
                            output.write(results.toString().toByteArray(Charsets.UTF_8))
                            output.flush()
                        }
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
                    if (laptopIp.isEmpty()) return@launch
                    Socket().use { sock ->
                        sock.connect(
                            InetSocketAddress(laptopIp, Config.EXECUTE_RESPONSE_PORT),
                            3000
                        )
                        sock.getOutputStream().use { output ->
                            output.write(sizePrefix + jsonBytes)
                            output.flush()
                        }
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
                    .background(APP_COLOR),
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
                sock.getOutputStream().use { output ->
                    output.write(
                        JSONObject().apply { put("prompt", userInput) }.toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                    output.flush()
                }
                sock.shutdownOutput()
            }
        } catch (e: Exception) {
            logError("sendAiExecuteCommand", e)
        }
    }
}

private suspend fun fetchIpFromSupabase(mobile: Boolean = false): String? =
    withContext(Dispatchers.IO) {
        repeat(3) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                val column = if (!mobile) "laptop" else "handy"
                val url = "${Config.SUPABASE_URL}/rest/v1/device_ips" +
                        "?device_id=eq.$column&select=ip_address"

                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache, no-store")
                connection.setRequestProperty("Pragma", "no-cache")
                connection.setRequestProperty("apikey", Config.SUPABASE_PUBLISHABLE_KEY)
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer ${Config.SUPABASE_PUBLISHABLE_KEY}"
                )

                val code = connection.responseCode

                if (code == 200) {
                    val jsonArray = JSONArray(connection.inputStream.bufferedReader().readText())
                    if (jsonArray.length() > 0) {
                        val ip = jsonArray.getJSONObject(0).optString("ip_address", "")
                        if (ip.isNotEmpty()) return@withContext ip
                    }
                }
            } catch (e: Exception) {
                if (attempt == 2) logError("SupabaseFetch", e)
            } finally {
                connection?.disconnect()
            }

            if (attempt < 2) delay((1000L * (attempt + 1)).milliseconds)
        }
        null
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
            connection.setRequestProperty("Cache-Control", "no-cache, no-store")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.useCaches = false
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

            return@withContext if (connection.responseCode in 200..201) {
                Log.d("CLOUDSA", "Mobile IP successfully inserted: $ipAddress")
                true
            } else {
                val errorBody = if (connection.responseCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: ""
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

fun getHotspotIp(): String? {
    val interfaces = NetworkInterface.getNetworkInterfaces()

    for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue

        for (addr in intf.inetAddresses) {
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                val ip = addr.hostAddress
                if (ip != null) {
                    if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.16.")) {
                        return ip
                    }
                }
            }
        }
    }
    return null
}

fun reportDeviceInformation(intent: Intent) {
    val now = System.currentTimeMillis()
    if (now - cache.lastSync < 3000) return
    cache.lastSync = now
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).takeIf { it >= 0 }.toString()
        .ifEmpty { return }
    val temp =
        intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).takeIf { it != -1 }?.div(10f)
            .toString().ifEmpty { return }
    val plugged =
        intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0).toString().ifEmpty { return }

    val pluggedb = plugged != "0"
    val bl = buildString {
        if (cache.batteryLevel != level) append("battery_level=$level ")
        if (cache.temperature != temp) append("temp=$temp ")
        if (cache.plugged != pluggedb) append("charging=$pluggedb")
    }.trim()
    cache.batteryLevel = level
    cache.plugged = pluggedb
    cache.temperature = temp

    if (bl.isEmpty()) return

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val currentLaptopIp = laptopIp.ifEmpty { return@launch }
            Socket(currentLaptopIp, INFO_PORT).use { socket ->
                socket.getOutputStream().write(bl.toByteArray())
                socket.shutdownOutput()
            }
        } catch (e: Exception) {
            println("Socket error: ${e.message}")
        }
    }
}

fun startSmsListener(context: Context) {
    if (smsJob?.isActive == true) return
    smsJob = launchServer(syncScope, Config.SMS_PORT, "startSmsListener") { client ->
        val command = BufferedReader(InputStreamReader(client.getInputStream())).readLine()?.trim()
        if (command == "fetch") {
            val json = readAllSms(context)
            client.getOutputStream().apply {
                write(json.toByteArray(Charsets.UTF_8))
                flush()
            }
        }
        client.close()
    }
}

private fun readAllSms(context: Context): String {
    val arr = JSONArray()
    try {
        context.contentResolver.query(
            "content://sms/inbox".toUri(),
            arrayOf("_id", "address", "body", "date", "read"),
            null, null, "date DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow("_id")
            val addrCol = cursor.getColumnIndexOrThrow("address")
            val bodyCol = cursor.getColumnIndexOrThrow("body")
            val dateCol = cursor.getColumnIndexOrThrow("date")
            val readCol = cursor.getColumnIndexOrThrow("read")
            while (cursor.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("id", cursor.getLong(idCol))
                    put("address", cursor.getString(addrCol) ?: "")
                    put("body", cursor.getString(bodyCol) ?: "")
                    put("date", cursor.getLong(dateCol))
                    put("read", cursor.getInt(readCol) == 1)
                })
            }
        }
    } catch (e: Exception) {
        logError("readAllSms", e)
    }
    return arr.toString()
}