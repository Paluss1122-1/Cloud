package com.tabslify.tabs.focusguard.monitoring

import com.tabslify.tabs.focusguard.data.FocusGuardConfig
import com.tabslify.tabs.focusguard.data.FocusGuardRepository
import com.tabslify.tabs.focusguard.data.StudyGoal

object StudyGoalTracker {
    fun todayGoal(): StudyGoal? = FocusGuardRepository.todayGoal.value

    fun quotaDone(): Boolean {
        val goal = FocusGuardRepository.todayGoal.value ?: return false
        return goal.targetCount <= 0 || goal.completedCount >= goal.targetCount
    }

    fun progress(): Int = FocusGuardRepository.todayGoal.value?.completedCount ?: 0

    fun needsReminder(intervalMin: Int = FocusGuardConfig.studyReminderIntervalMin): Boolean {
        val goal = FocusGuardRepository.todayGoal.value ?: return false
        if (quotaDone()) return false
        val last = goal.lastReminderAtMs ?: 0L
        return System.currentTimeMillis() - last >= intervalMin * 60_000L
    }

}
