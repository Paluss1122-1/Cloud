@file:Suppress("AssignedValueIsNeverRead")

package com.cloud.remotedesktop

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.wifi.WifiManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cloud.ui.theme.Cloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private class TouchableImageView(context: Context) : ImageView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

data class RemoteHost(
    val name: String,
    val ip: String,
    val sessionId: String,
    val port: Int,
    val signalStrength: Int = 100,
)

sealed class RemoteDesktopState {
    object ModeSelection : RemoteDesktopState()
    object Discovering : RemoteDesktopState()
    data class HostList(val hosts: List<RemoteHost>) : RemoteDesktopState()
    data class Connecting(val host: RemoteHost) : RemoteDesktopState()
    data class Connected(val host: RemoteHost, val latencyMs: Int = 0) : RemoteDesktopState()
    data class Hosting(
        val ip: String,
        val pin: String,
        val connected: Boolean = false,
        val clientIp: String = ""
    ) : RemoteDesktopState()

    data class Error(val message: String, val canRetry: Boolean = true) : RemoteDesktopState()
}

class PerformanceMonitor(private val windowSize: Int = 60) {
    private val decodeTimes = ArrayDeque<Long>()
    private val receiveTimes = ArrayDeque<Long>()
    private var totalFrames = 0L
    private var lastLogTime = System.currentTimeMillis()

    fun recordFrame(decodeMs: Long, receiveMs: Long) {
        totalFrames++
        decodeTimes.add(decodeMs)
        receiveTimes.add(receiveMs)
        if (decodeTimes.size > windowSize) decodeTimes.removeFirst()
        if (receiveTimes.size > windowSize) receiveTimes.removeFirst()
    }

    fun shouldLog(intervalMs: Long = 5000): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLogTime >= intervalMs) {
            lastLogTime = now; return true
        }
        return false
    }

    fun getStats(): String {
        if (decodeTimes.isEmpty()) return "No data"
        return "Avg Decode: %.1fms | Avg Receive: %.1fms | Total Frames: %d"
            .format(decodeTimes.average(), receiveTimes.average(), totalFrames)
    }
}

class RemoteDesktopViewModel : ViewModel() {

    private val _state = MutableStateFlow<RemoteDesktopState>(RemoteDesktopState.ModeSelection)
    val state: StateFlow<RemoteDesktopState> = _state

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame

    private var webSocket: WebSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryJob: Job? = null
    private var frameDecodeJob: Job? = null
    private var frameCounter = 0L
    private var lastFrameTime = 0L
    private var lastMoveSentTime = 0L

    // Pipeline flow control
    private val pendingDecodes = AtomicInteger(0)
    private var pipelineDepth = 2  // updated from server 'ok' message
    private var activeWebSocket: WebSocket? = null  // ref for ready signals during decode

    // Bitmap reuse: pre-allocated RGB_565 bitmap pool (latest only)
    private val reuseBitmapRef = AtomicReference<Bitmap?>(null)

    private val perfMonitor = PerformanceMonitor()

