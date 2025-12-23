package com.example.cloud.Authenticator

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@SuppressLint("UseKtx")
@Composable
fun TwoFAListScreen(db: TwoFADatabase, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<TwoFAEntry>>(emptyList()) }
    var name by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<TwoFAEntry?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var isEditingEntry by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    var editedFolder by remember { mutableStateOf("") }
    var generatedCode by rememberSaveable { mutableStateOf("------") }
    var secondsLeft by rememberSaveable { mutableIntStateOf(30) }
    var folder by rememberSaveable { mutableStateOf("") }
    var pendingEntries by remember { mutableStateOf<List<TwoFAEntry>>(emptyList()) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        entries = db.twoFADao().getAll()
        Log.d("APP_STARTUP", "📱 ${entries.size} lokale Einträge geladen")
    }

    // Initial-Sync beim Start
    LaunchedEffect(true) {
        if (isSyncing) return@LaunchedEffect

        // 1. Sofort lokale Daten laden (schnell!)
        entries = db.twoFADao().getAll()
        Log.d("APP_STARTUP", "📱 ${entries.size} lokale Einträge geladen (Initial)")

        // 2. Prüfen, wann zuletzt synchronisiert wurde
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_sync_timestamp", 0L)
        val currentTime = System.currentTimeMillis()
        val fiveMinutesInMillis = 1 * 20 * 1000L // 5 Minuten

        // Nur synchronisieren, wenn letzte Sync länger als 5 Minuten her ist
        // Nur synchronisieren, wenn letzte Sync länger als 5 Minuten her ist
        if (currentTime - lastSyncTime > fiveMinutesInMillis) {
            Log.d(
                "APP_STARTUP",
                "🔄 Starte Synchronisation (letzte Sync vor ${(currentTime - lastSyncTime) / 1000}s)"
            )

            isSyncing = true
            scope.launch {
                try {
                    val result = syncTwoFaEntriesWithConfirmation(db)

                    // Erst Liste aktualisieren
                    entries = db.twoFADao().getAll()

                    if (result.pendingDecisions.isNotEmpty()) {
                        pendingEntries = result.pendingDecisions
                        showSyncDialog = true
                        Log.d(
                            "APP_STARTUP",
                            "⚠️ ${result.pendingDecisions.size} Einträge benötigen Bestätigung"
                        )
                    }

                    if (result.error != null) {
                        Log.e("APP_STARTUP", "❌ Sync-Fehler: ${result.error}")
                        Toast.makeText(
                            context,
                            "Offline: ${result.error}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Sync-Timestamp speichern
                        prefs.edit { putLong("last_sync_timestamp", System.currentTimeMillis()) }

                        if (result.uploaded > 0 || result.downloaded > 0) {
                            Toast.makeText(
                                context,
                                "Synchronisiert: ${result.uploaded} hochgeladen, ${result.downloaded} heruntergeladen",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (result.pendingDecisions.isEmpty()) {
                            Log.d("APP_STARTUP", "✅ Bereits synchron")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("APP_STARTUP", "❌ Sync-Exception: ${e.message}")
                    Toast.makeText(
                        context,
                        "Synchronisation fehlgeschlagen: Keine Internetverbindung",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    isSyncing = false
                }
            }
        } else {
            Log.d(
                "APP_STARTUP",
                "⏭️ Sync übersprungen (letzte Sync vor ${(currentTime - lastSyncTime) / 1000}s)"
            )
        }
    }

    LaunchedEffect(selectedEntry, isEditingEntry) {
        val step = 30L
        val displayPeriod = 30L
        while (selectedEntry != null && !isEditingEntry) {
            val now = System.currentTimeMillis()
            generatedCode =
                TotpGenerator.generateTOTP(selectedEntry!!.secret, now, periodSeconds = step)
            val elapsed = (now / 1000) % displayPeriod
            secondsLeft = (displayPeriod - elapsed).toInt()
            delay(1000)
        }
    }

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF3E1E68), Color(0xFF9B4DCA), Color(0xFF6E48AA)),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Deine 2FA-Token",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White, fontWeight = FontWeight.Bold
                )
            )

            Button(
                onClick = {
                    scope.launch {
                        entries = db.twoFADao().getAll()
                        Toast.makeText(context, "Liste aktualisiert!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B4DCA)),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("Aktualisieren", color = Color.White)
            }

            val groupedEntries = try {
                entries.groupBy { it.folder ?: "Unsortiert" }
                    .toSortedMap(compareBy { if (it == "Unsortiert") "ZZZ" else it })
            } catch (e: Exception) {
                Log.e("UI_ERROR", "Fehler beim Gruppieren: ${e.message}")
                emptyMap<String, List<TwoFAEntry>>()
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) Composable@{
                if (groupedEntries.isEmpty()) {
                    item {
                        Text(
                            "Keine Einträge vorhanden",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                val allEntries = groupedEntries.values.flatten()
                val duplicateNames = allEntries
                    .groupBy { it.name }
                    .filter { it.value.size > 1 }
                    .keys

                if (duplicateNames.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        "Doppelte Namen gefunden: $duplicateNames",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("duplicateNames", "Doppelte Namen gefunden: $duplicateNames")
                    return@Composable
                }

                groupedEntries.forEach { (folderName, folderEntries) ->
                    item(key = folderName) {
                        Text(
                            text = "$folderName:",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF6E48AA), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }

                    items(folderEntries, key = { it.name }) { entry ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .pointerInput(entry) {
                                    detectTapGestures(
                                        onTap = { selectedEntry = entry },
                                        onLongPress = {
                                            val now = System.currentTimeMillis()
                                            val code = TotpGenerator.generateTOTP(entry.secret, now)
                                            val clipboard =
                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText(
                                                    "Generated Code",
                                                    code
                                                )
                                            )
                                            Toast.makeText(
                                                context,
                                                "Code für ${entry.name} kopiert!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    entry.name,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Open settings",
                        tint = Color.White
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B4DCA))
                ) {
                    Text("Hinzufügen", fontSize = 15.sp, color = Color.White)
                }

                Button(
                    onClick = {
                        val permissionCheck =
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            val intent = Intent(activity, SilentCaptureActivity::class.java)
                            intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
                            activity?.startActivity(intent)
                        } else {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        if (context !is Activity) {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                                        if (context !is Activity) {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    }
                                    context.startActivity(fallbackIntent)
                                } catch (e: Exception) {
                                    Log.e("Settings", "Konnte Einstellungen nicht öffnen", e)
                                    Toast.makeText(
                                        context,
                                        "Bitte Einstellungen manuell öffnen",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            Toast.makeText(
                                context,
                                "Erlaube Kamera Zugriff in den Einstellungen",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B4DCA))
                ) {
                    Text("QR-Code scannen", fontSize = 15.sp, color = Color.White)
                }
            }
        }

        // Overlay für ausgewählten Eintrag
        selectedEntry?.let { entry ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        isEditingEntry = false
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // Klick auf Hintergrund schließt die Box
                            selectedEntry = null
                            isEditingEntry = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    Modifier
                        .padding(32.dp)
                        .fillMaxWidth()
                        .clickable(enabled = false) {},
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4F4F4F))
                ) {
                    Column(
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isEditingEntry) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                label = { Text("Name bearbeiten", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFB388FF),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))

                            val existingFolders = entries.mapNotNull { it.folder }.distinct()
                            var folderExpanded by remember { mutableStateOf(false) }
                            var isFolderEditMode by remember { mutableStateOf(false) }

                            Box(Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editedFolder,
                                    onValueChange = { editedFolder = it },
                                    label = { Text("Ordner (optional)", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = !isFolderEditMode,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFB388FF),
                                        unfocusedBorderColor = Color.Gray
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            if (!isFolderEditMode) folderExpanded = true
                                        }) {
                                            Icon(
                                                imageVector = if (folderExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                contentDescription = "Dropdown-Pfeil",
                                                tint = Color.White
                                            )
                                        }
                                    },
                                    interactionSource = remember { MutableInteractionSource() }
                                        .also { interactionSource ->
                                            LaunchedEffect(interactionSource) {
                                                interactionSource.interactions.collect { interaction ->
                                                    if (interaction is PressInteraction.Release && !isFolderEditMode) {
                                                        folderExpanded = true
                                                    }
                                                }
                                            }
                                        }
                                )

                                DropdownMenu(
                                    expanded = folderExpanded,
                                    onDismissRequest = { folderExpanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    existingFolders.forEach { f ->
                                        DropdownMenuItem(
                                            text = { Text(f) },
                                            onClick = {
                                                editedFolder = f
                                                isFolderEditMode = false
                                                folderExpanded = false
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Neuen Ordner eingeben...") },
                                        onClick = {
                                            editedFolder = ""
                                            isFolderEditMode = true
                                            folderExpanded = false
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (editedName.isNotBlank()) {
                                                val updatedEntry = entry.copy(
                                                    name = editedName,
                                                    folder = editedFolder.ifBlank { null }
                                                )
                                                db.twoFADao().update(updatedEntry)

                                                // In Supabase aktualisieren (nicht neu einfügen!)
                                                val supabaseSuccess =
                                                    updateTwoFaEntryInSupabase(updatedEntry)

                                                if (!supabaseSuccess) {
                                                    Log.w(
                                                        "EDIT",
                                                        "⚠️ Supabase-Update fehlgeschlagen, aber lokal gespeichert"
                                                    )
                                                }

                                                entries = db.twoFADao().getAll()
                                                selectedEntry = updatedEntry
                                                isEditingEntry = false
                                                Toast.makeText(
                                                    context,
                                                    "Eintrag aktualisiert!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFF4CAF50
                                        )
                                    )
                                ) {
                                    Text("Speichern", color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        isEditingEntry = false
                                        editedName = entry.name
                                        editedFolder = entry.folder ?: ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                                ) {
                                    Text("Abbrechen", color = Color.White)
                                }
                            }
                        } else {
                            Text(
                                entry.name,
                                fontSize = 22.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .clickable {
                                        isEditingEntry = true
                                        editedName = entry.name
                                        editedFolder = entry.folder ?: ""
                                    }
                                    .fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Code: $generatedCode",
                            fontSize = 18.sp,
                            color = Color(0xFFB388FF),
                            modifier = Modifier.clickable {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Generated Code",
                                        generatedCode
                                    )
                                )
                                Toast.makeText(context, "Code kopiert!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("Gültig noch: ${secondsLeft}s", color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Information",
                                tint = Color.Gray
                            )
                            Text(
                                "Codes die hier abgelaufen sind, sind generell noch bis zu 10sek gültig",
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Generated Code",
                                        generatedCode
                                    )
                                )
                                Toast.makeText(context, "Code kopiert!", Toast.LENGTH_SHORT).show()
                                selectedEntry = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B4DCA)),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Code kopieren", color = Color.White)
                        }

                        var showDeleteDialog by remember { mutableStateOf(false) }

                        Button(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Eintrag löschen", color = Color.White)
                        }

                        val favPrefs =
                            context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
                        val favIndex = when (entry.secret) {
                            favPrefs.getString("fav1_secret", null) -> 1
                            favPrefs.getString("fav2_secret", null) -> 2
                            favPrefs.getString("fav3_secret", null) -> 3
                            else -> null
                        }

                        if (favIndex != null) {
                            Button(
                                onClick = {
                                    favPrefs.edit().apply {
                                        remove("fav${favIndex}_name")
                                        remove("fav${favIndex}_secret")
                                        apply()
                                    }
                                    Toast.makeText(
                                        context,
                                        "Favorit $favIndex entfernt!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text("Favorit $favIndex entfernen")
                            }
                        } else {
                            val favCount = listOf(
                                favPrefs.getString("fav1_secret", null),
                                favPrefs.getString("fav2_secret", null),
                                favPrefs.getString("fav3_secret", null)
                            ).count { it != null }

                            if (favCount < 3) {
                                Button(
                                    onClick = {
                                        val slot = when {
                                            favPrefs.getString("fav1_secret", null) == null -> 1
                                            favPrefs.getString("fav2_secret", null) == null -> 2
                                            favPrefs.getString("fav3_secret", null) == null -> 3
                                            else -> null
                                        }
                                        slot?.let {
                                            favPrefs.edit().apply {
                                                putString("fav${it}_name", entry.name)
                                                putString("fav${it}_secret", entry.secret)
                                                apply()
                                            }
                                            Toast.makeText(
                                                context,
                                                "Als Favorit $it gespeichert!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text("Als Favorit speichern")
                                }
                            } else {
                                Text("Max. 3 Favoriten erreicht", color = Color.Gray)
                            }
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Eintrag löschen?") },
                                text = { Text("Bist du sicher, dass du diesen 2FA-Eintrag löschen willst?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val favPrefs = context.getSharedPreferences(
                                                    "favorites",
                                                    Context.MODE_PRIVATE
                                                )
                                                favPrefs.edit().apply {
                                                    if (favPrefs.getString(
                                                            "fav1_secret",
                                                            null
                                                        ) == entry.secret
                                                    ) {
                                                        remove("fav1_name")
                                                        remove("fav1_secret")
                                                    }
                                                    if (favPrefs.getString(
                                                            "fav2_secret",
                                                            null
                                                        ) == entry.secret
                                                    ) {
                                                        remove("fav2_name")
                                                        remove("fav2_secret")
                                                    }
                                                    if (favPrefs.getString(
                                                            "fav3_secret",
                                                            null
                                                        ) == entry.secret
                                                    ) {
                                                        remove("fav3_name")
                                                        remove("fav3_secret")
                                                    }
                                                    apply()
                                                }
                                                db.twoFADao().delete(entry)
                                                entries = db.twoFADao().getAll()
                                                selectedEntry = null
                                                showDeleteDialog = false
                                            }
                                        }
                                    ) { Text("Ja, löschen") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showDeleteDialog = false
                                    }) { Text("Abbrechen") }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Overlay für "Hinzufügen"
        if (showAddDialog) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showAddDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    Modifier
                        .padding(32.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Neuen Code hinzufügen", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))

                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name (nur für dich)") }
                        )
                        TextField(
                            value = secret,
                            onValueChange = { secret = it },
                            label = { Text("Schlüssel") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )

                        var expanded by remember { mutableStateOf(false) }
                        val existingFolders = entries.mapNotNull { it.folder }.distinct()
                        var isEditMode by remember { mutableStateOf(false) }

                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = folder,
                                onValueChange = { folder = it },
                                label = { Text("Ordner (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = !isEditMode,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (!isEditMode) expanded = true
                                    }) {
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown-Pfeil"
                                        )
                                    }
                                },
                                interactionSource = remember { MutableInteractionSource() }
                                    .also { interactionSource ->
                                        LaunchedEffect(interactionSource) {
                                            interactionSource.interactions.collect { interaction ->
                                                if (interaction is PressInteraction.Release && !isEditMode) {
                                                    expanded = true
                                                }
                                            }
                                        }
                                    }
                            )

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                existingFolders.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text(f) },
                                        onClick = {
                                            folder = f
                                            isEditMode = false
                                            expanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Neuen Ordner eingeben...") },
                                    onClick = {
                                        folder = ""
                                        isEditMode = true
                                        expanded = false
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank() && secret.isNotBlank()) {
                                    scope.launch {
                                        Toast.makeText(context, "Starting!", Toast.LENGTH_SHORT)
                                            .show()
                                        val existingEntries = db.twoFADao().getAll()
                                        val secretExists =
                                            existingEntries.any { it.secret == secret }

                                        if (secretExists) {
                                            Toast.makeText(
                                                context,
                                                "Eintrag mit diesem Secret existiert bereits!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            val newEntry = TwoFAEntry(
                                                name = name,
                                                secret = secret,
                                                folder = folder.ifBlank { null }
                                            )

                                            try {
                                                // 1. Lokal speichern
                                                db.twoFADao().insert(newEntry)

                                                // 2. In Supabase speichern und ID zurückbekommen
                                                val supabaseSuccess =
                                                    saveTwoFaEntryToSupabase(newEntry, db)

                                                // 3. User-Feedback basierend auf Erfolg
                                                withContext(Dispatchers.Main) {
                                                    if (supabaseSuccess) {
                                                        Toast.makeText(
                                                            context,
                                                            "Gespeichert (lokal & Cloud)",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Lokal gespeichert (Cloud-Sync fehlgeschlagen)",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        "Fehler beim Speichern: ${e.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }

                                            Toast.makeText(context, "Server!", Toast.LENGTH_SHORT)
                                                .show()

                                            // UI aktualisieren
                                            entries = db.twoFADao().getAll()
                                            name = ""
                                            secret = ""
                                            folder = ""
                                            showAddDialog = false
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Hinzufügen")
                        }
                    }
                }
            }
        }
        if (showSyncDialog && pendingEntries.isNotEmpty()) {
            SyncConfirmationDialog(
                entries = pendingEntries,
                onDecision = { entry, decision ->
                    scope.launch {
                        val success = processSyncDecision(db, entry, decision)
                        if (success) {
                            entries = db.twoFADao().getAll()
                        }
                    }
                },
                onDismiss = {
                    showSyncDialog = false
                    pendingEntries = emptyList()
                }
            )
        }
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
fun SettingsScreenWithScreenshotProtection(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("lockEnabled", false)) }
    var screenshotProtectionEnabled by remember {
        mutableStateOf(prefs.getBoolean("screenshotProtectionEnabled", true))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF3E1E68), Color(0xFF9B4DCA), Color(0xFF6E48AA))
                )
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Einstellungen",
                fontSize = 26.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))
            Text(
                "Sicherheit",
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(8.dp))

            // App-Sperre
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = { enabled ->
                        lockEnabled = enabled
                        prefs.edit().apply {
                            putBoolean("lockEnabled", enabled)
                            putBoolean("authenticated", !enabled)
                            apply()
                        }
                    }
                )
                Text(
                    text = if (lockEnabled) "App-Sperre aktiviert" else "App-Sperre deaktiviert",
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Screenshot-Schutz
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Switch(
                    checked = screenshotProtectionEnabled,
                    onCheckedChange = { enabled ->
                        screenshotProtectionEnabled = enabled
                        prefs.edit().apply {
                            putBoolean("screenshotProtectionEnabled", enabled)
                            apply()
                        }
                        ScreenshotProtectionManager.setScreenshotProtection(activity, enabled)

                        Toast.makeText(
                            context,
                            if (enabled) "Screenshots jetzt gesperrt" else "Screenshots jetzt erlaubt",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                Text(
                    text = if (screenshotProtectionEnabled) "Screenshots gesperrt" else "Screenshots erlaubt",
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B4DCA))
            ) {
                Text("Zurück", color = Color.White)
            }
        }
    }
}

@Composable
fun MainApp(db: TwoFADatabase) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "list") {
        composable("list") {
            TwoFAListScreen(
                db = db,
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreenWithScreenshotProtection(onBackClick = { navController.popBackStack() })
        }
    }
}