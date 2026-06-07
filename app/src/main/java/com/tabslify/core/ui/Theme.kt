package com.tabslify.core.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tabslify.R
import java.util.Calendar

val APP_COLOR = Color(0xFF2A2A2A)
val gruen = Color(0xFF228B22)

val BgDeep = Color(0xFF121212)
val BgSurface = Color(0xFF1E1E1E)
val BgCard = Color(0xFF2A2A2A)
val AccentViolet = Color(0xFF7C4DFF)
val AccentVioletDim = Color(0xFF4A148C)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val TextTertiary = Color(0xFF757575)

fun c(): Color {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    return when (currentHour) {
        in 11..16 -> Color(0xFF383838)
        else -> Color(0xFF001FBB)
    }
}

@Composable
fun rememberAppColor(): Color {
    var targetColor by remember { mutableStateOf(c()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(
            durationMillis = 1500,
            easing = LinearEasing
        ),
        label = "appColorAnimation"
    )

    DisposableEffect(lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIME_TICK) {
                    targetColor = c()
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    targetColor = c()
                    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_TICK))
                }
                Lifecycle.Event.ON_PAUSE -> {
                    context.unregisterReceiver(receiver)
                }
                else -> {}
            }
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return color
}

val AppFontFamily = FontFamily(
    Font(R.font.smb_regular, FontWeight.Normal),
    Font(R.font.smb, FontWeight.Bold),
)
private val baseline = Typography()

val Typography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = baseline.titleLarge.copy(fontFamily = AppFontFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = AppFontFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = AppFontFamily),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = AppFontFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = AppFontFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = AppFontFamily),
)