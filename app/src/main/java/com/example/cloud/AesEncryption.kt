package com.example.cloud

// AesEncryption.kt
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object AesEncryption {
    private const val AES_KEY_HEX = "d59c85c77f623284e23f30d745dbc823" // 32 Hex-Zeichen = 16 Bytes
    private val keyBytes = AES_KEY_HEX.decodeHex()
    private val secretKey = SecretKeySpec(keyBytes, "AES")

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted // IV + Ciphertext
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Ungültige Hex-Länge" }
        return ByteArray(length / 2) { index ->
            Integer.parseInt(this.substring(index * 2, index * 2 + 2), 16).toByte()
        }
    }
}