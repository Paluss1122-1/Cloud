package com.tabslify.core.objects

object PrefsCleanup {

    fun <T> capToLast(items: List<T>, max: Int): List<T> =
        if (items.size <= max) items else items.takeLast(max)

   fun pruneInvalid(items: Set<String>, isValid: (String) -> Boolean): Set<String> {
        val pruned = items.filterTo(mutableSetOf(), isValid)
        return if (pruned.size == items.size) items else pruned
    }
}