    private val MOVE_THROTTLE_MS = 16L

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "RemoteDesktopVM"
        private const val DISCOVERY_PORT = 54321
        private const val BROADCAST_INTERVAL_MS = 1500L
        private const val DISCOVERY_TIMEOUT_MS = 3000L
    }

    fun selectConnectMode() {
        _state.value = RemoteDesktopState.Discovering
    }

    fun selectHostMode() {
        _state.value =
            RemoteDesktopState.Error("Host-Modus nicht verfügbar auf Android", canRetry = false)
    }

    fun backToModeSelection() {
        disconnect()
        _state.value = RemoteDesktopState.ModeSelection
    }

    fun startDiscovery(context: Context) {
        discoveryJob?.cancel()
        _state.value = RemoteDesktopState.Discovering
        Log.i(TAG, "[DISCOVERY] Starte Netzwerk-Scan...")

        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            val foundHosts = mutableListOf<RemoteHost>()
            var socket: DatagramSocket? = null
            try {
                val wifiManager =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifiManager.createMulticastLock("RemoteDesktop").apply {
                    setReferenceCounted(true)
                    acquire()
                }

                socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 500

                val msg = JSONObject().apply { put("type", "discover"); put("client", "android") }
                    .toString().toByteArray()
                val broadcast = InetAddress.getByName("255.255.255.255")
                val startTime = System.currentTimeMillis()
                var broadcastCount = 0

                while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS && isActive) {
                    try {
                        socket.send(DatagramPacket(msg, msg.size, broadcast, DISCOVERY_PORT))
                        broadcastCount++
                        Log.d(TAG, "[DISCOVERY] Broadcast #$broadcastCount")

                        val buf = ByteArray(1024)
                        val pkt = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(pkt)
                            val json = JSONObject(String(pkt.data, 0, pkt.length))
                            if (json.optString("type") == "announce") {
                                val host = RemoteHost(
                                    name = json.optString("name", "Unbekannt"),
                                    ip = pkt.address.hostAddress ?: "",
                                    sessionId = json.optString("session_id", ""),
                                    port = json.optInt("port", 54322),
                                )
                                if (foundHosts.none { it.ip == host.ip }) {
                                    foundHosts.add(host)
                                    Log.i(TAG, "[DISCOVERY] ✓ ${host.name} (${host.ip})")
                                    withContext(Dispatchers.Main) {
                                        _state.value =
                                            RemoteDesktopState.HostList(foundHosts.toList())
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[DISCOVERY] Broadcast-Fehler", e)
                    }
                    delay(BROADCAST_INTERVAL_MS)
                }

                withContext(Dispatchers.Main) {
                    if (foundHosts.isEmpty()) {
                        _state.value = RemoteDesktopState.Error(
                            "Keine Hosts gefunden. Beide Geräte im selben WLAN?",
                            canRetry = true
                        )
                    } else {
                        _state.value = RemoteDesktopState.HostList(foundHosts)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[DISCOVERY] Fehler", e)
                withContext(Dispatchers.Main) {
                    _state.value = RemoteDesktopState.Error(
                        "Discovery fehlgeschlagen: ${e.message}",
                        canRetry = true
                    )
                }
            } finally {
                socket?.close()
                multicastLock?.release()
                multicastLock = null
            }
        }
    }

    fun retryDiscovery(context: Context) = startDiscovery(context)

    fun connectToHost(host: RemoteHost, pin: String) {
        Log.i(TAG, "[CONN] Verbinde zu ${host.ip}:${host.port}")
        _state.value = RemoteDesktopState.Connecting(host)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("ws://${host.ip}:${host.port}").build()

                val listener = object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        Log.i(TAG, "[WS] Geöffnet")
                        ws.send(JSONObject().apply { put("type", "hello"); put("pin", pin) }
                            .toString())
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        Log.d(TAG, "[WS-TEXT] $text")
                        try {
                            val json = JSONObject(text)
                            when (json.optString("type")) {
                                "ok" -> {
                                    // Lese pipeline_depth vom Server (Fallback: 2)
                                    pipelineDepth = json.optInt("pipeline_depth", 2)
                                    pendingDecodes.set(0)
                                    activeWebSocket = ws
                                    Log.i(TAG, "[AUTH] ✓ Pipeline-Depth=$pipelineDepth")

                                    viewModelScope.launch(Dispatchers.Main) {
                                        _state.value = RemoteDesktopState.Connected(host)
                                        lastFrameTime = System.currentTimeMillis()
                                    }
                                    // Initialisiere Pipeline: sende pipelineDepth ready-Signale
                                    repeat(pipelineDepth) { sendReadySignal(ws) }
                                }

                                "denied" -> {
                                    viewModelScope.launch(Dispatchers.Main) {
                                        _state.value = RemoteDesktopState.Error(
                                            "Verbindung abgelehnt – falscher PIN?",
                                            canRetry = true
                                        )
                                    }
                                    ws.close(1000, "Denied")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "[WS-TEXT] Parse-Fehler", e)
                        }
                    }

                    override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                        val receiveTime = System.currentTimeMillis()
                        val byteArray = bytes.toByteArray()
                        Log.d(TAG, "[WS-BIN] Frame empfangen: ${byteArray.size} bytes")

                        // Pipeline: zu viele laufende Decodes → überspringen
                        if (pendingDecodes.get() > pipelineDepth) {
                            Log.w(TAG, "[FLOW] Übersprungen – $pendingDecodes laufende Decodes")
                            return
                        }
                        pendingDecodes.incrementAndGet()

                        frameDecodeJob?.cancel()
                        frameDecodeJob = viewModelScope.launch(Dispatchers.Default) {
                            try {
                                val decodeStart = System.currentTimeMillis()

                                // RGB_565: halb so viel RAM, ~20% schnelleres Decode
                                val opts = BitmapFactory.Options().apply {
                                    inPreferredConfig = Bitmap.Config.RGB_565
                                    // inBitmap wiederverwenden wenn Größe passt
                                    reuseBitmapRef.get()?.let { prev ->
                                        if (!prev.isRecycled) {
                                            inBitmap = prev
                                            inMutable = true
                                        }
                                    }
                                }

                                val bitmap = try {
                                    BitmapFactory.decodeByteArray(
                                        byteArray,
                                        0,
                                        byteArray.size,
                                        opts
                                    )
                                } catch (_: IllegalArgumentException) {
                                    // inBitmap inkompatibel → ohne Wiederverwendung
                                    BitmapFactory.decodeByteArray(
                                        byteArray, 0, byteArray.size,
                                        BitmapFactory.Options()
                                            .apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                                }

                                val decodeTime = System.currentTimeMillis() - decodeStart

                                if (bitmap != null) {
                                    Log.d(
                                        TAG,
                                        "[DECODE] ✓ ${decodeTime}ms (${bitmap.width}x${bitmap.height})"
                                    )

                                    reuseBitmapRef.getAndSet(bitmap)
                                    // Altes Bitmap nicht recyclen – könnte noch im ImageView sein
                                    // GC übernimmt (RGB_565 ist halb so groß)

                                    _currentFrame.value = bitmap
                                    frameCounter++

                                    val latency =
                                        (System.currentTimeMillis() - lastFrameTime).toInt()
                                    lastFrameTime = System.currentTimeMillis()

                                    perfMonitor.recordFrame(decodeTime, receiveTime - lastFrameTime)
                                    if (perfMonitor.shouldLog()) Log.i(
                                        TAG,
                                        "[STATS] ${perfMonitor.getStats()}"
                                    )

                                    val cur = _state.value
                                    if (cur is RemoteDesktopState.Connected) {
                                        launch(Dispatchers.Main.immediate) {
                                            _state.value = cur.copy(latencyMs = latency)
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "[DECODE] ✗ Dekodierung fehlgeschlagen")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "[DECODE] Fehler", e)
                            } finally {
                                pendingDecodes.decrementAndGet()
                                // Ready-Signal → Server darf nächsten Frame senden
                                activeWebSocket?.let { sendReadySignal(it) }
                            }
                        }
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "[WS] Verbindung fehlgeschlagen", t)
                        viewModelScope.launch(Dispatchers.Main) {
                            _state.value = RemoteDesktopState.Error(
                                "Verbindung fehlgeschlagen: ${t.message}",
                                canRetry = true
                            )
                        }
                    }

                    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "[WS] Schließt: [$code] $reason")
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "[WS] Geschlossen: [$code] $reason")
                        viewModelScope.launch(Dispatchers.Main) {
                            if (_state.value is RemoteDesktopState.Connected) {
                                _state.value =
                                    RemoteDesktopState.Error("Verbindung getrennt", canRetry = true)
                            }
                        }
                    }
                }

                webSocket = okHttpClient.newWebSocket(request, listener)
            } catch (e: Exception) {
                Log.e(TAG, "[CONN] Fehler", e)
                withContext(Dispatchers.Main) {
                    _state.value = RemoteDesktopState.Error(
                        "Verbindung fehlgeschlagen: ${e.message}",
                        canRetry = true
                    )
                }
            }
        }
    }

    private fun sendReadySignal(ws: WebSocket) {
        try {
            val sent = ws.send(JSONObject().apply { put("type", "ready") }.toString())
            Log.d(TAG, "[FLOW] Ready-Signal gesendet: $sent")
        } catch (e: Exception) {
            Log.e(TAG, "[FLOW] Ready-Signal fehlgeschlagen", e)
        }
    }

    fun sendTouchEvent(x: Float, y: Float, action: String) {
        if (action == "move") {
            val now = System.currentTimeMillis()
            if (now - lastMoveSentTime < MOVE_THROTTLE_MS) {
                Log.v(TAG, "[INPUT] Move-Event gedrosselt")
                return
            }
            lastMoveSentTime = now
        }

        webSocket?.let { ws ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val event = JSONObject().apply {
                        put("type", "touch")
                        put("x", x)
                        put("y", y)
                        put("action", action)
                    }.toString()
                    val t0 = System.currentTimeMillis()
                    val sent = ws.send(event)
                    val ms = System.currentTimeMillis() - t0
                    if (ms > 10) Log.w(TAG, "[INPUT] Touch dauerte ${ms}ms: $action")
                    else Log.v(TAG, "[INPUT] Touch: $action (${ms}ms)")
                    if (!sent) Log.w(TAG, "[INPUT] ✗ Nicht gesendet")
                } catch (e: Exception) {
                    Log.e(TAG, "[INPUT] Fehler", e)
                }
            }
        }
    }

    fun disconnect() {
        Log.i(TAG, "[CONN] Trennung...")
        frameDecodeJob?.cancel()
        frameDecodeJob = null
        activeWebSocket = null
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _currentFrame.value = null
        frameCounter = 0
        pendingDecodes.set(0)
        discoveryJob?.cancel()
        multicastLock?.release()
        multicastLock = null
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[NET] IP-Fehler", e)
        }
        return "Unbekannt"
    }
}

