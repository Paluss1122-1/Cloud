package com.cloud.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import io.ktor.network.selector.SelectInterest.Companion.flags

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: AutoClickAccessibilityService? = null

        fun getInstance(): AutoClickAccessibilityService? = instance

        fun isServiceEnabled(): Boolean = instance != null

        fun closeNots() {
            instance?.closeNotificationShade()
        }
    }

    fun closeNotificationShade() {
        if (instance != null) {
            instance?.closeNotificationShade()
        } else {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            this.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Nicht benötigt für Auto-Klick
    }

    override fun onInterrupt() {
        // Service wurde unterbrochen
    }

    /**
     * Führt einen Klick an der angegebenen Position aus
     */
    fun performClickAt(x: Int, y: Int, onComplete: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete?.invoke(false)
            }
        }, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}