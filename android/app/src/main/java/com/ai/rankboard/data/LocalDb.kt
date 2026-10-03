package com.ai.rankboard.data

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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_entries", primaryKeys = ["boardSlug", "modelSlug"])
data class CachedEntryEntity(
    val boardSlug: String,
    val modelSlug: String,
    val displayName: String,
    val vendor: String?,
    val rank: Int,
    val score: Double?,
    val priceIn: Double?,
    val priceOut: Double?,
    val currency: String,
    val fetchedAt: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val modelSlug: String,
    val displayName: String,
    val savedAt: Long,
)

@Dao
interface LeaderboardDao {
    @Query("SELECT * FROM cached_entries WHERE boardSlug = :boardSlug ORDER BY rank ASC")
    suspend fun cachedEntries(boardSlug: String): List<CachedEntryEntity>

    @Upsert
    suspend fun upsertEntries(entries: List<CachedEntryEntity>)

    @Query("DELETE FROM cached_entries WHERE boardSlug = :boardSlug")
    suspend fun clearBoard(boardSlug: String)

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun favorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    suspend fun favoriteList(): List<FavoriteEntity>

    @Query("SELECT modelSlug FROM favorites")
    fun favoriteSlugs(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE modelSlug = :slug")
    suspend fun removeFavorite(slug: String)

    @Query("SELECT COUNT(*) FROM cached_entries")
    suspend fun cachedEntryCount(): Int

    @Query("DELETE FROM cached_entries")
    suspend fun clearCachedEntries()
}

@Database(entities = [CachedEntryEntity::class, FavoriteEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LeaderboardDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rankboard.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
