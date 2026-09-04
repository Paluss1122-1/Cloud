package com.tabslify.tabs.fitnesstab

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabslify.R
import com.tabslify.tabs.fitnesstab.data.BIKE_MODE_AUTO
import com.tabslify.tabs.fitnesstab.data.BikeModeOverrides
import com.tabslify.tabs.fitnesstab.data.BikeRide
import com.tabslify.tabs.fitnesstab.data.BikeRideRepository
import com.tabslify.tabs.fitnesstab.data.BikeStats
import com.tabslify.tabs.fitnesstab.data.DayStats
import com.tabslify.tabs.fitnesstab.data.Exercise
import com.tabslify.tabs.fitnesstab.data.ExerciseRepository
import com.tabslify.tabs.fitnesstab.data.ExerciseSet
import com.tabslify.tabs.fitnesstab.data.ExploreActivityBridge
import com.tabslify.tabs.fitnesstab.data.FitnessStats
import com.tabslify.tabs.fitnesstab.data.FitnessStorage
import com.tabslify.tabs.fitnesstab.data.TrackingType
import com.tabslify.tabs.fitnesstab.data.WorkoutEntry
import com.tabslify.tabs.fitnesstab.data.WorkoutSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PREFS_NAME = "fitness_pushups"
private const val KEY_BEST = "best_reps"
private const val KEY_SEEN_HINT = "seen_pushup_hint"

enum class TrackingIssue { NONE, NO_BODY, ARMS_HIDDEN, CALIBRATING }

enum class FitnessScreen { DASHBOARD, EXERCISES, WORKOUT, BIKE, HISTORY }

enum class WorkoutMode { CAMERA_POSE, MANUAL }

enum class BikeFilter { ALL, BIKE, EBIKE }

class FitnessViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val counter = PushUpCounter()
    private val armCounter = ArmExtensionCounter()
    private var timerJob: Job? = null
    private var sessionStartedAtMs = 0L
    private var lostFrames = 0

    private val storage = FitnessStorage(application)

    var frame by mutableStateOf<PoseFrame?>(null)
        private set

    var reps by mutableIntStateOf(0)
        private set

    var phase by mutableStateOf(PushUpPhase.UNKNOWN)
        private set

    var elbowAngle by mutableIntStateOf(180)
        private set

    var poseDetected by mutableStateOf(false)
        private set

    var trackingIssue by mutableStateOf(TrackingIssue.NO_BODY)
        private set

    var isRunning by mutableStateOf(false)
        private set

    var elapsedMs by mutableLongStateOf(0L)
        private set

    var personalBest by mutableIntStateOf(prefs.getInt(KEY_BEST, 0))
        private set

    var isNewRecord by mutableStateOf(false)
        private set

    var formHintRes by mutableStateOf<Int?>(null)
        private set

    var lastRepAtMs by mutableLongStateOf(0L)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    var showSkeleton by mutableStateOf(true)

    var useFrontCamera by mutableStateOf(true)

    var showHint by mutableStateOf(!prefs.getBoolean(KEY_SEEN_HINT, false))
        private set

    var currentScreen by mutableStateOf(FitnessScreen.DASHBOARD)
        private set

    private val _stats = MutableStateFlow(FitnessStats())
    val stats: StateFlow<FitnessStats> = _stats.asStateFlow()

    private val _recentDays = MutableStateFlow<Map<String, DayStats>>(emptyMap())
    val recentDays: StateFlow<Map<String, DayStats>> = _recentDays.asStateFlow()

    private val _sessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val sessions: StateFlow<List<WorkoutSession>> = _sessions.asStateFlow()

    val goalMinutes: Int get() = storage.getDailyGoalMinutes()
    val goalCalories: Int get() = storage.getDailyGoalCalories()

    var userAgeYears by mutableIntStateOf(storage.getUserAgeYears())
        private set

    var userWeightKg by mutableStateOf(storage.getUserWeightKg())
        private set

    var profileDialogVisible by mutableStateOf(false)
        private set

    fun openProfileDialog() {
        profileDialogVisible = true
    }

    fun closeProfileDialog() {
        profileDialogVisible = false
    }

    fun updateProfile(ageYears: Int, weightKg: Float) {
        storage.setUserAgeYears(ageYears)
        storage.setUserWeightKg(weightKg)
        userAgeYears = ageYears
        userWeightKg = weightKg
        profileDialogVisible = false
    }

    private val _bikeRides = MutableStateFlow<List<BikeRide>>(emptyList())
    val bikeRides: StateFlow<List<BikeRide>> = _bikeRides.asStateFlow()

    private val _bikeStats = MutableStateFlow(BikeStats())
    val bikeStats: StateFlow<BikeStats> = _bikeStats.asStateFlow()

    var bikeLoading by mutableStateOf(false)
        private set

    var bikeRangeDays by mutableIntStateOf(DEFAULT_BIKE_RANGE_DAYS)
        private set

    var bikeFilter by mutableStateOf(BikeFilter.ALL)
        private set

    var bikeDefaultMode by mutableStateOf(BikeModeOverrides.defaultMode(application))
        private set

    var selectedRideId: String? by mutableStateOf(null)
        private set

    private var loadedBikeRangeDays = 0

    fun loadBikeRides(force: Boolean = false) {
        if (bikeLoading) return
        if (!force && loadedBikeRangeDays == bikeRangeDays) return
        bikeLoading = true
        viewModelScope.launch {
            val rides = runCatching {
                BikeRideRepository.ridesForLastDays(
                    getApplication<Application>(),
                    bikeRangeDays,
                    storage.calorieFactor()
                )
            }.getOrDefault(emptyList())
            _bikeRides.value = rides
            _bikeStats.value = BikeRideRepository.statsFor(rides)
            loadedBikeRangeDays = bikeRangeDays
            bikeLoading = false
        }
    }

    fun changeBikeRange(days: Int) {
        if (days == bikeRangeDays) return
        bikeRangeDays = days
        selectedRideId = null
        loadBikeRides(force = true)
    }

    fun changeBikeFilter(filter: BikeFilter) {
        bikeFilter = filter
    }

    fun openRide(rideId: String) {
        selectedRideId = rideId
    }

    fun closeRide() {
        selectedRideId = null
    }

    fun setRideMode(rideId: String, mode: String?) {
        BikeModeOverrides.setMode(getApplication<Application>(), rideId, mode)
        val factor = storage.calorieFactor()
        _bikeRides.value = _bikeRides.value.map { ride ->
            if (ride.rideId != rideId) {
                ride
            } else {
                val resolved = mode
                    ?: if (bikeDefaultMode == BIKE_MODE_AUTO) ride.autoMode else bikeDefaultMode
                ride.copy(
                    mode = resolved,
                    overridden = mode != null,
                    calories = BikeRideRepository.caloriesFor(
                        resolved,
                        ride.distanceKm,
                        ride.durationMs,
                        factor
                    )
                )
            }
        }
        _bikeStats.value = BikeRideRepository.statsFor(_bikeRides.value)
        refreshStats()
    }

    fun changeBikeDefaultMode(mode: String) {
        if (mode == bikeDefaultMode) return
        BikeModeOverrides.setDefaultMode(getApplication<Application>(), mode)
        bikeDefaultMode = mode
        loadBikeRides(force = true)
        refreshStats()
    }

    val exercises: List<Exercise> = ExerciseRepository.all()

    var selectedExerciseId: String? by mutableStateOf(null)
        private set

    var exercisesGroupFilter: com.tabslify.tabs.fitnesstab.data.MuscleGroup? by mutableStateOf(null)

    var exercisesDifficultyFilter: com.tabslify.tabs.fitnesstab.data.Difficulty? by mutableStateOf(null)

    var exercisesEquipmentFilter: com.tabslify.tabs.fitnesstab.data.Equipment? by mutableStateOf(null)

    var exercisesSearch: String by mutableStateOf("")

    val selectedExercise: Exercise?
        get() = selectedExerciseId?.let { id -> ExerciseRepository.findById(id) }

    // ---- MANUAL WORKOUT STATE ----
    var workoutMode by mutableStateOf(WorkoutMode.CAMERA_POSE)
        private set

    var workoutName by mutableStateOf("")

    var workoutStartedAtMs by mutableLongStateOf(0L)
        private set

    var workoutElapsedMs by mutableLongStateOf(0L)
        private set

    private var workoutTimerJob: Job? = null

    data class EditableWorkoutEntry(
        val entryId: String,
        val exerciseId: String,
        val sets: SnapshotStateList<ExerciseSet> = mutableStateListOf()
    )

    private val _editableEntries = mutableStateListOf<EditableWorkoutEntry>()
    val editableEntries: List<EditableWorkoutEntry> get() = _editableEntries

    var exercisePickerVisible by mutableStateOf(false)
        private set

    var restTimerSeconds by mutableIntStateOf(90)

    var restTimerRunning by mutableStateOf(false)
        private set

    var restTimerRemaining by mutableIntStateOf(90)

    private var restTimerJob: Job? = null

    fun changeWorkoutMode(mode: WorkoutMode) {
        workoutMode = mode
        if (mode == WorkoutMode.MANUAL && workoutStartedAtMs == 0L) {
            startManualWorkoutTimer()
        }
    }

    fun openExercisePicker() {
        exercisePickerVisible = true
    }

    fun closeExercisePicker() {
        exercisePickerVisible = false
    }

    fun addExerciseToWorkout(exerciseId: String) {
        val existing = _editableEntries.find { it.exerciseId == exerciseId }
        if (existing != null) {
            existing.sets.add(
                ExerciseSet(setNumber = existing.sets.size + 1)
            )
            return
        }
        val entryId = "entry_${System.currentTimeMillis()}_${exerciseId}"
        val entry = EditableWorkoutEntry(
            entryId = entryId,
            exerciseId = exerciseId,
            sets = mutableStateListOf(ExerciseSet(setNumber = 1))
        )
        _editableEntries.add(entry)
        if (workoutStartedAtMs == 0L) startManualWorkoutTimer()
    }

    fun removeWorkoutEntry(entryId: String) {
        val idx = _editableEntries.indexOfFirst { it.entryId == entryId }
        if (idx >= 0) _editableEntries.removeAt(idx)
    }

    fun addSetToEntry(entryId: String) {
        val entry = _editableEntries.find { it.entryId == entryId } ?: return
        entry.sets.add(ExerciseSet(setNumber = entry.sets.size + 1))
    }

    fun removeSetFromEntry(entryId: String, setIndex: Int) {
        val entry = _editableEntries.find { it.entryId == entryId } ?: return
        if (setIndex in entry.sets.indices) {
            entry.sets.removeAt(setIndex)
            entry.sets.forEachIndexed { i, s ->
                entry.sets[i] = s.copy(setNumber = i + 1)
            }
        }
    }

    fun updateSetInEntry(
        entryId: String,
        setIndex: Int,
        reps: Int? = null,
        weightKg: Float? = null,
        durationSeconds: Int? = null,
        completed: Boolean? = null
    ) {
        val entry = _editableEntries.find { it.entryId == entryId } ?: return
        if (setIndex !in entry.sets.indices) return
        val old = entry.sets[setIndex]
        entry.sets[setIndex] = old.copy(
            reps = reps ?: old.reps,
            weightKg = weightKg ?: old.weightKg,
            durationSeconds = durationSeconds ?: old.durationSeconds,
            completed = completed ?: old.completed
        )
    }

    private fun startWorkoutTicker() {
        workoutTimerJob?.cancel()
        workoutTimerJob = viewModelScope.launch {
            while (workoutStartedAtMs > 0L) {
                workoutElapsedMs = System.currentTimeMillis() - workoutStartedAtMs
                delay(500L)
            }
        }
    }

    private fun startManualWorkoutTimer() {
        workoutStartedAtMs = System.currentTimeMillis()
        startWorkoutTicker()
    }

    fun startRestTimer() {
        restTimerRemaining = restTimerSeconds
        restTimerRunning = true
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (restTimerRemaining > 0) {
                delay(1000L)
                restTimerRemaining--
            }
            restTimerRunning = false
        }
    }

    fun resetRestTimer() {
        restTimerJob?.cancel()
        restTimerRunning = false
        restTimerRemaining = restTimerSeconds
    }

    fun saveWorkoutSession() {
        if (_editableEntries.isEmpty()) return
        val now = System.currentTimeMillis()
        val start = if (workoutStartedAtMs > 0L) workoutStartedAtMs else now
        val entries = _editableEntries.map { e ->
            WorkoutEntry(
                entryId = e.entryId,
                exerciseId = e.exerciseId,
                sets = e.sets.toList(),
                startedAtMs = start,
                finishedAtMs = now
            )
        }

        var calories = 0f
        val minutes = ((now - start) / 60_000f).coerceAtLeast(1f)
        val exIds = _editableEntries.map { it.exerciseId }.distinct()
        val factor = storage.calorieFactor()
        for (exId in exIds) {
            val ex = ExerciseRepository.findById(exId)
            val perMinute = ex?.caloriesPerMinute ?: 5f
            calories += perMinute * factor * (minutes / exIds.size.coerceAtLeast(1))
        }

        val session = WorkoutSession(
            sessionId = "manual_${now}",
            dateStartMs = start,
            dateEndMs = now,
            entries = entries,
            totalCalories = calories,
            name = workoutName.trim()
        )
        storage.saveSession(session)
        discardWorkoutInternal()
        refreshStats()
        currentScreen = FitnessScreen.DASHBOARD
    }

    fun discardWorkout() {
        discardWorkoutInternal()
    }

    private fun discardWorkoutInternal() {
        _editableEntries.clear()
        workoutName = ""
        workoutStartedAtMs = 0L
        workoutElapsedMs = 0L
        workoutTimerJob?.cancel()
        workoutTimerJob = null
        restTimerJob?.cancel()
        restTimerJob = null
        restTimerRunning = false
        restTimerRemaining = restTimerSeconds
    }

    fun deleteSession(sessionId: String) {
        if (ExploreActivityBridge.isExploreSession(sessionId)) return
        storage.deleteSession(sessionId)
        refreshStats()
    }

    // ---- END MANUAL WORKOUT ----

    val repsPerMinute: Float
        get() = if (elapsedMs < 3_000L || reps == 0) 0f else reps * 60_000f / elapsedMs

    init {
        refreshStats()
    }

    fun switchTo(screen: FitnessScreen) {
        val wasWorkoutScreen = currentScreen == FitnessScreen.WORKOUT
        currentScreen = screen
        selectedExerciseId = null
        selectedRideId = null
        if (screen == FitnessScreen.BIKE) loadBikeRides()
        if (wasWorkoutScreen && screen != FitnessScreen.WORKOUT) {
            timerJob?.cancel()
            timerJob = null
            workoutTimerJob?.cancel()
            workoutTimerJob = null
        } else if (screen == FitnessScreen.WORKOUT) {
            if (isRunning && timerJob == null) {
                timerJob = viewModelScope.launch {
                    while (isRunning) {
                        elapsedMs = System.currentTimeMillis() - sessionStartedAtMs
                        delay(250L)
                    }
                }
            }
            if (workoutStartedAtMs > 0L && workoutTimerJob == null) {
                startWorkoutTicker()
            }
        }
    }

    fun openManualWorkout(startExerciseId: String? = null) {
        changeWorkoutMode(WorkoutMode.MANUAL)
        if (startExerciseId != null) addExerciseToWorkout(startExerciseId)
        currentScreen = FitnessScreen.WORKOUT
    }

    fun openCameraWorkout() {
        changeWorkoutMode(WorkoutMode.CAMERA_POSE)
        currentScreen = FitnessScreen.WORKOUT
    }

    fun openExerciseDetail(exerciseId: String) {
        selectedExerciseId = exerciseId
    }

    fun navigateBackFromDetail() {
        selectedExerciseId = null
    }

    fun refreshStats() {
        viewModelScope.launch {
            val exploreSessions = runCatching {
                ExploreActivityBridge.sessionsForLastDays(
                    getApplication<Application>(),
                    EXPLORE_HISTORY_DAYS,
                    storage.calorieFactor()
                )
            }.getOrDefault(emptyList())

            _stats.value = storage.getFitnessStats(exploreSessions)
            _recentDays.value = storage.loadLastNDays(7, exploreSessions)
            _sessions.value = storage.mergeWithStored(exploreSessions)
                .sortedByDescending { it.dateStartMs }
                .take(50)
        }
    }

    fun saveSessionFromPushUps() {
        if (reps <= 0 && elapsedMs < 10_000L) return
        val now = System.currentTimeMillis()
        val startMs = sessionStartedAtMs.takeIf { it > 0L } ?: (now - elapsedMs)
        val factor = storage.calorieFactor()
        val kcal = ((reps * 0.35f + (elapsedMs / 60_000f) * 6f) * factor).coerceAtLeast(0f)

        val entry = if (reps > 0) {
            listOf(
                WorkoutEntry(
                    entryId = "pushup_entry_${now}",
                    exerciseId = "chest_pushups",
                    sets = listOf(
                        ExerciseSet(
                            setNumber = 1,
                            reps = reps,
                            durationSeconds = (elapsedMs / 1000L).toInt(),
                            completed = true
                        )
                    ),
                    startedAtMs = startMs,
                    finishedAtMs = now
                )
            )
        } else {
            emptyList()
        }

        val session = WorkoutSession(
            sessionId = "pushup_${now}",
            dateStartMs = startMs,
            dateEndMs = now,
            entries = entry,
            totalCalories = kcal,
            name = ""
        )
        storage.saveSession(session)
        refreshStats()
    }

    fun dismissHint() {
        showHint = false
        prefs.edit { putBoolean(KEY_SEEN_HINT, true) }
    }

    fun toggleSession() {
        if (isRunning) stopSession() else startSession()
    }

    fun startSession() {
        counter.reset()
        armCounter.reset()
        lostFrames = 0
        lastRepAtMs = 0L
        reps = 0
        phase = PushUpPhase.UNKNOWN
        formHintRes = null
        isNewRecord = false
        elapsedMs = 0L
        sessionStartedAtMs = System.currentTimeMillis()
        isRunning = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isRunning) {
                elapsedMs = System.currentTimeMillis() - sessionStartedAtMs
                delay(250L)
            }
        }
    }

    fun stopSession() {
        val wasRunning = isRunning
        isRunning = false
        timerJob?.cancel()
        timerJob = null
        phase = PushUpPhase.UNKNOWN
        formHintRes = null
        if (reps > personalBest) {
            personalBest = reps
            prefs.edit { putInt(KEY_BEST, reps) }
            isNewRecord = true
        }
        if (wasRunning) saveSessionFromPushUps()
    }

    fun resetSession() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
        phase = PushUpPhase.UNKNOWN
        formHintRes = null
        counter.reset()
        armCounter.reset()
        lostFrames = 0
        lastRepAtMs = 0L
        reps = 0
        elapsedMs = 0L
        isNewRecord = false
        formHintRes = null
    }

    fun switchCamera() {
        useFrontCamera = !useFrontCamera
    }

    fun onPoseFrame(newFrame: PoseFrame) {
        frame = newFrame

        val trackable = PoseMetrics.isTrackable(newFrame)
        poseDetected = trackable

        val angle = if (trackable) PoseMetrics.elbowAngle(newFrame) else Float.NaN
        val extension = if (trackable) PoseMetrics.armExtension(newFrame) else Float.NaN
        val usable = !angle.isNaN() || !extension.isNaN()

        if (!usable) {
            lostFrames++
            if (lostFrames >= LOST_FRAME_GRACE) {
                counter.onPoseLost()
                armCounter.onPoseLost()
                phase = PushUpPhase.UNKNOWN
                trackingIssue = if (trackable) TrackingIssue.ARMS_HIDDEN else TrackingIssue.NO_BODY
            }
            return
        }

        lostFrames = 0
        val now = System.currentTimeMillis()

        if (!isRunning) {
            if (!angle.isNaN()) elbowAngle = angle.roundToInt()
            trackingIssue = TrackingIssue.NONE
            return
        }

        val angleRep = if (angle.isNaN()) null else counter.submit(angle, now)
        val extensionRep = if (extension.isNaN()) null else armCounter.submit(extension, now)

        if (!angle.isNaN()) elbowAngle = counter.smoothedAngle.roundToInt()
        phase = if (counter.phase == PushUpPhase.UNKNOWN) armCounter.phase else counter.phase

        if ((angleRep != null || extensionRep != null) && now - lastRepAtMs >= REP_REFRACTORY_MS) {
            reps++
            lastRepAtMs = now
        }

        trackingIssue = if (reps == 0 && counter.phase == PushUpPhase.UNKNOWN && !armCounter.calibrated) {
            TrackingIssue.CALIBRATING
        } else {
            TrackingIssue.NONE
        }

        formHintRes = resolveHint(newFrame)
    }

    private fun resolveHint(current: PoseFrame): Int? {
        if (PoseMetrics.hipReliable(current)) {
            val hip = PoseMetrics.hipAngle(current)
            if (!hip.isNaN() && hip < 152f) return R.string.fitness_form_huefte
        }
        if (counter.feedback == PushUpFeedback.TOO_SHALLOW) return R.string.fitness_form_tiefer
        return null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        workoutTimerJob?.cancel()
        restTimerJob?.cancel()
    }

    companion object {
        private const val LOST_FRAME_GRACE = 8
        private const val REP_REFRACTORY_MS = 700L
        private const val EXPLORE_HISTORY_DAYS = 30
        private const val DEFAULT_BIKE_RANGE_DAYS = 30
    }
}
