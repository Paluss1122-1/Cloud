package com.cloud.authenticator

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.time.Instant

private val Surface1 = Color(0xFF17171C)
private val Surface2 = Color(0xFF1F1F27)
private val Surface3 = Color(0xFF2A2A35)
private val AccentBlue = Color(0xFF4A90E2)
private val AccentRed = Color(0xFFE74C3C)
private val TextP = Color(0xFFEEEEF5)
private val TextS = Color(0xFF8A8A9F)
private val TextT = Color(0xFF55556A)

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
    var generatedCode by rememberSaveable { mutableStateOf("------") }
    var secondsLeft by rememberSaveable { mutableIntStateOf(30) }
    var pendingEntries by remember { mutableStateOf<List<TwoFAEntry>>(emptyList()) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current

    LaunchedEffect(true) {
        if (isSyncing) return@LaunchedEffect

        entries = db.twoFADao().getAll()
        isLoading = false

        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_sync_timestamp", 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSyncTime > 20_000L) {
            isSyncing = true
            scope.launch {
                try {
                    val result = syncTwoFaEntriesWithConfirmation(db)
                    entries = db.twoFADao().getAll()

                    if (result.pendingDecisions.isNotEmpty()) {
                        pendingEntries = result.pendingDecisions
                        showSyncDialog = true
                    }

                    if (result.error != null) {
                        Toast.makeText(context, "Offline: ${result.error}", Toast.LENGTH_LONG)
                            .show()
                        ERRORINSERT(
                            ERRORINSERTDATA(
                                "TwoFAListScreen",
                                "❌ Sync-Fehler: ${result.error}",
                                Instant.now().toString(),
                                "ERROR"
                            )
                        )
                    } else {
                        prefs.edit(commit = true) {
                            putLong(
                                "last_sync_timestamp",
                                System.currentTimeMillis()
                            )
                        }
                        if (result.uploaded > 0 || result.downloaded > 0) {
                            Toast.makeText(
                                context,
                                "Synchronisiert: ${result.uploaded} hochgeladen, ${result.downloaded} heruntergeladen",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Synchronisation fehlgeschlagen", Toast.LENGTH_LONG)
                        .show()
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            "TwoFAListScreen",
                            "❌ Sync-Exception: ${e.message}",
                            Instant.now().toString(),
                            "ERROR"
                        )
                    )
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    LaunchedEffect(selectedEntry, isEditingEntry) {
        while (selectedEntry != null && !isEditingEntry) {
            val now = System.currentTimeMillis()
            generatedCode =
                TotpGenerator.generateTOTP(selectedEntry!!.secret, now, periodSeconds = 30L)
            secondsLeft = (30 - (now / 1000) % 30).toInt()
            delay(1000)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Transparent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "🛡️ 2FA Codes",
                            color = TextP,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = AccentBlue)
                            }
                            IconButton(
                                onClick = { showScanner = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = AccentBlue)
                            }
                            IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Settings, null, tint = TextS)
                            }
                        }
                    }
                    Text("${entries.size} Einträge", color = TextS, fontSize = 12.sp)
                }
            }

            val duplicateNames = entries.groupBy { it.name }.filter { it.value.size > 1 }.keys
            if (duplicateNames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFCC00).copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            Color(0xFFFFCC00).copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚠️ Doppelte Einträge: ${duplicateNames.joinToString()}",
                        color = Color(0xFFFFCC00),
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛡️", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Noch keine 2FA-Einträge",
                            color = TextP,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tippe auf + oder scanne einen QR-Code",
                            color = TextS,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entries, key = { it.id }) { entry ->
                        TwoFACard(
                            entry = entry,
                            context = context,
                            onClick = { selectedEntry = entry },
                            onEdit = {
                                isEditingEntry = true
                                editedName = entry.name
                                selectedEntry = entry
                            },
                            onDelete = {
                                scope.launch {
                                    db.twoFADao().delete(entry)
                                    deleteTwoFaEntryFromSupabase(entry)
                                    entries = db.twoFADao().getAll()
                                }
                            },
                            onCopy = {
                                val code = TotpGenerator.generateTOTP(
                                    entry.secret,
                                    System.currentTimeMillis()
                                )
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", code))
                                Toast.makeText(
                                    context,
                                    "📋 Code für ${entry.name} kopiert",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        selectedEntry?.let { entry ->
            TwoFADetailOverlay(
                entry = entry,
                generatedCode = generatedCode,
                secondsLeft = secondsLeft,
                isEditing = isEditingEntry,
                editedName = editedName,
                onEditedNameChange = { editedName = it },
                onDismiss = { selectedEntry = null; isEditingEntry = false },
                onSaveEdit = {
                    scope.launch {
                        if (editedName.isNotBlank()) {
                            val updated = entry.copy(name = editedName)
                            db.twoFADao().update(updated)
                            updateTwoFaEntryInSupabase(updated)
                            entries = db.twoFADao().getAll()
                            selectedEntry = updated
                            isEditingEntry = false
                        }
                    }
                },
                onCancelEdit = { isEditingEntry = false; editedName = entry.name },
                onStartEdit = { isEditingEntry = true; editedName = entry.name },
                onDelete = {
                    scope.launch {
                        db.twoFADao().delete(entry)
                        deleteTwoFaEntryFromSupabase(entry)
                        entries = db.twoFADao().getAll()
                        selectedEntry = null
                    }
                },
                context = context
            )
        }

        if (showAddDialog) {
            TwoFAAddDialog(
                name = name,
                secret = secret,
                onNameChange = { name = it },
                onSecretChange = { secret = it },
                onDismiss = { showAddDialog = false },
                onSave = {
                    scope.launch {
                        val existing = db.twoFADao().getAll()
                        if (existing.any { it.secret == secret }) {
                            Toast.makeText(
                                context,
                                "Eintrag mit diesem Secret existiert bereits!",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }
                        val normalized =
                            secret.trim().replace(" ", "").uppercase(java.util.Locale.US)
                        if (!normalized.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567=" } || normalized.isEmpty()) {
                            Toast.makeText(
                                context,
                                "❌ Ungültiger Schlüssel (kein gültiges Base32)",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        val newEntry = TwoFAEntry(name = name.trim(), secret = normalized)
                        try {
                            db.twoFADao().insert(newEntry)
                            val ok = saveTwoFaEntryToSupabase(newEntry, db)
                            Toast.makeText(
                                context,
                                if (ok) "Gespeichert (lokal & Cloud)" else "Lokal gespeichert (Cloud-Sync fehlgeschlagen)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                        entries = db.twoFADao().getAll()
                        name = ""; secret = ""
                        showAddDialog = false
                    }
                }
            )
        }

        if (showSyncDialog && pendingEntries.isNotEmpty()) {
            SyncConfirmationDialog(
                entries = pendingEntries,
                onDecision = { entry, decision ->
                    scope.launch {
                        val success = processSyncDecision(db, entry, decision)
                        if (success) entries = db.twoFADao().getAll()
                    }
                },
                onDismiss = { showSyncDialog = false; pendingEntries = emptyList() }
            )
        }

        if (showScanner) {
            SilentCaptureScreen(onDismiss = { showScanner = false })
        }
    }
}

@Composable
private fun TwoFACard(
    entry: TwoFAEntry,
    context: Context,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconPrefs = context.getSharedPreferences("entry_icons", Context.MODE_PRIVATE)
    var iconUrl by remember { mutableStateOf(iconPrefs.getString(entry.secret, null) ?: "") }
    var showIconDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf(iconUrl) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Surface3, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentBlue.copy(alpha = 0.15f))
                    .clickable { inputUrl = iconUrl; showIconDialog = true },
                contentAlignment = Alignment.Center
            ) {
                when {
                    iconUrl.startsWith("data:image") -> {
                        val bytes = Base64.decode(iconUrl.substringAfter("base64,"), Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        Image(
                            bitmap.asImageBitmap(),
                            null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    iconUrl.isNotBlank() -> {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    else -> {
                        Text("🛡️", fontSize = 20.sp)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                entry.name,
                color = TextP,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )

            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, null, tint = TextS, modifier = Modifier.size(18.dp))
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Surface2)
        ) {
            DropdownMenuItem(
                text = { Text("✏️  Bearbeiten", color = TextP) },
                onClick = { showMenu = false; onEdit() })
            DropdownMenuItem(
                text = { Text("📋  Code kopieren", color = TextP) },
                onClick = { showMenu = false; onCopy() })
            DropdownMenuItem(
                text = { Text("🖼️  Icon ändern", color = TextP) },
                onClick = { showMenu = false; inputUrl = iconUrl; showIconDialog = true })
            HorizontalDivider(color = Surface3)
            DropdownMenuItem(
                text = { Text("🗑️  Löschen", color = AccentRed) },
                onClick = { showMenu = false; onDelete() })
        }
    }

    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            containerColor = Surface1,
            title = { Text("Icon-URL ändern", color = TextP) },
            text = {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("URL eingeben", color = TextT) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Surface3,
                        focusedTextColor = TextP,
                        unfocusedTextColor = TextP
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    iconPrefs.edit(commit = true) { putString(entry.secret, inputUrl) }
                    iconUrl = inputUrl
                    showIconDialog = false
                }) { Text("Speichern", color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showIconDialog = false }) {
                    Text(
                        "Abbrechen",
                        color = TextS
                    )
                }
            }
        )
    }
}

@Composable
private fun TwoFADetailOverlay(
    entry: TwoFAEntry,
    generatedCode: String,
    secondsLeft: Int,
    isEditing: Boolean,
    editedName: String,
    onEditedNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onStartEdit: () -> Unit,
    onDelete: () -> Unit,
    context: Context
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editedUrl by remember { mutableStateOf(entry.url) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .border(1.dp, Surface3, RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isEditing) {
                Text("✏️ Bearbeiten", color = TextP, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = editedName,
                    onValueChange = onEditedNameChange,
                    label = { Text("Name", color = TextT) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Surface3,
                        focusedTextColor = TextP,
                        unfocusedTextColor = TextP
                    )
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = editedUrl,
                    onValueChange = { editedUrl = it },
                    label = { Text("Website-URL", color = TextT) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Surface3,
                        focusedTextColor = TextP,
                        unfocusedTextColor = TextP
                    )
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onCancelEdit,
                        modifier = Modifier.weight(1f)
                    ) { Text("Abbrechen", color = TextS) }
                    Button(
                        onClick = onSaveEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f)
                    ) { Text("Speichern") }
                }
            } else {
                Text(
                    entry.name,
                    color = TextP,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .border(1.dp, Surface3, RoundedCornerShape(14.dp))
                        .clickable {
                            val cb =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("2FA Code", generatedCode))
                            Toast.makeText(context, "Code kopiert!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            generatedCode,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue,
                            letterSpacing = 6.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Gültig noch: ${secondsLeft}s", color = TextS, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue.copy(alpha = 0.08f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = TextS, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abgelaufene Codes sind noch ~10s gültig", color = TextS, fontSize = 11.sp)
                }

                Spacer(Modifier.height(20.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed),
                        modifier = Modifier.weight(1f)
                    ) { Text("Löschen") }

                    Button(
                        onClick = onStartEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = Surface3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(
                        Modifier.width(6.dp)
                    ); Text("Bearbeiten")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Surface1,
            title = { Text("Eintrag löschen?", color = TextP) },
            text = { Text("\"${entry.name}\" wird dauerhaft gelöscht.", color = TextS) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Löschen", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        "Abbrechen",
                        color = TextS
                    )
                }
            }
        )
    }
}

