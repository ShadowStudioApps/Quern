package com.quern.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_addons")
data class AddonEntity(
    @PrimaryKey val manifestUrl: String,
    val baseUrl: String,
    val addonId: String,
    val name: String,
    val manifestJson: String,
    val enabled: Boolean = true,
    val priority: Int = 0
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val mediaType: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val episodeId: String?,
    val episodeLabel: String?,
    val lastStreamUrl: String?,
    val updatedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val mediaType: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val rating: String?,
    val origin: String,
    val sourceId: String?,
    val addedAt: Long
)

@Entity(tableName = "media_servers")
data class MediaServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val baseUrl: String,
    val username: String,
    val userId: String,
    val enabled: Boolean
)

@Dao
interface AddonDao {
    @Query("SELECT * FROM installed_addons ORDER BY priority ASC, name ASC")
    fun observeAll(): Flow<List<AddonEntity>>

    @Query("SELECT * FROM installed_addons ORDER BY priority ASC, name ASC")
    suspend fun getAll(): List<AddonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AddonEntity)

    @Query("UPDATE installed_addons SET enabled = :enabled WHERE manifestUrl = :manifestUrl")
    suspend fun setEnabled(manifestUrl: String, enabled: Boolean)

    @Query("UPDATE installed_addons SET priority = :priority WHERE manifestUrl = :manifestUrl")
    suspend fun setPriority(manifestUrl: String, priority: Int)

    @Query("DELETE FROM installed_addons WHERE manifestUrl = :manifestUrl")
    suspend fun delete(manifestUrl: String)
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentId = :contentId LIMIT 1")
    suspend fun getById(contentId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE contentId = :contentId")
    suspend fun delete(contentId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId)")
    fun observeIsFavorite(contentId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :contentId")
    suspend fun delete(contentId: String)
}

@Dao
interface MediaServerDao {
    @Query("SELECT * FROM media_servers ORDER BY name ASC")
    fun observeAll(): Flow<List<MediaServerEntity>>

    @Query("SELECT * FROM media_servers ORDER BY name ASC")
    suspend fun getAll(): List<MediaServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaServerEntity)

    @Query("DELETE FROM media_servers WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        AddonEntity::class,
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        MediaServerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class QuernDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun mediaServerDao(): MediaServerDao

    companion object {
        fun build(context: Context): QuernDatabase =
            Room.databaseBuilder(context.applicationContext, QuernDatabase::class.java, "quern.db")
                .build()
    }
}
