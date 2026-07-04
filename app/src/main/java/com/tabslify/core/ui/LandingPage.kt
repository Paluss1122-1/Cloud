package com.tabslify.core.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tabslify.R
import com.tabslify.core.functions.canNotify
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.client
import com.tabslify.core.objects.Config.realDevice
import com.tabslify.core.objects.PasswordStorage
import com.tabslify.core.objects.prvt
import com.tabslify.quicksettingsfunctions.ChargingTrackerService
import com.tabslify.quicksettingsfunctions.startBatteryWorker
import com.tabslify.quicksettingsfunctions.stopBatteryWorker
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.WhatsAppNotificationListener
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

val inAppNotifications = mutableStateListOf<String>()

fun sendInAppNotification(message: String) {
    inAppNotifications.add(0, message)
}

fun saveRecentTab(context: Context, menuItem: MenuItem) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val recentTabsString = prefs.getString(KEY_RECENT_TABS, "") ?: ""
    val recentTabs = recentTabsString.split(",").filter { it.isNotEmpty() }.toMutableList()

    recentTabs.remove(menuItem.name)
    recentTabs.add(0, menuItem.name)
    val trimmedTabs = recentTabs.take(MAX_RECENT_TABS)

    prefs.edit {
        putString(KEY_RECENT_TABS, trimmedTabs.joinToString(","))
    }
}

fun loadRecentTabs(context: Context): List<MenuItem> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val recentTabsString = prefs.getString(KEY_RECENT_TABS, "") ?: ""
    return recentTabsString.split(",")
        .filter { it.isNotEmpty() }
        .mapNotNull { name ->
            try {
                MenuItem.valueOf(name)
            } catch (_: Exception) {
                null
            }
        }
}

fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }
    val model = Build.MODEL
    return if (model.startsWith(manufacturer, ignoreCase = true)) {
        model.replaceFirstChar { it.uppercaseChar() }
    } else {
        "$manufacturer $model"
    }
}

