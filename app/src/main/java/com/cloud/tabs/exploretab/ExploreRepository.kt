package com.cloud.tabs.exploretab

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlin.math.floor

const val TILE_SIZE = 0.001

fun locationToTile(lat: Double, lon: Double): Pair<Long, Long> =
    floor(lat / TILE_SIZE).toLong() to floor(lon / TILE_SIZE).toLong()

class ExploreRepository(context: Context) {
    private val dao = ExploreDatabase.get(context).dao()

    val countFlow: Flow<Long> = dao.countFlow()
    val allTilesFlow: Flow<List<ExploredTile>> = dao.allFlow()

    suspend fun recordLocation(lat: Double, lon: Double) {
        val (x, y) = locationToTile(lat, lon)
        dao.insertTile(ExploredTile(tileX = x, tileY = y))
    }

    suspend fun getTilesInViewport(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<ExploredTile> {
        val minX = floor(minLat / TILE_SIZE).toLong()
        val maxX = floor(maxLat / TILE_SIZE).toLong()
        val minY = floor(minLon / TILE_SIZE).toLong()
        val maxY = floor(maxLon / TILE_SIZE).toLong()
        return dao.inViewport(minX, maxX, minY, maxY)
    }

    suspend fun todayCount(): Long {
        val midnight = System.currentTimeMillis() / 86_400_000L * 86_400_000L
        return dao.countSince(midnight)
    }
}