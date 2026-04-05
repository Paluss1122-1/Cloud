package com.cloud.contactstab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import kotlinx.coroutines.launch
import java.time.Instant

data class ContactsTabState(
    val contacts: List<Contact> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {
    var state by androidx.compose.runtime.mutableStateOf(ContactsTabState())
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
