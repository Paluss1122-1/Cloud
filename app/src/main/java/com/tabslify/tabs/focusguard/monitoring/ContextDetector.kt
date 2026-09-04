package com.tabslify.tabs.focusguard.monitoring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

object ContextDetector {
    private const val BUFFER_SIZE = 20
    private const val MOTION_VARIANCE_THRESHOLD = 0.6f
    private const val HORIZONTAL_RATIO = 0.7f

    private val _resting = MutableStateFlow(false)
    val resting: StateFlow<Boolean> = _resting.asStateFlow()

    @Volatile
    private var running = false
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val buffer = ArrayDeque<Float>(BUFFER_SIZE)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val values = event?.values ?: return
            val x = values.getOrNull(0) ?: return
            val y = values.getOrNull(1) ?: return
            val z = values.getOrNull(2) ?: return
            val magnitude = sqrt(x * x + y * y + z * z)
            buffer.addLast(magnitude)
            if (buffer.size > BUFFER_SIZE) buffer.removeFirst()
            if (buffer.size < BUFFER_SIZE) return

            val avg = buffer.average().toFloat()
            val variance = buffer.sumOf { ((it - avg) * (it - avg)).toDouble() } / buffer.size
            val lowMotion = variance < MOTION_VARIANCE_THRESHOLD
            val horizontal = magnitude > 0f && abs(z) / magnitude > HORIZONTAL_RATIO
            _resting.value = lowMotion && horizontal
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(context: Context) {
        if (running) return
        running = true
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensor ->
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(listener)
        sensorManager = null
        accelerometer = null
        buffer.clear()
        _resting.value = false
    }

    fun isResting(): Boolean = _resting.value
}
