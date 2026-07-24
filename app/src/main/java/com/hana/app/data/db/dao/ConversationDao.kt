package com.hana.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hana.app.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun observeAllOnce(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM conversations WHERE characterId = :characterId")
    suspend fun countByCharacterId(characterId: String): Int

    @Query("SELECT * FROM conversations WHERE characterId = :characterId LIMIT 1")
    suspend fun getByCharacterId(characterId: String): ConversationEntity?

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE conversations SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePinned(id: String)

    @Query("UPDATE conversations SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE conversations SET lastMessage = :lastMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastMessage(id: String, lastMessage: String?, updatedAt: Long)

    @Query("UPDATE conversations SET title = :title, isNamed = :isNamed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, isNamed: Boolean, updatedAt: Long)

    @Query("""
        UPDATE conversations
        SET modelName = :modelName, temperature = :temperature, topP = :topP,
            maxTokens = :maxTokens, contextLimit = :contextLimit, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateParameters(
        id: String,
        modelName: String?,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        contextLimit: Int,
        updatedAt: Long
    )

    @Query("UPDATE conversations SET systemPrompt = :systemPrompt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSystemPrompt(id: String, systemPrompt: String?, updatedAt: Long)

    @Query("""
        UPDATE conversations
        SET historySummary = :historySummary,
            summaryUpToMessageId = :summaryUpToMessageId,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateHistorySummary(
        id: String,
        historySummary: String?,
        summaryUpToMessageId: Long?,
        updatedAt: Long
    )

    @Query("UPDATE conversations SET authorNote = :authorNote, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAuthorNote(id: String, authorNote: String?, updatedAt: Long)

    @Query("UPDATE conversations SET worldInfo = :worldInfo, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWorldInfo(id: String, worldInfo: String?, updatedAt: Long)

    @Query("UPDATE conversations SET groupScene = :groupScene, groupSceneLocked = :locked, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGroupScene(id: String, groupScene: String?, locked: Boolean, updatedAt: Long)

    @Query("UPDATE conversations SET totalTokens = totalTokens + :tokens, updatedAt = :updatedAt WHERE id = :id")
    suspend fun addTokenUsage(id: String, tokens: Int, updatedAt: Long)

    @Query("SELECT * FROM conversations WHERE conversationType = 'group'")
    suspend fun getGroupConversations(): List<ConversationEntity>

    @Query("UPDATE conversations SET participantCharacterIds = :participantCharacterIds, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateParticipants(id: String, participantCharacterIds: String?, updatedAt: Long)
}
