package com.tabslify.core.objects

import android.content.Context
import com.tabslify.core.ui.getDeviceName
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

enum class BackupOutcome {
    DONE,
    NO_MASTER,
    EMPTY,
    SHRINK_BLOCKED,
    WRONG_PASSWORD,
    CORRUPT,
    FAILED
}

data class BackupEntry(
    val fileName: String,
    val createdAt: Long
)

object PrefsBackup {

    private const val FOLDER = "prefs_backups"
    private const val MAGIC = "TBK1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val MAX_BACKUPS = 20
    private const val THROTTLE_MS = 12L * 60 * 60 * 1000
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LAST_MS = "last_prefs_backup_ms"
    private const val KEY_LAST_COUNT = "last_prefs_backup_count"

    suspend fun autoBackup(context: Context) {
        if (Config.masterPassword.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_MS, 0L)
        if (System.currentTimeMillis() - last < THROTTLE_MS) return
        backupNow(context, force = false)
    }

    suspend fun backupNow(context: Context, force: Boolean): BackupOutcome =
        withContext(Dispatchers.IO) {
            val pw = Config.masterPassword
            if (pw.isBlank()) return@withContext BackupOutcome.NO_MASTER

            val (json, count) = serializePrefs(context)
            if (count == 0) return@withContext BackupOutcome.EMPTY

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCount = prefs.getInt(KEY_LAST_COUNT, 0)
            if (!force && lastCount > 0 && count < lastCount / 2) {
                return@withContext BackupOutcome.SHRINK_BLOCKED
            }

            val blob = encrypt(json, pw)
            val verified = try {
                val roundTrip = decrypt(blob, pw)
                JSONObject(roundTrip).optJSONObject("prefs") != null
            } catch (_: Exception) {
                false
            }
            if (!verified) return@withContext BackupOutcome.FAILED

            try {
                val bucket = Config.client.storage.from(Config.SUPABASE_BUCKET)
                val fileName = "%s/backup_%013d.enc".format(FOLDER, System.currentTimeMillis())
                bucket.upload(fileName, blob) { upsert = true }
                prefs.edit {
                    putLong(KEY_LAST_MS, System.currentTimeMillis())
                        .putInt(KEY_LAST_COUNT, count)
                    }
                pruneOld()
                BackupOutcome.DONE
            } catch (_: Exception) {
                BackupOutcome.FAILED
            }
        }

