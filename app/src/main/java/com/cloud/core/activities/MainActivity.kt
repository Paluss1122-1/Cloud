package com.cloud.core.activities

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.admin.DeviceAdminReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
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
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.cloud.core.objects.Config
import com.cloud.core.functions.errorInsert
import com.cloud.core.functions.ERRORINSERTDATA
import com.cloud.core.ui.LandingPageOrApp
import com.cloud.core.PolicyManager
import com.cloud.core.ui.Typography
import com.cloud.core.ui.c
import com.cloud.quicksettingsfunctions.BatteryDataRepository
import com.cloud.services.ErrorMonitorService
import com.cloud.tabs.JsonEditorContent
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Calendar

class MyDeviceAdminReceiver : DeviceAdminReceiver()

class MainActivity : FragmentActivity() {
    val sbclient = Config.client

    private var jsonFilePath by mutableStateOf<String?>(null)
    private var jsonFileUri by mutableStateOf<Uri?>(null)
    private var showJsonEditor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CoroutineScope(Dispatchers.IO).launch {
                errorInsert(
                    ERRORINSERTDATA(
                        "UncaughtException: ${thread.name}",
                        throwable.stackTraceToString().take(8000),
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
            Thread.sleep(2000)

            defaultHandler?.uncaughtException(thread, throwable)

            Process.killProcess(Process.myPid())
        }

        if (savedInstanceState == null) {
            installSplashScreen()
        }
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

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (currentHour) {
            in 11..16 -> WindowCompat.getInsetsController(
                window,
                window.decorView
            ).isAppearanceLightStatusBars = true

            else -> WindowCompat.getInsetsController(
                window,
                window.decorView
            ).isAppearanceLightStatusBars = false
        }

        PolicyManager(this).checkAndRequestAdminRights()
        BatteryDataRepository.init(this)
        Config.init(this)

        val permissions = arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        permissions.forEach {
            if (ActivityCompat.checkSelfPermission(this, it)
                != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(arrayOf(it))
            }
        }

        checkPermissionsAndHandleIntent(intent)

        val startTarget = intent.getStringExtra("target")

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = c(),
                    onSurface = Color.White
                ),
                typography = Typography,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = c()
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
                    } else {
                        LandingPageOrApp(sbclient.storage, startTarget)
                    }
                }
            }
        }

        try {
            val serviceIntent = Intent(this, ErrorMonitorService::class.java)
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (!showJsonEditor) {
            checkPermissionsAndHandleIntent(intent)
        } else {
            showJsonEditor = false
            jsonFilePath = null
            jsonFileUri = null

            checkPermissionsAndHandleIntent(intent)
        }
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
        val outputStream = FileOutputStream(tempFile)

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        return tempFile.absolutePath
    }
}