package com.imagegrabber.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ImageEntity>)

    @Query("SELECT * FROM images WHERE imageKey IN (:keys)")
    suspend fun getByKeys(keys: List<String>): List<ImageEntity>

    @Query("SELECT * FROM images WHERE favorite = 1 OR status != 'NONE'")
    fun observeTracked(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE status != 'NONE' ORDER BY updatedAt DESC")
    fun observeDownloads(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE status = 'QUEUED' ORDER BY rowid ASC LIMIT 1")
    suspend fun nextQueued(): ImageEntity?

    @Query("SELECT COUNT(*) FROM images WHERE status IN ('QUEUED','DOWNLOADING')")
    suspend fun countActive(): Int

    @Query("UPDATE images SET favorite = :fav, updatedAt = :t WHERE imageKey = :key")
    suspend fun setFavorite(key: String, fav: Boolean, t: Long)

    @Query("UPDATE images SET status = :status, error = :error, updatedAt = :t WHERE imageKey = :key")
    suspend fun setStatus(key: String, status: String, error: String?, t: Long)

    @Query("UPDATE images SET status = 'DONE', localUri = :uri, sizeBytes = :size, error = NULL, updatedAt = :t WHERE imageKey = :key")
    suspend fun setDone(key: String, uri: String, size: Long, t: Long)

    @Query("UPDATE images SET status = :newStatus, error = NULL, updatedAt = :t WHERE status = :oldStatus")
    suspend fun moveAll(oldStatus: String, newStatus: String, t: Long)

    @Query("UPDATE images SET status = 'PAUSED', updatedAt = :t WHERE status IN ('QUEUED','DOWNLOADING')")
    suspend fun pauseAll(t: Long)

    @Query("UPDATE images SET status = 'QUEUED', error = NULL, updatedAt = :t WHERE imageKey IN (:keys) AND status IN ('NONE','FAILED','PAUSED')")
    suspend fun queueKeys(keys: List<String>, t: Long)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(h: SearchHistory)

    @Query("SELECT * FROM history ORDER BY time DESC LIMIT 12")
    fun observe(): Flow<List<SearchHistory>>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [ImageEntity::class, SearchHistory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun images(): ImageDao
    abstract fun history(): HistoryDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "app.db")
                .build().also { inst = it }
        }
    }
}
