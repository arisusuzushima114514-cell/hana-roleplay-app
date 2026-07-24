package com.hana.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_models")
@androidx.compose.runtime.Stable
data class SavedModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val apiKey: String,
    val baseUrl: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
    val modelCount: Int = 0,
    val lastRefreshAt: Long = 0L
)
