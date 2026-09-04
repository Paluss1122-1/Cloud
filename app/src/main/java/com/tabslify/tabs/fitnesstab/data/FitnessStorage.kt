package com.tabslify.tabs.fitnesstab.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FitnessStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(session: WorkoutSession) {
        val sessions = loadAllSessions().toMutableList()
        sessions.removeAll { it.sessionId == session.sessionId }
        sessions.add(session)
        saveSessions(sessions)
    }

    fun deleteSession(sessionId: String) {
        val sessions = loadAllSessions().filter { it.sessionId != sessionId }
        saveSessions(sessions)
    }

    fun loadAllSessions(): List<WorkoutSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx -> parseSession(arr.getJSONObject(idx)) }
        }.getOrDefault(emptyList())
    }

    fun loadSessionsForDay(ymd: String): List<WorkoutSession> {
        return loadAllSessions().filter { ymdOf(it.dateStartMs) == ymd }
    }

    fun mergeWithStored(extraSessions: List<WorkoutSession>): List<WorkoutSession> {
        val stored = loadAllSessions()
        if (extraSessions.isEmpty()) return stored
        val storedIds = stored.mapTo(mutableSetOf()) { it.sessionId }
        return stored + extraSessions.filter { it.sessionId !in storedIds }
    }

    fun loadLastNDays(days: Int, extraSessions: List<WorkoutSession> = emptyList()): Map<String, DayStats> {
        val result = mutableMapOf<String, DayStats>()
        val sessions = mergeWithStored(extraSessions)
        val sessionsByDay = sessions.groupBy { ymdOf(it.dateStartMs) }

        val cal = Calendar.getInstance()
        repeat(days) {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val ymd = ymdOf(cal.timeInMillis)
            val daySessions = sessionsByDay[ymd].orEmpty()
            result[ymd] = buildDayStats(ymd, daySessions)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return result
    }

    fun getFitnessStats(extraSessions: List<WorkoutSession> = emptyList()): FitnessStats {
        val all = mergeWithStored(extraSessions)
        val todayYmd = ymdOf(System.currentTimeMillis())
        val todaySessions = all.filter { ymdOf(it.dateStartMs) == todayYmd }
        val today = buildDayStats(todayYmd, todaySessions)

        val streak = calcStreak(all)
        val totalMinutes = all.sumOf { sessionMinutes(it) }
        val totalCalories = all.sumOf { it.totalCalories.toDouble() }.toFloat()
        val totalDistanceKm = all.sumOf { sessionDistanceKm(it).toDouble() }.toFloat()

        return FitnessStats(
            streakDays = streak,
            totalWorkouts = all.size,
            totalMinutes = totalMinutes,
            totalCalories = totalCalories,
            totalDistanceKm = totalDistanceKm,
            lastWorkoutMs = all.maxOfOrNull { it.dateEndMs } ?: 0L,
            today = today
        )
    }

    fun getUserWeightKg(): Float = prefs.getFloat(KEY_USER_WEIGHT, 75f)

    fun setUserWeightKg(kg: Float) = prefs.edit { putFloat(KEY_USER_WEIGHT, kg) }

    fun getUserAgeYears(): Int = prefs.getInt(KEY_USER_AGE, 30)

    fun setUserAgeYears(age: Int) = prefs.edit { putInt(KEY_USER_AGE, age) }

    fun calorieFactor(): Float {
        val weightFactor = getUserWeightKg() / 75f
        val age = getUserAgeYears()
        val ageFactor = if (age <= 25) 1f else (1f - (age - 25) * 0.003f).coerceIn(0.85f, 1f)
        return weightFactor * ageFactor
    }

    fun getDailyGoalMinutes(): Int = prefs.getInt(KEY_GOAL_MINUTES, 30)

    fun setDailyGoalMinutes(min: Int) = prefs.edit { putInt(KEY_GOAL_MINUTES, min) }

    fun getDailyGoalCalories(): Int = prefs.getInt(KEY_GOAL_CALORIES, 300)

    fun setDailyGoalCalories(kcal: Int) = prefs.edit { putInt(KEY_GOAL_CALORIES, kcal) }

    // ----- private helpers -----

    private fun saveSessions(sessions: List<WorkoutSession>) {
        val arr = JSONArray()
        sessions.sortedByDescending { it.dateStartMs }.forEach { arr.put(serializeSession(it)) }
        prefs.edit { putString(KEY_SESSIONS, arr.toString()) }
    }

    private fun serializeSession(s: WorkoutSession): JSONObject {
        val entriesArr = JSONArray()
        s.entries.forEach { entry ->
            val setsArr = JSONArray()
            entry.sets.forEach { set ->
                setsArr.put(
                    JSONObject()
                        .put("n", set.setNumber)
                        .put("reps", set.reps)
                        .put("w", set.weightKg.toDouble())
                        .put("dur", set.durationSeconds)
                        .put("dist", set.distanceKm.toDouble())
                        .put("done", set.completed)
                )
            }
            entriesArr.put(
                JSONObject()
                    .put("id", entry.entryId)
                    .put("exId", entry.exerciseId)
                    .put("sets", setsArr)
                    .put("notes", entry.notes)
                    .put("start", entry.startedAtMs)
                    .put("end", entry.finishedAtMs)
            )
        }
        return JSONObject()
            .put("id", s.sessionId)
            .put("start", s.dateStartMs)
            .put("end", s.dateEndMs)
            .put("entries", entriesArr)
            .put("kcal", s.totalCalories.toDouble())
            .put("name", s.name)
    }

    private fun parseSession(obj: JSONObject): WorkoutSession? {
        return runCatching {
            val entriesArr = obj.getJSONArray("entries")
            val entries = (0 until entriesArr.length()).map { i ->
                val eObj = entriesArr.getJSONObject(i)
                val setsArr = eObj.getJSONArray("sets")
                val sets = (0 until setsArr.length()).map { j ->
                    val sObj = setsArr.getJSONObject(j)
                    ExerciseSet(
                        setNumber = sObj.getInt("n"),
                        reps = sObj.getInt("reps"),
                        weightKg = sObj.getDouble("w").toFloat(),
                        durationSeconds = sObj.getInt("dur"),
                        distanceKm = sObj.getDouble("dist").toFloat(),
                        completed = sObj.getBoolean("done")
                    )
                }
                WorkoutEntry(
                    entryId = eObj.getString("id"),
                    exerciseId = eObj.getString("exId"),
                    sets = sets,
                    notes = eObj.optString("notes", ""),
                    startedAtMs = eObj.optLong("start", 0L),
                    finishedAtMs = eObj.optLong("end", 0L)
                )
            }
            WorkoutSession(
                sessionId = obj.getString("id"),
                dateStartMs = obj.getLong("start"),
                dateEndMs = obj.getLong("end"),
                entries = entries,
                totalCalories = obj.getDouble("kcal").toFloat(),
                name = obj.optString("name", "")
            )
        }.getOrNull()
    }

    private fun buildDayStats(ymd: String, sessions: List<WorkoutSession>): DayStats {
        val minutes = sessions.sumOf { sessionMinutes(it) }
        val kcal = sessions.sumOf { it.totalCalories.toDouble() }.toFloat()
        var totalReps = 0
        var exercisesCount = 0
        sessions.forEach { s ->
            s.entries.forEach { e ->
                exercisesCount++
                totalReps += e.sets.sumOf { it.reps }
            }
        }
        return DayStats(
            dateYmd = ymd,
            totalMinutes = minutes,
            totalCalories = kcal,
            workoutsCount = sessions.size,
            exercisesCount = exercisesCount,
            totalReps = totalReps,
            totalDistanceKm = sessions.sumOf { sessionDistanceKm(it).toDouble() }.toFloat(),
            sessionIds = sessions.map { it.sessionId }
        )
    }

    private fun sessionDistanceKm(s: WorkoutSession): Float =
        s.entries.sumOf { e -> e.sets.sumOf { it.distanceKm.toDouble() } }.toFloat()

    private fun calcStreak(sessions: List<WorkoutSession>): Int {
        if (sessions.isEmpty()) return 0
        val daysSet = sessions.map { ymdOf(it.dateStartMs) }.toSet()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (!daysSet.contains(ymdOf(cal.timeInMillis))) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (!daysSet.contains(ymdOf(cal.timeInMillis))) return 0
        }

        var streak = 0
        while (daysSet.contains(ymdOf(cal.timeInMillis))) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun sessionMinutes(s: WorkoutSession): Int {
        val diff = (s.dateEndMs - s.dateStartMs).coerceAtLeast(0L)
        return (diff / 60_000L).toInt()
    }

    private fun ymdOf(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date(ms))
    }

    companion object {
        private const val PREFS_NAME = "fitness_storage_v1"
        private const val KEY_SESSIONS = "sessions_json"
        private const val KEY_USER_WEIGHT = "user_weight_kg"
        private const val KEY_USER_AGE = "user_age_years"
        private const val KEY_GOAL_MINUTES = "goal_minutes"
        private const val KEY_GOAL_CALORIES = "goal_calories"
    }
}
