package com.cloud.authenticator

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cloud.Config
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.cloud.SupabaseConfigALT
import com.cloud.ui.theme.c
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant

private fun normalizeSecret(secret: String): String =
    secret.trim().replace(" ", "").uppercase(java.util.Locale.US).trimEnd('=')

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
        secret = Config.decrypt(this.secret)
    )
}

fun TwoFAEntry.toSupabase(): TwoFaEntrySupabase {
    return TwoFaEntrySupabase(
        account_name = this.name,
        secret = Config.encrypt(this.secret)
    )
}

suspend fun loadTwoFaEntriesFromSupabase(): List<TwoFAEntry> {
    return withContext(Dispatchers.IO) {
        try {
            val supabaseEntries = SupabaseConfigALT.client.postgrest
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

            val response = SupabaseConfigALT.client.postgrest
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

            SupabaseConfigALT.client.postgrest
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
                if (localEntries.isEmpty()) {
                    return@withContext SyncResult(uploaded = 0, downloaded = 0, total = 0)
                }
                return@withContext SyncResult(
                    uploaded = 0,
                    downloaded = 0,
                    total = localEntries.size,
                    pendingDecisions = localEntries
                )
            }

            val supabaseSecrets = supabaseEntries.map { normalizeSecret(it.secret) }.toSet()
            val localSecrets = localEntries.map { normalizeSecret(it.secret) }.toSet()

            val missingInSupabase = localEntries.filter { normalizeSecret(it.secret) !in supabaseSecrets }
            val missingLocally = supabaseEntries.filter { normalizeSecret(it.secret) !in localSecrets }

            var downloadedCount = 0
            missingLocally.forEach { entry ->
                try {
                    val inserted = db.twoFADao().insertOrIgnore(entry)
                    if (inserted != -1L) {
                        downloadedCount++
                    }
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
                    deleteTwoFaEntryFromSupabase(entry)
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
                    colors = ButtonDefaults.buttonColors(containerColor = c()),
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

suspend fun deleteTwoFaEntryFromSupabase(entry: TwoFAEntry): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (entry.supabaseId == null) {
                Log.d("TwoFARepository", "entry.supabaseId == null"); return@withContext true
            } // nichts zu löschen
            Log.d("TwoFARepository", "$entry")
            SupabaseConfigALT.client.postgrest
                .from("two_fa_entries")
                .delete {
                    filter { eq("id", entry.supabaseId) }
                }
            true
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "TwoFARepository",
                    "❌ Supabase Delete-Fehler: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
            false
        }
    }
}