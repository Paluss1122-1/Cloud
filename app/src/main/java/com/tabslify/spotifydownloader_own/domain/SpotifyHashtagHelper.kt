package com.tabslify.spotifydownloader_own.domain

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.tabslify.quiethoursnotificationhelper.AiProvider
import com.tabslify.quiethoursnotificationhelper.sendAiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun encodeAudio(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        android.util.Base64OutputStream(output, Base64.NO_WRAP).use { base64Out ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                base64Out.write(buffer, 0, bytesRead)
            }
        }
        output.toString("UTF-8")
    }
} catch (_: Exception) {
    null
}

suspend fun generateAndSaveHashtags(
    ctx: Context,
    trackId: String,
    title: String,
    artist: String,
    album: String,
    fileUri: Uri
): String? = withContext(Dispatchers.IO) {
    try {
        val userMessage = """
            Erstelle Hashtags für diesen Song:
            Titel: $title
            Künstler: $artist
            Album: $album
            
            Gib NUR die Hashtags zurück, ohne zusätzlichen Text. Die Hashtags sollen Genres beschreiben oder den Song beschreiben. Trenne sie mit Leerzeichen.
        """.trimIndent()

        val hashtags = sendAiRequest(
            userMessage = userMessage,
            audioUri = fileUri,
            context = ctx,
            model = "gemini-2.5-flash",
            provider = AiProvider.GEMINI,
            target = ""
        )?.trim()

        if (hashtags != null) {
            saveHashtags(ctx, trackId, hashtags)
        }
        hashtags
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun saveHashtags(ctx: Context, trackId: String, hashtags: String) {
    val prefs = ctx.getSharedPreferences("spotify_hashtags", Context.MODE_PRIVATE)
    prefs.edit().putString(trackId, hashtags).apply()
}

fun getHashtags(ctx: Context, trackId: String): String? {
    val prefs = ctx.getSharedPreferences("spotify_hashtags", Context.MODE_PRIVATE)
    return prefs.getString(trackId, null)
}
