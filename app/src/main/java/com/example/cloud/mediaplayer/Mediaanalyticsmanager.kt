package com.example.cloud.mediaplayer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

data class PlaySession(
    val startedAt: Long,
    val endedAt: Long,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val durationMs: Long,
    val wasCompleted: Boolean,
    val skippedAtMs: Long? = null,
    val playbackContext: String = "manual" // "playlist:[id]", "favorites", "manual"
)

data class SongAnalytics(
    val path: String,
    val recordedAt: Long = System.currentTimeMillis(),

    val playCount: Int = 0,
    val totalListenedMs: Long = 0,
    val completionRate: Float = 0f,
    val skipCount: Int = 0,
    val skipPositions: List<Long> = emptyList(),

    val lastPlayedAt: Long? = null,
    val firstPlayedAt: Long? = null,
    val playHistory: List<PlaySession> = emptyList(),

    val favoritedAt: Long? = null,
    val isFavorite: Boolean = false,

    val hourlyPlayCount: Map<Int, Int> = emptyMap(),
    val playedViaPlaylist: Map<String, Int> = emptyMap()
)

data class SpeedEntry(val timestamp: Long, val speed: Float)

data class PodcastSession(
    val startedAt: Long,
    val endedAt: Long,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val averageSpeed: Float
)

