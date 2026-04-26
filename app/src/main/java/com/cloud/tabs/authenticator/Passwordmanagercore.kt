package com.cloud.tabs.authenticator

import android.content.Context
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.cloud.core.objects.Config
import com.cloud.core.functions.errorInsert
import com.cloud.core.functions.ERRORINSERTDATA
import com.cloud.privatecloudapp.isOnline
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "NULL",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun PasswordEntry.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("username", username)
        put("password", password)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }
}


@Dao
interface PasswordDao {

    @Query("SELECT * FROM passwords ORDER BY name ASC")
    suspend fun getAll(): List<PasswordEntry>

    @Query(
        """
        SELECT * FROM passwords
        WHERE name LIKE '%' || :q || '%'
           OR username LIKE '%' || :q || '%'
           OR url LIKE '%' || :q || '%'
        ORDER BY name ASC
    """
    )
    suspend fun search(q: String): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE :domain != '' AND url LIKE '%' || :domain || '%'")
    suspend fun findByDomain(domain: String): List<PasswordEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PasswordEntry): Long

    @Update
    suspend fun update(entry: PasswordEntry)

    @Delete
    suspend fun delete(entry: PasswordEntry)

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun count(): Int

    @Query("DELETE FROM passwords")
    suspend fun deleteAll()
}


@Database(entities = [PasswordEntry::class], version = 4, exportSchema = false)
abstract class PasswordDatabase : RoomDatabase() {

    abstract fun passwordDao(): PasswordDao

    companion object {
        @Volatile
        private var INSTANCE: PasswordDatabase? = null

        fun getDatabase(context: Context): PasswordDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PasswordDatabase::class.java,
                    "cloud_passwords_v1.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}


object CloudCrypto {

    private val cachedKey: SecretKey by lazy {
        val fixedSalt = "cloud_sync_salt_v1".toByteArray(Charsets.UTF_8).copyOf(16)
        Config.deriveKey(Config.masterPassword, fixedSalt)
    }

    fun encryptForCloud(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return encryptWithKey(plaintext, cachedKey)
    }

    fun decryptFromCloud(ciphertext: String): String? {
        if (ciphertext.isEmpty()) return ""
        return try {
            decryptWithKey(ciphertext, cachedKey)
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                errorInsert(ERRORINSERTDATA("CloudCrypto", "Decrypt fehlgeschlagen: ${e.message}", Instant.now().toString(), "ERROR"))
            }
            null
        }
    }

    private fun encryptWithKey(plaintext: String, key: SecretKey): String {
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val bb = ByteBuffer.allocate(12 + encrypted.size)
        bb.put(iv)
        bb.put(encrypted)
        return Base64.encodeToString(bb.array(), Base64.NO_WRAP)
    }

    private fun decryptWithKey(ciphertext: String, key: SecretKey): String {
        val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val data = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(data).toString(Charsets.UTF_8)
    }
}


object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private const val AMBIGUOUS = "0OIl1"

    fun generate(
        length: Int = 20,
        lower: Boolean = true,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        noAmbiguous: Boolean = false
    ): String {
        val pool = buildString {
            if (lower) append(LOWER)
            if (upper) append(UPPER)
            if (digits) append(DIGITS)
            if (symbols) append(SYMBOLS)
        }.let { if (noAmbiguous) it.filter { c -> c.toString() !in AMBIGUOUS } else it }

        if (pool.isEmpty()) return ""

        val rng = SecureRandom.getInstanceStrong()
        val sb = StringBuilder(length)

        val guaranteed = buildList {
            if (lower) add(LOWER[rng.nextInt(LOWER.length)])
            if (upper) add(UPPER[rng.nextInt(UPPER.length)])
            if (digits) add(DIGITS[rng.nextInt(DIGITS.length)])
            if (symbols) add(SYMBOLS[rng.nextInt(SYMBOLS.length)])
        }

        repeat(length - guaranteed.size) { sb.append(pool[rng.nextInt(pool.length)]) }

        val all = (sb.toString().toList() + guaranteed).toMutableList()
        for (i in all.indices.reversed()) {
            val j = rng.nextInt(i + 1)
            val tmp = all[i]; all[i] = all[j]; all[j] = tmp
        }

        return all.take(length).joinToString("")
    }

