package com.tabslify.tabs.fitnesstab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.sqrt

data class PoseFrame(
    val landmarks: List<NormalizedLandmark>,
    val worldLandmarks: List<Landmark>,
    val imageWidth: Int,
    val imageHeight: Int
)

class PoseAnalyzer(
    context: Context,
    private val onFrame: (PoseFrame) -> Unit,
    private val onFailure: (Throwable) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var isFrontCamera: Boolean = true

    private var landmarker: PoseLandmarker? = null
    private var lastTimestampMs = 0L
    private var closed = false

    init {
        landmarker = createLandmarker(context, Delegate.GPU)
            ?: createLandmarker(context, Delegate.CPU)
        if (landmarker == null) onFailure(IllegalStateException("PoseLandmarker konnte nicht erstellt werden"))
    }

    private fun createLandmarker(context: Context, delegate: Delegate): PoseLandmarker? = runCatching {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, input -> publish(result, input) }
            .setErrorListener { error -> onFailure(error) }
            .build()

        PoseLandmarker.createFromOptions(context, options)
    }.getOrNull()

    private fun publish(result: PoseLandmarkerResult, input: MPImage) {
        if (closed) return
        onFrame(
            PoseFrame(
                landmarks = result.landmarks().firstOrNull().orEmpty(),
                worldLandmarks = result.worldLandmarks().firstOrNull().orEmpty(),
                imageWidth = input.width,
                imageHeight = input.height
            )
        )
    }

    override fun analyze(imageProxy: ImageProxy) {
        val detector = landmarker
        if (detector == null || closed) {
            imageProxy.close()
            return
        }

        var bitmap: Bitmap? = null
        try {
            val raw = imageProxy.toBitmap()
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) postScale(-1f, 1f, raw.width / 2f, raw.height / 2f)
            }
            bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } catch (t: Throwable) {
            onFailure(t)
        } finally {
            imageProxy.close()
        }

        val rotated = bitmap ?: return

        val timestamp = SystemClock.uptimeMillis().coerceAtLeast(lastTimestampMs + 1)
        lastTimestampMs = timestamp

        runCatching { detector.detectAsync(BitmapImageBuilder(rotated).build(), timestamp) }
            .onFailure { onFailure(it) }
    }

    fun close() {
        closed = true
        runCatching { landmarker?.close() }
        landmarker = null
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}

object PoseLandmarks {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    val CONNECTIONS = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_ELBOW,
        LEFT_ELBOW to LEFT_WRIST,
        RIGHT_SHOULDER to RIGHT_ELBOW,
        RIGHT_ELBOW to RIGHT_WRIST,
        LEFT_SHOULDER to LEFT_HIP,
        RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_HIP to RIGHT_HIP,
        LEFT_HIP to LEFT_KNEE,
        LEFT_KNEE to LEFT_ANKLE,
        RIGHT_HIP to RIGHT_KNEE,
        RIGHT_KNEE to RIGHT_ANKLE
    )

    val DRAWN_POINTS = listOf(
        LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST,
        LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
    )
}

object PoseMetrics {
    private const val MIN_VISIBILITY = 0.4f

    fun visibility(frame: PoseFrame, index: Int): Float {
        val landmark = frame.landmarks.getOrNull(index) ?: return 0f
        return landmark.visibility().orElse(0f)
    }

    fun isTrackable(frame: PoseFrame): Boolean {
        if (frame.landmarks.size < 33 || frame.worldLandmarks.size < 33) return false
        val armIndices = listOf(
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST,
            PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST
        )
        return armIndices.count { visibility(frame, it) >= MIN_VISIBILITY } >= 2
    }

    fun hipReliable(frame: PoseFrame): Boolean {
        val indices = listOf(
            PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP,
            PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE
        )
        return indices.all { visibility(frame, it) >= MIN_VISIBILITY }
    }

    fun armExtension(frame: PoseFrame): Float {
        val shoulder = midpoint(frame, PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER)
            ?: return Float.NaN
        val wrist = midpoint(frame, PoseLandmarks.LEFT_WRIST, PoseLandmarks.RIGHT_WRIST)
            ?: return Float.NaN
        val hip = midpoint(frame, PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP)
            ?: return Float.NaN

        if (frame.imageHeight <= 0) return Float.NaN
        val aspect = frame.imageWidth.toFloat() / frame.imageHeight.toFloat()

        val torso = distance(shoulder, hip, aspect)
        if (torso < 1e-4f) return Float.NaN

        return distance(shoulder, wrist, aspect) / torso
    }

    private fun midpoint(frame: PoseFrame, left: Int, right: Int): Pair<Float, Float>? {
        val leftVisible = visibility(frame, left) >= MIN_VISIBILITY
        val rightVisible = visibility(frame, right) >= MIN_VISIBILITY
        val a = frame.landmarks.getOrNull(left)
        val b = frame.landmarks.getOrNull(right)

        return when {
            leftVisible && rightVisible && a != null && b != null ->
                (a.x() + b.x()) / 2f to (a.y() + b.y()) / 2f

            leftVisible && a != null -> a.x() to a.y()
            rightVisible && b != null -> b.x() to b.y()
            else -> null
        }
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>, aspect: Float): Float {
        val dx = (a.first - b.first) * aspect
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    fun elbowAngle(frame: PoseFrame): Float {
        val left = sideAngle(
            frame,
            PoseLandmarks.LEFT_SHOULDER,
            PoseLandmarks.LEFT_ELBOW,
            PoseLandmarks.LEFT_WRIST
        )
        val right = sideAngle(
            frame,
            PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.RIGHT_ELBOW,
            PoseLandmarks.RIGHT_WRIST
        )
        return when {
            left.isNaN() && right.isNaN() -> Float.NaN
            left.isNaN() -> right
            right.isNaN() -> left
            else -> (left + right) / 2f
        }
    }

    fun hipAngle(frame: PoseFrame): Float {
        val left = sideAngle(
            frame,
            PoseLandmarks.LEFT_SHOULDER,
            PoseLandmarks.LEFT_HIP,
            PoseLandmarks.LEFT_KNEE
        )
        val right = sideAngle(
            frame,
            PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.RIGHT_HIP,
            PoseLandmarks.RIGHT_KNEE
        )
        return when {
            left.isNaN() && right.isNaN() -> Float.NaN
            left.isNaN() -> right
            right.isNaN() -> left
            else -> (left + right) / 2f
        }
    }

    private fun sideAngle(frame: PoseFrame, a: Int, b: Int, c: Int): Float {
        if (visibility(frame, a) < MIN_VISIBILITY ||
            visibility(frame, b) < MIN_VISIBILITY ||
            visibility(frame, c) < MIN_VISIBILITY
        ) return Float.NaN

        val pa = frame.worldLandmarks.getOrNull(a) ?: return Float.NaN
        val pb = frame.worldLandmarks.getOrNull(b) ?: return Float.NaN
        val pc = frame.worldLandmarks.getOrNull(c) ?: return Float.NaN

        return angleAt(
            pa.x(), pa.y(), pa.z(),
            pb.x(), pb.y(), pb.z(),
            pc.x(), pc.y(), pc.z()
        )
    }
}
