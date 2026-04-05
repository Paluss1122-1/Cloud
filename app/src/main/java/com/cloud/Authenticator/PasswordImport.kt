package com.cloud.authenticator

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONObject

private val S1 = Color(0xFF17171C)
private val S3 = Color(0xFF2A2A35)
private val AB = Color(0xFF4A90E2)
private val TP = Color(0xFFEEEEF5)
private val TS = Color(0xFF8A8A9F)

data class ImportResult(
    val passwords: Int,
    val updated: Int,
    val totp: Int,
    val skipped: Int,
    val removed: List<PasswordEntry> = emptyList()
)

fun parseBitwardenExport(json: String): Triple<List<PasswordEntry>, List<TwoFAEntry>, Int> {
    val passwords = mutableListOf<PasswordEntry>()
    val totpEntries = mutableListOf<TwoFAEntry>()
    var skipped = 0

    val root = JSONObject(json)
    val items = root.optJSONArray("items") ?: return Triple(emptyList(), emptyList(), 0)

    for (i in 0 until items.length()) {
        val item = items.getJSONObject(i)
        if (item.optInt("type") != 1) {
            skipped++; continue
        }

        val login = item.optJSONObject("login") ?: run { skipped++; continue }
        val password = login.optString("password", "")
        if (password.isBlank()) {
            skipped++; continue
        }

        val name = item.optString("name", "Unbekannt")
        val username = login.optString("username", "")
        val notes = item.optString("notes", "")

        val url = login.optJSONArray("uris")
            ?.optJSONObject(0)
            ?.optString("uri", "") ?: ""

        passwords.add(
            PasswordEntry(
                name = name,
                url = url,
                username = username,
                password = password,
                notes = notes.takeIf { it != "null" } ?: ""
            )
        )

        val totp = if (login.isNull("totp")) "" else login.optString("totp", "")
        if (totp.isNotBlank()) {
            totpEntries.add(
                TwoFAEntry(
                    name = name,
                    secret = totp.trim().replace(" ", "").uppercase()
                )
            )
        }
    }

    return Triple(passwords, totpEntries, skipped)
}

@Composable
fun ImportPasswordsDialog(
    passwordDb: PasswordDatabase,
    twoFaDb: TwoFADatabase,
    onDismiss: () -> Unit,
    onImportDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var importTotp by remember { mutableStateOf(true) }
    var pendingRemovals by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var removalsShown by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            isLoading = true
            scope.launch {
                try {
                    val json =
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                            ?: ""
                    val (passwords, totpList, skipped) = parseBitwardenExport(json)

                    var pwInserted = 0
                    var pwUpdated = 0
                    val existing = passwordDb.passwordDao().getAll()

                    passwords.forEach { new ->
                        val match = existing.find {
                            it.name == new.name && it.username == new.username
                        }
                        when {
                            match == null -> {
                                passwordDb.passwordDao().insert(new)
                                pwInserted++
                            }

                            match.password != new.password
                                    || match.url != new.url
                                    || match.notes != new.notes -> {
                                passwordDb.passwordDao().update(new.copy(id = match.id))
                                pwUpdated++
                            }
                        }
                    }

                    var totpInserted = 0
                    if (importTotp) {
                        totpList.forEach { entry ->
                            val inserted = twoFaDb.twoFADao().insertOrIgnore(entry)
                            if (inserted != -1L) totpInserted++
                        }
                    }

                    val removedEntries = existing.filter { old ->
                        passwords.none { new -> new.name == old.name && new.username == old.username }
                    }

                    result =
                        ImportResult(pwInserted, pwUpdated, totpInserted, skipped, removedEntries)
                    if (removedEntries.isEmpty()) onImportDone()
                } catch (e: Exception) {
                    Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(S1)
                .padding(24.dp)
        ) {
            Text("📥 Import", color = TP, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Bitwarden JSON Export (unverschlüsselt)", color = TS, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = importTotp,
                    onCheckedChange = { importTotp = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = AB, uncheckedTrackColor = S3)
                )
                Spacer(Modifier.width(8.dp))
                Text("TOTP-Secrets auch importieren", color = TP, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))

            result?.let {
                Text(
                    "✅ ${it.passwords} neu importiert",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp
                )
                if (it.updated > 0) Text(
                    "🔄 ${it.updated} aktualisiert",
                    color = Color(0xFFFBC02D),
                    fontSize = 14.sp
                )
                if (it.totp > 0) Text(
                    "🛡️ ${it.totp} 2FA-Einträge importiert",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp
                )
                if (it.skipped > 0) Text(
                    "⏭ ${it.skipped} übersprungen",
                    color = TS,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                LaunchedEffect(it) {
                    if (it.removed.isNotEmpty() && !removalsShown) {
                        removalsShown = true
                        pendingRemovals = it.removed
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AB),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fertig")
                }
            } ?: run {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AB)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Abbrechen", color = TS)
                        }
                        Button(
                            onClick = { launcher.launch("application/json") },
                            colors = ButtonDefaults.buttonColors(containerColor = AB),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Datei wählen")
                        }
                    }
                }
            }
        }
    }

    if (pendingRemovals.isNotEmpty()) {
        RemovalDecisionDialog(
            entries = pendingRemovals,
            onDecision = { entry, delete ->
                if (delete) {
                    scope.launch { passwordDb.passwordDao().delete(entry) }
                }
            },
            onDone = {
                pendingRemovals = emptyList()
                onImportDone()
            }
        )
    }
}

@Composable
fun RemovalDecisionDialog(
    entries: List<PasswordEntry>,
    onDecision: (PasswordEntry, Boolean) -> Unit,
    onDone: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }

    if (index >= entries.size) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    val entry = entries[index]

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(S1)
                .padding(24.dp)
        ) {
            Text(
                "🗑️ Nicht mehr im Export",
                color = TP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text("${index + 1} von ${entries.size}", color = TS, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1F1F27))
                    .padding(16.dp)
            ) {
                Text(entry.name, color = TP, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (entry.username.isNotEmpty())
                    Text(entry.username, color = TS, fontSize = 13.sp)
                if (entry.url.isNotEmpty())
                    Text(entry.url, color = TS, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))
            Text("Was soll mit diesem Eintrag passieren?", color = TP, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onDecision(entry, true); index++ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Löschen") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onDecision(entry, false); index++ },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Behalten", color = TS) }
        }
    }
}
