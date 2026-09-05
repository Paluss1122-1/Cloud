package com.tabslify.tabs

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tabslify.R
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.compose.foundation.Image as ImageComposable

var pendingApkmUri: Uri? by mutableStateOf(null)

private enum class ApkmPhase { LOADING, ERROR, READY, INSTALLING, SUCCESS, FAILURE }

private val AccentCyan = Color(0xFF00E5FF)
private val AccentViolet = Color(0xFF7C4DFF)
private val AccentOrange = Color(0xFFE8622A)
private val AccentGreen = Color(0xFF37D67A)
private val AccentRed = Color(0xFFFF5252)
private val AccentAmber = Color(0xFFFFB300)

@Composable
fun ApkmInstallerTabContent(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    
    val bundleWirdVorbereitetMsg = stringResource(R.string.bundle_wird_vorbereitet)
    val bundleWirdKopiertMsg = stringResource(R.string.bundle_wird_kopiert)
    val bundleWirdAnalysiertMsg = stringResource(R.string.bundle_wird_analysiert)
    val unbekannterFehlerMsg = stringResource(R.string.unbekannter_fehler)
    val basisApkIstErforderlichMsg = stringResource(R.string.basis_apk_ist_erforderlich)
    val installationWirdVorbereitetMsg = stringResource(R.string.installation_wird_vorbereitet)
    val bitteBestaetiigenMsg = stringResource(R.string.bitte_im_system_dialog_bestatigen)
    
    val scope = rememberCoroutineScope()
    val installer = remember(uri) { ApkmInstaller(context.applicationContext) }

    var phase by remember(uri) { mutableStateOf(ApkmPhase.LOADING) }
    var loadText by remember(uri) { mutableStateOf(bundleWirdVorbereitetMsg) }
    var loadProgress by remember(uri) { mutableStateOf<Float?>(null) }
    var pkg by remember(uri) { mutableStateOf<ApkmPackage?>(null) }
    var errorType by remember(uri) { mutableStateOf<ParseError?>(null) }
    var errorMessage by remember(uri) { mutableStateOf("") }
    var installStatusText by remember(uri) { mutableStateOf("") }
    var failure by remember(uri) { mutableStateOf<InstallOutcome.Failure?>(null) }
    var needsInstallPermission by remember(uri) { mutableStateOf(false) }
    var signatureAcknowledged by remember(uri) { mutableStateOf(false) }
    val selection = remember(uri) { mutableStateMapOf<String, Boolean>() }

    DisposableEffect(Unit) {
        onDispose { pendingApkmUri = null }
    }

    fun recomputePermission() {
        needsInstallPermission = !installer.canRequestInstalls()
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { recomputePermission() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recomputePermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uri) {
        val sourceName = uri.lastPathSegment ?: "bundle.apkm"
        try {
            phase = ApkmPhase.LOADING
            loadText = bundleWirdKopiertMsg
            val cacheFile = withContext(Dispatchers.IO) {
                installer.copyToCache(uri) { copied, total ->
                    loadProgress = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null
                }
            }
            loadText = bundleWirdAnalysiertMsg
            loadProgress = null
            val parsed = withContext(Dispatchers.IO) { installer.parse(cacheFile, sourceName) }
            pkg = parsed
            selection.clear()
            parsed.apks.forEach { selection[it.entryName] = it.recommended || it.kind == ApkKind.BASE }
            recomputePermission()
            phase = ApkmPhase.READY
        } catch (e: ApkmParseException) {
            errorType = e.error
            errorMessage = e.message ?: unbekannterFehlerMsg
            installer.log("❌ Parsen fehlgeschlagen: ${e.message}")
            phase = ApkmPhase.ERROR
        } catch (e: Exception) {
            errorType = ParseError.UNREADABLE
            errorMessage = e.message ?: unbekannterFehlerMsg
            installer.log("❌ Unerwarteter Fehler: ${e.message}")
            phase = ApkmPhase.ERROR
        }
    }

    fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        )
        runCatching { unknownSourcesLauncher.launch(intent) }
            .onFailure {
                unknownSourcesLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
            }
    }

    fun startInstall() {
        val current = pkg ?: return
        val selected = current.apks.filter { selection[it.entryName] == true }
        if (selected.none { it.kind == ApkKind.BASE }) {
            Toast.makeText(context, basisApkIstErforderlichMsg, Toast.LENGTH_LONG).show()
            return
        }
        phase = ApkmPhase.INSTALLING
        installStatusText = installationWirdVorbereitetMsg
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    withTimeout(10 * 60_000L) {
                        installer.install(current, selected) { confirmIntent ->
                            installStatusText = bitteBestaetiigenMsg
                            runCatching { context.startActivity(confirmIntent) }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    installer.log("❌ Timeout: Kein Installationsstatus empfangen (10 Min).")
                    InstallOutcome.Failure(Int.MIN_VALUE, "Timeout: Kein Installationsstatus empfangen", false)
                }
            }
            when (outcome) {
                is InstallOutcome.Success -> phase = ApkmPhase.SUCCESS
                is InstallOutcome.Failure -> {
                    failure = outcome
                    phase = ApkmPhase.FAILURE
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20B0B10))
            .padding(16.dp)
    ) {
        when (phase) {
            ApkmPhase.LOADING -> LoadingContent(loadText, loadProgress)
            ApkmPhase.ERROR -> ErrorContent(errorType, errorMessage, installer, onRetry = onDone)
            ApkmPhase.READY -> pkg?.let {
                ReadyContent(
                    installer = installer,
                    pkg = it,
                    selection = selection,
                    needsInstallPermission = needsInstallPermission,
                    signatureAcknowledged = signatureAcknowledged,
                    onToggleApk = { entry, checked -> selection[entry.entryName] = checked },
                    onAcknowledgeSignature = { signatureAcknowledged = true },
                    onRequestPermission = { requestInstallPermission() },
                    onInstall = { startInstall() }
                )
            }

            ApkmPhase.INSTALLING -> InstallingContent(installStatusText, installer)
            ApkmPhase.SUCCESS -> ResultContent(true, pkg, null, installer, onDone)
            ApkmPhase.FAILURE -> ResultContent(false, pkg, failure, installer, onDone)
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
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
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
        ParseError.CORRUPTED -> stringResource(R.string.datei_beschadigt)
        ParseError.NO_APKS -> stringResource(R.string.kein_gultiges_bundle)
        ParseError.UNREADABLE, null -> stringResource(R.string.datei_nicht_lesbar)
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
        ExpandableNeonSection(stringResource(R.string.logs), stringResource(R.string.technische_details), listOf(AccentViolet, AccentCyan)) {
            LogView(installer)
        }
        Spacer(Modifier.weight(1f))
        GlowButton(stringResource(R.string.schliesen), listOf(AccentRed, AccentOrange), onClick = onRetry)
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
            AppHeaderCard(pkg, selectedSize, selectedApks.size)

            Spacer(Modifier.height(16.dp))

            if (mismatch) {
                SignatureWarningCard(pkg, signatureAcknowledged, onAcknowledgeSignature)
                Spacer(Modifier.height(16.dp))
            }

            if (needsInstallPermission) {
                PermissionCard(onRequestPermission)
                Spacer(Modifier.height(16.dp))
            }

            ExpandableNeonSection(
                stringResource(R.string.enthaltene_apks),
                stringResource(R.string.dateien_ausgewahlt, pkg.apks.size, selectedApks.size),
                listOf(AccentCyan, AccentViolet),
                initiallyExpanded = false
            ) {
                ApkSelectionList(pkg, selection, onToggleApk)
            }

            Spacer(Modifier.height(12.dp))

            ExpandableNeonSection(
                stringResource(R.string.signatur_version),
                signatureLabel(pkg.signatureState),
                listOf(if (mismatch) AccentRed else AccentGreen, AccentCyan)
            ) {
                SignatureDetails(pkg)
            }

            Spacer(Modifier.height(12.dp))

            ExpandableNeonSection(
                stringResource(R.string.gerateabgleich),
                stringResource(R.string.abis_sprachen_auflosung),
                listOf(AccentViolet, AccentOrange)
            ) {
                DeviceMatchDetails(pkg)
            }

            if (pkg.infoJsonRaw != null) {
                Spacer(Modifier.height(12.dp))
                ExpandableNeonSection("info.json", stringResource(R.string.rohe_bundle_metadaten), listOf(AccentOrange, AccentAmber)) {
                    MonospaceBlock(pkg.infoJsonRaw)
                }
            }

            Spacer(Modifier.height(12.dp))

            ExpandableNeonSection(stringResource(R.string.logs_live), pluralStringResource(R.plurals.eintrage, installer.logs.size, installer.logs.size), listOf(AccentViolet, AccentCyan)) {
                LogView(installer)
            }

            Spacer(Modifier.height(16.dp))
        }

        val installEnabled = !needsInstallPermission &&
            selectedApks.any { it.kind == ApkKind.BASE } &&
            (!mismatch || signatureAcknowledged)

        val (btnLabel, btnColors) = when {
            needsInstallPermission -> stringResource(R.string.erst_quelle_erlauben) to listOf(Color.Gray, Color.DarkGray)
            mismatch && !signatureAcknowledged -> stringResource(R.string.signaturrisiko_bestatigen) to listOf(Color.Gray, Color.DarkGray)
            mismatch -> stringResource(R.string.trotz_signaturkonflikt_installieren) to listOf(AccentRed, AccentOrange)
            else -> stringResource(R.string.installieren, humanSize(selectedSize)) to listOf(AccentGreen, AccentCyan)
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
                            pkg.versionCode > installedCode -> stringResource(R.string.update)
                            pkg.versionCode < installedCode -> stringResource(R.string.downgrade)
                            else -> stringResource(R.string.neuinstallation)
                        }
                        append(arrow)
                    } else {
                        append(stringResource(R.string.neu_2))
                    }
                }
                Text(versionLine, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.apks, selectedCount, humanSize(selectedSize)),
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
            ImageComposable(bitmap = icon, contentDescription = name, modifier = Modifier.size(56.dp))
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
            Text(stringResource(R.string.signaturkonflikt), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.signaturkonflikt_text, pkg.packageName),
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
                    stringResource(R.string.ich_verstehe_das_risiko_und),
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
            Text(stringResource(R.string.berechtigung_benotigt), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.damit_tabslify_apps_installieren_darf),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            GlowButton(stringResource(R.string.installationsquelle_erlauben), listOf(AccentAmber, AccentOrange), onClick = onRequest)
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
                                Tag(stringResource(R.string.empfohlen), AccentGreen)
                            }
                            if (isBase) {
                                Spacer(Modifier.width(6.dp))
                                Tag(stringResource(R.string.erforderlich), AccentAmber)
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
        InfoRow(stringResource(R.string.signaturstatus), signatureLabel(pkg.signatureState))
        InfoRow(stringResource(R.string.neue_version), "v${pkg.versionName ?: "?"} (${pkg.versionCode})")
        if (pkg.installedVersionName != null) {
            InfoRow(stringResource(R.string.installiert), "v${pkg.installedVersionName} (${pkg.installedVersionCode})")
        } else {
            InfoRow(stringResource(R.string.installiert), stringResource(R.string.neuinstallation_2))
        }
        InfoRow(stringResource(R.string.min_sdk), pkg.minSdk?.toString() ?: "?")
        InfoRow(stringResource(R.string.target_sdk), pkg.targetSdk?.toString() ?: "?")
        InfoRow(stringResource(R.string.gesamtgrose), humanSize(pkg.totalSize))
    }
}

