package com.hana.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hana.app.data.db.entity.CachedModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedModelDao {
    @Query("SELECT * FROM cached_models ORDER BY isFavorite DESC, name ASC")
    fun observeAll(): Flow<List<CachedModelEntity>>

    @Query("SELECT * FROM cached_models ORDER BY isFavorite DESC, name ASC")
    suspend fun getAll(): List<CachedModelEntity>

    @Query("SELECT * FROM cached_models WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavorites(): List<CachedModelEntity>

    @Query("SELECT * FROM cached_models WHERE isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<CachedModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<CachedModelEntity>)

    @Query("UPDATE cached_models SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM cached_models")
    suspend fun deleteAll()

    @Query("DELETE FROM cached_models WHERE baseUrl = :baseUrl")
    suspend fun deleteByBaseUrl(baseUrl: String)

    @Query("SELECT COUNT(*) FROM cached_models")
    suspend fun count(): Int
}
