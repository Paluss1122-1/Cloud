package com.example.cloud

import android.annotation.SuppressLint
import android.util.Base64
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object Config {
    const val SUPABASE_URL = "" // Your supabase URL (https://xxxxxxx.supabase.co)
    const val SUPABASE_PUBLISHABLE_KEY = "" // Your supabase anon / publisable key (ey... / sb)
    const val SUPABASE_BUCKET = "Files" // create a bucket on supabase for Private Cloud

    const val BWMP = "Sec.P1122.!!\"\"" // Bitwarden Master Password (for Command 'bitwarden')

    const val DBKEY = "" // deutsche bahn clientkey (create a application on https://developers.deutschebahn.com/db-api-marketplace/apis/frontpage)

    const val DBKEY1 = "" // deutsche bahn apikey

    const val NVIDIA = "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" // nvidia api key for ai models

    val LAPTOP_IPS = listOf(
        "192.168.178.20",
        "10.58.5.120",
        "10.135.30.120",
        "192.168.49.1",
        "10.164.93.120"
    ) // ips for laptops to ping

    const val SYNC_PORT = 8888
    const val UPDATE_PORT = 8890
    const val CLIPBOARD_PORT = 8891
    const val NOTIFICATION_PORT = 8892
    const val FLASHCARD_SEND_PORT    = 8896
    const val FLASHCARD_RECEIVE_PORT = 8897

    const val IMAGE_SHARE_PORT = 8898

    const val GMAILPASSWORD = "" // leave blank

    @OptIn(SupabaseInternal::class)
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Storage)
        install(Postgrest)
        install(Realtime)

        httpConfig {
            install(WebSockets)
        }
        httpEngine = OkHttp.create()
    }

    private const val FIXED_KEY = "" // enryption key

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

object TMDBConfig {
    const val APIKEY = "" // for MovieDiscover Tab

}
