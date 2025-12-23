package com.example.cloud.Authenticator

import android.util.Log
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
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.toSet
import kotlin.jvm.javaClass

// Supabase-spezifisches Modell
@Serializable
data class TwoFaEntrySupabase(
    val id: String? = null,
    val account_name: String,
    val issuer: String? = null,
    val secret: String
)

// Konvertierung: Supabase → Lokal (Room)
fun TwoFaEntrySupabase.toLocal(): TwoFAEntry {
    return TwoFAEntry(
        supabaseId = this.id,
        name = this.account_name,
        secret = EncryptionUtils.decrypt(this.secret), // 🔓 entschlüsseln
        folder = this.issuer
    )
}

// Konvertierung: Lokal → Supabase
fun TwoFAEntry.toSupabase(): TwoFaEntrySupabase {
    return TwoFaEntrySupabase(
        account_name = this.name,
        issuer = this.folder,
        secret = EncryptionUtils.encrypt(this.secret) // 🔒 verschlüsseln
    )
}

// Lädt alle Einträge aus Supabase
suspend fun loadTwoFaEntriesFromSupabase(): List<TwoFAEntry> {
    return withContext(Dispatchers.IO) {
        try {
            val supabaseEntries = supabase.postgrest
                .from("two_fa_entries")
                .select()
                .decodeList<TwoFaEntrySupabase>()
            supabaseEntries.map { it.toLocal() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

// Speichert einen lokalen Eintrag in Supabase und gibt die ID zurück
suspend fun saveTwoFaEntryToSupabase(entry: TwoFAEntry, db: TwoFADatabase? = null): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            println("🔍 DEBUG: Starte Supabase-Insert")
            println("🔍 Entry: name=${entry.name}, secret=${entry.secret}, folder=${entry.folder}")

            val supabaseEntry = entry.toSupabase()
            println("🔍 Supabase Entry: account_name=${supabaseEntry.account_name}, issuer=${supabaseEntry.issuer}")

            // Insert mit select() um die generierte ID zu bekommen
            val response = supabase.postgrest
                .from("two_fa_entries")
                .insert(supabaseEntry) {
                    select()
                }
                .decodeSingle<TwoFaEntrySupabase>()

            println("✅ Supabase-Insert erfolgreich! ID: ${response.id}")

            // Wenn DB übergeben wurde, supabaseId lokal aktualisieren
            if (db != null && response.id != null) {
                val updatedEntry = entry.copy(supabaseId = response.id)
                db.twoFADao().update(updatedEntry)
                println("✅ Lokale supabaseId aktualisiert: ${response.id}")
            }

            true
        } catch (e: Exception) {
            println("❌ Supabase Fehler: ${e.javaClass.simpleName}")
            println("❌ Message: ${e.message}")
            println("❌ Stack trace:")
            e.printStackTrace()
            false
        }
    }
}
// Aktualisiert einen bestehenden Eintrag in Supabase
// Aktualisiert einen bestehenden Eintrag in Supabase (ID-basiert)
suspend fun updateTwoFaEntryInSupabase(entry: TwoFAEntry): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (entry.supabaseId == null) {
                println("⚠️ Keine Supabase-ID vorhanden, überspringe Update")
                return@withContext false
            }

            println("🔍 DEBUG: Starte Supabase-Update für ID: ${entry.supabaseId}")
            println("🔍 Entry: name=${entry.name}, folder=${entry.folder}")

            val supabaseEntry = entry.toSupabase()

            // Update basierend auf der ID
            supabase.postgrest
                .from("two_fa_entries")
                .update({
                    set("account_name", supabaseEntry.account_name)
                }) {
                    filter {
                        eq("id", entry.supabaseId)
                    }
                }

            println("✅ Supabase-Update erfolgreich!")
            true
        } catch (e: Exception) {
            println("❌ Supabase Update-Fehler: ${e.javaClass.simpleName}")
            println("❌ Message: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

// Neue Sync-Funktion mit Bestätigung
suspend fun syncTwoFaEntriesWithConfirmation(db: TwoFADatabase): SyncResult {
    return withContext(Dispatchers.IO) {
        try {
            // 1. Lokale Einträge laden (unverschlüsselte Secrets)
            val localEntries = db.twoFADao().getAll()

            // 2. Supabase-Einträge laden (werden automatisch entschlüsselt durch toLocal())
            val supabaseEntries = loadTwoFaEntriesFromSupabase()

            if (supabaseEntries.isEmpty()) {
                // Keine Supabase-Daten -> Alle lokalen als "pending" zurückgeben
                Log.d("SYNC", "⚠️ Keine Cloud-Daten, alle lokalen Einträge zur Bestätigung")
                return@withContext SyncResult(
                    uploaded = 0,
                    downloaded = 0,
                    total = localEntries.size,
                    pendingDecisions = localEntries
                )
            }

            // 3. Vergleiche basierend auf UNVERSCHLÜSSELTEN Secrets
            // (beide Seiten sind jetzt entschlüsselt, da loadTwoFaEntriesFromSupabase() toLocal() aufruft!)
            val supabaseSecrets = supabaseEntries.map { it.secret }.toSet()
            val localSecrets = localEntries.map { it.secret }.toSet()

            // Welche lokalen Einträge fehlen in Supabase?
            val missingInSupabase = localEntries.filter { it.secret !in supabaseSecrets }

            // Welche Supabase-Einträge fehlen lokal?
            val missingLocally = supabaseEntries.filter { it.secret !in localSecrets }

            // 4. Fehlende Einträge lokal speichern (automatisch) - jetzt mit supabaseId!
            var downloadedCount = 0
            missingLocally.forEach { entry ->
                try {
                    // Entry hat bereits supabaseId von toLocal()
                    db.twoFADao().insert(entry)
                    downloadedCount++
                    Log.d("SYNC", "✅ Lokal gespeichert: ${entry.name} (Supabase-ID: ${entry.supabaseId})")
                } catch (e: Exception) {
                    Log.e("SYNC", "❌ Fehler beim lokalen Speichern: ${entry.name}", e)
                }
            }

            // 5. Rückgabe mit pending Einträgen zur Bestätigung
            SyncResult(
                uploaded = 0,
                downloaded = downloadedCount,
                total = localEntries.size + downloadedCount,
                pendingDecisions = missingInSupabase
            )

        } catch (e: Exception) {
            SyncResult(uploaded = 0, downloaded = 0, total = 0, error = e.message)
        }
    }
}

// Daten-Klasse für Sync-Ergebnis (erweitert)
data class SyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val total: Int,
    val pendingDecisions: List<TwoFAEntry> = emptyList(),
    val error: String? = null
)

// Enum für Benutzerentscheidung
enum class SyncDecision {
    UPLOAD, DELETE, SKIP
}

// Verarbeitet eine einzelne Entscheidung
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
                    true // Nichts tun
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        // Alle Einträge verarbeitet
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    val currentEntry = entries[currentIndex]

    Dialog(onDismissRequest = { /* Dialog kann nicht durch Klick außerhalb geschlossen werden */ }) {
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

                // Upload Button
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

                // Delete Button
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