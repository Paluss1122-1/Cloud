package com.cloud

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AnticipateInterpolator
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.cloud.errorreports.ErrorMonitorService
import com.cloud.jsoneditor.JsonEditorContent
import com.cloud.privatecloudapp.LandingPageOrApp
import com.cloud.quicksettingsfunctions.BatteryDataRepository
import com.cloud.ui.theme.c
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

var storage: Storage? = null

class MainActivity : FragmentActivity() {
    private lateinit var policyManager: PolicyManager
    val supabase: SupabaseClient = SupabaseConfigALT.client

    private var jsonFilePath by mutableStateOf<String?>(null)
    private var jsonFileUri by mutableStateOf<Uri?>(null)
    private var showJsonEditor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CoroutineScope(Dispatchers.IO).launch {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "UncaughtException: ${thread.name}",
                        throwable.stackTraceToString().take(8000),
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
            Thread.sleep(2000)
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        installSplashScreen()
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        window.insetsController?.let { controller ->
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        policyManager = PolicyManager(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false

        policyManager.checkAndRequestAdminRights()
        BatteryDataRepository.init(this)

        val audioPermission = Manifest.permission.READ_MEDIA_AUDIO
        val imagesPermission = Manifest.permission.READ_MEDIA_IMAGES
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val cameraPermission = Manifest.permission.CAMERA
        val contactsPermission = Manifest.permission.READ_CONTACTS

        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        launcher.launch(
            arrayOf(
                audioPermission,
                imagesPermission,
                locationPermission,
                cameraPermission,
                contactsPermission
            )
        )

        checkPermissionsAndHandleIntent(intent)

        val startTarget = intent.getStringExtra("target")

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = c(),
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = c()
                ) {
                    if (showJsonEditor && jsonFilePath != null) {
                        JsonEditorContent(
                            filePath = jsonFilePath!!,
                            fileUri = jsonFileUri,
                            context = this@MainActivity,
                            onClose = {
                                showJsonEditor = false
                                jsonFilePath = null
                                jsonFileUri = null
                            }
                        )
                    } else {
                        LandingPageOrApp(supabase.storage, startTarget)
                        storage = supabase.storage
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
        android.util.Log.d("MainActivity", "Intent Action: ${intent.action}")
        android.util.Log.d("MainActivity", "Intent Data: ${intent.data}")
        android.util.Log.d("MainActivity", "Intent Type: ${intent.type}")
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
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
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