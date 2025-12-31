package com.example.cloud

import android.Manifest
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
        ) { permissions ->
            permissions.entries.forEach {
                println("${it.key} is granted: ${it.value}")
            }
        }

        launcher.launch(
            arrayOf(
                audioPermission,
                imagesPermission,
                locationPermission,
                cameraPermission,
                contactsPermission
            )
        )

        // MP4-Dateien von 'Other' nach 'videos' verschieben
        // Erste 100 Dateien in Unterordner '1' verschieben
        /*lifecycleScope.launch {
            try {
                // Erste 100 Dateien listen
                val listResult = supabase.storage.from("Other").list()

                println("Gefundene Dateien: ${listResult.size}")

                var movedCount = 0

                listResult.forEach { file ->
                    if (file.name.startsWith("VID")) {
                        try {
                            println("Versuche zu verschieben: ${file.name}")

                            // Datei herunterladen
                            val downloadedBytes = supabase.storage.from("Other")
                                .downloadAuthenticated(file.name)

                            println("Heruntergeladen: ${downloadedBytes.size} bytes")

                            // Datei in Unterordner '1' hochladen
                            supabase.storage.from("Other")
                                .upload("videos/${file.name}", downloadedBytes) {
                                    upsert = true
                                }

                            println("Hochgeladen: 1/${file.name}")

                            // Original löschen
                            supabase.storage.from("Other")
                                .delete(file.name)

                            movedCount++
                            println("✓ Erfolgreich verschoben ($movedCount): ${file.name} -> 1/${file.name}")
                        } catch (e: Exception) {
                            println("✗ Fehler beim Verschieben von ${file.name}: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }

                println("=== Fertig! $movedCount Dateien in Ordner '1' verschoben ===")
            } catch (e: Exception) {
                println("Fehler: ${e.message}")
                e.printStackTrace()
            }
        }*/

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
