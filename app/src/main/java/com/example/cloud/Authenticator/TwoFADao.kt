package com.example.cloud.authenticator

import androidx.room.*

@Dao
interface TwoFADao {
    @Query("SELECT * FROM twofa_entries WHERE folder = :folder")
    suspend fun getByFolder(folder: String?): List<TwoFAEntry>
    @Query("SELECT * FROM twofa_entries")
    suspend fun getAll(): List<TwoFAEntry>

    @Insert
    suspend fun insert(entry: TwoFAEntry)

    @Delete
    suspend fun delete(entry: TwoFAEntry)

    @Update
    suspend fun update(entry: TwoFAEntry)
}
