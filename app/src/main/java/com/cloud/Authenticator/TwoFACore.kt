package com.cloud.authenticator

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

@Entity(
    tableName = "twofa_entries",
    indices = [Index(value = ["secret"], unique = true)]
)
data class TwoFAEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supabaseId: String? = null,
    val name: String,
    val secret: String,
    val url: String = ""
)

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

@Database(entities = [TwoFAEntry::class], version = 5, exportSchema = false)
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
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object TotpGenerator {
    fun base32Decode(encoded: String): ByteArray {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = encoded.trim().replace("=", "").replace(" ", "").uppercase(Locale.US)
        val baos = ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (ch in cleaned) {
            val valIndex = base32Chars.indexOf(ch)
            if (valIndex == -1) continue
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                val byteVal = (buffer shr bitsLeft) and 0xFF
                baos.write(byteVal)
            }
        }
        return baos.toByteArray()
    }

    fun generateTOTP(secretBase32: String, timeMillis: Long = System.currentTimeMillis(), digits: Int = 6, periodSeconds: Long = 30): String {
        try {
            val key = base32Decode(secretBase32)
            val counter = timeMillis / 1000L / periodSeconds
            val data = ByteArray(8)
            var value = counter
            for (i in 7 downTo 0) {
                data[i] = (value and 0xFF).toByte()
                value = value shr 8
            }

            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            val hash = mac.doFinal(data)

            val offset = hash[hash.size - 1].toInt() and 0x0f
            val binary =
                ((hash[offset].toInt() and 0x7f) shl 24) or
                        ((hash[offset + 1].toInt() and 0xff) shl 16) or
                        ((hash[offset + 2].toInt() and 0xff) shl 8) or
                        (hash[offset + 3].toInt() and 0xff)

            val otp = binary % 10.0.pow(digits.toDouble()).toInt()
            return String.format(Locale.US, "%0${digits}d", otp)
        } catch (_: Exception) {
            return "ERROR"
        }
    }
}