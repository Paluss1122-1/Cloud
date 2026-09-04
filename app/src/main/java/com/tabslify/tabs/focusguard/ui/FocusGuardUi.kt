package com.tabslify.tabs.focusguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.TextPrimary
import com.tabslify.tabs.focusguard.data.CATEGORY_ENTERTAINMENT
import com.tabslify.tabs.focusguard.data.CATEGORY_GAMING
import com.tabslify.tabs.focusguard.data.CATEGORY_PRODUCTIVITY
import com.tabslify.tabs.focusguard.data.CATEGORY_SOCIAL
import com.tabslify.tabs.focusguard.data.parseYmd
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FgSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

fun categoryColor(category: String): Color = when (category) {
    CATEGORY_SOCIAL -> Color(0xFF4CFCC1)
    CATEGORY_GAMING -> Color(0xFFFF8A4C)
    CATEGORY_ENTERTAINMENT -> Color(0xFFB45CFC)
    CATEGORY_PRODUCTIVITY -> Color(0xFF6B4CFC)
    else -> Color(0xFF9AA0A6)
}

@Composable
fun categoryLabel(category: String): String = when (category) {
    CATEGORY_SOCIAL -> stringResource(R.string.focusguard_cat_social)
    CATEGORY_GAMING -> stringResource(R.string.focusguard_cat_gaming)
    CATEGORY_ENTERTAINMENT -> stringResource(R.string.focusguard_cat_entertainment)
    CATEGORY_PRODUCTIVITY -> stringResource(R.string.focusguard_cat_productivity)
    else -> stringResource(R.string.focusguard_cat_other)
}

fun weekdayAbbrev(ymd: String): String =
    parseYmd(ymd).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.GERMAN)
