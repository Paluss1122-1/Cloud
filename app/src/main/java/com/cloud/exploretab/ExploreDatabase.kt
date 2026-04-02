package com.cloud.exploretab

import android.content.Context
import androidx.room.*
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

@Dao
interface ExploredTileDao {
    @Query("SELECT COUNT(*) FROM explored_tiles")
    fun countFlow(): Flow<Long>

    @Query("SELECT * FROM explored_tiles ORDER BY timestamp DESC")
    fun allFlow(): Flow<List<ExploredTile>>

    @Query("SELECT * FROM explored_tiles WHERE tileX BETWEEN :minX AND :maxX AND tileY BETWEEN :minY AND :maxY")
    suspend fun inViewport(minX: Long, maxX: Long, minY: Long, maxY: Long): List<ExploredTile>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTile(tile: ExploredTile): Long

    @Query("SELECT COUNT(*) FROM explored_tiles WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Long
}

@Database(entities = [ExploredTile::class], version = 1, exportSchema = false)
abstract class ExploreDatabase : RoomDatabase() {
    abstract fun dao(): ExploredTileDao

    companion object {
        @Volatile
        private var INSTANCE: ExploreDatabase? = null

        fun get(context: Context): ExploreDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ExploreDatabase::class.java,
                    "explore_db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
    }
}