@Composable
private fun DeviceMatchDetails(pkg: ApkmPackage) {
    Column(modifier = Modifier.padding(16.dp)) {
        InfoRow(stringResource(R.string.gerat_abis), android.os.Build.SUPPORTED_ABIS.joinToString(", "))
        InfoRow(stringResource(R.string.bundle_abis), pkg.supportedAbis.ifEmpty { listOf("universal") }.joinToString(", "))
        InfoRow(stringResource(R.string.bundle_sprachen), pkg.supportedLanguages.ifEmpty { listOf("—") }.joinToString(", "))
        InfoRow(stringResource(R.string.bundle_auflosungen), pkg.supportedDensities.ifEmpty { listOf("—") }.joinToString(", "))
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
                    if (success) stringResource(R.string.installation_erfolgreich) else stringResource(R.string.installation_fehlgeschlagen),
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
                        stringResource(R.string.es_wurde_nichts_teilweise_installiert),
                        color = AccentAmber,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ExpandableNeonSection(
            stringResource(R.string.logs),
            pluralStringResource(R.plurals.eintrage, installer.logs.size, installer.logs.size),
            listOf(AccentViolet, AccentCyan),
            initiallyExpanded = !success
        ) {
            LogView(installer)
        }
        Spacer(Modifier.weight(1f))
        GlowButton(
            if (success) stringResource(R.string.fertig_2) else stringResource(R.string.schliesen),
            if (success) listOf(AccentGreen, AccentCyan) else listOf(AccentRed, AccentOrange),
            onClick = onDone
        )
    }
}

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
                Text(stringResource(R.string.keine_logs), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
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
            stringResource(R.string.logs_kopieren),
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

@Composable
private fun kindLabel(kind: ApkKind): String = when (kind) {
    ApkKind.BASE -> stringResource(R.string.basis_apk)
    ApkKind.ARCH -> stringResource(R.string.architektur_abi)
    ApkKind.DENSITY -> stringResource(R.string.bildschirmauflosung)
    ApkKind.LANGUAGE -> stringResource(R.string.sprachen)
    ApkKind.FEATURE -> stringResource(R.string.feature_module)
    ApkKind.OTHER -> stringResource(R.string.sonstige)
}

@Composable
private fun signatureLabel(state: SignatureState): String = when (state) {
    SignatureState.NOT_INSTALLED -> stringResource(R.string.neuinstallation_keine_kollision)
    SignatureState.MATCH -> stringResource(R.string.signatur_passt_zur_installierten_app)
    SignatureState.MISMATCH -> stringResource(R.string.signaturkonflikt_2)
    SignatureState.UNKNOWN -> stringResource(R.string.unbekannt)
}

@Composable
private fun friendlyFailure(failure: InstallOutcome.Failure): String {
    if (failure.isSignatureConflict) {
        return stringResource(R.string.signaturkonflikt_failure)
    }
    val base = failure.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unbekannter_fehler)
    return stringResource(R.string.fehler_msg, base)
}
