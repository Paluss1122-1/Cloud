package com.tabslify.tabs.exploretab

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "explored_tiles",
    indices = [Index(value = ["tileX", "tileY"], unique = true)]
)
data class ExploredTile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tileX: Long,
    val tileY: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "raw_points",
    indices = [Index(value = ["timestamp"])]
)
data class RawPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val speed: Float?,
    val bearing: Float?,
    val activityType: String,
    val activityConfidence: Int,
    val timestamp: Long,
    val altitude: Double? = null
)

@Entity(
    tableName = "segments",
    indices = [Index(value = ["startTime"]), Index(value = ["endTime"])]
)
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val startTime: Long,
    val endTime: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val distanceMeters: Float,
    val pointCount: Int
)

@Entity(
    tableName = "stays",
    indices = [Index(value = ["startTime"]), Index(value = ["endTime"])]
)
data class Stay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val centerLat: Double,
    val centerLon: Double,
    val startTime: Long,
    val endTime: Long,
    val placeName: String?,
    val isHome: Boolean
)

@Entity(
    tableName = "tracker_events",
    indices = [Index(value = ["timestamp"])]
)
data class TrackerEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val mode: String,
    val confidence: Int,
    val detail: String?
)

@Dao
interface ExploredTileDao {
    @Query("SELECT COUNT(*) FROM explored_tiles")
    fun countFlow(): Flow<Long>

    @Query("SELECT * FROM explored_tiles ORDER BY timestamp DESC")
    fun allFlow(): Flow<List<ExploredTile>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTile(tile: ExploredTile): Long

    @Query("SELECT COUNT(*) FROM explored_tiles WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Long

    @Query("DELETE FROM explored_tiles WHERE tileX = :x AND tileY = :y")
    suspend fun deleteTile(x: Long, y: Long)
}

@Dao
interface RawPointDao {
    @Insert
    suspend fun insert(point: RawPoint): Long

    @Query("SELECT COUNT(*) FROM raw_points")
    fun countAllFlow(): Flow<Long>

    @Query("SELECT * FROM raw_points WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp ASC")
    suspend fun pointsBetween(dayStart: Long, dayEnd: Long): List<RawPoint>

    @Query("SELECT * FROM raw_points WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp ASC")
    fun pointsBetweenFlow(dayStart: Long, dayEnd: Long): Flow<List<RawPoint>>

    @Query("DELETE FROM raw_points WHERE timestamp < :cutoff")
    suspend fun deleteBefore(cutoff: Long)
}

@Dao
interface SegmentDao {
    @Insert
    suspend fun insert(segment: Segment): Long

    @Query("SELECT * FROM segments WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    fun segmentsForDayFlow(dayStart: Long, dayEnd: Long): Flow<List<Segment>>

    @Query("SELECT * FROM segments WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    suspend fun segmentsBetween(dayStart: Long, dayEnd: Long): List<Segment>

    @Query("SELECT COUNT(*) FROM segments WHERE startTime >= :dayStart AND startTime < :dayEnd")
    suspend fun countForDay(dayStart: Long, dayEnd: Long): Int

    @Query("DELETE FROM segments WHERE startTime >= :dayStart AND startTime < :dayEnd")
    suspend fun deleteForDay(dayStart: Long, dayEnd: Long)
}

@Dao
interface StayDao {
    @Insert
    suspend fun insert(stay: Stay): Long

    @Query("SELECT * FROM stays WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    fun staysForDayFlow(dayStart: Long, dayEnd: Long): Flow<List<Stay>>

    @Query("SELECT * FROM stays WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    suspend fun staysBetween(dayStart: Long, dayEnd: Long): List<Stay>

    @Query("DELETE FROM stays WHERE startTime >= :dayStart AND startTime < :dayEnd")
    suspend fun deleteForDay(dayStart: Long, dayEnd: Long)
}

@Dao
interface TrackerEventDao {
    @Insert
    suspend fun insert(event: TrackerEvent): Long

    @Query("SELECT * FROM tracker_events WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    suspend fun eventsBetween(from: Long, to: Long): List<TrackerEvent>

    @Query("DELETE FROM tracker_events WHERE timestamp < :cutoff")
    suspend fun deleteBefore(cutoff: Long)
}

@Database(
    entities = [ExploredTile::class, RawPoint::class, Segment::class, Stay::class, TrackerEvent::class],
    version = 4,
    exportSchema = false
)
abstract class ExploreDatabase : RoomDatabase() {
    abstract fun dao(): ExploredTileDao
    abstract fun rawPointDao(): RawPointDao
    abstract fun segmentDao(): SegmentDao
    abstract fun stayDao(): StayDao
    abstract fun trackerEventDao(): TrackerEventDao

    companion object {
        @Volatile
        private var INSTANCE: ExploreDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `raw_points` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `speed` REAL,
                        `bearing` REAL,
                        `activityType` TEXT NOT NULL,
                        `activityConfidence` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_points_timestamp` ON `raw_points` (`timestamp`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `segments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `startLat` REAL NOT NULL,
                        `startLon` REAL NOT NULL,
                        `endLat` REAL NOT NULL,
                        `endLon` REAL NOT NULL,
                        `distanceMeters` REAL NOT NULL,
                        `pointCount` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_segments_startTime` ON `segments` (`startTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_segments_endTime` ON `segments` (`endTime`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stays` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `centerLat` REAL NOT NULL,
                        `centerLon` REAL NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `placeName` TEXT,
                        `isHome` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stays_startTime` ON `stays` (`startTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stays_endTime` ON `stays` (`endTime`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tracker_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `confidence` INTEGER NOT NULL,
                        `detail` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracker_events_timestamp` ON `tracker_events` (`timestamp`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `raw_points` ADD COLUMN `altitude` REAL")
            }
        }

        fun get(context: Context): ExploreDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ExploreDatabase::class.java,
                    "explore_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
    }
}
