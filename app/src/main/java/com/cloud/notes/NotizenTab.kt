package com.cloud.notes

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*
import com.cloud.ui.theme.Gray
import androidx.core.content.edit

sealed class NoteType {
    data class Text(val content: String) : NoteType()
    data class Checklist(val items: List<ChecklistItem>) : NoteType()
}

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: NoteType,
    val timestamp: Long = System.currentTimeMillis(),
    val color: NoteColor = NoteColor.DEFAULT
)

enum class NoteColor(val color: Color) {
    DEFAULT(Gray),
    RED(Color(0xFFF28B82)),
    ORANGE(Color(0xFFFBBC04)),
    YELLOW(Color(0xFFFFF475)),
    GREEN(Color(0xFFCCFF90)),
    BLUE(Color(0xFF1666FF)),
    PURPLE(Color(0xFFCBB5F5))
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotizenApp() {
    val context = LocalContext.current

    var notes by remember { mutableStateOf(loadNotes(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }

    BackHandler(selectedNote !== null) {
        selectedNote = null
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Notiz erstellen")
            }
        },
        modifier = Modifier.background(Color.Transparent),
        containerColor = Color.Transparent
    ) { _ ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Keine Notizen vorhanden",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Gray),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        modifier = Modifier.background(Color.Gray),
                        note = note,
                        onClick = { selectedNote = note },
                        onDelete = { notes = notes.filter { it.id != note.id } }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNoteDialog(
            onDismiss = { showCreateDialog = false },
            onNoteCreated = { note ->
                notes = notes + note
                selectedNote = note
                showCreateDialog = false
            }
        )
    }

    selectedNote?.let { note ->
        EditNoteDialog(
            note = note,
            onDismiss = { selectedNote = null },
            onNoteSaved = { updatedNote ->
                notes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                selectedNote = null
                saveNotes(context, notes)
            },
            onDelete = {
                notes = notes.filter { it.id != note.id }
                selectedNote = null
            }
        )
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.color == NoteColor.DEFAULT)
                Color.White.copy(0.05f)
            else note.color.color
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Löschen",
                        modifier = Modifier.size(20.dp),
                        tint = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val type = note.type) {
                is NoteType.Text -> {
                    Text(
                        text = type.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5
                    )
                }

                is NoteType.Checklist -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        type.items.take(3).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = null,
                                    enabled = false,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                                )
                            }
                        }
                        if (type.items.size > 3) {
                            Text(
                                text = "+${type.items.size - 3} weitere",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateFormat.format(Date(note.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onNoteCreated: (Note) -> Unit
) {
    var noteTypeSelection by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Gray
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Notiztyp auswählen",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { noteTypeSelection = "text" },
                    border = BorderStroke(
                        2.dp,
                        if (noteTypeSelection == "text") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Textnotiz", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Normale Notiz mit Text",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { noteTypeSelection = "checklist" },
                    border = BorderStroke(
                        2.dp,
                        if (noteTypeSelection == "checklist") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Checkliste", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Liste mit Kontrollkästchen",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when (noteTypeSelection) {
                                "text" -> onNoteCreated(
                                    Note(
                                        title = "",
                                        type = NoteType.Text("")
                                    )
                                )

                                "checklist" -> onNoteCreated(
                                    Note(
                                        title = "Neue Checkliste",
                                        type = NoteType.Checklist(emptyList())
                                    )
                                )
                            }
                        },
                        enabled = noteTypeSelection != null
                    ) {
                        Text("Erstellen")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteDialog(
    note: Note,
    onDismiss: () -> Unit,
    onNoteSaved: (Note) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var textContent by remember { mutableStateOf(if (note.type is NoteType.Text) note.type.content else "") }
    var checklistItems by remember {
        mutableStateOf(if (note.type is NoteType.Checklist) note.type.items else emptyList())
    }
    var newItemText by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(note.color) }
    var showColorPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val titleColor = if (selectedColor == NoteColor.ORANGE ||
            selectedColor == NoteColor.YELLOW ||
            selectedColor == NoteColor.GREEN ||
            selectedColor == NoteColor.PURPLE
        ) {
            Color.Black
        } else {
            Color.White
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (selectedColor == NoteColor.DEFAULT) {
                        Gray
                    } else {
                        selectedColor.color
                    }
                )
        ) {
            TopAppBar(
                title = { Text("Notiz bearbeiten", color = titleColor) },
                colors = if (selectedColor == NoteColor.DEFAULT) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Gray
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = selectedColor.color
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = titleColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showColorPicker = !showColorPicker }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Farbe",
                            tint = titleColor
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Löschen",
                            tint = Color.Red
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )

            AnimatedVisibility(visible = showColorPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NoteColor.entries.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = color.color,
                                border = BorderStroke(
                                    2.dp,
                                    if (selectedColor == color) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            ) {}
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel", color = titleColor) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = titleColor,
                        unfocusedTextColor = titleColor,
                        focusedLabelColor = titleColor,
                        unfocusedLabelColor = titleColor.copy(alpha = 0.6f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (note.type) {
                    is NoteType.Text -> {
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = { textContent = it },
                            label = { Text("Notiz") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = titleColor,
                                unfocusedTextColor = titleColor,
                                focusedLabelColor = titleColor,
                                unfocusedLabelColor = titleColor.copy(alpha = 0.6f)
                            )
                        )
                    }

                    is NoteType.Checklist -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(checklistItems) { index, item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = item.isChecked,
                                        onCheckedChange = { checked ->
                                            checklistItems =
                                                checklistItems.toMutableList().apply {
                                                    this[index] = item.copy(isChecked = checked)
                                                }
                                        }
                                    )
                                    Text(
                                        text = item.text,
                                        modifier = Modifier.weight(1f),
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                                    )
                                    IconButton(
                                        onClick = {
                                            checklistItems =
                                                checklistItems.filterIndexed { i, _ -> i != index }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Entfernen"
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newItemText,
                                onValueChange = { newItemText = it },
                                label = { Text("Neuer Punkt") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    if (newItemText.isNotBlank()) {
                                        checklistItems =
                                            checklistItems + ChecklistItem(text = newItemText)
                                        newItemText = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val updatedNote = note.copy(
                        title = title,
                        type = when (note.type) {
                            is NoteType.Text -> NoteType.Text(textContent)
                            is NoteType.Checklist -> NoteType.Checklist(checklistItems)
                        },
                        color = selectedColor
                    )
                    onNoteSaved(updatedNote)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = title.isNotBlank()
            ) {
                Text("Speichern")
            }
        }
    }
}

fun saveNotes(context: Context, notes: List<Note>) {
    val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    val serialized = notes.joinToString("||") { note ->
        val typePart = when (val type = note.type) {
            is NoteType.Text ->
                "TEXT;;${type.content}"

            is NoteType.Checklist ->
                "CHECK;;" + type.items.joinToString(";;") {
                    "${it.id},${it.text},${it.isChecked}"
                }
        }

        listOf(
            note.id,
            note.title,
            note.timestamp.toString(),
            note.color.name,
            typePart
        ).joinToString("##")
    }

    prefs.edit(commit = true) { putString("notes", serialized) }
}

fun loadNotes(context: Context): List<Note> {
    val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    val raw = prefs.getString("notes", null) ?: return emptyList()

    return raw.split("||").mapNotNull { entry ->
        val parts = entry.split("##")
        if (parts.size < 5) return@mapNotNull null

        val (id, title, timestamp, colorName, typeRaw) = parts

        val type = when {
            typeRaw.startsWith("TEXT;;") ->
                NoteType.Text(typeRaw.removePrefix("TEXT;;"))

            typeRaw.startsWith("CHECK;;") -> {
                val items = typeRaw
                    .removePrefix("CHECK;;")
                    .takeIf { it.isNotBlank() }
                    ?.split(";;")
                    ?.map {
                        val i = it.split(",")
                        ChecklistItem(
                            id = i[0],
                            text = i[1],
                            isChecked = i[2].toBoolean()
                        )
                    } ?: emptyList()

                NoteType.Checklist(items)
            }

            else -> return@mapNotNull null
        }

        Note(
            id = id,
            title = title,
            timestamp = timestamp.toLong(),
            color = NoteColor.valueOf(colorName),
            type = type
        )
    }
}