package com.cloud.contactstab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun ContactsTabContent(
    state: ContactsTabState,
    onLoadContacts: () -> Unit,
    onSaveContact: (Contact) -> Unit,
    onDeleteContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onLoadContacts() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("Kontakte laden", fontSize = 18.sp)
            }

            Button(
                onClick = {
                    selectedContact = Contact("", "", "", "")
                    showDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("Neu erstellen", fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            state.loading -> {
                CircularProgressIndicator(color = Color(0xFFFFA500))
                Spacer(Modifier.height(8.dp))
                Text("Lade Kontakte…", color = Color.White)
            }
            state.error != null -> {
                Text("Fehler: ${state.error}", color = Color.Red)
                LaunchedEffect(Unit) {
                    scope.launch {
                        ERRORINSERT(
                            ERRORINSERTDATA(
                                "tabcontentoriginal",
                                "❌ Fehler beim Laden von Kontakten: ${state.error}",
                                Instant.now().toString(),
                                "Error"
                            )
                        )
                    }
                }
            }
            state.contacts.isEmpty() -> {
                Text("Keine Kontakte vorhanden", color = Color.Gray, fontSize = 16.sp)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.contacts) { contact ->
                        ContactCard(
                            contact = contact,
                            onEdit = {
                                selectedContact = contact
                                showDialog = true
                            },
                            onDelete = { onDeleteContact(contact.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog && selectedContact != null) {
        ContactEditDialog(
            contact = selectedContact!!,
            onDismiss = { showDialog = false },
            onSave = { updatedContact ->
                onSaveContact(updatedContact)
                showDialog = false
            }
        )
    }
}

@Composable
fun ContactCard(
    contact: Contact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = contact.name,
                color = Color.White,
                fontSize = 20.sp,
                style = MaterialTheme.typography.titleMedium
            )
            if (contact.email.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "📧 ${contact.email}",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
            }
            if (contact.phone.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "📱 ${contact.phone}",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Bearbeiten", fontSize = 14.sp)
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Löschen", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ContactEditDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (Contact) -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var email by remember { mutableStateOf(contact.email) }
    var phone by remember { mutableStateOf(contact.phone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (contact.id.isEmpty()) "Neuer Kontakt" else "Kontakt bearbeiten")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefon") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedContact = contact.copy(
                        name = name,
                        email = email,
                        phone = phone
                    )
                    onSave(updatedContact)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
