package com.cloud.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.cloud.autoclickertab.ClickPoint
import kotlinx.coroutines.*

class AutoClickerService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isClicking = false
    private var clickJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Click Point Markers
    private val pointMarkers = mutableListOf<View>()

    companion object {
        var clickPoints = mutableListOf<ClickPoint>()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        updatePointMarkers()
    }

    private fun createOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 100
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#EE1A1A1A"))

            // Titel
            addView(TextView(this@AutoClickerService).apply {
                text = "🎯 Auto Clicker"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 20)
            })

            // Status
            val statusText = TextView(this@AutoClickerService).apply {
                text = "Status: Inaktiv"
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 10)
            }
            addView(statusText)

            // Punkte Anzahl
            val pointsText = TextView(this@AutoClickerService).apply {
                text = "Punkte: ${clickPoints.size}"
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 20)
            }
            addView(pointsText)

            // Add Point Button
            addView(Button(this@AutoClickerService).apply {
                text = "+ Punkt hinzufügen"
                setBackgroundColor(Color.parseColor("#FF9800"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    showAddPointDialog()
                }
            })

            // Play Button
            addView(Button(this@AutoClickerService).apply {
                text = "▶ Start"
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    startAutoClick()
                    statusText.text = "Status: Aktiv"
                    statusText.setTextColor(Color.GREEN)
                }
            })

            // Stop Button
            addView(Button(this@AutoClickerService).apply {
                text = "■ Stop"
                setBackgroundColor(Color.parseColor("#F44336"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    stopAutoClick()
                    statusText.text = "Status: Inaktiv"
                    statusText.setTextColor(Color.GRAY)
                }
            })

            // Clear All Button
            addView(Button(this@AutoClickerService).apply {
                text = "🗑 Alle löschen"
                setBackgroundColor(Color.parseColor("#666666"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    clickPoints.clear()
                    updatePointMarkers()
                    pointsText.text = "Punkte: 0"
                }
            })

            // Close Button
            addView(Button(this@AutoClickerService).apply {
                text = "✕ Schließen"
                setBackgroundColor(Color.parseColor("#555555"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    stopAutoClick()
                    stopSelf()
                }
            })
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun showAddPointDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#EE2A2A2A"))
        }

        val xInput = EditText(this).apply {
            hint = "X-Koordinate"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val yInput = EditText(this).apply {
            hint = "Y-Koordinate"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val countInput = EditText(this).apply {
            hint = "Anzahl Klicks"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        val delayInput = EditText(this).apply {
            hint = "Verzögerung (ms)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1000")
        }

        dialogLayout.addView(TextView(this).apply {
            text = "Neuer Klick-Punkt"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        })
        dialogLayout.addView(xInput)
        dialogLayout.addView(yInput)
        dialogLayout.addView(countInput)
        dialogLayout.addView(delayInput)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val addBtn = Button(this).apply {
            text = "Hinzufügen"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setOnClickListener {
                val x = xInput.text.toString().toIntOrNull() ?: 0
                val y = yInput.text.toString().toIntOrNull() ?: 0
                val count = countInput.text.toString().toIntOrNull() ?: 1
                val delay = delayInput.text.toString().toLongOrNull() ?: 1000

                clickPoints.add(ClickPoint(x, y, count, delay))
                updatePointMarkers()
                windowManager?.removeView(dialogLayout)

                // Update Punkte Text im Overlay
                (overlayView as? LinearLayout)?.getChildAt(2)?.let {
                    (it as? TextView)?.text = "Punkte: ${clickPoints.size}"
                }
            }
        }

        val cancelBtn = Button(this).apply {
            text = "Abbrechen"
            setBackgroundColor(Color.parseColor("#666666"))
            setOnClickListener {
                windowManager?.removeView(dialogLayout)
            }
        }

        btnLayout.addView(addBtn)
        btnLayout.addView(cancelBtn)
        dialogLayout.addView(btnLayout)

        val dialogParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager?.addView(dialogLayout, dialogParams)
    }

    private fun updatePointMarkers() {
        // Entferne alte Marker
        pointMarkers.forEach { windowManager?.removeView(it) }
        pointMarkers.clear()

        // Erstelle neue Marker für jeden Punkt
        clickPoints.forEachIndexed { index, point ->
            val marker = createMarker(point, index)
            pointMarkers.add(marker)
        }
    }

    private fun createMarker(point: ClickPoint, index: Int): View {
        val markerSize = 80

        val markerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#AA4CAF50"))
            setPadding(10, 10, 10, 10)
            gravity = Gravity.CENTER

            addView(TextView(this@AutoClickerService).apply {
                text = "${index + 1}"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })

            addView(TextView(this@AutoClickerService).apply {
                text = "${point.clickCount}x"
                textSize = 10f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
        }

        val params = WindowManager.LayoutParams(
            markerSize,
            markerSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = point.x - markerSize / 2
            y = point.y - markerSize / 2
        }

        // Touch Listener für Drag & Drop + Löschen
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L

        markerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Marker fokussierbar machen
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    windowManager?.updateViewLayout(markerView, params)

                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(markerView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Marker wieder unfokussierbar machen
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager?.updateViewLayout(markerView, params)

                    val touchDuration = System.currentTimeMillis() - touchStartTime
                    val moved = Math.abs(event.rawX - initialTouchX) > 10 ||
                            Math.abs(event.rawY - initialTouchY) > 10

                    // Long Press = Löschen
                    if (touchDuration > 1000 && !moved) {
                        clickPoints.removeAt(index)
                        updatePointMarkers()
                        Toast.makeText(this, "Punkt gelöscht", Toast.LENGTH_SHORT).show()

                        // Update Punkte Text
                        (overlayView as? LinearLayout)?.getChildAt(2)?.let {
                            (it as? TextView)?.text = "Punkte: ${clickPoints.size}"
                        }
                    } else if (moved) {
                        // Position aktualisieren
                        val centerX = params.x + markerSize / 2
                        val centerY = params.y + markerSize / 2
                        clickPoints[index] = clickPoints[index].copy(x = centerX, y = centerY)
                        Toast.makeText(this, "Position aktualisiert", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(markerView, params)
        return markerView
    }

    private fun startAutoClick() {
        if (isClicking || clickPoints.isEmpty()) return
        if (!AutoClickAccessibilityService.isServiceEnabled()) {
            Toast.makeText(this, "Accessibility Service nicht aktiv!", Toast.LENGTH_LONG).show()
            return
        }

        isClicking = true
        clickJob = serviceScope.launch {
            while (isClicking) {
                for (point in clickPoints) {
                    if (!isClicking) break

                    repeat(point.clickCount) {
                        if (!isClicking) break
                        withContext(Dispatchers.Main) {
                            AutoClickAccessibilityService.getInstance()?.performClickAt(
                                point.x, point.y
                            )
                        }
                        delay(150)
                    }

                    if (isClicking) {
                        delay(point.delayMs)
                    }
                }
            }
        }
    }

    private fun stopAutoClick() {
        isClicking = false
        clickJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoClick()
        pointMarkers.forEach { windowManager?.removeView(it) }
        pointMarkers.clear()
        try {
            windowManager?.removeView(overlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
    }
}