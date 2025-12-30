package com.example.cloud.Authenticator

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

private const val TAG = "AuthenticatorDebug"

@Composable
fun AuthenticatorTab() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Finde die FragmentActivity
    val activity = remember {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    if (activity == null) {
        Log.e(TAG, "❌ Keine FragmentActivity gefunden!")
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Fehler",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Fehler: MainActivity muss von FragmentActivity erben",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    Log.d(TAG, "✅ FragmentActivity gefunden: ${activity.javaClass.simpleName}")

    val prefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var isAuthenticated by remember { mutableStateOf(false) }
    var lockEnabled by remember {
        mutableStateOf(prefs.getBoolean("lockEnabled", false))
    }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var shouldShowPrompt by remember { mutableStateOf(false) }

    Log.d(TAG, "📱 Initial State - lockEnabled: $lockEnabled")

    // Lifecycle Observer - warte bis Activity RESUMED ist
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Log.d(TAG, "🔄 Lifecycle Event: $event")
            if (event == Lifecycle.Event.ON_RESUME) {
                if (lockEnabled && !isAuthenticated && !shouldShowPrompt) {
                    Log.d(TAG, "✅ Activity RESUMED - zeige Prompt")
                    shouldShowPrompt = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Zeige Biometric Prompt wenn Activity bereit ist
    LaunchedEffect(shouldShowPrompt) {
        if (shouldShowPrompt && lockEnabled && !isAuthenticated) {
            delay(300) // Kurze Verzögerung für UI-Stabilität

            Log.d(TAG, "🔐 Zeige Biometric Prompt")
            showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    Log.d(TAG, "✅ AUTH SUCCESS")
                    isAuthenticated = true
                    showError = false
                    shouldShowPrompt = false
                },
                onError = { error, isCritical ->
                    Log.e(TAG, "❌ AUTH ERROR: $error (critical: $isCritical)")

                    if (isCritical) {
                        // Kritischer Hardware-Fehler - zeige Error Screen
                        errorMessage = error
                        showError = true
                        shouldShowPrompt = false
                        isAuthenticated = false // App bleibt gesperrt
                    } else {
                        // User hat abgebrochen - bleibe auf Lock Screen
                        shouldShowPrompt = false
                        // App bleibt gesperrt, kein Error Screen
                    }
                }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            !isAuthenticated && showError -> {
                Log.d(TAG, "⚠️ Zeige Error Screen")
                ErrorScreen(
                    message = errorMessage,
                    onRetry = {
                        showError = false
                        shouldShowPrompt = true
                    },
                    onUnlock = {
                        Log.w(TAG, "⚠️ Notfall-Entsperrung deaktiviert")
                        // isAuthenticated = true // SICHERHEITSLÜCKE: Deaktiviert
                        showError = false
                        shouldShowPrompt = true // Versuch es nochmal
                    }
                )
            }

            !isAuthenticated -> {
                Log.d(TAG, "🔒 Zeige LockScreen")
                LockScreen(
                    onRetry = {
                        shouldShowPrompt = true
                    }
                )
            }

            else -> {
                Log.d(TAG, "🎯 Zeige MainApp")
                val db = TwoFADatabase.getDatabase(context)
                MainApp(db)
            }
        }
    }
}

@Composable
private fun LockScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Gesperrt",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "App gesperrt",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Authentifizierung erforderlich",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Authentifizieren")
            }
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Fehler",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Authentifizierungsfehler",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Erneut versuchen")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onUnlock) {
                Text("Ohne Authentifizierung fortfahren")
            }
        }
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (error: String, isCritical: Boolean) -> Unit
) {
    Log.d(TAG, "🔍 showBiometricPrompt() aufgerufen")
    Log.d(TAG, "🔍 Activity State: ${activity.lifecycle.currentState}")

    // Prüfe ob Activity im richtigen Zustand ist
    if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        Log.e(TAG, "❌ Activity ist nicht im STARTED State!")
        onError("Activity nicht bereit", true)
        return
    }

    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(
        Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    )

    Log.d(TAG, "🔐 Biometric Status: ${getBiometricStatusString(canAuth)}")

    when (canAuth) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            // Biometric verfügbar - weiter unten
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            Log.w(TAG, "⚠️ Keine Biometrics eingerichtet")
            onError("Keine Authentifizierung eingerichtet. Bitte richte einen Fingerabdruck oder PIN ein.", true)
            return
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            Log.w(TAG, "⚠️ Keine Biometric-Hardware")
            onError("Biometrische Hardware nicht verfügbar", true)
            return
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            Log.w(TAG, "⚠️ Hardware temporär nicht verfügbar")
            onError("Hardware temporär nicht verfügbar", true)
            return
        }
        else -> {
            Log.w(TAG, "⚠️ Biometric nicht verfügbar: $canAuth")
            onError("Authentifizierung nicht verfügbar", true)
            return
        }
    }

    val executor = ContextCompat.getMainExecutor(activity)

    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "🎉 onAuthenticationSucceeded!")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "❌ onAuthenticationError: ${getErrorCodeName(errorCode)} - $errString")

                when (errorCode) {
                    // Hardware-Fehler - KRITISCH, zeige Error Screen und entsperre
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                        onError("Biometrische Authentifizierung nicht verfügbar", true)
                    }

                    // User hat abgebrochen - NICHT kritisch, bleibe auf Lock Screen
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        Log.d(TAG, "ℹ️ User hat abgebrochen - bleibe auf Lock Screen")
                        onError("Authentifizierung abgebrochen", false)
                    }

                    // Lockout - NICHT kritisch, kann später wieder versuchen
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onError("Zu viele Fehlversuche. Bitte warte einen Moment.", false)
                    }

                    // Timeout - NICHT kritisch
                    BiometricPrompt.ERROR_TIMEOUT -> {
                        onError("Zeitüberschreitung", false)
                    }

                    // Andere Fehler - vorsichtshalber nicht kritisch
                    else -> {
                        onError("Fehler: $errString", false)
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "⚠️ onAuthenticationFailed (falscher Fingerabdruck)")
                // Kein Callback hier - Prompt bleibt offen für weitere Versuche
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("App-Sperre")
        .setSubtitle("Authentifizieren, um die App zu öffnen")
        .setAllowedAuthenticators(
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    try {
        Log.d(TAG, "🚀 biometricPrompt.authenticate() wird aufgerufen")
        biometricPrompt.authenticate(promptInfo)
        Log.d(TAG, "✅ authenticate() erfolgreich aufgerufen")
    } catch (e: Exception) {
        Log.e(TAG, "❌ Exception beim authenticate(): ${e.message}")
        e.printStackTrace()
        onError("Fehler beim Starten der Authentifizierung: ${e.message}", true)
    }
}

