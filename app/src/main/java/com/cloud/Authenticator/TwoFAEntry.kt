package com.cloud.authenticator

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "twofa_entries",
    indices = [Index(value = ["secret"], unique = true)]
)
data class TwoFAEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supabaseId: String? = null,
    val name: String,
    val secret: String
)