data class PodcastAnalytics(
    val path: String,
    val showId: String,
    val recordedAt: Long = System.currentTimeMillis(),

    val playCount: Int = 0,
    val totalListenedMs: Long = 0,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val lastPosition: Long = 0,

    val speedHistory: List<SpeedEntry> = emptyList(),
    val averageSpeed: Float = 1.0f,

    val lastPlayedAt: Long? = null,
    val firstPlayedAt: Long? = null,
    val sessions: List<PodcastSession> = emptyList(),

    val rewindCount: Int = 0,
    val forwardSkipCount: Int = 0,
    val rewindPositions: List<Long> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Analytics Manager (Singleton)
// ─────────────────────────────────────────────────────────────────────────────

object MediaAnalyticsManager {

    private const val PREFS_NAME = "media_analytics"
    private const val KEY_SONG_PREFIX = "song_"
    private const val KEY_PODCAST_PREFIX = "podcast_"
    private const val MAX_PLAY_HISTORY = 100
    private const val MAX_SKIP_POSITIONS = 50

    private val gson: Gson = GsonBuilder().create()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Song Analytics ────────────────────────────────────────────────────────

    fun getSongAnalytics(path: String): SongAnalytics {
        val json =
            prefs?.getString(KEY_SONG_PREFIX + path.hashCode(), null) ?: return SongAnalytics(path)
        return try {
            gson.fromJson(json, SongAnalytics::class.java) ?: SongAnalytics(path)
        } catch (_: Exception) {
            SongAnalytics(path)
        }
    }

    fun getAllSongAnalytics(): Map<String, SongAnalytics> {
        val result = mutableMapOf<String, SongAnalytics>()
        prefs?.all?.forEach { (key, value) ->
            if (key.startsWith(KEY_SONG_PREFIX) && value is String) {
                try {
                    val a = gson.fromJson(value, SongAnalytics::class.java)
                    if (a != null) result[a.path] = a
                } catch (_: Exception) {
                }
            }
        }
        return result
    }

    fun recordSongStart(path: String, context: String = "manual") {
        val now = System.currentTimeMillis()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val a = getSongAnalytics(path)
        val updated = a.copy(
            playCount = a.playCount + 1,
            lastPlayedAt = now,
            firstPlayedAt = a.firstPlayedAt ?: now,
            hourlyPlayCount = a.hourlyPlayCount.toMutableMap().also {
                it[hour] = (it[hour] ?: 0) + 1
            }
        )
        saveSongAnalytics(updated)
    }

    fun recordSongEnd(
        path: String,
        positionMs: Long,
        durationMs: Long,
        wasCompleted: Boolean,
        context: String = "manual"
    ) {
        val now = System.currentTimeMillis()
        val a = getSongAnalytics(path)
        val sessionDuration = positionMs
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val session = PlaySession(
            startedAt = now - sessionDuration,
            endedAt = now,
            startPositionMs = 0,
            endPositionMs = positionMs,
            durationMs = sessionDuration,
            wasCompleted = wasCompleted,
            playbackContext = context
        )
        val history = (a.playHistory + session).takeLast(MAX_PLAY_HISTORY)
        val newAvgCompletion =
            history.map { it.endPositionMs.toFloat() / durationMs.coerceAtLeast(1) }.average()
                .toFloat().coerceIn(0f, 1f)
        val updated = a.copy(
            totalListenedMs = a.totalListenedMs + sessionDuration,
            completionRate = newAvgCompletion,
            playHistory = history
        )
        saveSongAnalytics(updated)
    }

    fun recordSongSkip(path: String, positionMs: Long) {
        val a = getSongAnalytics(path)
        val positions = (a.skipPositions + positionMs).takeLast(MAX_SKIP_POSITIONS)
        saveSongAnalytics(a.copy(skipCount = a.skipCount + 1, skipPositions = positions))
    }

    fun recordSongFavorite(path: String, isFavorite: Boolean) {
        val a = getSongAnalytics(path)
        saveSongAnalytics(
            a.copy(
                isFavorite = isFavorite,
                favoritedAt = if (isFavorite) System.currentTimeMillis() else a.favoritedAt
            )
        )
    }

    fun resetSongAnalytics(path: String) {
        prefs?.edit()?.remove(KEY_SONG_PREFIX + path.hashCode())?.apply()
    }

    private fun saveSongAnalytics(a: SongAnalytics) {
        prefs?.edit()?.putString(KEY_SONG_PREFIX + a.path.hashCode(), gson.toJson(a))?.apply()
    }

    // ── Podcast Analytics ─────────────────────────────────────────────────────

    fun getPodcastAnalytics(path: String, showId: String = "unassigned"): PodcastAnalytics {
        val json = prefs?.getString(KEY_PODCAST_PREFIX + path.hashCode(), null)
            ?: return PodcastAnalytics(path, showId)
        return try {
            gson.fromJson(json, PodcastAnalytics::class.java) ?: PodcastAnalytics(path, showId)
        } catch (_: Exception) {
            PodcastAnalytics(path, showId)
        }
    }

    fun getAllPodcastAnalytics(): Map<String, PodcastAnalytics> {
        val result = mutableMapOf<String, PodcastAnalytics>()
        prefs?.all?.forEach { (key, value) ->
            if (key.startsWith(KEY_PODCAST_PREFIX) && value is String) {
                try {
                    val a = gson.fromJson(value, PodcastAnalytics::class.java)
                    if (a != null) result[a.path] = a
                } catch (_: Exception) {
                }
            }
        }
        return result
    }

    fun recordPodcastSession(
        path: String,
        showId: String,
        startPos: Long,
        endPos: Long,
        speed: Float
    ) {
        val now = System.currentTimeMillis()
        val duration = ((endPos - startPos) / speed).toLong().coerceAtLeast(0)
        val a = getPodcastAnalytics(path, showId)
        val session = PodcastSession(
            startedAt = now - duration,
            endedAt = now,
            startPositionMs = startPos,
            endPositionMs = endPos,
            averageSpeed = speed
        )
        val speedEntry = SpeedEntry(now, speed)
        val newSpeedHistory = (a.speedHistory + speedEntry).takeLast(50)
        val avgSpeed = newSpeedHistory.map { it.speed }.average().toFloat()
        val updated = a.copy(
            playCount = a.playCount + 1,
            totalListenedMs = a.totalListenedMs + duration,
            lastPosition = endPos,
            lastPlayedAt = now,
            firstPlayedAt = a.firstPlayedAt ?: now,
            sessions = (a.sessions + session).takeLast(100),
            speedHistory = newSpeedHistory,
            averageSpeed = avgSpeed
        )
        savePodcastAnalytics(updated)
    }

    fun recordPodcastCompleted(path: String, showId: String) {
        val a = getPodcastAnalytics(path, showId)
        savePodcastAnalytics(a.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
    }

    fun recordPodcastRewind(path: String, showId: String, positionMs: Long) {
        val a = getPodcastAnalytics(path, showId)
        val positions = (a.rewindPositions + positionMs).takeLast(50)
        savePodcastAnalytics(a.copy(rewindCount = a.rewindCount + 1, rewindPositions = positions))
    }

    fun recordPodcastForward(path: String, showId: String) {
        val a = getPodcastAnalytics(path, showId)
        savePodcastAnalytics(a.copy(forwardSkipCount = a.forwardSkipCount + 1))
    }

    fun resetPodcastAnalytics(path: String) {
        prefs?.edit()?.remove(KEY_PODCAST_PREFIX + path.hashCode())?.apply()
    }

    private fun savePodcastAnalytics(a: PodcastAnalytics) {
        prefs?.edit()?.putString(KEY_PODCAST_PREFIX + a.path.hashCode(), gson.toJson(a))?.apply()
    }

    // ── Global Stats ──────────────────────────────────────────────────────────

    data class GlobalStats(
        val totalListenedMs: Long = 0,
        val listeningStreakDays: Int = 0,
        val topSongThisWeek: String? = null,
        val topSongThisWeekCount: Int = 0,
        val totalSongsPlayed: Int = 0
    )

    fun getGlobalStats(): GlobalStats {
        val allSong = getAllSongAnalytics()
        val totalMs = allSong.values.sumOf { it.totalListenedMs }
        val streak = calculateStreak(allSong)
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val topThisWeek = allSong.values
            .filter { (it.lastPlayedAt ?: 0) > weekAgo }
            .maxByOrNull { a -> a.playHistory.count { it.startedAt > weekAgo } }
        val topCount = topThisWeek?.playHistory?.count { it.startedAt > weekAgo } ?: 0
        return GlobalStats(
            totalListenedMs = totalMs,
            listeningStreakDays = streak,
            topSongThisWeek = topThisWeek?.path,
            topSongThisWeekCount = topCount,
            totalSongsPlayed = allSong.values.count { it.playCount > 0 }
        )
    }

    private fun calculateStreak(analytics: Map<String, SongAnalytics>): Int {
        val playDays = mutableSetOf<Long>()
        analytics.values.forEach { a ->
            a.playHistory.forEach { s ->
                val dayStart = s.startedAt / (24 * 60 * 60 * 1000L)
                playDays.add(dayStart)
            }
        }
        if (playDays.isEmpty()) return 0
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        var streak = 0
        var checkDay = today
        while (playDays.contains(checkDay)) {
            streak++
            checkDay--
        }
        return streak
    }
}