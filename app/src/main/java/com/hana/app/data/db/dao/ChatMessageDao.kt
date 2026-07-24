package com.hana.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hana.app.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun observeByConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp ASC, id ASC
        """
    )
    suspend fun getByConversation(conversationId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        DELETE FROM chat_messages
        WHERE conversationId = :conversationId
        AND id >= :id
        """
    )
    suspend fun deleteFromMessage(conversationId: String, id: Long)

    @Query(
        """
        DELETE FROM chat_messages
        WHERE conversationId = :conversationId
        AND id > :id
        """
    )
    suspend fun deleteAfterMessage(conversationId: String, id: Long)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM chat_messages WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun observeFavorites(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE isFavorite = 1 ORDER BY timestamp DESC")
    suspend fun getFavorites(): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE chat_messages SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChatMessageEntity?

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId AND role = :role")
    suspend fun countByRole(conversationId: String, role: String): Int

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLastByConversation(conversationId: String): ChatMessageEntity?

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE conversationId IN (:conversationIds)
        ORDER BY timestamp ASC, id ASC
        """
    )
    suspend fun getByConversationIds(conversationIds: List<String>): List<ChatMessageEntity>
}
