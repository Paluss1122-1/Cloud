package com.example.cloud.authenticator

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlin.jvm.java

@Database(entities = [TwoFAEntry::class], version = 4, exportSchema = false)
abstract class TwoFADatabase : RoomDatabase() {
    abstract fun twoFADao(): TwoFADao

    companion object {
        @Volatile
        private var INSTANCE: TwoFADatabase? = null

        fun getDatabase(context: Context): TwoFADatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context,
                    TwoFADatabase::class.java,
                    "twofa_database"
                )
                    .fallbackToDestructiveMigration(true) // WICHTIG: Bei Schema-Änderung
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}