@Composable
fun LandingPageOrApp(storage: Storage, startTarget: String?) {
    val context = LocalContext.current
    var hasLoadedApp by rememberSaveable { mutableStateOf(startTarget != null) }
    var selectedMenuItem by rememberSaveable { mutableStateOf<MenuItem?>(null) }
    var masterPw by remember { mutableStateOf(PasswordStorage.loadPassword(context)) }
    var supabaseReady by remember { mutableStateOf(false) }
    val landingListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    var reloadKey by remember { mutableIntStateOf(0) }
    realDevice = !getDeviceName().trim().contains("sdk_gphone", ignoreCase = true)
    var landingReloadTrigger by remember { mutableIntStateOf(0) }

    if (realDevice && prvt()) {
        if (masterPw == null || Config.masterPassword.isEmpty()) {
            MasterPasswordSetupScreen { pw ->
                PasswordStorage.savePassword(context, pw)
                Config.masterPassword = pw
            }
            return
        }
        Config.masterPassword = masterPw!!

        val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
        var hasCoords by remember { mutableStateOf(prefs.getBoolean("has_coordinates", false)) }
        if (!hasCoords) {
            CoordinatesSetupScreen { lat, lon ->
                prefs.edit {
                    putFloat("lat_key", lat.toFloat())
                    putFloat("lon_key", lon.toFloat())
                    putBoolean("has_coordinates", true)
                }
                Config.LAT = lat
                Config.LON = lon
                hasCoords = true
            }
            return
        }
    }

    LaunchedEffect(Unit) {
        if (prvt()) {
            client.auth.awaitInitialization()
            supabaseReady = client.auth.currentSessionOrNull() != null
        }
    }

    if (!supabaseReady && prvt()) {
        SupabaseLoginScreen { supabaseReady = true }
        return
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    LaunchedEffect(startTarget) {
        if (startTarget != null && selectedMenuItem == null) {
            selectedMenuItem = when (startTarget) {
                "weather" -> MenuItem.WEATHER
                "aitab" -> MenuItem.AITAB
                else -> null
            }
            if (selectedMenuItem != null) {
                saveRecentTab(context, selectedMenuItem!!)
            }
        }
    }

    val landingOffsetX = remember { Animatable(if (!hasLoadedApp) 0f else -1f) }
    val scope = rememberCoroutineScope()

    fun openLanding() {
        landingReloadTrigger++
        scope.launch {
            landingOffsetX.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    fun closeLanding(force: Boolean = false, then: (() -> Unit)? = null) {
        scope.launch {
            landingOffsetX.animateTo(
                -1f,
                tween(if (force) 1 else 300, easing = FastOutSlowInEasing)
            )
            then?.invoke()
            landingOffsetX.snapTo(-1f)
        }
    }

    var pendingOverlayItem by remember { mutableStateOf<MenuItem?>(null) }
    val overlayScale = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(0f) }

    Box(modifier = Modifier.fillMaxSize()) {

        if (hasLoadedApp) {
            val targetMenuItem = selectedMenuItem ?: startTarget?.let { target ->
                when (target) {
                    "weather" -> MenuItem.WEATHER
                    else -> null
                }
            }

            key(selectedMenuItem, reloadKey) {
                Box(Modifier.fillMaxSize()) {
                    if (targetMenuItem != null) {
                        PrivateTabslifyApp(
                            storage = storage,
                            startTarget = null,
                            initialMenuItem = targetMenuItem,
                            onMenuClick = { openLanding() }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = landingOffsetX.value * size.width
                }
        ) {
            LandingPage(
                showCloseButton = hasLoadedApp,
                onClose = { closeLanding() },
                onTabSelected = { menuItem -> pendingOverlayItem = menuItem },
                state = landingListState,
                reloadTrigger = landingReloadTrigger
            )
        }

        if (pendingOverlayItem != null) {
            val item = pendingOverlayItem!!
            val graphicsLayer = rememberGraphicsLayer()
            var previewBitmap by remember(item) { mutableStateOf<ImageBitmap?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = 10000f }
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                    .background(APP_COLOR)
            ) {
                PrivateTabslifyApp(
                    storage = storage,
                    startTarget = null,
                    initialMenuItem = item,
                    onMenuClick = null
                )
            }

            LaunchedEffect(item) {
                repeat(5) { withFrameNanos { } }
                val captured = graphicsLayer.toImageBitmap()
                previewBitmap = captured

                snapshotFlow { previewBitmap }.filterNotNull().first()

                overlayScale.snapTo(0.05f)
                overlayAlpha.snapTo(1f)

                overlayScale.animateTo(
                    1f, tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )

                closeLanding(true)

                selectedMenuItem = item
                saveRecentTab(context, item)

                reloadKey++

                if (!hasLoadedApp) {
                    hasLoadedApp = true
                }

                delay(80.milliseconds)
                overlayAlpha.animateTo(0f, tween(durationMillis = 200))
                previewBitmap = null
                overlayScale.snapTo(0f)
                pendingOverlayItem = null
            }

            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = overlayScale.value
                            scaleY = overlayScale.value
                            alpha = overlayAlpha.value
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                ) {
                    Image(
                        bitmap = previewBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }
    }
}

@Composable
fun MasterPasswordSetupScreen(onPasswordSaved: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf("") }
    val isValid = input.length >= 20 && input == confirmed

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF17171C)), contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "🔑 Master-Passwort einrichten",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Wird einmalig gesetzt. Mindestens 20 Zeichen.",
                color = Color(0xFF8A8A9F),
                fontSize = 13.sp
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Passwort") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmed,
                onValueChange = { confirmed = it },
                label = { Text("Wiederholen") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (input.isNotEmpty() && input != confirmed)
                Text(
                    "Passwörter stimmen nicht überein",
                    color = Color(0xFFE74C3C),
                    fontSize = 12.sp
                )
            if (input.isNotEmpty() && input.length < 20)
                Text("Mindestens 20 Zeichen", color = Color(0xFFE74C3C), fontSize = 12.sp)

            Button(
                onClick = { onPasswordSaved(input) },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Speichern & Starten") }
        }
    }
}

@Composable
fun CoordinatesSetupScreen(onCoordinatesSaved: (Double, Double) -> Unit) {
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    val latDouble = latInput.toDoubleOrNull()
    val lonDouble = lonInput.toDoubleOrNull()
    val isValid = latDouble != null && lonDouble != null

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF17171C)), contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "📍 Koordinaten einrichten",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Bitte gib deine Standard-Koordinaten (Latitude und Longitude) ein.",
                color = Color(0xFF8A8A9F),
                fontSize = 13.sp
            )

            OutlinedTextField(
                value = latInput,
                onValueChange = { latInput = it },
                label = { Text("Breitengrad (Latitude)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lonInput,
                onValueChange = { lonInput = it },
                label = { Text("Längengrad (Longitude)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (latInput.isNotEmpty() && latDouble == null)
                Text(
                    "Ungültiger Breitengrad",
                    color = Color(0xFFE74C3C),
                    fontSize = 12.sp
                )
            if (lonInput.isNotEmpty() && lonDouble == null)
                Text(
                    "Ungültiger Längengrad",
                    color = Color(0xFFE74C3C),
                    fontSize = 12.sp
                )

            Button(
                onClick = { onCoordinatesSaved(latDouble!!, lonDouble!!) },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Speichern & Starten") }
        }
    }
}

@Composable
fun SupabaseLoginScreen(onLoggedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Tabslify Login",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-Mail") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Box(
            modifier = Modifier
                .height(36.dp)
                .padding(top = 8.dp)
        ) {
            error?.let {
                val formattedError = when {
                    it.contains("invalid_credentials") -> "Invalid Credentials"
                    else -> it
                }
                Text(formattedError, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            loading = true
            scope.launch {
                try {
                    client.auth.signInWith(Email) { this.email = email; this.password = password }
                    onLoggedIn()
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    loading = false
                }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (loading) "..." else "Anmelden")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingPage(
    onTabSelected: (MenuItem) -> Unit,
    showCloseButton: Boolean = false,
    onClose: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    reloadTrigger: Int = 0
) {
    val context = LocalContext.current
    var recentTabs by remember { mutableStateOf(loadRecentTabs(context)) }
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(reloadTrigger) {
        recentTabs = loadRecentTabs(context)
    }
    val allTabsSorted = remember {
        MenuItem.entries.filter {
            prvt() || (it != MenuItem.GMAIL && it != MenuItem.PRIVATE_CLOUD && it != MenuItem.REMOTEDESKTOP && it != MenuItem.PC_MANAGER)
        }.sortedBy { it.title }
    }
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val neonOrange = c()
    val neonGlow = when (currentHour) {
        in 11..16 -> Color(0xFF2C2C2C)
        else -> Color(0xFF00177E)
    }

    val glowPaint = remember {
        Paint().apply {
            color = neonGlow.copy(alpha = 0.6f).toArgb()
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(
                18f, android.graphics.BlurMaskFilter.Blur.OUTER
            )
        }
    }

    val orangePaint = remember {
        Paint().apply {
            color = neonOrange.copy(alpha = 0.75f).toArgb()
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(
                10f, android.graphics.BlurMaskFilter.Blur.OUTER
            )
        }
    }

    val gradient = remember {
        val colors = when (currentHour) {
            in 11..16 -> listOf(
                APP_COLOR.copy(alpha = 0.85f),
                Color(0xFF001A93).copy(alpha = 0.35f)
            )

            else -> listOf(
                APP_COLOR.copy(alpha = 0.7f),
                Color(0xFF001A93).copy(alpha = 0.7f)
            )
        }
        Brush.linearGradient(colors = colors, start = Offset.Zero, end = Offset.Infinite)
    }
    val txtcolors = Color.White
    val bgpicture = remember {
        when (currentHour) {
            in 11..16 -> R.drawable.day; else -> R.drawable.night
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = bgpicture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        )

        CompositionLocalProvider(LocalContentColor provides txtcolors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 40.dp)
                ) {
                    if (showCloseButton) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Text(
                                "✕",
                                color = txtcolors,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Tabslify",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )

                    var showNotifications by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Open notification frame",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Open settings frame",
                                tint = Color.White
                            )
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        DropdownMenu(
                            expanded = showNotifications,
                            onDismissRequest = { showNotifications = false },
                            containerColor = Color.Black
                        ) {
                            if (inAppNotifications.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Keine Benachrichtigungen") },
                                    onClick = { showNotifications = false }
                                )
                            } else {
                                inAppNotifications.forEach { notif ->
                                    DropdownMenuItem(
                                        text = { Text(notif) },
                                        onClick = { showNotifications = false }
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    if (recentTabs.isNotEmpty()) {
                        item(key = "recent_header") {
                            Text(
                                text = "Zuletzt verwendet",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        items(items = recentTabs, key = { "recent_${it.ordinal}" }) { menuItem ->
                            TabCard(
                                menuItem = menuItem,
                                onClick = { onTabSelected(menuItem) },
                                glowPaint,
                                orangePaint,
                                neonOrange
                            )
                        }
                        item(key = "divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 16.dp),
                                thickness = 1.dp,
                                color = Color.White.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "Alle Tabs",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                    items(items = allTabsSorted, key = { "all_${it.ordinal}" }) { menuItem ->
                        TabCard(
                            menuItem = menuItem,
                            onClick = { onTabSelected(menuItem) },
                            glowPaint,
                            orangePaint,
                            neonOrange
                        )
                    }
                }
            }
        }
    }

    SettingsFrame(
        visible = showSettings,
        onClose = { showSettings = false }
    )
}

@Composable
fun TabCard(
    menuItem: MenuItem,
    onClick: () -> Unit,
    glowPaint: Paint,
    orangePaint: Paint,
    neonOrange: Color
) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(300))
    }

    Box(
        Modifier
            .drawBehind {
                val canvasSize = size
                val cornerRadius = 8.dp.toPx()

                drawContext.canvas.nativeCanvas.apply {
                    drawRoundRect(
                        0f, 0f, canvasSize.width, canvasSize.height,
                        cornerRadius, cornerRadius,
                        glowPaint
                    )
                    drawRoundRect(
                        0f, 0f, canvasSize.width, canvasSize.height,
                        cornerRadius, cornerRadius,
                        orangePaint
                    )
                }
            }
            .border(
                width = 1.5.dp,
                color = neonOrange,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.6f
                )
            ),
            shape = RoundedCornerShape(8.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = menuItem.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Left
                )
                Text(
                    text = menuItem.icon,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsFrame(
    visible: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var directedToSettings by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionInfo by remember { mutableStateOf(false) }
    var animatePermissionInfo by remember { mutableStateOf(false) }

    BackHandler {
        if (showPermissionInfo) {
            showPermissionInfo = false
        } else {
            onClose()
        }
    }

    var titleDialog by remember { mutableStateOf("") }
    var usagesDialog by remember { mutableStateOf<List<String>>(emptyList()) }
    val permissionUsages = mapOf(
        "READ_MEDIA_AUDIO" to listOf(
            "MediaPlayerService & Tab: Songs & Podcasts abspielen",
            "SpotifyDownloader: Speichern von Audiodateien im MediaStore"
        ),
        "POST_NOTIFICATIONS" to listOf(
            "QuietHoursNotificationService: Anzeigen von Quiet-Hours Benachrichtigungen",
            "MediaPlayerService: Wiedergabenotifikationen",
            "Status-Updates für laufende Downloads",
            "ChargingTrackerService: Ladeinformationen",
            "NetworkInfo: Netzwerkinfos",
            "BatteryInfo: Batterieinformationen"
        ),
        "ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION" to listOf(
            "Standortbasierte Wettervorhersage im WeatherTab",
            "ExploreTab & ExploreLocationTracker: Standortverfolgung und Geofencing",
            "showNetwerkInfo: Anzeige der WLAN-SSID"
        ),
        "SYSTEM_ALERT_WINDOW" to listOf(
            "QuietHoursNotificationService: Test-Overlay für YouTube"
        ),
        "FOREGROUND_SERVICE" to listOf(
            "MediaPlayerService: Medienwiedergabe im Vordergrund",
            "QuietHoursNotificationService: Dauerhafte Benachrichtigungen",
            "ChargingTrackerService: Ladeüberwachung",
            "AudioForegroundService: Audioaufnahmen"
        ),
        "READ_MEDIA_IMAGES / READ_MEDIA_VIDEO" to listOf(
            "GalleryTab: Anzeige von Bildern und Videos aus der Galerie"
        ),
        "READ_CONTACTS / WRITE_CONTACTS" to listOf(
            "ContactsTab: Laden, Speichern und Löschen von Kontakten"
        ),
        "RECEIVE_BOOT_COMPLETED" to listOf(
            "BootReceiver: Starten von QuietHoursNotificationService beim Gerätestart"
        ),
        "RECORD_AUDIO" to listOf(
            "AudioRecorderTab & AudioForegroundService: Audioaufnahmen mit Mikrofon"
        ),
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to listOf(
            "QuietHoursNotificationService: Anfrage zum Ignorieren von Batterieoptimierungen für zuverlässige Hintergrunddienste"
        ),
        "CAMERA" to listOf(
            "Authenticator: Scannen von QR Codes für 2FA"
        )
    )

    var masterEnabled by remember { mutableStateOf(prefs.getBoolean("services_master", false)) }
    var servicesExpanded by remember { mutableStateOf(false) }

    var serviceQhns by remember { mutableStateOf(prefs.getBoolean("service_qhns", true)) }
    var serviceWh by remember { mutableStateOf(prefs.getBoolean("service_wh", true)) }
    var serviceCharge by remember { mutableStateOf(prefs.getBoolean("service_charge", true)) }
    var serviceBattery by remember { mutableStateOf(prefs.getBoolean("service_battery", true)) }

    var aiPrefGlobal by remember {
        mutableStateOf(
            prefs.getString("ai_pref_global", "gemini") ?: "gemini"
        )
    }
    var aiPrefChat by remember {
        mutableStateOf(
            prefs.getString("ai_pref_service_chat", "default") ?: "default"
        )
    }
    var aiPrefMusicSummary by remember {
        mutableStateOf(
            prefs.getString(
                "ai_pref_service_music_summary",
                "default"
            ) ?: "default"
        )
    }
    var aiPrefVision by remember {
        mutableStateOf(
            prefs.getString(
                "ai_pref_service_vision",
                "default"
            ) ?: "default"
        )
    }
    var aiPrefReplies by remember {
        mutableStateOf(
            prefs.getString(
                "ai_pref_service_replies",
                "default"
            ) ?: "default"
        )
    }
    var aiSettingsExpanded by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember { mutableStateOf(true) }
    var intendedToEnableQhns by remember { mutableStateOf(false) }

    fun applyService(cls: Class<*>, enabled: Boolean) {
        if (cls == WhatsAppNotificationListener::class.java) return

        val intent = Intent(context, cls)
        val permissionOk =
            if (cls == QuietHoursNotificationService::class.java) hasNotificationPermission else true

        if (enabled && masterEnabled && permissionOk) {
            when (cls) {
                QuietHoursNotificationService::class.java,
                ChargingTrackerService::class.java -> ContextCompat.startForegroundService(
                    context,
                    intent
                )

                else -> context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    fun save() {
        prefs.edit {
            putBoolean("services_master", masterEnabled)
                .putBoolean("service_qhns", serviceQhns)
                .putBoolean("service_wh", serviceWh)
                .putBoolean("service_charge", serviceCharge)
                .putBoolean("service_battery", serviceBattery)
                .putString("ai_pref_global", aiPrefGlobal)
                .putString("ai_pref_service_chat", aiPrefChat)
                .putString("ai_pref_service_music_summary", aiPrefMusicSummary)
                .putString("ai_pref_service_vision", aiPrefVision)
                .putString("ai_pref_service_replies", aiPrefReplies)
        }

        applyService(QuietHoursNotificationService::class.java, serviceQhns)
        applyService(ChargingTrackerService::class.java, serviceCharge)

        if (masterEnabled && serviceBattery) {
            startBatteryWorker(context)
        } else {
            stopBatteryWorker(context)
        }
    }

    fun checkPermissionsAndSyncServices() {
        val permitted = canNotify(context)
        hasNotificationPermission = permitted

        masterEnabled = prefs.getBoolean("services_master", false)
        serviceWh = prefs.getBoolean("service_wh", true)
        serviceCharge = prefs.getBoolean("service_charge", true)
        serviceBattery = prefs.getBoolean("service_battery", true)

        if (permitted) {
            serviceQhns = if (intendedToEnableQhns) true else prefs.getBoolean("service_qhns", true)
            intendedToEnableQhns = false
        } else {
            serviceQhns = false
            intendedToEnableQhns = false
        }
        save()
    }

    val manualPerm = {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }

            context.startActivity(intent)
            directedToSettings = true
            intendedToEnableQhns = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        var isFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("YEP22")
                if (isFirstResume) {
                    println("YEP")
                    isFirstResume = false
                    return@LifecycleEventObserver
                }
                checkPermissionsAndSyncServices()
                directedToSettings = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(visible) {
        if (visible) {
            checkPermissionsAndSyncServices()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                //.verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Einstellungen",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                NeonBox(
                    modifier = Modifier.fillMaxWidth(),
                    neonColors = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)),
                    backgroundAlpha = 0.15f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Hintergrunddienste",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Alle Hintergrundaktivitäten",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = masterEnabled,
                                onCheckedChange = { masterEnabled = it; save() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray,
                                    disabledUncheckedTrackColor = Color.DarkGray.copy(alpha = 0.4f)
                                )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { servicesExpanded = !servicesExpanded }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Einzelne Dienste",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (servicesExpanded) "▲" else "▼",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }

                        AnimatedVisibility(visible = servicesExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                val services = if (!prvt()) emptyList() else listOfNotNull(
                                    Triple(
                                        "QuietHoursNotificationService",
                                        "Alles rund um Commands",
                                        serviceQhns
                                    ),
                                    if (prvt()) Triple(
                                        "WhatsAppNotificationListener",
                                        "Verarbeitet eingehende WhatsApps",
                                        serviceWh
                                    ) else null,
                                    Triple(
                                        "ChargingTracker",
                                        "Verfolgt Ladevorgänge für Ladedauer-Schätzungen",
                                        serviceCharge
                                    ),
                                    Triple(
                                        "BatterySamplingWorker",
                                        "Hintergrund-Aufzeichnung der Batteriedaten",
                                        serviceBattery
                                    )
                                )

                                services.forEachIndexed { index, (title, subtitle, checked) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(title, color = Color.White, fontSize = 14.sp)
                                            Text(
                                                subtitle,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp
                                            )
                                            if (index == 0 && !hasNotificationPermission) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "⚠️ Keine Benachrichtigungsberechtigung!",
                                                    color = Color(0xFFFF5252),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = checked && masterEnabled,
                                            onCheckedChange = { v ->
                                                when (title) {
                                                    "QuietHoursNotificationService" -> {
                                                        if (hasNotificationPermission) {
                                                            serviceQhns = v
                                                        } else {
                                                            manualPerm()
                                                        }
                                                    }
                                                    "WhatsAppNotificationListener" -> {
                                                        serviceWh = v
                                                    }
                                                    "ChargingTracker" -> {
                                                        serviceCharge = v
                                                    }
                                                    "BatterySamplingWorker" -> {
                                                        serviceBattery = v
                                                    }
                                                }
                                                save()
                                            },
                                            enabled = masterEnabled,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = Color.Gray,
                                                uncheckedTrackColor = Color.DarkGray,
                                                disabledCheckedTrackColor = Color.DarkGray,
                                                disabledCheckedThumbColor = Color.Gray
                                            )
                                        )
                                    }
                                    if (index < services.lastIndex) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                NeonBox(
                    modifier = Modifier.fillMaxWidth(),
                    neonColors = listOf(Color(0xFF00E5FF), Color(0xFFE8622A)),
                    backgroundAlpha = 0.15f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Bevorzugte AI (Global)",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Standard-Anbieter für alle Dienste",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }

                            var showGlobalDropdown by remember { mutableStateOf(false) }
                            Box {
                                Text(
                                    text = aiPrefGlobal.uppercase(),
                                    color = Color(0xFF00E5FF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showGlobalDropdown = true }
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.2f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                DropdownMenu(
                                    expanded = showGlobalDropdown,
                                    onDismissRequest = { showGlobalDropdown = false },
                                    containerColor = Color.Black
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Gemini", color = Color.White) },
                                        onClick = {
                                            aiPrefGlobal = "gemini"; showGlobalDropdown =
                                            false; save()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("NVIDIA", color = Color.White) },
                                        onClick = {
                                            aiPrefGlobal = "nvidia"; showGlobalDropdown =
                                            false; save()
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { aiSettingsExpanded = !aiSettingsExpanded }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Einzelne Dienste anpassen",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (aiSettingsExpanded) "▲" else "▼",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }

                        AnimatedVisibility(visible = aiSettingsExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                val aiServices = listOf(
                                    Triple(
                                        "Befehl 'ai' / Chat",
                                        aiPrefChat
                                    ) { v: String -> aiPrefChat = v },
                                    Triple(
                                        "Musik-Zusammenfassung",
                                        aiPrefMusicSummary
                                    ) { v: String -> aiPrefMusicSummary = v },
                                    Triple(
                                        "Vokabel-Vision",
                                        aiPrefVision
                                    ) { v: String -> aiPrefVision = v },
                                    Triple(
                                        "Nachrichten-Antworten",
                                        aiPrefReplies
                                    ) { v: String -> aiPrefReplies = v }
                                )

                                aiServices.forEachIndexed { index, (title, currentVal, setter) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        var showServiceDropdown by remember { mutableStateOf(false) }
                                        Box {
                                            Text(
                                                text = when (currentVal) {
                                                    "default" -> "Standard"
                                                    "gemini" -> "Gemini"
                                                    "nvidia" -> "NVIDIA"
                                                    else -> "Standard"
                                                },
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .clickable { showServiceDropdown = true }
                                                    .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.15f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                            DropdownMenu(
                                                expanded = showServiceDropdown,
                                                onDismissRequest = { showServiceDropdown = false },
                                                containerColor = Color.Black
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "Standard (Global)",
                                                            color = Color.White
                                                        )
                                                    },
                                                    onClick = {
                                                        setter("default"); showServiceDropdown =
                                                        false; save()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Gemini", color = Color.White) },
                                                    onClick = {
                                                        setter("gemini"); showServiceDropdown =
                                                        false; save()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("NVIDIA", color = Color.White) },
                                                    onClick = {
                                                        setter("nvidia"); showServiceDropdown =
                                                        false; save()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (index < aiServices.lastIndex) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 5.dp)
                            .drawBehind {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawRect(
                                        0f, 0f, size.width, size.height,
                                        Paint().apply {
                                            color = Color(0xFFBEBEBE).copy(alpha = 0.5f).toArgb()
                                            isAntiAlias = true
                                            maskFilter = android.graphics.BlurMaskFilter(
                                                25f,
                                                android.graphics.BlurMaskFilter.Blur.OUTER
                                            )
                                        }
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(5.dp))
                            .height(5.dp)
                            .background(Color(0xFFBEBEBE))
                    )
                    Text(
                        "Transparenz",
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFBEBEBE),
                        fontSize = 17.sp
                    )
                    Spacer(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 5.dp)
                            .drawBehind {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawRect(
                                        0f, 0f, size.width, size.height,
                                        Paint().apply {
                                            color = Color(0xFFBEBEBE).copy(alpha = 0.5f).toArgb()
                                            isAntiAlias = true
                                            maskFilter = android.graphics.BlurMaskFilter(
                                                25f,
                                                android.graphics.BlurMaskFilter.Blur.OUTER
                                            )
                                        }
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(5.dp))
                            .height(5.dp)
                            .background(Color(0xFFBEBEBE))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NeonBox(
                    modifier = Modifier.fillMaxWidth(),
                    neonColors = listOf(Color(0xFFE8B92A), Color(0xFFFF0000)),
                    backgroundAlpha = 0.15f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPermissionInfo = true }
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Warum fordere ich so viele Berechtigungen?",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Open information",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tabslify v1.0.0",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp
                )
            }
        }

        var selectedPermission by remember { mutableStateOf<String?>(null) }

        AnimatedVisibility(
            visible = showPermissionInfo && animatePermissionInfo,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LaunchedEffect(showPermissionInfo) {
                if (showPermissionInfo) {
                    delay(50)
                    animatePermissionInfo = true
                } else {
                    animatePermissionInfo = false
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C1017))
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                val hazeState = remember { HazeState() }
                var headerHeightDp by remember { mutableStateOf(0.dp) }
                val density = LocalDensity.current

                run {
                    @Composable
                    fun PermissionButton(txt: String) {
                        val shape = RoundedCornerShape(22.dp)
                        val isSelected = selectedPermission == txt

                        Button(
                            onClick = {
                                selectedPermission = txt
                                titleDialog = txt
                                usagesDialog = permissionUsages[txt] ?: listOf("Keine Angaben hinterlegt")
                            },
                            shape = shape,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) APP_COLOR.copy(alpha = 0.3f) else Color.Transparent),
                            modifier = Modifier
                                .padding(vertical = 5.dp)
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (isSelected) APP_COLOR.copy(alpha = 0.3f) else APP_COLOR)
                                .animateContentSize()
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFFF368A),
                                                Color(0xFF7C4DFF)
                                            )
                                        )
                                    ),
                                    shape
                                )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(txt, modifier = Modifier.weight(1f), fontSize = 16.sp)
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Open information",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = hazeState)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 15.dp)
                    ) {
                        // Platzhalter in Höhe des Headers, damit die Liste darunter startet
                        Spacer(Modifier.height(headerHeightDp))

                        // man soll nur in dieser column scrollen können, Items blurren dabei unter dem Header
                        PermissionButton("READ_MEDIA_AUDIO")
                        PermissionButton("POST_NOTIFICATIONS")
                        PermissionButton("ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION")
                        PermissionButton("SYSTEM_ALERT_WINDOW")
                        PermissionButton("FOREGROUND_SERVICE")
                        PermissionButton("READ_MEDIA_IMAGES / READ_MEDIA_VIDEO")
                        PermissionButton("READ_CONTACTS / WRITE_CONTACTS")
                        PermissionButton("CAMERA")
                        PermissionButton("RECEIVE_BOOT_COMPLETED")
                        PermissionButton("RECORD_AUDIO")
                        PermissionButton("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = Color(0xFF0C1017),
                                tint = HazeTint(Color(0xFF0C1017).copy(alpha = 0.7f)),
                                blurRadius = 60.dp,
                                noiseFactor = 0f
                            )
                        ) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f,
                                preferPerformance = true
                            )
                        }
                        .onGloballyPositioned {
                            headerHeightDp = with(density) { it.size.height.toDp() }
                        }
                        .padding(horizontal = 15.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Tabslify Berechtigungen",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 5.dp),
                            fontSize = 22.sp
                        )
                        IconButton(
                            onClick = { showPermissionInfo = false },
                            modifier = Modifier.background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = buildAnnotatedString {
                            append("Tabslify ist ")
                            withStyle(
                                SpanStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFF368A),
                                            Color(0xFF7C4DFF)
                                        )
                                    )
                                )
                            ) {
                                append("nicht nur irgendeine App")
                            }
                            append(", sie ist ein ")
                            withStyle(
                                SpanStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFF368A),
                                            Color(0xFF7C4DFF)
                                        )
                                    )
                                )
                            ) {
                                append("Mix aus ganz vielen")
                            }
                            append(" Apps, unter anderem für die Schule, zum eigenen Erfolg tracken oder zum Podcast hören. \nDieser Mix aus Funktionen benötigt ")
                            withStyle(
                                SpanStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFF368A),
                                            Color(0xFF7C4DFF)
                                        )
                                    )
                                )
                            ) {
                                append("jeweils viele Berechtigungen")
                            }
                            append(" um das Benutzererlebnis ")
                            withStyle(
                                SpanStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFF368A),
                                            Color(0xFF7C4DFF)
                                        )
                                    )
                                )
                            ) {
                                append("möglichst unkompliziert")
                            }
                            append(" zu halten.")
                        },
                        color = APP_COLOR.copy(
                            red = APP_COLOR.red + .6f,
                            green = APP_COLOR.green + .6f,
                            blue = APP_COLOR.blue + .6f
                        ),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedPermission != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            DialogTabslify(
                onConfirm = { selectedPermission = null },
                onDismiss = { selectedPermission = null },
                title = titleDialog,
                text = usagesDialog.joinToString("\n\n") { "•  $it" },
                confirmText = "Schließen",
                oneButton = true,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
}
