package com.cloud.core.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cloud.R
import java.util.Calendar

val Cloud = Color(0xFF2A2A2A)
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