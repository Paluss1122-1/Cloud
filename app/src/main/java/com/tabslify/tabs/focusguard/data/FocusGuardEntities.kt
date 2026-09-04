package com.tabslify.tabs.focusguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

const val CATEGORY_SOCIAL = "SOCIAL"
const val CATEGORY_GAMING = "GAMING"
const val CATEGORY_ENTERTAINMENT = "ENTERTAINMENT"
const val CATEGORY_PRODUCTIVITY = "PRODUCTIVITY"
const val CATEGORY_OTHER = "OTHER"

val FOCUSGUARD_CATEGORIES = listOf(
    CATEGORY_SOCIAL,
    CATEGORY_GAMING,
    CATEGORY_ENTERTAINMENT,
    CATEGORY_PRODUCTIVITY,
    CATEGORY_OTHER
)

val FOCUSGUARD_RESTRICTED_CATEGORIES = listOf(
    CATEGORY_SOCIAL,
    CATEGORY_GAMING,
    CATEGORY_ENTERTAINMENT
)

@Entity(tableName = "app_usage_logs")
data class AppUsageLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val category: String,
    val date: String,
    val sessionStartMs: Long,
    val sessionEndMs: Long,
    val durationMs: Long,
    val synced: Boolean = false
)

@Entity(tableName = "restriction_rules")
data class RestrictionRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysOfWeekMask: Int = 127,
    val enabled: Boolean = true,
    val synced: Boolean = false
)

@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String,
    val bedtimeMs: Long,
    val wakeMs: Long,
    val durationMs: Long,
    val synced: Boolean = false
)

@Entity(tableName = "study_goals")
data class StudyGoal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String,
    val targetCount: Int,
    val completedCount: Int = 0,
    val deadlineMs: Long? = null,
    val reminderIntervalMin: Int = 120,
    val lastReminderAtMs: Long? = null,
    val synced: Boolean = false
)

@Entity(tableName = "user_achievements")
data class UserAchievement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,
    val earnedAtMs: Long,
    val points: Int = 0,
    val meta: String? = null,
    val synced: Boolean = false
)
