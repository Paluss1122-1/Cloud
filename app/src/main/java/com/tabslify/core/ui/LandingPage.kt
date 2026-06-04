package com.tabslify.core.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.client
import com.tabslify.core.objects.Config.realDevice
import com.tabslify.core.objects.PasswordStorage
import com.tabslify.core.objects.prvt
import com.tabslify.services.ChatService
import com.tabslify.services.QuietHoursNotificationService
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
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
    realDevice = getDeviceName().trim().equals("Samsung SM-S921U1", ignoreCase = true)
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
        QuietHoursNotificationService.startService(context)
        ChatService.startService(context)
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

                snapshotFlow { previewBitmap }.filter { it != null }.first()

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

                delay(80)
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
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp)); Text(
            it,
            color = MaterialTheme.colorScheme.error
        )
        }
        Spacer(Modifier.height(24.dp))
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
    LaunchedEffect(reloadTrigger) {
        recentTabs = loadRecentTabs(context)
    }
    val allTabsSorted = remember {
        MenuItem.entries.filter {
            prvt() || (it != MenuItem.GMAIL && it != MenuItem.PRIVATE_TABSLIFY && it != MenuItem.REMOTEDESKTOP)
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
                    /*IconButton(onClick = {

                    },
                        modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Done, contentDescription = null, tint = Color.Yellow)
                    }*/
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