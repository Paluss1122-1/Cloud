package com.example.cloud.authenticator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cloud.ERRORINSERT
import com.example.cloud.ERRORINSERTDATA
import com.example.cloud.SupabaseConfig
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.toSet

@Serializable
data class TwoFaEntrySupabase(
    val id: String? = null,
    val account_name: String,
    val issuer: String? = null,
    val secret: String
)

fun TwoFaEntrySupabase.toLocal(): TwoFAEntry {
    return TwoFAEntry(
        supabaseId = this.id,
        name = this.account_name,
        secret = EncryptionUtils.decrypt(this.secret),
        folder = this.issuer
    )
}

fun TwoFAEntry.toSupabase(): TwoFaEntrySupabase {
    return TwoFaEntrySupabase(
        account_name = this.name,
        issuer = this.folder,
        secret = EncryptionUtils.encrypt(this.secret)
    )
}

suspend fun loadTwoFaEntriesFromSupabase(): List<TwoFAEntry> {
    return withContext(Dispatchers.IO) {
        try {
            val supabaseEntries = SupabaseConfig.client.postgrest
                .from("two_fa_entries")
                .select()
                .decodeList<TwoFaEntrySupabase>()
            supabaseEntries.map { it.toLocal() }
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "TwoFaRepository",
                    "Fehler bei Laden von Einträgen aus Supabase: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
            emptyList()
        }
    }
}

suspend fun saveTwoFaEntryToSupabase(entry: TwoFAEntry, db: TwoFADatabase? = null): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val supabaseEntry = entry.toSupabase()

            val response = SupabaseConfig.client.postgrest
                .from("two_fa_entries")
                .insert(supabaseEntry) {
                    select()
                }
                .decodeSingle<TwoFaEntrySupabase>()

            if (db != null && response.id != null) {
                val updatedEntry = entry.copy(supabaseId = response.id)
                db.twoFADao().update(updatedEntry)
            }

            true
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "TwoFARepository",
                    "❌ Supabase Fehler: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
            false
        }
    }
}

suspend fun updateTwoFaEntryInSupabase(entry: TwoFAEntry): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (entry.supabaseId == null) {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "TwoFARepository",
                        "Keine Supabase-ID vorhanden, überspringe Update",
                        Instant.now().toString(),
                        "Warning"
                    )
                )
                return@withContext false
            }

            val supabaseEntry = entry.toSupabase()

            SupabaseConfig.client.postgrest
                .from("two_fa_entries")
                .update({
                    set("account_name", supabaseEntry.account_name)
                }) {
                    filter {
                        eq("id", entry.supabaseId)
                    }
                }

            true
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "TwoFARepository",
                    "❌ Supabase Update-Fehler: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
            false
        }
    }
}

suspend fun syncTwoFaEntriesWithConfirmation(db: TwoFADatabase): SyncResult {
    return withContext(Dispatchers.IO) {
        try {
            val localEntries = db.twoFADao().getAll()

            val supabaseEntries = loadTwoFaEntriesFromSupabase()

            if (supabaseEntries.isEmpty()) {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "TwoFARepository",
                        "Keine Cloud-Daten, alle lokalen Einträge zur Bestätigung",
                        Instant.now().toString(),
                        "Warning"
                    )
                )
                return@withContext SyncResult(
                    uploaded = 0,
                    downloaded = 0,
                    total = localEntries.size,
                    pendingDecisions = localEntries
                )
            }

            val supabaseSecrets = supabaseEntries.map { it.secret }.toSet()
            val localSecrets = localEntries.map { it.secret }.toSet()

            val missingInSupabase = localEntries.filter { it.secret !in supabaseSecrets }

            val missingLocally = supabaseEntries.filter { it.secret !in localSecrets }

            var downloadedCount = 0
            missingLocally.forEach { entry ->
                try {
                    db.twoFADao().insert(entry)
                    downloadedCount++
                } catch (e: Exception) {
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            "TwoFARepository",
                            "❌ Fehler beim lokalen Speichern: ${entry.name} (${e.message})",
                            Instant.now().toString(),
                            "Error"
                        )
                    )
                }
            }

            SyncResult(
                uploaded = 0,
                downloaded = downloadedCount,
                total = localEntries.size + downloadedCount,
                pendingDecisions = missingInSupabase
            )
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "TwoFARepository",
                    "❌ Fehler beim Synchronisieren: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
            SyncResult(uploaded = 0, downloaded = 0, total = 0, error = e.message)
        }
    }
}

data class SyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val total: Int,
    val pendingDecisions: List<TwoFAEntry> = emptyList(),
    val error: String? = null
)

enum class SyncDecision {
    UPLOAD, DELETE, SKIP
}

suspend fun processSyncDecision(
    db: TwoFADatabase,
    entry: TwoFAEntry,
    decision: SyncDecision
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            when (decision) {
                SyncDecision.UPLOAD -> {
                    saveTwoFaEntryToSupabase(entry, db)
                }

                SyncDecision.DELETE -> {
                    db.twoFADao().delete(entry)
                    true
                }

                SyncDecision.SKIP -> {
                    true
                }
            }
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "TwoFaRepository",
                    "Fehler in processSyncDecision: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
            false
        }
    }
}

@Composable
fun SyncConfirmationDialog(
    entries: List<TwoFAEntry>,
    onDecision: (TwoFAEntry, SyncDecision) -> Unit,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }

    if (currentIndex >= entries.size) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    val currentEntry = entries[currentIndex]

    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4F4F4F))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Neuer lokaler Eintrag gefunden",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Eintrag ${currentIndex + 1} von ${entries.size}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Name:",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            currentEntry.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        if (currentEntry.folder != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ordner:",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                currentEntry.folder,
                                fontSize = 16.sp,
                                color = Color(0xFFB388FF)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "Was möchtest du mit diesem Eintrag tun?",
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        onDecision(currentEntry, SyncDecision.UPLOAD)
                        currentIndex++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        "In Cloud hochladen",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        onDecision(currentEntry, SyncDecision.DELETE)
                        currentIndex++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        "Lokal löschen",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        "Abbrechen (Sync später)",
                        color = Color.Gray
                    )
                }
            }
        }
    }
}