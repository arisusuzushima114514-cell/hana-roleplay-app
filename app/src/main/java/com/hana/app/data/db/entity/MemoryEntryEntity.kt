package com.hana.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_entries",
    indices = [Index("scope"), Index("updatedAt")]
)
@androidx.compose.runtime.Stable
data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val scope: String,
    val type: String,
    val content: String,
    val sourceConversationId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val confidence: Float = 0.7f,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

object MemoryScope {
    const val MAIN = "main"
}
