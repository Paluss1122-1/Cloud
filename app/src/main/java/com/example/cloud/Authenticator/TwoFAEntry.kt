package com.example.cloud.Authenticator

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "twofa_entries")
data class TwoFAEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supabaseId: String? = null,
    val name: String,
    val secret: String,
    val folder: String? = null
)