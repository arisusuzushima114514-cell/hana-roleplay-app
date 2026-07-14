package com.hana.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val conversationType: String = "normal",
    val characterId: String? = null,
    val participantCharacterIds: String? = null,
    val characterName: String? = null,
    val characterAvatar: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String? = null,
    val isNamed: Boolean = false,
    val modelName: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val contextLimit: Int = 36,
    val systemPrompt: String? = null,
    val totalTokens: Int = 0,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false
)
