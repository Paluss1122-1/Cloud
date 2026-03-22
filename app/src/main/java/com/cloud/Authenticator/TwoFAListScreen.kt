package com.cloud.authenticator

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.cloud.ui.theme.c
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

/*@Composable
@Preview
fun Start() {
    val navController = rememberNavController()
    val context = LocalContext.current
    NavHost(navController, startDestination = "list") {
        composable("list") {
            TwoFAListScreen(
                db = TwoFADatabase.getDatabase(context),
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreenWithScreenshotProtection(onBackClick = { navController.popBackStack() })
        }
    }
}*/

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

    val context = LocalContext.current

    LaunchedEffect(true) {
        if (isSyncing) return@LaunchedEffect

        entries = db.twoFADao().getAll()

        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_sync_timestamp", 0L)
        val currentTime = System.currentTimeMillis()
        val fiveMinutesInMillis = 1 * 20 * 1000L

        if (currentTime - lastSyncTime > fiveMinutesInMillis) {
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
                        Toast.makeText(
                            context,
                            "Offline: ${result.error}",
                            Toast.LENGTH_LONG
                        ).show()
                        ERRORINSERT(
                            ERRORINSERTDATA(
                                "TwoFAListScreen",
                                "❌ Sync-Fehler: ${result.error}",
                                Instant.now().toString(),
                                "Error"
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
                    Toast.makeText(
                        context,
                        "Synchronisation fehlgeschlagen: Keine Internetverbindung",
                        Toast.LENGTH_LONG
                    ).show()
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            "TwoFAListScreen",
                            "❌ Sync-Exception: ${e.message}",
                            Instant.now().toString(),
                            "Error"
                        )
                    )
                } finally {
                    isSyncing = false
                }
            }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
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
                        Toast.makeText(context, "Liste aktualisiert!", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("Aktualisieren", color = Color.White)
            }

            val groupedEntries = entries
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

                val duplicateNames = groupedEntries
                    .groupBy { it.name }
                    .filter { it.value.size > 1 }
                    .keys

                if (duplicateNames.isNotEmpty()) {
                    item {
                        Text(
                            "⚠️ Doppelte Einträge gefunden: ${duplicateNames.joinToString()}",
                            color = Color(0xFFFFCC00),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                items(groupedEntries, key = { it.id }) { entry ->
                    val iconPrefs =
                        context.getSharedPreferences("entry_icons", Context.MODE_PRIVATE)
                    var iconUrl by remember {
                        mutableStateOf(
                            iconPrefs.getString(
                                entry.secret,
                                null
                            ) ?: ""
                        )
                    }
                    var showIconDialog by remember { mutableStateOf(false) }
                    var inputUrl by remember { mutableStateOf(iconUrl) }

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = c()),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (iconUrl.isNotBlank()) {
                                if (iconUrl.startsWith("data:image")) {
                                    // Base64 Image
                                    val base64String = iconUrl.substringAfter("base64,")
                                    val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                                    val bitmap = BitmapFactory.decodeByteArray(
                                        imageBytes,
                                        0,
                                        imageBytes.size
                                    )

                                    IconButton(onClick = {
                                        inputUrl = iconUrl
                                        showIconDialog = true
                                    }) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Icon für ${entry.name}",
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    }
                                } else {
                                    IconButton(onClick = {
                                        inputUrl = iconUrl
                                        showIconDialog = true
                                    }) {
                                        AsyncImage(
                                            model = iconUrl,
                                            contentDescription = "Icon für ${entry.name}",
                                            modifier = Modifier.size(60.dp)
                                        )
                                    }
                                }
                            } else {
                                IconButton(onClick = {
                                    inputUrl = iconUrl
                                    showIconDialog = true
                                }) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Icon ändern",
                                        tint = Color.White,
                                        modifier = Modifier.size(60.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (showIconDialog) {
                        AlertDialog(
                            onDismissRequest = { showIconDialog = false },
                            title = { Text("Icon-URL ändern") },
                            text = {
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    label = { Text("URL eingeben") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    iconPrefs.edit(commit = true) {
                                        putString(entry.secret, inputUrl)
                                        apply()
                                    }
                                    iconUrl = inputUrl
                                    showIconDialog = false
                                }) { Text("Speichern") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showIconDialog = false
                                }) { Text("Abbrechen") }
                            }
                        )
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
                    onClick = { showAddDialog = true }
                ) {
                    Text("Hinzufügen", fontSize = 15.sp, color = Color.White)
                }

                Button(
                    onClick = {
                        val permissionCheck =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            )
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            showScanner = true
                        } else {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data =
                                            Uri.fromParts("package", context.packageName, null)
                                        if (context !is Activity) {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallbackIntent =
                                        Intent(Settings.ACTION_SETTINGS).apply {
                                            if (context !is Activity) {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                        }
                                    context.startActivity(fallbackIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Bitte Einstellungen manuell öffnen",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    scope.launch {
                                        ERRORINSERT(
                                            ERRORINSERTDATA(
                                                "TwoFAListScreen",
                                                "Konnte Einstellungen nicht öffnen: ${e.message}",
                                                Instant.now().toString(),
                                                "Error"
                                            )
                                        )
                                    }
                                }
                            }
                            Toast.makeText(
                                context,
                                "Erlaube Kamera Zugriff in den Einstellungen",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("QR-Code scannen", fontSize = 15.sp, color = Color.White)
                }
            }
        }

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

                            var editedUrl by remember { mutableStateOf(entry.url) }

                            OutlinedTextField(
                                value = editedUrl,
                                onValueChange = { editedUrl = it },
                                label = {
                                    Text(
                                        "Website-URL (z.B. github.com)",
                                        color = Color.Gray
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFB388FF),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

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
                                                    url = editedUrl
                                                )
                                                db.twoFADao().update(updatedEntry)

                                                val supabaseSuccess =
                                                    updateTwoFaEntryInSupabase(updatedEntry)

                                                if (!supabaseSuccess) {
                                                    scope.launch {
                                                        ERRORINSERT(
                                                            ERRORINSERTDATA(
                                                                "TwoFAListScreen",
                                                                "Supabase-Update fehlgeschlagen, aber lokal gespeichert",
                                                                Instant.now().toString(),
                                                                "Warning"
                                                            )
                                                        )
                                                    }
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
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                                ) {
                                    Text("Abbrechen", color = Color.White)
                                }
                            }
                        } else {
                            Text(
                                entry.name,
                                fontSize = 18.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clickable {
                                        isEditingEntry = true
                                        editedName = entry.name
                                    }
                                    .fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Code: $generatedCode",
                            fontSize = 30.sp,
                            textAlign = TextAlign.Center,
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
                                Toast.makeText(context, "Code kopiert!", Toast.LENGTH_SHORT)
                                    .show()
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
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
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
                                Toast.makeText(context, "Code kopiert!", Toast.LENGTH_SHORT)
                                    .show()
                                selectedEntry = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(
                                    0xFF9B4DCA
                                )
                            ),
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
                                    favPrefs.edit(commit = true) {
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
                                            favPrefs.edit(commit = true) {
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
                                                favPrefs.edit(commit = true) {
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
                                                deleteTwoFaEntryFromSupabase(entry)
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
                        Text(
                            "Neuen Code hinzufügen",
                            style = MaterialTheme.typography.titleLarge
                        )
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

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank() && secret.isNotBlank()) {
                                    scope.launch {
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
                                            val normalizedSecret =
                                                secret.trim().replace(" ", "")
                                                    .uppercase(java.util.Locale.US)
                                            val isValidBase32 =
                                                normalizedSecret.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567=" }

                                            if (!isValidBase32 || normalizedSecret.isEmpty()) {
                                                Toast.makeText(
                                                    context,
                                                    "❌ Ungültiger Schlüssel (kein gültiges Base32)",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                return@launch
                                            }

                                            val newEntry = TwoFAEntry(
                                                name = name.trim(),
                                                secret = normalizedSecret
                                            )

                                            try {
                                                db.twoFADao().insert(newEntry)

                                                val supabaseSuccess =
                                                    saveTwoFaEntryToSupabase(newEntry, db)

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
                                                scope.launch {
                                                    ERRORINSERT(
                                                        ERRORINSERTDATA(
                                                            "TwoFAListScreen",
                                                            "Fehler beim Speichern: ${e.message}",
                                                            Instant.now().toString(),
                                                            "Error"
                                                        )
                                                    )
                                                }
                                            }

                                            entries = db.twoFADao().getAll()
                                            name = ""
                                            secret = ""
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

        if (showScanner) {
            SilentCaptureScreen(onDismiss = { showScanner = false })
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
            .background(Color.Transparent)
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

            Spacer(Modifier.height(8.dp))

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
                        prefs.edit(commit = true) {
                            putBoolean("lockEnabled", enabled)
                            putBoolean("authenticated", !enabled)
                            apply()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedIconColor = c(),
                        checkedTrackColor = c(),
                        checkedBorderColor = c(),
                    )
                )
                Text(
                    text = if (lockEnabled) "App-Sperre aktiviert" else "App-Sperre deaktiviert",
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

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
                        prefs.edit(commit = true) {
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
                onClick = onBackClick
            ) {
                Text("Zurück", color = Color.White)
            }

            val autofillIntent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = "package:${context.packageName}".toUri()
            }
            Button(
                onClick = { context.startActivity(autofillIntent) }
            ) {
                Text("Autofill aktivieren", color = Color.White)
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

@Composable
fun SilentCaptureScreen(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }

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
                                                ERRORINSERT(
                                                    ERRORINSERTDATA(
                                                        "Capture Activity",
                                                        "❌ Ungültiges Format! (${uri})",
                                                        Instant.now().toString(),
                                                        "Error"
                                                    )
                                                )
                                                isProcessing = false
                                                onDismiss()
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
                                                    "❌ Kein Secret gefunden! (uri: $uri, secret: $secretParam)",
                                                    Instant.now().toString(),
                                                    "Error"
                                                )
                                            )
                                            isProcessing = false
                                            onDismiss()
                                            return@launch
                                        }

                                        val db = TwoFADatabase.getDatabase(context)

                                        val existingEntries = db.twoFADao().getAll()
                                        val alreadyExists = existingEntries.any {
                                            it.secret == secretParam || it.name.equals(
                                                displayName,
                                                ignoreCase = true
                                            )
                                        }

                                        if (alreadyExists) {
                                            Toast.makeText(
                                                context,
                                                "⚠️ Eintrag existiert bereits!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false
                                            onDismiss()
                                            ERRORINSERT(
                                                ERRORINSERTDATA(
                                                    "Capture Activity",
                                                    "⚠️ Eintrag existiert bereits! (secret: ${secretParam}, name: $displayName)",
                                                    Instant.now().toString(),
                                                    "Warning"
                                                )
                                            )
                                            return@launch
                                        }

                                        val newEntry = TwoFAEntry(
                                            name = displayName,
                                            secret = secretParam
                                        )

                                        val inserted = db.twoFADao().insertOrIgnore(newEntry)
                                        if (inserted == -1L) {
                                            Toast.makeText(
                                                context,
                                                "⚠️ Eintrag existiert bereits!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false
                                            onDismiss()
                                            return@launch
                                        }

                                        val supabaseSuccess = saveTwoFaEntryToSupabase(newEntry, db)

                                        withContext(Dispatchers.Main) {
                                            val message = if (supabaseSuccess) {
                                                "✅ Token für $displayName hinzugefügt (lokal & Cloud)!"
                                            } else {
                                                "✅ Token für $displayName hinzugefügt (Cloud-Sync fehlgeschlagen)"
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG)
                                                .show()

                                            isProcessing = false
                                            onDismiss()
                                        }

                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "❌ Fehler: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        isProcessing = false
                                        onDismiss()
                                        ERRORINSERT(
                                            ERRORINSERTDATA(
                                                "Capture Activity",
                                                "❌ Fehler: ${e.message}",
                                                Instant.now().toString(),
                                                "Error"
                                            )
                                        )
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
                    .border(4.dp, Color(0xFF9B4DCA), RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
            )
            Spacer(Modifier.height(40.dp))
            Text(
                text = "Halte den QR-Code in den Rahmen",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}