package com.tabslify.tabs.authenticator

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.tabslify.core.functions.errorInsert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.time.Duration.Companion.milliseconds

object BiometricKeyHelper {
    private const val KEY_NAME = "tabslify_auth_key"

    fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        ks.getKey(KEY_NAME, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
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
    val lifecycleOwner = LocalLifecycleOwner.current

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
            errorInsert(
                "AuthenticatorTab",
                "❌ FragmentActivity fehlt",
                Instant.now().toString(),
                "ERROR"

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
            delay(100.milliseconds)
            showBiometricPrompt(
                activity = activity,
                onSuccess = { _ ->
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
                        errorInsert(
                            "AuthenticatorTab",
                            "❌ AUTH: $error",
                            Instant.now().toString(),
                            "ERROR"

                        )
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
                AuthenticatedContent(context = context)
            }

            showError -> {
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
private fun AuthenticatedContent(context: Context) {
    val passwordDb = remember { PasswordDatabase.getDatabase(context) }
    val twoFaDb = remember { TwoFADatabase.getDatabase(context) }
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsScreenWithScreenshotProtection()
        } else {
            PasswordManagerScreen(
                db = passwordDb,
                twoFaDb = twoFaDb,
                onSettingsClick = { showSettings = true })
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
    } catch (_: KeyPermanentlyInvalidatedException) {
        onError("Biometriedaten haben sich geändert. Bitte erneut einrichten.", true)
        return
    }

    val prompt = BiometricPrompt(
        activity, executor,
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
        .setTitle("Tabslify Passwort-Manager")
        .setSubtitle("Authentifizieren um fortzufahren")
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Abbrechen")
        .build()

    try {
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (_: KeyPermanentlyInvalidatedException) {
        BiometricKeyHelper.deleteKey()
        onError("Biometriedaten haben sich geändert. Bitte erneut einrichten.", true)
        return
    } catch (e: Exception) {
        onError("Fehler beim Starten: ${e.message}", true)
    }
}

object ScreenshotProtectionManager {
    fun setScreenshotProtection(activity: Activity?, enabled: Boolean) {
        activity?.window?.let { window ->
            if (enabled) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

@Composable
fun SettingsScreenWithScreenshotProtection() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("lockEnabled", false)) }
    var screenshotProtectionEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "screenshotProtectionEnabled",
                true
            )
        )
    }
    var showBiometricInfoDialog by remember { mutableStateOf(false) }
    var biometricErrorMsg by remember { mutableStateOf("") }

    val bm = BiometricManager.from(context)
    val canAuth = bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
    val isBiometricAvailable = canAuth == BiometricManager.BIOMETRIC_SUCCESS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("⚙️ Einstellungen", color = TextP, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .border(1.dp, Surface3, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "App-Sperre",
                                color = TextP,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Biometrische Authentifizierung", color = TextS, fontSize = 12.sp)
                        }
                        Switch(
                            checked = lockEnabled,
                            onCheckedChange = { enabled ->
                                if (isBiometricAvailable || !enabled) {
                                    lockEnabled = enabled
                                    prefs.edit(commit = true) {
                                        putBoolean("lockEnabled", enabled)
                                        putBoolean("authenticated", !enabled)
                                    }
                                } else {
                                    biometricErrorMsg = when (canAuth) {
                                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                            "Keine Authentifizierung eingerichtet. Bitte richte einen Fingerabdruck oder PIN ein."

                                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                                            "Biometrische Hardware nicht verfügbar"

                                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                                            "Hardware temporär nicht verfügbar"

                                        else ->
                                            "Authentifizierung nicht verfügbar"
                                    }
                                    showBiometricInfoDialog = true
                                }
                            },
                            enabled = isBiometricAvailable || lockEnabled,
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                        )
                    }

                    HorizontalDivider(color = Surface3)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Screenshot-Schutz",
                                color = TextP,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Screenshots und Screen-Recording blockieren",
                                color = TextS,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = screenshotProtectionEnabled,
                            onCheckedChange = { enabled ->
                                screenshotProtectionEnabled = enabled
                                prefs.edit(commit = true) {
                                    putBoolean(
                                        "screenshotProtectionEnabled",
                                        enabled
                                    )
                                }
                                ScreenshotProtectionManager.setScreenshotProtection(
                                    activity,
                                    enabled
                                )
                                Toast.makeText(
                                    context,
                                    if (enabled) "Screenshots gesperrt" else "Screenshots erlaubt",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🔑 Autofill aktivieren", color = TextP)
            }
        }

        if (showBiometricInfoDialog) {
            AlertDialog(
                onDismissRequest = { showBiometricInfoDialog = false },
                containerColor = Surface1,
                title = { Text("Biometrie nicht verfügbar", color = TextP) },
                text = { Text(biometricErrorMsg, color = TextS) },
                confirmButton = {
                    TextButton(onClick = { showBiometricInfoDialog = false }) {
                        Text("OK", color = AccentBlue)
                    }
                }
            )
        }
    }
}

@Composable
fun SilentCaptureScreen(
    onDismiss: () -> Unit,
    onSecretScanned: ((secret: String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                DecoratedBarcodeView(ctx).apply {
                    viewFinder.visibility = View.GONE
                    setStatusText("")
                    decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            if (!isProcessing && result?.text != null) {
                                isProcessing = true
                                pause()
                                scope.launch {
                                    try {
                                        val decodedText = withContext(Dispatchers.IO) {
                                            URLDecoder.decode(result.text, "UTF-8")
                                        }
                                        val uri = decodedText.toUri()

                                        if (uri.scheme != "otpauth") {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "❌ Ungültiges Format!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                errorInsert(
                                                    "Capture Activity",
                                                    "❌ Ungültiges Format! ($uri)",
                                                    Instant.now().toString(),
                                                    "ERROR"

                                                )
                                                isProcessing = false; onDismiss()
                                            }
                                            return@launch
                                        }

                                        val label = uri.path?.removePrefix("/") ?: "Unbekannt"
                                        val secretParam = uri.getQueryParameter("secret")
                                        val issuerParam = uri.getQueryParameter("issuer")
                                        val displayName =
                                            issuerParam?.let { "$it ($label)" } ?: label

                                        if (secretParam.isNullOrBlank()) {
                                            Toast.makeText(
                                                context,
                                                "❌ Kein Secret gefunden!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            errorInsert(
                                                "Capture Activity",
                                                "❌ Kein Secret! ($uri)",
                                                Instant.now().toString(),
                                                "ERROR"

                                            )
                                            isProcessing = false; onDismiss()
                                            return@launch
                                        }

                                        // NEU: wenn Callback gesetzt → nur Secret zurückgeben, nicht speichern
                                        if (onSecretScanned != null) {
                                            withContext(Dispatchers.Main) {
                                                onSecretScanned(secretParam)
                                                // onDismiss wird vom Aufrufer durch den Callback ausgelöst
                                            }
                                            return@launch
                                        }

                                        // Altverhalten: direkt in DB speichern
                                        val db = TwoFADatabase.getDatabase(context)
                                        val existing = db.twoFADao().getAll()
                                        if (existing.any {
                                                it.secret == secretParam || it.name.equals(
                                                    displayName,
                                                    ignoreCase = true
                                                )
                                            }) {
                                            Toast.makeText(
                                                context,
                                                "⚠️ Eintrag existiert bereits!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false; onDismiss()
                                            return@launch
                                        }

                                        val newEntry =
                                            TwoFAEntry(name = displayName, secret = secretParam)
                                        val inserted = db.twoFADao().insertOrIgnore(newEntry)
                                        if (inserted == -1L) {
                                            Toast.makeText(
                                                context,
                                                "⚠️ Eintrag existiert bereits!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false; onDismiss()
                                            return@launch
                                        }

                                        val ok = saveTwoFaEntryToSupabase(newEntry, db)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (ok) "✅ $displayName hinzugefügt (lokal & Cloud)!" else "✅ $displayName hinzugefügt (Cloud fehlgeschlagen)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false; onDismiss()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "❌ Fehler: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        errorInsert(
                                            "Capture Activity",
                                            "❌ ${e.message}",
                                            Instant.now().toString(),
                                            "ERROR"

                                        )
                                        isProcessing = false; onDismiss()
                                    }
                                }
                            }
                        }

                        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
                    })
                    resume()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .border(3.dp, AccentBlue, RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
            )
            Spacer(Modifier.height(40.dp))
            Text(
                "Halte den QR-Code in den Rahmen",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}