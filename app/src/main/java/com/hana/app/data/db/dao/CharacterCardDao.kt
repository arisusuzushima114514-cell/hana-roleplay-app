package com.hana.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hana.app.data.db.entity.CharacterCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterCardDao {
    @Query("SELECT * FROM character_cards ORDER BY lastMessageAt DESC, updatedAt DESC")
    fun observeAll(): Flow<List<CharacterCardEntity>>

    @Query("SELECT * FROM character_cards ORDER BY lastMessageAt DESC, updatedAt DESC")
    suspend fun getAll(): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CharacterCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CharacterCardEntity)

    @Update
    suspend fun update(entity: CharacterCardEntity)

    @Delete
    suspend fun delete(entity: CharacterCardEntity)

    @Query("SELECT COUNT(*) FROM character_cards")
    suspend fun count(): Int

    @Query("UPDATE character_cards SET lastMessageAt = :lastMessageAt, lastMessagePreview = :preview WHERE id = :id")
    suspend fun updateLastMessage(id: String, lastMessageAt: Long, preview: String)
}
