package com.hana.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hana.app.data.db.entity.SavedModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedModelDao {
    @Query("SELECT * FROM saved_models ORDER BY isActive DESC, createdAt DESC")
    fun observeAll(): Flow<List<SavedModelEntity>>

    @Query("SELECT * FROM saved_models ORDER BY isActive DESC, createdAt DESC")
    suspend fun getAll(): List<SavedModelEntity>

    @Query("SELECT * FROM saved_models WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): SavedModelEntity?

    @Query("SELECT * FROM saved_models WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavedModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedModelEntity): Long

    @Update
    suspend fun update(entity: SavedModelEntity)

    @Delete
    suspend fun delete(entity: SavedModelEntity)

    @Query("UPDATE saved_models SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun activateExclusive(id: Long)

    @Query("UPDATE saved_models SET modelCount = :count, lastRefreshAt = :refreshAt WHERE id = :id")
    suspend fun updateModelCount(id: Long, count: Int, refreshAt: Long)

    @Transaction
    suspend fun switchActiveTo(id: Long) {
        activateExclusive(id)
    }
}