@Composable
fun RemoteDesktopTabContent() {
    val viewModel: RemoteDesktopViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(100)
        alpha.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Transparent)
        .alpha(alpha.value)) {
        when (val s = state) {
            is RemoteDesktopState.ModeSelection -> ModeSelectionScreen(
                onConnect = { viewModel.selectConnectMode(); viewModel.startDiscovery(context) },
                onHost = { viewModel.selectHostMode() }
            )

            is RemoteDesktopState.Discovering -> DiscoveryScreen(onCancel = { viewModel.backToModeSelection() })
            is RemoteDesktopState.HostList -> HostListScreen(
                hosts = s.hosts,
                onSelectHost = { host, pin -> viewModel.connectToHost(host, pin) },
                onRetry = { viewModel.retryDiscovery(context) },
                onBack = { viewModel.backToModeSelection() }
            )

            is RemoteDesktopState.Connecting -> ConnectingScreen(
                host = s.host,
                onCancel = { viewModel.backToModeSelection() })

            is RemoteDesktopState.Connected -> ConnectedScreen(
                viewModel = viewModel, host = s.host, latencyMs = s.latencyMs,
                onDisconnect = { viewModel.backToModeSelection() }
            )

            is RemoteDesktopState.Hosting -> HostingScreen(
                ip = s.ip,
                pin = s.pin,
                connected = s.connected,
                clientIp = s.clientIp,
                onStop = { viewModel.backToModeSelection() })

            is RemoteDesktopState.Error -> ErrorScreen(
                message = s.message,
                canRetry = s.canRetry,
                onRetry = { viewModel.retryDiscovery(context) },
                onBack = { viewModel.backToModeSelection() })
        }
    }
}

