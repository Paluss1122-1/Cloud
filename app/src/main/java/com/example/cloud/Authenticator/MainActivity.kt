package com.example.cloud.Authenticator

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

@Composable
fun AuthenticatorTab() {
    val context = LocalContext.current

    // Versuche erst FragmentActivity, dann ComponentActivity
    val activity = when (context) {
        is FragmentActivity -> context
        is ComponentActivity -> context
        else -> (context as? ContextWrapper)?.baseContext as? ComponentActivity
    }

    if (activity == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Fehler: Keine Activity gefunden")
        }
        return
    }

    val prefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var isAuthenticated by remember { mutableStateOf(false) }
    var lockEnabled by remember {
        mutableStateOf(prefs.getBoolean("lockEnabled", false))
    }

    // Biometric Prompt nur einmal beim Start anzeigen
    LaunchedEffect(Unit) {
        if (lockEnabled && !isAuthenticated) {
            delay(300) // UI-Stabilität
            showBiometricPrompt(
                activity = activity,  // ✅ Jetzt korrekt!
                onSuccess = {
                    isAuthenticated = true
                },
                onError = {
                    isAuthenticated = true
                }
            )
        } else {
            isAuthenticated = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!isAuthenticated) {
            // Entsperr-Screen
            LockScreen(
                onRetry = {
                    showBiometricPrompt(
                        activity = activity,
                        onSuccess = {
                            isAuthenticated = true
                        },
                        onError = {
                            isAuthenticated = true
                        }
                    )
                }
            )
        } else {
            // Hauptapp anzeigen
            val db = TwoFADatabase.getDatabase(context)
            MainApp(db)
        }
    }
}

@Composable
private fun LockScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Authentifizierung erforderlich",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Erneut authentifizieren")
            }
        }
    }
}

private fun showBiometricPrompt(
    activity: ComponentActivity,  // ✅ Jetzt ComponentActivity!
    onSuccess: () -> Unit,
    onError: () -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(
        Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    )

    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
        val executor = ContextCompat.getMainExecutor(activity)

        // BiometricPrompt braucht FragmentActivity - prüfe das!
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity == null) {
            // Fallback: Keine Biometric möglich ohne FragmentActivity
            onError()
            return
        }

        val biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_CANCELED -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_LOCKOUT -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_NO_SPACE -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_TIMEOUT -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            onError()
                        }

                        BiometricPrompt.ERROR_VENDOR -> {
                            onError()
                        }
                    }
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
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Exception) {
            onError()
        }
    } else {
        // Kein Biometric verfügbar
        onError()
    }
}