package com.tabslify.core.activities

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.admin.DeviceAdminReceiver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.animation.AnticipateInterpolator
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.requestPermission
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.LandingPageOrApp
import com.tabslify.core.ui.Typography
import com.tabslify.core.ui.rememberAppColor
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.tabs.JsonEditorContent
import io.github.jan.supabase.storage.storage
import java.io.File

class MyDeviceAdminReceiver : DeviceAdminReceiver()

class MainActivity : FragmentActivity() {
    val sbclient = Config.client
    private var startTarget by mutableStateOf<String?>(null)

    private var jsonFilePath by mutableStateOf<String?>(null)
    private var jsonFileUri by mutableStateOf<Uri?>(null)
    private var showJsonEditor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val animator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(
                        splashScreenView,
                        View.TRANSLATION_Y,
                        0f,
                        -splashScreenView.height.toFloat()
                    )
                )
                interpolator = AnticipateInterpolator()
                duration = 1000L
            }
            animator.doOnEnd { splashScreenView.remove() }
            animator.start()
        }

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = false
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val notificationsGranted = results[Manifest.permission.POST_NOTIFICATIONS] == true
            if (notificationsGranted) {
                QuietHoursNotificationService.startService(this)
            }
        }

        requestPermission("not", launcher, this)

        if (prvt()) {

            requestPermission("all", launcher, this)

            val musicPrefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE)
            val wasPlayingMusic = musicPrefs.getBoolean("was_playing_music", false)
            val wasPlayingPodcast = musicPrefs.getBoolean("was_playing_podcast", false)

            if (wasPlayingMusic || wasPlayingPodcast) {
                com.tabslify.services.MediaPlayerService.startMusicService(this)
            }
        }

        checkPermissionsAndHandleIntent(intent)

        val startTarget = intent.getStringExtra("target")
        this.startTarget = startTarget
        setContent {
            val appColor = rememberAppColor()
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = appColor,
                    onSurface = Color.White
                ),
                typography = Typography,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = appColor
                ) {
                    if (showJsonEditor && jsonFilePath != null) {
                        JsonEditorContent(
                            filePath = jsonFilePath!!,
                            fileUri = jsonFileUri,
                            context = this,
                            onClose = {
                                showJsonEditor = false
                                jsonFilePath = null
                                jsonFileUri = null
                            }
                        )
                    } else if (startTarget == "files") {
                        //FilesTabContent()
                    } else {
                        LandingPageOrApp(sbclient.storage, startTarget)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (showJsonEditor) {
            showJsonEditor = false
            jsonFilePath = null
            jsonFileUri = null
        }
        checkPermissionsAndHandleIntent(intent)
    }

    private fun checkPermissionsAndHandleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) {
            handleIncomingIntent(intent)
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.let { uri ->
                    loadFileFromUri(uri)
                }
            }
        }
    }

    private fun loadFileFromUri(uri: Uri) {
        try {
            val filePath = when (uri.scheme) {
                "file" -> {
                    uri.path ?: throw Exception("Ungültiger Dateipfad")
                }

                "content" -> {
                    copyContentToTempFile(uri)
                }

                else -> throw Exception("Nicht unterstütztes URI-Schema: ${uri.scheme}")
            }

            jsonFilePath = filePath
            jsonFileUri = uri
            showJsonEditor = true

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Fehler beim Laden der JSON-Datei: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }

    private fun copyContentToTempFile(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception("Datei konnte nicht geöffnet werden")

        val fileName = try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "temp_${System.currentTimeMillis()}.json"
        } catch (_: Exception) {
            "temp_${System.currentTimeMillis()}.json"
        }

        val tempFile = File(cacheDir, fileName)
        tempFile.outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }

        return tempFile.absolutePath
    }
}