@Composable
fun ModeSelectionScreen(onConnect: () -> Unit, onHost: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "🖥️ Remote Desktop",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Cloud),
            onClick = onConnect
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Computer,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Verbinden",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Auf einen PC zugreifen",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.5f)),
            onClick = onHost
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Freigeben",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        "Dieses Gerät freigeben (nicht verfügbar)",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoveryScreen(onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp), color = Color.White)
            Spacer(Modifier.height(24.dp))
            Text("Suche im Netzwerk...", fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(
                "Stelle sicher, dass beide Geräte\nim selben WLAN sind",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) { Text("Abbrechen") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    hosts: List<RemoteHost>,
    onSelectHost: (RemoteHost, String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    var selectedHost by remember { mutableStateOf<RemoteHost?>(null) }
    var pin by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Zurück",
                    tint = Color.White
                )
            }
            Text(
                "Gefundene Hosts (${hosts.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    "Erneut suchen",
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hosts) { host ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Cloud),
                    onClick = { selectedHost = host; showDialog = true }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                host.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(host.ip, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                    }
                }
            }
        }
    }

    if (showDialog && selectedHost != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color.DarkGray,
            title = { Text("PIN eingeben", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Verbinde mit: ${selectedHost!!.name}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pin, onValueChange = { if (it.length <= 6) pin = it },
                        label = { Text("PIN", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (pin.isNotEmpty()) {
                                onSelectHost(selectedHost!!, pin); showDialog = false
                            }
                        }),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Cloud,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pin.isNotEmpty()) {
                        onSelectHost(selectedHost!!, pin); showDialog = false
                    }
                }, enabled = pin.isNotEmpty()) { Text("Verbinden") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        "Abbrechen",
                        color = Color.Gray
                    )
                }
            }
        )
    }
}

