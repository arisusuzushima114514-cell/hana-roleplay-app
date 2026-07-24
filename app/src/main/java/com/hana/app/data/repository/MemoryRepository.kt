package com.hana.app.data.repository

import com.hana.app.data.db.dao.MemoryEntryDao
import com.hana.app.data.db.entity.MemoryEntryEntity
import com.hana.app.data.db.entity.MemoryScope
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MemoryRepository(
    private val dao: MemoryEntryDao
) {
    fun observeMainMemory(): Flow<List<MemoryEntryEntity>> = dao.observeByScope(MemoryScope.MAIN)

    suspend fun getMainMemory(): List<MemoryEntryEntity> = dao.getByScope(MemoryScope.MAIN)

    suspend fun upsertMainMemory(content: String, type: String, sourceConversationId: String, confidence: Float = 0.78f) {
        val clean = content.trim()
        if (clean.isBlank()) return
        val existing = dao.findByScopeAndContent(MemoryScope.MAIN, clean)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.update(existing.copy(updatedAt = now, confidence = maxOf(existing.confidence, confidence)))
        } else {
            dao.insert(
                MemoryEntryEntity(
                    id = UUID.randomUUID().toString(),
                    scope = MemoryScope.MAIN,
                    type = type,
                    content = clean,
                    sourceConversationId = sourceConversationId,
                    createdAt = now,
                    updatedAt = now,
                    confidence = confidence
                )
            )
        }
    }

    suspend fun togglePinned(id: String, current: Boolean) {
        dao.setPinned(id = id, isPinned = !current, updatedAt = System.currentTimeMillis())
    }

    suspend fun archive(id: String) {
        dao.archive(id = id, updatedAt = System.currentTimeMillis())
    }

    suspend fun update(item: MemoryEntryEntity, newContent: String, newType: String) {
        val clean = newContent.trim()
        if (clean.isBlank()) return
        dao.update(item.copy(content = clean, type = newType.trim().ifBlank { item.type }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun exportMainMemoryJson(): String {
        val items = getMainMemory()
        return JSONObject().apply {
            put("scope", MemoryScope.MAIN)
            put("exportedAt", System.currentTimeMillis())
            put(
                "items",
                JSONArray().also { array ->
                    items.forEach { item ->
                        array.put(
                            JSONObject()
                                .put("id", item.id)
                                .put("scope", item.scope)
                                .put("type", item.type)
                                .put("content", item.content)
                                .put("sourceConversationId", item.sourceConversationId)
                                .put("createdAt", item.createdAt)
                                .put("updatedAt", item.updatedAt)
                                .put("confidence", item.confidence)
                                .put("isPinned", item.isPinned)
                                .put("isArchived", item.isArchived)
                        )
                    }
                }
            )
        }.toString(2)
    }

    suspend fun importMainMemoryJson(json: String): Int {
        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: JSONArray()
        var imported = 0
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val content = item.optString("content").trim()
            if (content.isBlank()) continue
            if (dao.findByScopeAndContent(MemoryScope.MAIN, content) == null) {
                val importedId = item.optString("id").trim()
                val safeId = importedId.takeIf { it.isNotBlank() && dao.getById(it) == null }
                    ?: UUID.randomUUID().toString()
                val inserted = dao.insertIfAbsent(
                    MemoryEntryEntity(
                        id = safeId,
                        scope = MemoryScope.MAIN,
                        type = item.optString("type").ifBlank { "事实" },
                        content = content,
                        sourceConversationId = item.optString("sourceConversationId"),
                        createdAt = item.optLong("createdAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
                        updatedAt = item.optLong("updatedAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
                        confidence = item.optDouble("confidence", 0.78).toFloat(),
                        isPinned = item.optBoolean("isPinned", false),
                        isArchived = item.optBoolean("isArchived", false)
                    )
                )
                if (inserted != -1L) imported++
            }
        }
        return imported
    }
}
