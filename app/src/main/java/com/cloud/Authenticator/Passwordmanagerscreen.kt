package com.cloud.authenticator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Surface1 = Color(0xFF17171C)
private val Surface2 = Color(0xFF1F1F27)
private val Surface3 = Color(0xFF2A2A35)
private val AccentBlue = Color(0xFF4A90E2)
private val AccentRed = Color(0xFFE74C3C)
private val TextP = Color(0xFFEEEEF5)
private val TextS = Color(0xFF8A8A9F)
private val TextT = Color(0xFF55556A)

private val categoryColor = mapOf(
    "Social" to Color(0xFF4267B2),
    "Banking" to Color(0xFF27AE60),
    "E-Mail" to Color(0xFFE74C3C),
    "Shopping" to Color(0xFFFF6B00),
    "Arbeit" to Color(0xFF8E44AD),
    "Gaming" to Color(0xFF2980B9),
    "Andere" to Color(0xFF5D6D7E)
)

@Composable
fun PasswordManagerScreen(db: PasswordDatabase) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var entries by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Alle") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var detailEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            isLoading = true
            entries = db.passwordDao().getAll()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val visible = remember(entries, searchQuery, selectedCategory) {
        entries.filter { e ->
            val q = searchQuery.trim()
            val matchSearch = q.isEmpty() ||
                    e.name.contains(q, true) ||
                    e.username.contains(q, true) ||
                    e.url.contains(q, true)
            val matchCat = selectedCategory == "Alle" || e.category == selectedCategory
            matchSearch && matchCat
        }
    }

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
                        "🔐 Passwörter",
                        color = TextP,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Add, "Hinzufügen", tint = AccentBlue)
                    }
                }
                Text(
                    "${entries.size} Einträge",
                    color = TextS,
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Suche...", color = TextT) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextS) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, null, tint = TextS)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Surface3,
                focusedTextColor = TextP,
                unfocusedTextColor = TextP,
                cursorColor = AccentBlue
            )
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PASSWORD_CATEGORIES.forEach { cat ->
                val selected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) AccentBlue else Surface2)
                        .border(
                            1.dp,
                            if (selected) AccentBlue else Surface3,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        cat,
                        color = if (selected) Color.White else TextS,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AccentBlue) }
        } else if (visible.isEmpty()) {
            EmptyState(hasEntries = entries.isNotEmpty())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visible, key = { it.id }) { entry ->
                    PasswordCard(
                        entry = entry,
                        onClick = { detailEntry = entry },
                        onEdit = { editEntry = entry },
                        onDelete = {
                            scope.launch {
                                db.passwordDao().delete(entry)
                                reload()
                            }
                        },
                        onCopy = {
                            scope.launch {
                                val plain = withContext(Dispatchers.Default) {
                                    PasswordCrypto.decrypt(entry.encryptedPassword)
                                }
                                copyToClipboard(context, "Passwort", plain)
                            }
                        },
                        onToggleFavorite = {
                            scope.launch {
                                db.passwordDao()
                                    .update(entry.copy(isFavorite = !entry.isFavorite))
                                reload()
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddEditPasswordDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { newEntry ->
                scope.launch {
                    db.passwordDao().insert(newEntry)
                    showAddDialog = false
                    reload()
                    Toast.makeText(context, "✅ Eintrag gespeichert", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    editEntry?.let { entry ->
        AddEditPasswordDialog(
            initial = entry,
            onDismiss = { editEntry = null },
            onSave = { updated ->
                scope.launch {
                    db.passwordDao().update(updated.copy(updatedAt = System.currentTimeMillis()))
                    editEntry = null
                    reload()
                    Toast.makeText(context, "✅ Aktualisiert", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    detailEntry?.let { entry ->
        PasswordDetailSheet(
            entry = entry,
            onDismiss = { detailEntry = null },
            onEdit = { detailEntry = null; editEntry = entry },
            onDelete = {
                scope.launch {
                    db.passwordDao().delete(entry)
                    detailEntry = null
                    reload()
                }
            }
        )
    }
}

@Composable
private fun PasswordCard(
    entry: PasswordEntry,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val accent = categoryColor[entry.category] ?: Color(0xFF5D6D7E)
    val initials = entry.name.take(2).uppercase()

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
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp))
                .background(accent)
        )

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    CATEGORY_ICONS[entry.category] ?: initials,
                    fontSize = if (CATEGORY_ICONS.containsKey(entry.category)) 20.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = TextP,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.username.isNotEmpty()) {
                    Text(
                        entry.username,
                        color = TextS,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (entry.isFavorite) {
                Text("⭐", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
            }

            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    "Kopieren",
                    tint = TextS,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Surface2)
        ) {
            DropdownMenuItem(
                text = { Text("✏️  Bearbeiten", color = TextP) },
                onClick = { showMenu = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("📋  Passwort kopieren", color = TextP) },
                onClick = { showMenu = false; onCopy() }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (entry.isFavorite) "💔  Favorit entfernen" else "⭐  Favorit",
                        color = TextP
                    )
                },
                onClick = { showMenu = false; onToggleFavorite() }
            )
            HorizontalDivider(color = Surface3)
            DropdownMenuItem(
                text = { Text("🗑️  Löschen", color = AccentRed) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}

@Composable
private fun PasswordDetailSheet(
    entry: PasswordEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var plainPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        plainPassword = withContext(Dispatchers.Default) {
            PasswordCrypto.decrypt(entry.encryptedPassword)
        }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    CATEGORY_ICONS[entry.category] ?: "🔑",
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, color = TextP, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(entry.category, color = TextS, fontSize = 12.sp)
                }
                if (entry.isFavorite) Text("⭐", fontSize = 18.sp)
            }

            Spacer(Modifier.height(20.dp))

            if (entry.url.isNotEmpty()) {
                DetailField(
                    label = "🌐 URL",
                    value = entry.url,
                    onCopy = { copyToClipboard(context, "URL", entry.url) }
                )
                Spacer(Modifier.height(12.dp))
            }

            if (entry.username.isNotEmpty()) {
                DetailField(
                    label = "👤 Benutzername",
                    value = entry.username,
                    onCopy = { copyToClipboard(context, "Benutzername", entry.username) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Column {
                Text(
                    "🔒 Passwort",
                    color = TextS,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPassword) plainPassword else "•".repeat(
                            plainPassword.length.coerceAtMost(
                                20
                            )
                        ),
                        color = if (showPassword) TextP else TextS,
                        fontSize = 14.sp,
                        fontFamily = if (showPassword) FontFamily.Monospace else FontFamily.Default,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = TextS, modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { copyToClipboard(context, "Passwort", plainPassword) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (plainPassword.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    StrengthBar(PasswordGenerator.strength(plainPassword))
                }
            }

            if (entry.notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column {
                    Text(
                        "📝 Notizen",
                        color = TextS,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface2)
                            .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(entry.notes, color = TextP, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed),
                    modifier = Modifier.weight(1f)
                ) { Text("Löschen") }

                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.weight(1f)
                ) { Text("Bearbeiten") }
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
                    Text("Abbrechen", color = TextS)
                }
            }
        )
    }
}

@Composable
private fun DetailField(label: String, value: String, onCopy: () -> Unit) {
    Column {
        Text(label, color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
                .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                color = TextP,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, null, tint = TextS, modifier = Modifier.size(16.dp))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordDialog(
    initial: PasswordEntry?,
    onDismiss: () -> Unit,
    onSave: (PasswordEntry) -> Unit
) {
    val isEdit = initial != null

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Andere") }
    var isFavorite by remember { mutableStateOf(initial?.isFavorite ?: false) }
    var showPass by remember { mutableStateOf(false) }
    var showGen by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(initial) {
        if (initial != null) {
            password = withContext(Dispatchers.Default) {
                PasswordCrypto.decrypt(initial.encryptedPassword)
            }
        }
    }

    val strength = remember(password) { PasswordGenerator.strength(password) }
    val isValid = name.isNotBlank() && password.isNotBlank()

    if (showGen) {
        PasswordGeneratorSheet(
            onAccept = { generated -> password = generated; showGen = false },
            onDismiss = { showGen = false }
        )
        return
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
                .background(Surface1)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEdit) "✏️  Bearbeiten" else "➕  Neuer Eintrag",
                    color = TextP,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Text(if (isFavorite) "⭐" else "☆", fontSize = 20.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            PwField(label = "Name *", value = name, onValueChange = { name = it })
            Spacer(Modifier.height(12.dp))

            PwField(
                label = "URL / Domain", value = url, onValueChange = { url = it },
                keyboardType = KeyboardType.Uri
            )
            Spacer(Modifier.height(12.dp))

            PwField(
                label = "Benutzername / E-Mail",
                value = username,
                onValueChange = { username = it },
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(12.dp))

            Column {
                Text(
                    "Passwort *",
                    color = TextS,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextP,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    )
                    IconButton(
                        onClick = { showPass = !showPass },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = TextS, modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { showGen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            "Generieren",
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    StrengthBar(strength)
                }
            }

            Spacer(Modifier.height(12.dp))

            Column {
                Text("Kategorie", color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${CATEGORY_ICONS[category] ?: "🔑"} $category",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryEditable,
                                enabled = true
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Surface3,
                            focusedTextColor = TextP,
                            unfocusedTextColor = TextP
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false },
                        modifier = Modifier.background(Surface2)
                    ) {
                        PASSWORD_CATEGORIES.drop(1).forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${CATEGORY_ICONS[cat] ?: "🔑"}  $cat",
                                        color = TextP
                                    )
                                },
                                onClick = { category = cat; catExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Column {
                Text("Notizen", color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    maxLines = 4,
                    placeholder = { Text("Optional...", color = TextT) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Surface3,
                        focusedTextColor = TextP,
                        unfocusedTextColor = TextP
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Abbrechen", color = TextS) }

                Button(
                    onClick = {
                        val encrypted = PasswordCrypto.encrypt(password)
                        val entry = if (isEdit && initial != null) {
                            initial.copy(
                                name = name.trim(),
                                url = url.trim(),
                                username = username.trim(),
                                encryptedPassword = encrypted,
                                notes = notes.trim(),
                                category = category,
                                isFavorite = isFavorite
                            )
                        } else {
                            PasswordEntry(
                                name = name.trim(),
                                url = url.trim(),
                                username = username.trim(),
                                encryptedPassword = encrypted,
                                notes = notes.trim(),
                                category = category,
                                isFavorite = isFavorite
                            )
                        }
                        onSave(entry)
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.weight(1f)
                ) { Text("Speichern") }
            }
        }
    }
}


@Composable
fun PasswordGeneratorSheet(
    onAccept: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var length by remember { mutableStateOf(20f) }
    var useLower by remember { mutableStateOf(true) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var noAmbiguous by remember { mutableStateOf(false) }
    var generated by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(length, useLower, useUpper, useDigits, useSymbols, noAmbiguous) {
        generated = PasswordGenerator.generate(
            length = length.toInt(),
            lower = useLower,
            upper = useUpper,
            digits = useDigits,
            symbols = useSymbols,
            noAmbiguous = noAmbiguous
        )
    }

    val strength = remember(generated) { PasswordGenerator.strength(generated) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .padding(24.dp)
        ) {
            Text(
                "⚡ Passwort-Generator",
                color = TextP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .border(1.dp, Surface3, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        generated,
                        color = TextP,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { copyToClipboard(context, "Passwort", generated) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            null,
                            tint = TextS,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            generated = PasswordGenerator.generate(
                                length.toInt(),
                                useLower,
                                useUpper,
                                useDigits,
                                useSymbols,
                                noAmbiguous
                            )
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            StrengthBar(strength)
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Länge:", color = TextS, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                Slider(
                    value = length,
                    onValueChange = { length = it },
                    valueRange = 8f..64f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue
                    )
                )
                Text(
                    "${length.toInt()}",
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            ToggleRow("Kleinbuchstaben (a-z)", useLower) { useLower = it }
            ToggleRow("Großbuchstaben (A-Z)", useUpper) { useUpper = it }
            ToggleRow("Ziffern (0-9)", useDigits) { useDigits = it }
            ToggleRow("Sonderzeichen (!@#...)", useSymbols) { useSymbols = it }
            ToggleRow("Keine ähnl. Zeichen (0Ol1)", noAmbiguous) { noAmbiguous = it }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Abbrechen", color = TextS)
                }
                Button(
                    onClick = { onAccept(generated) },
                    enabled = generated.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.weight(1f)
                ) { Text("Übernehmen") }
            }
        }
    }
}


@Composable
fun StrengthBar(strength: PasswordStrength) {
    val animFraction by animateFloatAsState(
        targetValue = strength.fraction,
        animationSpec = tween(400),
        label = "strength"
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Stärke:", color = TextT, fontSize = 11.sp)
            Text(
                strength.label,
                color = strength.color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Surface3)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(strength.color)
            )
        }
    }
}

@Composable
private fun PwField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(label, color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextP, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentBlue,
                uncheckedTrackColor = Surface3
            )
        )
    }
}

@Composable
private fun EmptyState(hasEntries: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hasEntries) "🔍" else "🔐", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasEntries) "Keine Treffer" else "Noch keine Passwörter",
                color = TextP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (hasEntries) "Passe die Suche an" else "Tippe auf + um loszulegen",
                color = TextS, fontSize = 14.sp
            )
        }
    }
}


fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "📋 $label kopiert", Toast.LENGTH_SHORT).show()
}