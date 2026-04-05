package com.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PasswordStorage {

    private const val KEYSTORE_ALIAS = "master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val FILE_NAME = "master_password.enc"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun savePassword(context: Context, password: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)                    // IV weglassen
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted                     // IV danach holen
        File(context.noBackupFilesDir, FILE_NAME).writeBytes(combined)
    }

    fun loadPassword(context: Context): String? {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        if (!file.exists()) return null

        val all = file.readBytes()
        val iv = all.copyOfRange(0, 12)
        val encrypted = all.copyOfRange(12, all.size)

        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    fun changePassword(context: Context, newPassword: String) {
        savePassword(context, newPassword)
    }
}