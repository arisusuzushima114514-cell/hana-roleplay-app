package com.hana.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: String,
    val role: String,
    val speakerCharacterId: String? = null,
    val speakerName: String? = null,
    val content: String,
    val thinkingContent: String? = null,
    val thinkingDuration: Int? = null,
    val timestamp: Long,
    val tokenCount: Int? = null,
    val isError: Boolean = false,
    val isFavorite: Boolean = false
)