    suspend fun listBackups(): List<BackupEntry> = withContext(Dispatchers.IO) {
        try {
            Config.client.storage.from(Config.SUPABASE_BUCKET).list(FOLDER)
                .map { it.name }
                .filter { it.startsWith("backup_") && it.endsWith(".enc") }
                .mapNotNull { name ->
                    val ms = name.removePrefix("backup_").removeSuffix(".enc").toLongOrNull()
                    if (ms == null) null else BackupEntry(name, ms)
                }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun restore(context: Context, fileName: String): BackupOutcome =
        withContext(Dispatchers.IO) {
            val pw = Config.masterPassword
            if (pw.isBlank()) return@withContext BackupOutcome.NO_MASTER

            val blob = try {
                Config.client.storage.from(Config.SUPABASE_BUCKET)
                    .downloadAuthenticated("$FOLDER/$fileName")
            } catch (_: Exception) {
                return@withContext BackupOutcome.FAILED
            }

            val json = try {
                decrypt(blob, pw)
            } catch (_: Exception) {
                return@withContext BackupOutcome.WRONG_PASSWORD
            }

            val prefsObj = try {
                JSONObject(json).optJSONObject("prefs")
            } catch (_: Exception) {
                null
            } ?: return@withContext BackupOutcome.CORRUPT

            try {
                val (snapshot, _) = serializePrefs(context)
                File(context.filesDir, "pre_restore_${System.currentTimeMillis()}.json")
                    .writeText(snapshot)
            } catch (_: Exception) {
            }

            try {
                backupNow(context, force = true)
            } catch (_: Exception) {
            }

            applyMerge(context, prefsObj)
            BackupOutcome.DONE
        }

    private fun serializePrefs(context: Context): Pair<String, Int> {
        val prefsObj = JSONObject()
        var count = 0
        val dir = File(context.dataDir, "shared_prefs")
        val names = dir.listFiles { file -> file.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            ?: emptyList()

        for (name in names) {
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val all = sp.all
            if (all.isEmpty()) continue
            val fileObj = JSONObject()
            for ((key, value) in all) {
                val entry = JSONObject()
                when (value) {
                    is Boolean -> {
                        entry.put("t", "b"); entry.put("v", value)
                    }
                    is Int -> {
                        entry.put("t", "i"); entry.put("v", value)
                    }
                    is Long -> {
                        entry.put("t", "l"); entry.put("v", value)
                    }
                    is Float -> {
                        entry.put("t", "f"); entry.put("v", value.toDouble())
                    }
                    is String -> {
                        entry.put("t", "s"); entry.put("v", value)
                    }
                    is Set<*> -> {
                        entry.put("t", "ss")
                        val arr = JSONArray()
                        value.forEach { if (it is String) arr.put(it) }
                        entry.put("v", arr)
                    }
                    else -> continue
                }
                fileObj.put(key, entry)
                count++
            }
            prefsObj.put(name, fileObj)
        }

        val root = JSONObject()
        root.put("v", 1)
        root.put("createdAt", System.currentTimeMillis())
        root.put("device", getDeviceName())
        root.put("prefs", prefsObj)
        return root.toString() to count
    }

    private fun applyMerge(context: Context, prefsObj: JSONObject) {
        val fileNames = prefsObj.keys()
        while (fileNames.hasNext()) {
            val name = fileNames.next()
            val fileObj = prefsObj.optJSONObject(name) ?: continue
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            sp.edit {
                val keys = fileObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val entry = fileObj.optJSONObject(key) ?: continue
                    when (entry.optString("t")) {
                        "b" -> putBoolean(key, entry.optBoolean("v"))
                        "i" -> putInt(key, entry.optInt("v"))
                        "l" -> putLong(key, entry.optLong("v"))
                        "f" -> putFloat(key, entry.optDouble("v").toFloat())
                        "s" -> putString(key, entry.optString("v"))
                        "ss" -> {
                            val arr = entry.optJSONArray("v")
                            val set = mutableSetOf<String>()
                            if (arr != null) {
                                for (i in 0 until arr.length()) set.add(arr.optString(i))
                            }
                            putStringSet(key, set)
                        }
                    }
                }
            }
        }
    }

    private suspend fun pruneOld() {
        try {
            val bucket = Config.client.storage.from(Config.SUPABASE_BUCKET)
            val items = bucket.list(FOLDER)
                .map { it.name }
                .filter { it.startsWith("backup_") && it.endsWith(".enc") }
                .sorted()
            val excess = items.size - MAX_BACKUPS
            if (excess > 0) {
                items.take(excess).forEach { bucket.delete("$FOLDER/$it") }
            }
        } catch (_: Exception) {
        }
    }

    private fun encrypt(json: String, pw: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val key = Config.deriveKey(pw, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + cipher.iv + encrypted
    }

    private fun decrypt(blob: ByteArray, pw: String): String {
        val magicLen = MAGIC.length
        val magic = String(blob.copyOfRange(0, magicLen), Charsets.US_ASCII)
        require(magic == MAGIC)
        val salt = blob.copyOfRange(magicLen, magicLen + SALT_LEN)
        val iv = blob.copyOfRange(magicLen + SALT_LEN, magicLen + SALT_LEN + IV_LEN)
        val encrypted = blob.copyOfRange(magicLen + SALT_LEN + IV_LEN, blob.size)
        val key = Config.deriveKey(pw, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
