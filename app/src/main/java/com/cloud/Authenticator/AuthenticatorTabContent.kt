package com.cloud.authenticator

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant


private enum class AuthTab(val label: String, val icon: String) {
    PASSWORDS("Passwörter", "🔑"),
    TWOFACTOR("2FA Codes", "🛡️")
}

object BiometricKeyHelper {
    private const val KEY_NAME = "cloud_auth_key"

    fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        ks.getKey(KEY_NAME, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(KEY_NAME, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return keyGen.generateKey()
    }

    fun deleteKey() {
    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
    if (ks.containsAlias(KEY_NAME)) ks.deleteEntry(KEY_NAME)
    }

    fun getCipher(): Cipher =
        Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
}


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
        LaunchedEffect(Unit) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "AuthenticatorTab",
                    "❌ FragmentActivity fehlt",
                    Instant.now().toString(),
                    "ERROR"
                )
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Fehler: MainActivity muss FragmentActivity erben",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        }
        return
    }

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var isAuthenticated by remember { mutableStateOf(false) }
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("lockEnabled", false)) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var shouldShowPrompt by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(AuthTab.PASSWORDS) }

    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && lockEnabled && !isAuthenticated && !shouldShowPrompt) {
                shouldShowPrompt = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(shouldShowPrompt) {
        if (shouldShowPrompt && lockEnabled && !isAuthenticated) {
            delay(100)
            showBiometricPrompt(
    activity = activity,
    onSuccess = { cipher ->
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
                                    "AuthenticatorTab",
                                    "❌ AUTH: $error",
                                    Instant.now().toString(),
                                    "ERROR"
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

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        when {
            !lockEnabled || isAuthenticated -> {
                AuthenticatedContent(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    context = context
                )
            }

            !isAuthenticated && showError -> {
                ErrorScreen(
                    message = errorMessage,
                    onRetry = { showError = false; shouldShowPrompt = true },
                    onUnlock = { showError = false; shouldShowPrompt = true }
                )
            }

            else -> {
                LockScreen(onRetry = { shouldShowPrompt = true })
            }
        }
    }
}


@Composable
private fun AuthenticatedContent(
    selectedTab: AuthTab,
    onTabSelected: (AuthTab) -> Unit,
    context: Context
) {
    val passwordDb = remember { PasswordDatabase.getDatabase(context) }
    val twoFaDb = remember { TwoFADatabase.getDatabase(context) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF17171C),
                tonalElevation = 0.dp
            ) {
                AuthTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = {
                            Text(tab.icon, style = MaterialTheme.typography.titleMedium)
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color(0xFF4A90E2),
                            unselectedIconColor = Color(0xFF6B6B80),
                            unselectedTextColor = Color(0xFF6B6B80)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = selectedTab == AuthTab.PASSWORDS,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PasswordManagerScreen(db = passwordDb)
            }

            AnimatedVisibility(
                visible = selectedTab == AuthTab.TWOFACTOR,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TwoFAAppContent(db = twoFaDb)
            }
        }
    }
}


@Composable
private fun TwoFAAppContent(db: TwoFADatabase) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "list") {
        composable("list") {
            TwoFAListScreen(
                db = db,
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreenWithScreenshotProtection(
                onBackClick = { navController.popBackStack() }
            )
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
            Spacer(Modifier.height(24.dp))
            Text(
                "App gesperrt",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Biometrische Authentifizierung erforderlich",
                style = MaterialTheme.typography.bodyMedium, color = Color.White
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Entsperren") }
        }
    }
}


@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onUnlock: () -> Unit) {
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
            Spacer(Modifier.height(24.dp))
            Text(
                "Authentifizierungsfehler",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUnlock) { Text("Ohne Authentifizierung fortfahren") }
        }
    }
}


private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: (Cipher) -> Unit,
    onError: (error: String, isCritical: Boolean) -> Unit
) {
    if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        onError("Activity nicht bereit", true); return
    }

    val bm = BiometricManager.from(activity)
    val canAuth =
        bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
    when (canAuth) {
        BiometricManager.BIOMETRIC_SUCCESS -> { /* proceed */
        }

        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError(
                "Keine Authentifizierung eingerichtet. Bitte richte einen Fingerabdruck oder PIN ein.",
                true
            ); return
        }

        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            onError("Biometrische Hardware nicht verfügbar", true); return
        }

        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onError("Hardware temporär nicht verfügbar", true); return
        }

        else -> {
            onError("Authentifizierung nicht verfügbar", true); return
        }
    }

    val executor = ContextCompat.getMainExecutor(activity)

    val cipher = BiometricKeyHelper.getCipher()
    try {
        cipher.init(Cipher.ENCRYPT_MODE, BiometricKeyHelper.getOrCreateKey())
    } catch (e: KeyPermanentlyInvalidatedException) {
        onError("Biometriedaten haben sich geändert. Bitte erneut einrichten.", true)
        return
    }

    val prompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val unlockedCipher = result.cryptoObject?.cipher ?: run {
                    onError("Kryptografisches Objekt fehlt", true); return
                }
                onSuccess(unlockedCipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                        onError("Biometrische Authentifizierung nicht verfügbar", true)

                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED ->
                        onError("Authentifizierung abgebrochen", false)

                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                        onError("Zu viele Fehlversuche. Bitte warte einen Moment.", true)

                    BiometricPrompt.ERROR_TIMEOUT ->
                        onError("Zeitüberschreitung", false)

                    else ->
                        onError("Fehler: $errString", false)
                }
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Cloud Passwort-Manager")
        .setSubtitle("Authentifizieren um fortzufahren")
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Abbrechen")
        .build()

    try {
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (e: KeyPermanentlyInvalidatedException) {
    BiometricKeyHelper.deleteKey()
    onError("Biometriedaten haben sich geändert. Bitte erneut einrichten.", true)
    return
    } catch (e: Exception) {
        onError("Fehler beim Starten: ${e.message}", true)
    }
}
