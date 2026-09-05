package com.tabslify.tabs.focusguard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabslify.R
import com.tabslify.core.objects.prvt
import com.tabslify.tabs.focusguard.ui.FocusGuardConfigScreen
import com.tabslify.tabs.focusguard.ui.FocusGuardDashboardScreen
import com.tabslify.tabs.focusguard.ui.FocusGuardStatsScreen

@Composable
fun FocusGuardTabContent(vm: FocusGuardViewModel = viewModel()) {
    val context = LocalContext.current
    val forbiddenMsg = stringResource(R.string.forbidden)

    if (!prvt()) {
        Toast.makeText(context, forbiddenMsg, Toast.LENGTH_SHORT).show()
        return
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
                if (vm.currentScreen == FocusGuardScreen.CONFIG) vm.loadInstalledApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 78.dp)
        ) {
            when (vm.currentScreen) {
                FocusGuardScreen.DASHBOARD -> FocusGuardDashboardScreen(vm)
                FocusGuardScreen.CONFIG -> FocusGuardConfigScreen(vm)
                FocusGuardScreen.STATS -> FocusGuardStatsScreen(vm)
            }
        }

        FocusGuardBottomNav(
            vm = vm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun FocusGuardBottomNav(vm: FocusGuardViewModel, modifier: Modifier = Modifier) {
    val items = listOf(
        FocusGuardNavItem(FocusGuardScreen.DASHBOARD, Icons.Default.Dashboard, R.string.focusguard_nav_dashboard, listOf(Color(0xFF4CFCC1), Color(0xFF6B4CFC))),
        FocusGuardNavItem(FocusGuardScreen.CONFIG, Icons.Default.Settings, R.string.focusguard_nav_config, listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC))),
        FocusGuardNavItem(FocusGuardScreen.STATS, Icons.Default.Insights, R.string.focusguard_nav_stats, listOf(Color(0xFF00AAFF), Color(0xFFB45CFC)))
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF0B0A10).copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val selected = vm.currentScreen == item.screen
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) Brush.horizontalGradient(item.colors)
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { vm.switchTo(item.screen) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else Color(0xFF7A7880),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(item.labelRes),
                    color = if (selected) Color.White else Color(0xFF7A7880),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

private data class FocusGuardNavItem(
    val screen: FocusGuardScreen,
    val icon: ImageVector,
    val labelRes: Int,
    val colors: List<Color>
)
