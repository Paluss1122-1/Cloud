package com.tabslify.core.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tabslify.apkm.ApkEntryInfo
import com.tabslify.apkm.ApkKind
import com.tabslify.apkm.ApkmInstaller
import com.tabslify.apkm.ApkmInstaller.Companion.humanSize
import com.tabslify.apkm.ApkmPackage
import com.tabslify.apkm.ApkmParseException
import com.tabslify.apkm.InstallOutcome
import com.tabslify.apkm.ParseError
import com.tabslify.apkm.SignatureState
import com.tabslify.core.ui.NeonBox
import com.tabslify.core.ui.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI-Phasen des Installers. */
private enum class Phase { LOADING, ERROR, READY, INSTALLING, SUCCESS, FAILURE }

class ApkmInstallActivity : ComponentActivity() {

    private lateinit var installer: ApkmInstaller

    // --- Reaktiver UI-State ---
    private var phase by mutableStateOf(Phase.LOADING)
    private var loadText by mutableStateOf("Bundle wird vorbereitet…")
    private var loadProgress by mutableStateOf<Float?>(null)
    private var pkg by mutableStateOf<ApkmPackage?>(null)
    private var errorType by mutableStateOf<ParseError?>(null)
    private var errorMessage by mutableStateOf("")
    private var installStatusText by mutableStateOf("")
    private var failure by mutableStateOf<InstallOutcome.Failure?>(null)
    private var needsInstallPermission by mutableStateOf(false)
    private var signatureAcknowledged by mutableStateOf(false)
    private val selection = snapshotStateMapOf<String, Boolean>()

    private val unknownSourcesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            recomputePermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installer = ApkmInstaller(applicationContext)

        val uri = resolveUri(intent)
        if (uri == null) {
            Toast.makeText(this, "Keine Datei erhalten.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MaterialTheme(typography = Typography) {
                ApkmInstallScreen(
                    installer = installer,
                    phase = phase,
                    loadText = loadText,
                    loadProgress = loadProgress,
                    pkg = pkg,
                    errorType = errorType,
                    errorMessage = errorMessage,
                    installStatusText = installStatusText,
                    failure = failure,
                    needsInstallPermission = needsInstallPermission,
                    signatureAcknowledged = signatureAcknowledged,
                    selection = selection,
                    onToggleApk = { entry, checked -> selection[entry.entryName] = checked },
                    onAcknowledgeSignature = { signatureAcknowledged = true },
                    onRequestPermission = ::requestInstallPermission,
                    onInstall = ::startInstall,
                    onRetry = { finish() },
                    onClose = { finish() },
                    onDone = { finish() }
                )
            }
        }

        loadBundle(uri)
    }

    override fun onResume() {
        super.onResume()
        recomputePermission()
    }

    private fun resolveUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> intent.data
        }
    }

    private fun loadBundle(uri: Uri) {
        val sourceName = uri.lastPathSegment ?: "bundle.apkm"
        lifecycleScope.launch {
            try {
                phase = Phase.LOADING
                loadText = "Bundle wird kopiert…"
                val cacheFile = withContext(Dispatchers.IO) {
                    installer.copyToCache(uri) { copied, total ->
                        loadProgress = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null
                    }
                }
                loadText = "Bundle wird analysiert…"
                loadProgress = null
                val parsed = withContext(Dispatchers.IO) { installer.parse(cacheFile, sourceName) }
                pkg = parsed
                // Standardauswahl = empfohlene APKs
                selection.clear()
                parsed.apks.forEach { selection[it.entryName] = it.recommended || it.kind == ApkKind.BASE }
                recomputePermission()
                phase = Phase.READY
            } catch (e: ApkmParseException) {
                errorType = e.error
                errorMessage = e.message ?: "Unbekannter Fehler"
                installer.log("❌ Parsen fehlgeschlagen: ${e.message}")
                phase = Phase.ERROR
            } catch (e: Exception) {
                errorType = ParseError.UNREADABLE
                errorMessage = e.message ?: "Unbekannter Fehler"
                installer.log("❌ Unerwarteter Fehler: ${e.message}")
                phase = Phase.ERROR
            }
        }
    }

    private fun recomputePermission() {
        needsInstallPermission = !installer.canRequestInstalls()
    }

    private fun requestInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
        runCatching { unknownSourcesLauncher.launch(intent) }
            .onFailure {
                // Fallback: generische Seite
                unknownSourcesLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
            }
    }

    private fun startInstall() {
        val current = pkg ?: return
        val selected = current.apks.filter { selection[it.entryName] == true }
        if (selected.none { it.kind == ApkKind.BASE }) {
            Toast.makeText(this, "Basis-APK ist erforderlich.", Toast.LENGTH_LONG).show()
            return
        }
        phase = Phase.INSTALLING
        installStatusText = "Installation wird vorbereitet…"
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                installer.install(current, selected) { confirmIntent ->
                    installStatusText = "Bitte im System-Dialog bestätigen…"
                    runCatching { startActivity(confirmIntent) }
                }
            }
            when (outcome) {
                is InstallOutcome.Success -> phase = Phase.SUCCESS
                is InstallOutcome.Failure -> {
                    failure = outcome
                    phase = Phase.FAILURE
                }
            }
        }
    }
}

