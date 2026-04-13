package com.cloud

import android.content.Context
import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.cloud.Config.realDevice
import com.cloud.privatecloudapp.KEY_LAST_MENU_ITEM
import com.cloud.privatecloudapp.KEY_RECENT_TABS
import com.cloud.privatecloudapp.MAX_RECENT_TABS
import com.cloud.privatecloudapp.MenuItem
import com.cloud.privatecloudapp.PREFS_NAME
import com.cloud.privatecloudapp.PrivateCloudApp
import com.cloud.quicksettingsfunctions.BatteryDataRepository
import com.cloud.service.ChatService
import com.cloud.service.QuietHoursNotificationService
import com.cloud.ui.theme.Cloud
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

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

fun saveLastMenuItem(context: Context, menuItem: MenuItem) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putString(KEY_LAST_MENU_ITEM, menuItem.name)
        }
}

fun loadLastMenuItem(context: Context): MenuItem {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedName = prefs.getString(KEY_LAST_MENU_ITEM, MenuItem.PRIVATE_CLOUD.name)
    return try {
        MenuItem.valueOf(savedName ?: MenuItem.PRIVATE_CLOUD.name)
    } catch (_: Exception) {
        MenuItem.PRIVATE_CLOUD
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
    realDevice = getDeviceName().trim().equals("Samsung SM-S921U1", ignoreCase = true)

    if (realDevice) {
        if (masterPw == null || Config.masterPassword.isEmpty()) {
            MasterPasswordSetupScreen { pw ->
                PasswordStorage.savePassword(context, pw)
                Config.masterPassword = pw
            }
            return
        }
        Config.masterPassword = masterPw!!
    }

    LaunchedEffect(Unit) {
        QuietHoursNotificationService.startService(context)
        ChatService.startService(context)
        BatteryDataRepository.init(context)
    }

    // ── Initialisierung bei startTarget ──────────────────────────────────────
    LaunchedEffect(startTarget) {
        if (startTarget != null && selectedMenuItem == null) {
            selectedMenuItem = when (startTarget) {
                "weather" -> MenuItem.WEATHER
                else -> null
            }
            if (selectedMenuItem != null) {
                saveRecentTab(context, selectedMenuItem!!)
            }
        }
    }

    var isLandingVisible by rememberSaveable { mutableStateOf(!hasLoadedApp) }
    val landingOffsetX = remember { Animatable(if (!hasLoadedApp) 0f else -1f) }
    val scope = rememberCoroutineScope()

    fun openLanding() {
        scope.launch {
            isLandingVisible = true
            landingOffsetX.snapTo(-1f)
            landingOffsetX.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    fun closeLanding(force: Boolean = false, then: (() -> Unit)? = null) {
        scope.launch {
            landingOffsetX.animateTo(
                -1f,
                tween(if (force) 1 else 300, easing = FastOutSlowInEasing)
            )
            isLandingVisible = false
            then?.invoke()
        }
    }

    // ── Forward-Transition (LandingPage → Tab) ──────────────────────────────
    var pendingOverlayItem by remember { mutableStateOf<MenuItem?>(null) }
    val overlayScale = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(0f) }

    // ── Backward-Transition (Tab → zurück) ──────────────────────────────────
    var closingBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val closeScale = remember { Animatable(1f) }
    val closeAlpha = remember { Animatable(1f) }

    val appGraphicsLayer = rememberGraphicsLayer()
    var recordingEnabled by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── App-Inhalt (immer im Hintergrund, sobald einmal geladen) ─────────
        if (hasLoadedApp) {
            val targetMenuItem = selectedMenuItem ?: startTarget?.let { target ->
                when (target) {
                    "weather" -> MenuItem.WEATHER
                    else -> null
                }
            }

            key(selectedMenuItem) {  // <-- Diese Zeile hinzufügen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (recordingEnabled) {
                                Modifier.drawWithContent {
                                    appGraphicsLayer.record { this@drawWithContent.drawContent() }
                                    drawLayer(appGraphicsLayer)
                                }
                            } else Modifier
                        )
                ) {
                    if (targetMenuItem != null) {
                        PrivateCloudApp(
                            storage = storage,
                            startTarget = null,
                            initialMenuItem = targetMenuItem,
                            onMenuClick = { openLanding() }
                        )
                    } else {
                        PrivateCloudApp(
                            storage = storage,
                            startTarget = startTarget,
                            initialMenuItem = null,
                            onMenuClick = { openLanding() }
                        )
                    }
                }
            }
        }

        // ── Landing-Overlay (von links reinslidend) ──────────────────────────
        if (isLandingVisible) {
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
                    onTabSelected = { menuItem ->
                        pendingOverlayItem = menuItem
                    }
                )
            }
        }

        // ── Forward-Overlay (Tab-Zoom rein) ───────────────────────────────────
        if (pendingOverlayItem != null) {
            val item = pendingOverlayItem!!
            val graphicsLayer = rememberGraphicsLayer()
            var previewBitmap by remember(item) { mutableStateOf<ImageBitmap?>(null) }

            // Off-screen rendering des neuen Tabs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = 10000f }
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                    .background(Cloud)
            ) {
                PrivateCloudApp(
                    storage = storage,
                    startTarget = null,
                    initialMenuItem = item,
                    onMenuClick = null
                )
            }

            LaunchedEffect(item) {
                // Warten bis Tab gerendert ist
                repeat(5) { withFrameNanos { } }
                val captured = graphicsLayer.toImageBitmap()
                previewBitmap = captured

                // Warten bis Bitmap gesetzt ist
                snapshotFlow { previewBitmap }.filter { it != null }.first()

                // Animation starten
                overlayScale.snapTo(0.05f)
                overlayAlpha.snapTo(1f)

                overlayScale.animateTo(
                    1f, tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )

                // Landing Page schließen
                closeLanding(true)

                // KORREKTUR: Tab korrekt setzen und speichern!
                selectedMenuItem = item
                saveRecentTab(context, item)

                // KORREKTUR: hasLoadedApp setzen falls noch nicht gesetzt
                if (!hasLoadedApp) {
                    hasLoadedApp = true
                }

                // Fade out der Bitmap
                delay(80)
                overlayAlpha.animateTo(0f, tween(durationMillis = 200))
                overlayScale.snapTo(0f)
                pendingOverlayItem = null
            }

            // Zoom-Animation anzeigen
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

        // ── Backward-Overlay (Tab schließen – schrumpft weg) ────────────────
        if (closingBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = closeScale.value
                        scaleY = closeScale.value
                        alpha = closeAlpha.value
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                Image(
                    bitmap = closingBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingPage(
    onTabSelected: (MenuItem) -> Unit,
    showCloseButton: Boolean = false,   // NEU
    onClose: () -> Unit = {}            // NEU
) {
    val context = LocalContext.current
    var recentTabs by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    recentTabs = loadRecentTabs(context)
    val allTabsSorted = remember { MenuItem.entries.sortedBy { it.title } }
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val gradient = remember {
        val colors = when (currentHour) {
            in 11..16 -> listOf(
                Color(0xFF00F2FE).copy(alpha = 0.5f),
                Color(0xFFDEFE4F).copy(alpha = 0.5f)
            )

            else -> listOf(
                Cloud.copy(alpha = 0.7f),
                Color(0xFF001A93).copy(alpha = 0.7f)
            )
        }
        Brush.linearGradient(colors = colors, start = Offset.Zero, end = Offset.Infinite)
    }
    val txtcolors = remember {
        when (currentHour) {
            in 11..16 -> Color.Black; else -> Color.White
        }
    }
    val bgpicture = remember {
        when (currentHour) {
            in 11..16 -> R.drawable.mittag; else -> R.drawable.night
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
                        text = "CLOUD",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }

                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
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
                                onClick = { onTabSelected(menuItem) })
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
                            onClick = { onTabSelected(menuItem) })
                    }
                }
            }
        }
    }
}

@Composable
fun TabCard(
    menuItem: MenuItem,
    onClick: () -> Unit
) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(300))
    }
    val containerColor = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .alpha(alpha.value)
            .drawBehind {
                val canvasSize = size
                drawContext.canvas.nativeCanvas.apply {
                    drawRoundRect(
                        0f, 0f, canvasSize.width, canvasSize.height,
                        5.dp.toPx(), 5.dp.toPx(),
                        Paint().apply {
                            color = containerColor.toArgb()
                            isAntiAlias = true
                            setShadowLayer(
                                5.dp.toPx(), 0f, 0f,
                                containerColor.copy(alpha = 0.85f).toArgb()
                            )
                        }
                    )
                }
            }
            .border(1.dp, Color.Gray.copy(0.8f), RoundedCornerShape(5.dp))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
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