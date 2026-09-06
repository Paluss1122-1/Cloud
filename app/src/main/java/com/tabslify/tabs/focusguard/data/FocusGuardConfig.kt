package com.tabslify.tabs.focusguard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json

object FocusGuardConfig {
    private const val PREFS_NAME = "focusguard_prefs"
    private const val KEY_RESTRICTED_APPS = "restricted_apps"
    private const val KEY_AFTERNOON_THRESHOLD_MIN = "afternoon_threshold_min"
    private const val KEY_SLEEP_THRESHOLD_MIN = "sleep_threshold_min"
    private const val KEY_STUDY_DAILY_TARGET = "study_daily_target"
    private const val KEY_STUDY_REMINDER_INTERVAL_MIN = "study_reminder_interval_min"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
    private const val KEY_SCHOOL_CALENDAR_ENABLED = "school_calendar_enabled"
    private const val KEY_POINTS = "points"
    private const val KEY_CURRENT_STREAK = "current_streak"
    private const val KEY_LAST_ACTIVE_DAY_YMD = "last_active_day_ymd"
    private const val KEY_OVERRIDE_UNTIL_MS = "override_until_ms"
    private const val KEY_DAILY_SUMMARY_HOUR = "daily_summary_hour"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_EXCESSIVE_USAGE_THRESHOLD_MIN = "excessive_usage_threshold_min"

    private lateinit var sharedPrefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var restrictedAppsCache: Map<String, String> = emptyMap()

    fun init(ctx: Context) {
        if (!::sharedPrefs.isInitialized) {
            sharedPrefs = ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        restrictedAppsCache = loadRestrictedApps()
    }

    private fun prefs() = sharedPrefs

    fun restrictedApps(): Map<String, String> = restrictedAppsCache

    fun setRestrictedApps(apps: Map<String, String>) {
        restrictedAppsCache = apps
        prefs().edit { putString(KEY_RESTRICTED_APPS, json.encodeToString(apps)) }
    }

    private fun loadRestrictedApps(): Map<String, String> {
        val raw = prefs().getString(KEY_RESTRICTED_APPS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }
            .getOrDefault(emptyMap())
    }

    var afternoonThresholdMin: Int
        get() = prefs().getInt(KEY_AFTERNOON_THRESHOLD_MIN, 15 * 60)
        set(value) = prefs().edit { putInt(KEY_AFTERNOON_THRESHOLD_MIN, value) }

    var sleepThresholdMin: Int
        get() = prefs().getInt(KEY_SLEEP_THRESHOLD_MIN, 7 * 60)
        set(value) = prefs().edit { putInt(KEY_SLEEP_THRESHOLD_MIN, value) }

    var studyDailyTarget: Int
        get() = prefs().getInt(KEY_STUDY_DAILY_TARGET, 20)
        set(value) = prefs().edit { putInt(KEY_STUDY_DAILY_TARGET, value) }

    var studyReminderIntervalMin: Int
        get() = prefs().getInt(KEY_STUDY_REMINDER_INTERVAL_MIN, 120)
        set(value) = prefs().edit { putInt(KEY_STUDY_REMINDER_INTERVAL_MIN, value) }

    var overlayEnabled: Boolean
        get() = prefs().getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs().edit { putBoolean(KEY_OVERLAY_ENABLED, value) }

    var cooldownMinutes: Int
        get() = prefs().getInt(KEY_COOLDOWN_MINUTES, 30)
        set(value) = prefs().edit { putInt(KEY_COOLDOWN_MINUTES, value) }

    var schoolCalendarEnabled: Boolean
        get() = prefs().getBoolean(KEY_SCHOOL_CALENDAR_ENABLED, true)
        set(value) = prefs().edit { putBoolean(KEY_SCHOOL_CALENDAR_ENABLED, value) }

    var points: Int
        get() = prefs().getInt(KEY_POINTS, 0)
        set(value) = prefs().edit { putInt(KEY_POINTS, value) }

    var currentStreak: Int
        get() = prefs().getInt(KEY_CURRENT_STREAK, 0)
        set(value) = prefs().edit { putInt(KEY_CURRENT_STREAK, value) }

    var lastActiveDayYmd: String
        get() = prefs().getString(KEY_LAST_ACTIVE_DAY_YMD, "") ?: ""
        set(value) = prefs().edit { putString(KEY_LAST_ACTIVE_DAY_YMD, value) }

    var overrideUntilMs: Long
        get() = prefs().getLong(KEY_OVERRIDE_UNTIL_MS, 0L)
        set(value) = prefs().edit { putLong(KEY_OVERRIDE_UNTIL_MS, value) }

    var dailySummaryHour: Int
        get() = prefs().getInt(KEY_DAILY_SUMMARY_HOUR, 20)
        set(value) = prefs().edit { putInt(KEY_DAILY_SUMMARY_HOUR, value) }

    var notificationsEnabled: Boolean
        get() = prefs().getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs().edit { putBoolean(KEY_NOTIFICATIONS_ENABLED, value) }

    var excessiveUsageThresholdMin: Int
        get() = prefs().getInt(KEY_EXCESSIVE_USAGE_THRESHOLD_MIN, 120)
        set(value) = prefs().edit { putInt(KEY_EXCESSIVE_USAGE_THRESHOLD_MIN, value) }

}
