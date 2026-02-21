package com.example.cloud.mediaplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

data class PodcastShow(
    val id: String,
    val name: String,
    val description: String = "",
    val matchPatterns: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val accentColorSeed: Int = name.hashCode()
)

data class PodcastEpisode(
    val path: String,
    val title: String,
    val showId: String,
    val publishedAt: Long? = null,
    val durationMs: Long = 0,
    val savedPositionMs: Long = 0,
    val isCompleted: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// PodcastShowManager (Singleton)
// ─────────────────────────────────────────────────────────────────────────────

object PodcastShowManager {

    private const val PREFS_NAME = "podcast_shows"
    private const val KEY_SHOWS = "shows_list"
    private const val KEY_PATTERNS = "pattern_mappings"

    private val defaultShows = listOf(
        PodcastShow(
            id = "aeffchen",
            name = "Äffchen mit Käffchen",
            description = "Äffchen mit Käffchen Podcast",
            matchPatterns = listOf("aeffchen", "äffchen", "kaeffchen", "käffchen"),
            accentColorSeed = "aeffchen".hashCode()
        ),
        PodcastShow(
            id = "heise",
            name = "Heise Show",
            description = "Heise Online Podcast",
            matchPatterns = listOf("heise", "heiseshow"),
            accentColorSeed = "heise".hashCode()
        ),
        PodcastShow(
            id = "unassigned",
            name = "Sonstige",
            description = "Alle nicht zugeordneten Folgen",
            matchPatterns = emptyList(),
            accentColorSeed = "unassigned".hashCode()
        )
    )

    private var prefs: SharedPreferences? = null
    private val shows = mutableListOf<PodcastShow>()

    // Extra pattern → showId mappings added via commands
    private val extraPatterns = mutableMapOf<String, String>() // pattern → showId

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadShows()
        loadPatternMappings()
    }

    // ── Shows CRUD ────────────────────────────────────────────────────────────

    fun getShows(): List<PodcastShow> = shows.toList()

    fun getShow(id: String): PodcastShow? = shows.find { it.id == id }

    fun createShow(name: String): PodcastShow {
        val id = name.lowercase().replace(" ", "_")
            .replace("[^a-z0-9_]".toRegex(), "") + "_${System.currentTimeMillis() % 10000}"
        val show = PodcastShow(id = id, name = name)
        shows.add(show)
        saveShows()
        return show
    }

    fun renameShow(oldName: String, newName: String): Boolean {
        val idx = shows.indexOfFirst { it.name.equals(oldName, ignoreCase = true) }
        if (idx < 0) return false
        shows[idx] = shows[idx].copy(name = newName)
        saveShows()
        return true
    }

    fun deleteShow(id: String): Boolean {
        if (id == "unassigned") return false
        val removed = shows.removeIf { it.id == id }
        if (removed) saveShows()
        return removed
    }

    // ── Pattern Assignment ────────────────────────────────────────────────────

    /**
     * Assigns a pattern to a show by name. Creates the show if it doesn't exist.
     * Example: assignPattern("heise", "Heise Show")
     */
    fun assignPattern(pattern: String, showName: String): Boolean {
        val show = shows.find { it.name.equals(showName, ignoreCase = true) }
            ?: createShow(showName)
        extraPatterns[pattern.lowercase()] = show.id
        // Also add pattern to the show's matchPatterns list
        val idx = shows.indexOf(show)
        if (idx >= 0) {
            val updated =
                show.copy(matchPatterns = (show.matchPatterns + pattern.lowercase()).distinct())
            shows[idx] = updated
        }
        saveShows()
        savePatternMappings()
        return true
    }

    fun removePattern(pattern: String) {
        extraPatterns.remove(pattern.lowercase())
        shows.forEachIndexed { idx, show ->
            if (show.matchPatterns.contains(pattern.lowercase())) {
                shows[idx] = show.copy(matchPatterns = show.matchPatterns - pattern.lowercase())
            }
        }
        saveShows()
        savePatternMappings()
    }

    // ── Episode → Show Assignment ─────────────────────────────────────────────

    fun resolveShowForEpisode(path: String, title: String): String {
        val combined = (path + " " + title).lowercase()
        // Check extra patterns first (user-defined take priority)
        extraPatterns.forEach { (pattern, showId) ->
            if (combined.contains(pattern)) return showId
        }
        // Check built-in patterns
        for (show in shows) {
            if (show.id == "unassigned") continue
            for (pattern in show.matchPatterns) {
                if (combined.contains(pattern.lowercase())) return show.id
            }
        }
        return "unassigned"
    }

    fun groupEpisodesIntoShows(episodes: List<PodcastEpisode>): Map<PodcastShow, List<PodcastEpisode>> {
        val result = mutableMapOf<PodcastShow, MutableList<PodcastEpisode>>()
        // Initialize all shows
        shows.forEach { show -> result[show] = mutableListOf() }
        episodes.forEach { ep ->
            val show = shows.find { it.id == ep.showId } ?: shows.find { it.id == "unassigned" }
            ?: return@forEach
            result[show]?.add(ep)
        }
        // Remove empty shows (except "Sonstige")
        return result.filter { (show, eps) -> eps.isNotEmpty() || show.id == "unassigned" }
    }

    // ── Show Stats ────────────────────────────────────────────────────────────

    data class ShowStats(
        val totalEpisodes: Int,
        val completedEpisodes: Int,
        val unheardEpisodes: Int,
        val totalDurationMs: Long,
        val listenedDurationMs: Long,
        val completionFraction: Float
    )

    fun getShowStats(
        showId: String,
        episodes: List<PodcastEpisode>,
        analytics: Map<String, PodcastAnalytics>
    ): ShowStats {
        val showEps = episodes.filter { it.showId == showId }
        val completed = showEps.count { it.isCompleted }
        val unheard = showEps.count { (analytics[it.path]?.playCount ?: 0) == 0 }
        val totalDur = showEps.sumOf { it.durationMs }
        val listenedMs = showEps.sumOf { analytics[it.path]?.totalListenedMs ?: 0L }
        val fraction =
            if (showEps.isEmpty()) 0f else (completed.toFloat() / showEps.size).coerceIn(0f, 1f)
        return ShowStats(showEps.size, completed, unheard, totalDur, listenedMs, fraction)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveShows() {
        val sb = StringBuilder()
        shows.forEach { show ->
            sb.append("${show.id}|||${show.name}|||${show.description}|||")
            sb.append(show.matchPatterns.joinToString("~~"))
            sb.append("|||${show.createdAt}|||${show.accentColorSeed}")
            sb.append("\n---\n")
        }
        prefs?.edit { putString(KEY_SHOWS, sb.toString()) }
    }

    private fun loadShows() {
        shows.clear()
        val raw = prefs?.getString(KEY_SHOWS, null)
        if (raw.isNullOrEmpty()) {
            shows.addAll(defaultShows)
            saveShows()
            return
        }
        try {
            val entries = raw.split("\n---\n").filter { it.isNotBlank() }
            entries.forEach { entry ->
                val parts = entry.split("|||")
                if (parts.size >= 6) {
                    val patterns = if (parts[3].isBlank()) emptyList()
                    else parts[3].split("~~").filter { it.isNotBlank() }
                    shows.add(
                        PodcastShow(
                            id = parts[0],
                            name = parts[1],
                            description = parts[2],
                            matchPatterns = patterns,
                            createdAt = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                            accentColorSeed = parts[5].toIntOrNull() ?: parts[1].hashCode()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            shows.clear()
            shows.addAll(defaultShows)
        }
        // Ensure "unassigned" always exists
        if (shows.none { it.id == "unassigned" }) {
            shows.add(defaultShows.last())
        }
    }

    private fun savePatternMappings() {
        val encoded = extraPatterns.entries.joinToString("\n") { "${it.key}|||${it.value}" }
        prefs?.edit { putString(KEY_PATTERNS, encoded) }
    }

    private fun loadPatternMappings() {
        extraPatterns.clear()
        val raw = prefs?.getString(KEY_PATTERNS, null) ?: return
        raw.lines().forEach { line ->
            val parts = line.split("|||")
            if (parts.size == 2) extraPatterns[parts[0]] = parts[1]
        }
    }
}