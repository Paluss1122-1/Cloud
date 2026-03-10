package com.cloud

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuManager {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        Shizuku.requestPermission(1001)
    }

    fun suspendApp(packageName: String, suspend: Boolean) {
        if (!hasPermission()) return
        try {
            val cmd = if (suspend) "pm suspend $packageName" else "pm unsuspend $packageName"
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}