private fun getBiometricStatusString(status: Int): String {
    return when (status) {
        BiometricManager.BIOMETRIC_SUCCESS -> "SUCCESS"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "HW_UNAVAILABLE"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NONE_ENROLLED"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO_HARDWARE"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "SECURITY_UPDATE_REQUIRED"
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "UNSUPPORTED"
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "STATUS_UNKNOWN"
        else -> "UNKNOWN ($status)"
    }
}

private fun getErrorCodeName(errorCode: Int): String {
    return when (errorCode) {
        BiometricPrompt.ERROR_CANCELED -> "ERROR_CANCELED"
        BiometricPrompt.ERROR_HW_NOT_PRESENT -> "ERROR_HW_NOT_PRESENT"
        BiometricPrompt.ERROR_HW_UNAVAILABLE -> "ERROR_HW_UNAVAILABLE"
        BiometricPrompt.ERROR_LOCKOUT -> "ERROR_LOCKOUT"
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "ERROR_LOCKOUT_PERMANENT"
        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "ERROR_NEGATIVE_BUTTON"
        BiometricPrompt.ERROR_NO_BIOMETRICS -> "ERROR_NO_BIOMETRICS"
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> "ERROR_NO_DEVICE_CREDENTIAL"
        BiometricPrompt.ERROR_NO_SPACE -> "ERROR_NO_SPACE"
        BiometricPrompt.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
        BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "ERROR_UNABLE_TO_PROCESS"
        BiometricPrompt.ERROR_USER_CANCELED -> "ERROR_USER_CANCELED"
        BiometricPrompt.ERROR_VENDOR -> "ERROR_VENDOR"
        else -> "UNKNOWN_ERROR ($errorCode)"
    }
}