package com.hana.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hana.app.data.db.entity.MemoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEntryDao {
    @Query("SELECT * FROM memory_entries WHERE scope = :scope AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getByScope(scope: String): List<MemoryEntryEntity>

    @Query("SELECT * FROM memory_entries WHERE scope = :scope AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeByScope(scope: String): Flow<List<MemoryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: MemoryEntryEntity): Long

    @Update
    suspend fun update(entity: MemoryEntryEntity)

    @Query("SELECT * FROM memory_entries WHERE scope = :scope AND content = :content LIMIT 1")
    suspend fun findByScopeAndContent(scope: String, content: String): MemoryEntryEntity?

    @Query("SELECT * FROM memory_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryEntryEntity?

    @Query("UPDATE memory_entries SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE memory_entries SET isArchived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Long)

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAll()
}
