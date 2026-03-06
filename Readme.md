# Cloud (Android App)

This project is a powerful multi-functional Android application that combines a number of utilities and services into one unified app. It includes features such as a private cloud storage manager, media recording & playback, authenticator, browser, notes, quick settings helpers, and more.

## Key Features

### Security & Authentication
- **2FA authenticator** (TOTP generation, secure storage)
- **Device admin and policy management** (for enhanced control)
- **Encrypted storage functionality** (AesEncryption and secure file handling)

### ☁️ Private Cloud & File Management
- Private cloud file viewer and manager
- File import/export to/from local storage and DCIM
- File icons and MIME type detection
- Fullscreen image preview

### 🎵 Media & Recording
- Audio recorder (with UI for recording and playback)
- Media player integration
- Video frame extraction from media files

### 🧠 Productivity & Utility Tools
- Notes tab for quick note-taking
- JSON editor
- Date calculator
- Quick settings tile helpers (battery chart, network info, sensor info)

### 🌐 Communication & Social
- Browser tab for quick in-app browsing
- Gmail tab integration
- Contacts tab and management

### 🛠️ Background Services & Utilities
- Auto-clicker service with accessibility support
- WhatsApp notification listener
- Quiet hours notification manager
- Shizuku manager support

---

## 🛠️ How to Build & Run
1. Open the project in **Android Studio**.
2. ```
    package com.example.cloud
    import android.annotation.SuppressLint
    import android.util.Base64
    import io.github.jan.supabase.annotations.  SupabaseInternal
    import io.github.jan.supabase.createSupabaseClient
    import io.github.jan.supabase.postgrest.Postgrest
    import io.github.jan.supabase.realtime.Realtime
    import io.github.jan.supabase.storage.Storage
    import io.ktor.client.engine.okhttp.OkHttp
    import io.ktor.client.plugins.websocket.WebSockets
    import javax.crypto.Cipher
    import javax.crypto.spec.SecretKeySpec

    object Config {
    const val SUPABASE_URL = "https://oulgglfvobyjmfongnil.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    const val SUPABASE_BUCKET = "Files"

    const val BWMP = "Sec.P1122.!!\"\""

    const val DBKEY = "8af038ea9e12dbc6806c1c06b4a3efc3"

    const val DBKEY1 = "1083a6548de37eca71de8c1030790c25"

    const val NVIDIA = "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"

    val LAPTOP_IPS = listOf(
        "192.168.178.20",
        "10.58.5.120",
        "10.135.30.120",
        "192.168.49.1",
        "10.164.93.120"
    )

    const val SYNC_PORT = 8888
    const val UPDATE_PORT = 8890
    const val CLIPBOARD_PORT = 8891
    const val NOTIFICATION_PORT = 8892
    const val FLASHCARD_SEND_PORT    = 8896
    const val FLASHCARD_RECEIVE_PORT = 8897

    const val IMAGE_SHARE_PORT = 8898

    const val GMAILPASSWORD = "mejf bonk zjlo xein "

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

    object SupabaseConfigALT {
    const val SUPABASE_URL = "https://vfctgombfsgirctobpca.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_kfsZrgk44Gos9XLrdQXQOA_ONGSZq8i"

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
    }

    object TMDBConfig {
    const val APIKEY = "c9b585a04735e5a130f9c6ee642088c0"}
3. Run the app on an emulator or physical device.

## 📦 Project Structure
This repository follows a modular structure with feature folders under `app/src/main/java/com/example/cloud/`.

Some of the main modules include:
- `authenticator/` – 2FA and authenticator UI
- `privatecloudapp/` – file browsing and cloud helpers
- `mediarecorder/` & `mediaplayer/` – media recording/playback
- `quicksettingsfunctions/` – quick settings utilities
- `service/` – background services like auto clicker and notification listeners

---

> ⚠️ This app likely requires permissions for storage, audio recording, device admin, and accessibility to enable all features.

---

## 🧩 Notes for Contributors
- This is an experimental multi-tool app with many features under active development.
- Feel free to explore the existing packages and add new tabs or services.
- Keep feature modules organized under their corresponding packages.

---

## 📬 Contact / Support
If you want to discuss features or contribute, open an issue or send a PR.
