package com.hana.app.viewmodel

import android.graphics.Bitmap
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hana.app.data.api.ApiService
import com.hana.app.data.api.AttachmentService
import com.hana.app.data.api.models.StreamDelta
import com.hana.app.data.api.models.IncompleteStreamException
import com.hana.app.data.api.models.OutputTruncatedException
import com.hana.app.data.api.models.UpstreamContentBlockedException
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.ConversationEntity
import com.hana.app.data.db.entity.isMainChatConversation
import com.hana.app.data.db.entity.isGroupConversation
import com.hana.app.data.db.entity.SavedModelEntity
import com.hana.app.data.db.entity.ModelInfo
import com.hana.app.data.db.entity.MemoryScope
import com.hana.app.data.db.entity.Capability
import com.hana.app.data.db.entity.ModelCapabilityMap
import com.hana.app.data.repository.CharacterRepository
import com.hana.app.data.repository.ConversationRepository
import com.hana.app.data.repository.MessageRepository
import com.hana.app.data.repository.MemoryRepository
import com.hana.app.data.repository.ModelCacheRepository
import com.hana.app.data.repository.ModelRepository
import com.hana.app.data.repository.parseTags
import com.hana.app.data.remote.ModelService
import com.hana.app.data.settings.DEFAULT_QUICK_PHRASES
import com.hana.app.data.settings.CharacterStoryState
import com.hana.app.data.settings.CharacterStoryLogEntry
import com.hana.app.data.settings.InterCharacterRelationState
import com.hana.app.data.settings.interCharacterRelationKey
import com.hana.app.data.settings.SettingsRepository
import com.hana.app.manager.BackgroundManager
import com.hana.app.manager.BackgroundTarget
import com.hana.app.manager.SavedBackgroundInfo
import android.util.Log
import com.hana.app.ui.chat.AttachmentKind
import com.hana.app.ui.chat.ChatAttachment
import com.hana.app.ui.chat.decodeChatContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val DEFAULT_CONVERSATION_TITLE = "新对话"
private const val CHARACTER_STORY_REBUILD_VERSION = 4

@androidx.compose.runtime.Stable
data class StreamingAssistantState(
    val conversationId: String,
    val speakerCharacterId: String? = null,
    val speakerName: String? = null,
    val content: String = "",
    val thinkingContent: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val firstContentAt: Long? = null,
    val firstResponseAt: Long? = null
)

