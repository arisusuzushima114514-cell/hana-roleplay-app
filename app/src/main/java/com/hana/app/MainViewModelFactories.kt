package com.hana.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hana.app.core.AppContainer
import com.hana.app.viewmodel.ChatViewModel
import com.hana.app.viewmodel.ImageGenerationViewModel
import com.hana.app.viewmodel.MemoryViewModel
import com.hana.app.viewmodel.SettingsViewModel

internal class ChatViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                conversationRepository = appContainer.conversationRepository,
                messageRepository = appContainer.messageRepository,
                characterRepository = appContainer.characterRepository,
                apiService = appContainer.apiService,
                settingsRepository = appContainer.settingsRepository,
                backgroundManager = appContainer.backgroundManager,
                modelRepository = appContainer.modelRepository,
                modelCacheRepository = appContainer.modelCacheRepository,
                modelService = appContainer.modelService,
                attachmentService = appContainer.attachmentService,
                memoryRepository = appContainer.memoryRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}

internal class SettingsViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                repository = appContainer.settingsRepository,
                modelService = appContainer.modelService,
                modelRepository = appContainer.modelRepository,
                providerAccountService = appContainer.providerAccountService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}

internal class ImageGenerationViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImageGenerationViewModel::class.java)) {
            return ImageGenerationViewModel(
                settingsRepository = appContainer.settingsRepository,
                modelRepository = appContainer.modelRepository,
                imageGenerationService = appContainer.imageGenerationService,
                attachmentService = appContainer.attachmentService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}

internal class MemoryViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            return MemoryViewModel(appContainer.memoryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
