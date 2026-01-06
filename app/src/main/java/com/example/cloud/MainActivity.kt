package com.example.cloud

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.example.cloud.privatecloudapp.PrivateCloudApp
import com.example.cloud.quicksettingsfunctions.BatteryDataRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.cloud.errorreportsclaude.ErrorMonitorService

class MainActivity : FragmentActivity() {
    private lateinit var policyManager: PolicyManager
    val supabase: SupabaseClient = SupabaseConfig.client

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        super.onCreate(savedInstanceState)

        window.insetsController?.let { controller ->
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        policyManager = PolicyManager(this)

        // ✅ Systemleisten transparent machen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false

        // Admin-Rechte prüfen und ggf. anfordern
        policyManager.checkAndRequestAdminRights()
        BatteryDataRepository.init(this)

        val audioPermission = Manifest.permission.READ_MEDIA_AUDIO

        val imagesPermission = Manifest.permission.READ_MEDIA_IMAGES

        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION

        val cameraPermission = Manifest.permission.CAMERA

        val contactsPermission = Manifest.permission.READ_CONTACTS

        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->}

        launcher.launch(
            arrayOf(
                audioPermission,
                imagesPermission,
                locationPermission,
                cameraPermission,
                contactsPermission
            )
        )

        val startTarget = intent.getStringExtra("target")

        // Content setzen
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrivateCloudApp(supabase.storage, startTarget)
                }
            }
        }

        try {
            val serviceIntent = Intent(this, ErrorMonitorService::class.java)
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Permissions für Notifications prüfen (Android 13+)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }
}
