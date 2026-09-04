package com.tabslify.tabs.exploretab

import android.location.Location
import kotlin.math.abs
import kotlin.math.sqrt

data class BikeClassification(
    val mode: String,
    val score: Double,
    val movingSeconds: Double,
    val medianSpeedMps: Double,
    val p85SpeedMps: Double,
    val cruiseShare: Double,
    val fastShare: Double,
    val speedVariation: Double,
    val climbSpeedRatio: Double?,
    val climbSeconds: Double,
    val sampleCount: Int,
    val decided: Boolean
)

object ExploreBikeClassifier {

    const val MODE_BICYCLE = "ON_BICYCLE"
    const val MODE_EBIKE = "ON_EBIKE"

    private const val MIN_SAMPLE_GAP_S = 2.0
    private const val MAX_SAMPLE_GAP_S = 90.0
    private const val MAX_PLAUSIBLE_SPEED_MPS = 16.0
    private const val MOVING_SPEED_MPS = 1.5

    private const val MIN_SAMPLES = 12
    private const val MIN_MOVING_SECONDS = 240.0

    private const val CRUISE_LOW_MPS = 5.8
    private const val CRUISE_HIGH_MPS = 7.5
    private const val FAST_SPEED_MPS = 6.1

    private const val MEDIAN_BIKE_MPS = 4.6
    private const val MEDIAN_EBIKE_MPS = 6.1

    private const val CRUISE_SHARE_BIKE = 0.12
    private const val CRUISE_SHARE_EBIKE = 0.5

    private const val FAST_SHARE_BIKE = 0.15
    private const val FAST_SHARE_EBIKE = 0.6

    private const val VARIATION_BIKE = 0.46
    private const val VARIATION_EBIKE = 0.22

    private const val MIN_GRADE_DISTANCE_M = 15.0
    private const val MAX_VERTICAL_RATE_MPS = 3.0
    private const val CLIMB_GRADE = 0.02
    private const val FLAT_GRADE = 0.01
    private const val MIN_CLIMB_SECONDS = 45.0
    private const val MIN_FLAT_SECONDS = 60.0
    private const val CLIMB_RATIO_BIKE = 0.6
    private const val CLIMB_RATIO_EBIKE = 0.88

    private const val WEIGHT_MEDIAN = 0.35
    private const val WEIGHT_CRUISE = 0.25
    private const val WEIGHT_FAST = 0.1
    private const val WEIGHT_STEADY = 0.15
    private const val WEIGHT_CLIMB = 0.25

    private const val EBIKE_SCORE_THRESHOLD = 0.58

    private class Sample(val speed: Double, val seconds: Double, val grade: Double?)

    fun tuningSnapshot(): Map<String, Any> = mapOf(
        "minSamples" to MIN_SAMPLES,
        "minMovingSeconds" to MIN_MOVING_SECONDS,
        "minSampleGapSeconds" to MIN_SAMPLE_GAP_S,
        "maxSampleGapSeconds" to MAX_SAMPLE_GAP_S,
        "maxPlausibleSpeedMps" to MAX_PLAUSIBLE_SPEED_MPS,
        "movingSpeedMps" to MOVING_SPEED_MPS,
        "cruiseBandMps" to listOf(CRUISE_LOW_MPS, CRUISE_HIGH_MPS),
        "fastSpeedMps" to FAST_SPEED_MPS,
        "medianRampMps" to listOf(MEDIAN_BIKE_MPS, MEDIAN_EBIKE_MPS),
        "cruiseShareRamp" to listOf(CRUISE_SHARE_BIKE, CRUISE_SHARE_EBIKE),
        "fastShareRamp" to listOf(FAST_SHARE_BIKE, FAST_SHARE_EBIKE),
        "variationRamp" to listOf(VARIATION_BIKE, VARIATION_EBIKE),
        "climbGrade" to CLIMB_GRADE,
        "flatGrade" to FLAT_GRADE,
        "minClimbSeconds" to MIN_CLIMB_SECONDS,
        "minFlatSeconds" to MIN_FLAT_SECONDS,
        "climbRatioRamp" to listOf(CLIMB_RATIO_BIKE, CLIMB_RATIO_EBIKE),
        "weights" to mapOf(
            "median" to WEIGHT_MEDIAN,
            "cruiseShare" to WEIGHT_CRUISE,
            "fastShare" to WEIGHT_FAST,
            "steadiness" to WEIGHT_STEADY,
            "climbRatio" to WEIGHT_CLIMB
        ),
        "ebikeScoreThreshold" to EBIKE_SCORE_THRESHOLD
    )

