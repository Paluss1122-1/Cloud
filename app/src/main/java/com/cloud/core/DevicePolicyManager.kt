package com.cloud.core

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.cloud.core.activities.MyDeviceAdminReceiver

class PolicyManager(private val activity: ComponentActivity) {

    private val adminComponent: ComponentName =
        ComponentName(activity, MyDeviceAdminReceiver::class.java)

    private var adminRequestInFlight = false

    private val adminRequestLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            adminRequestInFlight = false
            when (result.resultCode) {
                Activity.RESULT_OK -> {}
                Activity.RESULT_CANCELED -> {}
                else -> {}
            }
        }

    fun checkAndRequestAdminRights() {
        if (adminRequestInFlight) return

        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Ermöglicht Sicherheitsfunktionen wie das Sperren des Geräts."
                )
            }
            try {
                adminRequestInFlight = true
                adminRequestLauncher.launch(intent)
            } catch (_: Exception) {
                adminRequestInFlight = false
            }
        }
    }
}