    /** 0–100 strength score */
    fun score(password: String): Int {
        if (password.isEmpty()) return 0
        var s = 0
        s += (password.length * 4).coerceAtMost(40)
        if (password.any { it.isLowerCase() }) s += 10
        if (password.any { it.isUpperCase() }) s += 10
        if (password.any { it.isDigit() }) s += 10
        if (password.any { !it.isLetterOrDigit() }) s += 20
        val unique = password.toSet().size
        s += (unique * 2).coerceAtMost(10)
        return s.coerceIn(0, 100)
    }

    fun strength(password: String): PasswordStrength {
        return when (score(password)) {
            in 0..25 -> PasswordStrength.WEAK
            in 26..49 -> PasswordStrength.FAIR
            in 50..74 -> PasswordStrength.GOOD
            in 75..89 -> PasswordStrength.STRONG
            else -> PasswordStrength.EXCELLENT
        }
    }
}


enum class PasswordStrength(
    val label: String,
    val fraction: Float,
    val color: Color
) {
    WEAK("Schwach", 0.20f, Color(0xFFD32F2F)),
    FAIR("Ausreichend", 0.40f, Color(0xFFE64A19)),
    GOOD("Gut", 0.60f, Color(0xFFFBC02D)),
    STRONG("Stark", 0.80f, Color(0xFF388E3C)),
    EXCELLENT("Ausgezeichnet", 1.00f, Color(0xFF1B5E20))
}

@Serializable
data class PasswordEntrySupabase(
    val id: String? = null,
    val name: String,
    val url: String? = null,
    val username: String? = null,
    val encrypted_password: String = "",
    val notes: String? = null,
    val totp_secret: String? = null
)

suspend fun syncPasswordEntriesWithCloud(passwordDb: PasswordDatabase, twoFaDb: TwoFADatabase, context: Context): SyncResult {
    if (!isOnline(context)) {
        return SyncResult(uploaded = 0, downloaded = 0, total = 0, error = "Kein Internet")
    }
    return withContext(Dispatchers.IO) {
        try {
            val localPasswords = passwordDb.passwordDao().getAll()
            val localTwoFa = twoFaDb.twoFADao().getAll()
            val cloudEntries = try {
                Config.client.postgrest.from("password_entries")
                    .select().decodeList<PasswordEntrySupabase>()
            } catch (e: Exception) {
                errorInsert(
                    ERRORINSERTDATA(
                        "PasswordRepository",
                        "Cloud-Laden fehlgeschlagen: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
                return@withContext SyncResult(uploaded = 0, downloaded = 0, total = 0, error = e.message)
            }

            val cloudNames = mutableSetOf<String>()
            var downloaded = 0
            cloudEntries.forEach { cloud ->
                cloudNames.add(cloud.name.trim().lowercase())
                val decryptedPw = CloudCrypto.decryptFromCloud(cloud.encrypted_password) ?: run {
                    return@forEach
                }
                val existing = localPasswords.find { it.name == cloud.name && it.username == cloud.username }
                if (existing == null) {
                    passwordDb.passwordDao().insert(PasswordEntry(name = cloud.name, url = cloud.url ?: "", username = cloud.username ?: "", password = decryptedPw))
                    downloaded++
                }
            }

            val missingInCloud = localPasswords.filter { it.name.trim().lowercase() !in cloudNames }
            val toUpload = coroutineScope {
                missingInCloud.map { local ->
                    async {
                        try {
                            val encryptedPw = CloudCrypto.encryptForCloud(local.password)
                            val matchedSecret = localTwoFa.firstOrNull { fa ->
                                val n = fa.name.lowercase(); val ln = local.name.lowercase(); val lu = local.url.lowercase()
                                n.contains(ln) || ln.contains(n) || (lu.isNotEmpty() && n.split(" ").any { lu.contains(it) })
                            }?.secret
                            PasswordEntrySupabase(name = local.name, url = local.url, username = local.username, encrypted_password = encryptedPw, notes = local.notes, totp_secret = matchedSecret?.let { CloudCrypto.encryptForCloud(it) })
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (toUpload.isNotEmpty()) {
                try {
                    Config.client.postgrest.from("password_entries").insert(toUpload)
                } catch (e: Exception) {
                    errorInsert(ERRORINSERTDATA("PasswordRepository", "Batch-Upload fehlgeschlagen: ${e.message}", Instant.now().toString(), "ERROR"))
                }
            }
            SyncResult(uploaded = missingInCloud.size, downloaded = 0, total = localPasswords.size)
        } catch (e: Exception) {
            errorInsert(
                ERRORINSERTDATA(
                    "PasswordRepository",
                    "Sync-Exception: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            )
            SyncResult(uploaded = 0, downloaded = 0, total = 0, error = e.message)
        }
    }
}