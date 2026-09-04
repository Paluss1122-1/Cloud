package com.tabslify.tabs.focusguard.data

import android.content.Context
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.prvt
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object FocusGuardRepository {
    private lateinit var context: Context
    private lateinit var db: FocusGuardDatabase

    private val _todayUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val todayUsage: StateFlow<Map<String, Long>> = _todayUsage.asStateFlow()

    private val _todayPackages = MutableStateFlow<List<PackageTotal>>(emptyList())
    val todayPackages: StateFlow<List<PackageTotal>> = _todayPackages.asStateFlow()

    private val _sleepWeek = MutableStateFlow<List<SleepRecord>>(emptyList())
    val sleepWeek: StateFlow<List<SleepRecord>> = _sleepWeek.asStateFlow()

    private val _todayGoal = MutableStateFlow<StudyGoal?>(null)
    val todayGoal: StateFlow<StudyGoal?> = _todayGoal.asStateFlow()

    private val _achievements = MutableStateFlow<List<UserAchievement>>(emptyList())
    val achievements: StateFlow<List<UserAchievement>> = _achievements.asStateFlow()

    private val _rules = MutableStateFlow<List<RestrictionRule>>(emptyList())
    val rules: StateFlow<List<RestrictionRule>> = _rules.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(ctx: Context) {
        context = ctx.applicationContext
        if (initialized) return
        initialized = true
        db = FocusGuardDatabase.get(context)
        FocusGuardConfig.init(context)
        refresh()
    }

    fun refresh() {
        appScope.launch(Dispatchers.IO) {
            runCatching { refreshNow() }
        }
    }

    suspend fun refreshNow() {
        withContext(Dispatchers.IO) {
            val today = todayYmd()
            _todayUsage.value = db.appUsageLogDao().totalsByCategory(today)
                .associate { it.category to it.total }
            _todayPackages.value = db.appUsageLogDao().totalsByPackage(today)
            _sleepWeek.value = db.sleepRecordDao().lastN(today, 7)
            _todayGoal.value = db.studyGoalDao().forDate(today)
            _achievements.value = db.userAchievementDao().all()
            _rules.value = db.restrictionRuleDao().all()
        }
    }

    fun logUsage(logs: List<AppUsageLog>) {
        if (logs.isEmpty()) return
        appScope.launch(Dispatchers.IO) {
            runCatching {
                db.appUsageLogDao().insertAll(logs)
            }
            refreshUsage()
        }
    }

    fun logUsage(packageName: String, category: String, sessionStartMs: Long, sessionEndMs: Long) {
        val start = minOf(sessionStartMs, sessionEndMs)
        val end = maxOf(sessionStartMs, sessionEndMs)
        if (end - start <= 0L) return
        val log = AppUsageLog(
            packageName = packageName,
            category = category,
            date = ymdOf(end),
            sessionStartMs = start,
            sessionEndMs = end,
            durationMs = end - start
        )
        appScope.launch(Dispatchers.IO) {
            runCatching { db.appUsageLogDao().insert(log) }
            refreshUsage()
        }
    }

    fun saveRules(rules: List<RestrictionRule>) {
        appScope.launch(Dispatchers.IO) {
            runCatching { db.restrictionRuleDao().upsertAll(rules) }
            _rules.value = rules
        }
    }

    suspend fun upsertSleep(record: SleepRecord) {
        withContext(Dispatchers.IO) {
            runCatching { db.sleepRecordDao().upsert(record) }
        }
        refresh()
    }

    suspend fun ensureTodayGoal(targetCount: Int = FocusGuardConfig.studyDailyTarget, deadlineMs: Long? = null): StudyGoal {
        val today = todayYmd()
        val existing = db.studyGoalDao().forDate(today)
        val goal = existing ?: StudyGoal(date = today, targetCount = targetCount, deadlineMs = deadlineMs)
        if (existing == null) {
            runCatching { db.studyGoalDao().upsert(goal) }
        }
        _todayGoal.value = goal
        return goal
    }

    fun addStudyProgress(count: Int) {
        appScope.launch(Dispatchers.IO) {
            val today = todayYmd()
            val goal = runCatching { db.studyGoalDao().forDate(today) }
                .getOrNull()
                ?: StudyGoal(
                    date = today,
                    targetCount = FocusGuardConfig.studyDailyTarget
                )
            val updated = goal.copy(completedCount = goal.completedCount + count)
            runCatching { db.studyGoalDao().upsert(updated) }
            _todayGoal.value = updated
        }
    }

    fun resetTodayGoal() {
        appScope.launch(Dispatchers.IO) {
            val today = todayYmd()
            val goal = runCatching { db.studyGoalDao().forDate(today) }.getOrNull() ?: return@launch
            val updated = goal.copy(completedCount = 0)
            runCatching { db.studyGoalDao().upsert(updated) }
            _todayGoal.value = updated
        }
    }

    fun markGoalReminderSent() {
        appScope.launch(Dispatchers.IO) {
            val today = todayYmd()
            val goal = runCatching { db.studyGoalDao().forDate(today) }.getOrNull() ?: return@launch
            val updated = goal.copy(lastReminderAtMs = System.currentTimeMillis())
            runCatching { db.studyGoalDao().upsert(updated) }
            _todayGoal.value = updated
        }
    }

    suspend fun dailyUsageForLastDays(days: Int): Map<String, Map<String, Long>> =
        withContext(Dispatchers.IO) {
            val from = ymdDaysAgo(days - 1)
            runCatching {
                db.appUsageLogDao().dailyTotals(from)
                    .groupBy({ it.date }, { it.category to it.total })
                    .mapValues { e -> e.value.toMap() }
            }.getOrDefault(emptyMap())
        }

    suspend fun goalsSince(days: Int): List<StudyGoal> = withContext(Dispatchers.IO) {
        val from = ymdDaysAgo(days - 1)
        runCatching { db.studyGoalDao().since(from) }.getOrDefault(emptyList())
    }

    suspend fun sleepSince(days: Int): List<SleepRecord> = withContext(Dispatchers.IO) {
        runCatching { db.sleepRecordDao().lastN(todayYmd(), days) }.getOrDefault(emptyList())
    }

    fun recordAchievement(type: String, points: Int, meta: String? = null) {
        appScope.launch(Dispatchers.IO) {
            val existing = runCatching { db.userAchievementDao().byType(type) }.getOrNull()
            if (existing != null) return@launch
            val achievement = UserAchievement(
                type = type,
                earnedAtMs = System.currentTimeMillis(),
                points = points,
                meta = meta
            )
            runCatching { db.userAchievementDao().insert(achievement) }
            FocusGuardConfig.points = FocusGuardConfig.points + points
            refresh()
        }
    }

    fun syncToSupabase() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                if (!prvt()) return@runCatching
                val logs = db.appUsageLogDao().unsynced()
                if (logs.isNotEmpty()) {
                    Config.client.from("app_usage_logs").upsert(
                        buildJsonArray { logs.forEach { add(usageLogToJson(it)) } }
                    )
                    db.appUsageLogDao().markSynced(logs.map { it.id })
                }
                val rules = db.restrictionRuleDao().unsynced()
                if (rules.isNotEmpty()) {
                    Config.client.from("restriction_rules").upsert(
                        buildJsonArray { rules.forEach { add(ruleToJson(it)) } }
                    )
                    db.restrictionRuleDao().markSynced(rules.map { it.id })
                }
                val sleeps = db.sleepRecordDao().unsynced()
                if (sleeps.isNotEmpty()) {
                    Config.client.from("sleep_records").upsert(
                        buildJsonArray { sleeps.forEach { add(sleepToJson(it)) } }
                    )
                    db.sleepRecordDao().markSynced(sleeps.map { it.id })
                }
                val goals = db.studyGoalDao().unsynced()
                if (goals.isNotEmpty()) {
                    Config.client.from("study_goals").upsert(
                        buildJsonArray { goals.forEach { add(goalToJson(it)) } }
                    )
                    db.studyGoalDao().markSynced(goals.map { it.id })
                }
                val achievements = db.userAchievementDao().unsynced()
                if (achievements.isNotEmpty()) {
                    Config.client.from("user_achievements").upsert(
                        buildJsonArray { achievements.forEach { add(achievementToJson(it)) } }
                    )
                    db.userAchievementDao().markSynced(achievements.map { it.id })
                }
            }
        }
    }

    private fun refreshUsage() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val today = todayYmd()
                _todayUsage.value = db.appUsageLogDao().totalsByCategory(today)
                    .associate { it.category to it.total }
                _todayPackages.value = db.appUsageLogDao().totalsByPackage(today)
            }
        }
    }

    private fun usageLogToJson(log: AppUsageLog): JsonObject = buildJsonObject {
        put("id", log.id)
        put("package_name", log.packageName)
        put("category", log.category)
        put("date", log.date)
        put("session_start", log.sessionStartMs)
        put("session_end", log.sessionEndMs)
        put("duration_ms", log.durationMs)
        put("synced", true)
    }

    private fun ruleToJson(rule: RestrictionRule): JsonObject = buildJsonObject {
        put("id", rule.id)
        put("category", rule.category)
        put("start_minute", rule.startMinute)
        put("end_minute", rule.endMinute)
        put("days_of_week_mask", rule.daysOfWeekMask)
        put("enabled", rule.enabled)
    }

    private fun sleepToJson(record: SleepRecord): JsonObject = buildJsonObject {
        put("id", record.id)
        put("date", record.date)
        put("bedtime", record.bedtimeMs)
        put("wake", record.wakeMs)
        put("duration_ms", record.durationMs)
    }

    private fun goalToJson(goal: StudyGoal): JsonObject = buildJsonObject {
        put("id", goal.id)
        put("date", goal.date)
        put("target_count", goal.targetCount)
        put("completed_count", goal.completedCount)
        goal.deadlineMs?.let { put("deadline", it) }
        put("reminder_interval_min", goal.reminderIntervalMin)
        goal.lastReminderAtMs?.let { put("last_reminder_at", it) }
    }

    private fun achievementToJson(achievement: UserAchievement): JsonObject = buildJsonObject {
        put("id", achievement.id)
        put("type", achievement.type)
        put("earned_at", achievement.earnedAtMs)
        put("points", achievement.points)
        achievement.meta?.let { put("meta", it) }
    }
}
