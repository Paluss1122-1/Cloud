package com.tabslify.tabs.focusguard

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabslify.tabs.focusguard.data.FOCUSGUARD_CATEGORIES
import com.tabslify.tabs.focusguard.data.FocusGuardConfig
import com.tabslify.tabs.focusguard.data.FocusGuardRepository
import com.tabslify.tabs.focusguard.data.RestrictionRule
import com.tabslify.tabs.focusguard.data.SleepRecord
import com.tabslify.tabs.focusguard.data.StudyGoal
import com.tabslify.tabs.focusguard.data.UserAchievement
import com.tabslify.tabs.focusguard.monitoring.RestrictionEngine
import com.tabslify.tabs.focusguard.monitoring.UsageTracker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class FocusGuardScreen { DASHBOARD, CONFIG, STATS }

data class AppInfo(
    val packageName: String,
    val label: String,
    val category: String,
    val restricted: Boolean
)

class FocusGuardViewModel(application: Application) : AndroidViewModel(application) {

    var currentScreen by mutableStateOf(FocusGuardScreen.DASHBOARD)
        private set

    val todayUsage: StateFlow<Map<String, Long>> = FocusGuardRepository.todayUsage
    val todayPackages: StateFlow<List<com.tabslify.tabs.focusguard.data.PackageTotal>> =
        FocusGuardRepository.todayPackages
    val sleepWeek: StateFlow<List<SleepRecord>> = FocusGuardRepository.sleepWeek
    val todayGoal: StateFlow<StudyGoal?> = FocusGuardRepository.todayGoal
    val achievements: StateFlow<List<UserAchievement>> = FocusGuardRepository.achievements
    val rules: StateFlow<List<RestrictionRule>> = FocusGuardRepository.rules

    var points by mutableIntStateOf(FocusGuardConfig.points)
        private set
    var currentStreak by mutableIntStateOf(FocusGuardConfig.currentStreak)
        private set

    var allApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    var usageWeek by mutableStateOf<Map<String, Map<String, Long>>>(emptyMap())
        private set
    var goalsWeek by mutableStateOf<List<StudyGoal>>(emptyList())
        private set
    var sleepWeekData by mutableStateOf<List<SleepRecord>>(emptyList())
        private set

    var thresholdHour by mutableIntStateOf(FocusGuardConfig.afternoonThresholdMin / 60)
    var sleepThresholdHours by mutableIntStateOf(FocusGuardConfig.sleepThresholdMin / 60)
    var studyTarget by mutableIntStateOf(FocusGuardConfig.studyDailyTarget)
    var reminderIntervalMin by mutableIntStateOf(FocusGuardConfig.studyReminderIntervalMin)
    var excessiveThresholdMin by mutableIntStateOf(FocusGuardConfig.excessiveUsageThresholdMin)
    var dailySummaryHour by mutableIntStateOf(FocusGuardConfig.dailySummaryHour)
    var overlayEnabled by mutableStateOf(FocusGuardConfig.overlayEnabled)
    var cooldownMinutes by mutableIntStateOf(FocusGuardConfig.cooldownMinutes)
    var schoolCalendarEnabled by mutableStateOf(FocusGuardConfig.schoolCalendarEnabled)
    var notificationsEnabled by mutableStateOf(FocusGuardConfig.notificationsEnabled)

    var ruleDrafts by mutableStateOf<Map<String, WindowDraft>>(emptyMap())
        private set

    data class WindowDraft(
        val category: String,
        val startHour: Int,
        val endHour: Int,
        val enabled: Boolean
    )

    init {
        FocusGuardRepository.init(application)
        refresh()
    }

    fun refresh() {
        points = FocusGuardConfig.points
        currentStreak = FocusGuardConfig.currentStreak
        viewModelScope.launch {
            FocusGuardRepository.refreshNow()
            loadRuleDrafts()
            usageWeek = FocusGuardRepository.dailyUsageForLastDays(7)
            goalsWeek = FocusGuardRepository.goalsSince(7)
            sleepWeekData = FocusGuardRepository.sleepSince(7)
        }
    }

    fun switchTo(screen: FocusGuardScreen) {
        currentScreen = screen
        if (screen == FocusGuardScreen.STATS) refreshStats()
        if (screen == FocusGuardScreen.CONFIG) {
            loadInstalledApps()
            loadRuleDrafts()
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            usageWeek = FocusGuardRepository.dailyUsageForLastDays(7)
            goalsWeek = FocusGuardRepository.goalsSince(7)
            sleepWeekData = FocusGuardRepository.sleepSince(7)
        }
    }

