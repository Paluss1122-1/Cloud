package com.tabslify.core.objects

import android.Manifest
import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.LocaleList
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import com.tabslify.BuildConfig
import com.tabslify.core.functions.canNotify
import com.tabslify.core.ui.MenuItem
import com.tabslify.core.ui.getDeviceName
import com.tabslify.tabs.HeiseNewsTabContent
import com.tabslify.tabs.WeatherTabContent
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

object Config {
    const val SUPABASE_URL = BuildConfig.SUPABASE_URL
    const val SUPABASE_PUBLISHABLE_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    @OptIn(SupabaseInternal::class)
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Storage)
        install(Postgrest)
        install(Realtime)
        install(Functions)
        install(Auth)

        httpConfig {
            install(WebSockets)
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }
        httpEngine = OkHttp.create {
            requestTimeout = 60.seconds
        }
    }

    const val DEF_GEMINI = "gemini-2.5-flash"
    const val MAX_GEMINI = "gemini-3-flash-preview"

    const val SUPABASE_BUCKET = "Files"

    val helpFrameEntries: Map<MenuItem, String> = mapOf(
        MenuItem.PRIVATE_CLOUD to "So bedienst du die Private Cloud:\n\n1. Gib beim Öffnen dein Master-Passwort ein, um den Speicher zu entsperren.\n2. Tippe auf das Plus- bzw. Upload-Symbol, um Dateien vom Gerät hochzuladen.\n3. Tippe eine Datei an, um sie herunterzuladen oder zu öffnen.\n4. Halte eine Datei gedrückt, um sie umzubenennen oder zu löschen.\n\nAlle Inhalte werden mit deinem Master-Passwort verschlüsselt und zwischen deinen Geräten synchronisiert.",
        MenuItem.AITAB to "So bedienst du den KI-Chat:\n\n1. Tippe unten in das Eingabefeld und schreibe deine Frage.\n2. Bei vision Models: Über das Bild-Symbol hängst du ein Foto an, das die KI analysieren soll.\n3. Tippe auf Senden – die Antwort erscheint im Chatverlauf.\n4. Scrolle nach oben, um frühere Nachrichten nachzulesen.",
        MenuItem.BROWSER to "So bedienst du den Browser:\n\n1. Gib eine Webadresse in die Adresszeile ein oder einen Suchbegriff.\n2. Bestätige mit Enter / Tippe auf den Öffnen-Button, um die Seite zu laden.",
        MenuItem.QUICK to "So bedienst du die Schnellübersicht:\n\n1. Scrolle durch die Kacheln für Akku-Verlauf, Netzwerk-Infos und Lade-Statistiken.\n2. Tippe eine Kachel an, um Details einzublenden.",
        MenuItem.GALLERY to "So bedienst du die Galerie:\n\n1. Wische nach oben und unten, um durch deine Fotos und Videos zu blättern.\n2. Tippe ein Bild an, um es im Vollbild zu öffnen.\n3. Ziehe zwei Finger auseinander, um zu zoomen.",
        MenuItem.AUTHENTICATOR to "So bedienst du den Passwort-Manager:\n\n1. Tippe auf das Plus-Symbol, um einen neuen Eintrag mit Name, Benutzername, Passwort, URL und Notizen anzulegen.\n2. Über das Zauberstab-Symbol erzeugst du starke Passwörter automatisch.\n3. Optional hinterlegst du pro Eintrag ein 2FA-Secret (QR-Code scannen oder Base32-Schlüssel eingeben) – der 6-stellige Code wird dann alle 30 Sekunden neu erzeugt.\n4. Tippe einen Eintrag an, um Details zu sehen, das Passwort ein-/auszublenden oder es zu kopieren.\n5. Nutze die Suche, um Einträge schnell zu finden.\n\nAlle Passwörter und Schlüssel werden verschlüsselt gespeichert.",
        MenuItem.WEATHER to "So bedienst du das Wetter:\n\n1. Beim ersten Öffnen wird dein Standort abgefragt, um das lokale Wetter zu laden.\n2. Wische seitwärts, um zwischen aktuellem Wetter, Stunden- und Tagesvorhersage zu wechseln.\n3. Ziehe die Ansicht nach unten, um die Daten zu aktualisieren.",
        MenuItem.CONTACTS to "So bedienst du die Kontakte:\n\n1. Erlaube beim ersten Öffnen den Kontakt-Zugriff.\n2. Tippe auf das Plus-Symbol, um einen neuen Kontakt anzulegen.\n3. Tippe einen Kontakt an, um Details zu sehen oder ihn zu bearbeiten.\n4. Über das Löschen-Symbol entfernst du einen Kontakt.",
        MenuItem.RECORDER to "So bedienst du den Rekorder:\n\n1. Tippe auf den Aufnahme-Knopf, um eine Sprachnotiz zu starten.\n2. Tippe erneut, um die Aufnahme zu stoppen und zu speichern.\n3. Tippe eine Aufnahme in der Liste an, um sie abzuspielen.\n4. Halte eine Aufnahme gedrückt, um sie umzubenennen oder zu löschen.\n\nAlle Aufnahmen bleiben lokal auf dem Gerät.",
        MenuItem.DATECALCULATOR to "So bedienst du den Datumsrechner:\n\n1. Wähle für Zeitspannen ein Start- und ein Enddatum – die Differenz wird berechnet.",
        MenuItem.MOVIEDISCOVER to "So bedienst du die Film-Empfehlungen:\n\n1. Wähle oben ein Genre aus, um die Vorschläge einzugrenzen.\n2. Tippe auf „🎲 Neue Filme“, um neue Zufallsvorschläge zu laden.\n3. Tippe auf den Stern, um einen Film zu merken – über „Gemerkte“ zeigst du nur deine gemerkten Filme an.\n4. Tippe einen Film an, um Poster, Bewertung und Beschreibung zu öffnen.",
        MenuItem.NOTES to "So bedienst du die Notizen:\n\n1. Tippe auf das Plus-Symbol, um eine neue Notiz zu erstellen.\n2. Tippe eine Notiz an, um sie zu bearbeiten.\n3. Halte eine Notiz gedrückt, um sie zu löschen.\n\nAlle Notizen bleiben lokal auf dem Gerät.",
        MenuItem.MEDIAPLAYERTAB to "So bedienst du den Media Player:\n\n1. Wähle einen Titel aus der Liste, um die Wiedergabe zu starten.\n2. Mit Play/Pause und den Skip-Tasten steuerst du die Wiedergabe.\n3. Ziehe den Fortschrittsbalken, um an eine andere Stelle zu springen.\n4. Über das Playlist-Menü verwaltest du deine Wiedergabelisten.",
        MenuItem.GMAIL to "So bedienst du die E-Mails:\n\n1. Tippe eine Nachricht in der Liste an, um sie zu lesen.\n2. Nutze die Symbole zum Antworten, Weiterleiten oder Löschen.\n3. Ziehe die Liste nach unten, um neue Mails abzurufen.",
        MenuItem.Vocabs to "So bedienst du das Vokabeltraining:\n\n1. Tippe auf das Plus-Symbol, um Vokabeln hinzuzufügen – oder fotografiere eine Liste, damit sie automatisch erkannt wird.\n2. Starte eine Lernrunde: Die Karte zeigt zuerst ein Wort.\n3. Tippe die Karte an, um die Lösung umzudrehen.\n4. Bewerte, ob du sie wusstest – die Wiederholung passt sich an.",
        MenuItem.EXPLORE to "So funktioniert die Karte:\n\n1. Erlaube den Standort-Zugriff – jeder Bereich, an dem du vorbeikommst, wird automatisch als „erkundet“ markiert und blau eingefärbt.\n2. Das Tracking läuft im Hintergrund.\n3. Oben siehst du deine Statistiken (erkundete Bereiche, Anteil, heute) und den aktuellen Tracker-Status.\n5. Halte einen blauen Bereich gedrückt, um ihn wieder als unbesucht zu markieren.",
        MenuItem.CALENDAR to "So bedienst du den Kalender:\n\n1. Wische zwischen den Wochen bzw. Monaten, um zu navigieren.\n2. Tippe einen Tag an, um seine Termine zu sehen.\n3. Tippe auf das Plus-Symbol, um ein neues Ereignis anzulegen.\n4. Tippe einen Termin an, um ihn zu bearbeiten oder zu löschen.",
        MenuItem.REMOTEDESKTOP to "So bedienst du die Fernsteuerung:\n\n1. Stelle sicher, dass dein PC gekoppelt und im selben Netzwerk ist.\n2. Verbinde dich über den Verbinden-Button.\n3. Steuere Maus und Tastatur über die Bildschirmfläche und die Eingabeleiste.",
        MenuItem.PODCAST to "So bedienst du die Podcasts:\n\n1. Suche einen Podcast und tippe auf den Stern.\n2. Wähle eine Folge aus der Liste, um sie abzuspielen.\n3. Mit Play/Pause und den Skip-Tasten steuerst du die Wiedergabe.\n\nNeue Folgen von mit Stern markierten Shows werden automatisch täglich 15Uhr geladen.",
        MenuItem.HEISE_NEWS to "So bedienst du die News:\n\n1. Scrolle durch die aktuellen Meldungen.\n2. Tippe eine Überschrift an, um den vollständigen Artikel zu öffnen.\n3. Ziehe die Liste nach unten, um neue Meldungen zu laden.",
        MenuItem.PC_MANAGER to "So bedienst du den PC-Manager:\n\n1. Stelle sicher, dass dein PC gekoppelt und erreichbar ist.\n2. Sieh dir Status und Dateien deines PCs an.\n3. Wähle einen Befehl aus, um ihn an den PC zu senden.",
        MenuItem.APKM_INSTALLER to "So bedienst du den APKM-Installer:\n\n1. Tippe auf „Datei auswählen“ und suche das .apkm-Paket.\n2. Bestätige die Auswahl, um die Installation zu starten.\n3. Folge der System-Abfrage, um die Split-APKs zu installieren."
    )

    var LAT: Double = 0.0
    var LON: Double = 0.0

    const val SYNC_PORT = 8888
    const val NOTIFICATION_PORT = 8889
    const val UPDATE_PORT = 8890
    const val CLIPBOARD_PORT = 8891
    const val TRIGGER_PORT = 8893
    const val SESSION_PORT = 8894
    const val AI_RECEIVE_PORT = 8895
    const val FLASHCARD_SEND_PORT = 8896
    const val FLASHCARD_RECEIVE_PORT = 8897

    const val IMAGE_SHARE_PORT = 8898
    const val MEDIA_COMMAND_PORT = 8899
    const val MEDIA_STATE_PORT = 8900
    const val AI_PORT = 8902
    const val MAIL_NOTIFY_PORT = 8904
    const val EXECUTE_PORT = 8905
    const val EXECUTE_PORT_SEND_FROM_HANDY = 8906
    const val EXECUTE_RESPONSE_PORT = 8907
    const val INFO_PORT = 8909
    const val SMS_PORT = 8910

    private const val ITERATIONS = 200_000
    private const val KEY_LENGTH = 256

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec).encoded
        return SecretKeySpec(tmp, "AES")
    }

    var masterPassword: String = ""

    fun init(context: Context) {
        masterPassword = PasswordStorage.loadPassword(context)
            ?: ""
        val prefs = context.getSharedPreferences("tabslify_app_prefs", Context.MODE_PRIVATE)
        LAT = if (prefs.contains("lat_key_d"))
            java.lang.Double.longBitsToDouble(prefs.getLong("lat_key_d", 0L))
        else
            prefs.getFloat("lat_key", 0.0f).toDouble()
        LON = if (prefs.contains("lon_key_d"))
            java.lang.Double.longBitsToDouble(prefs.getLong("lon_key_d", 0L))
        else
            prefs.getFloat("lon_key", 0.0f).toDouble()
    }

    var realDevice = false

    fun setAppLanguage(context: Context, tag: String) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
    }

    fun currentAppLanguage(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        return locales?.takeUnless { it.isEmpty }?.get(0)?.language ?: ""
    }

    fun cms(): Int = System.currentTimeMillis().toInt()

    const val SHOWCOMMANDS = 20000
    const val GAL = 70000
    const val DEL_GAL_CONF = 70001
    const val PD_QUEUE = 50001
    const val VOICE_NOTE = 40000
    const val TODOS = 10000
    const val CHAT_SERVICE = 30000
    const val CHAT_SERVICE_HISTORY = 30001
    const val COMPLETED_PODCASTS = 50002
    const val PODCASTS = 50003
    const val PLALISTS = 50101
    const val MEDIA_PLAYER = 50000
    const val BLOCKED_MESSAGES = 60000

    @Suppress("unused")
    fun sendBridgeCommand(context: Context, json: String) {
        context.sendBroadcast(Intent("com.paluss1122.accessibily.EXECUTE").apply {
            setPackage("com.paluss1122.accessibily")
            putExtra("cmd", json)
        })
    }

    fun requestPermission(
        target: String,
        launcher: ActivityResultLauncher<Array<String>>
    ): Boolean {
        val permission = when (target) {
            "audio" -> Manifest.permission.READ_MEDIA_AUDIO
            "img" -> Manifest.permission.READ_MEDIA_IMAGES
            "loc" -> Manifest.permission.ACCESS_FINE_LOCATION
            "cam" -> Manifest.permission.CAMERA
            "con" -> Manifest.permission.READ_CONTACTS
            "not" -> Manifest.permission.POST_NOTIFICATIONS
            "mic" -> Manifest.permission.RECORD_AUDIO
            "all" -> {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
                return true
            }

            else -> return false
        }

        launcher.launch(arrayOf(permission))
        return true
    }

    fun getAppSignatureSha256(context: Context): String? {
        val allowedHashes = listOf(
            BuildConfig.DEBUG_SHA256,
            BuildConfig.RELEASE_SHA256
        ).map { it.replace(":", "").lowercase() }

        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo
            val signatures = signingInfo?.signingCertificateHistory ?: signingInfo?.apkContentsSigners
            if (signatures != null) {
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA-256")
                    md.update(signature.toByteArray())
                    val digest = md.digest()
                    val toRet = digest.fold("") { str, it -> str + "%02x".format(it) }.replace(":", "")
                        .lowercase()
                    if (allowedHashes.contains(toRet)) {
                        return toRet
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    suspend fun <T> safeCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (e.message?.contains("JWT expired", ignoreCase = true) == true) {
                try {
                    val refreshToken = client.auth.currentSessionOrNull()?.refreshToken
                        ?: throw IllegalStateException("No refresh token available")
                    client.auth.refreshSession(refreshToken)
                    block()
                } catch (refreshError: Exception) {
                    Log.e("Config", "Refresh failed", refreshError)
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    suspend fun openApiProxyConnection(
        context: Context,
        readTimeoutMs: Int = 15_000
    ): HttpURLConnection? = withContext(Dispatchers.IO) {
        val sha256 = getAppSignatureSha256(context) ?: return@withContext null
        (URL("$SUPABASE_URL/functions/v1/api-proxy").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $SUPABASE_PUBLISHABLE_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Android-Cert", sha256)
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            doOutput = true
        }
    }

    suspend fun apiProxyPost(
        context: Context,
        body: JSONObject,
        tag: String = "apiProxy",
        readTimeoutMs: Int = 15_000
    ): String? = withContext(Dispatchers.IO) {
        val connection = openApiProxyConnection(context, readTimeoutMs) ?: return@withContext null
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                Log.e(tag, "api-proxy failed: Code ${connection.responseCode}, Body: $errorText")
                return@withContext null
            }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchBWMP(context: Context): String {
        if (getAppSignatureSha256(context) == null) return "App-Signatur konnte nicht validiert werden"
        val body = JSONObject().apply { put("action", "BWMP") }
        val responseText = apiProxyPost(context, body, "FetchBWMP") ?: return ""
        return JSONObject(responseText).getString("value")
    }

    fun userApiKey(context: Context, name: String): String {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("api_key_$name", "")?.trim().orEmpty()
    }
}

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

fun prvt(): Boolean {
    @Suppress("KotlinConstantConditions", "RedundantSuppression", "SimplifyBooleanWithConstants")
    return (getDeviceName().trim().contains(BuildConfig.LOCAL_DEVICE_NAME, ignoreCase = true) || !Config.realDevice) && BuildConfig.IS_DEV
}

fun hasStarred(
    username: String,
    callback: (Boolean) -> Unit
) {
    Thread {
        try {
            val client = OkHttpClient()

            val request = Request.Builder()
                .url("https://api.github.com/repos/Paluss1122-1/Tabslify/stargazers")
                .addHeader("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                callback(false)
                return@Thread
            }

            val json = JSONArray(response.body.string())

            for (i in 0 until json.length()) {
                val login = json.getJSONObject(i).getString("login")
                if (login == username) {
                    callback(true)
                    return@Thread
                }
            }

            callback(false)
        } catch (e: Exception) {
            e.printStackTrace()
            callback(false)
        }
    }.start()
}

fun tNotify(ctx: Context, notificationId: Int, notification: Any, tag: String? = null) {
    if (!canNotify(ctx)) return

    val notificationManager = ctx.getSystemService(NotificationManager::class.java)
    val resolvedNotification = when (notification) {
        is NotificationCompat.Builder -> notification.build()
        is Notification -> notification
        else -> {
            Log.w("tNotify", "Unsupported notification type: ${notification::class.java.name}")
            return
        }
    }

    if (tag == null) {
        notificationManager?.notify(notificationId, resolvedNotification)
    } else {
        notificationManager?.notify(tag, notificationId, resolvedNotification)
    }
}