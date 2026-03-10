package com.cloud

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class PolicyManager(private val activity: MainActivity) {

    private val adminComponent: ComponentName = ComponentName(activity, MyDeviceAdminReceiver::class.java)

    private val adminRequestLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->}

    fun checkAndRequestAdminRights() {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Ermöglicht Sicherheitsfunktionen wie das Sperren des Geräts.")
            }
            adminRequestLauncher.launch(intent)
        }
    }
}