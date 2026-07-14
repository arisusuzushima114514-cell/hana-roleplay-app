package com.hana.app.data.repository

import com.hana.app.data.db.dao.ConversationDao
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val dao: ConversationDao
) {
    companion object {
        const val DEFAULT_CONTEXT_LIMIT = 36
        const val GROUP_CONTEXT_LIMIT = 20
    }

    fun observeConversations(): Flow<List<ConversationEntity>> = dao.observeAll()

    suspend fun getAll(): List<ConversationEntity> = dao.observeAllOnce()

    suspend fun getById(id: String): ConversationEntity? = dao.getById(id)

    suspend fun getByCharacterId(characterId: String): ConversationEntity? {
        return dao.getByCharacterId(characterId)
    }

    suspend fun createNormalConversation(title: String = "新对话"): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            lastMessage = null,
            isNamed = false,
            temperature = 0.7f,
            contextLimit = DEFAULT_CONTEXT_LIMIT
        )
        dao.insert(conversation)
        return conversation
    }

    suspend fun createCharacterConversation(character: CharacterCardEntity): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = character.name,
            conversationType = "character",
            characterId = character.id,
            characterName = character.name,
            characterAvatar = character.avatarUrl,
            createdAt = now,
            updatedAt = now,
            lastMessage = character.greeting,
            isNamed = false,
            temperature = 0.9f,
            contextLimit = DEFAULT_CONTEXT_LIMIT
        )
        dao.insert(conversation)
        return conversation
    }

    suspend fun createGroupConversation(characters: List<CharacterCardEntity>): ConversationEntity {
        require(characters.isNotEmpty()) { "characters must not be empty" }
        val now = System.currentTimeMillis()
        val title = characters.joinToString("、") { it.name }.take(30)
        val participantIds = characters.joinToString(",") { it.id }
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = if (title.isBlank()) "角色群聊" else title,
            conversationType = "group",
            participantCharacterIds = participantIds,
            characterName = characters.joinToString("、") { it.name }.take(80),
            characterAvatar = characters.firstOrNull()?.avatarUrl,
            createdAt = now,
            updatedAt = now,
            lastMessage = null,
            isNamed = false,
            temperature = 0.9f,
            contextLimit = GROUP_CONTEXT_LIMIT
        )
        dao.insert(conversation)
        return conversation
    }

    suspend fun updateLastMessage(conversation: ConversationEntity, lastMessage: String?) {
        dao.update(conversation.copy(lastMessage = lastMessage, updatedAt = System.currentTimeMillis()))
    }

    suspend fun rename(conversation: ConversationEntity, title: String, isNamed: Boolean = conversation.isNamed) {
        dao.update(conversation.copy(title = title, isNamed = isNamed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateParameters(
        conversation: ConversationEntity, modelName: String?, temperature: Float, topP: Float, maxTokens: Int, contextLimit: Int
    ) {
        dao.update(conversation.copy(
            modelName = modelName?.trim()?.takeIf { it.isNotBlank() },
            temperature = temperature.coerceIn(0f, 2f), topP = topP.coerceIn(0f, 1f),
            maxTokens = maxTokens.coerceAtLeast(1), contextLimit = contextLimit.coerceIn(1, 120),
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun updateSystemPrompt(conversation: ConversationEntity, systemPrompt: String) {
        dao.update(conversation.copy(systemPrompt = systemPrompt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addTokenUsage(conversation: ConversationEntity, tokens: Int) {
        if (tokens <= 0) return
        dao.update(conversation.copy(totalTokens = conversation.totalTokens + tokens, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(conversationId: String) {
        dao.deleteById(conversationId)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun isCharacterUsed(characterId: String): Boolean {
        return dao.countByCharacterId(characterId) > 0
    }

    suspend fun togglePinned(conversationId: String) {
        dao.togglePinned(conversationId)
    }

    suspend fun toggleFavorite(conversationId: String) {
        dao.toggleFavorite(conversationId)
    }
}
