package com.tabslify.tabs.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class CategoryTotal(
    val category: String,
    val total: Long
)

data class PackageTotal(
    val packageName: String,
    val total: Long
)

data class DailyCategoryTotal(
    val date: String,
    val category: String,
    val total: Long
)

@Dao
interface AppUsageLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AppUsageLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AppUsageLog>)

    @Query("SELECT * FROM app_usage_logs WHERE date = :date")
    suspend fun forDate(date: String): List<AppUsageLog>

    @Query("SELECT * FROM app_usage_logs WHERE sessionEndMs >= :from AND sessionEndMs < :to")
    suspend fun between(from: Long, to: Long): List<AppUsageLog>

    @Query("SELECT * FROM app_usage_logs WHERE synced = 0")
    suspend fun unsynced(): List<AppUsageLog>

    @Query("UPDATE app_usage_logs SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT category, SUM(durationMs) AS total FROM app_usage_logs WHERE date = :date GROUP BY category")
    suspend fun totalsByCategory(date: String): List<CategoryTotal>

    @Query("SELECT packageName, SUM(durationMs) AS total FROM app_usage_logs WHERE date = :date GROUP BY packageName ORDER BY total DESC")
    suspend fun totalsByPackage(date: String): List<PackageTotal>

    @Query("SELECT date, category, SUM(durationMs) AS total FROM app_usage_logs WHERE date >= :fromDate GROUP BY date, category ORDER BY date")
    suspend fun dailyTotals(fromDate: String): List<DailyCategoryTotal>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM app_usage_logs WHERE date = :date AND packageName = :packageName")
    suspend fun packageDuration(date: String, packageName: String): Long

    @Query("DELETE FROM app_usage_logs WHERE date < :cutoffDate")
    suspend fun deleteBefore(cutoffDate: String)

    @Query("DELETE FROM app_usage_logs WHERE synced = 1 AND sessionEndMs < :cutoff")
    suspend fun deleteSyncedBefore(cutoff: Long)
}

@Dao
interface RestrictionRuleDao {
    @Query("SELECT * FROM restriction_rules ORDER BY category")
    suspend fun all(): List<RestrictionRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<RestrictionRule>)

    @Query("SELECT * FROM restriction_rules WHERE synced = 0")
    suspend fun unsynced(): List<RestrictionRule>

    @Query("UPDATE restriction_rules SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface SleepRecordDao {
    @Query("SELECT * FROM sleep_records WHERE date = :date LIMIT 1")
    suspend fun forDate(date: String): SleepRecord?

    @Query("SELECT * FROM sleep_records WHERE date <= :date ORDER BY date DESC LIMIT :limit")
    suspend fun lastN(date: String, limit: Int): List<SleepRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SleepRecord)

    @Query("SELECT * FROM sleep_records WHERE synced = 0")
    suspend fun unsynced(): List<SleepRecord>

    @Query("UPDATE sleep_records SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query(
        "DELETE FROM sleep_records AS s WHERE EXISTS (" +
            "SELECT 1 FROM sleep_records AS o WHERE o.date = s.date AND (" +
            "o.wakeMs > s.wakeMs OR " +
            "(o.wakeMs = s.wakeMs AND o.bedtimeMs > s.bedtimeMs) OR " +
            "(o.wakeMs = s.wakeMs AND o.bedtimeMs = s.bedtimeMs AND o.id > s.id)))"
    )
    suspend fun deleteDuplicates()
}

@Dao
interface StudyGoalDao {
    @Query("SELECT * FROM study_goals WHERE date = :date LIMIT 1")
    suspend fun forDate(date: String): StudyGoal?

    @Query("SELECT * FROM study_goals WHERE date >= :fromDate ORDER BY date")
    suspend fun since(fromDate: String): List<StudyGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: StudyGoal)

    @Query("SELECT * FROM study_goals WHERE synced = 0")
    suspend fun unsynced(): List<StudyGoal>

    @Query("UPDATE study_goals SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface UserAchievementDao {
    @Query("SELECT * FROM user_achievements ORDER BY earnedAtMs DESC")
    suspend fun all(): List<UserAchievement>

    @Query("SELECT * FROM user_achievements WHERE type = :type LIMIT 1")
    suspend fun byType(type: String): UserAchievement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: UserAchievement)

    @Query("SELECT * FROM user_achievements WHERE synced = 0")
    suspend fun unsynced(): List<UserAchievement>

    @Query("UPDATE user_achievements SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
