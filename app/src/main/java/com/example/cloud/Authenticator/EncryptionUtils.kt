package com.example.cloud.authenticator

import android.annotation.SuppressLint
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.toString
import kotlin.text.toByteArray

object EncryptionUtils {

     private const val FIXED_KEY = "n9n4Nl4tDEa7oOOBAl9bbgFzaiRaWpr6"

    private val secretKey = SecretKeySpec(FIXED_KEY.toByteArray(), "AES")

    @SuppressLint("GetInstance")
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    @SuppressLint("GetInstance")
    fun decrypt(encryptedBase64: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return decryptedBytes.toString(Charsets.UTF_8)
    }
}