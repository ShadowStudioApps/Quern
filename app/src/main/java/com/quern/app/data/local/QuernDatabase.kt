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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/** Per-profile watch progress. Primary key is (profileId, contentId) since schema v2. */
@Entity(tableName = "watch_history", primaryKeys = ["profileId", "contentId"])
data class WatchHistoryEntity(
    val profileId: String,
    val contentId: String,
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

/** Per-profile favorites. Primary key is (profileId, contentId) since schema v2. */
@Entity(tableName = "favorites", primaryKeys = ["profileId", "contentId"])
data class FavoriteEntity(
    val profileId: String,
    val contentId: String,
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

/**
 * Remembers deletions for account sync so they reach other devices (schema v3).
 * [kind] is "favorite" or "progress".
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["kind", "profileId", "contentId"])
data class TombstoneEntity(
    val kind: String,
    val profileId: String,
    val contentId: String,
    val deletedAt: Long
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
    @Query("SELECT * FROM watch_history WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT 30")
    fun observeRecent(profileId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND contentId = :contentId LIMIT 1")
    suspend fun getById(profileId: String, contentId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId AND contentId = :contentId")
    suspend fun delete(profileId: String, contentId: String)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)

    /** Every profile's rows, for account sync. */
    @Query("SELECT * FROM watch_history")
    suspend fun getAllRows(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history")
    fun observeAllRows(): Flow<List<WatchHistoryEntity>>
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(profileId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND contentId = :contentId)")
    fun observeIsFavorite(profileId: String, contentId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND contentId = :contentId")
    suspend fun delete(profileId: String, contentId: String)

    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)

    /** Every profile's rows, for account sync. */
    @Query("SELECT * FROM favorites")
    suspend fun getAllRows(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites")
    fun observeAllRows(): Flow<List<FavoriteEntity>>
}

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM sync_tombstones WHERE kind = :kind")
    suspend fun getAll(kind: String): List<TombstoneEntity>

    @Query("SELECT * FROM sync_tombstones")
    fun observeAll(): Flow<List<TombstoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TombstoneEntity)

    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND profileId = :profileId AND contentId = :contentId")
    suspend fun delete(kind: String, profileId: String, contentId: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :before")
    suspend fun pruneOlderThan(before: Long)
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
        MediaServerEntity::class,
        TombstoneEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class QuernDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun mediaServerDao(): MediaServerDao
    abstract fun tombstoneDao(): TombstoneDao

    companion object {
        /**
         * @param legacyProfileId profile that receives history/favorites saved before profiles were
         * separated (the profile active at upgrade time).
         */
        fun build(context: Context, legacyProfileId: String): QuernDatabase =
            Room.databaseBuilder(context.applicationContext, QuernDatabase::class.java, "quern.db")
                .addMigrations(migration1To2(legacyProfileId), MIGRATION_2_3)
                .build()

        /** v2 -> v3: adds sync_tombstones for account sync. Existing tables are untouched. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_tombstones` (" +
                        "`kind` TEXT NOT NULL, `profileId` TEXT NOT NULL, `contentId` TEXT NOT NULL, " +
                        "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `profileId`, `contentId`))"
                )
            }
        }

        /** v1 -> v2: watch_history and favorites become per-profile. Keeps every existing row. */
        internal fun migration1To2(legacyProfileId: String) = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS watch_history_v2 (" +
                        "profileId TEXT NOT NULL, contentId TEXT NOT NULL, title TEXT NOT NULL, " +
                        "mediaType TEXT NOT NULL, posterUrl TEXT, backdropUrl TEXT, " +
                        "positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, episodeId TEXT, " +
                        "episodeLabel TEXT, lastStreamUrl TEXT, updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(profileId, contentId))"
                )
                db.execSQL(
                    "INSERT INTO watch_history_v2 (profileId, contentId, title, mediaType, posterUrl, " +
                        "backdropUrl, positionMs, durationMs, episodeId, episodeLabel, lastStreamUrl, updatedAt) " +
                        "SELECT ?, contentId, title, mediaType, posterUrl, backdropUrl, positionMs, durationMs, " +
                        "episodeId, episodeLabel, lastStreamUrl, updatedAt FROM watch_history",
                    arrayOf<Any>(legacyProfileId)
                )
                db.execSQL("DROP TABLE watch_history")
                db.execSQL("ALTER TABLE watch_history_v2 RENAME TO watch_history")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorites_v2 (" +
                        "profileId TEXT NOT NULL, contentId TEXT NOT NULL, title TEXT NOT NULL, " +
                        "mediaType TEXT NOT NULL, posterUrl TEXT, backdropUrl TEXT, year TEXT, " +
                        "rating TEXT, origin TEXT NOT NULL, sourceId TEXT, addedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(profileId, contentId))"
                )
                db.execSQL(
                    "INSERT INTO favorites_v2 (profileId, contentId, title, mediaType, posterUrl, " +
                        "backdropUrl, year, rating, origin, sourceId, addedAt) " +
                        "SELECT ?, contentId, title, mediaType, posterUrl, backdropUrl, year, rating, origin, " +
                        "sourceId, addedAt FROM favorites",
                    arrayOf<Any>(legacyProfileId)
                )
                db.execSQL("DROP TABLE favorites")
                db.execSQL("ALTER TABLE favorites_v2 RENAME TO favorites")
            }
        }
    }
}
