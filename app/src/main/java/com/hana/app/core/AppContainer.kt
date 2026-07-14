package com.hana.app.core

import android.content.Context
import android.util.Log
import com.hana.app.data.api.ApiService
import com.hana.app.data.api.AttachmentService
import com.hana.app.data.api.ImageGenerationService
import com.hana.app.data.db.AppDatabase
import com.hana.app.data.remote.ProviderAccountService
import com.hana.app.data.repository.CharacterRepository
import com.hana.app.data.repository.ConversationRepository
import com.hana.app.data.repository.MemoryRepository
import com.hana.app.data.repository.MessageRepository
import com.hana.app.data.repository.ModelCacheRepository
import com.hana.app.data.repository.ModelRepository
import com.hana.app.data.remote.ModelService
import com.hana.app.data.settings.SettingsRepository
import com.hana.app.manager.BackgroundManager
import kotlin.system.exitProcess

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy {
        try {
            AppDatabase.getInstance(appContext)
        } catch (e: Exception) {
            Log.e("AppContainer", "Failed to init database, exiting", e)
            throw e
        }
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    val backgroundManager: BackgroundManager by lazy {
        BackgroundManager(appContext)
    }

    val modelService: ModelService by lazy { ModelService() }

    val providerAccountService: ProviderAccountService by lazy { ProviderAccountService() }

    val apiService: ApiService by lazy {
        ApiService(settingsRepository, modelRepository)
    }

    val imageGenerationService: ImageGenerationService by lazy {
        ImageGenerationService()
    }

    val attachmentService: AttachmentService by lazy {
        AttachmentService(appContext)
    }

    val characterRepository: CharacterRepository by lazy {
        CharacterRepository(dao = database.characterCardDao())
    }

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(dao = database.conversationDao())
    }

    val messageRepository: MessageRepository by lazy {
        MessageRepository(dao = database.chatMessageDao())
    }

    val modelRepository: ModelRepository by lazy {
        ModelRepository(dao = database.savedModelDao())
    }
    val modelCacheRepository: ModelCacheRepository by lazy {
        ModelCacheRepository(dao = database.cachedModelDao())
    }
    val memoryRepository: MemoryRepository by lazy {
        MemoryRepository(dao = database.memoryEntryDao())
    }
}
