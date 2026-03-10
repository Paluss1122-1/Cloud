package com.example.cloud.authenticator

import androidx.room.*

@Dao
interface TwoFADao {
    @Query("SELECT * FROM twofa_entries")
    suspend fun getAll(): List<TwoFAEntry>

    @Insert
    suspend fun insert(entry: TwoFAEntry)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(entry: TwoFAEntry): Long

    @Delete
    suspend fun delete(entry: TwoFAEntry)

    @Update
    suspend fun update(entry: TwoFAEntry)
}