@Composable
private fun TwoFAAddDialog(
    name: String,
    secret: String,
    onNameChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val isValid = name.isNotBlank() && secret.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .border(1.dp, Surface3, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text(
                "➕ Neuer 2FA-Eintrag",
                color = TextP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            Column {
                Text("Name", color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    placeholder = { Text("z.B. GitHub", color = TextT) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Surface3,
                        focusedTextColor = TextP,
                        unfocusedTextColor = TextP,
                        cursorColor = AccentBlue
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            Column {
                Text(
                    "Secret-Schlüssel",
                    color = TextS,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = onSecretChange,
                    singleLine = true,
                    placeholder = { Text("Base32-Schlüssel", color = TextT) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Surface3,
                        focusedTextColor = TextP,
                        unfocusedTextColor = TextP,
                        cursorColor = AccentBlue
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Abbrechen", color = TextS)
                }
                Button(
                    onClick = onSave,
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.weight(1f)
                ) { Text("Hinzufügen") }
            }
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
        mutableStateOf(
            prefs.getBoolean(
                "screenshotProtectionEnabled",
                true
            )
        )
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Transparent)) {
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
                                lockEnabled = enabled
                                prefs.edit(commit = true) {
                                    putBoolean("lockEnabled", enabled)
                                    putBoolean("authenticated", !enabled)
                                }
                            },
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

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Zurück") }
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

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
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
                                                ERRORINSERT(
                                                    ERRORINSERTDATA(
                                                        "Capture Activity",
                                                        "❌ Ungültiges Format! ($uri)",
                                                        Instant.now().toString(),
                                                        "ERROR"
                                                    )
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
                                            ERRORINSERT(
                                                ERRORINSERTDATA(
                                                    "Capture Activity",
                                                    "❌ Kein Secret! ($uri)",
                                                    Instant.now().toString(),
                                                    "ERROR"
                                                )
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
                                        ERRORINSERT(
                                            ERRORINSERTDATA(
                                                "Capture Activity",
                                                "❌ ${e.message}",
                                                Instant.now().toString(),
                                                "ERROR"
                                            )
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