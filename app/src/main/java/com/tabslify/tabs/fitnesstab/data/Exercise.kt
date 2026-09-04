package com.tabslify.tabs.fitnesstab.data

enum class MuscleGroup {
    CHEST, BACK, LEGS, CORE, SHOULDERS, ARMS, CARDIO, FULLBODY
}

enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

enum class Equipment { NONE, RESISTANCE_BAND, DUMBBELL, BARBELL, KETTLEBELL, PULLUP_BAR, BENCH }

enum class TrackingType {
    REPS,
    TIME,
    DISTANCE,
    WEIGHT_REPS,
    CAMERA_POSE
}

data class Exercise(
    val id: String,
    val nameRes: Int,
    val muscleGroup: MuscleGroup,
    val difficulty: Difficulty,
    val equipment: Equipment,
    val trackingType: TrackingType,
    val descriptionRes: Int = 0,
    val stepsRes: List<Int> = emptyList(),
    val tipsRes: List<Int> = emptyList(),
    val mistakesRes: List<Int> = emptyList(),
    val caloriesPerMinute: Float = 6f,
    val iconRes: Int = 0
)

data class ExerciseSet(
    val setNumber: Int,
    val reps: Int = 0,
    val weightKg: Float = 0f,
    val durationSeconds: Int = 0,
    val distanceKm: Float = 0f,
    val completed: Boolean = false
)

data class WorkoutEntry(
    val entryId: String,
    val exerciseId: String,
    val sets: List<ExerciseSet>,
    val notes: String = "",
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long = 0L
)

data class WorkoutSession(
    val sessionId: String,
    val dateStartMs: Long,
    val dateEndMs: Long,
    val entries: List<WorkoutEntry>,
    val totalCalories: Float = 0f,
    val name: String = ""
)

data class DayStats(
    val dateYmd: String,
    val totalMinutes: Int = 0,
    val totalCalories: Float = 0f,
    val workoutsCount: Int = 0,
    val exercisesCount: Int = 0,
    val totalReps: Int = 0,
    val totalDistanceKm: Float = 0f,
    val sessionIds: List<String> = emptyList()
)

data class FitnessStats(
    val streakDays: Int = 0,
    val totalWorkouts: Int = 0,
    val totalMinutes: Int = 0,
    val totalCalories: Float = 0f,
    val totalDistanceKm: Float = 0f,
    val lastWorkoutMs: Long = 0L,
    val today: DayStats = DayStats(dateYmd = "")
)