    fun loadInstalledApps() {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val restricted = FocusGuardConfig.restrictedApps()
        allApps = pm.queryIntentActivities(intent, 0).mapNotNull { resolve ->
            val pkg = resolve.activityInfo.packageName
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            AppInfo(
                packageName = pkg,
                label = pm.getApplicationLabel(info).toString(),
                category = restricted[pkg] ?: UsageTracker.categoryFor(pkg),
                restricted = pkg in restricted
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun toggleRestricted(app: AppInfo) {
        val current = FocusGuardConfig.restrictedApps()
        FocusGuardConfig.setRestrictedApps(
            if (app.restricted) current - app.packageName
            else current + (app.packageName to app.category)
        )
        loadInstalledApps()
    }

    fun setAppCategory(app: AppInfo, category: String) {
        if (app.restricted) {
            val current = FocusGuardConfig.restrictedApps()
            FocusGuardConfig.setRestrictedApps(current + (app.packageName to category))
        }
        allApps = allApps.map {
            if (it.packageName == app.packageName) it.copy(category = category) else it
        }
    }

    fun addStudyProgress(count: Int = 1) {
        FocusGuardRepository.addStudyProgress(count)
    }

    fun resetTodayGoal() {
        FocusGuardRepository.resetTodayGoal()
        FocusGuardRepository.refresh()
    }

    fun saveConfig() {
        FocusGuardConfig.afternoonThresholdMin = thresholdHour * 60
        FocusGuardConfig.sleepThresholdMin = sleepThresholdHours * 60
        FocusGuardConfig.studyDailyTarget = studyTarget
        FocusGuardConfig.studyReminderIntervalMin = reminderIntervalMin
        FocusGuardConfig.excessiveUsageThresholdMin = excessiveThresholdMin
        FocusGuardConfig.dailySummaryHour = dailySummaryHour
        FocusGuardConfig.overlayEnabled = overlayEnabled
        FocusGuardConfig.cooldownMinutes = cooldownMinutes
        FocusGuardConfig.schoolCalendarEnabled = schoolCalendarEnabled
        FocusGuardConfig.notificationsEnabled = notificationsEnabled
        FocusGuardRepository.saveRules(ruleDrafts.values.map { draft ->
            RestrictionRule(
                category = draft.category,
                startMinute = draft.startHour * 60,
                endMinute = if (draft.endHour == 0) 24 * 60 else draft.endHour * 60,
                enabled = draft.enabled
            )
        })
        com.tabslify.tabs.focusguard.monitoring.scheduleFocusGuardDailySummary(getApplication())
        FocusGuardRepository.refresh()
    }

    fun setDraft(draft: WindowDraft) {
        ruleDrafts = ruleDrafts + (draft.category to draft)
    }

    val hasUsageAccess: Boolean
        get() = UsageTracker.hasUsageAccess(getApplication())

    val canDrawOverlays: Boolean
        get() = Settings.canDrawOverlays(getApplication())

    val schoolDay: Boolean
        get() = FocusGuardConfig.schoolCalendarEnabled && RestrictionEngine.todayIsSchoolDay()

    val overrideActive: Boolean
        get() = System.currentTimeMillis() < FocusGuardConfig.overrideUntilMs

    val restrictionsActive: Boolean
        get() {
            if (schoolDay || overrideActive) return false
            val now = System.currentTimeMillis()
            return RestrictionEngine.restrictionsPossibleToday(
                FocusGuardConfig.restrictedApps().keys,
                rules.value,
                schoolDay,
                FocusGuardConfig.overrideUntilMs,
                now
            )
        }

    val restrictionEnabled: Boolean
        get() = FocusGuardConfig.restrictedApps().isNotEmpty()

    fun categories(): List<String> = FOCUSGUARD_CATEGORIES

    private fun loadRuleDrafts() {
        val existing = rules.value
        ruleDrafts = FOCUSGUARD_CATEGORIES.filter { it in setOf("SOCIAL", "GAMING", "ENTERTAINMENT") }
            .associateWith { category ->
                val rule = existing.firstOrNull { it.category == category }
                if (rule != null) {
                    WindowDraft(
                        category = category,
                        startHour = rule.startMinute / 60,
                        endHour = rule.endMinute / 60,
                        enabled = rule.enabled
                    )
                } else {
                    WindowDraft(
                        category = category,
                        startHour = FocusGuardConfig.afternoonThresholdMin / 60,
                        endHour = 24,
                        enabled = true
                    )
                }
            }
    }
}
