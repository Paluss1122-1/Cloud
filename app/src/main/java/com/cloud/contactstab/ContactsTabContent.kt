package com.example.cloud.contactstab

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.example.cloud.ERRORINSERT
import com.example.cloud.ERRORINSERTDATA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

data class Contact(
    val id: String,
    val name: String,
    val email: String,
    val phone: String
)

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
                    "Error"
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