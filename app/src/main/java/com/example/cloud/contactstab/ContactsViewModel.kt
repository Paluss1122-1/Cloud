package com.example.cloud.contactstab

import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue

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
            }
        }
    }

    fun saveContact(contact: Contact) {
        viewModelScope.launch {
            try {
                val success = repository.saveContact(contact)
                if (success) {
                    loadContacts() // Neu laden nach Speichern
                } else {
                    state = state.copy(error = "Fehler beim Speichern")
                }
            } catch (e: Exception) {
                state = state.copy(error = e.message)
            }
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteContact(id)
                if (success) {
                    loadContacts() // Neu laden nach Löschen
                } else {
                    state = state.copy(error = "Fehler beim Löschen")
                }
            } catch (e: Exception) {
                state = state.copy(error = e.message)
            }
        }
    }
}
