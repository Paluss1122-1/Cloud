@file:Suppress("AssignedValueIsNeverRead")

package com.cloud.tabs

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloud.core.functions.ERRORINSERT
import com.cloud.core.functions.ERRORINSERTDATA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class Contact(
    val id: String,
    val name: String,
    val email: String,
    val phone: String
)

data class ContactsTabState(
    val contacts: List<Contact> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

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

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(100)
        alpha.animateTo(
            1f, animationSpec = tween(
                durationMillis = 150,
                easing = FastOutSlowInEasing
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp)
            .alpha(alpha.value),
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
                                "ERROR"
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
                    items(state.contacts) {
                        ContactCard(
                            contact = it,
                            onEdit = {
                                selectedContact = it
                                showDialog = true
                            },
                            onDelete = { onDeleteContact(it.id) }
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
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(300))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .alpha(alpha.value),
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

class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {
    var state by mutableStateOf(ContactsTabState())
        private set

    fun loadContacts() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val contacts = repository.loadContacts()
                state = state.copy(loading = false, contacts = contacts)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message)
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "ContactsViewModel",
                        "❌ Fehler beim Laden von von Kontakten: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
        }
    }

    fun saveContact(contact: Contact) {
        viewModelScope.launch {
            try {
                val success = repository.saveContact(contact)
                if (success) {
                    loadContacts()
                } else {
                    state = state.copy(error = "Fehler beim Speichern")
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            "ContactsViewModel",
                            "❌ Fehler beim Speichern von Kontakten",
                            Instant.now().toString(),
                            "ERROR"
                        )
                    )
                }
            } catch (e: Exception) {
                state = state.copy(error = e.message)
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "ContactsViewModel",
                        "❌ Fehler beim Speichern von Kontakten: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteContact(id)
                if (success) {
                    loadContacts()
                } else {
                    state = state.copy(error = "Fehler beim Löschen")
                    ERRORINSERT(
                        ERRORINSERTDATA(
                            "ContactsViewModel",
                            "❌ Fehler beim Löschen von Kontakten",
                            Instant.now().toString(),
                            "ERROR"
                        )
                    )
                }
            } catch (e: Exception) {
                state = state.copy(error = e.message)
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "ContactsViewModel",
                        "❌ Fehler beim Löschen von Kontakten: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
        }
    }
}

class ContactsRepository(private val context: Context) {

    suspend fun loadContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()

        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} IS NOT NULL AND ${ContactsContract.Contacts.DISPLAY_NAME} != ''",
            null,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: ""

                val email = getContactEmail(id)
                val phone = getContactPhone(id)

                contacts.add(Contact(id, name, email, phone))
            }
        }

        contacts
    }

    private fun getContactEmail(contactId: String): String {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            null,
            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
            arrayOf(contactId),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                return it.getString(emailIndex) ?: ""
            }
        }
        return ""
    }

    private fun getContactPhone(contactId: String): String {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
            arrayOf(contactId),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return it.getString(phoneIndex) ?: ""
            }
        }
        return ""
    }

    suspend fun saveContact(contact: Contact): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<ContentProviderOperation>()

            if (contact.id.isEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
                        .build()
                )

                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            contact.name
                        )
                        .build()
                )

                if (contact.email.isNotEmpty()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                            )
                            .withValue(
                                ContactsContract.CommonDataKinds.Email.ADDRESS,
                                contact.email
                            )
                            .withValue(
                                ContactsContract.CommonDataKinds.Email.TYPE,
                                ContactsContract.CommonDataKinds.Email.TYPE_HOME
                            )
                            .build()
                    )
                }

                if (contact.phone.isNotEmpty()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phone)
                            .withValue(
                                ContactsContract.CommonDataKinds.Phone.TYPE,
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                            )
                            .build()
                    )
                }
            } else {
                updateContactName(contact.id, contact.name, ops)
                updateContactEmail(contact.id, contact.email, ops)
                updateContactPhone(contact.id, contact.phone, ops)
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "ContactsTabContent",
                    "Fehler beim Speichern von Kontakt: $contact: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            )
            false
        }
    }

    private fun updateContactName(
        contactId: String,
        name: String,
        ops: ArrayList<ContentProviderOperation>
    ) {
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(
                        contactId,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )
    }

    private fun updateContactEmail(
        contactId: String,
        email: String,
        ops: ArrayList<ContentProviderOperation>
    ) {
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                .build()
        )
    }

    private fun updateContactPhone(
        contactId: String,
        phone: String,
        ops: ArrayList<ContentProviderOperation>
    ) {
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .build()
        )
    }

    suspend fun deleteContact(contactId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build()

            context.contentResolver.delete(
                uri,
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId)
            )
            true
        } catch (e: Exception) {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "ContactsTabContent",
                    "Fehler bei Löschen von Kontakten: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
            false
        }
    }
}