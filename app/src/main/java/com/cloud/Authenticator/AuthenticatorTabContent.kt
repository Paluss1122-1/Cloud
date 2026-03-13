package com.cloud.authenticator

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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun AuthenticatorTab() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val activity = remember {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    if (activity == null) {
        LaunchedEffect(activity) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "Main Acivity (Authenticator)",
                    "❌ Keine FragmentActivity gefunden!",
                    Instant.now().toString(),
                    "Error"
                )
            )
        }
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (lockEnabled && !isAuthenticated && !shouldShowPrompt) {
                    shouldShowPrompt = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(shouldShowPrompt) {
        if (shouldShowPrompt && lockEnabled && !isAuthenticated) {
            delay(100)
            showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    isAuthenticated = true
                    showError = false
                    shouldShowPrompt = false
                },
                onError = { error, isCritical ->
                    if (isCritical) {
                        errorMessage = error
                        showError = true
                        shouldShowPrompt = false
                        isAuthenticated = false
                        coroutineScope.launch {
                            ERRORINSERT(
                                ERRORINSERTDATA(
                                    "Main Activity (Authenticator)",
                                    "❌ AUTH ERROR: $error",
                                    Instant.now().toString(),
                                    "Error"
                                )
                            )
                        }
                    } else {
                        shouldShowPrompt = false
                    }
                }
            )
        }
    }

    LaunchedEffect(showError) {
        if (showError) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "Main Activity (Authenticator)",
                    "⚠ Zeige Error Screen",
                    Instant.now().toString(),
                    "Warning"
                )
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        when {
            !lockEnabled ->{
                val db = TwoFADatabase.getDatabase(context)
                MainApp(db)
            }
            !isAuthenticated && showError -> {
                ErrorScreen(
                    message = errorMessage,
                    onRetry = {
                        showError = false
                        shouldShowPrompt = true
                    },
                    onUnlock = {
                        showError = false
                        shouldShowPrompt = true
                    }
                )
            }

            !isAuthenticated -> {
                LockScreen(
                    onRetry = {
                        shouldShowPrompt = true
                    }
                )
            }

            else -> {
                val db = TwoFADatabase.getDatabase(context)
                MainApp(db)
            }
        }
    }
}

@Composable
private fun LockScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
    if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        onError("Activity nicht bereit", true)
        return
    }

    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(
        Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    )

    when (canAuth) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
        }

        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError(
                "Keine Authentifizierung eingerichtet. Bitte richte einen Fingerabdruck oder PIN ein.",
                true
            )
            return
        }

        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            onError("Biometrische Hardware nicht verfügbar", true)
            return
        }

        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onError("Hardware temporär nicht verfügbar", true)
            return
        }

        else -> {
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
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                        onError("Biometrische Authentifizierung nicht verfügbar", true)
                    }

                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onError("Authentifizierung abgebrochen", false)
                    }

                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onError("Zu viele Fehlversuche. Bitte warte einen Moment.", true)
                    }

                    BiometricPrompt.ERROR_TIMEOUT -> {
                        onError("Zeitüberschreitung", false)
                    }

                    else -> {
                        onError("Fehler: $errString", false)
                    }
                }
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("App-Sperre")
        .setSubtitle("Authentifizieren, um den Tab zu öffnen")
        .setAllowedAuthenticators(
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onError("Fehler beim Starten der Authentifizierung: ${e.message}", true)
    }
}