@Composable
fun ConnectingScreen(host: RemoteHost, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp), color = Color.White)
            Spacer(Modifier.height(24.dp))
            Text("Verbinde mit ${host.name}...", fontSize = 18.sp, color = Color.White)
            Text(host.ip, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) { Text("Abbrechen") }
        }
    }
}

@Composable
fun ConnectedScreen(
    viewModel: RemoteDesktopViewModel,
    host: RemoteHost,
    latencyMs: Int,
    onDisconnect: () -> Unit
) {
    val currentFrame by viewModel.currentFrame.collectAsState()
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }
    var isFullscreen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        if (isFullscreen) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlay = ComposeView(context).apply {
                setViewTreeLifecycleOwner(activity as? LifecycleOwner)
                setViewTreeSavedStateRegistryOwner(activity as? SavedStateRegistryOwner)
                setViewTreeViewModelStoreOwner(activity as? ViewModelStoreOwner)
                @Suppress("DEPRECATION")
                systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
                setContent {
                    val frame by viewModel.currentFrame.collectAsState()
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)) {
                        AndroidView(
                            factory = { ctx ->
                                TouchableImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setOnTouchListener { v, event ->
                                        val action = when (event.action) {
                                            MotionEvent.ACTION_DOWN -> "down"
                                            MotionEvent.ACTION_UP -> {
                                                v.performClick()
                                                "up"
                                            }
                                            MotionEvent.ACTION_MOVE -> "move"
                                            else -> return@setOnTouchListener false
                                        }
                                        viewModel.sendTouchEvent(
                                            event.x / v.width.toFloat(),
                                            event.y / v.height.toFloat(),
                                            action
                                        )
                                        true
                                    }
                                }
                            },
                            update = { iv -> frame?.let { iv.setImageBitmap(it) } },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }) {
                                    isFullscreen = false
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.FullscreenExit, null, tint = Color.White) }
                    }
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            wm.addView(overlay, params)
            onDispose {
                try {
                    wm.removeView(overlay)
                } catch (_: Exception) {
                }
                activity?.window?.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            }
        } else {
            activity?.window?.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            onDispose {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TouchableImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setOnTouchListener { v, event ->
                        viewWidth = v.width.toFloat()
                        viewHeight = v.height.toFloat()
                        val action = when (event.action) {
                            MotionEvent.ACTION_DOWN -> "down"
                            MotionEvent.ACTION_UP -> {
                                v.performClick()
                                "up"
                            }
                            MotionEvent.ACTION_MOVE -> "move"
                            else -> return@setOnTouchListener false
                        }
                        viewModel.sendTouchEvent(event.x / viewWidth, event.y / viewHeight, action)
                        true
                    }
                }
            },
            update = { iv -> currentFrame?.let { iv.setImageBitmap(it) } },
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Speed,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${latencyMs}ms", color = Color.White, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
                Text(
                    "${host.name} (${host.ip})",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    "Vollbild", tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(6.dp)
                )
            }
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Default.Close, "Trennen", tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(6.dp)
                )
            }
        }

        if (currentFrame == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Warte auf ersten Frame...", color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "💡 Querformat für beste Darstellung",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HostingScreen(
    ip: String,
    pin: String,
    connected: Boolean,
    clientIp: String,
    onStop: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Host-Modus nicht verfügbar", color = Color.White, fontSize = 18.sp)
    }
}

@Composable
fun ErrorScreen(message: String, canRetry: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = Color.Red)
            Spacer(Modifier.height(24.dp))
            Text("Fehler", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) { Text("Zurück") }
                if (canRetry) Button(onClick = onRetry) { Text("Erneut versuchen") }
            }
        }
    }
}