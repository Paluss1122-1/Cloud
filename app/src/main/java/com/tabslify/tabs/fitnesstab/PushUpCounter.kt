package com.tabslify.tabs.fitnesstab

import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

enum class PushUpPhase { UNKNOWN, UP, DOWN }

enum class PushUpFeedback { NONE, TOO_SHALLOW }

data class PushUpRep(
    val index: Int,
    val depthAngle: Float,
    val durationMs: Long
)

class PushUpCounter(
    private val upThreshold: Float = 148f,
    private val downThreshold: Float = 112f,
    private val shallowCeiling: Float = 136f,
    private val holdFrames: Int = 2,
    private val windowSize: Int = 5,
    private val minRepDurationMs: Long = 450L,
    private val maxRepDurationMs: Long = 15_000L
) {
    private val window = ArrayDeque<Float>()
    private var candidatePhase = PushUpPhase.UNKNOWN
    private var candidateFrames = 0
    private var repStartedAtMs = 0L
    private var deepestAngle = MAX_ANGLE
    private var minAngleSinceTop = MAX_ANGLE

    var phase = PushUpPhase.UNKNOWN
        private set

    var count = 0
        private set

    var smoothedAngle = MAX_ANGLE
        private set

    var feedback = PushUpFeedback.NONE
        private set

    fun reset() {
        window.clear()
        candidatePhase = PushUpPhase.UNKNOWN
        candidateFrames = 0
        repStartedAtMs = 0L
        deepestAngle = MAX_ANGLE
        minAngleSinceTop = MAX_ANGLE
        phase = PushUpPhase.UNKNOWN
        count = 0
        smoothedAngle = MAX_ANGLE
        feedback = PushUpFeedback.NONE
    }

    fun onPoseLost() {
        window.clear()
        candidatePhase = PushUpPhase.UNKNOWN
        candidateFrames = 0
        phase = PushUpPhase.UNKNOWN
        minAngleSinceTop = MAX_ANGLE
        feedback = PushUpFeedback.NONE
    }

    fun submit(elbowAngle: Float, timestampMs: Long): PushUpRep? {
        if (elbowAngle.isNaN()) return null

        window.addLast(elbowAngle)
        if (window.size > windowSize) window.removeFirst()
        val smoothed = window.sorted()[window.size / 2]
        smoothedAngle = smoothed

        if (phase == PushUpPhase.DOWN) deepestAngle = min(deepestAngle, smoothed)
        if (phase == PushUpPhase.UP) {
            minAngleSinceTop = min(minAngleSinceTop, smoothed)
            if (smoothed >= upThreshold && minAngleSinceTop in downThreshold..shallowCeiling) {
                feedback = PushUpFeedback.TOO_SHALLOW
                minAngleSinceTop = MAX_ANGLE
            }
        }

        val observed = when {
            smoothed >= upThreshold -> PushUpPhase.UP
            smoothed <= downThreshold -> PushUpPhase.DOWN
            else -> PushUpPhase.UNKNOWN
        }

        if (observed == PushUpPhase.UNKNOWN) {
            candidatePhase = PushUpPhase.UNKNOWN
            candidateFrames = 0
            return null
        }

        if (observed == candidatePhase) {
            candidateFrames++
        } else {
            candidatePhase = observed
            candidateFrames = 1
        }

        if (candidateFrames < holdFrames || observed == phase) return null

        val previous = phase
        phase = observed

        if (observed == PushUpPhase.DOWN) {
            repStartedAtMs = timestampMs
            deepestAngle = smoothed
            feedback = PushUpFeedback.NONE
            return null
        }

        minAngleSinceTop = MAX_ANGLE
        if (previous != PushUpPhase.DOWN) return null

        val duration = timestampMs - repStartedAtMs
        if (duration !in minRepDurationMs..maxRepDurationMs) return null

        count++
        feedback = PushUpFeedback.NONE
        return PushUpRep(count, deepestAngle, duration)
    }

    companion object {
        const val MAX_ANGLE = 180f
    }
}

