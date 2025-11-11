package com.example.cloud

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher

class PolicyManager(private val activity: MainActivity) {

    private val REQUEST_CODE_ENABLE_ADMIN = 1001
    private lateinit var adminComponent: ComponentName

    init {
        adminComponent = ComponentName(activity, MyDeviceAdminReceiver::class.java)
    }

    fun checkAndRequestAdminRights() {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Ermöglicht Sicherheitsfunktionen wie das Sperren des Geräts.")
            }
            activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
        }
    }

    fun isAdminActive(): Boolean {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent)
    }
}