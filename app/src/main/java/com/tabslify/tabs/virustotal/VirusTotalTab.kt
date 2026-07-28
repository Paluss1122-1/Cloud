package com.tabslify.tabs.virustotal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.Callout
import com.tabslify.core.ui.CalloutType
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

import androidx.compose.runtime.DisposableEffect

@Composable
fun VirusTotalTabContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val jobs by VirusTotalScanManager.jobs.collectAsState()

    DisposableEffect(Unit) {
        VirusTotalScanManager.vtTabVisible = true
        onDispose { VirusTotalScanManager.vtTabVisible = false }
    }

    var mode by rememberSaveable { mutableStateOf(VirusTotalMode.URL) }
    var input by rememberSaveable { mutableStateOf("") }
    var selectedFileUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = queryDisplayName(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.virustotal),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VirusTotalMode.entries.forEach { entry ->
                val selected = mode == entry
                Button(
                    onClick = {
                        mode = entry
                        input = ""
                        selectedFileUri = null
                        selectedFileName = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFF6B6BFF) else BgCard
                    )
                ) {
                    Text(
                        text = stringResource(entry.labelRes()),
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when (mode) {
            VirusTotalMode.URL -> {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.virustotal_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tabTextFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { VirusTotalScanManager.startUrl(context, input) },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.virustotal_prufen))
                }
            }

            VirusTotalMode.HASH -> {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.virustotal_hash_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tabTextFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { VirusTotalScanManager.startHash(context, input) },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.virustotal_prufen))
                }
            }

            VirusTotalMode.FILE -> {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedFileName ?: stringResource(R.string.virustotal_datei_auswahlen),
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val uri = selectedFileUri ?: return@Button
                        val name = selectedFileName ?: "file"
                        scope.launch {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null) VirusTotalScanManager.startFile(context, name, bytes)
                        }
                    },
                    enabled = selectedFileUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.virustotal_hochladen_prufen))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        val relevantJob = pendingVirusTotalReport?.let { id -> jobs.find { it.id == id } }
            ?: jobs.lastOrNull { it.mode == mode }

        androidx.compose.runtime.LaunchedEffect(relevantJob) {
            if (pendingVirusTotalReport != null && relevantJob != null && relevantJob.id == pendingVirusTotalReport) {
                pendingVirusTotalReport = null
            }
        }

        when (val current = relevantJob?.state ?: VirusTotalState.Idle) {
            VirusTotalState.Idle -> {}

            VirusTotalState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6B6BFF))
                }
            }

            is VirusTotalState.Error -> {
                Callout(type = CalloutType.DANGER, content = {
                    Text(current.message, color = TextPrimary)
                })
            }

            is VirusTotalState.Result -> {
                VirusTotalResultCard(current.stats, current.permalink)
            }
        }
    }
}

@Composable
private fun VirusTotalResultCard(stats: VirusTotalStats, permalink: String) {
    val calloutType = when {
        stats.malicious > 0 -> CalloutType.DANGER
        stats.suspicious > 0 -> CalloutType.WARNING
        else -> CalloutType.SUCCESS
    }
    val context = LocalContext.current

    Callout(type = calloutType, content = {
        Column {
            Text(
                text = stringResource(
                    R.string.virustotal_ergebnis,
                    stats.malicious,
                    stats.total
                ),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    })

    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            VirusTotalStatRow(stringResource(R.string.virustotal_bosartig), stats.malicious)
            VirusTotalStatRow(stringResource(R.string.virustotal_verdachtig), stats.suspicious)
            VirusTotalStatRow(stringResource(R.string.virustotal_harmlos), stats.harmless)
            VirusTotalStatRow(stringResource(R.string.virustotal_unentdeckt), stats.undetected)
        }
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(permalink))
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.virustotal_bericht_offnen))
    }
}

@Composable
private fun VirusTotalStatRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value.toString(), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun tabTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF6B6BFF),
    unfocusedBorderColor = Color(0xFF44444F),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFF6B6BFF)
)

private fun VirusTotalMode.labelRes(): Int = when (this) {
    VirusTotalMode.URL -> R.string.virustotal_modus_url
    VirusTotalMode.HASH -> R.string.virustotal_modus_hash
    VirusTotalMode.FILE -> R.string.virustotal_modus_datei
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.lastPathSegment
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull()
}