    fun classify(points: List<RawPoint>): BikeClassification {
        val samples = buildSamples(points)
        val moving = samples.filter { it.speed >= MOVING_SPEED_MPS }
        val movingSeconds = moving.sumOf { it.seconds }

        if (moving.size < MIN_SAMPLES || movingSeconds < MIN_MOVING_SECONDS) {
            return BikeClassification(
                mode = MODE_BICYCLE,
                score = 0.0,
                movingSeconds = movingSeconds,
                medianSpeedMps = weightedPercentile(moving, 0.5),
                p85SpeedMps = weightedPercentile(moving, 0.85),
                cruiseShare = 0.0,
                fastShare = 0.0,
                speedVariation = 0.0,
                climbSpeedRatio = null,
                climbSeconds = 0.0,
                sampleCount = moving.size,
                decided = false
            )
        }

        val median = weightedPercentile(moving, 0.5)
        val p85 = weightedPercentile(moving, 0.85)
        val cruiseShare = moving.filter { it.speed in CRUISE_LOW_MPS..CRUISE_HIGH_MPS }.sumOf { it.seconds } / movingSeconds
        val fastShare = moving.filter { it.speed >= FAST_SPEED_MPS }.sumOf { it.seconds } / movingSeconds
        val mean = moving.sumOf { it.speed * it.seconds } / movingSeconds
        val variance = moving.sumOf { (it.speed - mean) * (it.speed - mean) * it.seconds } / movingSeconds
        val variation = if (mean > 0.0) sqrt(variance) / mean else 0.0

        val climbing = moving.filter { it.grade != null && it.grade >= CLIMB_GRADE }
        val flat = moving.filter { it.grade != null && abs(it.grade) < FLAT_GRADE }
        val climbSeconds = climbing.sumOf { it.seconds }
        val flatSeconds = flat.sumOf { it.seconds }
        val climbRatio = if (climbSeconds >= MIN_CLIMB_SECONDS && flatSeconds >= MIN_FLAT_SECONDS) {
            val climbSpeed = climbing.sumOf { it.speed * it.seconds } / climbSeconds
            val flatSpeed = flat.sumOf { it.speed * it.seconds } / flatSeconds
            if (flatSpeed > 0.0) climbSpeed / flatSpeed else null
        } else null

        var weighted = 0.0
        var totalWeight = 0.0

        weighted += WEIGHT_MEDIAN * ramp(median, MEDIAN_BIKE_MPS, MEDIAN_EBIKE_MPS)
        totalWeight += WEIGHT_MEDIAN

        weighted += WEIGHT_CRUISE * ramp(cruiseShare, CRUISE_SHARE_BIKE, CRUISE_SHARE_EBIKE)
        totalWeight += WEIGHT_CRUISE

        weighted += WEIGHT_FAST * ramp(fastShare, FAST_SHARE_BIKE, FAST_SHARE_EBIKE)
        totalWeight += WEIGHT_FAST

        weighted += WEIGHT_STEADY * ramp(variation, VARIATION_BIKE, VARIATION_EBIKE)
        totalWeight += WEIGHT_STEADY

        if (climbRatio != null) {
            weighted += WEIGHT_CLIMB * ramp(climbRatio, CLIMB_RATIO_BIKE, CLIMB_RATIO_EBIKE)
            totalWeight += WEIGHT_CLIMB
        }

        val score = if (totalWeight > 0.0) weighted / totalWeight else 0.0

        return BikeClassification(
            mode = if (score >= EBIKE_SCORE_THRESHOLD) MODE_EBIKE else MODE_BICYCLE,
            score = score,
            movingSeconds = movingSeconds,
            medianSpeedMps = median,
            p85SpeedMps = p85,
            cruiseShare = cruiseShare,
            fastShare = fastShare,
            speedVariation = variation,
            climbSpeedRatio = climbRatio,
            climbSeconds = climbSeconds,
            sampleCount = moving.size,
            decided = true
        )
    }

    fun isBikeMode(mode: String): Boolean = mode == MODE_BICYCLE || mode == MODE_EBIKE

    private fun ramp(value: Double, low: Double, high: Double): Double {
        if (low == high) return 0.0
        return ((value - low) / (high - low)).coerceIn(0.0, 1.0)
    }

    private fun buildSamples(points: List<RawPoint>): List<Sample> {
        val samples = mutableListOf<Sample>()
        val distance = FloatArray(1)
        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val current = points[i]
            val seconds = (current.timestamp - previous.timestamp) / 1000.0
            if (seconds < MIN_SAMPLE_GAP_S || seconds > MAX_SAMPLE_GAP_S) continue

            Location.distanceBetween(previous.lat, previous.lon, current.lat, current.lon, distance)
            val meters = distance[0].toDouble()
            val implied = meters / seconds
            val doppler = current.speed?.toDouble()
            val speed = if (doppler != null && doppler > 0.0 && doppler <= MAX_PLAUSIBLE_SPEED_MPS) doppler else implied
            if (speed > MAX_PLAUSIBLE_SPEED_MPS) continue

            samples += Sample(speed, seconds, gradeBetween(previous, current, meters, seconds))
        }
        return samples
    }

    private fun gradeBetween(previous: RawPoint, current: RawPoint, meters: Double, seconds: Double): Double? {
        val from = previous.altitude ?: return null
        val to = current.altitude ?: return null
        if (meters < MIN_GRADE_DISTANCE_M) return null
        val rise = to - from
        if (abs(rise) / seconds > MAX_VERTICAL_RATE_MPS) return null
        return (rise / meters).coerceIn(-0.25, 0.25)
    }

    private fun weightedPercentile(samples: List<Sample>, percentile: Double): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sortedBy { it.speed }
        val target = sorted.sumOf { it.seconds } * percentile
        var accumulated = 0.0
        for (sample in sorted) {
            accumulated += sample.seconds
            if (accumulated >= target) return sample.speed
        }
        return sorted.last().speed
    }
}
