package com.cloud

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class PolicyManager(private val activity: ComponentActivity) {

    private val adminComponent: ComponentName = ComponentName(activity, MyDeviceAdminReceiver::class.java)

    private var adminRequestInFlight = false

    private val adminRequestLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            adminRequestInFlight = false
            val dpm =
                activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    if (!dpm.isAdminActive(adminComponent)) {
                        Log.i(
                            TAG,
                            "Device-Admin-Dialog beendet (OK), aber Admin ist nicht aktiv – Nutzer hat vermutlich abgelehnt."
                        )
                    }
                }
                Activity.RESULT_CANCELED -> {
                    Log.i(TAG, "Device-Admin-Anfrage abgebrochen.")
                }
                else -> {
                    Log.i(TAG, "Device-Admin-Anfrage beendet mit resultCode=${result.resultCode}")
                }
            }
        }

    fun checkAndRequestAdminRights() {
        if (adminRequestInFlight) return

        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Ermöglicht Sicherheitsfunktionen wie das Sperren des Geräts.")
            }
            try {
                adminRequestInFlight = true
                adminRequestLauncher.launch(intent)
            } catch (e: Exception) {
                adminRequestInFlight = false
                Log.e(TAG, "Device-Admin-Intent konnte nicht gestartet werden", e)
            }
        }
    }

    private companion object {
        private const val TAG = "PolicyManager"
    }
}