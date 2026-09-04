package com.tabslify.tabs.focusguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppUsageLog::class,
        RestrictionRule::class,
        SleepRecord::class,
        StudyGoal::class,
        UserAchievement::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FocusGuardDatabase : RoomDatabase() {
    abstract fun appUsageLogDao(): AppUsageLogDao
    abstract fun restrictionRuleDao(): RestrictionRuleDao
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun studyGoalDao(): StudyGoalDao
    abstract fun userAchievementDao(): UserAchievementDao

    companion object {
        @Volatile
        private var INSTANCE: FocusGuardDatabase? = null

        fun get(context: Context): FocusGuardDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    FocusGuardDatabase::class.java,
                    "focusguard_db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
    }
}
