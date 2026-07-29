package com.hana.app.data.repository

import com.hana.app.data.db.dao.SavedModelDao
import com.hana.app.data.db.entity.SavedModelEntity
import com.hana.app.data.settings.normalizeApiBaseUrl
import kotlinx.coroutines.flow.Flow

class ModelRepository(
    private val dao: SavedModelDao
) {
    fun observeModels(): Flow<List<SavedModelEntity>> = dao.observeAll()

    suspend fun getAll(): List<SavedModelEntity> = dao.getAll()

    suspend fun getActive(): SavedModelEntity? = dao.getActive()

    suspend fun getById(id: Long): SavedModelEntity? = dao.getById(id)

    suspend fun save(model: SavedModelEntity): Long {
        val normalized = model.copy(baseUrl = normalizeApiBaseUrl(model.baseUrl))
        return if (normalized.id == 0L) {
            dao.insert(normalized)
        } else {
            val current = dao.getById(normalized.id)
            dao.update(
                normalized.copy(
                    createdAt = current?.createdAt ?: normalized.createdAt,
                    isActive = current?.isActive ?: normalized.isActive
                )
            )
            normalized.id
        }
    }

    suspend fun delete(model: SavedModelEntity) {
        dao.delete(model)
    }

    suspend fun activate(id: Long) {
        dao.switchActiveTo(id)
    }

    suspend fun setActive(model: SavedModelEntity) {
        dao.switchActiveTo(model.id)
    }

    suspend fun updateModelCount(id: Long, count: Int, refreshAt: Long) {
        dao.updateModelCount(id, count, refreshAt)
    }

    suspend fun findDuplicate(baseUrl: String, apiKey: String): SavedModelEntity? {
        return dao.getAll().firstOrNull {
            it.baseUrl == normalizeApiBaseUrl(baseUrl) && it.apiKey == apiKey.trim()
        }
    }
}
