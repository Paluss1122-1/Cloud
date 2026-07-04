package com.tabslify.core.objects

import android.content.Context
import androidx.core.content.edit

object FavoriteManager {
    private const val PREF_NAME = "app_prefs"
    private const val KEY_FAVORITES = "favorites"

    fun saveFavorites(context: Context, favorites: Set<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit(commit = true) {
            putStringSet(KEY_FAVORITES, favorites)
        }
    }

    fun loadFavorites(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun pruneMissing(context: Context, existingFileNames: Collection<String>): Set<String> {
        val current = loadFavorites(context)
        val existing = existingFileNames.toHashSet()
        val pruned = PrefsCleanup.pruneInvalid(current) { existing.contains(it) }
        if (pruned !== current) saveFavorites(context, pruned)
        return pruned
    }

    fun remove(context: Context, fileName: String) {
        val current = loadFavorites(context)
        if (fileName !in current) return
        saveFavorites(context, current - fileName)
    }
}
