package com.hana.app.data.repository

import com.hana.app.data.db.dao.ChatMessageDao
import com.hana.app.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class MessageRepository(
    private val dao: ChatMessageDao
) {
    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>> {
        return dao.observeByConversation(conversationId)
    }

    suspend fun getMessages(conversationId: String): List<ChatMessageEntity> {
        return dao.getByConversation(conversationId)
    }

    suspend fun getMessagesByConversationIds(conversationIds: List<String>): List<ChatMessageEntity> {
        if (conversationIds.isEmpty()) return emptyList()
        return dao.getByConversationIds(conversationIds)
    }

    suspend fun insert(message: ChatMessageEntity): Long {
        return dao.insert(message)
    }

    suspend fun delete(messageId: Long) {
        dao.deleteById(messageId)
    }

    suspend fun deleteFrom(message: ChatMessageEntity) {
        dao.deleteFromMessage(
            conversationId = message.conversationId,
            id = message.id
        )
    }

    suspend fun deleteByConversation(conversationId: String) {
        dao.deleteByConversation(conversationId)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    fun observeFavorites(): Flow<List<ChatMessageEntity>> = dao.observeFavorites()

    suspend fun getFavorites(): List<ChatMessageEntity> = dao.getFavorites()

    suspend fun toggleFavorite(messageId: Long) {
        dao.toggleFavorite(messageId)
    }

    suspend fun getById(messageId: Long): ChatMessageEntity? = dao.getById(messageId)
}
