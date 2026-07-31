package com.tabslify.tabs.exploretab

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

const val TILE_SIZE = 0.005

fun locationToTile(lat: Double, lon: Double): Pair<Long, Long> =
    floor(lat / TILE_SIZE).toLong() to floor(lon / TILE_SIZE).toLong()

fun dayBounds(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

class ExploreRepository(context: Context) {
    private val db = ExploreDatabase.get(context)
    private val dao = db.dao()
    private val rawPointDao = db.rawPointDao()
    private val segmentDao = db.segmentDao()
    private val stayDao = db.stayDao()
    private val trackerEventDao = db.trackerEventDao()

    val countFlow: Flow<Long> = dao.countFlow()
    val allTilesFlow: Flow<List<ExploredTile>> = dao.allFlow()
    val rawPointCountFlow: Flow<Long> = rawPointDao.countAllFlow()

    suspend fun ingest(point: RawPoint) {
        rawPointDao.insert(point)
        val (x, y) = locationToTile(point.lat, point.lon)
        dao.insertTile(ExploredTile(tileX = x, tileY = y))
    }

    suspend fun pointsForDay(dayStart: Long, dayEnd: Long): List<RawPoint> =
        rawPointDao.pointsBetween(dayStart, dayEnd)

    fun pointsForDayFlow(dayStart: Long, dayEnd: Long): Flow<List<RawPoint>> =
        rawPointDao.pointsBetweenFlow(dayStart, dayEnd)

    fun segmentsForDay(dayStart: Long, dayEnd: Long): Flow<List<Segment>> =
        segmentDao.segmentsForDayFlow(dayStart, dayEnd)

    fun staysForDay(dayStart: Long, dayEnd: Long): Flow<List<Stay>> =
        stayDao.staysForDayFlow(dayStart, dayEnd)

    suspend fun hasSegmentsForDay(dayStart: Long, dayEnd: Long): Boolean =
        segmentDao.countForDay(dayStart, dayEnd) > 0

    suspend fun existingStaysForDay(dayStart: Long, dayEnd: Long): List<Stay> =
        stayDao.staysBetween(dayStart, dayEnd)

    suspend fun replaceDayResults(dayStart: Long, dayEnd: Long, segments: List<Segment>, stays: List<Stay>) {
        segmentDao.deleteForDay(dayStart, dayEnd)
        stayDao.deleteForDay(dayStart, dayEnd)
        segments.forEach { segmentDao.insert(it) }
        stays.forEach { stayDao.insert(it) }
    }

    suspend fun segmentsBetween(dayStart: Long, dayEnd: Long): List<Segment> =
        segmentDao.segmentsBetween(dayStart, dayEnd)

    suspend fun logTrackerEvent(event: TrackerEvent) {
        trackerEventDao.insert(event)
    }

    suspend fun trackerEventsBetween(from: Long, to: Long): List<TrackerEvent> =
        trackerEventDao.eventsBetween(from, to)

    suspend fun deleteRawPointsBefore(cutoff: Long) = rawPointDao.deleteBefore(cutoff)

    suspend fun deleteTrackerEventsBefore(cutoff: Long) = trackerEventDao.deleteBefore(cutoff)

    suspend fun todayCount(): Long {
        val midnight = System.currentTimeMillis() / 86_400_000L * 86_400_000L
        return dao.countSince(midnight)
    }

    suspend fun deleteTile(x: Long, y: Long) = dao.deleteTile(x, y)
}
