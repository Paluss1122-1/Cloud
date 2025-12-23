package com.example.cloud.Authenticator

import java.io.ByteArrayOutputStream
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow
import kotlin.ranges.downTo
import kotlin.text.format
import kotlin.text.indexOf
import kotlin.text.replace
import kotlin.text.trim
import kotlin.text.uppercase

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
