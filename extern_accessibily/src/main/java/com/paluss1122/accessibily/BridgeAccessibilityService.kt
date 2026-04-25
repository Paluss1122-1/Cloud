package com.paluss1122.accessibily

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import org.json.JSONObject

@SuppressLint("AccessibilityPolicy")
class BridgeAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val json = intent.getStringExtra("cmd") ?: return
            Toast.makeText(this@BridgeAccessibilityService, "Received", Toast.LENGTH_LONG).show()
            Log.d("access_cloud", "Received")
            mainHandler.post { execute(json) }
        }
    }

    override fun onServiceConnected() {
        val filter = IntentFilter("com.paluss1122.accessibily.EXECUTE")
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun execute(json: String) {
        try {
            val cmd = JSONObject(json)
            when (cmd.getString("action")) {
                "click_text"   -> clickByText(cmd.getString("text"))
                "click_id"     -> clickById(cmd.getString("id"))
                "tap"          -> tap(cmd.getDouble("x").toFloat(), cmd.getDouble("y").toFloat())
                "swipe"        -> swipe(cmd)
                "press"        -> press(cmd.getString("key"))
                "input_text"   -> inputText(cmd.getString("text"))
                "scroll"       -> scroll(cmd)
                "open_app"     -> startActivity(packageManager.getLaunchIntentForPackage(cmd.getString("package")))
                "close_nots"   -> performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            Log.e("Bridge", "execute failed: $e")
        }
    }

    private fun clickByText(text: String) {
        rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
            ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun clickById(id: String) {
        rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)
            ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build(), null, null
        )
    }

    private fun swipe(cmd: JSONObject) {
        val path = Path().apply {
            moveTo(cmd.getDouble("x1").toFloat(), cmd.getDouble("y1").toFloat())
            lineTo(cmd.getDouble("x2").toFloat(), cmd.getDouble("y2").toFloat())
        }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, cmd.optLong("duration", 300)))
                .build(), null, null
        )
    }

    private fun press(key: String) {
        val action = when (key) {
            "back"       -> GLOBAL_ACTION_BACK
            "home"       -> GLOBAL_ACTION_HOME
            "recents"    -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quicksettings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else         -> return
        }
        performGlobalAction(action)
    }

    private fun inputText(text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun scroll(cmd: JSONObject) {
        val action = if (cmd.optString("direction") == "down")
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

        val id = cmd.optString("id")
        val node = if (id.isNotEmpty())
            rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
        else
            rootInActiveWindow
        node?.performAction(action)
    }
}