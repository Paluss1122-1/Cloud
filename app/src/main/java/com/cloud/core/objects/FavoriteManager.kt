package com.cloud.core.objects

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
}