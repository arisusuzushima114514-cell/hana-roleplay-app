package com.hana.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_cards")
@androidx.compose.runtime.Stable
data class CharacterCardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String,
    val description: String,
    val greeting: String,
    val userPersona: String = "",
    val tags: String = "",
    val modelId: String = "",
    val temperature: Float = 0.9f,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long = 0L,
    val lastMessagePreview: String = "",
    val characterMode: String = CHARACTER_MODE_SINGLE,
    val subCharactersJson: String = EMPTY_SUB_CHARACTERS_JSON
)