// =================================================================================================
// UI
// =================================================================================================

private val AccentCyan = Color(0xFF00E5FF)
private val AccentViolet = Color(0xFF7C4DFF)
private val AccentOrange = Color(0xFFE8622A)
private val AccentGreen = Color(0xFF37D67A)
private val AccentRed = Color(0xFFFF5252)
private val AccentAmber = Color(0xFFFFB300)

@Composable
private fun ApkmInstallScreen(
    installer: ApkmInstaller,
    phase: Phase,
    loadText: String,
    loadProgress: Float?,
    pkg: ApkmPackage?,
    errorType: ParseError?,
    errorMessage: String,
    installStatusText: String,
    failure: InstallOutcome.Failure?,
    needsInstallPermission: Boolean,
    signatureAcknowledged: Boolean,
    selection: Map<String, Boolean>,
    onToggleApk: (ApkEntryInfo, Boolean) -> Unit,
    onAcknowledgeSignature: () -> Unit,
    onRequestPermission: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20B0B10))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Kopfzeile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "APKM Installer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Split-Bundle Installation",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                }
            }

            Spacer(Modifier.height(20.dp))

            when (phase) {
                Phase.LOADING -> LoadingContent(loadText, loadProgress)
                Phase.ERROR -> ErrorContent(errorType, errorMessage, installer, onRetry)
                Phase.READY -> if (pkg != null) {
                    ReadyContent(
                        installer, pkg, selection, needsInstallPermission, signatureAcknowledged,
                        onToggleApk, onAcknowledgeSignature, onRequestPermission, onInstall
                    )
                }

                Phase.INSTALLING -> InstallingContent(installStatusText, installer)
                Phase.SUCCESS -> ResultContent(true, pkg, null, installer, onDone)
                Phase.FAILURE -> ResultContent(false, pkg, failure, installer, onDone)
            }
        }
    }
}

