package com.tabslify.tabs.focusguard.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.tabs.focusguard.FocusGuardViewModel
import com.tabslify.tabs.focusguard.data.CATEGORY_ENTERTAINMENT
import com.tabslify.tabs.focusguard.data.CATEGORY_GAMING
import com.tabslify.tabs.focusguard.data.CATEGORY_SOCIAL
import androidx.core.net.toUri

@Composable
fun FocusGuardConfigScreen(vm: FocusGuardViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Button(
                onClick = { vm.saveConfig() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF6B4CFC))
            ) {
                Text(stringResource(R.string.focusguard_config_save), color = androidx.compose.ui.graphics.Color.White)
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_permission_usage)) {
                PermissionRow(
                    granted = vm.hasUsageAccess,
                    hint = stringResource(R.string.focusguard_permission_usage_hint),
                    buttonText = stringResource(R.string.focusguard_permission_usage),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_permission_overlay)) {
                PermissionRow(
                    granted = vm.canDrawOverlays,
                    hint = stringResource(R.string.focusguard_permission_overlay_hint),
                    buttonText = stringResource(R.string.focusguard_permission_overlay),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = "package:${context.packageName}".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_config_threshold)) {
                Text(
                    text = "${vm.thresholdHour}:00",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = vm.thresholdHour.toFloat(),
                    onValueChange = { vm.thresholdHour = it.toInt() },
                    valueRange = 8f..23f,
                    steps = 14
                )
                Text(
                    text = stringResource(R.string.focusguard_config_threshold_hint),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_config_windows)) {
                Text(
                    text = stringResource(R.string.focusguard_config_window_hint),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                listOf(CATEGORY_SOCIAL, CATEGORY_GAMING, CATEGORY_ENTERTAINMENT).forEach { category ->
                    val draft = vm.ruleDrafts[category]
                    if (draft != null) {
                        WindowEditor(vm = vm, category = category, startHour = draft.startHour, endHour = draft.endHour, enabled = draft.enabled)
                    }
                }
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_config_goal)) {
                LabeledSlider(
                    label = "${stringResource(R.string.focusguard_config_goal_target)}: ${vm.studyTarget}",
                    value = vm.studyTarget.toFloat(),
                    onValueChange = { vm.studyTarget = it.toInt() },
                    range = 0f..100f
                )
                LabeledSlider(
                    label = "${stringResource(R.string.focusguard_config_reminder_interval)}: ${vm.reminderIntervalMin}",
                    value = vm.reminderIntervalMin.toFloat(),
                    onValueChange = { vm.reminderIntervalMin = it.toInt() },
                    range = 30f..480f
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_dash_sleep)) {
                LabeledSlider(
                    label = "${stringResource(R.string.focusguard_config_sleep_threshold)}: ${vm.sleepThresholdHours}",
                    value = vm.sleepThresholdHours.toFloat(),
                    onValueChange = { vm.sleepThresholdHours = it.toInt() },
                    range = 5f..12f
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_config_overlay)) {
                SwitchRow(
                    label = stringResource(R.string.focusguard_config_overlay),
                    hint = stringResource(R.string.focusguard_config_overlay_hint),
                    checked = vm.overlayEnabled,
                    onCheckedChange = { vm.overlayEnabled = it }
                )
                LabeledSlider(
                    label = "${stringResource(R.string.focusguard_config_cooldown)}: ${vm.cooldownMinutes}",
                    value = vm.cooldownMinutes.toFloat(),
                    onValueChange = { vm.cooldownMinutes = it.toInt() },
                    range = 5f..120f
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_config_school_calendar)) {
                SwitchRow(
                    label = stringResource(R.string.focusguard_config_school_calendar),
                    hint = stringResource(R.string.focusguard_config_school_calendar_hint),
                    checked = vm.schoolCalendarEnabled,
                    onCheckedChange = { vm.schoolCalendarEnabled = it }
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_config_notifications)) {
                SwitchRow(
                    label = stringResource(R.string.focusguard_config_notifications),
                    hint = "",
                    checked = vm.notificationsEnabled,
                    onCheckedChange = { vm.notificationsEnabled = it }
                )
                LabeledSlider(
                    label = "${stringResource(R.string.focusguard_config_summary_hour)}: ${vm.dailySummaryHour}",
                    value = vm.dailySummaryHour.toFloat(),
                    onValueChange = { vm.dailySummaryHour = it.toInt() },
                    range = 16f..23f
                )
                LabeledSlider(
                    label = "${stringResource(R.string.focusguard_config_excessive_threshold)}: ${vm.excessiveThresholdMin}",
                    value = vm.excessiveThresholdMin.toFloat(),
                    onValueChange = { vm.excessiveThresholdMin = it.toInt() },
                    range = 30f..360f
                )
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_config_apps)) {
                Text(
                    text = stringResource(R.string.focusguard_config_apps_hint),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (vm.allApps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.focusguard_restricted_empty),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        items(vm.allApps, key = { it.packageName }) { app ->
            AppRow(vm, app)
        }
    }
}

@Composable
private fun PermissionRow(granted: Boolean, hint: String, buttonText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (granted) "✅ $buttonText" else buttonText,
                color = TextPrimary,
                fontSize = 13.sp
            )
            Text(text = hint, color = TextSecondary, fontSize = 11.sp)
        }
        if (!granted) {
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.erteilen), color = androidx.compose.ui.graphics.Color(0xFFB45CFC))
            }
        }
    }
}

@Composable
private fun WindowEditor(
    vm: FocusGuardViewModel,
    category: String,
    startHour: Int,
    endHour: Int,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchRow(
            label = categoryLabel(category),
            hint = if (enabled) "${startHour}:00 – ${endHourOf(endHour)}:00" else "",
            checked = enabled,
            onCheckedChange = {
                vm.setDraft(FocusGuardViewModel.WindowDraft(category, startHour, endHour, it))
            }
        )
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.focusguard_config_window_start),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(56.dp)
                )
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { vm.setDraft(FocusGuardViewModel.WindowDraft(category, it.toInt(), endHour, true)) },
                    valueRange = 8f..23f,
                    steps = 14,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.focusguard_config_window_end),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(56.dp)
                )
                Slider(
                    value = if (endHour == 24) 24f else endHour.toFloat(),
                    onValueChange = { vm.setDraft(FocusGuardViewModel.WindowDraft(category, startHour, if (it.toInt() == 24) 24 else it.toInt(), true)) },
                    valueRange = 8f..24f,
                    steps = 15,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun endHourOf(hour: Int): Int = if (hour == 24) 0 else hour

@Composable
private fun LabeledSlider(label: String, value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    Column {
        Text(text = label, color = TextPrimary, fontSize = 13.sp)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = TextPrimary, fontSize = 14.sp)
            if (hint.isNotEmpty()) {
                Text(text = hint, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRow(vm: FocusGuardViewModel, app: com.tabslify.tabs.focusguard.AppInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, color = TextPrimary, fontSize = 14.sp)
            Text(text = app.packageName, color = TextSecondary, fontSize = 11.sp)
        }
        var expanded by remember { mutableStateOf(false) }
        if (app.restricted) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.width(140.dp)
            ) {
                OutlinedTextField(
                    value = categoryLabel(app.category),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .height(48.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    vm.categories().forEach { category ->
                        DropdownMenuItem(
                            text = { Text(categoryLabel(category), fontSize = 13.sp) },
                            onClick = {
                                vm.setAppCategory(app, category)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = app.restricted,
            onCheckedChange = { vm.toggleRestricted(app) }
        )
    }
}