class ArmExtensionCounter(
    private val holdFrames: Int = 2,
    private val windowSize: Int = 5,
    private val minRepDurationMs: Long = 450L,
    private val maxRepDurationMs: Long = 15_000L,
    private val minRangeRatio: Float = 0.25f
) {
    private val window = ArrayDeque<Float>()
    private var observedMin = Float.NaN
    private var observedMax = Float.NaN
    private var candidatePhase = PushUpPhase.UNKNOWN
    private var candidateFrames = 0
    private var repStartedAtMs = 0L
    private var deepestExtension = Float.NaN

    var phase = PushUpPhase.UNKNOWN
        private set

    var count = 0
        private set

    var calibrated = false
        private set

    fun reset() {
        window.clear()
        observedMin = Float.NaN
        observedMax = Float.NaN
        candidatePhase = PushUpPhase.UNKNOWN
        candidateFrames = 0
        repStartedAtMs = 0L
        deepestExtension = Float.NaN
        phase = PushUpPhase.UNKNOWN
        count = 0
        calibrated = false
    }

    fun onPoseLost() {
        window.clear()
        candidatePhase = PushUpPhase.UNKNOWN
        candidateFrames = 0
        phase = PushUpPhase.UNKNOWN
    }

    fun submit(extension: Float, timestampMs: Long): PushUpRep? {
        if (extension.isNaN() || extension <= 0f) return null

        window.addLast(extension)
        if (window.size > windowSize) window.removeFirst()
        val smoothed = window.sorted()[window.size / 2]

        observedMin = when {
            observedMin.isNaN() -> smoothed
            smoothed < observedMin -> smoothed
            else -> observedMin + (smoothed - observedMin) * DRIFT
        }
        observedMax = when {
            observedMax.isNaN() -> smoothed
            smoothed > observedMax -> smoothed
            else -> observedMax + (smoothed - observedMax) * DRIFT
        }

        val range = observedMax - observedMin
        calibrated = observedMax > 0f && range >= minRangeRatio * observedMax
        if (!calibrated) {
            candidatePhase = PushUpPhase.UNKNOWN
            candidateFrames = 0
            return null
        }

        val downLevel = observedMin + range * 0.35f
        val upLevel = observedMin + range * 0.65f

        val observed = when {
            smoothed >= upLevel -> PushUpPhase.UP
            smoothed <= downLevel -> PushUpPhase.DOWN
            else -> PushUpPhase.UNKNOWN
        }

        if (phase == PushUpPhase.DOWN && !deepestExtension.isNaN()) {
            deepestExtension = min(deepestExtension, smoothed)
        }

        if (observed == PushUpPhase.UNKNOWN) {
            candidatePhase = PushUpPhase.UNKNOWN
            candidateFrames = 0
            return null
        }

        if (observed == candidatePhase) {
            candidateFrames++
        } else {
            candidatePhase = observed
            candidateFrames = 1
        }

        if (candidateFrames < holdFrames || observed == phase) return null

        val previous = phase
        phase = observed

        if (observed == PushUpPhase.DOWN) {
            repStartedAtMs = timestampMs
            deepestExtension = smoothed
            return null
        }

        if (previous != PushUpPhase.DOWN) return null

        val duration = timestampMs - repStartedAtMs
        if (duration !in minRepDurationMs..maxRepDurationMs) return null

        count++
        return PushUpRep(count, deepestExtension, duration)
    }

    companion object {
        private const val DRIFT = 0.002f
    }
}

fun angleAt(
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    cx: Float, cy: Float, cz: Float
): Float {
    val v1x = ax - bx
    val v1y = ay - by
    val v1z = az - bz
    val v2x = cx - bx
    val v2y = cy - by
    val v2z = cz - bz

    val n1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
    val n2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
    if (n1 < 1e-6f || n2 < 1e-6f) return Float.NaN

    val cos = ((v1x * v2x + v1y * v2y + v1z * v2z) / (n1 * n2)).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cos).toDouble()).toFloat()
}
