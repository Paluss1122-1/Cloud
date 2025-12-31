package com.example.cloud.musicstatstab

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson

data class SongStats(
    val name: String,
    val path: String,
    val playCount: Int = 0,
    val totalPlayTimeMs: Long = 0L,
    val totalTimePlayedMs: Long = 0L, // Nur echte Spielzeit (ohne Pausen)
    val lastPlayedTime: Long = 0L,
    val duration: Long = 0L,
    val pauseCount: Int = 0, // Wie oft pausiert
    val skipCount: Int = 0, // Wie oft übersprungen
    val interruptCount: Int = 0, // Wie oft Service beendet
    val completionRate: Float = 0f, // Prozentsatz vollständig gehört
    val averagePlaybackPosition: Long = 0L, // Durchschnittliche Position wenn pausiert
    val firstPlayedTime: Long = 0L, // Wann zum ersten Mal gehört
    val longestSessionMs: Long = 0L, // Längste Session
    val abruptStopsCount: Int = 0 // Abrupte Stopps (nicht zu Ende gehört)
)

class MusicStatsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "music_stats_prefs",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    private companion object {
        const val KEY_STATS = "song_stats"
        const val KEY_SESSION_START = "session_start_time"
        const val KEY_LAST_SONG = "last_playing_song"
        const val KEY_SESSION_PAUSE_TIME = "session_pause_time"
        const val KEY_SESSION_PLAY_TIME = "session_play_time"
    }

    fun recordSongStart(songPath: String, songName: String, duration: Long) {
        prefs.edit(commit = true) {
            putLong(KEY_SESSION_START, System.currentTimeMillis())
            putLong(KEY_SESSION_PLAY_TIME, 0L)
            putLong(KEY_SESSION_PAUSE_TIME, 0L)
            putString(KEY_LAST_SONG, songPath)
        }
    }

    fun recordSongPause(songPath: String, songName: String, duration: Long, currentPosition: Long) {
        val sessionStart = prefs.getLong(KEY_SESSION_START, System.currentTimeMillis())
        val sessionPlayTime = prefs.getLong(KEY_SESSION_PLAY_TIME, 0L)
        val playTimeMs = (System.currentTimeMillis() - sessionStart) + sessionPlayTime

        val stats = getAllStats().toMutableList()
        val existingIndex = stats.indexOfFirst { it.path == songPath }

        if (existingIndex >= 0) {
            val existing = stats[existingIndex]
            val completionPercent = if (duration > 0) {
                ((currentPosition.toFloat() / duration) * 100).coerceIn(0f, 100f)
            } else {
                0f
            }

            stats[existingIndex] = existing.copy(
                pauseCount = existing.pauseCount + 1,
                totalPlayTimeMs = existing.totalPlayTimeMs + playTimeMs,
                totalTimePlayedMs = existing.totalTimePlayedMs + playTimeMs,
                lastPlayedTime = System.currentTimeMillis(),
                duration = duration,
                averagePlaybackPosition = (existing.averagePlaybackPosition + currentPosition) / 2,
                longestSessionMs = maxOf(existing.longestSessionMs, playTimeMs),
                completionRate = completionPercent
            )
        }

        saveStats(stats)

        prefs.edit(commit = true) {
            putLong(KEY_SESSION_PAUSE_TIME, System.currentTimeMillis())
        }
    }

    fun recordSongResume(songPath: String) {
        val pauseTime = prefs.getLong(KEY_SESSION_PAUSE_TIME, 0L)

        if (pauseTime > 0) {
            val sessionStart = prefs.getLong(KEY_SESSION_START, System.currentTimeMillis())
            val pauseDuration = System.currentTimeMillis() - pauseTime

            prefs.edit(commit = true) {
                putLong(KEY_SESSION_START, sessionStart + pauseDuration)
                putLong(KEY_SESSION_PAUSE_TIME, 0L)
            }
        }
    }

    fun recordSongEnd(
        songPath: String,
        songName: String,
        duration: Long,
        currentPosition: Long,
        isCompleted: Boolean = false
    ) {
        val sessionStart = prefs.getLong(KEY_SESSION_START, System.currentTimeMillis())
        val playTimeMs = (System.currentTimeMillis() - sessionStart).coerceAtLeast(0L)

        val stats = getAllStats().toMutableList()
        val existingIndex = stats.indexOfFirst { it.path == songPath }

        val completionPercent = if (duration > 0) {
            ((currentPosition.toFloat() / duration) * 100).coerceIn(0f, 100f)
        } else {
            0f
        }

        if (existingIndex >= 0) {
            val existing = stats[existingIndex]
            stats[existingIndex] = existing.copy(
                playCount = existing.playCount + (if (isCompleted) 1 else 0),
                totalPlayTimeMs = existing.totalPlayTimeMs + playTimeMs,
                totalTimePlayedMs = existing.totalTimePlayedMs + playTimeMs,
                lastPlayedTime = System.currentTimeMillis(),
                duration = duration,
                averagePlaybackPosition = (existing.averagePlaybackPosition + currentPosition) / 2,
                longestSessionMs = maxOf(existing.longestSessionMs, playTimeMs),
                completionRate = completionPercent,
                abruptStopsCount = existing.abruptStopsCount + (if (!isCompleted && currentPosition > 0) 1 else 0)
            )
        } else {
            stats.add(
                SongStats(
                    name = songName,
                    path = songPath,
                    playCount = if (isCompleted) 1 else 0,
                    totalPlayTimeMs = playTimeMs,
                    totalTimePlayedMs = playTimeMs,
                    lastPlayedTime = System.currentTimeMillis(),
                    duration = duration,
                    pauseCount = 0,
                    skipCount = 0,
                    interruptCount = 0,
                    completionRate = completionPercent,
                    averagePlaybackPosition = currentPosition,
                    firstPlayedTime = System.currentTimeMillis(),
                    longestSessionMs = playTimeMs,
                    abruptStopsCount = if (!isCompleted && currentPosition > 0) 1 else 0
                )
            )
        }

        saveStats(stats)

        prefs.edit(commit = true) {
            putLong(KEY_SESSION_START, 0L)
            putLong(KEY_SESSION_PAUSE_TIME, 0L)
            putLong(KEY_SESSION_PLAY_TIME, 0L)
        }
    }

    fun recordSongSkip(songPath: String) {
        val stats = getAllStats().toMutableList()
        val existingIndex = stats.indexOfFirst { it.path == songPath }

        if (existingIndex >= 0) {
            val existing = stats[existingIndex]
            stats[existingIndex] = existing.copy(
                skipCount = existing.skipCount + 1,
                abruptStopsCount = existing.abruptStopsCount + 1
            )
        }

        saveStats(stats)
    }

    fun recordServiceInterrupt(songPath: String, currentPosition: Long, duration: Long) {
        val sessionStart = prefs.getLong(KEY_SESSION_START, System.currentTimeMillis())
        val playTimeMs = (System.currentTimeMillis() - sessionStart).coerceAtLeast(0L)

        val stats = getAllStats().toMutableList()
        val existingIndex = stats.indexOfFirst { it.path == songPath }

        val completionPercent = if (duration > 0) {
            ((currentPosition.toFloat() / duration) * 100).coerceIn(0f, 100f)
        } else {
            0f
        }

        if (existingIndex >= 0) {
            val existing = stats[existingIndex]
            stats[existingIndex] = existing.copy(
                interruptCount = existing.interruptCount + 1,
                totalPlayTimeMs = existing.totalPlayTimeMs + playTimeMs,
                totalTimePlayedMs = existing.totalTimePlayedMs + playTimeMs,
                lastPlayedTime = System.currentTimeMillis(),
                averagePlaybackPosition = (existing.averagePlaybackPosition + currentPosition) / 2,
                longestSessionMs = maxOf(existing.longestSessionMs, playTimeMs),
                abruptStopsCount = existing.abruptStopsCount + 1,
                completionRate = completionPercent
            )
        }

        saveStats(stats)

        prefs.edit(commit = true) {
            putLong(KEY_SESSION_START, 0L)
            putLong(KEY_SESSION_PAUSE_TIME, 0L)
            putLong(KEY_SESSION_PLAY_TIME, 0L)
        }
    }

    fun getAllStats(): List<SongStats> {
        val json = prefs.getString(KEY_STATS, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<SongStats>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getStatsForSong(path: String): SongStats? {
        return getAllStats().find { it.path == path }
    }

    fun getSortedByPlayCount(): List<SongStats> {
        return getAllStats().sortedByDescending { it.playCount }
    }

    fun getSortedByTotalPlayTime(): List<SongStats> {
        return getAllStats().sortedByDescending { it.totalTimePlayedMs }
    }

    fun getSortedByMostRecent(): List<SongStats> {
        return getAllStats().sortedByDescending { it.lastPlayedTime }
    }

    fun getMostSkipped(): List<SongStats> {
        return getAllStats().sortedByDescending { it.skipCount }
    }

    fun getMostPausedSongs(): List<SongStats> {
        return getAllStats().sortedByDescending { it.pauseCount }
    }

    fun getFavoriteSongs(): List<SongStats> {
        return getAllStats()
            .filter { it.completionRate >= 80f && it.playCount >= 2 }
            .sortedByDescending { it.playCount }
    }

    fun getNeverCompleted(): List<SongStats> {
        return getAllStats()
            .filter { it.completionRate < 50f && it.pauseCount > 2 }
            .sortedByDescending { it.pauseCount }
    }

    fun getTotalStats(): Map<String, Any> {
        val allStats = getAllStats()
        return mapOf(
            "totalSongs" to allStats.size.toLong(),
            "totalPlays" to allStats.sumOf { it.playCount }.toLong(),
            "totalPlayTimeMs" to allStats.sumOf { it.totalTimePlayedMs },
            "totalPauses" to allStats.sumOf { it.pauseCount }.toLong(),
            "totalSkips" to allStats.sumOf { it.skipCount }.toLong(),
            "totalInterrupts" to allStats.sumOf { it.interruptCount }.toLong(),
            "averageCompletionRate" to if (allStats.isNotEmpty()) {
                allStats.map { it.completionRate }.average()
            } else {
                0.0
            }
        )
    }

    private fun saveStats(stats: List<SongStats>) {
        val json = gson.toJson(stats)
        prefs.edit(commit = true) { putString(KEY_STATS, json) }
    }
}