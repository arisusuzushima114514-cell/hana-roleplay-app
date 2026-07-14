package com.hana.app.data.repository

import com.hana.app.data.db.dao.CachedModelDao
import com.hana.app.data.db.entity.CachedModelEntity
import com.hana.app.data.db.entity.ModelInfo
import com.hana.app.data.db.entity.Capability
import com.hana.app.data.db.entity.ModelCapabilityMap
import com.hana.app.data.db.entity.guessProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelCacheRepository(
    private val dao: CachedModelDao
) {
    fun observeAll(): Flow<List<ModelInfo>> = dao.observeAll().map { list ->
        list.map { it.toModelInfo() }
    }

    fun observeFavorites(): Flow<List<ModelInfo>> = dao.observeFavorites().map { list ->
        list.map { it.toModelInfo() }
    }

    suspend fun getAll(): List<ModelInfo> = dao.getAll().map { it.toModelInfo() }

    suspend fun getFavorites(): List<ModelInfo> = dao.getFavorites().map { it.toModelInfo() }

    suspend fun replaceAll(models: List<String>, baseUrl: String) {
        val existingFavorites = dao.getAll()
            .filter { it.baseUrl == baseUrl }
            .associateBy({ it.name }, { it.isFavorite })
        dao.deleteByBaseUrl(baseUrl)
        val entities = models.map { name ->
            CachedModelEntity(
                id = "$baseUrl::$name",
                name = name,
                provider = guessProvider(name, baseUrl),
                baseUrl = baseUrl,
                isFavorite = existingFavorites[name] == true,
                capabilities = Capability.toString(ModelCapabilityMap.get(name))
            )
        }
        dao.insertAll(entities)
    }

    suspend fun toggleFavorite(modelId: String) {
        val entity = dao.getAll().firstOrNull { it.id == modelId } ?: return
        dao.setFavorite(modelId, !entity.isFavorite)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun isCacheEmpty(): Boolean = dao.count() == 0
}

private fun CachedModelEntity.toModelInfo() = ModelInfo(
    id = id,
    name = name,
    provider = provider,
    baseUrl = baseUrl,
    isFavorite = isFavorite,
    capabilities = Capability.fromString(capabilities)
)
