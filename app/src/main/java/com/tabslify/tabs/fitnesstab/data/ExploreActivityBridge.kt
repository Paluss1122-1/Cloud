package com.tabslify.tabs.fitnesstab.data

import android.content.Context
import com.tabslify.tabs.exploretab.ExploreRepository
import com.tabslify.tabs.exploretab.ExploreSegmentBuilder
import com.tabslify.tabs.exploretab.Segment
import com.tabslify.tabs.exploretab.dayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

const val EXPLORE_SESSION_PREFIX = "explore_"
const val EXERCISE_ID_WALKING = "cardio_walking"
const val EXERCISE_ID_CYCLING = "cardio_cycling"

object ExploreActivityBridge {

    private const val MIN_DURATION_MS = 120_000L
    private const val MIN_DISTANCE_METERS = 150f
    private const val EBIKE_EFFORT_FACTOR = 0.55f

    fun isExploreSession(sessionId: String): Boolean = sessionId.startsWith(EXPLORE_SESSION_PREFIX)

    suspend fun sessionsForLastDays(
        context: Context,
        days: Int,
        calorieFactor: Float
    ): List<WorkoutSession> = withContext(Dispatchers.Default) {
        val appContext = context.applicationContext
        val repo = ExploreRepository(appContext)
        val snapshot = BikeModeOverrides.snapshot(appContext)
        val today = LocalDate.now()
        val sessions = mutableListOf<WorkoutSession>()
        for (offset in 0 until days) {
            val day = today.minusDays(offset.toLong())
            segmentsForDay(repo, day).forEach { segment ->
                val mode = snapshot.modeFor(segment.startTime, segment.mode)
                toSession(segment, mode, calorieFactor)?.let { sessions += it }
            }
        }
        sessions
    }

    private suspend fun segmentsForDay(repo: ExploreRepository, day: LocalDate): List<Segment> {
        val (dayStart, dayEnd) = dayBounds(day)
        val points = repo.pointsForDay(dayStart, dayEnd)
        if (points.isNotEmpty()) return ExploreSegmentBuilder.build(points).first
        return repo.segmentsBetween(dayStart, dayEnd)
    }

    fun caloriesFor(
        exerciseId: String,
        distanceKm: Float,
        durationMs: Long,
        mode: String,
        calorieFactor: Float
    ): Float {
        val minutes = durationMs / 60_000f
        if (minutes <= 0f) return 0f
        val speedKmh = distanceKm / (minutes / 60f)
        return kcalPerMinute(exerciseId, speedKmh) * minutes * calorieFactor * effortFactor(mode)
    }

    private fun toSession(segment: Segment, mode: String, calorieFactor: Float): WorkoutSession? {
        val exerciseId = exerciseIdFor(mode) ?: return null
        val durationMs = segment.endTime - segment.startTime
        if (durationMs < MIN_DURATION_MS || segment.distanceMeters < MIN_DISTANCE_METERS) return null

        val km = segment.distanceMeters / 1000f
        val sessionId = "$EXPLORE_SESSION_PREFIX${exerciseId}_${segment.startTime}"

        return WorkoutSession(
            sessionId = sessionId,
            dateStartMs = segment.startTime,
            dateEndMs = segment.endTime,
            entries = listOf(
                WorkoutEntry(
                    entryId = "${sessionId}_entry",
                    exerciseId = exerciseId,
                    sets = listOf(
                        ExerciseSet(
                            setNumber = 1,
                            durationSeconds = (durationMs / 1000L).toInt(),
                            distanceKm = km,
                            completed = true
                        )
                    ),
                    startedAtMs = segment.startTime,
                    finishedAtMs = segment.endTime
                )
            ),
            totalCalories = caloriesFor(exerciseId, km, durationMs, mode, calorieFactor)
        )
    }

    private fun exerciseIdFor(mode: String): String? = when (mode) {
        "WALKING", "ON_FOOT" -> EXERCISE_ID_WALKING
        "ON_BICYCLE", "ON_EBIKE" -> EXERCISE_ID_CYCLING
        else -> null
    }

    private fun effortFactor(mode: String): Float = if (mode == "ON_EBIKE") EBIKE_EFFORT_FACTOR else 1f

    private fun kcalPerMinute(exerciseId: String, speedKmh: Float): Float = when (exerciseId) {
        EXERCISE_ID_CYCLING -> (7f * speedKmh / 18f).coerceIn(3.5f, 14f)
        else -> (4.5f * speedKmh / 5f).coerceIn(2.5f, 11f)
    }
}
