package com.tabslify.tabs.fitnesstab.data

import com.tabslify.R

object ExerciseRepository {

    private val cached: List<Exercise> by lazy { build() }

    private val byId: Map<String, Exercise> by lazy { cached.associateBy { it.id } }

    fun all(): List<Exercise> = cached

    private fun build(): List<Exercise> = listOf(
        // ==== CHEST ====
        Exercise(
            id = "chest_pushups",
            nameRes = R.string.fitness_ex_chest_pushups,
            muscleGroup = MuscleGroup.CHEST,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.CAMERA_POSE,
            descriptionRes = R.string.fitness_ex_chest_pushups_desc,
            stepsRes = listOf(
                R.string.fitness_ex_chest_pushups_step_1,
                R.string.fitness_ex_chest_pushups_step_2,
                R.string.fitness_ex_chest_pushups_step_3,
                R.string.fitness_ex_chest_pushups_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_chest_pushups_tip_1,
                R.string.fitness_ex_chest_pushups_tip_2,
                R.string.fitness_ex_chest_pushups_tip_3
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_chest_pushups_mistake_1,
                R.string.fitness_ex_chest_pushups_mistake_2,
                R.string.fitness_ex_chest_pushups_mistake_3
            ),
            caloriesPerMinute = 8.5f
        ),
        Exercise(
            id = "chest_wide",
            nameRes = R.string.fitness_ex_chest_wide,
            muscleGroup = MuscleGroup.CHEST,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_chest_wide_desc,
            stepsRes = listOf(
                R.string.fitness_ex_chest_wide_step_1,
                R.string.fitness_ex_chest_wide_step_2,
                R.string.fitness_ex_chest_wide_step_3,
                R.string.fitness_ex_chest_wide_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_chest_wide_tip_1,
                R.string.fitness_ex_chest_wide_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_chest_wide_mistake_1,
                R.string.fitness_ex_chest_wide_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "chest_diamond",
            nameRes = R.string.fitness_ex_chest_diamond,
            muscleGroup = MuscleGroup.CHEST,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_chest_diamond_desc,
            stepsRes = listOf(
                R.string.fitness_ex_chest_diamond_step_1,
                R.string.fitness_ex_chest_diamond_step_2,
                R.string.fitness_ex_chest_diamond_step_3,
                R.string.fitness_ex_chest_diamond_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_chest_diamond_tip_1,
                R.string.fitness_ex_chest_diamond_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_chest_diamond_mistake_1,
                R.string.fitness_ex_chest_diamond_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "chest_incline",
            nameRes = R.string.fitness_ex_chest_incline,
            muscleGroup = MuscleGroup.CHEST,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.BENCH,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_chest_incline_desc,
            stepsRes = listOf(
                R.string.fitness_ex_chest_incline_step_1,
                R.string.fitness_ex_chest_incline_step_2,
                R.string.fitness_ex_chest_incline_step_3,
                R.string.fitness_ex_chest_incline_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_chest_incline_tip_1,
                R.string.fitness_ex_chest_incline_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_chest_incline_mistake_1,
                R.string.fitness_ex_chest_incline_mistake_2
            ),
            caloriesPerMinute = 7f
        ),
        Exercise(
            id = "chest_decline",
            nameRes = R.string.fitness_ex_chest_decline,
            muscleGroup = MuscleGroup.CHEST,
            difficulty = Difficulty.ADVANCED,
            equipment = Equipment.BENCH,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_chest_decline_desc,
            stepsRes = listOf(
                R.string.fitness_ex_chest_decline_step_1,
                R.string.fitness_ex_chest_decline_step_2,
                R.string.fitness_ex_chest_decline_step_3,
                R.string.fitness_ex_chest_decline_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_chest_decline_tip_1,
                R.string.fitness_ex_chest_decline_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_chest_decline_mistake_1,
                R.string.fitness_ex_chest_decline_mistake_2
            ),
            caloriesPerMinute = 9f
        ),
        Exercise(
            id = "chest_bench",
            nameRes = R.string.fitness_ex_chest_bench,
            muscleGroup = MuscleGroup.CHEST,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.BARBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_chest_bench_desc,
            stepsRes = listOf(
                R.string.fitness_ex_chest_bench_step_1,
                R.string.fitness_ex_chest_bench_step_2,
                R.string.fitness_ex_chest_bench_step_3,
                R.string.fitness_ex_chest_bench_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_chest_bench_tip_1,
                R.string.fitness_ex_chest_bench_tip_2,
                R.string.fitness_ex_chest_bench_tip_3
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_chest_bench_mistake_1,
                R.string.fitness_ex_chest_bench_mistake_2,
                R.string.fitness_ex_chest_bench_mistake_3
            ),
            caloriesPerMinute = 9f
        ),
        Exercise(
            id = "chest_fly",
            nameRes = R.string.fitness_ex_chest_fly,
            muscleGroup = MuscleGroup.CHEST,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_chest_fly_desc,
            stepsRes = listOf(
                R.string.fitness_ex_chest_fly_step_1,
                R.string.fitness_ex_chest_fly_step_2,
                R.string.fitness_ex_chest_fly_step_3,
                R.string.fitness_ex_chest_fly_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_chest_fly_tip_1,
                R.string.fitness_ex_chest_fly_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_chest_fly_mistake_1,
                R.string.fitness_ex_chest_fly_mistake_2
            ),
            caloriesPerMinute = 5f
        ),

        // ==== BACK ====
        Exercise(
            id = "back_pullups",
            nameRes = R.string.fitness_ex_back_pullups,
            muscleGroup = MuscleGroup.BACK,
            difficulty = Difficulty.ADVANCED,
            equipment = Equipment.PULLUP_BAR,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_back_pullups_desc,
            stepsRes = listOf(
                R.string.fitness_ex_back_pullups_step_1,
                R.string.fitness_ex_back_pullups_step_2,
                R.string.fitness_ex_back_pullups_step_3,
                R.string.fitness_ex_back_pullups_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_back_pullups_tip_1,
                R.string.fitness_ex_back_pullups_tip_2,
                R.string.fitness_ex_back_pullups_tip_3
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_back_pullups_mistake_1,
                R.string.fitness_ex_back_pullups_mistake_2,
                R.string.fitness_ex_back_pullups_mistake_3
            ),
            caloriesPerMinute = 10f
        ),
        Exercise(
            id = "back_chinups",
            nameRes = R.string.fitness_ex_back_chinups,
            muscleGroup = MuscleGroup.BACK,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.PULLUP_BAR,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_back_chinups_desc,
            stepsRes = listOf(
                R.string.fitness_ex_back_chinups_step_1,
                R.string.fitness_ex_back_chinups_step_2,
                R.string.fitness_ex_back_chinups_step_3,
                R.string.fitness_ex_back_chinups_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_back_chinups_tip_1,
                R.string.fitness_ex_back_chinups_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_back_chinups_mistake_1,
                R.string.fitness_ex_back_chinups_mistake_2
            ),
            caloriesPerMinute = 9.5f
        ),
        Exercise(
            id = "back_inverted",
            nameRes = R.string.fitness_ex_back_inverted,
            muscleGroup = MuscleGroup.BACK,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.BENCH,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_back_inverted_desc,
            stepsRes = listOf(
                R.string.fitness_ex_back_inverted_step_1,
                R.string.fitness_ex_back_inverted_step_2,
                R.string.fitness_ex_back_inverted_step_3,
                R.string.fitness_ex_back_inverted_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_back_inverted_tip_1,
                R.string.fitness_ex_back_inverted_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_back_inverted_mistake_1,
                R.string.fitness_ex_back_inverted_mistake_2
            ),
            caloriesPerMinute = 7f
        ),
        Exercise(
            id = "back_barrow",
            nameRes = R.string.fitness_ex_back_barrow,
            muscleGroup = MuscleGroup.BACK,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.BARBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_back_barrow_desc,
            stepsRes = listOf(
                R.string.fitness_ex_back_barrow_step_1,
                R.string.fitness_ex_back_barrow_step_2,
                R.string.fitness_ex_back_barrow_step_3,
                R.string.fitness_ex_back_barrow_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_back_barrow_tip_1,
                R.string.fitness_ex_back_barrow_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_back_barrow_mistake_1,
                R.string.fitness_ex_back_barrow_mistake_2,
                R.string.fitness_ex_back_barrow_mistake_3
            ),
            caloriesPerMinute = 8.5f
        ),
        Exercise(
            id = "back_dumbrow",
            nameRes = R.string.fitness_ex_back_dumbrow,
            muscleGroup = MuscleGroup.BACK,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_back_dumbrow_desc,
            stepsRes = listOf(
                R.string.fitness_ex_back_dumbrow_step_1,
                R.string.fitness_ex_back_dumbrow_step_2,
                R.string.fitness_ex_back_dumbrow_step_3,
                R.string.fitness_ex_back_dumbrow_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_back_dumbrow_tip_1,
                R.string.fitness_ex_back_dumbrow_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_back_dumbrow_mistake_1,
                R.string.fitness_ex_back_dumbrow_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "back_superman",
            nameRes = R.string.fitness_ex_back_superman,
            muscleGroup = MuscleGroup.BACK,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_back_superman_desc,
            stepsRes = listOf(
                R.string.fitness_ex_back_superman_step_1,
                R.string.fitness_ex_back_superman_step_2,
                R.string.fitness_ex_back_superman_step_3,
                R.string.fitness_ex_back_superman_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_back_superman_tip_1,
                R.string.fitness_ex_back_superman_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_back_superman_mistake_1,
                R.string.fitness_ex_back_superman_mistake_2
            ),
            caloriesPerMinute = 4f
        ),
        Exercise(
            id = "back_goodmorning",
            nameRes = R.string.fitness_ex_back_goodmorning,
            muscleGroup = MuscleGroup.BACK,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.BARBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_back_goodmorning_desc,
            stepsRes = listOf(
                R.string.fitness_ex_back_goodmorning_step_1,
                R.string.fitness_ex_back_goodmorning_step_2,
                R.string.fitness_ex_back_goodmorning_step_3,
                R.string.fitness_ex_back_goodmorning_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_back_goodmorning_tip_1,
                R.string.fitness_ex_back_goodmorning_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_back_goodmorning_mistake_1,
                R.string.fitness_ex_back_goodmorning_mistake_2
            ),
            caloriesPerMinute = 7f
        ),

        // ==== LEGS ====
        Exercise(
            id = "legs_squat",
            nameRes = R.string.fitness_ex_legs_squat,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_legs_squat_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_squat_step_1,
                R.string.fitness_ex_legs_squat_step_2,
                R.string.fitness_ex_legs_squat_step_3,
                R.string.fitness_ex_legs_squat_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_squat_tip_1,
                R.string.fitness_ex_legs_squat_tip_2,
                R.string.fitness_ex_legs_squat_tip_3
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_squat_mistake_1,
                R.string.fitness_ex_legs_squat_mistake_2,
                R.string.fitness_ex_legs_squat_mistake_3
            ),
            caloriesPerMinute = 9f
        ),
        Exercise(
            id = "legs_goblet",
            nameRes = R.string.fitness_ex_legs_goblet,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_legs_goblet_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_goblet_step_1,
                R.string.fitness_ex_legs_goblet_step_2,
                R.string.fitness_ex_legs_goblet_step_3,
                R.string.fitness_ex_legs_goblet_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_goblet_tip_1,
                R.string.fitness_ex_legs_goblet_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_goblet_mistake_1,
                R.string.fitness_ex_legs_goblet_mistake_2
            ),
            caloriesPerMinute = 9.5f
        ),
        Exercise(
            id = "legs_reverselunge",
            nameRes = R.string.fitness_ex_legs_reverselunge,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_legs_reverselunge_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_reverselunge_step_1,
                R.string.fitness_ex_legs_reverselunge_step_2,
                R.string.fitness_ex_legs_reverselunge_step_3,
                R.string.fitness_ex_legs_reverselunge_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_reverselunge_tip_1,
                R.string.fitness_ex_legs_reverselunge_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_reverselunge_mistake_1,
                R.string.fitness_ex_legs_reverselunge_mistake_2
            ),
            caloriesPerMinute = 8.5f
        ),
        Exercise(
            id = "legs_walkinglunge",
            nameRes = R.string.fitness_ex_legs_walkinglunge,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_legs_walkinglunge_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_walkinglunge_step_1,
                R.string.fitness_ex_legs_walkinglunge_step_2,
                R.string.fitness_ex_legs_walkinglunge_step_3,
                R.string.fitness_ex_legs_walkinglunge_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_walkinglunge_tip_1,
                R.string.fitness_ex_legs_walkinglunge_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_walkinglunge_mistake_1,
                R.string.fitness_ex_legs_walkinglunge_mistake_2
            ),
            caloriesPerMinute = 10f
        ),
        Exercise(
            id = "legs_bulgarian",
            nameRes = R.string.fitness_ex_legs_bulgarian,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.ADVANCED,
            equipment = Equipment.BENCH,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_legs_bulgarian_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_bulgarian_step_1,
                R.string.fitness_ex_legs_bulgarian_step_2,
                R.string.fitness_ex_legs_bulgarian_step_3,
                R.string.fitness_ex_legs_bulgarian_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_bulgarian_tip_1,
                R.string.fitness_ex_legs_bulgarian_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_bulgarian_mistake_1,
                R.string.fitness_ex_legs_bulgarian_mistake_2
            ),
            caloriesPerMinute = 9f
        ),
        Exercise(
            id = "legs_wallsit",
            nameRes = R.string.fitness_ex_legs_wallsit,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_legs_wallsit_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_wallsit_step_1,
                R.string.fitness_ex_legs_wallsit_step_2,
                R.string.fitness_ex_legs_wallsit_step_3,
                R.string.fitness_ex_legs_wallsit_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_wallsit_tip_1,
                R.string.fitness_ex_legs_wallsit_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_wallsit_mistake_1,
                R.string.fitness_ex_legs_wallsit_mistake_2
            ),
            caloriesPerMinute = 6f
        ),
        Exercise(
            id = "legs_calfraise",
            nameRes = R.string.fitness_ex_legs_calfraise,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_legs_calfraise_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_calfraise_step_1,
                R.string.fitness_ex_legs_calfraise_step_2,
                R.string.fitness_ex_legs_calfraise_step_3,
                R.string.fitness_ex_legs_calfraise_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_calfraise_tip_1,
                R.string.fitness_ex_legs_calfraise_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_calfraise_mistake_1,
                R.string.fitness_ex_legs_calfraise_mistake_2
            ),
            caloriesPerMinute = 4.5f
        ),
        Exercise(
            id = "legs_glutebridge",
            nameRes = R.string.fitness_ex_legs_glutebridge,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_legs_glutebridge_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_glutebridge_step_1,
                R.string.fitness_ex_legs_glutebridge_step_2,
                R.string.fitness_ex_legs_glutebridge_step_3,
                R.string.fitness_ex_legs_glutebridge_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_glutebridge_tip_1,
                R.string.fitness_ex_legs_glutebridge_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_glutebridge_mistake_1,
                R.string.fitness_ex_legs_glutebridge_mistake_2
            ),
            caloriesPerMinute = 5.5f
        ),
        Exercise(
            id = "legs_romaniandl",
            nameRes = R.string.fitness_ex_legs_romaniandl,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_legs_romaniandl_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_romaniandl_step_1,
                R.string.fitness_ex_legs_romaniandl_step_2,
                R.string.fitness_ex_legs_romaniandl_step_3,
                R.string.fitness_ex_legs_romaniandl_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_romaniandl_tip_1,
                R.string.fitness_ex_legs_romaniandl_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_romaniandl_mistake_1,
                R.string.fitness_ex_legs_romaniandl_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "legs_deadlift",
            nameRes = R.string.fitness_ex_legs_deadlift,
            muscleGroup = MuscleGroup.LEGS,
            difficulty = Difficulty.ADVANCED,
            equipment = Equipment.BARBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_legs_deadlift_desc,
            stepsRes = listOf(
                R.string.fitness_ex_legs_deadlift_step_1,
                R.string.fitness_ex_legs_deadlift_step_2,
                R.string.fitness_ex_legs_deadlift_step_3,
                R.string.fitness_ex_legs_deadlift_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_legs_deadlift_tip_1,
                R.string.fitness_ex_legs_deadlift_tip_2,
                R.string.fitness_ex_legs_deadlift_tip_3
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_legs_deadlift_mistake_1,
                R.string.fitness_ex_legs_deadlift_mistake_2,
                R.string.fitness_ex_legs_deadlift_mistake_3
            ),
            caloriesPerMinute = 12f
        ),

        // ==== CORE ====
        Exercise(
            id = "core_plank",
            nameRes = R.string.fitness_ex_core_plank,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_core_plank_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_plank_step_1,
                R.string.fitness_ex_core_plank_step_2,
                R.string.fitness_ex_core_plank_step_3,
                R.string.fitness_ex_core_plank_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_plank_tip_1,
                R.string.fitness_ex_core_plank_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_plank_mistake_1,
                R.string.fitness_ex_core_plank_mistake_2
            ),
            caloriesPerMinute = 4f
        ),
        Exercise(
            id = "core_sideplank",
            nameRes = R.string.fitness_ex_core_sideplank,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_core_sideplank_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_sideplank_step_1,
                R.string.fitness_ex_core_sideplank_step_2,
                R.string.fitness_ex_core_sideplank_step_3,
                R.string.fitness_ex_core_sideplank_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_sideplank_tip_1,
                R.string.fitness_ex_core_sideplank_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_sideplank_mistake_1,
                R.string.fitness_ex_core_sideplank_mistake_2
            ),
            caloriesPerMinute = 4.5f
        ),
        Exercise(
            id = "core_crunch",
            nameRes = R.string.fitness_ex_core_crunch,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_core_crunch_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_crunch_step_1,
                R.string.fitness_ex_core_crunch_step_2,
                R.string.fitness_ex_core_crunch_step_3,
                R.string.fitness_ex_core_crunch_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_crunch_tip_1,
                R.string.fitness_ex_core_crunch_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_crunch_mistake_1,
                R.string.fitness_ex_core_crunch_mistake_2
            ),
            caloriesPerMinute = 4.5f
        ),
        Exercise(
            id = "core_russian",
            nameRes = R.string.fitness_ex_core_russian,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_core_russian_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_russian_step_1,
                R.string.fitness_ex_core_russian_step_2,
                R.string.fitness_ex_core_russian_step_3,
                R.string.fitness_ex_core_russian_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_russian_tip_1,
                R.string.fitness_ex_core_russian_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_russian_mistake_1,
                R.string.fitness_ex_core_russian_mistake_2
            ),
            caloriesPerMinute = 6f
        ),
        Exercise(
            id = "core_legraise",
            nameRes = R.string.fitness_ex_core_legraise,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_core_legraise_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_legraise_step_1,
                R.string.fitness_ex_core_legraise_step_2,
                R.string.fitness_ex_core_legraise_step_3,
                R.string.fitness_ex_core_legraise_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_legraise_tip_1,
                R.string.fitness_ex_core_legraise_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_legraise_mistake_1,
                R.string.fitness_ex_core_legraise_mistake_2
            ),
            caloriesPerMinute = 5.5f
        ),
        Exercise(
            id = "core_bicycle",
            nameRes = R.string.fitness_ex_core_bicycle,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_core_bicycle_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_bicycle_step_1,
                R.string.fitness_ex_core_bicycle_step_2,
                R.string.fitness_ex_core_bicycle_step_3,
                R.string.fitness_ex_core_bicycle_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_bicycle_tip_1,
                R.string.fitness_ex_core_bicycle_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_bicycle_mistake_1,
                R.string.fitness_ex_core_bicycle_mistake_2
            ),
            caloriesPerMinute = 6f
        ),
        Exercise(
            id = "core_mountain",
            nameRes = R.string.fitness_ex_core_mountain,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_core_mountain_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_mountain_step_1,
                R.string.fitness_ex_core_mountain_step_2,
                R.string.fitness_ex_core_mountain_step_3,
                R.string.fitness_ex_core_mountain_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_mountain_tip_1,
                R.string.fitness_ex_core_mountain_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_mountain_mistake_1,
                R.string.fitness_ex_core_mountain_mistake_2
            ),
            caloriesPerMinute = 9f
        ),
        Exercise(
            id = "core_flutter",
            nameRes = R.string.fitness_ex_core_flutter,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_core_flutter_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_flutter_step_1,
                R.string.fitness_ex_core_flutter_step_2,
                R.string.fitness_ex_core_flutter_step_3,
                R.string.fitness_ex_core_flutter_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_flutter_tip_1,
                R.string.fitness_ex_core_flutter_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_flutter_mistake_1,
                R.string.fitness_ex_core_flutter_mistake_2
            ),
            caloriesPerMinute = 5f
        ),
        Exercise(
            id = "core_hollow",
            nameRes = R.string.fitness_ex_core_hollow,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.ADVANCED,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_core_hollow_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_hollow_step_1,
                R.string.fitness_ex_core_hollow_step_2,
                R.string.fitness_ex_core_hollow_step_3,
                R.string.fitness_ex_core_hollow_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_hollow_tip_1,
                R.string.fitness_ex_core_hollow_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_hollow_mistake_1,
                R.string.fitness_ex_core_hollow_mistake_2
            ),
            caloriesPerMinute = 4.5f
        ),
        Exercise(
            id = "core_birddog",
            nameRes = R.string.fitness_ex_core_birddog,
            muscleGroup = MuscleGroup.CORE,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_core_birddog_desc,
            stepsRes = listOf(
                R.string.fitness_ex_core_birddog_step_1,
                R.string.fitness_ex_core_birddog_step_2,
                R.string.fitness_ex_core_birddog_step_3,
                R.string.fitness_ex_core_birddog_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_core_birddog_tip_1,
                R.string.fitness_ex_core_birddog_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_core_birddog_mistake_1,
                R.string.fitness_ex_core_birddog_mistake_2
            ),
            caloriesPerMinute = 3.5f
        ),

        // ==== SHOULDERS ====
        Exercise(
            id = "shoulders_ohp",
            nameRes = R.string.fitness_ex_shoulders_ohp,
            muscleGroup = MuscleGroup.SHOULDERS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.BARBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_shoulders_ohp_desc,
            stepsRes = listOf(
                R.string.fitness_ex_shoulders_ohp_step_1,
                R.string.fitness_ex_shoulders_ohp_step_2,
                R.string.fitness_ex_shoulders_ohp_step_3,
                R.string.fitness_ex_shoulders_ohp_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_shoulders_ohp_tip_1,
                R.string.fitness_ex_shoulders_ohp_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_shoulders_ohp_mistake_1,
                R.string.fitness_ex_shoulders_ohp_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "shoulders_pikepush",
            nameRes = R.string.fitness_ex_shoulders_pikepush,
            muscleGroup = MuscleGroup.SHOULDERS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_shoulders_pikepush_desc,
            stepsRes = listOf(
                R.string.fitness_ex_shoulders_pikepush_step_1,
                R.string.fitness_ex_shoulders_pikepush_step_2,
                R.string.fitness_ex_shoulders_pikepush_step_3,
                R.string.fitness_ex_shoulders_pikepush_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_shoulders_pikepush_tip_1,
                R.string.fitness_ex_shoulders_pikepush_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_shoulders_pikepush_mistake_1,
                R.string.fitness_ex_shoulders_pikepush_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "shoulders_lateral",
            nameRes = R.string.fitness_ex_shoulders_lateral,
            muscleGroup = MuscleGroup.SHOULDERS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_shoulders_lateral_desc,
            stepsRes = listOf(
                R.string.fitness_ex_shoulders_lateral_step_1,
                R.string.fitness_ex_shoulders_lateral_step_2,
                R.string.fitness_ex_shoulders_lateral_step_3,
                R.string.fitness_ex_shoulders_lateral_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_shoulders_lateral_tip_1,
                R.string.fitness_ex_shoulders_lateral_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_shoulders_lateral_mistake_1,
                R.string.fitness_ex_shoulders_lateral_mistake_2
            ),
            caloriesPerMinute = 5f
        ),
        Exercise(
            id = "shoulders_frontraise",
            nameRes = R.string.fitness_ex_shoulders_frontraise,
            muscleGroup = MuscleGroup.SHOULDERS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_shoulders_frontraise_desc,
            stepsRes = listOf(
                R.string.fitness_ex_shoulders_frontraise_step_1,
                R.string.fitness_ex_shoulders_frontraise_step_2,
                R.string.fitness_ex_shoulders_frontraise_step_3,
                R.string.fitness_ex_shoulders_frontraise_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_shoulders_frontraise_tip_1,
                R.string.fitness_ex_shoulders_frontraise_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_shoulders_frontraise_mistake_1,
                R.string.fitness_ex_shoulders_frontraise_mistake_2
            ),
            caloriesPerMinute = 5f
        ),
        Exercise(
            id = "shoulders_arnold",
            nameRes = R.string.fitness_ex_shoulders_arnold,
            muscleGroup = MuscleGroup.SHOULDERS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_shoulders_arnold_desc,
            stepsRes = listOf(
                R.string.fitness_ex_shoulders_arnold_step_1,
                R.string.fitness_ex_shoulders_arnold_step_2,
                R.string.fitness_ex_shoulders_arnold_step_3,
                R.string.fitness_ex_shoulders_arnold_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_shoulders_arnold_tip_1,
                R.string.fitness_ex_shoulders_arnold_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_shoulders_arnold_mistake_1,
                R.string.fitness_ex_shoulders_arnold_mistake_2
            ),
            caloriesPerMinute = 7f
        ),
        Exercise(
            id = "shoulders_facepull",
            nameRes = R.string.fitness_ex_shoulders_facepull,
            muscleGroup = MuscleGroup.SHOULDERS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.RESISTANCE_BAND,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_shoulders_facepull_desc,
            stepsRes = listOf(
                R.string.fitness_ex_shoulders_facepull_step_1,
                R.string.fitness_ex_shoulders_facepull_step_2,
                R.string.fitness_ex_shoulders_facepull_step_3,
                R.string.fitness_ex_shoulders_facepull_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_shoulders_facepull_tip_1,
                R.string.fitness_ex_shoulders_facepull_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_shoulders_facepull_mistake_1,
                R.string.fitness_ex_shoulders_facepull_mistake_2
            ),
            caloriesPerMinute = 5f
        ),

        // ==== ARMS ====
        Exercise(
            id = "arms_bicepcurl",
            nameRes = R.string.fitness_ex_arms_bicepcurl,
            muscleGroup = MuscleGroup.ARMS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_arms_bicepcurl_desc,
            stepsRes = listOf(
                R.string.fitness_ex_arms_bicepcurl_step_1,
                R.string.fitness_ex_arms_bicepcurl_step_2,
                R.string.fitness_ex_arms_bicepcurl_step_3,
                R.string.fitness_ex_arms_bicepcurl_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_arms_bicepcurl_tip_1,
                R.string.fitness_ex_arms_bicepcurl_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_arms_bicepcurl_mistake_1,
                R.string.fitness_ex_arms_bicepcurl_mistake_2
            ),
            caloriesPerMinute = 5f
        ),
        Exercise(
            id = "arms_hammercurl",
            nameRes = R.string.fitness_ex_arms_hammercurl,
            muscleGroup = MuscleGroup.ARMS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_arms_hammercurl_desc,
            stepsRes = listOf(
                R.string.fitness_ex_arms_hammercurl_step_1,
                R.string.fitness_ex_arms_hammercurl_step_2,
                R.string.fitness_ex_arms_hammercurl_step_3,
                R.string.fitness_ex_arms_hammercurl_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_arms_hammercurl_tip_1,
                R.string.fitness_ex_arms_hammercurl_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_arms_hammercurl_mistake_1,
                R.string.fitness_ex_arms_hammercurl_mistake_2
            ),
            caloriesPerMinute = 5.5f
        ),
        Exercise(
            id = "arms_tricepdip",
            nameRes = R.string.fitness_ex_arms_tricepdip,
            muscleGroup = MuscleGroup.ARMS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.BENCH,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_arms_tricepdip_desc,
            stepsRes = listOf(
                R.string.fitness_ex_arms_tricepdip_step_1,
                R.string.fitness_ex_arms_tricepdip_step_2,
                R.string.fitness_ex_arms_tricepdip_step_3,
                R.string.fitness_ex_arms_tricepdip_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_arms_tricepdip_tip_1,
                R.string.fitness_ex_arms_tricepdip_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_arms_tricepdip_mistake_1,
                R.string.fitness_ex_arms_tricepdip_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "arms_cgpushup",
            nameRes = R.string.fitness_ex_arms_cgpushup,
            muscleGroup = MuscleGroup.ARMS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_arms_cgpushup_desc,
            stepsRes = listOf(
                R.string.fitness_ex_arms_cgpushup_step_1,
                R.string.fitness_ex_arms_cgpushup_step_2,
                R.string.fitness_ex_arms_cgpushup_step_3,
                R.string.fitness_ex_arms_cgpushup_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_arms_cgpushup_tip_1,
                R.string.fitness_ex_arms_cgpushup_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_arms_cgpushup_mistake_1,
                R.string.fitness_ex_arms_cgpushup_mistake_2
            ),
            caloriesPerMinute = 8f
        ),
        Exercise(
            id = "arms_tricepkickback",
            nameRes = R.string.fitness_ex_arms_tricepkickback,
            muscleGroup = MuscleGroup.ARMS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_arms_tricepkickback_desc,
            stepsRes = listOf(
                R.string.fitness_ex_arms_tricepkickback_step_1,
                R.string.fitness_ex_arms_tricepkickback_step_2,
                R.string.fitness_ex_arms_tricepkickback_step_3,
                R.string.fitness_ex_arms_tricepkickback_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_arms_tricepkickback_tip_1,
                R.string.fitness_ex_arms_tricepkickback_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_arms_tricepkickback_mistake_1,
                R.string.fitness_ex_arms_tricepkickback_mistake_2
            ),
            caloriesPerMinute = 4f
        ),
        Exercise(
            id = "arms_concentration",
            nameRes = R.string.fitness_ex_arms_concentration,
            muscleGroup = MuscleGroup.ARMS,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_arms_concentration_desc,
            stepsRes = listOf(
                R.string.fitness_ex_arms_concentration_step_1,
                R.string.fitness_ex_arms_concentration_step_2,
                R.string.fitness_ex_arms_concentration_step_3,
                R.string.fitness_ex_arms_concentration_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_arms_concentration_tip_1,
                R.string.fitness_ex_arms_concentration_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_arms_concentration_mistake_1,
                R.string.fitness_ex_arms_concentration_mistake_2
            ),
            caloriesPerMinute = 4.5f
        ),
        Exercise(
            id = "arms_skullcrusher",
            nameRes = R.string.fitness_ex_arms_skullcrusher,
            muscleGroup = MuscleGroup.ARMS,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.BARBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_arms_skullcrusher_desc,
            stepsRes = listOf(
                R.string.fitness_ex_arms_skullcrusher_step_1,
                R.string.fitness_ex_arms_skullcrusher_step_2,
                R.string.fitness_ex_arms_skullcrusher_step_3,
                R.string.fitness_ex_arms_skullcrusher_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_arms_skullcrusher_tip_1,
                R.string.fitness_ex_arms_skullcrusher_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_arms_skullcrusher_mistake_1,
                R.string.fitness_ex_arms_skullcrusher_mistake_2
            ),
            caloriesPerMinute = 5.5f
        ),

        // ==== CARDIO ====
        Exercise(
            id = "cardio_jjacks",
            nameRes = R.string.fitness_ex_cardio_jjacks,
            muscleGroup = MuscleGroup.CARDIO,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_cardio_jjacks_desc,
            stepsRes = listOf(
                R.string.fitness_ex_cardio_jjacks_step_1,
                R.string.fitness_ex_cardio_jjacks_step_2,
                R.string.fitness_ex_cardio_jjacks_step_3,
                R.string.fitness_ex_cardio_jjacks_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_cardio_jjacks_tip_1,
                R.string.fitness_ex_cardio_jjacks_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_cardio_jjacks_mistake_1,
                R.string.fitness_ex_cardio_jjacks_mistake_2
            ),
            caloriesPerMinute = 9f
        ),
        Exercise(
            id = "cardio_burpees",
            nameRes = R.string.fitness_ex_cardio_burpees,
            muscleGroup = MuscleGroup.CARDIO,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_cardio_burpees_desc,
            stepsRes = listOf(
                R.string.fitness_ex_cardio_burpees_step_1,
                R.string.fitness_ex_cardio_burpees_step_2,
                R.string.fitness_ex_cardio_burpees_step_3,
                R.string.fitness_ex_cardio_burpees_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_cardio_burpees_tip_1,
                R.string.fitness_ex_cardio_burpees_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_cardio_burpees_mistake_1,
                R.string.fitness_ex_cardio_burpees_mistake_2
            ),
            caloriesPerMinute = 12f
        ),
        Exercise(
            id = "cardio_highknees",
            nameRes = R.string.fitness_ex_cardio_highknees,
            muscleGroup = MuscleGroup.CARDIO,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_cardio_highknees_desc,
            stepsRes = listOf(
                R.string.fitness_ex_cardio_highknees_step_1,
                R.string.fitness_ex_cardio_highknees_step_2,
                R.string.fitness_ex_cardio_highknees_step_3,
                R.string.fitness_ex_cardio_highknees_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_cardio_highknees_tip_1,
                R.string.fitness_ex_cardio_highknees_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_cardio_highknees_mistake_1,
                R.string.fitness_ex_cardio_highknees_mistake_2
            ),
            caloriesPerMinute = 11f
        ),
        Exercise(
            id = "cardio_jumprope",
            nameRes = R.string.fitness_ex_cardio_jumprope,
            muscleGroup = MuscleGroup.CARDIO,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_cardio_jumprope_desc,
            stepsRes = listOf(
                R.string.fitness_ex_cardio_jumprope_step_1,
                R.string.fitness_ex_cardio_jumprope_step_2,
                R.string.fitness_ex_cardio_jumprope_step_3,
                R.string.fitness_ex_cardio_jumprope_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_cardio_jumprope_tip_1,
                R.string.fitness_ex_cardio_jumprope_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_cardio_jumprope_mistake_1,
                R.string.fitness_ex_cardio_jumprope_mistake_2
            ),
            caloriesPerMinute = 13f
        ),
        Exercise(
            id = "cardio_skipping",
            nameRes = R.string.fitness_ex_cardio_skipping,
            muscleGroup = MuscleGroup.CARDIO,
            difficulty = Difficulty.BEGINNER,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_cardio_skipping_desc,
            stepsRes = listOf(
                R.string.fitness_ex_cardio_skipping_step_1,
                R.string.fitness_ex_cardio_skipping_step_2,
                R.string.fitness_ex_cardio_skipping_step_3,
                R.string.fitness_ex_cardio_skipping_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_cardio_skipping_tip_1,
                R.string.fitness_ex_cardio_skipping_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_cardio_skipping_mistake_1,
                R.string.fitness_ex_cardio_skipping_mistake_2
            ),
            caloriesPerMinute = 7f
        ),

        // ==== FULL BODY ====
        Exercise(
            id = "fullbody_thruster",
            nameRes = R.string.fitness_ex_fullbody_thruster,
            muscleGroup = MuscleGroup.FULLBODY,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_fullbody_thruster_desc,
            stepsRes = listOf(
                R.string.fitness_ex_fullbody_thruster_step_1,
                R.string.fitness_ex_fullbody_thruster_step_2,
                R.string.fitness_ex_fullbody_thruster_step_3,
                R.string.fitness_ex_fullbody_thruster_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_fullbody_thruster_tip_1,
                R.string.fitness_ex_fullbody_thruster_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_fullbody_thruster_mistake_1,
                R.string.fitness_ex_fullbody_thruster_mistake_2
            ),
            caloriesPerMinute = 11f
        ),
        Exercise(
            id = "fullbody_bearcrawl",
            nameRes = R.string.fitness_ex_fullbody_bearcrawl,
            muscleGroup = MuscleGroup.FULLBODY,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.NONE,
            trackingType = TrackingType.TIME,
            descriptionRes = R.string.fitness_ex_fullbody_bearcrawl_desc,
            stepsRes = listOf(
                R.string.fitness_ex_fullbody_bearcrawl_step_1,
                R.string.fitness_ex_fullbody_bearcrawl_step_2,
                R.string.fitness_ex_fullbody_bearcrawl_step_3,
                R.string.fitness_ex_fullbody_bearcrawl_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_fullbody_bearcrawl_tip_1,
                R.string.fitness_ex_fullbody_bearcrawl_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_fullbody_bearcrawl_mistake_1,
                R.string.fitness_ex_fullbody_bearcrawl_mistake_2
            ),
            caloriesPerMinute = 9f
        ),
        Exercise(
            id = "fullbody_kbswing",
            nameRes = R.string.fitness_ex_fullbody_kbswing,
            muscleGroup = MuscleGroup.FULLBODY,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = Equipment.KETTLEBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_fullbody_kbswing_desc,
            stepsRes = listOf(
                R.string.fitness_ex_fullbody_kbswing_step_1,
                R.string.fitness_ex_fullbody_kbswing_step_2,
                R.string.fitness_ex_fullbody_kbswing_step_3,
                R.string.fitness_ex_fullbody_kbswing_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_fullbody_kbswing_tip_1,
                R.string.fitness_ex_fullbody_kbswing_tip_2,
                R.string.fitness_ex_fullbody_kbswing_tip_3
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_fullbody_kbswing_mistake_1,
                R.string.fitness_ex_fullbody_kbswing_mistake_2,
                R.string.fitness_ex_fullbody_kbswing_mistake_3
            ),
            caloriesPerMinute = 13f
        ),
        Exercise(
            id = "fullbody_burpeepushup",
            nameRes = R.string.fitness_ex_fullbody_burpeepushup,
            muscleGroup = MuscleGroup.FULLBODY,
            difficulty = Difficulty.ADVANCED,
            equipment = Equipment.NONE,
            trackingType = TrackingType.REPS,
            descriptionRes = R.string.fitness_ex_fullbody_burpeepushup_desc,
            stepsRes = listOf(
                R.string.fitness_ex_fullbody_burpeepushup_step_1,
                R.string.fitness_ex_fullbody_burpeepushup_step_2,
                R.string.fitness_ex_fullbody_burpeepushup_step_3,
                R.string.fitness_ex_fullbody_burpeepushup_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_fullbody_burpeepushup_tip_1,
                R.string.fitness_ex_fullbody_burpeepushup_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_fullbody_burpeepushup_mistake_1,
                R.string.fitness_ex_fullbody_burpeepushup_mistake_2
            ),
            caloriesPerMinute = 13f
        ),
        Exercise(
            id = "fullbody_manmaker",
            nameRes = R.string.fitness_ex_fullbody_manmaker,
            muscleGroup = MuscleGroup.FULLBODY,
            difficulty = Difficulty.ADVANCED,
            equipment = Equipment.DUMBBELL,
            trackingType = TrackingType.WEIGHT_REPS,
            descriptionRes = R.string.fitness_ex_fullbody_manmaker_desc,
            stepsRes = listOf(
                R.string.fitness_ex_fullbody_manmaker_step_1,
                R.string.fitness_ex_fullbody_manmaker_step_2,
                R.string.fitness_ex_fullbody_manmaker_step_3,
                R.string.fitness_ex_fullbody_manmaker_step_4
            ),
            tipsRes = listOf(
                R.string.fitness_ex_fullbody_manmaker_tip_1,
                R.string.fitness_ex_fullbody_manmaker_tip_2
            ),
            mistakesRes = listOf(
                R.string.fitness_ex_fullbody_manmaker_mistake_1,
                R.string.fitness_ex_fullbody_manmaker_mistake_2
            ),
            caloriesPerMinute = 14f
        )
    )

    fun findById(id: String): Exercise? = byId[id]

    fun groupByMuscle(): Map<MuscleGroup, List<Exercise>> =
        all().groupBy { it.muscleGroup }.toSortedMap(compareBy { it.ordinal })
}