@Composable
private fun LoadingContent(text: String, progress: Float?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (progress != null) {
            val p = progress
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .clip(RoundedCornerShape(4.dp)),
                color = AccentCyan,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Spacer(Modifier.height(8.dp))
            Text("${(progress * 100).toInt()} %", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorContent(
    type: ParseError?,
    message: String,
    installer: ApkmInstaller,
    onRetry: () -> Unit
) {
    val title = when (type) {
        ParseError.CORRUPTED -> "🧩 Datei beschädigt"
        ParseError.NO_APKS -> "📭 Kein gültiges Bundle"
        ParseError.UNREADABLE, null -> "⚠️ Datei nicht lesbar"
    }
    Column(modifier = Modifier.fillMaxSize()) {
        NeonBox(
            modifier = Modifier.fillMaxWidth(),
            neonColors = listOf(AccentRed, AccentOrange),
            backgroundAlpha = 0.18f
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(message, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        ExpandableNeonSection("Logs", "Technische Details", listOf(AccentViolet, AccentCyan)) {
            LogView(installer)
        }
        Spacer(Modifier.weight(1f))
        GlowButton("Schließen", listOf(AccentRed, AccentOrange), onClick = onRetry)
    }
}

@Composable
private fun ReadyContent(
    installer: ApkmInstaller,
    pkg: ApkmPackage,
    selection: Map<String, Boolean>,
    needsInstallPermission: Boolean,
    signatureAcknowledged: Boolean,
    onToggleApk: (ApkEntryInfo, Boolean) -> Unit,
    onAcknowledgeSignature: () -> Unit,
    onRequestPermission: () -> Unit,
    onInstall: () -> Unit
) {
    val selectedApks = pkg.apks.filter { selection[it.entryName] == true }
    val selectedSize = selectedApks.sumOf { it.size }
    val mismatch = pkg.signatureState == SignatureState.MISMATCH

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // --- App-Kopf ---
            AppHeaderCard(pkg, selectedSize, selectedApks.size)

            Spacer(Modifier.height(16.dp))

            // --- Signaturwarnung (nur bei Konflikt) ---
            if (mismatch) {
                SignatureWarningCard(pkg, signatureAcknowledged, onAcknowledgeSignature)
                Spacer(Modifier.height(16.dp))
            }

            // --- Berechtigung ---
            if (needsInstallPermission) {
                PermissionCard(onRequestPermission)
                Spacer(Modifier.height(16.dp))
            }

            // --- Nerd-Sektionen ---
            ExpandableNeonSection(
                "Enthaltene APKs",
                "${pkg.apks.size} Dateien · ${selectedApks.size} ausgewählt",
                listOf(AccentCyan, AccentViolet),
                initiallyExpanded = false
            ) {
                ApkSelectionList(pkg, selection, onToggleApk)
            }

            Spacer(Modifier.height(12.dp))

            ExpandableNeonSection(
                "Signatur & Version",
                signatureLabel(pkg.signatureState),
                listOf(if (mismatch) AccentRed else AccentGreen, AccentCyan)
            ) {
                SignatureDetails(pkg)
            }

            Spacer(Modifier.height(12.dp))

            ExpandableNeonSection(
                "Geräteabgleich",
                "ABIs · Sprachen · Auflösung",
                listOf(AccentViolet, AccentOrange)
            ) {
                DeviceMatchDetails(pkg)
            }

            if (pkg.infoJsonRaw != null) {
                Spacer(Modifier.height(12.dp))
                ExpandableNeonSection("info.json", "Rohe Bundle-Metadaten", listOf(AccentOrange, AccentAmber)) {
                    MonospaceBlock(pkg.infoJsonRaw)
                }
            }

            Spacer(Modifier.height(12.dp))

            ExpandableNeonSection("Logs (live)", "${installer.logs.size} Einträge", listOf(AccentViolet, AccentCyan)) {
                LogView(installer)
            }

            Spacer(Modifier.height(16.dp))
        }

        // --- Installations-Button ---
        val installEnabled = !needsInstallPermission &&
            selectedApks.any { it.kind == ApkKind.BASE } &&
            (!mismatch || signatureAcknowledged)

        val (btnLabel, btnColors) = when {
            needsInstallPermission -> "Erst Quelle erlauben" to listOf(Color.Gray, Color.DarkGray)
            mismatch && !signatureAcknowledged -> "Signaturrisiko bestätigen" to listOf(Color.Gray, Color.DarkGray)
            mismatch -> "Trotz Signaturkonflikt installieren" to listOf(AccentRed, AccentOrange)
            else -> "Installieren (${humanSize(selectedSize)})" to listOf(AccentGreen, AccentCyan)
        }

        GlowButton(
            text = btnLabel,
            neonColors = btnColors,
            enabled = installEnabled,
            pulsing = installEnabled && !mismatch,
            onClick = onInstall
        )
    }
}

@Composable
private fun AppHeaderCard(pkg: ApkmPackage, selectedSize: Long, selectedCount: Int) {
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        neonColors = listOf(AccentCyan, AccentViolet),
        backgroundAlpha = 0.16f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(pkg.icon, pkg.appName)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pkg.appName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    pkg.packageName,
                    color = AccentCyan.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                val installedCode = pkg.installedVersionCode
                val versionLine = buildString {
                    append("v${pkg.versionName ?: "?"} (${pkg.versionCode})")
                    if (installedCode != null) {
                        val arrow = when {
                            pkg.versionCode > installedCode -> "  ⬆ Update"
                            pkg.versionCode < installedCode -> "  ⬇ Downgrade"
                            else -> "  ↺ Neuinstallation"
                        }
                        append(arrow)
                    } else {
                        append("  ✨ Neu")
                    }
                }
                Text(versionLine, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$selectedCount APKs · ${humanSize(selectedSize)}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, name: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = name, modifier = Modifier.size(56.dp))
        } else {
            Text(name.take(1).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SignatureWarningCard(
    pkg: ApkmPackage,
    acknowledged: Boolean,
    onAcknowledge: () -> Unit
) {
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        neonColors = listOf(AccentRed, AccentAmber),
        backgroundAlpha = 0.2f
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("⚠️ Signaturkonflikt", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Die installierte App \"${pkg.packageName}\" wurde mit einem anderen Zertifikat signiert. " +
                    "Android verhindert das Update. Zum Fortfahren müsste die vorhandene App zuerst " +
                    "deinstalliert werden – dabei gehen ihre Daten verloren.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = acknowledged,
                    onCheckedChange = { if (it) onAcknowledge() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentRed,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Ich verstehe das Risiko und will fortfahren",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        neonColors = listOf(AccentAmber, AccentOrange),
        backgroundAlpha = 0.18f
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("🔓 Berechtigung benötigt", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Damit Tabslify Apps installieren darf, muss \"Apps aus dieser Quelle installieren\" aktiviert werden.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            GlowButton("Installationsquelle erlauben", listOf(AccentAmber, AccentOrange), onClick = onRequest)
        }
    }
}

@Composable
private fun ApkSelectionList(
    pkg: ApkmPackage,
    selection: Map<String, Boolean>,
    onToggle: (ApkEntryInfo, Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val grouped = pkg.apks.groupBy { it.kind }
        ApkKind.entries.forEach { kind ->
            val entries = grouped[kind] ?: return@forEach
            Text(
                kindLabel(kind),
                color = AccentCyan.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            entries.forEach { apk ->
                val checked = selection[apk.entryName] == true
                val isBase = apk.kind == ApkKind.BASE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(apk.fileName, color = Color.White, fontSize = 13.sp)
                            if (apk.recommended && !isBase) {
                                Spacer(Modifier.width(6.dp))
                                Tag("empfohlen", AccentGreen)
                            }
                            if (isBase) {
                                Spacer(Modifier.width(6.dp))
                                Tag("erforderlich", AccentAmber)
                            }
                        }
                        Text(
                            humanSize(apk.size),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = checked,
                        enabled = !isBase,
                        onCheckedChange = { onToggle(apk, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray,
                            disabledCheckedTrackColor = AccentCyan.copy(alpha = 0.5f),
                            disabledCheckedThumbColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureDetails(pkg: ApkmPackage) {
    Column(modifier = Modifier.padding(16.dp)) {
        InfoRow("Signaturstatus", signatureLabel(pkg.signatureState))
        InfoRow("Neue Version", "v${pkg.versionName ?: "?"} (${pkg.versionCode})")
        if (pkg.installedVersionName != null) {
            InfoRow("Installiert", "v${pkg.installedVersionName} (${pkg.installedVersionCode})")
        } else {
            InfoRow("Installiert", "— (Neuinstallation)")
        }
        InfoRow("Min. SDK", pkg.minSdk?.toString() ?: "?")
        InfoRow("Target SDK", pkg.targetSdk?.toString() ?: "?")
        InfoRow("Gesamtgröße", humanSize(pkg.totalSize))
    }
}

@Composable
private fun DeviceMatchDetails(pkg: ApkmPackage) {
    Column(modifier = Modifier.padding(16.dp)) {
        InfoRow("Gerät ABIs", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
        InfoRow("Bundle ABIs", pkg.supportedAbis.ifEmpty { listOf("universal") }.joinToString(", "))
        InfoRow("Bundle Sprachen", pkg.supportedLanguages.ifEmpty { listOf("—") }.joinToString(", "))
        InfoRow("Bundle Auflösungen", pkg.supportedDensities.ifEmpty { listOf("—") }.joinToString(", "))
    }
}

@Composable
private fun InstallingContent(status: String, installer: ApkmInstaller) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text(status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        NeonBox(
            modifier = Modifier.fillMaxWidth(),
            neonColors = listOf(AccentViolet, AccentCyan),
            backgroundAlpha = 0.12f
        ) {
            Box(modifier = Modifier.padding(12.dp)) { LogView(installer) }
        }
    }
}

@Composable
private fun ResultContent(
    success: Boolean,
    pkg: ApkmPackage?,
    failure: InstallOutcome.Failure?,
    installer: ApkmInstaller,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NeonBox(
            modifier = Modifier.fillMaxWidth(),
            neonColors = if (success) listOf(AccentGreen, AccentCyan) else listOf(AccentRed, AccentOrange),
            backgroundAlpha = 0.18f
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (success) "✅" else "❌", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (success) "Installation erfolgreich" else "Installation fehlgeschlagen",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (pkg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(pkg.appName, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
                if (!success && failure != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        friendlyFailure(failure),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "↩ Es wurde nichts teilweise installiert (Rollback).",
                        color = AccentAmber,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ExpandableNeonSection(
            "Logs",
            "${installer.logs.size} Einträge",
            listOf(AccentViolet, AccentCyan),
            initiallyExpanded = !success
        ) {
            LogView(installer)
        }
        Spacer(Modifier.weight(1f))
        GlowButton(
            if (success) "Fertig" else "Schließen",
            if (success) listOf(AccentGreen, AccentCyan) else listOf(AccentRed, AccentOrange),
            onClick = onDone
        )
    }
}

// --- Wiederverwendbare Bausteine -----------------------------------------------------------------

@Composable
private fun ExpandableNeonSection(
    title: String,
    subtitle: String,
    neonColors: List<Color>,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        neonColors = neonColors,
        backgroundAlpha = 0.13f
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                Text(
                    if (expanded) "▲" else "▼",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f))
                        .animateContentSize()
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LogView(installer: ApkmInstaller) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.padding(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            if (installer.logs.isEmpty()) {
                Text("Keine Logs.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            } else {
                Column {
                    installer.logs.forEach { line ->
                        Text(
                            line,
                            color = Color(0xFF9BE7A0),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "📋 Logs kopieren",
            color = AccentCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { clipboard.setText(AnnotatedString(installer.logs.joinToString("\n"))) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MonospaceBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GlowButton(
    text: String,
    neonColors: List<Color>,
    enabled: Boolean = true,
    pulsing: Boolean = false,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val glow by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glow"
    )
    val effectiveAlpha = if (pulsing) glow else 1f

    NeonBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        cornerRadius = RoundedCornerShape(16.dp),
        neonColors = if (enabled) neonColors.map { it.copy(alpha = effectiveAlpha) } else neonColors,
        backgroundAlpha = if (enabled) 0.35f else 0.12f,
        onClick = if (enabled) onClick else null
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// --- Text-Helfer ---------------------------------------------------------------------------------

private fun kindLabel(kind: ApkKind): String = when (kind) {
    ApkKind.BASE -> "Basis-APK"
    ApkKind.ARCH -> "Architektur (ABI)"
    ApkKind.DENSITY -> "Bildschirmauflösung"
    ApkKind.LANGUAGE -> "Sprachen"
    ApkKind.FEATURE -> "Feature-Module"
    ApkKind.OTHER -> "Sonstige"
}

private fun signatureLabel(state: SignatureState): String = when (state) {
    SignatureState.NOT_INSTALLED -> "Neuinstallation (keine Kollision)"
    SignatureState.MATCH -> "✓ Signatur passt zur installierten App"
    SignatureState.MISMATCH -> "✗ Signaturkonflikt!"
    SignatureState.UNKNOWN -> "Unbekannt"
}

private fun friendlyFailure(failure: InstallOutcome.Failure): String {
    if (failure.isSignatureConflict) {
        return "Signaturkonflikt: Die vorhandene App wurde mit einem anderen Zertifikat signiert. " +
            "Deinstalliere sie zuerst und versuche es erneut."
    }
    val base = failure.message?.takeIf { it.isNotBlank() } ?: "Unbekannter Fehler"
    return "Fehler: $base"
}
