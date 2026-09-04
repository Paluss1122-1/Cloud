package com.tabslify.tabs.fitnesstab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.AppBackground
import com.tabslify.core.ui.PloppingButton
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.tabs.fitnesstab.ui.BikeScreen
import com.tabslify.tabs.fitnesstab.ui.DashboardScreen
import com.tabslify.tabs.fitnesstab.ui.ExerciseDetailScreen
import com.tabslify.tabs.fitnesstab.ui.ExerciseListScreen
import com.tabslify.tabs.fitnesstab.ui.HistoryScreen
import com.tabslify.tabs.fitnesstab.ui.WorkoutScreen

@Composable
fun FitnessTabContent(vm: FitnessViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var directedToSettings by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasPermission = granted[Manifest.permission.CAMERA] == true
        if (!hasPermission) showPermissionDialog = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) Config.requestPermission("cam", launcher)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && directedToSettings) {
                directedToSettings = false
                hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) showPermissionDialog = false
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPermissionDialog) {
        AlertDialogTabslify(
            onDismiss = { showPermissionDialog = false },
            title = stringResource(R.string.fitness_kamerazugriff_benotigt),
            text = stringResource(R.string.fitness_kamera_dialog_text),
            confirmText = stringResource(R.string.zu_den_einstellungen),
            onConfirm = {
                showPermissionDialog = false
                directedToSettings = true
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            var navBarHeight by remember { mutableStateOf(78.dp) }
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = maxOf(navBarHeight, imeBottom))
            ) {
                when (vm.currentScreen) {
                    FitnessScreen.DASHBOARD -> {
                        DashboardScreen(vm = vm)
                    }
                    FitnessScreen.EXERCISES -> {
                        val sel = vm.selectedExercise
                        if (sel != null) {
                            ExerciseDetailScreen(
                                exercise = sel,
                                vm = vm
                            )
                        } else {
                            ExerciseListScreen(vm = vm)
                        }
                    }
                    FitnessScreen.WORKOUT -> {
                        WorkoutScreen(vm = vm) {
                            if (hasPermission) {
                                PushUpCameraScreen(vm = vm)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(text = "📷", fontSize = 48.sp)
                                        Text(
                                            text = stringResource(R.string.fitness_kamera_verweigert),
                                            color = TextSecondary,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            fontSize = 18.sp,
                                            lineHeight = 26.sp
                                        )
                                        PloppingButton(
                                            onClick = { showPermissionDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                Color(0xFFFF8A4C),
                                                                Color(0xFFB45CFC),
                                                                Color(0xFF6B4CFC)
                                                            )
                                                        )
                                                    )
                                                    .padding(horizontal = 24.dp, vertical = 14.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.einstellungen),
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FitnessScreen.BIKE -> {
                        BikeScreen(vm = vm)
                    }
                    FitnessScreen.HISTORY -> {
                        HistoryScreen(vm = vm)
                    }
                }
            }

            BottomNav(
                vm = vm,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        val h = with(density) { size.height.toDp() }
                        if (h > 0.dp && h != navBarHeight) navBarHeight = h
                    }
            )
        }
    }
}

@Composable
private fun BottomNav(vm: FitnessViewModel, modifier: Modifier = Modifier) {
    val items = listOf(
        BottomNavItem(FitnessScreen.DASHBOARD, Icons.Default.Dashboard, R.string.fitness_nav_dashboard, listOf(Color(0xFF6B4CFC), Color(0xFFB45CFC))),
        BottomNavItem(FitnessScreen.EXERCISES, Icons.Default.ListAlt, R.string.fitness_nav_exercises, listOf(Color(0xFF4CFCC1), Color(0xFF2196F3))),
        BottomNavItem(FitnessScreen.WORKOUT, Icons.Default.FitnessCenter, R.string.fitness_nav_workout, listOf(Color(0xFFFF8A4C), Color(0xFFFF5C9A))),
        BottomNavItem(FitnessScreen.BIKE, Icons.Default.DirectionsBike, R.string.fitness_nav_bike, listOf(Color(0xFF00FFAA), Color(0xFF00CCFF))),
        BottomNavItem(FitnessScreen.HISTORY, Icons.Default.CalendarMonth, R.string.fitness_nav_history, listOf(Color(0xFFFFC107), Color(0xFFFF6B4C)))
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF0B0A10).copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val selected = vm.currentScreen == item.screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) Brush.horizontalGradient(item.colors)
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { vm.switchTo(item.screen) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
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
}

private data class BottomNavItem(
    val screen: FitnessScreen,
    val icon: ImageVector,
    val labelRes: Int,
    val colors: List<Color>
)
