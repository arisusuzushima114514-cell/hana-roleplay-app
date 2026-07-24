package com.hana.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hana.app.data.db.entity.ModelInfo
import com.hana.app.data.remote.ModelService
import com.hana.app.data.remote.ProviderAccountInfo
import com.hana.app.data.remote.ProviderAccountService
import com.hana.app.data.db.entity.SavedModelEntity
import com.hana.app.data.settings.ApiSettings
import com.hana.app.data.settings.SettingsData
import com.hana.app.data.settings.SettingsRepository
import com.hana.app.data.settings.AppLanguage
import com.hana.app.ui.theme.ThemeMode
import com.hana.app.ui.theme.ThemePalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val language: String = "zh",
    val modelMessage: String = "",
    val connectionMessage: String = "",
    val timeoutSeconds: Int = 60,
    val historicalTokens: Long = 0L,
    val quickPhrasesText: String = "",
    val visionBaseUrl: String = "",
    val visionApiKey: String = "",
    val visionModelName: String = "",
    val summaryBaseUrl: String = "",
    val summaryApiKey: String = "",
    val summaryModelName: String = "",
    val autoSummaryThreshold: Int = com.hana.app.data.settings.DEFAULT_AUTO_SUMMARY_THRESHOLD,
    val supportsImage: Boolean = false,
    val supportsFile: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: ThemePalette = ThemePalette.VIOLET,
    val voiceInputEnabled: Boolean = true,
    val streamEnabled: Boolean = true,
    val searchIndependentMode: Boolean = true,
    val searchProviderUrl: String = "",
    val searchProviderKey: String = "",
    val personaEnabled: Boolean = false,
    val personaPrompt: String = "",
    val webSearchEnabled: Boolean = false,
    val creativePresetText: String = "",
    val characterCreativePresetEnabled: Map<String, Boolean> = emptyMap(),
    val characterCreativePresetAffectsPersona: Map<String, Boolean> = emptyMap(),
    val characterCreativePresetTexts: Map<String, String> = emptyMap(),
    val characterIndependentCreativePresetEnabled: Map<String, Boolean> = emptyMap(),
    val characterIndependentCreativePresetAffectsPersona: Map<String, Boolean> = emptyMap(),
    val autoThemeSuggestionEnabled: Boolean = true,
    val lastAutoThemeSuggestionTag: String = "",
    val imageProviderId: Long = 0L,
    val imageModelName: String = "",
    val backgroundIntensity: String = "soft",
    val activeProviderName: String = "",
    val activeImageProviderName: String = "",
    val activeProviderModelCount: Int = 0,
    val activeProviderModelPreview: List<String> = emptyList(),
    val activeProviderAvailableModels: List<String> = emptyList(),
    val providerModels: Map<Long, List<String>> = emptyMap()
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val modelService: ModelService,
    private val modelRepository: com.hana.app.data.repository.ModelRepository,
    private val providerAccountService: ProviderAccountService
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    val language = repository.languageFlow
    private val providerModelsCache = mutableMapOf<Long, List<String>>()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                val imageProvider = if (settings.imageProviderId > 0L && settings.imageModelName.isNotBlank()) {
                    modelRepository.getById(settings.imageProviderId)
                } else null
                _uiState.update {
                    it.copy(
                        apiBaseUrl = settings.apiBaseUrl,
                        apiKey = settings.apiKey,
                        selectedModel = settings.selectedModel,
                        language = settings.language,
                        timeoutSeconds = settings.timeoutSeconds,
                        historicalTokens = settings.historicalTokens,
                        quickPhrasesText = settings.quickPhrases.joinToString("\n"),
                        visionBaseUrl = settings.visionBaseUrl,
                        visionApiKey = settings.visionApiKey,
                        visionModelName = settings.visionModelName,
                        summaryBaseUrl = settings.summaryBaseUrl,
                        summaryApiKey = settings.summaryApiKey,
                        summaryModelName = settings.summaryModelName,
                        autoSummaryThreshold = settings.autoSummaryThreshold,
                        supportsImage = settings.supportsImage,
                        supportsFile = settings.supportsFile,
                        themeMode = settings.themeMode,
                        themePalette = settings.themePalette,
                        voiceInputEnabled = settings.voiceInputEnabled,
                        streamEnabled = settings.streamEnabled,
                        searchIndependentMode = settings.searchIndependentMode,
                        searchProviderUrl = settings.searchProviderUrl,
                        searchProviderKey = settings.searchProviderKey,
                        personaEnabled = settings.personaEnabled,
                        personaPrompt = settings.personaPrompt,
                        webSearchEnabled = settings.webSearchEnabled,
                        creativePresetText = settings.creativePresetText,
                        characterCreativePresetEnabled = settings.characterCreativePresetEnabled,
                        characterCreativePresetAffectsPersona = settings.characterCreativePresetAffectsPersona,
                        characterCreativePresetTexts = settings.characterCreativePresetTexts,
                        characterIndependentCreativePresetEnabled = settings.characterIndependentCreativePresetEnabled,
                        characterIndependentCreativePresetAffectsPersona = settings.characterIndependentCreativePresetAffectsPersona,
                        autoThemeSuggestionEnabled = settings.autoThemeSuggestionEnabled,
                        lastAutoThemeSuggestionTag = settings.lastAutoThemeSuggestionTag,
                        imageProviderId = settings.imageProviderId,
                        imageModelName = settings.imageModelName,
                        backgroundIntensity = settings.backgroundIntensity,
                        activeImageProviderName = imageProvider?.name.orEmpty()
                    )
                }
            }
        }
        viewModelScope.launch {
            modelRepository.observeModels().collect { models ->
                val active = models.firstOrNull { it.isActive }
                _uiState.update {
                    it.copy(
                        activeProviderName = active?.name.orEmpty(),
                        activeProviderModelCount = active?.modelCount ?: 0,
                        activeProviderModelPreview = if (active != null && it.selectedModel.isNotBlank()) listOf(it.selectedModel) else emptyList()
                    )
                }
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(apiBaseUrl = value) }
        viewModelScope.launch { repository.saveBaseUrl(value) }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value) }
        viewModelScope.launch { repository.saveApiKey(value) }
    }

    fun onSelectedModelChange(value: String) {
        _uiState.update { it.copy(selectedModel = value) }
        viewModelScope.launch { repository.saveSelectedModel(value) }
    }

    fun onLanguageChange(value: String) {
        _uiState.update { it.copy(language = value) }
        viewModelScope.launch { repository.saveLanguage(value) }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { repository.saveThemeMode(mode) }
        com.hana.app.ui.theme.applyThemeMode(mode)
    }

    fun onThemePaletteChange(palette: ThemePalette) {
        _uiState.update { it.copy(themePalette = palette) }
        viewModelScope.launch { repository.saveThemePalette(palette) }
    }

    fun toggleVoiceInput(enabled: Boolean) {
        _uiState.update { it.copy(voiceInputEnabled = enabled) }
        viewModelScope.launch { repository.saveVoiceInputEnabled(enabled) }
    }

    fun toggleStream(enabled: Boolean) {
        _uiState.update { it.copy(streamEnabled = enabled) }
        viewModelScope.launch { repository.saveStreamEnabled(enabled) }
    }

    fun saveSearchSettings(url: String, key: String, mode: Boolean) {
        _uiState.update { it.copy(searchProviderUrl = url, searchProviderKey = key, searchIndependentMode = mode) }
        viewModelScope.launch { repository.saveSearchSettings(url, key, mode) }
    }

    fun savePersonaSettings(enabled: Boolean, prompt: String) {
        _uiState.update { it.copy(personaEnabled = enabled, personaPrompt = prompt) }
        viewModelScope.launch { repository.savePersonaSettings(enabled, prompt) }
    }

    fun saveWebSearchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(webSearchEnabled = enabled) }
        viewModelScope.launch { repository.saveWebSearchEnabled(enabled) }
    }

    fun onCharacterCreativePresetEnabledChange(characterId: String, enabled: Boolean) {
        _uiState.update {
            it.copy(characterCreativePresetEnabled = it.characterCreativePresetEnabled.toMutableMap().apply {
                put(characterId, enabled)
            })
        }
        viewModelScope.launch { repository.saveCharacterCreativePresetEnabled(characterId, enabled) }
    }

    fun onCharacterCreativePresetAffectsPersonaChange(characterId: String, enabled: Boolean) {
        _uiState.update {
            it.copy(characterCreativePresetAffectsPersona = it.characterCreativePresetAffectsPersona.toMutableMap().apply {
                put(characterId, enabled)
            })
        }
        viewModelScope.launch { repository.saveCharacterCreativePresetAffectsPersona(characterId, enabled) }
    }

    fun onCreativePresetTextChange(value: String) {
        _uiState.update { it.copy(creativePresetText = value) }
        viewModelScope.launch { repository.saveCreativePresetText(value) }
    }

    fun onCharacterCreativePresetTextChange(characterId: String, value: String) {
        _uiState.update {
            it.copy(characterCreativePresetTexts = it.characterCreativePresetTexts.toMutableMap().apply {
                put(characterId, value)
            })
        }
        viewModelScope.launch { repository.saveCharacterCreativePresetText(characterId, value) }
    }

    fun onCharacterIndependentCreativePresetEnabledChange(characterId: String, enabled: Boolean) {
        _uiState.update {
            it.copy(characterIndependentCreativePresetEnabled = it.characterIndependentCreativePresetEnabled.toMutableMap().apply {
                put(characterId, enabled)
            })
        }
        viewModelScope.launch { repository.saveCharacterIndependentCreativePresetEnabled(characterId, enabled) }
    }

    fun onCharacterIndependentCreativePresetAffectsPersonaChange(characterId: String, enabled: Boolean) {
        _uiState.update {
            it.copy(characterIndependentCreativePresetAffectsPersona = it.characterIndependentCreativePresetAffectsPersona.toMutableMap().apply {
                put(characterId, enabled)
            })
        }
        viewModelScope.launch { repository.saveCharacterIndependentCreativePresetAffectsPersona(characterId, enabled) }
    }

    fun onAutoThemeSuggestionEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(autoThemeSuggestionEnabled = enabled) }
        viewModelScope.launch { repository.saveAutoThemeSuggestionEnabled(enabled) }
    }

    fun markAutoThemeSuggestionShown(tag: String) {
        viewModelScope.launch { repository.saveLastAutoThemeSuggestionTag(tag) }
    }

    fun onImageProviderChange(providerId: Long) {
        _uiState.update { it.copy(imageProviderId = providerId) }
        viewModelScope.launch { repository.saveImageProviderId(providerId) }
    }

    fun onImageModelNameChange(modelName: String) {
        _uiState.update { it.copy(imageModelName = modelName) }
        viewModelScope.launch { repository.saveImageModelName(modelName) }
    }

    fun onBackgroundIntensityChange(value: String) {
        _uiState.update { it.copy(backgroundIntensity = value) }
        viewModelScope.launch { repository.saveBackgroundIntensity(value) }
    }

    fun onTimeoutSecondsChange(value: String) {
        val seconds = value.toIntOrNull() ?: return
        _uiState.update { it.copy(timeoutSeconds = seconds.coerceIn(5, 300)) }
        viewModelScope.launch { repository.saveTimeoutSeconds(seconds) }
    }

    fun onQuickPhrasesChange(value: String) {
        _uiState.update { it.copy(quickPhrasesText = value) }
        viewModelScope.launch {
            repository.saveQuickPhrases(value.lines())
        }
    }

    fun onVisionBaseUrlChange(value: String) {
        _uiState.update { it.copy(visionBaseUrl = value) }
        saveVisionSettings()
    }

    fun onVisionApiKeyChange(value: String) {
        _uiState.update { it.copy(visionApiKey = value) }
        saveVisionSettings()
    }

    fun onVisionModelNameChange(value: String) {
        _uiState.update { it.copy(visionModelName = value) }
        saveVisionSettings()
    }

    fun onSummaryBaseUrlChange(value: String) {
        _uiState.update { it.copy(summaryBaseUrl = value) }
        saveSummarySettings()
    }

    fun onSummaryApiKeyChange(value: String) {
        _uiState.update { it.copy(summaryApiKey = value) }
        saveSummarySettings()
    }

    fun onSummaryModelNameChange(value: String) {
        _uiState.update { it.copy(summaryModelName = value) }
        saveSummarySettings()
    }

    fun onAutoSummaryThresholdChange(value: String) {
        val threshold = value.toIntOrNull() ?: return
        _uiState.update { it.copy(autoSummaryThreshold = threshold.coerceIn(4, 500)) }
        viewModelScope.launch { repository.saveAutoSummaryThreshold(threshold) }
    }

    fun onSupportsImageChange(value: Boolean) {
        _uiState.update { it.copy(supportsImage = value) }
        viewModelScope.launch { repository.saveSupportsImage(value) }
    }

    fun onSupportsFileChange(value: Boolean) {
        _uiState.update { it.copy(supportsFile = value) }
        viewModelScope.launch { repository.saveSupportsFile(value) }
    }

    private fun saveVisionSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveVisionSettings(state.visionBaseUrl, state.visionApiKey, state.visionModelName)
        }
    }

    private fun saveSummarySettings() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSummarySettings(state.summaryBaseUrl, state.summaryApiKey, state.summaryModelName)
        }
    }

    fun loadModels() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(modelMessage = "正在获取模型列表...") }
            modelService.fetchModels(state.apiBaseUrl, state.apiKey)
                .onSuccess { models ->
                    _uiState.update {
                        it.copy(
                            availableModels = models,
                            modelMessage = if (models.isEmpty()) "未获取到模型" else "已获取 ${models.size} 个模型"
                        )
                    }
                    if (models.isNotEmpty() && state.selectedModel.isBlank()) {
                        onSelectedModelChange(models.first())
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(modelMessage = "获取模型失败: ${throwable.message.orEmpty()}")
                    }
                }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(connectionMessage = "正在测试连接...") }
            modelService.fetchModels(state.apiBaseUrl, state.apiKey)
                .onSuccess { models ->
                    _uiState.update { current ->
                        current.copy(connectionMessage = if (models.isEmpty()) "连接成功，但未返回模型" else "连接成功，返回 ${models.size} 个模型")
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(connectionMessage = "连接失败: ${throwable.message.orEmpty()}")
                    }
                }
        }
    }

    fun testProvider(provider: SavedModelEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            modelService.fetchModels(provider.baseUrl, provider.apiKey)
                .onSuccess { models ->
                    val latency = System.currentTimeMillis() - startedAt
                    onResult(if (models.isEmpty()) "连接成功，延迟 ${latency}ms，但未返回模型" else "连接成功，延迟 ${latency}ms，返回 ${models.size} 个模型")
                }
                .onFailure { throwable ->
                    val latency = System.currentTimeMillis() - startedAt
                    onResult("连接失败，延迟 ${latency}ms，原因: ${throwable.message.orEmpty()}")
                }
        }
    }

    fun fetchProviderModels(provider: SavedModelEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            modelService.fetchModels(provider.baseUrl, provider.apiKey)
                .onSuccess { models ->
                    val latency = System.currentTimeMillis() - startedAt
                    modelRepository.save(provider.copy(modelCount = models.size, lastRefreshAt = System.currentTimeMillis()))
                    if (provider.isActive) {
                        providerModelsCache[provider.id] = models
                        _uiState.update {
                            it.copy(
                                activeProviderModelCount = models.size,
                                activeProviderModelPreview = listOfNotNull(it.selectedModel.takeIf(String::isNotBlank)),
                                activeProviderAvailableModels = models,
                                providerModels = providerModelsCache.toMap(),
                                modelMessage = if (models.isEmpty()) "未获取到模型" else "已获取 ${models.size} 个模型"
                            )
                        }
                    } else {
                        providerModelsCache[provider.id] = models
                        _uiState.update {
                            it.copy(
                                providerModels = providerModelsCache.toMap(),
                                modelMessage = if (models.isEmpty()) "未获取到模型" else "已获取 ${models.size} 个模型"
                            )
                        }
                    }
                    onResult(if (models.isEmpty()) "拉取完成，耗时 ${latency}ms，但未返回模型" else "拉取完成，耗时 ${latency}ms，返回 ${models.size} 个模型")
                }
                .onFailure { throwable ->
                    val latency = System.currentTimeMillis() - startedAt
                    onResult("拉取失败，耗时 ${latency}ms，原因: ${throwable.message.orEmpty()}")
                }
        }
    }

    fun selectProviderModel(modelName: String) {
        if (modelName.isBlank()) return
        _uiState.update {
            it.copy(
                selectedModel = modelName,
                activeProviderModelPreview = listOf(modelName)
            )
        }
        viewModelScope.launch { repository.saveSelectedModel(modelName) }
    }

    fun queryProviderAccount(provider: SavedModelEntity, onResult: (Result<ProviderAccountInfo>) -> Unit) {
        viewModelScope.launch {
            onResult(providerAccountService.queryAccount(provider))
        }
    }

    suspend fun currentApiSettings(): ApiSettings = repository.getApiSettings()

    fun currentLanguageOption(): AppLanguage = AppLanguage.fromCode(_uiState.value.language)
}
