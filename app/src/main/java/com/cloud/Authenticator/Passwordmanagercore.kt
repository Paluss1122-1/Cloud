package com.cloud.authenticator

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ─────────────────────────────────────────────────────────────────────────────
// Room Entity
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,                          // Site / App name
    val url: String = "",                      // Used for AutoFill domain matching
    val username: String = "",
    val encryptedPassword: String = "",        // AES-GCM encrypted, Base64-encoded
    val notes: String = "",
    val category: String = "Andere",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface PasswordDao {

    @Query("SELECT * FROM passwords ORDER BY name ASC")
    suspend fun getAll(): List<PasswordEntry>

    @Query("""
        SELECT * FROM passwords
        WHERE name LIKE '%' || :q || '%'
           OR username LIKE '%' || :q || '%'
           OR url LIKE '%' || :q || '%'
        ORDER BY name ASC
    """)
    suspend fun search(q: String): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE url LIKE '%' || :domain || '%' ORDER BY name ASC")
    suspend fun findByDomain(domain: String): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE category = :category ORDER BY name ASC")
    suspend fun getByCategory(category: String): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavorites(): List<PasswordEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PasswordEntry): Long

    @Update
    suspend fun update(entry: PasswordEntry)

    @Delete
    suspend fun delete(entry: PasswordEntry)

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun count(): Int
}

// ─────────────────────────────────────────────────────────────────────────────
// Room Database
// ─────────────────────────────────────────────────────────────────────────────

@Database(entities = [PasswordEntry::class], version = 1, exportSchema = false)
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

// ─────────────────────────────────────────────────────────────────────────────
// AES-256-GCM Encryption via Android Keystore
// ─────────────────────────────────────────────────────────────────────────────

object PasswordCrypto {

    private const val KEY_ALIAS   = "cloud_pwm_master_v1"
    private const val KEY_SIZE    = 256
    private const val IV_SIZE     = 12
    private const val TAG_SIZE    = 128
    private const val ALGORITHM   = "AES/GCM/NoPadding"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
                generateKey()
            }
        }
        return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /** Encrypts plaintext → Base64(IV || ciphertext). Returns "" on error. */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = cipher.iv + encrypted          // 12 bytes IV + ciphertext + 16 byte GCM tag
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    /** Decrypts Base64(IV || ciphertext) → plaintext. Returns "" on error. */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""
        return try {
            val combined  = Base64.decode(ciphertext, Base64.NO_WRAP)
            val iv        = combined.copyOfRange(0, IV_SIZE)
            val encrypted = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher    = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Password Generator (cryptographically secure)
// ─────────────────────────────────────────────────────────────────────────────

object PasswordGenerator {

    private const val LOWER   = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS  = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()_+-=[]{}|;:,.<>?"
    private const val AMBIGUOUS = "0OIl1"   // characters that look similar

    fun generate(
        length: Int      = 20,
        lower:   Boolean = true,
        upper:   Boolean = true,
        digits:  Boolean = true,
        symbols: Boolean = true,
        noAmbiguous: Boolean = false
    ): String {
        val pool = buildString {
            if (lower)   append(LOWER)
            if (upper)   append(UPPER)
            if (digits)  append(DIGITS)
            if (symbols) append(SYMBOLS)
        }.let { if (noAmbiguous) it.filter { c -> c.toString() !in AMBIGUOUS } else it }

        if (pool.isEmpty()) return ""

        val rng = SecureRandom.getInstanceStrong()
        val sb  = StringBuilder(length)

        // Ensure at least one char from each chosen set
        val guaranteed = buildList {
            if (lower)   add(LOWER[rng.nextInt(LOWER.length)])
            if (upper)   add(UPPER[rng.nextInt(UPPER.length)])
            if (digits)  add(DIGITS[rng.nextInt(DIGITS.length)])
            if (symbols) add(SYMBOLS[rng.nextInt(SYMBOLS.length)])
        }

        // Fill rest
        repeat(length - guaranteed.size) { sb.append(pool[rng.nextInt(pool.length)]) }

        // Shuffle guaranteed chars into the result
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
        s += (password.length * 4).coerceAtMost(40)          // length up to 40 pts
        if (password.any { it.isLowerCase() })      s += 10
        if (password.any { it.isUpperCase() })      s += 10
        if (password.any { it.isDigit() })          s += 10
        if (password.any { !it.isLetterOrDigit() }) s += 20
        val unique = password.toSet().size
        s += (unique * 2).coerceAtMost(10)                    // unique chars up to 10 pts
        return s.coerceIn(0, 100)
    }

    fun strength(password: String): PasswordStrength {
        return when (score(password)) {
            in 0..25  -> PasswordStrength.WEAK
            in 26..49 -> PasswordStrength.FAIR
            in 50..74 -> PasswordStrength.GOOD
            in 75..89 -> PasswordStrength.STRONG
            else      -> PasswordStrength.EXCELLENT
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Strength enum
// ─────────────────────────────────────────────────────────────────────────────

enum class PasswordStrength(
    val label: String,
    val fraction: Float,
    val color: androidx.compose.ui.graphics.Color
) {
    WEAK     ("Schwach",       0.20f, androidx.compose.ui.graphics.Color(0xFFD32F2F)),
    FAIR     ("Ausreichend",   0.40f, androidx.compose.ui.graphics.Color(0xFFE64A19)),
    GOOD     ("Gut",           0.60f, androidx.compose.ui.graphics.Color(0xFFFBC02D)),
    STRONG   ("Stark",         0.80f, androidx.compose.ui.graphics.Color(0xFF388E3C)),
    EXCELLENT("Ausgezeichnet", 1.00f, androidx.compose.ui.graphics.Color(0xFF1B5E20))
}

// ─────────────────────────────────────────────────────────────────────────────
// Domain helper (used by Screen + AutoFill)
// ─────────────────────────────────────────────────────────────────────────────

fun extractDomain(raw: String): String {
    if (raw.isBlank()) return ""
    val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw
                     else "https://$raw"
    return try {
        java.net.URL(withScheme).host.removePrefix("www.").lowercase()
    } catch (_: Exception) {
        raw.lowercase()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

val PASSWORD_CATEGORIES = listOf(
    "Alle", "Social", "Banking", "E-Mail",
    "Shopping", "Arbeit", "Gaming", "Andere"
)

val CATEGORY_ICONS = mapOf(
    "Social"   to "👥",
    "Banking"  to "🏦",
    "E-Mail"   to "✉️",
    "Shopping" to "🛒",
    "Arbeit"   to "💼",
    "Gaming"   to "🎮",
    "Andere"   to "🔑"
)

suspend fun PasswordDatabase.decryptedPassword(entry: PasswordEntry): String =
    withContext(Dispatchers.Default) {
        PasswordCrypto.decrypt(entry.encryptedPassword)
    }