@androidx.compose.runtime.Stable
data class PromptPreviewState(
    val messages: List<PromptPreviewMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

private data class GroupReplyCandidate(
    val character: CharacterCardEntity,
    val score: Int
)

@androidx.compose.runtime.Stable
data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val characters: List<CharacterCardEntity> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val streamingAssistant: StreamingAssistantState? = null,
    val backgroundBitmap: Bitmap? = null,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = "",
    val timeoutSeconds: Int = 60,
    val quickPhrases: List<String> = DEFAULT_QUICK_PHRASES,
    val supportsImage: Boolean = false,
    val supportsFile: Boolean = false,
    val hasVisionConfig: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val savedModels: List<SavedModelEntity> = emptyList(),
    val favorites: List<ChatMessageEntity> = emptyList(),
    val speechAvailable: Boolean = true,
    val modelSupportsVision: Boolean = false,
    val modelSupportsTools: Boolean = false,
    val modelList: List<ModelInfo> = emptyList(),
    val modelFavorites: List<ModelInfo> = emptyList(),
    val personaEnabled: Boolean = false,
    val personaPrompt: String = "",
    val creativePresetText: String = "",
    val characterCreativePresetEnabled: Map<String, Boolean> = emptyMap(),
    val characterCreativePresetAffectsPersona: Map<String, Boolean> = emptyMap(),
    val characterCreativePresetTexts: Map<String, String> = emptyMap(),
    val characterIndependentCreativePresetEnabled: Map<String, Boolean> = emptyMap(),
    val characterIndependentCreativePresetAffectsPersona: Map<String, Boolean> = emptyMap(),
    val streamEnabled: Boolean = true,
    val backgroundIntensity: String = "soft",
    val characterStoryStates: Map<String, com.hana.app.data.settings.CharacterStoryState> = emptyMap(),
    val characterStoryLogs: Map<String, List<com.hana.app.data.settings.CharacterStoryLogEntry>> = emptyMap(),
    val interCharacterRelations: Map<String, InterCharacterRelationState> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val characterRepository: CharacterRepository,
    private val apiService: ApiService,
    private val settingsRepository: SettingsRepository,
    private val backgroundManager: BackgroundManager,
    private val modelRepository: ModelRepository,
    private val modelCacheRepository: ModelCacheRepository,
    private val modelService: ModelService,
    private val attachmentService: AttachmentService,
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    private val selectedConversationId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _promptPreviewState = MutableStateFlow(PromptPreviewState())
    val promptPreviewState: StateFlow<PromptPreviewState> = _promptPreviewState.asStateFlow()
    private val _scrollTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val scrollTrigger: SharedFlow<Unit> = _scrollTrigger.asSharedFlow()
    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorFlow: SharedFlow<String> = _error.asSharedFlow()
    private val _generationCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val generationCompleted: SharedFlow<Unit> = _generationCompleted.asSharedFlow()
    private var activeReplyJob: Job? = null
    private val replyInFlight = AtomicBoolean(false)
    private val activeSearchCall = AtomicReference<okhttp3.Call?>(null)
    private var speechAvailableChecked = false
    // 复用 OkHttpClient 避免每次搜索都新建线程池
    private val searchHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val messageBuilder = ChatMessageBuilder(
        conversationRepository, messageRepository, characterRepository,
        settingsRepository, memoryRepository, attachmentService
    )

    init {
        viewModelScope.launch {
            try { characterRepository.ensurePresetCharacters() }
            catch (e: Exception) { Log.e("ChatVM", "ensurePreset failed", e) }
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                try {
                    val modelSupportsVision = detectModelCapability(settings.selectedModel, multimodalModelKeywords())
                    val modelSupportsTools = detectModelCapability(settings.selectedModel, toolCapableModelKeywords())
                    _uiState.update {
                        it.copy(
                            selectedModel = settings.selectedModel,
                            timeoutSeconds = settings.timeoutSeconds,
                            quickPhrases = settings.quickPhrases,
                            supportsImage = settings.supportsImage || modelSupportsVision,
                            supportsFile = settings.supportsFile || modelSupportsVision,
                            hasVisionConfig = settings.visionBaseUrl.isNotBlank() &&
                                settings.visionApiKey.isNotBlank() &&
                                settings.visionModelName.isNotBlank(),
                            modelSupportsVision = modelSupportsVision,
                            modelSupportsTools = modelSupportsTools,
                            personaEnabled = settings.personaEnabled,
                            personaPrompt = settings.personaPrompt,
                            creativePresetText = settings.creativePresetText,
                            characterCreativePresetEnabled = settings.characterCreativePresetEnabled,
                            characterCreativePresetAffectsPersona = settings.characterCreativePresetAffectsPersona,
                            characterCreativePresetTexts = settings.characterCreativePresetTexts,
                            characterIndependentCreativePresetEnabled = settings.characterIndependentCreativePresetEnabled,
                            characterIndependentCreativePresetAffectsPersona = settings.characterIndependentCreativePresetAffectsPersona,
                            streamEnabled = settings.streamEnabled,
                            webSearchEnabled = settings.webSearchEnabled,
                            backgroundIntensity = settings.backgroundIntensity,
                            characterStoryStates = settings.characterStoryStates,
                            characterStoryLogs = settings.characterStoryLogs,
                            interCharacterRelations = settings.interCharacterRelations
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ChatVM", "settingsFlow collect error", e)
                }
            }
        }
        viewModelScope.launch {
            try {
                characterRepository.observeCharacters().collect { characters ->
                    _uiState.update { it.copy(characters = characters) }
                }
            } catch (e: Exception) { Log.e("ChatVM", "character observe error", e) }
        }
        viewModelScope.launch {
            try {
                modelRepository.observeModels().collect { models ->
                    _uiState.update { it.copy(savedModels = models) }
                    val activeProvider = models.firstOrNull { it.isActive && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
                    if (activeProvider != null) {
                        ensureUsableConnectionSettings()
                    }
                }
            } catch (e: Exception) { Log.e("ChatVM", "model observe error", e) }
        }
        viewModelScope.launch {
            try {
                messageRepository.observeFavorites().collect { favs ->
                    _uiState.update { it.copy(favorites = favs) }
                }
            } catch (e: Exception) { Log.e("ChatVM", "favorites observe error", e) }
        }
        viewModelScope.launch {
            try {
                modelCacheRepository.observeAll().collect { models ->
                    _uiState.update { it.copy(modelList = models) }
                }
            } catch (e: Exception) { Log.e("ChatVM", "model list observe error", e) }
        }
        viewModelScope.launch {
            try {
                modelCacheRepository.observeFavorites().collect { favModels ->
                    _uiState.update { it.copy(modelFavorites = favModels) }
                }
            } catch (e: Exception) { Log.e("ChatVM", "model favs observe error", e) }
        }
        viewModelScope.launch {
            try {
                conversationRepository.observeConversations().collect { conversations ->
                    _uiState.update { it.copy(conversations = conversations) }
                    val currentId = selectedConversationId.value
                    when {
                        currentId == null && conversations.isNotEmpty() -> {
                            val mainConv = conversations.firstOrNull { it.isMainChatConversation() }
                            if (mainConv != null) {
                                selectConversation(mainConv.id)
                            } else {
                                val created = conversationRepository.createNormalConversation()
                                selectConversation(created.id)
                            }
                        }
                        currentId != null && conversations.none { it.id == currentId } -> {
                            val mainConv = conversations.firstOrNull { it.isMainChatConversation() }
                            if (mainConv != null) {
                                selectConversation(mainConv.id)
                            } else {
                                val created = conversationRepository.createNormalConversation()
                                selectConversation(created.id)
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("ChatVM", "conversation observe error", e) }
        }
        viewModelScope.launch {
            try {
                selectedConversationId
                    .flatMapLatest { conversationId ->
                        if (conversationId == null) flowOf(emptyList())
                        else messageRepository.observeMessages(conversationId)
                    }
                    .collect { messages ->
                        _uiState.update { it.copy(messages = messages) }
                    }
            } catch (e: Exception) { Log.e("ChatVM", "message observe error", e) }
        }
        refreshBackground()
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun persistImageAttachment(uri: Uri, onReady: (ChatAttachment?) -> Unit) {
        viewModelScope.launch {
            onReady(attachmentService.persistPickedUri(uri, "image_${System.currentTimeMillis()}", AttachmentKind.IMAGE))
        }
    }

    fun persistFileAttachment(uri: Uri, onReady: (ChatAttachment?) -> Unit) {
        viewModelScope.launch {
            onReady(attachmentService.persistPickedUri(uri, "file_${System.currentTimeMillis()}", AttachmentKind.FILE))
        }
    }

    fun persistCameraAttachment(bitmap: Bitmap, onReady: (ChatAttachment?) -> Unit) {
        viewModelScope.launch {
            onReady(attachmentService.persistCameraBitmap(bitmap))
        }
    }

    fun saveAttachmentImage(uriString: String, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            attachmentService.saveImageToGallery(uriString)
                .onSuccess { onResult(it) }
                .onFailure { onResult("保存失败: ${it.message.orEmpty()}") }
        }
    }

    fun toggleWebSearch() {
        val enabled = !_uiState.value.webSearchEnabled
        _uiState.update { it.copy(webSearchEnabled = enabled) }
        viewModelScope.launch { settingsRepository.saveWebSearchEnabled(enabled) }
    }

    fun togglePersona() {
        val enabled = !_uiState.value.personaEnabled
        _uiState.update { it.copy(personaEnabled = enabled) }
        viewModelScope.launch { settingsRepository.savePersonaSettings(enabled, _uiState.value.personaPrompt) }
    }

    fun updatePersonaPrompt(prompt: String) {
        _uiState.update { it.copy(personaPrompt = prompt) }
        viewModelScope.launch { settingsRepository.savePersonaSettings(_uiState.value.personaEnabled, prompt) }
    }

    fun selectConversation(conversationId: String) {
        selectedConversationId.value = conversationId
        _uiState.update { it.copy(currentConversationId = conversationId) }
        refreshBackground()
    }

    fun updateCreativePresetSnapshot(characterId: String, text: String? = null, enabled: Boolean? = null, affectsPersona: Boolean? = null) {
        _uiState.update { state ->
            state.copy(
                creativePresetText = text ?: state.creativePresetText,
                characterCreativePresetEnabled = state.characterCreativePresetEnabled.toMutableMap().apply {
                    enabled?.let { put(characterId, it) }
                },
                characterCreativePresetAffectsPersona = state.characterCreativePresetAffectsPersona.toMutableMap().apply {
                    affectsPersona?.let { put(characterId, it) }
                }
            )
        }
    }

    fun updateIndependentCreativePresetSnapshot(characterId: String, text: String? = null, enabled: Boolean? = null, affectsPersona: Boolean? = null) {
        _uiState.update { state ->
            state.copy(
                characterCreativePresetTexts = state.characterCreativePresetTexts.toMutableMap().apply {
                    text?.let { put(characterId, it) }
                },
                characterIndependentCreativePresetEnabled = state.characterIndependentCreativePresetEnabled.toMutableMap().apply {
                    enabled?.let { put(characterId, it) }
                },
                characterIndependentCreativePresetAffectsPersona = state.characterIndependentCreativePresetAffectsPersona.toMutableMap().apply {
                    affectsPersona?.let { put(characterId, it) }
                }
            )
        }
    }

    fun refreshPromptPreview(conversationId: String? = _uiState.value.currentConversationId, characterId: String? = null) {
        if (conversationId.isNullOrBlank()) {
            _promptPreviewState.value = PromptPreviewState(error = "当前没有可预览的对话")
            return
        }
        viewModelScope.launch {
            _promptPreviewState.value = _promptPreviewState.value.copy(isLoading = true, error = null)
            runCatching {
                val conversation = conversationRepository.getById(conversationId)
                    ?: error("对话不存在")
                val settingsModel = settingsRepository.getSettings().selectedModel.takeIf { it.isNotBlank() }
                val participants = if (ChatMessageBuilder.isGroupConversation(conversation)) {
                    getConversationParticipants(conversation)
                } else {
                    emptyList()
                }
                val lastUserText = messageRepository.getMessages(conversationId)
                    .lastOrNull { it.role == "user" }
                    ?.let { decodeChatContent(it.content).text }
                    .orEmpty()
                val character = when {
                    characterId != null -> characterRepository.getById(characterId)
                    conversation.characterId != null -> characterRepository.getById(conversation.characterId)
                    participants.isNotEmpty() -> chooseGroupReplyCharacters(conversation, participants, lastUserText)
                        .firstOrNull()?.character
                    else -> null
                }
                val effectiveModel = character?.modelId?.takeIf { it.isNotBlank() }
                    ?: conversation.modelName?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.selectedModel.takeIf { it.isNotBlank() }
                    ?: settingsModel
                val overridePrompt = if (participants.isNotEmpty() && character != null) {
                    messageBuilder.buildGroupCharacterPrompt(conversation, character, participants, ::getCharacterStoryState)
                } else {
                    null
                }
                val searchResultText = if (participants.isEmpty() && _uiState.value.webSearchEnabled) {
                    val searchSettings = settingsRepository.getSettings()
                    if (searchSettings.searchIndependentMode && searchSettings.searchProviderUrl.isNotBlank()) {
                        performWebSearch(lastUserText, searchSettings.searchProviderUrl, searchSettings.searchProviderKey)
                    } else null
                } else null
                messageBuilder.buildApiMessages(
                    conversationId = conversationId,
                    userText = lastUserText,
                    effectiveModel = effectiveModel,
                    overrideSystemPrompt = overridePrompt,
                    webSearchEnabled = _uiState.value.webSearchEnabled,
                    personaEnabled = _uiState.value.personaEnabled,
                    personaPrompt = _uiState.value.personaPrompt,
                    modelSupportsVision = _uiState.value.modelSupportsVision,
                    hasVisionConfig = _uiState.value.hasVisionConfig,
                    selectedModel = _uiState.value.selectedModel,
                    characterStoryStates = _uiState.value.characterStoryStates,
                    searchResultText = searchResultText,
                    creativePresetCharacterId = character?.id,
                    creativePresetTextOverride = _uiState.value.creativePresetText,
                    creativePresetEnabledOverride = character?.id?.let {
                        _uiState.value.characterCreativePresetEnabled[it]
                    },
                    creativePresetAffectsPersonaOverride = character?.id?.let {
                        _uiState.value.characterCreativePresetAffectsPersona[it]
                    },
                    characterCreativePresetTextOverride = character?.id?.let {
                        _uiState.value.characterCreativePresetTexts[it]
                    },
                    characterCreativePresetEnabledOverride = character?.id?.let {
                        _uiState.value.characterIndependentCreativePresetEnabled[it]
                    },
                    characterCreativePresetAffectsPersonaOverride = character?.id?.let {
                        _uiState.value.characterIndependentCreativePresetAffectsPersona[it]
                    },
                    groupViewerCharacterId = character?.id?.takeIf { participants.isNotEmpty() },
                    maxOutputTokens = conversation.maxTokens
                )
            }.onSuccess(::publishPromptPreview)
                .onFailure { error ->
                    _promptPreviewState.value = PromptPreviewState(error = error.message ?: "预览生成失败")
                }
        }
    }

    private fun publishPromptPreview(messages: List<ApiService.ChatPayload>) {
        _promptPreviewState.value = PromptPreviewState(messages = messageBuilder.buildPromptPreview(messages))
    }

    fun returnToMainConversation() {
        viewModelScope.launch {
            val conversations = _uiState.value.conversations
            val mainConversationId = conversations
                .firstOrNull { it.isMainChatConversation() }
                ?.id
                ?: conversationRepository.createNormalConversation().id
            selectConversation(mainConversationId)
        }
    }

    fun createNormalConversation(selectAfterCreate: Boolean = true) {
        viewModelScope.launch {
            val conversation = conversationRepository.createNormalConversation()
            if (selectAfterCreate) {
                selectConversation(conversation.id)
            }
        }
    }

    fun createStoryConversation(
        title: String,
        premise: String,
        style: String,
        length: String,
        modelName: String,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val safeTitle = title.ifBlank { "故事模拟" }
            val conversation = conversationRepository.createNormalConversation(title = "故事：$safeTitle")
            val systemPrompt = buildString {
                append("你是一个擅长长篇互动叙事的 AI 故事主持者。")
                append("\n请以 ${style} 的文风来推进故事。")
                append("\n故事节奏要求：${length}。")
                append("\n保持小说感、对白感和场景推进，不要只写成说明书。")
                append("\n以下是本次故事的设定与开场要求：\n")
                append(premise)
            }
            conversationRepository.updateSystemPrompt(conversation, systemPrompt)
            if (modelName.isNotBlank()) {
                conversationRepository.updateParameters(
                    conversation = conversation,
                    modelName = modelName,
                    temperature = conversation.temperature,
                    topP = conversation.topP,
                    maxTokens = conversation.maxTokens,
                    contextLimit = conversation.contextLimit
                )
            }
            selectConversation(conversation.id)
            _uiState.update { it.copy(input = "以故事开场开始" ) }
            onCreated(conversation.id)
        }
    }

    fun createCharacterConversation(character: CharacterCardEntity, onReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val existingConversation = conversationRepository.getByCharacterId(character.id)
                if (existingConversation != null) {
                    rebuildCharacterStoryStateIfNeeded(existingConversation)
                    selectConversation(existingConversation.id)
                    onReady(existingConversation.id)
                } else {
                    val conversation = conversationRepository.createCharacterConversation(character)
                    val cleanGreeting = character.greeting.trim()
                    if (cleanGreeting.isNotBlank()) {
                        messageRepository.insert(
                            ChatMessageEntity(
                                conversationId = conversation.id, role = "assistant",
                                speakerCharacterId = character.id,
                                speakerName = character.name,
                                content = cleanGreeting, thinkingContent = null,
                                thinkingDuration = null, timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    characterRepository.updateLastMessage(character.id, cleanGreeting)
                    selectConversation(conversation.id)
                    onReady(conversation.id)
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "createCharacterConversation failed", e)
                _error.emit("进入角色聊天失败: ${e.message?.take(80)}")
            }
        }
    }

    fun createGroupConversation(characters: List<CharacterCardEntity>, onReady: (String) -> Unit = {}) {
        val uniqueCharacters = characters.distinctBy { it.id }
        if (uniqueCharacters.size < 2) return
        viewModelScope.launch {
            try {
                val conversation = conversationRepository.createGroupConversation(uniqueCharacters)
                selectConversation(conversation.id)
                onReady(conversation.id)
            } catch (e: Exception) {
                Log.e("ChatVM", "createGroupConversation failed", e)
                _error.emit("创建群聊失败: ${e.message?.take(80)}")
            }
        }
    }

    fun renameConversation(conversationId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val conversation = conversationRepository.getById(conversationId) ?: return@launch
            conversationRepository.rename(conversation, title.trim(), isNamed = true)
        }
    }

    fun toggleConversationPinned(conversationId: String) {
        viewModelScope.launch { conversationRepository.togglePinned(conversationId) }
    }

    fun toggleConversationFavorite(conversationId: String) {
        viewModelScope.launch { conversationRepository.toggleFavorite(conversationId) }
    }

    fun exportCharacterConversation(context: android.content.Context, characterId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val character = characterRepository.getById(characterId) ?: error("角色不存在")
                val conversation = conversationRepository.getByCharacterId(characterId) ?: error("对话不存在")
                val messages = messageRepository.getMessages(conversation.id)
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "${character.name}_$time.md".replace(Regex("""[\\/:*?"<>|]"""), "_")
                val body = buildString {
                appendLine("# ${character.name}")
                appendLine()
                appendLine("> ${character.description.take(200)}")
                appendLine()
                messages.forEach { msg ->
                    val speaker = if (msg.role == "user") "你" else (msg.speakerName?.takeIf { it.isNotBlank() } ?: character.name)
                    appendLine("**$speaker**")
                    appendLine(msg.content)
                    appendLine()
                }
                }
                writeDownloadExport(context, fileName, "text/markdown", body.toByteArray(Charsets.UTF_8))
            }.onSuccess { onResult(true, it) }
             .onFailure { onResult(false, it.message.orEmpty()) }
        }
    }

    fun importCharacterCard(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: error("无法读取所选文件")
                val imported = characterRepository.importCharacterCard(bytes)
                var character = imported.character
                imported.embeddedAvatarBytes?.let { avatarBytes ->
                    val avatarDir = java.io.File(context.filesDir, "characters")
                    require(avatarDir.exists() || avatarDir.mkdirs()) { "无法创建角色头像目录" }
                    val avatarFile = java.io.File(avatarDir, "${character.id}.png")
                    avatarFile.writeBytes(avatarBytes)
                    character = character.copy(avatarUrl = android.net.Uri.fromFile(avatarFile).toString())
                    require(characterRepository.save(character)) { "角色卡已解析，但头像保存失败" }
                }
                character.name
            }.onSuccess { onResult(true, "已导入角色卡：$it") }
                .onFailure { onResult(false, it.message.orEmpty()) }
        }
    }

    fun exportCharacterCardJson(context: Context, characterId: String, uri: Uri, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val character = characterRepository.getById(characterId) ?: error("角色不存在")
                val payload = org.json.JSONObject().apply {
                    put("id", character.id)
                    put("name", character.name)
                    put("avatarUrl", character.avatarUrl)
                    put("description", character.description)
                    put("greeting", character.greeting)
                    put("userPersona", character.userPersona)
                    put("tags", character.tags)
                    put("modelId", character.modelId)
                    put("temperature", character.temperature)
                    put("characterMode", character.characterMode)
                    put("subCharacters", org.json.JSONObject(character.subCharactersJson).optJSONArray("profiles") ?: org.json.JSONArray())
                    put("createdAt", character.createdAt)
                    put("updatedAt", character.updatedAt)
                }.toString(2)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选位置")
                character.name
            }.onSuccess { onResult(true, "$it.json") }
                .onFailure { onResult(false, it.message.orEmpty()) }
        }
    }

    fun exportCharacterCardPng(context: Context, characterId: String, uri: Uri, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val character = characterRepository.getById(characterId) ?: error("角色不存在")
                val cardJson = CharacterCardExporter.buildCharacterCardPayload(character).toString(2)
                val coverBitmap = character.avatarUrl.takeIf { it.isNotBlank() }
                    ?.let { attachmentService.loadImageBitmap(it) }
                    ?: CharacterCardExporter.buildDefaultCharacterCover(character)
                val pngBytes = CharacterCardExporter.buildCharacterCardPngBytes(coverBitmap, cardJson)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pngBytes)
                } ?: error("无法写入所选位置")
                character.name
            }.onSuccess { onResult(true, "$it.png") }
                .onFailure { onResult(false, it.message.orEmpty()) }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            val messages = messageRepository.getMessages(conversationId)
            attachmentService.deleteAttachmentsForMessages(messages)
            messageRepository.deleteByConversation(conversationId)
            conversationRepository.delete(conversationId)
            if (_uiState.value.currentConversationId == conversationId) {
                selectedConversationId.value = null
                _uiState.update { it.copy(currentConversationId = null, messages = emptyList()) }
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            val allConversations = conversationRepository.getAll()
            allConversations.forEach { conv ->
                val messages = messageRepository.getMessages(conv.id)
                attachmentService.deleteAttachmentsForMessages(messages)
            }
            conversationRepository.deleteAll()
            selectedConversationId.value = null
            _uiState.update { it.copy(currentConversationId = null, messages = emptyList()) }
        }
    }

    fun clearAllMessagesOnly() {
        viewModelScope.launch {
            val conversations = conversationRepository.getAll()
            conversations.forEach { conversation ->
                attachmentService.deleteAttachmentsForMessages(messageRepository.getMessages(conversation.id))
            }
            messageRepository.deleteAll()
            conversations.forEach { conversation ->
                conversationRepository.updateLastMessage(conversation, null)
                if (conversation.characterId != null) {
                    characterRepository.updateLastMessage(conversation.characterId, "")
                }
            }
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    fun clearModelCache() {
        viewModelScope.launch {
            modelCacheRepository.clearAll()
            _uiState.update { it.copy(modelList = emptyList(), modelFavorites = emptyList()) }
        }
    }

    fun clearBackgroundCache() {
        backgroundManager.clearAllBackgrounds()
        refreshBackground()
    }

    fun performSelectiveCleanup(
        clearMessages: Boolean,
        clearModelCache: Boolean,
        clearBackground: Boolean,
        clearAllConversations: Boolean
    ) {
        viewModelScope.launch {
            if (clearAllConversations) {
                conversationRepository.getAll().forEach { conversation ->
                    attachmentService.deleteAttachmentsForMessages(messageRepository.getMessages(conversation.id))
                }
                conversationRepository.deleteAll()
                selectedConversationId.value = null
                _uiState.update { it.copy(currentConversationId = null, messages = emptyList()) }
                return@launch
            }
            if (clearMessages) {
                clearAllMessagesOnly()
            }
            if (clearModelCache) {
                clearModelCache()
            }
            if (clearBackground) {
                clearBackgroundCache()
            }
        }
    }

    fun exportAllData(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val payload = buildExportPayload()
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "hana_backup_$time.json"
                writeDownloadExport(context, fileName, "application/json", payload.toByteArray(Charsets.UTF_8))
            }.onSuccess { onResult(true, it) }
             .onFailure { onResult(false, it.message.orEmpty()) }
        }
    }

    fun exportAllDataToUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val payload = buildExportPayload()
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    ?: error("无法写入选定位置")
                "备份已导出"
            }.onSuccess { onResult(true, it) }
             .onFailure { onResult(false, it.message.orEmpty()) }
        }
    }

    private suspend fun buildExportPayload(): String {
        val conversations = conversationRepository.getAll()
        val characters = _uiState.value.characters
        val memories = memoryRepository.getMainMemory()
        val providers = modelRepository.getAll()
        val settings = settingsRepository.getSettings()
        return org.json.JSONObject().apply {
            put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("conversationCount", conversations.size)
            put("characterCount", characters.size)
            put("memoryCount", memories.size)
            put(
                "conversations",
                org.json.JSONArray().also { convArray ->
                    conversations.forEach { conversation ->
                        val messages = messageRepository.getMessages(conversation.id)
                        convArray.put(
                            org.json.JSONObject().apply {
                                put("id", conversation.id)
                                put("title", conversation.title)
                                put("characterId", conversation.characterId)
                                put("conversationType", conversation.conversationType)
                                put("participantCharacterIds", conversation.participantCharacterIds)
                                put("groupScene", conversation.groupScene)
                                put("groupSceneLocked", conversation.groupSceneLocked)
                                put(
                                    "messages",
                                    org.json.JSONArray().also { msgArray ->
                                        messages.forEach { msg ->
                                            msgArray.put(
                                                        org.json.JSONObject().apply {
                                                            put("role", msg.role)
                                                            put("content", msg.content)
                                                            put("speakerCharacterId", msg.speakerCharacterId)
                                                            put("speakerName", msg.speakerName)
                                                            put("roundId", msg.roundId)
                                                            put("turnIndex", msg.turnIndex)
                                                            put("replyToMessageId", msg.replyToMessageId)
                                                            put("replyToSpeakerCharacterId", msg.replyToSpeakerCharacterId)
                                                            put("replyToSpeakerName", msg.replyToSpeakerName)
                                                            put("replyToContent", msg.replyToContent)
                                                        }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
            put("characters", org.json.JSONArray().also { array ->
                characters.forEach { character ->
                    array.put(org.json.JSONObject().apply {
                        put("id", character.id)
                        put("name", character.name)
                        put("avatarUrl", character.avatarUrl)
                        put("description", character.description)
                        put("greeting", character.greeting)
                        put("userPersona", character.userPersona)
                        put("tags", character.tags)
                        put("modelId", character.modelId)
                        put("temperature", character.temperature)
                        put("characterMode", character.characterMode)
                        put("subCharacters", org.json.JSONObject(character.subCharactersJson).optJSONArray("profiles") ?: org.json.JSONArray())
                    })
                }
            })
            put("memory", org.json.JSONArray().also { array ->
                memories.forEach { memory ->
                    array.put(org.json.JSONObject().apply {
                        put("id", memory.id)
                        put("scope", memory.scope)
                        put("type", memory.type)
                        put("content", memory.content)
                        put("sourceConversationId", memory.sourceConversationId)
                        put("createdAt", memory.createdAt)
                        put("updatedAt", memory.updatedAt)
                        put("confidence", memory.confidence)
                        put("isPinned", memory.isPinned)
                        put("isArchived", memory.isArchived)
                    })
                }
            })
            put("providers", org.json.JSONArray().also { array ->
                providers.forEach { provider ->
                    array.put(org.json.JSONObject().apply {
                        put("id", provider.id)
                        put("name", provider.name)
                        put("baseUrl", provider.baseUrl)
                        put("modelCount", provider.modelCount)
                        put("isActive", provider.isActive)
                    })
                }
            })
            put("settings", org.json.JSONObject().apply {
                put("language", settings.language)
                put("themeMode", settings.themeMode.value)
                put("themePalette", settings.themePalette.value)
                put("selectedModel", settings.selectedModel)
                put("imageProviderId", settings.imageProviderId)
                put("imageModelName", settings.imageModelName)
                put("personaEnabled", settings.personaEnabled)
                put("webSearchEnabled", settings.webSearchEnabled)
                put("streamEnabled", settings.streamEnabled)
                put("breakArmorPromptText", settings.creativePresetText)
                put("breakArmorEnabledByCharacter", org.json.JSONObject(settings.characterCreativePresetEnabled))
                put("breakArmorAffectsPersonaByCharacter", org.json.JSONObject(settings.characterCreativePresetAffectsPersona))
                put("creativePresetTextByCharacter", org.json.JSONObject(settings.characterCreativePresetTexts))
                put("creativePresetEnabledByCharacter", org.json.JSONObject(settings.characterIndependentCreativePresetEnabled))
                put("creativePresetAffectsPersonaByCharacter", org.json.JSONObject(settings.characterIndependentCreativePresetAffectsPersona))
            })
            put("interCharacterRelations", org.json.JSONObject().also { relationRoot ->
                settings.interCharacterRelations.forEach { (key, relation) ->
                    relationRoot.put(key, org.json.JSONObject().apply {
                        put("affinity", relation.affinity)
                        put("rivalry", relation.rivalry)
                        put("tension", relation.tension)
                        put("relationLabel", relation.relationLabel)
                        put("recentEvent", relation.recentEvent)
                        put("updatedAt", relation.updatedAt)
                    })
                }
            })
        }.toString(2)
    }

    private fun writeDownloadExport(context: Context, fileName: String, mimeType: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Hana")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            ) ?: error("无法创建导出文件")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入导出文件")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
                return uri.toString()
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }

        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: java.io.File(context.filesDir, "downloads")
        val directory = java.io.File(baseDir, "Hana").apply {
            if (!exists() && !mkdirs()) error("无法创建应用下载目录")
        }
        val file = java.io.File(directory, fileName)
        file.outputStream().use { it.write(bytes) }
        return file.absolutePath
    }

    fun storageSummary(): Triple<Int, Int, Long> {
        return Triple(
            _uiState.value.conversations.size,
            _uiState.value.characters.size,
            backgroundManager.backgroundFileSizeBytes()
        )
    }

    fun storageBreakdown(): StorageBreakdown {
        val conversationCount = _uiState.value.conversations.size
        val characterCount = _uiState.value.characters.size
        val backgroundBytes = backgroundManager.backgroundFileSizeBytes()
        val appBytes = backgroundManager.appFilesSizeBytes()
        return StorageBreakdown(
            conversationCount = conversationCount,
            characterCount = characterCount,
            backgroundBytes = backgroundBytes,
            appBytes = appBytes,
            estimatedMessageBytes = conversationCount * 24L * 1024L,
            estimatedModelCacheBytes = _uiState.value.modelList.size * 4L * 1024L
        )
    }

    data class StorageBreakdown(
        val conversationCount: Int,
        val characterCount: Int,
        val backgroundBytes: Long,
        val appBytes: Long,
        val estimatedMessageBytes: Long,
        val estimatedModelCacheBytes: Long
    )

    fun exportConversation(
        context: Context,
        conversationId: String,
        asMarkdown: Boolean = true,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            runCatching {
                val conversation = conversationRepository.getById(conversationId)
                    ?: error("Conversation not found")
                val messages = messageRepository.getMessages(conversationId)
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val extension = if (asMarkdown) "md" else "txt"
                val fileName = "${conversation.title.ifBlank { "conversation" }}_$time.$extension"
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                val body = buildString {
                    if (asMarkdown) {
                        append("# ${conversation.title}\n\n")
                    } else {
                        append("${conversation.title}\n\n")
                    }
                    messages.forEach { message ->
                val speaker = when (message.role) {
                    "user" -> "User"
                    "assistant" -> message.speakerName?.takeIf { it.isNotBlank() } ?: "Assistant"
                    else -> message.role
                }
                        if (asMarkdown) {
                            append("## $speaker\n\n${message.content}\n\n")
                        } else {
                            append("[$speaker]\n${message.content}\n\n")
                        }
                    }
                }
                writeDownloadExport(
                    context,
                    fileName,
                    if (asMarkdown) "text/markdown" else "text/plain",
                    body.toByteArray(Charsets.UTF_8)
                )
            }.onSuccess { fileName ->
                onResult(true, fileName)
            }.onFailure { throwable ->
                onResult(false, throwable.message.orEmpty())
            }
        }
    }

    fun deleteMessage(message: ChatMessageEntity) {
        if (message.id <= 0L) return
        viewModelScope.launch {
            attachmentService.deleteAttachmentsForMessages(listOf(message))
            messageRepository.delete(message.id)
            conversationRepository.clearHistorySummary(message.conversationId)
            rebuildCharacterStoryStateAfterHistoryChange(message.conversationId)
            refreshConversationLastMessage(message.conversationId)
        }
    }

    fun regenerateAssistantMessage(message: ChatMessageEntity) {
        if (message.id <= 0L || message.role != "assistant" || !tryStartReply()) return
        launchReplyJob reply@{
            val conversation = conversationRepository.getById(message.conversationId) ?: return@reply
            val history = messageRepository.getMessages(message.conversationId)
            val latestUserMessage = history
                .filter {
                    it.timestamp < message.timestamp ||
                        (it.timestamp == message.timestamp && it.id < message.id)
                }
                .lastOrNull { it.role == "user" }
                ?: return@reply

            val decoded = decodeChatContent(latestUserMessage.content)
            val replacementStoryState = conversation.characterId?.let { characterId ->
                rebuildCharacterStoryStateFromHistory(
                    messages = history.filter { it.id < message.id && !it.isError },
                    character = characterRepository.getById(characterId)
                )
            }
            requestAssistantReply(
                conversation = conversation,
                userText = decoded.text.ifBlank { decoded.attachments.firstOrNull()?.name ?: "附件消息" },
                historyUpToMessageId = message.id - 1L,
                replaceFromAssistant = message,
                replacementStoryState = replacementStoryState
            )
        }
    }

    fun editUserMessage(message: ChatMessageEntity, newContent: String) {
        val text = newContent.trim()
        if (message.id <= 0L || message.role != "user" || text.isBlank() || _uiState.value.isSending) return
        viewModelScope.launch {
            messageRepository.deleteFrom(message)
            conversationRepository.clearHistorySummary(message.conversationId)
            rebuildCharacterStoryStateAfterHistoryChange(message.conversationId)
            refreshConversationLastMessage(message.conversationId)
            selectConversation(message.conversationId)
            sendMessage(text)
        }
    }

    fun editMessageToInput(message: ChatMessageEntity) {
        if (message.role != "user" || _uiState.value.isSending) return
        val decoded = decodeChatContent(message.content)
        _uiState.update { it.copy(input = decoded.text) }
        viewModelScope.launch {
            messageRepository.deleteFrom(message)
            conversationRepository.clearHistorySummary(message.conversationId)
            rebuildCharacterStoryStateAfterHistoryChange(message.conversationId)
            refreshConversationLastMessage(message.conversationId)
            selectConversation(message.conversationId)
        }
    }

    fun saveCharacter(character: CharacterCardEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            if (characterRepository.save(character)) {
                withContext(Dispatchers.Main) { onDone() }
            } else {
                _error.emit("保存角色失败，请稍后重试")
            }
        }
    }

    fun deleteCharacter(character: CharacterCardEntity) {
        viewModelScope.launch {
            val conversation = conversationRepository.getByCharacterId(character.id)
            if (conversation != null) {
                val messages = messageRepository.getMessages(conversation.id)
                attachmentService.deleteAttachmentsForMessages(messages)
                messageRepository.deleteByConversation(conversation.id)
                conversationRepository.delete(conversation.id)
            }
            conversationRepository.removeCharacterFromGroups(character.id)
            characterRepository.delete(character)
            // 清除残留的好感度状态和日志
            settingsRepository.clearCharacterStoryState(character.id)
        }
    }

    fun clearCharacterChatHistory(characterId: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.getByCharacterId(characterId) ?: return@launch
            attachmentService.deleteAttachmentsForMessages(messageRepository.getMessages(conversation.id))
            messageRepository.deleteByConversation(conversation.id)
            conversationRepository.clearHistorySummary(conversation.id)
            val character = characterRepository.getById(characterId)
            if (character != null && character.greeting.isNotBlank()) {
                messageRepository.insert(
                    ChatMessageEntity(
                        conversationId = conversation.id, role = "assistant",
                        speakerCharacterId = character.id,
                        speakerName = character.name,
                        content = character.greeting, thinkingContent = null,
                        thinkingDuration = null, timestamp = System.currentTimeMillis()
                    )
                )
                conversationRepository.updateLastMessage(conversation, character.greeting)
                characterRepository.resetLastMessage(characterId, character.greeting)
            } else {
                conversationRepository.updateLastMessage(conversation, null)
                characterRepository.resetLastMessage(characterId, "")
            }
            conversationRepository.rename(conversation, "新对话", isNamed = false)
            // 重置好感度到初始状态，清除旧日志
            if (character != null) {
                settingsRepository.clearCharacterStoryState(characterId)
                val initialState = deriveInitialCharacterStoryState(character)
                settingsRepository.saveCharacterStoryState(characterId, initialState)
            }
        }
    }

    fun deleteLastRound(conversationId: String) {
        viewModelScope.launch {
            val messages = messageRepository.getMessages(conversationId)
            val lastAssistant = messages.lastOrNull { it.role == "assistant" }
            val lastUser = messages.lastOrNull { it.role == "user" }
            if (lastUser == null) return@launch

            val deletions = buildList {
                add(lastUser)
                if (lastAssistant != null && lastAssistant.timestamp >= lastUser.timestamp) {
                    add(lastAssistant)
                }
            }
            deletions.forEach { messageRepository.delete(it.id) }
            conversationRepository.clearHistorySummary(conversationId)
            rebuildCharacterStoryStateAfterHistoryChange(conversationId)
            refreshConversationLastMessage(conversationId)
        }
    }

    fun sendMessage(content: String = _uiState.value.input) {
        val text = content.trim()
        if (text.isBlank() || !tryStartReply(clearInput = true)) return
        val requestedConversationId = _uiState.value.currentConversationId

        launchReplyJob {
            sendMessageInternal(text, requestedConversationId)
        }
    }

    private suspend fun sendMessageInternal(text: String, requestedConversationId: String?) {
            val decoded = decodeChatContent(text)
            val visibleText = decoded.text.trim()
            val previewText = visibleText.ifBlank { decoded.attachments.firstOrNull()?.name ?: "附件消息" }

            val conversation = ensureConversationForSend(requestedConversationId)
            val conversationId = conversation.id
            val roundId = if (ChatMessageBuilder.isGroupConversation(conversation)) UUID.randomUUID().toString() else null
            selectedConversationId.value = conversationId
            _uiState.update { it.copy(currentConversationId = conversationId) }

            messageRepository.insert(
                ChatMessageEntity(
                    conversationId = conversationId, role = "user", speakerCharacterId = null, speakerName = null, content = text,
                    roundId = roundId, turnIndex = if (roundId != null) 0 else null,
                    thinkingContent = null, thinkingDuration = null, timestamp = System.currentTimeMillis()
                )
            )
            conversationRepository.updateLastMessage(conversation, previewText)

            _scrollTrigger.emit(Unit)

            requestAssistantReply(
                conversation = conversation,
                userText = previewText
            )
    }

    fun stopGeneration() {
        if (!_uiState.value.isSending) return
        val streaming = _uiState.value.streamingAssistant
        apiService.cancelActiveStream()
        activeSearchCall.getAndSet(null)?.cancel()
        activeReplyJob?.cancel()
        activeReplyJob = null
        replyInFlight.set(false)
        viewModelScope.launch {
            try {
                if (streaming != null && streaming.content.isNotBlank()) {
                    val stoppedConversation = conversationRepository.getById(streaming.conversationId) ?: return@launch
                    saveIncompleteAssistant(
                        conversation = stoppedConversation,
                        character = (streaming.speakerCharacterId ?: stoppedConversation.characterId)
                            ?.let { characterRepository.getById(it) },
                        content = streaming.content,
                        thinking = streaming.thinkingContent,
                        requestStartAt = streaming.startedAt,
                        tokenCount = null
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "stopGeneration save failed", e)
            } finally {
                _uiState.update { state ->
                    if (streaming == null || state.streamingAssistant?.conversationId == streaming.conversationId) {
                        state.copy(isSending = false, streamingAssistant = null)
                    } else state
                }
            }
        }
    }

    fun saveCustomBackground(uri: Uri, target: BackgroundTarget = currentBackgroundTarget()) {
        viewModelScope.launch {
            if (backgroundManager.saveBackground(uri, target)) {
                refreshBackground()
            }
        }
    }

    fun clearCustomBackground(target: BackgroundTarget = currentBackgroundTarget()) {
        backgroundManager.clearBackground(target)
        refreshBackground()
    }

    fun updateConversationParameters(
        modelName: String?,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        contextLimit: Int,
        conversationId: String? = null
    ) {
        val id = conversationId ?: _uiState.value.currentConversationId ?: return
        viewModelScope.launch {
            val conversation = conversationRepository.getById(id) ?: return@launch
            conversationRepository.updateParameters(conversation, modelName, temperature, topP, maxTokens, contextLimit)
            // 保存后立即同步 UI 状态，避免 Room Flow 延迟导致面板重开时显示旧值
            val updated = conversationRepository.getById(id) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    conversations = state.conversations.map { if (it.id == updated.id) updated else it }
                )
            }
        }
    }

    fun updateSystemPrompt(systemPrompt: String, conversationId: String? = null) {
        val id = conversationId ?: _uiState.value.currentConversationId ?: return
        viewModelScope.launch {
            val conversation = conversationRepository.getById(id) ?: return@launch
            conversationRepository.updateSystemPrompt(conversation, systemPrompt)
        }
    }

    fun retryLastUserMessage() {
        if (!tryStartReply()) return
        launchReplyJob reply@{
            val conversationId = _uiState.value.currentConversationId ?: return@reply
            val conversation = conversationRepository.getById(conversationId) ?: return@reply
            val lastUser = messageRepository.getMessages(conversationId).lastOrNull { it.role == "user" } ?: return@reply
            messageRepository.deleteAfter(lastUser)
            conversationRepository.clearHistorySummary(conversationId)
            rebuildCharacterStoryStateAfterHistoryChange(conversationId)
            refreshConversationLastMessage(conversationId)
            val decoded = decodeChatContent(lastUser.content)
            requestAssistantReply(conversation, decoded.text.ifBlank { decoded.attachments.firstOrNull()?.name ?: "附件消息" })
        }
    }

    fun retryFromUserMessage(message: ChatMessageEntity) {
        if (message.role != "user" || !tryStartReply()) return
        launchReplyJob reply@{
            conversationRepository.getById(message.conversationId) ?: return@reply
            messageRepository.deleteFrom(message)
            conversationRepository.clearHistorySummary(message.conversationId)
            rebuildCharacterStoryStateAfterHistoryChange(message.conversationId)
            refreshConversationLastMessage(message.conversationId)
            selectConversation(message.conversationId)
            sendMessageInternal(message.content, message.conversationId)
        }
    }

    fun refreshBackground() {
        viewModelScope.launch {
            _uiState.update { it.copy(backgroundBitmap = backgroundManager.loadBackground(currentBackgroundTarget())) }
        }
    }

    fun currentBackgroundTarget(): BackgroundTarget {
        val conversation = _uiState.value.conversations.firstOrNull { it.id == _uiState.value.currentConversationId }
        return when {
            conversation?.characterId != null -> BackgroundTarget.Character(conversation.characterId)
            conversation != null -> BackgroundTarget.MainChat
            else -> BackgroundTarget.MainChat
        }
    }

    fun clearAllBackgrounds() {
        backgroundManager.clearAllBackgrounds()
        refreshBackground()
    }

    fun savedBackgrounds(): List<SavedBackgroundInfo> = backgroundManager.listSavedBackgrounds()

    fun applySavedBackground(filePath: String, target: BackgroundTarget = currentBackgroundTarget()) {
        if (backgroundManager.applySavedBackground(filePath, target)) {
            refreshBackground()
        }
    }

    fun deleteSavedBackground(filePath: String) {
        if (backgroundManager.deleteSavedBackground(filePath)) {
            refreshBackground()
        }
    }

    fun renameSavedBackground(filePath: String, newName: String): Boolean {
        val ok = backgroundManager.renameSavedBackground(filePath, newName)
        if (ok) refreshBackground()
        return ok
    }

    fun backgroundIntensity(): String = _uiState.value.backgroundIntensity

    fun currentBackgroundPath(target: BackgroundTarget): String? = backgroundManager.currentBackgroundPath(target)

    fun saveBackgroundIntensity(value: String) {
        viewModelScope.launch {
            settingsRepository.saveBackgroundIntensity(value)
        }
    }

    fun getCharacterStoryState(characterId: String): CharacterStoryState {
        val current = _uiState.value.characterStoryStates[characterId]
        if (current != null) return current
        val character = getCharacterById(characterId)
        return deriveInitialCharacterStoryState(character)
    }

    private suspend fun rebuildCharacterStoryStateIfNeeded(conversation: ConversationEntity) {
        val characterId = conversation.characterId ?: return
        val settings = settingsRepository.getSettings()
        val currentState = settings.characterStoryStates[characterId]
        val rebuildNeeded = settings.characterStoryStateMigrationVersion < CHARACTER_STORY_REBUILD_VERSION ||
            currentState == null ||
            currentState.recentEventSummary.isBlank()
        if (!rebuildNeeded) return

        val history = messageRepository.getMessages(conversation.id)
            .filter { it.id > 0L }
            .sortedBy { it.timestamp }
        if (history.isEmpty()) {
            val character = getCharacterById(characterId)
            val derived = deriveInitialCharacterStoryState(character)
            val rebuilt = preserveLockedRelationshipAnchor(derived, currentState)
            settingsRepository.saveCharacterStoryState(characterId, rebuilt)
            settingsRepository.saveCharacterStoryStateMigrationVersion(CHARACTER_STORY_REBUILD_VERSION)
            return
        }

        val rebuiltState = rebuildCharacterStoryStateFromHistory(
            messages = history,
            character = characterRepository.getById(characterId),
            preservedState = currentState
        )
        settingsRepository.saveCharacterStoryState(characterId, rebuiltState)
        settingsRepository.saveCharacterStoryStateMigrationVersion(CHARACTER_STORY_REBUILD_VERSION)
    }

    private fun rebuildCharacterStoryStateFromHistory(
        messages: List<ChatMessageEntity>,
        character: CharacterCardEntity? = null,
        preservedState: CharacterStoryState? = null
    ): CharacterStoryState {
        val firstMessage = messages.firstOrNull()
        val conversationId = firstMessage?.conversationId.orEmpty()
        val resolvedCharacter = character ?: _uiState.value.conversations
            .firstOrNull { it.id == conversationId }
            ?.characterId
            ?.let(::getCharacterById)
        var state = preserveLockedRelationshipAnchor(
            deriveInitialCharacterStoryState(resolvedCharacter),
            preservedState
        )
        var roundCount = 0
        var pendingUser: ChatMessageEntity? = null

        messages.filter { !it.isError }.forEach { message ->
            when (message.role) {
                "user" -> pendingUser = message
                "assistant" -> {
                    val userMessage = pendingUser
                    if (userMessage != null) {
                        roundCount += 1
                        state = advanceCharacterStoryState(
                            previous = state,
                            userText = userMessage.content,
                            assistantText = message.content,
                            assistantInnerThought = message.thinkingContent.orEmpty(),
                            rounds = roundCount,
                            timestamp = message.timestamp
                        ).state
                        pendingUser = null
                    }
                }
            }
        }

        return state
    }

    private suspend fun rebuildCharacterStoryStateAfterHistoryChange(conversationId: String) {
        val conversation = conversationRepository.getById(conversationId) ?: return
        val characterId = conversation.characterId ?: return
        val history = messageRepository.getMessages(conversationId)
            .filter { it.id > 0L }
            .sortedWith(compareBy<ChatMessageEntity> { it.timestamp }.thenBy { it.id })
        val preservedState = settingsRepository.getSettings().characterStoryStates[characterId]
        settingsRepository.clearCharacterStoryState(characterId)
        val rebuilt = if (history.isEmpty()) {
            preserveLockedRelationshipAnchor(
                deriveInitialCharacterStoryState(characterRepository.getById(characterId)),
                preservedState
            )
        } else {
            rebuildCharacterStoryStateFromHistory(
                messages = history,
                character = characterRepository.getById(characterId),
                preservedState = preservedState
            )
        }
        settingsRepository.saveCharacterStoryState(characterId, rebuilt)
    }

    fun saveCharacterStoryState(characterId: String, state: CharacterStoryState) {
        viewModelScope.launch {
            settingsRepository.saveCharacterStoryState(characterId, state)
        }
    }

    fun getCharacterStoryLogs(characterId: String): List<CharacterStoryLogEntry> {
        return _uiState.value.characterStoryLogs[characterId].orEmpty()
    }

    fun appendCharacterStoryLog(characterId: String, entry: CharacterStoryLogEntry) {
        viewModelScope.launch {
            settingsRepository.appendCharacterStoryLog(characterId, entry)
        }
    }

    fun toggleFavorite(message: ChatMessageEntity) {
        if (message.id <= 0L) return
        viewModelScope.launch {
            messageRepository.toggleFavorite(message.id)
        }
    }

    fun autoSaveCurrentModel() {
        viewModelScope.launch {
            val settings = try {
                settingsRepository.getSettings()
            } catch (_: Exception) {
                return@launch
            }
            val baseUrl = settings.apiBaseUrl
            val apiKey = settings.apiKey
            if (baseUrl.isBlank() || apiKey.isBlank()) return@launch
            val existing = modelRepository.findDuplicate(baseUrl, apiKey)
            if (existing == null) {
                val modelName = _uiState.value.selectedModel.ifBlank { "默认" }
                modelRepository.save(
                    SavedModelEntity(
                        name = modelName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        isActive = true
                    )
                )
            } else {
                modelRepository.setActive(existing)
            }
        }
    }

    fun switchModel(model: SavedModelEntity) {
        viewModelScope.launch {
            try {
                val resolvedProvider = modelRepository.getById(model.id) ?: model
                val baseUrl = resolvedProvider.baseUrl.ifBlank { model.baseUrl }.trim().trimEnd('/')
                val apiKey = resolvedProvider.apiKey.ifBlank { model.apiKey }.trim()
                require(baseUrl.isNotBlank()) { "当前服务商没有保存 API 地址，请先保存配置" }
                require(apiKey.isNotBlank()) { "当前服务商没有保存 API Key，请先保存配置" }
                val models = modelService.fetchModels(baseUrl, apiKey).getOrThrow()
                if (models.isEmpty()) {
                    _error.emit("切换失败: 该服务商没有返回可用模型")
                    return@launch
                }
                modelCacheRepository.replaceAll(models, baseUrl)
                val firstModelName = models.firstOrNull()?.trim().orEmpty()
                if (firstModelName.isBlank()) {
                    _error.emit("切换失败: 该服务商没有可用默认模型")
                    return@launch
                }
                applyDefaultProviderAndModel(resolvedProvider, firstModelName)
                daoUpdateModelCount(resolvedProvider.id, models.size)
            } catch (e: Exception) {
                Log.e("ChatVM", "switchModel failed", e)
                _error.emit("切换失败: ${e.message.orEmpty().take(80)}")
            }
        }
    }

    fun switchProviderAndModel(provider: SavedModelEntity, modelName: String) {
        viewModelScope.launch {
            try {
                val resolvedProvider = modelRepository.getById(provider.id) ?: provider
                val baseUrl = resolvedProvider.baseUrl.ifBlank { provider.baseUrl }.trim().trimEnd('/')
                val apiKey = resolvedProvider.apiKey.ifBlank { provider.apiKey }.trim()
                require(baseUrl.isNotBlank()) { "当前服务商没有保存 API 地址，请先保存配置" }
                require(apiKey.isNotBlank()) { "当前服务商没有保存 API Key，请先保存配置" }

                applyDefaultProviderAndModel(resolvedProvider, modelName)

                viewModelScope.launch {
                    runCatching {
                        val models = modelService.fetchModels(baseUrl, apiKey).getOrThrow()
                        modelCacheRepository.replaceAll(models, baseUrl)
                        daoUpdateModelCount(resolvedProvider.id, models.size)
                    }.onFailure { refreshError ->
                        Log.w("ChatVM", "switchProviderAndModel refresh failed", refreshError)
                    }
                }

                _error.emit("已切换到 $modelName")
            } catch (e: Exception) {
                Log.e("ChatVM", "switchProviderAndModel failed", e)
                _error.emit("切换失败: ${e.message.orEmpty().take(80)}")
            }
        }
    }

    fun deleteModel(model: SavedModelEntity) {
        viewModelScope.launch {
            modelRepository.delete(model)
        }
    }

    fun addModel(name: String, apiKey: String, baseUrl: String) {
        viewModelScope.launch {
            modelRepository.save(
                SavedModelEntity(
                    name = name,
                    apiKey = apiKey,
                    baseUrl = baseUrl.trimEnd('/'),
                    isActive = false
                )
            )
        }
    }

    fun updateSavedProvider(provider: SavedModelEntity) {
        viewModelScope.launch {
            modelRepository.save(provider.copy(baseUrl = provider.baseUrl.trimEnd('/')))
            if (provider.isActive) {
                settingsRepository.saveBaseUrl(provider.baseUrl.trimEnd('/'))
                settingsRepository.saveApiKey(provider.apiKey)
            }
        }
    }

    fun refreshModelList() {
        viewModelScope.launch {
            try {
                ensureUsableConnectionSettings()
                val settings = settingsRepository.getSettings()
                val baseUrl = settings.apiBaseUrl.trimEnd('/')
                val apiKey = settings.apiKey
                if (baseUrl.isBlank() || apiKey.isBlank()) return@launch
                val models = modelService.fetchModels(baseUrl, apiKey).getOrElse { emptyList() }
                if (models.isNotEmpty()) {
                    modelCacheRepository.replaceAll(models, baseUrl)
                }
                val savedModels = modelRepository.getAll()
                val provider = savedModels.firstOrNull { it.baseUrl == baseUrl && it.apiKey == apiKey }
                if (provider != null) {
                    daoUpdateModelCount(provider.id, models.size)
                } else {
                    val active = savedModels.firstOrNull { it.isActive }
                    if (active != null) {
                        daoUpdateModelCount(active.id, models.size)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "refreshModelList failed", e)
            }
        }
    }

    private suspend fun daoUpdateModelCount(providerId: Long, count: Int) {
        try {
            modelRepository.updateModelCount(providerId, count, System.currentTimeMillis())
        } catch (_: Exception) {}
    }

    private suspend fun applyDefaultProviderAndModel(provider: SavedModelEntity, modelName: String) {
        val settings = runCatching { settingsRepository.getSettings() }.getOrElse { return }
        val resolvedBaseUrl = provider.baseUrl.trim().trimEnd('/')
        val resolvedApiKey = provider.apiKey.trim()
        val resolvedModelName = modelName.trim()
        require(resolvedBaseUrl.isNotBlank()) { "当前服务商没有保存 API 地址，请先保存配置" }
        require(resolvedApiKey.isNotBlank()) { "当前服务商没有保存 API Key，请先保存配置" }
        require(resolvedModelName.isNotBlank()) { "当前没有可用模型" }

        if (provider.baseUrl != resolvedBaseUrl || provider.apiKey != resolvedApiKey) {
            modelRepository.save(provider.copy(baseUrl = resolvedBaseUrl, apiKey = resolvedApiKey))
        }
        modelRepository.activate(provider.id)
        settingsRepository.saveBaseUrl(resolvedBaseUrl)
        settingsRepository.saveApiKey(resolvedApiKey)
        settingsRepository.saveSelectedModel(resolvedModelName)

        val caps = ModelCapabilityMap.get(resolvedModelName)
        settingsRepository.saveSupportsImage(caps.contains(Capability.VISION))
        settingsRepository.saveSupportsFile(caps.contains(Capability.VISION))

        if (settings.imageProviderId == 0L || settings.imageModelName.isBlank()) {
            settingsRepository.saveImageProviderId(0L)
        }

        clearConversationModelOverridesForDefault()

        _uiState.update {
            it.copy(
                selectedModel = resolvedModelName,
                modelSupportsVision = caps.contains(Capability.VISION),
                modelSupportsTools = caps.contains(Capability.TOOLS),
                supportsImage = caps.contains(Capability.VISION),
                supportsFile = caps.contains(Capability.VISION)
            )
        }
    }

    private suspend fun clearConversationModelOverridesForDefault() {
        val charactersById = characterRepository.getAll().associateBy { it.id }
        conversationRepository.getAll().forEach { conversation ->
            val shouldFollowDefault = when (val characterId = conversation.characterId) {
                null -> true
                else -> charactersById[characterId]?.modelId.isNullOrBlank()
            }
            if (shouldFollowDefault && conversation.modelName != null) {
                conversationRepository.updateParameters(
                    conversation,
                    null,
                    conversation.temperature,
                    conversation.topP,
                    conversation.maxTokens,
                    conversation.contextLimit
                )
            }
        }
    }

    fun toggleModelFavorite(modelId: String) {
        viewModelScope.launch {
            modelCacheRepository.toggleFavorite(modelId)
        }
    }

    fun switchModelAndApply(modelInfo: ModelInfo) {
        val caps = modelInfo.capabilities
        _uiState.update {
            it.copy(
                selectedModel = modelInfo.name,
                modelSupportsVision = caps.contains(Capability.VISION),
                modelSupportsTools = caps.contains(Capability.TOOLS),
                supportsImage = caps.contains(Capability.VISION),
                supportsFile = caps.contains(Capability.VISION)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                settingsRepository.saveSelectedModel(modelInfo.name)
                settingsRepository.saveSupportsImage(caps.contains(Capability.VISION))
                settingsRepository.saveSupportsFile(caps.contains(Capability.VISION))
                val savedModels = modelRepository.getAll()
                val matchingProvider = savedModels.firstOrNull { provider ->
                    modelInfo.baseUrl.contains(provider.baseUrl) || provider.baseUrl.contains(modelInfo.baseUrl)
                }
                if (matchingProvider != null) {
                    modelRepository.activate(matchingProvider.id)
                    settingsRepository.saveBaseUrl(matchingProvider.baseUrl)
                    settingsRepository.saveApiKey(matchingProvider.apiKey)
                }
                val convId = _uiState.value.currentConversationId ?: return@launch
                val conv = conversationRepository.getById(convId) ?: return@launch
                conversationRepository.updateParameters(conv, modelInfo.name, conv.temperature, conv.topP, conv.maxTokens, conv.contextLimit)
            } catch (e: Exception) {
                Log.e("ChatVM", "switchModelAndApply failed", e)
            }
        }
    }

    fun switchDefaultModel(modelInfo: ModelInfo) {
        val caps = modelInfo.capabilities
        _uiState.update {
            it.copy(
                selectedModel = modelInfo.name,
                modelSupportsVision = caps.contains(Capability.VISION),
                modelSupportsTools = caps.contains(Capability.TOOLS),
                supportsImage = caps.contains(Capability.VISION),
                supportsFile = caps.contains(Capability.VISION)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                settingsRepository.saveSelectedModel(modelInfo.name)
                settingsRepository.saveSupportsImage(caps.contains(Capability.VISION))
                settingsRepository.saveSupportsFile(caps.contains(Capability.VISION))
                val savedModels = modelRepository.getAll()
                val matchingProvider = savedModels.firstOrNull { provider ->
                    modelInfo.baseUrl.contains(provider.baseUrl) || provider.baseUrl.contains(modelInfo.baseUrl)
                }
                if (matchingProvider != null) {
                    modelRepository.activate(matchingProvider.id)
                    settingsRepository.saveBaseUrl(matchingProvider.baseUrl)
                    settingsRepository.saveApiKey(matchingProvider.apiKey)
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "switchDefaultModel failed", e)
            }
        }
    }

    fun ensureModelCacheLoaded() {
        viewModelScope.launch {
            try {
                if (modelCacheRepository.isCacheEmpty()) {
                    refreshModelList()
                }
            } catch (e: Exception) { Log.e("ChatVM", "ensureModelCache failed", e) }
        }
    }

    fun getCharacterById(characterId: String): CharacterCardEntity? {
        return _uiState.value.characters.firstOrNull { it.id == characterId }
    }

    fun checkSpeechAvailability(context: android.content.Context) {
        if (speechAvailableChecked) return
        speechAvailableChecked = true
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val available = intent.resolveActivity(context.packageManager) != null
        _uiState.update { it.copy(speechAvailable = available) }
    }

    private suspend fun requestAssistantReply(
        conversation: ConversationEntity,
        userText: String,
        historyUpToMessageId: Long? = null,
        replaceFromAssistant: ChatMessageEntity? = null,
        replacementStoryState: CharacterStoryState? = null
    ) {
        if (ChatMessageBuilder.isGroupConversation(conversation)) {
            requestGroupAssistantReplies(conversation, userText)
            return
        }
        val conversationId = conversation.id
        val requestStartAt = System.currentTimeMillis()
        val latestConversation = conversationRepository.getById(conversationId) ?: conversation
        _uiState.update {
            it.copy(
                isSending = true,
                streamingAssistant = StreamingAssistantState(conversationId = conversationId, startedAt = requestStartAt)
            )
        }

        val isCharacterChat = latestConversation.characterId != null
        val character = if (isCharacterChat) characterRepository.getById(latestConversation.characterId!!) else null
        ensureUsableConnectionSettings()
        val settingsModel = settingsRepository.getSettings().selectedModel.takeIf { it.isNotBlank() }

        val effectiveModel = if (isCharacterChat) {
            character?.modelId?.takeIf { it.isNotBlank() }
                ?: latestConversation.modelName?.takeIf { it.isNotBlank() }
                ?: _uiState.value.selectedModel.takeIf { it.isNotBlank() }
                ?: settingsModel
        } else {
            latestConversation.modelName?.takeIf { it.isNotBlank() }
                ?: _uiState.value.selectedModel.takeIf { it.isNotBlank() }
                ?: settingsModel
        }
        val searchResultText = if (_uiState.value.webSearchEnabled) {
            val searchSettings = settingsRepository.getSettings()
            if (searchSettings.searchIndependentMode && searchSettings.searchProviderUrl.isNotBlank()) {
                performWebSearch(userText, searchSettings.searchProviderUrl, searchSettings.searchProviderKey)
            } else null
        } else null
        if (historyUpToMessageId == null) {
            refreshHistorySummaryIfNeeded(latestConversation, effectiveModel)
        }
        val apiMessages = messageBuilder.buildApiMessages(
            conversationId = conversationId,
            userText = userText,
            effectiveModel = effectiveModel,
            webSearchEnabled = _uiState.value.webSearchEnabled,
            personaEnabled = _uiState.value.personaEnabled,
            personaPrompt = _uiState.value.personaPrompt,
            modelSupportsVision = _uiState.value.modelSupportsVision,
            hasVisionConfig = _uiState.value.hasVisionConfig,
            selectedModel = _uiState.value.selectedModel,
            characterStoryStates = replacementStoryState?.let { state ->
                val characterId = latestConversation.characterId
                if (characterId == null) _uiState.value.characterStoryStates
                else _uiState.value.characterStoryStates + (characterId to state)
            } ?: _uiState.value.characterStoryStates,
            searchResultText = searchResultText,
            creativePresetTextOverride = _uiState.value.creativePresetText,
            creativePresetEnabledOverride = latestConversation.characterId?.let {
                _uiState.value.characterCreativePresetEnabled[it]
            },
            creativePresetAffectsPersonaOverride = latestConversation.characterId?.let {
                _uiState.value.characterCreativePresetAffectsPersona[it]
            },
            characterCreativePresetTextOverride = latestConversation.characterId?.let {
                _uiState.value.characterCreativePresetTexts[it]
            },
            characterCreativePresetEnabledOverride = latestConversation.characterId?.let {
                _uiState.value.characterIndependentCreativePresetEnabled[it]
            },
            characterCreativePresetAffectsPersonaOverride = latestConversation.characterId?.let {
                _uiState.value.characterIndependentCreativePresetAffectsPersona[it]
            },
            historyUpToMessageId = historyUpToMessageId,
            maxOutputTokens = latestConversation.maxTokens
        )
        publishPromptPreview(apiMessages)

        val effectiveTemperature = if (isCharacterChat && character != null && character.temperature > 0f) {
            character.temperature
        } else {
            latestConversation.temperature
        }
        val shouldUseVisionConfig = _uiState.value.hasVisionConfig && apiMessages.any {
            it.imageDataUrls.isNotEmpty() || it.fileTexts.isNotEmpty()
        }

        suspend fun executeRequest() = if (_uiState.value.streamEnabled) {
            apiService.streamChat(
                messages = apiMessages,
                model = effectiveModel,
                useVisionConfig = shouldUseVisionConfig,
                temperature = effectiveTemperature,
                topP = latestConversation.topP,
                maxTokens = latestConversation.maxTokens,
                timeoutSeconds = _uiState.value.timeoutSeconds.toLong(),
                webSearch = _uiState.value.webSearchEnabled,
                onDelta = { delta ->
                    appendStreamingDeltaGradually(conversationId, requestStartAt, delta)
                }
            )
        } else {
            apiService.chat(
                messages = apiMessages,
                model = effectiveModel,
                useVisionConfig = shouldUseVisionConfig,
                temperature = effectiveTemperature,
                topP = latestConversation.topP,
                maxTokens = latestConversation.maxTokens,
                timeoutSeconds = _uiState.value.timeoutSeconds.toLong(),
                webSearch = _uiState.value.webSearchEnabled
            ).onSuccess { result ->
                appendStreamingDelta(
                    conversationId = conversationId,
                    startAt = requestStartAt,
                    content = result.content,
                    reasoningContent = result.reasoningContent
                )
            }
        }

        var streamResult = executeRequest()
        val firstAttemptContent = _uiState.value.streamingAssistant
            ?.takeIf { it.conversationId == conversationId }
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: streamResult.getOrNull()?.content.orEmpty()
            .ifBlank { partialContentFromFailure(streamResult.exceptionOrNull()) }
        val shouldRetryResponse = streamResult.isSuccess && firstAttemptContent.isBlank()
        val shouldRetryFailure = firstAttemptContent.isBlank() &&
            isRetryableGenerationFailure(streamResult.exceptionOrNull())
        if (shouldRetryFailure || shouldRetryResponse) {
            _uiState.update { state ->
                state.copy(
                    streamingAssistant = StreamingAssistantState(
                        conversationId = conversationId,
                        startedAt = requestStartAt
                    )
                )
            }
            delay(600L)
            streamResult = executeRequest()
        }

        if (streamResult.exceptionOrNull() is CancellationException) {
            clearStreamingState(conversationId)
            return
        }

        streamResult.onSuccess { result ->
            try {
                val streamingState = _uiState.value.streamingAssistant?.takeIf { it.conversationId == conversationId }
                val finalContent = streamingState?.content?.ifBlank { result.content } ?: result.content
                val finalThinking = streamingState?.thinkingContent?.ifBlank {
                    result.reasoningContent
                } ?: result.reasoningContent

                val firstResponseAt = streamingState?.firstResponseAt
                val thinkingDuration = (((firstResponseAt ?: System.currentTimeMillis()) - requestStartAt) / 1000L)
                    .toInt()
                    .coerceAtLeast(1)

                val extractedThinking = extractThinking(finalContent)
                val mergedThinking = listOf(finalThinking, extractedThinking.thinking)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")

                val cleanContent = ChatMessageBuilder.stripLeadingSpeakerPrefix(extractedThinking.content.trim(), character?.name)
                val cleanThinking = mergedThinking

                if (cleanContent.isBlank()) {
                    _error.emit(
                        if (cleanThinking.isNotBlank()) {
                            "模型只返回了思考内容，没有正式回答。请重试或切换模型/关闭实时打字。"
                        } else {
                            "AI 未返回有效回复，请重试"
                        }
                    )
                } else {
                    val assistantText = cleanContent
                    val responseSavedAt = System.currentTimeMillis()
                    if (replaceFromAssistant != null) {
                        messageRepository.deleteFrom(replaceFromAssistant)
                        conversationRepository.clearHistorySummary(conversationId)
                        rebuildCharacterStoryStateAfterHistoryChange(conversationId)
                    }
                    messageRepository.insert(
                        ChatMessageEntity(
                            conversationId = conversationId, role = "assistant",
                            speakerCharacterId = character?.id,
                            speakerName = character?.name,
                            content = assistantText,
                            thinkingContent = cleanThinking.ifBlank { null },
                            thinkingDuration = if (cleanThinking.isBlank()) null else thinkingDuration,
                            timestamp = responseSavedAt, tokenCount = result.totalTokens
                        )
                    )

                    val updatedConversation = conversationRepository.getById(conversationId) ?: conversation
                    conversationRepository.updateLastMessage(updatedConversation, assistantText)
                    if (updatedConversation.characterId != null) {
                        characterRepository.updateLastMessage(updatedConversation.characterId, assistantText)
                    }
                    result.totalTokens?.let { tokens ->
                        conversationRepository.addTokenUsage(updatedConversation, tokens)
                        settingsRepository.addHistoricalTokens(tokens)
                    }
                    if (!updatedConversation.isNamed) {
                        autoNameConversation(updatedConversation, userText, assistantText)
                    }
                    updatedConversation.characterId?.let { characterId ->
                        if (isMetaSafetyRefusal(assistantText)) return@let
                        val userRounds = messageRepository.countByRole(conversationId, "user")
                        val assistantRounds = messageRepository.countByRole(conversationId, "assistant")
                        val totalRounds = minOf(userRounds, assistantRounds)
                        val knownOtherCharacters = _uiState.value.characters.filterNot { it.id == characterId }
                        val conversationHistory = messageRepository.getMessages(conversationId)
                        val cardRoleNames = character?.let {
                            messageBuilder.resolveSingleCardRoleNames(it, conversationHistory)
                        }.orEmpty()
                        val publicSpeakers = ChatMessageBuilder.extractNamedPublicSpeakers(
                            assistantText,
                            cardRoleNames
                        )
                        val progress = advanceCharacterStoryState(
                            previous = replacementStoryState ?: getCharacterStoryState(characterId),
                            userText = userText,
                            assistantText = assistantText,
                            assistantInnerThought = cleanThinking,
                            rounds = totalRounds,
                            directInteractionAllowed = isDirectInteractionFor(
                                characterId = characterId,
                                otherCharacters = knownOtherCharacters,
                                userText = userText
                            ),
                            otherCharacterNames = knownOtherCharacters.map { it.name },
                            roleInteractionOnly = cardRoleNames.size >= 2 && publicSpeakers.size != 1,
                            timestamp = responseSavedAt
                        )
                        settingsRepository.saveCharacterStoryState(characterId, progress.state)
                        if (progress.shouldAppendLog) {
                            settingsRepository.appendCharacterStoryLog(
                                characterId,
                                CharacterStoryLogEntry(
                                    id = UUID.randomUUID().toString(),
                                    timestamp = responseSavedAt,
                                    title = progress.logTitle,
                                    note = progress.logNote,
                                    affection = progress.state.affection,
                                    trust = progress.state.trust,
                                    tension = progress.state.tension
                                )
                            )
                        }
                    }
                    if (updatedConversation.isMainChatConversation()) {
                        extractMainMemory(userText, conversationId)
                    }
                    breakArmorOutputDiagnostic(updatedConversation.characterId, assistantText)?.let { warning ->
                        _error.emit(warning)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "post-response handling failed", e)
                _error.emit("回复已生成，但后处理失败: ${e.message.orEmpty().take(80)}")
            }
        }.onFailure { throwable ->
            val streamingPartial = _uiState.value.streamingAssistant
                ?.takeIf { it.conversationId == conversationId }
            val failurePartial = partialResultFromFailure(throwable)
            val visiblePartial = streamingPartial?.content?.takeIf { it.isNotBlank() }
                ?: failurePartial?.content.orEmpty()
            if (visiblePartial.isNotBlank()) {
                saveIncompleteAssistant(
                    conversation = conversation,
                    character = character,
                    content = visiblePartial,
                    thinking = streamingPartial?.thinkingContent?.takeIf { it.isNotBlank() }
                        ?: failurePartial?.reasoningContent.orEmpty(),
                    requestStartAt = requestStartAt,
                    tokenCount = failurePartial?.totalTokens
                )
            }
            _error.emit(formatChatFailure(throwable))
        }

        clearStreamingState(conversationId)
    }

    fun updateRelationshipAnchor(characterId: String, anchor: String, intimacyBaseline: Int) {
        val updated = getCharacterStoryState(characterId).copy(
            relationshipAnchor = anchor.trim().ifBlank { "未知" },
            relationshipAnchorLocked = true,
            intimacyBaseline = intimacyBaseline.coerceIn(-100, 100)
        )
        _uiState.update { state ->
            state.copy(characterStoryStates = state.characterStoryStates + (characterId to updated))
        }
        viewModelScope.launch { settingsRepository.saveCharacterStoryState(characterId, updated) }
    }

    fun restoreAutomaticRelationshipAnchor(characterId: String) {
        val derived = deriveInitialCharacterStoryState(getCharacterById(characterId))
        val current = getCharacterStoryState(characterId)
        val updated = current.copy(
            relationshipAnchor = derived.relationshipAnchor,
            relationshipAnchorLocked = false,
            intimacyBaseline = derived.intimacyBaseline
        )
        _uiState.update { state ->
            state.copy(characterStoryStates = state.characterStoryStates + (characterId to updated))
        }
        viewModelScope.launch { settingsRepository.saveCharacterStoryState(characterId, updated) }
    }

    private fun preserveLockedRelationshipAnchor(
        derived: CharacterStoryState,
        preserved: CharacterStoryState?
    ): CharacterStoryState {
        if (preserved?.relationshipAnchorLocked != true) return derived
        return derived.copy(
            relationshipAnchor = preserved.relationshipAnchor,
            relationshipAnchorLocked = true,
            intimacyBaseline = preserved.intimacyBaseline
        )
    }

    fun updateConversationContextLayers(
        conversationId: String,
        worldInfo: String,
        authorNote: String
    ) {
        viewModelScope.launch {
            conversationRepository.updateWorldInfo(conversationId, worldInfo)
            conversationRepository.updateAuthorNote(conversationId, authorNote)
        }
    }

    fun updateGroupScene(conversationId: String, scene: String, locked: Boolean) {
        viewModelScope.launch {
            conversationRepository.updateGroupScene(conversationId, scene, locked)
        }
    }

    fun clearConversationHistorySummary(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.clearHistorySummary(conversationId)
            _error.emit("历史摘要已清除，将从保留的原始消息重新生成")
        }
    }

    fun summarizeConversationNow(conversationId: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.getById(conversationId) ?: return@launch
            val effectiveModel = conversation.modelName?.takeIf { it.isNotBlank() }
                ?: _uiState.value.selectedModel.takeIf { it.isNotBlank() }
            val before = conversation.summaryUpToMessageId
            refreshHistorySummaryIfNeeded(conversation, effectiveModel, force = true)
            val updated = conversationRepository.getById(conversationId)
            _error.emit(
                if (updated?.summaryUpToMessageId != before) "历史摘要已更新" else "当前没有需要压缩的旧消息"
            )
        }
    }

    private suspend fun refreshHistorySummaryIfNeeded(
        conversation: ConversationEntity,
        effectiveModel: String?,
        force: Boolean = false
    ) {
        val history = messageRepository.getMessages(conversation.id)
            .filter { !it.isError && (it.role == "user" || it.role == "assistant") }
        val summaryThreshold = settingsRepository.getSettings().autoSummaryThreshold.coerceIn(4, 500)
        if (!force && history.size < summaryThreshold) return
        val keepRecentRounds = conversation.contextLimit.coerceIn(1, 120)
        val cutoffIndex = splitIndexForRecentUserRounds(history.map { it.role }, keepRecentRounds)
        if (cutoffIndex <= 0) return
        val summarizable = history.take(cutoffIndex)
            .filter { it.id > (conversation.summaryUpToMessageId ?: 0L) }
        val batchIndexes = selectSummaryBatchIndexes(
            contentLengths = summarizable.map { decodeChatContent(it.content).text.length + 16 },
            charBudget = 24_000
        ) ?: return
        val selectedForSummary = summarizable.slice(batchIndexes)
        val lastCovered = selectedForSummary.lastOrNull() ?: return
        val cardCharacter = conversation.characterId?.let { characterRepository.getById(it) }
        val cardRoleNames = cardCharacter?.let {
            messageBuilder.resolveSingleCardRoleNames(it, selectedForSummary)
        }.orEmpty()
        val transcript = selectedForSummary.joinToString("\n") { message ->
            val content = ChatMessageBuilder.publicGroupContent(
                decodeChatContent(message.content).text
            ).trim()
            if (message.role == "user") {
                "[message role=user]\n$content"
            } else {
                val owner = message.speakerName?.takeIf { it.isNotBlank() }
                    ?: cardCharacter?.name
                    ?: "角色卡"
                val publicSpeakers = ChatMessageBuilder.extractNamedPublicSpeakers(content, cardRoleNames)
                val actualSpeakers = publicSpeakers.takeIf { it.isNotEmpty() }
                    ?.joinToString("|")
                    ?: "未确认"
                "[message role=assistant owner=\"$owner\" publicSpeakers=\"$actualSpeakers\"]\n$content"
            }
        }
        if (transcript.isBlank()) return

        apiService.summarizeConversation(
            previousSummary = conversation.historySummary,
            transcript = transcript
        ).onSuccess { summary ->
            if (summary.isNotBlank()) {
                conversationRepository.updateHistorySummary(
                    conversationId = conversation.id,
                    historySummary = summary,
                    summaryUpToMessageId = lastCovered.id
                )
            }
        }.onFailure { error ->
            Log.w("ChatVM", "history summary failed; using local fallback for this request", error)
        }
    }


    private fun launchReplyJob(block: suspend CoroutineScope.() -> Unit) {
        val job = viewModelScope.launch(block = block)
        activeReplyJob = job
        job.invokeOnCompletion {
            if (activeReplyJob === job) {
                activeReplyJob = null
                replyInFlight.set(false)
                _uiState.update { it.copy(isSending = false, streamingAssistant = null) }
            }
        }
    }

    private fun tryStartReply(clearInput: Boolean = false): Boolean {
        if (!replyInFlight.compareAndSet(false, true)) return false
        _uiState.update { it.copy(isSending = true, input = if (clearInput) "" else it.input) }
        return true
    }

    private fun clearStreamingState(conversationId: String) {
        _uiState.update { state ->
            if (state.streamingAssistant?.conversationId == conversationId) {
                state.copy(isSending = false, streamingAssistant = null)
            } else if (state.streamingAssistant == null) {
                state.copy(isSending = false)
            } else state
        }
    }

    private fun isRetryableGenerationFailure(error: Throwable?): Boolean {
        if (error == null || error is CancellationException || error is OutputTruncatedException ||
            error is UpstreamContentBlockedException) return false
        if (error is IncompleteStreamException) return true
        val message = error.message.orEmpty().lowercase()
        return listOf(
            "timeout", "超时", "连接失败", "http 408", "http 429",
            "http 500", "http 502", "http 503", "http 504"
        ).any(message::contains)
    }

    private fun partialResultFromFailure(error: Throwable?): com.hana.app.data.api.models.StreamResult? {
        return when (error) {
            is IncompleteStreamException -> error.partialResult
            is OutputTruncatedException -> error.partialResult
            is UpstreamContentBlockedException -> error.partialResult
            else -> null
        }
    }

    private fun partialContentFromFailure(error: Throwable?): String {
        return partialResultFromFailure(error)?.content.orEmpty()
    }

    private suspend fun saveIncompleteAssistant(
        conversation: ConversationEntity,
        character: CharacterCardEntity?,
        content: String,
        thinking: String,
        requestStartAt: Long,
        tokenCount: Int?
    ) {
        val extracted = extractThinking(content)
        val cleanContent = ChatMessageBuilder.stripLeadingSpeakerPrefix(extracted.content.trim(), character?.name)
        if (cleanContent.isBlank()) return
        val cleanThinking = listOf(thinking, extracted.thinking)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val timestamp = System.currentTimeMillis()
        messageRepository.insert(
            ChatMessageEntity(
                conversationId = conversation.id,
                role = "assistant",
                speakerCharacterId = character?.id,
                speakerName = character?.name,
                content = cleanContent,
                thinkingContent = cleanThinking.ifBlank { null },
                thinkingDuration = if (cleanThinking.isBlank()) null else
                    ((timestamp - requestStartAt) / 1000L).toInt().coerceAtLeast(1),
                timestamp = timestamp,
                tokenCount = tokenCount,
                isError = true
            )
        )
        val latestConversation = conversationRepository.getById(conversation.id) ?: conversation
        conversationRepository.updateLastMessage(latestConversation, cleanContent)
        latestConversation.characterId?.let { characterRepository.updateLastMessage(it, cleanContent) }
    }

    private suspend fun ensureConversationForSend(requestedConversationId: String?): ConversationEntity {
        val currentId = requestedConversationId
        if (currentId != null) {
            conversationRepository.getById(currentId)?.let { return it }
        }
        return conversationRepository.createNormalConversation()
    }

    private suspend fun appendStreamingDeltaGradually(conversationId: String, startAt: Long, delta: StreamDelta) {
        if (delta.reasoningContent.isNotBlank()) {
            appendStreamingDelta(
                conversationId = conversationId,
                startAt = startAt,
                content = "",
                reasoningContent = delta.reasoningContent
            )
        }
        if (delta.content.isBlank()) return

        // 将整个 chunk 一次性更新，不逐字符 delay，避免大量 Compose 重组
        appendStreamingDelta(
            conversationId = conversationId,
            startAt = startAt,
            content = delta.content,
            reasoningContent = ""
        )
        _scrollTrigger.emit(Unit)
    }

    private fun appendStreamingDelta(
        conversationId: String,
        startAt: Long,
        content: String,
        reasoningContent: String
    ) {
        try {
            _uiState.update { state ->
                val current = state.streamingAssistant
                    ?.takeIf { it.conversationId == conversationId }
                    ?: return@update state
                val now = System.currentTimeMillis()
                val firstResponseAt = current.firstResponseAt ?: now.takeIf {
                    content.isNotBlank() || reasoningContent.isNotBlank()
                }
                val firstContentAt = when {
                    current.firstContentAt != null -> current.firstContentAt
                    content.isNotBlank() -> now
                    else -> null
                }
                state.copy(
                    streamingAssistant = current.copy(
                        content = current.content + content,
                        thinkingContent = current.thinkingContent + reasoningContent,
                        firstContentAt = firstContentAt,
                        firstResponseAt = firstResponseAt
                    )
                )
            }
        } catch (_: Exception) {}
    }

    private suspend fun requestGroupAssistantReplies(
        conversation: ConversationEntity,
        userText: String
    ) {
        val latestConversation = conversationRepository.getById(conversation.id) ?: conversation
        val participants = getConversationParticipants(latestConversation)
        if (participants.isEmpty()) {
            _error.emit("群聊中没有可用角色")
            return
        }
        _uiState.update { it.copy(isSending = true, streamingAssistant = null) }
        try {
            ensureUsableConnectionSettings()
            val mentionedIds = mentionedParticipantIds(participants, userText)
            val replyCharacters = chooseGroupReplyCharacters(latestConversation, participants, userText)
                .let { candidates ->
                    val explicitlyMentioned = candidates.filter { it.character.id in mentionedIds }
                    if (mentionedIds.size == 1 && !invitesGroupParticipation(userText)) {
                        explicitlyMentioned.take(1)
                    } else candidates
                }
            val roundId = messageRepository.getMessages(conversation.id)
                .lastOrNull { it.role == "user" }?.roundId
                ?: UUID.randomUUID().toString()
            var previousReply: ChatMessageEntity? = null
            for ((index, candidate) in replyCharacters.withIndex()) {
                val latest = conversationRepository.getById(conversation.id) ?: latestConversation
                val requestStartAt = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isSending = true,
                        streamingAssistant = StreamingAssistantState(
                            conversationId = conversation.id,
                            speakerCharacterId = candidate.character.id,
                            speakerName = candidate.character.name,
                            startedAt = requestStartAt
                        )
                    )
                }
                val result = requestSingleCharacterReply(
                    conversation = latest,
                    character = candidate.character,
                    userText = userText,
                    participants = participants,
                    requestStartAt = requestStartAt,
                    responseOffset = index,
                    roundId = roundId,
                    turnIndex = index + 1,
                    previousReply = previousReply
                )
                if (!result) {
                    break
                }
                previousReply = messageRepository.getLastMessage(conversation.id)
            }
        } finally {
            clearStreamingState(conversation.id)
        }
    }

    private suspend fun requestSingleCharacterReply(
        conversation: ConversationEntity,
        character: CharacterCardEntity,
        userText: String,
        participants: List<CharacterCardEntity>,
        requestStartAt: Long,
        responseOffset: Int,
        roundId: String,
        turnIndex: Int,
        previousReply: ChatMessageEntity?
    ): Boolean {
        val settingsModel = settingsRepository.getSettings().selectedModel.takeIf { it.isNotBlank() }
        val fallbackModel = conversation.modelName?.takeIf { it.isNotBlank() }
            ?: _uiState.value.selectedModel.takeIf { it.isNotBlank() }
            ?: settingsModel
        val activeBaseUrl = modelRepository.getActive()?.baseUrl?.trim()?.trimEnd('/').orEmpty()
        val availableModels = modelCacheRepository.getAll()
            .filter { activeBaseUrl.isBlank() || it.baseUrl.trim().trimEnd('/') == activeBaseUrl }
            .mapTo(mutableSetOf()) { it.name }
        val effectiveModel = resolveGroupCharacterModel(
            characterModel = character.modelId,
            fallbackModel = fallbackModel,
            availableModels = availableModels
        )
        if (character.modelId.isNotBlank() && effectiveModel != character.modelId && effectiveModel != null) {
            _error.emit("${character.name} 绑定的模型“${character.modelId}”在当前服务商不可用，已回退到 $effectiveModel")
        }
        val systemPrompt = messageBuilder.buildGroupCharacterPrompt(
            conversation,
            character,
            participants,
            ::getCharacterStoryState,
            previousReply,
            previousReply?.speakerCharacterId?.let { targetId ->
                _uiState.value.interCharacterRelations[interCharacterRelationKey(character.id, targetId)]
            }
        )
        refreshHistorySummaryIfNeeded(conversation, effectiveModel)
        val apiMessages = messageBuilder.buildApiMessages(
            conversationId = conversation.id,
            userText = userText,
            effectiveModel = effectiveModel,
            overrideSystemPrompt = systemPrompt,
            webSearchEnabled = _uiState.value.webSearchEnabled,
            personaEnabled = _uiState.value.personaEnabled,
            personaPrompt = _uiState.value.personaPrompt,
            modelSupportsVision = _uiState.value.modelSupportsVision,
            hasVisionConfig = _uiState.value.hasVisionConfig,
            selectedModel = _uiState.value.selectedModel,
            characterStoryStates = _uiState.value.characterStoryStates,
            creativePresetCharacterId = character.id,
            creativePresetTextOverride = _uiState.value.creativePresetText,
            creativePresetEnabledOverride = _uiState.value.characterCreativePresetEnabled[character.id],
            creativePresetAffectsPersonaOverride = _uiState.value.characterCreativePresetAffectsPersona[character.id],
            characterCreativePresetTextOverride = _uiState.value.characterCreativePresetTexts[character.id],
            characterCreativePresetEnabledOverride = _uiState.value.characterIndependentCreativePresetEnabled[character.id],
            characterCreativePresetAffectsPersonaOverride = _uiState.value.characterIndependentCreativePresetAffectsPersona[character.id],
            groupViewerCharacterId = character.id,
            maxOutputTokens = conversation.maxTokens
        )
        publishPromptPreview(apiMessages)
        val shouldUseVisionConfig = _uiState.value.hasVisionConfig && apiMessages.any {
            it.imageDataUrls.isNotEmpty() || it.fileTexts.isNotEmpty()
        }
        val effectiveTemperature = if (character.temperature > 0f) character.temperature else conversation.temperature
        suspend fun executeGroupRequest(model: String?) = if (_uiState.value.streamEnabled) {
            apiService.streamChat(
                messages = apiMessages,
                model = model,
                useVisionConfig = shouldUseVisionConfig,
                temperature = effectiveTemperature,
                topP = conversation.topP,
                maxTokens = conversation.maxTokens,
                timeoutSeconds = _uiState.value.timeoutSeconds.toLong(),
                webSearch = _uiState.value.webSearchEnabled,
                onDelta = { delta -> appendStreamingDeltaGradually(conversation.id, requestStartAt, delta) }
            )
        } else {
            apiService.chat(
                messages = apiMessages,
                model = model,
                useVisionConfig = shouldUseVisionConfig,
                temperature = effectiveTemperature,
                topP = conversation.topP,
                maxTokens = conversation.maxTokens,
                timeoutSeconds = _uiState.value.timeoutSeconds.toLong(),
                webSearch = _uiState.value.webSearchEnabled
            ).onSuccess { result ->
                appendStreamingDelta(
                    conversationId = conversation.id,
                    startAt = requestStartAt,
                    content = result.content,
                    reasoningContent = result.reasoningContent
                )
            }
        }
        var streamResult = executeGroupRequest(effectiveModel)
        val failedWith404 = streamResult.exceptionOrNull()?.message.orEmpty().contains("HTTP 404", ignoreCase = true)
        if (failedWith404 && fallbackModel != null && !effectiveModel.equals(fallbackModel, ignoreCase = true)) {
            _uiState.update { state ->
                val current = state.streamingAssistant
                if (current?.conversationId == conversation.id && current.speakerCharacterId == character.id) {
                    state.copy(streamingAssistant = current.copy(content = "", thinkingContent = ""))
                } else state
            }
            _error.emit("${character.name} 的专属模型返回 404，正在使用 $fallbackModel 重试")
            streamResult = executeGroupRequest(fallbackModel)
        }
        if (streamResult.exceptionOrNull() is CancellationException) {
            return false
        }
        var success = true
        streamResult.onSuccess { result ->
            val streamingState = _uiState.value.streamingAssistant?.takeIf { it.conversationId == conversation.id }
            val finalContent = streamingState?.content?.ifBlank { result.content } ?: result.content
            val finalThinking = streamingState?.thinkingContent?.ifBlank { result.reasoningContent } ?: result.reasoningContent
            val firstResponseAt = streamingState?.firstResponseAt
            val thinkingDuration = (((firstResponseAt ?: System.currentTimeMillis()) - requestStartAt) / 1000L)
                .toInt()
                .coerceAtLeast(1)
            val extractedThinking = extractThinking(finalContent)
            val mergedThinking = listOf(finalThinking, extractedThinking.thinking)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            val quotePrevious = previousReply != null && extractedThinking.content.contains("<quote_previous/>", ignoreCase = true)
            val replyContent = extractedThinking.content.replace(Regex("<quote_previous\\s*/>", RegexOption.IGNORE_CASE), "").trim()
            val cleanContent = ChatMessageBuilder.sanitizeGroupReplyContent(replyContent, character.name, participants)
            if (cleanContent.isBlank()) {
                success = false
                return@onSuccess
            }
            val responseSavedAt = System.currentTimeMillis() + responseOffset
            viewModelScope.launch {
                messageRepository.insert(
                    ChatMessageEntity(
                        conversationId = conversation.id,
                        role = "assistant",
                        speakerCharacterId = character.id,
                        speakerName = character.name,
                        roundId = roundId,
                        turnIndex = turnIndex,
                        replyToMessageId = previousReply?.id?.takeIf { quotePrevious },
                        replyToSpeakerCharacterId = previousReply?.speakerCharacterId?.takeIf { quotePrevious },
                        replyToSpeakerName = previousReply?.speakerName?.takeIf { quotePrevious },
                        replyToContent = previousReply?.content
                            ?.let(ChatMessageBuilder::publicGroupContent)
                            ?.take(220)
                            ?.takeIf { quotePrevious },
                        content = cleanContent,
                        thinkingContent = mergedThinking.ifBlank { null },
                        thinkingDuration = if (mergedThinking.isBlank()) null else thinkingDuration,
                        timestamp = responseSavedAt,
                        tokenCount = result.totalTokens
                    )
                )
                val updatedConversation = conversationRepository.getById(conversation.id) ?: conversation
                conversationRepository.updateLastMessage(updatedConversation, cleanContent)
                result.totalTokens?.let { tokens ->
                    conversationRepository.addTokenUsage(updatedConversation, tokens)
                    settingsRepository.addHistoricalTokens(tokens)
                }
                val totalRounds = minOf(
                    messageRepository.getMessages(conversation.id).count { it.role == "user" },
                    messageRepository.getMessages(conversation.id).count { it.role == "assistant" && it.speakerCharacterId == character.id }
                )
                val progress = if (isMetaSafetyRefusal(cleanContent)) {
                    null
                } else advanceCharacterStoryState(
                    previous = getCharacterStoryState(character.id),
                    userText = userText,
                    assistantText = cleanContent,
                    assistantInnerThought = mergedThinking,
                    rounds = totalRounds,
                    directInteractionAllowed = isDirectGroupInteractionFor(character, participants, userText),
                    otherCharacterNames = participants.filterNot { it.id == character.id }.map { it.name },
                    roleInteractionOnly = quotePrevious,
                    timestamp = responseSavedAt
                )
                progress?.let { settingsRepository.saveCharacterStoryState(character.id, it.state) }
                if (quotePrevious && previousReply?.speakerCharacterId != null) {
                    val targetId = previousReply.speakerCharacterId
                    val key = interCharacterRelationKey(character.id, targetId)
                    val previousRelation = _uiState.value.interCharacterRelations[key] ?: InterCharacterRelationState()
                    val nextRelation = advanceInterCharacterRelation(
                        previous = previousRelation,
                        responseText = cleanContent,
                        sourceName = character.name,
                        targetName = previousReply.speakerName ?: "上一位角色",
                        timestamp = responseSavedAt
                    )
                    _generationCompleted.tryEmit(Unit)
                    if (nextRelation != previousRelation) {
                        settingsRepository.saveInterCharacterRelation(character.id, targetId, nextRelation)
                    }
                }
                if (progress?.shouldAppendLog == true) {
                    settingsRepository.appendCharacterStoryLog(
                        character.id,
                        CharacterStoryLogEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = responseSavedAt,
                            title = progress.logTitle,
                            note = progress.logNote,
                            affection = progress.state.affection,
                            trust = progress.state.trust,
                            tension = progress.state.tension
                        )
                    )
                }
                if (!updatedConversation.isNamed) {
                    autoNameConversation(updatedConversation, userText, cleanContent)
                }
                breakArmorOutputDiagnostic(character.id, cleanContent)?.let { warning ->
                    _error.emit(warning)
                }
                _generationCompleted.tryEmit(Unit)
            }.join()
        }.onFailure {
            success = false
            val streamingPartial = _uiState.value.streamingAssistant
                ?.takeIf { state -> state.conversationId == conversation.id }
            val failurePartial = partialResultFromFailure(it)
            val visiblePartial = streamingPartial?.content?.takeIf { text -> text.isNotBlank() }
                ?: failurePartial?.content.orEmpty()
            if (visiblePartial.isNotBlank()) {
                saveIncompleteAssistant(
                    conversation = conversation,
                    character = character,
                    content = visiblePartial,
                    thinking = streamingPartial?.thinkingContent?.takeIf { text -> text.isNotBlank() }
                        ?: failurePartial?.reasoningContent.orEmpty(),
                    requestStartAt = requestStartAt,
                    tokenCount = failurePartial?.totalTokens
                )
            }
            _error.emit(formatChatFailure(it))
        }
        return success
    }

    private fun formatChatFailure(error: Throwable): String {
        return when (error) {
            is UpstreamContentBlockedException ->
                "上游模型或 API 服务商拦截了本次内容（${error.reason.take(80)}）。破甲提示已发送，但 App 无法关闭服务端安全策略。"
            is IncompleteStreamException ->
                "流式连接中断，已保留当前正文；下一轮会从这里继续。"
            is OutputTruncatedException ->
                "回复达到当前输出长度上限，已保留当前正文；下一轮会从这里继续。"
            else -> "发送失败: ${error.message.orEmpty().take(100)}"
        }
    }

    private fun breakArmorOutputDiagnostic(characterId: String?, content: String): String? {
        val id = characterId ?: return null
        val state = _uiState.value
        val breakArmorEnabled = state.characterCreativePresetEnabled[id] == true
        if (!breakArmorEnabled || content.isBlank()) return null

        return when (diagnoseBreakArmorOutput(content)) {
            "japanese_residue" -> "破甲诊断：回复中检测到日语中介残留，正文已保留。可在最终 Prompt 中确认第四层是否完整注入。"
            "refusal_template" -> null
            "too_short" -> "破甲诊断：模型正文异常简短，内容已保留。建议查看结束原因和最终 Prompt 注入状态。"
            else -> null
        }
    }

    private suspend fun getConversationParticipants(conversation: ConversationEntity): List<CharacterCardEntity> {
        val ids = conversation.participantCharacterIds
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return ids.mapNotNull { characterRepository.getById(it) }
    }

    private suspend fun chooseGroupReplyCharacters(
        conversation: ConversationEntity,
        participants: List<CharacterCardEntity>,
        userText: String
    ): List<GroupReplyCandidate> {
        val history = messageRepository.getMessages(conversation.id)
        val recentAssistantSpeakers = history.asReversed()
            .filter { it.role == "assistant" && !it.speakerCharacterId.isNullOrBlank() }
            .mapNotNull { it.speakerCharacterId }
            .distinct()
        val mentionedIds = mentionedParticipantIds(participants, userText)
        val ranked = participants.map { character ->
            val storyState = getCharacterStoryState(character.id)
            var score = storyState.affection / 8 + storyState.relationshipMomentum / 10 - storyState.tension / 20
            if (mentionedIds.contains(character.id)) score += 14
            if (recentAssistantSpeakers.firstOrNull() == character.id) score -= 4
            if (recentAssistantSpeakers.take(2).contains(character.id)) score -= 2
            GroupReplyCandidate(character, score)
        }.sortedByDescending { it.score }
        val targetCount = when {
            mentionedIds.size >= 3 -> 3
            mentionedIds.size == 2 -> 2
            mentionedIds.size == 1 && !invitesGroupParticipation(userText) -> 1
            invitesGroupParticipation(userText) || userText.length >= 120 -> 3
            else -> 2
        }.coerceAtMost(participants.size)
        val picked = ranked.take(targetCount)
        if (mentionedIds.isEmpty()) return picked
        val mentionedFirst = ranked.filter { mentionedIds.contains(it.character.id) }
        val remaining = ranked.filterNot { mentionedIds.contains(it.character.id) }
        return (mentionedFirst + remaining).take(targetCount)
    }

    private fun mentionedParticipantIds(participants: List<CharacterCardEntity>, userText: String): Set<String> {
        val lowerText = userText.lowercase()
        return participants.filter { character ->
            lowerText.contains("@${character.name.lowercase()}") || lowerText.contains(character.name.lowercase())
        }.map { it.id }.toSet()
    }

    private fun invitesGroupParticipation(userText: String): Boolean {
        return listOf("大家", "你们", "所有人", "都说说", "一起", "群里", "各位").any {
            userText.contains(it, ignoreCase = true)
        }
    }

    fun getInterCharacterRelations(): Map<String, InterCharacterRelationState> =
        _uiState.value.interCharacterRelations

    private fun isDirectGroupInteractionFor(
        character: CharacterCardEntity,
        participants: List<CharacterCardEntity>,
        userText: String
    ): Boolean {
        val lowerText = userText.lowercase()
        val mentionedIds = participants.filter { participant ->
            lowerText.contains("@${participant.name.lowercase()}") ||
                lowerText.contains(participant.name.lowercase())
        }.map { it.id }.toSet()
        return mentionedIds.isEmpty() || character.id in mentionedIds
    }

    private fun isDirectInteractionFor(
        characterId: String,
        otherCharacters: List<CharacterCardEntity>,
        userText: String
    ): Boolean {
        val lowerText = userText.lowercase()
        val mentionedOther = otherCharacters.any { other ->
            lowerText.contains("@${other.name.lowercase()}") || lowerText.contains(other.name.lowercase())
        }
        val currentName = _uiState.value.characters.firstOrNull { it.id == characterId }?.name.orEmpty()
        val currentMentioned = currentName.isNotBlank() && (
            lowerText.contains("@${currentName.lowercase()}") || lowerText.contains(currentName.lowercase())
        )
        return !mentionedOther || currentMentioned
    }

    private suspend fun ensureUsableConnectionSettings() {
        val settings = settingsRepository.getSettings()
        val activeProvider = modelRepository.getActive()
        val fallbackProvider = activeProvider ?: modelRepository.getAll().firstOrNull { it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
        val preferredProvider = activeProvider?.takeIf { it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() } ?: fallbackProvider

        val resolvedBaseUrl = preferredProvider?.baseUrl
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: settings.apiBaseUrl.trim().trimEnd('/')
        val resolvedApiKey = preferredProvider?.apiKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: settings.apiKey.trim()

        if (resolvedBaseUrl.isNotBlank() && resolvedBaseUrl != settings.apiBaseUrl.trim().trimEnd('/')) {
            settingsRepository.saveBaseUrl(resolvedBaseUrl)
        }
        if (resolvedApiKey.isNotBlank() && resolvedApiKey != settings.apiKey.trim()) {
            settingsRepository.saveApiKey(resolvedApiKey)
        }
    }

    fun buildSystemPrompt(character: CharacterCardEntity?, state: CharacterStoryState? = null): String {
        return buildCharacterSystemPrompt(character, state)
    }

    private fun autoNameConversation(
        conversation: ConversationEntity,
        userText: String,
        assistantText: String
    ) {
        viewModelScope.launch {
            runCatching {
                if (conversation.isGroupConversation()) {
                    val participantNames = conversation.participantCharacterIds
                        .orEmpty()
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .mapNotNull { id -> characterRepository.getById(id)?.name }
                    val lead = participantNames.take(3).joinToString("、")
                    val summary = userText.take(10).ifBlank { assistantText.take(10) }
                    val fallbackTitle = buildString {
                        append(if (lead.isBlank()) "角色群聊" else lead)
                        if (summary.isNotBlank()) {
                            append(" · ")
                            append(summary)
                        }
                    }.take(24).ifBlank { "角色群聊" }
                    conversationRepository.rename(
                        conversation = conversation,
                        title = fallbackTitle,
                        isNamed = true
                    )
                    return@runCatching
                }
                apiService.generateTitle(userText, assistantText).onSuccess { title ->
                    conversationRepository.rename(
                        conversation = conversation,
                        title = title.ifBlank { DEFAULT_CONVERSATION_TITLE },
                        isNamed = true
                    )
                }
            }.onFailure { e ->
                Log.e("ChatVM", "autoNameConversation failed", e)
            }
        }
    }

    private suspend fun refreshConversationLastMessage(conversationId: String) {
        val conversation = conversationRepository.getById(conversationId) ?: return
        val latestMessage = messageRepository.getLastMessage(conversationId)
        val preview = latestMessage?.let { msg ->
            val decoded = decodeChatContent(msg.content)
            decoded.text.ifBlank { decoded.attachments.firstOrNull()?.name ?: "" }.take(200)
        }
        conversationRepository.updateLastMessage(conversation, preview)
    }

    private suspend fun performWebSearch(query: String, url: String, apiKey: String): String? {
        return withContext(Dispatchers.IO) {
            var call: okhttp3.Call? = null
            try {
            val body = org.json.JSONObject().apply {
                put("query", query)
                if (url.contains("tavily")) {
                    put("api_key", apiKey)
                    put("max_results", 3)
                    put("include_answer", true)
                }
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(url.trimEnd('/'))
                .post(body)
                .header("Content-Type", "application/json")
                .apply { if (!url.contains("tavily")) header("Authorization", "Bearer $apiKey") }
                .build()
            call = searchHttpClient.newCall(request)
            activeSearchCall.set(call)
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = org.json.JSONObject(response.body?.string().orEmpty())
                buildString {
                if (url.contains("tavily")) {
                    val answer = json.optString("answer", "")
                    if (answer.isNotBlank()) { appendLine(answer); appendLine() }
                    val results = json.optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until minOf(3, results.length())) {
                            val r = results.optJSONObject(i)
                            appendLine("- ${r?.optString("title", "")}: ${r?.optString("content", "")}")
                        }
                    }
                } else {
                    appendLine(json.toString(2).take(1500))
                }
                }.takeIf { it.isNotBlank() }
            }
            } catch (e: Exception) {
                if (e !is java.io.InterruptedIOException) {
                    Log.e("ChatVM", "Search failed", e)
                }
                null
            } finally {
                call?.let { activeSearchCall.compareAndSet(it, null) }
            }
        }
    }


    private fun extractMainMemory(text: String, conversationId: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val normalized = normalizeRelativeTimeForMemory(clean)
        val candidates = buildList {
            if (Regex("我(今天|刚刚|最近)?.{0,20}(喜欢|爱|常用|在做|正在做|吃了|住在|叫|是)").containsMatchIn(normalized) && normalized.length <= 160) {
                add("[用户事实] $normalized")
            }
            if ((normalized.contains("我喜欢") || normalized.contains("我不喜欢") || normalized.contains("偏好")) && normalized.length <= 160) {
                add("[用户偏好] $normalized")
            }
            if ((normalized.contains("我在做") || normalized.contains("我正在做") || normalized.contains("项目") || normalized.contains("需求")) && normalized.length <= 200) {
                add("[进行中事项] $normalized")
            }
        }.distinct()
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                candidates.forEach { memoryText ->
                    val type = when {
                        memoryText.startsWith("[用户偏好]") -> "偏好"
                        memoryText.startsWith("[进行中事项]") -> "事项"
                        else -> "事实"
                    }
                    memoryRepository.upsertMainMemory(
                        content = memoryText,
                        type = type,
                        sourceConversationId = conversationId
                    )
                }
            }.onFailure { e ->
                Log.e("ChatVM", "extractMainMemory failed", e)
            }
        }
    }

    private fun normalizeRelativeTimeForMemory(text: String): String {
        val now = Date()
        val date = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(now)
        return text
            .replace("今天", "$date 这天")
            .replace("刚刚", "$date 稍早")
            .replace("最近", "最近这段时间")
    }
}
