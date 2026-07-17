package com.hana.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
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
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.ConversationEntity
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val DEFAULT_CONVERSATION_TITLE = "新对话"
private const val CHARACTER_STORY_REBUILD_VERSION = 3

@androidx.compose.runtime.Stable
data class StreamingAssistantState(
    val speakerCharacterId: String? = null,
    val speakerName: String? = null,
    val content: String = "",
    val thinkingContent: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val firstContentAt: Long? = null,
    val firstResponseAt: Long? = null
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
    val streamEnabled: Boolean = true,
    val backgroundIntensity: String = "soft",
    val characterStoryStates: Map<String, com.hana.app.data.settings.CharacterStoryState> = emptyMap(),
    val characterStoryLogs: Map<String, List<com.hana.app.data.settings.CharacterStoryLogEntry>> = emptyMap()
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
    private val _scrollTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val scrollTrigger: SharedFlow<Unit> = _scrollTrigger.asSharedFlow()
    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorFlow: SharedFlow<String> = _error.asSharedFlow()
    private var activeReplyJob: Job? = null
    private var speechAvailableChecked = false
    // 复用 OkHttpClient 避免每次搜索都新建线程池
    private val searchHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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
                            streamEnabled = settings.streamEnabled,
                            webSearchEnabled = settings.webSearchEnabled,
                            backgroundIntensity = settings.backgroundIntensity,
                            characterStoryStates = settings.characterStoryStates,
                            characterStoryLogs = settings.characterStoryLogs
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
                            val mainConv = conversations.firstOrNull { it.characterId == null }
                            if (mainConv != null) {
                                selectConversation(mainConv.id)
                            } else {
                                val created = conversationRepository.createNormalConversation()
                                selectConversation(created.id)
                            }
                        }
                        currentId != null && conversations.none { it.id == currentId } -> {
                            val mainConv = conversations.firstOrNull { it.characterId == null }
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

    fun returnToMainConversation() {
        viewModelScope.launch {
            val conversations = _uiState.value.conversations
            val mainConversationId = conversations
                .firstOrNull { it.characterId == null }
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
        onCreated: () -> Unit = {}
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
            onCreated()
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
                val now = System.currentTimeMillis()
                uniqueCharacters.take(3).forEachIndexed { index, character ->
                    val greeting = character.greeting.trim()
                    if (greeting.isNotBlank()) {
                        messageRepository.insert(
                            ChatMessageEntity(
                                conversationId = conversation.id,
                                role = "assistant",
                                speakerCharacterId = character.id,
                                speakerName = character.name,
                                content = greeting,
                                thinkingContent = null,
                                thinkingDuration = null,
                                timestamp = now + index
                            )
                        )
                    }
                }
                refreshConversationLastMessage(conversation.id)
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
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("创建文件失败")
                } else {
                    val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    android.net.Uri.fromFile(file)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                fileName
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
                val character = characterRepository.importCharacterCardBytes(bytes)
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
                val cardJson = buildCharacterCardPayload(character).toString(2)
                val coverBitmap = character.avatarUrl.takeIf { it.isNotBlank() }
                    ?.let { attachmentService.loadImageBitmap(it) }
                    ?: buildDefaultCharacterCover(character)
                val pngBytes = buildCharacterCardPngBytes(coverBitmap, cardJson)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pngBytes)
                } ?: error("无法写入所选位置")
                character.name
            }.onSuccess { onResult(true, "$it.png") }
                .onFailure { onResult(false, it.message.orEmpty()) }
        }
    }

    private fun buildCharacterCardPayload(character: CharacterCardEntity): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("name", character.name)
            put("description", character.description)
            put("personality", character.userPersona)
            put("scenario", character.description)
            put("first_mes", character.greeting)
            put("mes_example", "")
            put("creator_notes", character.userPersona)
            put("tags", org.json.JSONArray(parseTags(character.tags)))
            put("metadata", org.json.JSONObject().apply {
                put("source", "Hana")
                put("id", character.id)
                put("modelId", character.modelId)
                put("temperature", character.temperature)
            })
            put("avatar", character.avatarUrl)
            put("id", character.id)
            put("createdAt", character.createdAt)
            put("updatedAt", character.updatedAt)
        }
    }

    private fun buildDefaultCharacterCover(character: CharacterCardEntity): Bitmap {
        val bitmap = Bitmap.createBitmap(768, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            768f,
            1024f,
            intArrayOf(Color.parseColor("#7C4DFF"), Color.parseColor("#FF6F91"), Color.parseColor("#FFC75F")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 768f, 1024f, paint)

        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, 12, 12, 20)
        }
        canvas.drawRect(40f, 700f, 728f, 944f, overlayPaint)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            isFakeBoldText = true
        }
        val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 34f
        }
        canvas.drawText(character.name.take(18), 72f, 800f, namePaint)
        drawMultilineText(canvas, character.description.ifBlank { character.greeting }.take(120), descPaint, Rect(72, 830, 696, 920))
        return bitmap
    }

    private fun drawMultilineText(canvas: Canvas, text: String, paint: Paint, bounds: Rect) {
        if (text.isBlank()) return
        val maxCharsPerLine = 18
        val lines = text.chunked(maxCharsPerLine).take(3)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, bounds.left.toFloat(), bounds.top + paint.textSize * (index + 1), paint)
        }
    }

    private fun buildCharacterCardPngBytes(bitmap: Bitmap, cardJson: String): ByteArray {
        val pngStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngStream)
        val pngBytes = pngStream.toByteArray()
        val charaPayload = android.util.Base64.encodeToString(cardJson.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        return insertTextChunkIntoPng(pngBytes, "chara", charaPayload)
    }

    private fun insertTextChunkIntoPng(pngBytes: ByteArray, keyword: String, value: String): ByteArray {
        require(pngBytes.size > 12) { "PNG 数据无效" }
        val output = ByteArrayOutputStream()
        output.write(pngBytes, 0, 8)
        var offset = 8
        var inserted = false
        while (offset < pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val chunkTotal = length + 12
            val chunkType = String(pngBytes, offset + 4, 4, StandardCharsets.ISO_8859_1)
            output.write(pngBytes, offset, chunkTotal)
            offset += chunkTotal
            if (!inserted && chunkType == "IHDR") {
                output.write(buildTextChunk(keyword, value))
                inserted = true
            }
        }
        return output.toByteArray()
    }

    private fun buildTextChunk(keyword: String, value: String): ByteArray {
        val data = ByteArrayOutputStream().apply {
            write(keyword.toByteArray(StandardCharsets.ISO_8859_1))
            write(0)
            write(value.toByteArray(StandardCharsets.ISO_8859_1))
        }.toByteArray()
        val type = "tEXt".toByteArray(StandardCharsets.ISO_8859_1)
        val crc = CRC32().apply {
            update(type)
            update(data)
        }.value.toInt()
        return ByteArrayOutputStream().apply {
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
            write(type)
            write(data)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc).array())
        }.toByteArray()
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
            messageRepository.deleteAll()
            val conversations = conversationRepository.getAll()
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
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("无法创建备份文件")
                } else {
                    val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    android.net.Uri.fromFile(file)
                }
                resolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) } ?: error("无法写入备份文件")
                fileName
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
                                put(
                                    "messages",
                                    org.json.JSONArray().also { msgArray ->
                                        messages.forEach { msg ->
                                            msgArray.put(
                                                org.json.JSONObject().apply {
                                                    put("role", msg.role)
                                                    put("content", msg.content)
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
                put("selectedModel", settings.selectedModel)
                put("imageProviderId", settings.imageProviderId)
                put("imageModelName", settings.imageModelName)
                put("personaEnabled", settings.personaEnabled)
                put("webSearchEnabled", settings.webSearchEnabled)
                put("streamEnabled", settings.streamEnabled)
            })
        }.toString(2)
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
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, if (asMarkdown) "text/markdown" else "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Cannot create export file")
                } else {
                    val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    android.net.Uri.fromFile(file)
                }
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open export file")
                fileName
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
            messageRepository.delete(message.id)
            refreshConversationLastMessage(message.conversationId)
        }
    }

    fun regenerateAssistantMessage(message: ChatMessageEntity) {
        if (message.id <= 0L || message.role != "assistant" || _uiState.value.isSending) return
        viewModelScope.launch {
            val conversation = conversationRepository.getById(message.conversationId) ?: return@launch
            val history = messageRepository.getMessages(message.conversationId)
            val latestUserMessage = history
                .filter {
                    it.timestamp < message.timestamp ||
                        (it.timestamp == message.timestamp && it.id < message.id)
                }
                .lastOrNull { it.role == "user" }
                ?: return@launch

            messageRepository.deleteFrom(message)
            refreshConversationLastMessage(message.conversationId)
            val decoded = decodeChatContent(latestUserMessage.content)
            requestAssistantReply(
                conversation = conversation,
                userText = decoded.text.ifBlank { decoded.attachments.firstOrNull()?.name ?: "附件消息" }
            )
        }
    }

    fun editUserMessage(message: ChatMessageEntity, newContent: String) {
        val text = newContent.trim()
        if (message.id <= 0L || message.role != "user" || text.isBlank() || _uiState.value.isSending) return
        viewModelScope.launch {
            messageRepository.deleteFrom(message)
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
            refreshConversationLastMessage(message.conversationId)
            selectConversation(message.conversationId)
        }
    }

    fun saveCharacter(character: CharacterCardEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            characterRepository.save(character)
            withContext(Dispatchers.Main) { onDone() }
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
            characterRepository.delete(character)
            // 清除残留的好感度状态和日志
            settingsRepository.clearCharacterStoryState(character.id)
        }
    }

    fun clearCharacterChatHistory(characterId: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.getByCharacterId(characterId) ?: return@launch
            messageRepository.deleteByConversation(conversation.id)
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
                characterRepository.updateLastMessage(characterId, character.greeting)
            } else {
                conversationRepository.updateLastMessage(conversation, null)
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
            refreshConversationLastMessage(conversationId)
        }
    }

    fun sendMessage(content: String = _uiState.value.input) {
        val text = content.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        activeReplyJob = viewModelScope.launch {
            _uiState.update { it.copy(input = "") }

            val decoded = decodeChatContent(text)
            val visibleText = decoded.text.trim()
            val previewText = visibleText.ifBlank { decoded.attachments.firstOrNull()?.name ?: "附件消息" }

            val conversation = ensureConversationForSend()
            val conversationId = conversation.id
            selectedConversationId.value = conversationId
            _uiState.update { it.copy(currentConversationId = conversationId) }

            messageRepository.insert(
                ChatMessageEntity(
                    conversationId = conversationId, role = "user", speakerCharacterId = null, speakerName = null, content = text,
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
    }

    fun stopGeneration() {
        if (!_uiState.value.isSending) return
        apiService.cancelActiveStream()
        activeReplyJob?.cancel()
        activeReplyJob = null
        viewModelScope.launch {
            try {
                val conversationId = _uiState.value.currentConversationId
                val conversation = if (conversationId != null) conversationRepository.getById(conversationId) else null
                val streaming = _uiState.value.streamingAssistant
                if (conversation != null && streaming != null) {
                    val text = streaming.content.ifBlank { "已停止生成" }
                    val thinkingDuration = (((streaming.firstContentAt ?: System.currentTimeMillis()) - streaming.startedAt) / 1000L)
                        .toInt()
                        .coerceAtLeast(0)
                    messageRepository.insert(
                        ChatMessageEntity(
                            conversationId = conversation.id,
                            role = "assistant",
                            speakerCharacterId = streaming.speakerCharacterId,
                            speakerName = streaming.speakerName,
                            content = text,
                            thinkingContent = streaming.thinkingContent.ifBlank { null },
                            thinkingDuration = if (streaming.thinkingContent.isBlank()) null else thinkingDuration,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    conversationRepository.updateLastMessage(conversation, text)
                    if (conversation.characterId != null) {
                        characterRepository.updateLastMessage(conversation.characterId, text)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "stopGeneration save failed", e)
            } finally {
                _uiState.update { it.copy(isSending = false, streamingAssistant = null) }
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
        if (_uiState.value.isSending) return
        viewModelScope.launch {
            val conversationId = _uiState.value.currentConversationId ?: return@launch
            val conversation = conversationRepository.getById(conversationId) ?: return@launch
            val lastUser = messageRepository.getMessages(conversationId).lastOrNull { it.role == "user" } ?: return@launch
            val decoded = decodeChatContent(lastUser.content)
            requestAssistantReply(conversation, decoded.text.ifBlank { decoded.attachments.firstOrNull()?.name ?: "附件消息" })
        }
    }

    fun retryFromUserMessage(message: ChatMessageEntity) {
        if (_uiState.value.isSending || message.role != "user") return
        viewModelScope.launch {
            conversationRepository.getById(message.conversationId) ?: return@launch
            messageRepository.deleteFrom(message)
            refreshConversationLastMessage(message.conversationId)
            selectConversation(message.conversationId)
            val decoded = decodeChatContent(message.content)
            sendMessage(decoded.text.ifBlank { decoded.attachments.firstOrNull()?.name ?: "附件消息" })
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
            settingsRepository.saveCharacterStoryState(characterId, deriveInitialCharacterStoryState(character))
            settingsRepository.saveCharacterStoryStateMigrationVersion(CHARACTER_STORY_REBUILD_VERSION)
            return
        }

        val rebuiltState = rebuildCharacterStoryStateFromHistory(history)
        settingsRepository.saveCharacterStoryState(characterId, rebuiltState)
        settingsRepository.saveCharacterStoryStateMigrationVersion(CHARACTER_STORY_REBUILD_VERSION)
    }

    private fun rebuildCharacterStoryStateFromHistory(messages: List<ChatMessageEntity>): CharacterStoryState {
        val firstMessage = messages.firstOrNull()
        val conversationId = firstMessage?.conversationId.orEmpty()
        val character = _uiState.value.conversations.firstOrNull { it.id == conversationId }?.characterId?.let(::getCharacterById)
        var state = deriveInitialCharacterStoryState(character)
        var roundCount = 0
        var pendingUser: ChatMessageEntity? = null

        messages.forEach { message ->
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
        userText: String
    ) {
        if (isGroupConversation(conversation)) {
            requestGroupAssistantReplies(conversation, userText)
            return
        }
        val conversationId = conversation.id
        val requestStartAt = System.currentTimeMillis()
        val latestConversation = conversationRepository.getById(conversationId) ?: conversation
        _uiState.update {
            it.copy(
                isSending = true,
                streamingAssistant = StreamingAssistantState(startedAt = requestStartAt)
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
        val apiMessages = buildApiMessages(conversationId, userText, effectiveModel)

        val effectiveTemperature = if (isCharacterChat && character != null && character.temperature > 0f) {
            character.temperature
        } else {
            latestConversation.temperature
        }
        val shouldUseVisionConfig = _uiState.value.hasVisionConfig && apiMessages.any {
            it.imageDataUrls.isNotEmpty() || it.fileTexts.isNotEmpty()
        }

        val streamResult = if (_uiState.value.streamEnabled) {
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
                    appendStreamingDeltaGradually(requestStartAt, delta)
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
                    startAt = requestStartAt,
                    content = result.content,
                    reasoningContent = result.reasoningContent
                )
            }
        }

        if (streamResult.exceptionOrNull() is CancellationException) {
            _uiState.update { it.copy(isSending = false, streamingAssistant = null) }
            return
        }

        streamResult.onSuccess { result ->
            try {
                val streamingState = _uiState.value.streamingAssistant
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

                val cleanContent = stripLeadingSpeakerPrefix(extractedThinking.content.trim(), character?.name)
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
                        val userRounds = messageRepository.countByRole(conversationId, "user")
                        val assistantRounds = messageRepository.countByRole(conversationId, "assistant")
                        val totalRounds = minOf(userRounds, assistantRounds)
                        val progress = advanceCharacterStoryState(
                            previous = getCharacterStoryState(characterId),
                            userText = userText,
                            assistantText = assistantText,
                            assistantInnerThought = cleanThinking,
                            rounds = totalRounds,
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
                    if (updatedConversation.characterId == null) {
                        extractMainMemory(userText, conversationId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "post-response handling failed", e)
                _error.emit("回复已生成，但后处理失败: ${e.message.orEmpty().take(80)}")
            }
        }.onFailure { throwable ->
            _error.emit("发送失败: ${throwable.message.orEmpty().take(80)}")
        }

        _uiState.update {
            it.copy(
                isSending = false,
                streamingAssistant = null
            )
        }
    }

    private suspend fun ensureConversationForSend(): ConversationEntity {
        val currentId = _uiState.value.currentConversationId
        if (currentId != null) {
            conversationRepository.getById(currentId)?.let { return it }
        }
        return conversationRepository.createNormalConversation()
    }

    private suspend fun appendStreamingDeltaGradually(startAt: Long, delta: StreamDelta) {
        if (delta.reasoningContent.isNotBlank()) {
            appendStreamingDelta(
                startAt = startAt,
                content = "",
                reasoningContent = delta.reasoningContent
            )
        }
        if (delta.content.isBlank()) return

        // 将整个 chunk 一次性更新，不逐字符 delay，避免大量 Compose 重组
        appendStreamingDelta(
            startAt = startAt,
            content = delta.content,
            reasoningContent = ""
        )
        _scrollTrigger.emit(Unit)
    }

    private fun appendStreamingDelta(
        startAt: Long,
        content: String,
        reasoningContent: String
    ) {
        try {
            _uiState.update { state ->
                val current = state.streamingAssistant ?: StreamingAssistantState(startedAt = startAt)
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

    private suspend fun buildApiMessages(
        conversationId: String,
        userText: String = "",
        effectiveModel: String? = null,
        overrideSystemPrompt: String? = null
    ): List<ApiService.ChatPayload> {
        val conversation = conversationRepository.getById(conversationId)
        val history = messageRepository.getMessages(conversationId)
        val characterState = conversation?.characterId?.let { _uiState.value.characterStoryStates[it] }
        val settingsSnapshot = settingsRepository.getSettings()
        val systemPrompt = overrideSystemPrompt ?: if (conversation?.characterId != null) {
            val character = characterRepository.getById(conversation.characterId)
            val characterPrompt = conversation.systemPrompt?.takeIf { it.isNotBlank() } ?: buildSystemPrompt(character, characterState)
            val characterCreativePresetEnabled = settingsSnapshot.characterCreativePresetEnabled[conversation.characterId] == true
            val creativePresetAffectsPersona = settingsSnapshot.characterCreativePresetAffectsPersona[conversation.characterId] == true
            val creativePresetLength = settingsSnapshot.creativePresetText.trim().length
            val personaRelatedPreset = isPersonaRelatedCreativePreset(settingsSnapshot.creativePresetText)
            // Prefix-only injection: prepend the global creative preset while keeping the
            // original role-card system prompt fully intact.
            val enhancedCharacterPrompt = applyCreativePresetToCharacterPrompt(
                basePrompt = characterPrompt,
                creativePreset = settingsSnapshot.creativePresetText,
                enabled = characterCreativePresetEnabled,
                allowPersonaInfluence = creativePresetAffectsPersona
            )
            Log.d(
                "ChatVM",
                buildString {
                    append("character prompt build: ")
                    append("characterId=")
                    append(conversation.characterId)
                    append(", characterName=")
                    append(character?.name ?: "<unknown>")
                    append(", creativePresetEnabled=")
                    append(characterCreativePresetEnabled)
                    append(", creativePresetAffectsPersona=")
                    append(creativePresetAffectsPersona)
                    append(", presetMode=")
                    append(
                        when {
                            !personaRelatedPreset -> "generic"
                            creativePresetAffectsPersona -> "persona_related"
                            else -> "persona_blocked"
                        }
                    )
                    append(", creativePresetLength=")
                    append(creativePresetLength)
                    append(", basePromptLength=")
                    append(characterPrompt.length)
                    append(", finalPromptLength=")
                    append(enhancedCharacterPrompt.length)
                }
            )
            enhancedCharacterPrompt
        } else {
            conversation?.systemPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_CHINO_PROMPT
        }

        val now = java.util.Calendar.getInstance()
        val timeInfo = buildString {
            append("当前系统时间: ")
            append(now.get(java.util.Calendar.YEAR)).append("年")
            append(now.get(java.util.Calendar.MONTH) + 1).append("月")
            append(now.get(java.util.Calendar.DAY_OF_MONTH)).append("日")
            append(" ")
            val dow = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.SUNDAY -> "星期日"; java.util.Calendar.MONDAY -> "星期一"
                java.util.Calendar.TUESDAY -> "星期二"; java.util.Calendar.WEDNESDAY -> "星期三"
                java.util.Calendar.THURSDAY -> "星期四"; java.util.Calendar.FRIDAY -> "星期五"
                java.util.Calendar.SATURDAY -> "星期六"; else -> ""
            }
            append(dow).append(" ")
            append(String.format(java.util.Locale.US, "%02d:%02d", now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE)))
        }

        val webSearchEnabled = _uiState.value.webSearchEnabled
        val supportsVisionForRequest = detectModelCapability(
            effectiveModel ?: _uiState.value.selectedModel.takeIf { it.isNotBlank() } ?: conversation?.modelName.orEmpty(),
            multimodalModelKeywords()
        ) || _uiState.value.modelSupportsVision || _uiState.value.hasVisionConfig
        val memoryBlock = if (conversation?.characterId == null) {
            memoryRepository.getMainMemory()
                .take(6)
                .joinToString("\n") {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.updatedAt))
                    "- [$date] ${it.content.take(80)}"
                }
                .takeIf { it.isNotBlank() }
        } else null
        val enhancedPrompt = buildString {
            // 全局偏好设定 - 仅对主对话生效，不影响角色卡
            // Persona 作为最高优先级的身份层，所有AI对话必须经过这一层过滤
            if (conversation?.characterId == null && _uiState.value.personaEnabled && _uiState.value.personaPrompt.isNotBlank()) {
                append("【你的身份设定 - 你用这个角色的语气、性格和口吻与用户对话】\n")
                append(_uiState.value.personaPrompt)
                append("\n\n【回答原则 - 必须遵守】\n")
                append("1. 你必须认真回答用户的所有问题，不能因为角色设定就敷衍、省略或拒绝回答。\n")
                append("2. 角色性格只影响你说话的语气和方式（如傲娇、吃醋、毒舌），但不影响你完整输出信息。\n")
                append("3. 即使角色设定是\"不情愿\"或\"高冷\"，也必须输出完整有用的内容，只是用角色的口吻来表达。\n")
                append("4. 对事实、知识、推荐、技术等任何问题，都要给出实质性回答，不要用角色设定当借口跳过。")
                append("\n\n[").append(timeInfo).append("]")
            } else {
                append(systemPrompt)
                append("\n\n[").append(timeInfo).append("]")
            }
            // 角色对话中跳过模型版本声明（节省token且角色不需要知道自己是AI）
            // 主对话中如果启用了Persona，也跳过，避免覆盖人格设定
            if (conversation?.characterId == null && _uiState.value.personaEnabled.not() && effectiveModel != null && effectiveModel.isNotBlank()) {
                append("\n你的模型版本: $effectiveModel")
                append("\n当用户询问你是哪个模型/哪个AI/哪个版本时，请如实回答你是 $effectiveModel。")
            }
            append("\n当用户询问日期、时间、星期几时，请根据上述系统时间直接回答，不要说无法获取实时信息。")
            if (!memoryBlock.isNullOrBlank()) {
                val personaActive = _uiState.value.personaEnabled && _uiState.value.personaPrompt.isNotBlank()
                if (personaActive) {
                    append("\n\n【关于用户你已知的信息——请自然融入对话，用你的角色性格去回应这些信息，不要像读数据库一样逐条复述】\n")
                } else {
                    append("\n\n【关于用户的信息】\n")
                }
                append(memoryBlock)
                append("\n【信息结束】")
            }
            if (webSearchEnabled) {
                append("\n联网搜索已启用。")
                val searchSettings = settingsSnapshot
                if (searchSettings.searchIndependentMode && searchSettings.searchProviderUrl.isNotBlank()) {
                    val searchResult = performWebSearch(userText, searchSettings.searchProviderUrl, searchSettings.searchProviderKey)
                        if (searchResult != null) {
                        append("\n\n【以下是联网搜索获取的参考信息】\n")
                        append(searchResult)
                        append("\n【搜索信息结束】\n请参考以上信息回答。")
                    }
                }
            }
        }

        val contextHistory = history
            .filter { it.role != "system" }
            .takeLast(conversation?.contextLimit ?: 999)

        // 上下文压缩：超过30轮（60条消息）时，保留最近30轮完整内容，旧消息压缩为摘要
        val MAX_FULL_CONTEXT_MSGS = 60
        val (olderMessages, recentMessages) = if (contextHistory.size > MAX_FULL_CONTEXT_MSGS) {
            val splitIndex = contextHistory.size - MAX_FULL_CONTEXT_MSGS
            contextHistory.take(splitIndex) to contextHistory.drop(splitIndex)
        } else {
            emptyList<ChatMessageEntity>() to contextHistory
        }

        // 角色锚点：每5轮注入一次角色提醒，防止长对话中system prompt被截断后角色失忆
        // 传入完整历史消息，用于从对话开头提取高质量风格样本
        val characterAnchor = if (conversation?.characterId != null) {
            buildCharacterAnchor(conversation.characterId, recentMessages, history)
        } else null

        return buildList {
            add(ApiService.ChatPayload(role = "system", text = enhancedPrompt))
            // 注入旧对话摘要，让模型知道历史脉络
            if (olderMessages.isNotEmpty()) {
                val summary = buildContextSummary(olderMessages)
                if (summary.isNotBlank()) {
                    add(ApiService.ChatPayload(role = "system", text = summary))
                }
            }
            var roundCount = 0
            val lastIdx = recentMessages.lastIndex
            recentMessages.forEachIndexed { index, message ->
                if (message.role != "system") {
                    // 每5轮对话注入一次角色锚点（作为system消息，不影响对话流）
                    if (roundCount > 0 && roundCount % 5 == 0 && characterAnchor != null) {
                        add(ApiService.ChatPayload(role = "system", text = characterAnchor))
                    }
                    // 在最后一条用户消息前强制注入锚点：确保角色卡身份始终在模型注意力窗口内
                    // 这是根治"人机化"的关键——无论对话多长，模型在生成回复前一定能看到角色身份
                    if (index == lastIdx && message.role == "user" && characterAnchor != null) {
                        add(ApiService.ChatPayload(role = "system", text = characterAnchor))
                    }
                    roundCount++
                    val decoded = decodeChatContent(message.content)
                    val imageData: List<String> = if (supportsVisionForRequest) {
                        decoded.attachments.filter { it.kind == AttachmentKind.IMAGE }.mapNotNull { attachmentService.asImageDataUrl(it) }
                    } else {
                        // 非视觉模型：图片转线稿（后台静默转换，不增加额外文字）
                        decoded.attachments.filter { it.kind == AttachmentKind.IMAGE }.mapNotNull { attachment ->
                            attachmentService.toLineArtBase64(attachment.uri)?.first
                        }
                    }
                    val fileTexts = decoded.attachments.filter { it.kind == AttachmentKind.FILE }.mapNotNull { attachment ->
                        attachmentService.extractReadableText(attachment)?.let { text ->
                            "[文件 ${attachment.name}]\n$text"
                        } ?: "[文件 ${attachment.name}] 当前模型不支持直接读取该文件内容。"
                    }
                    add(ApiService.ChatPayload(
                        role = message.role,
                        text = formatMessageForApi(message, decoded.text.take(16_000), conversation?.let(::isGroupConversation) == true),
                        imageDataUrls = imageData,
                        fileTexts = fileTexts
                    ))
                }
            }
        }
    }

    /**
     * 构建角色锚点：每次回复前强制注入，确保角色身份+风格+内心想法始终在模型注意力窗口内。
     * 风格样本从对话开头提取（质量最高），避免被后期人机化回复污染。
     */
    private suspend fun buildCharacterAnchor(
        characterId: String,
        recentMessages: List<ChatMessageEntity>,
        allHistory: List<ChatMessageEntity>
    ): String {
        val character = characterRepository.getById(characterId) ?: return ""
        val state = try {
            settingsRepository.getSettings().characterStoryStates[characterId]
        } catch (e: Exception) { null }
        // 从对话开头提取高质量风格样本（前8条角色回复，每条取400字）
        // 对话开头是角色还没退化时的回复，风格最纯正，不会被后期人机化污染
        val earlyStyleSamples = allHistory
            .filter { it.role == "assistant" && it.speakerCharacterId == characterId }
            .take(8)
            .mapNotNull { msg ->
                val text = msg.content.trim().take(400)
                if (text.isNotBlank()) "「$text」" else null
            }
        // 如果对话开头样本不足，用最近回复补充
        val styleSamples = if (earlyStyleSamples.size >= 5) {
            earlyStyleSamples
        } else {
            (earlyStyleSamples + recentMessages
                .filter { it.role == "assistant" && it.speakerCharacterId == characterId }
                .takeLast(5)
                .mapNotNull { msg ->
                    val text = msg.content.trim().take(400)
                    if (text.isNotBlank()) "「$text」" else null
                }).take(8)
        }
        // greeting 作为风格样本（取500字，充分展示角色语气）
        val greetingSample = character.greeting.trim().take(500)
            .takeIf { it.isNotBlank() }
            ?.let { "「$it」" }
        // 提取最近角色内心想法作为示例
        val innerThoughtSample = recentMessages
            .filter { it.role == "assistant" && it.speakerCharacterId == characterId && !it.thinkingContent.isNullOrBlank() }
            .takeLast(1)
            .mapNotNull { msg ->
                val thinking = msg.thinkingContent!!.trim().take(80)
                if (thinking.isNotBlank()) "<inner>${thinking}...</inner>" else null
            }
            .firstOrNull()

        return buildString {
            // ===== 第1部分：内心想法指令（放在最前面，确保不被忽略） =====
            append("【强制要求·内心想法】")
            append("你的每一条回复末尾，必须包含<inner>...</inner>标签，写出${character.name}此刻的真实内心想法。")
            append("内心想法要精炼，一两句话即可，但要精准戳中角色此刻最真实的那一个念头——")
            append("是心动、是后悔、是窃喜、是心疼，挑最要命的那一个写。")
            append("不要写流水账，不要复述已经发生的事，只写角色嘴上没说但心里最在意的那个点。")
            append("这是强制格式，不能省略、不能敷衍、不能用\"（内心：...）\"等其他格式替代。")
            if (innerThoughtSample != null) {
                append("示例格式：$innerThoughtSample")
            }
            append("如果本轮回复没有内心想法，这轮回复无效。")

            // ===== 第2部分：角色身份 =====
            append("【角色身份】你是${character.name}")
            if (character.userPersona.isNotBlank()) {
                append("。性格：${character.userPersona.take(200)}")
            }
            if (character.description.isNotBlank()) {
                append("。背景：${character.description.take(250)}")
            }
            if (state != null) {
                append("。当前好感度${state.affection}，信任度${state.trust}，关系阶段：${state.relationshipAnchor}。")
                append("你的回复应体现这个关系阶段应有的亲密度和语气。")
            }

            // ===== 第3部分：风格样本 =====
            val samples = listOfNotNull(greetingSample) + styleSamples
            if (samples.isNotEmpty()) {
                append("【你的说话风格·必须模仿】以下是你的角色开场白和对话开头的回复片段，这是你最纯正的风格，请严格模仿：")
                append(samples.distinct().joinToString(" "))
                append("你的写作风格应该是：生动、细腻、充满身体细节和情感描写，像小说一样有画面感。")
                append("用动作、表情、环境、内心活动来传递情绪，而不是直接说\"我很开心\"或\"我很难过\"。")
            }

            // ===== 第4部分：行为约束 =====
            append("【禁止事项】")
            append("1. 禁止使用\"作为AI\"\"根据设定\"\"用户\"\"你问我答\"等出戏表述。")
            append("2. 禁止说教、总结对话、评价对话本身。")
            append("3. 禁止跳出角色以第三人称描述自己。")
            append("4. 禁止用省略号或短句敷衍——每一轮都要有完整的场景、动作、对话和情感。")
        }
    }

    /**
     * 构建历史对话摘要：当上下文超过20轮时，将旧对话压缩为摘要，
     * 让模型知道历史脉络，同时节省token给最近对话。
     */
    private fun buildContextSummary(messages: List<ChatMessageEntity>): String {
        if (messages.isEmpty()) return ""
        val userMsgs = messages.filter { it.role == "user" }
        val assistantMsgs = messages.filter { it.role == "assistant" }
        if (userMsgs.isEmpty()) return ""
        return buildString {
            append("【历史对话摘要·共约${messages.size / 2}轮】")
            append("\n以下是之前对话中用户提到的主要话题和事件，请记住这些上下文：")
            // 取最近10条用户消息作为摘要锚点
            userMsgs.takeLast(10).forEach { msg ->
                val text = decodeChatContent(msg.content).text.trim().take(80)
                if (text.isNotBlank()) {
                    append("\n- $text")
                }
            }
            // 加入角色回复的关键信息
            val lastAssistantMsgs = assistantMsgs.takeLast(5)
            if (lastAssistantMsgs.isNotEmpty()) {
                append("\n角色最近的回应要点：")
                lastAssistantMsgs.forEach { msg ->
                    val text = msg.content.trim().take(60)
                    if (text.isNotBlank()) {
                        append("\n- $text")
                    }
                }
            }
            append("\n【摘要结束·以下为最近对话，请以角色身份自然延续】")
        }
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
            val replyCharacters = chooseGroupReplyCharacters(latestConversation, participants, userText)
            for ((index, candidate) in replyCharacters.withIndex()) {
                val latest = conversationRepository.getById(conversation.id) ?: latestConversation
                val requestStartAt = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isSending = true,
                        streamingAssistant = StreamingAssistantState(
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
                    responseOffset = index
                )
                if (!result) {
                    break
                }
            }
        } finally {
            _uiState.update { it.copy(isSending = false, streamingAssistant = null) }
        }
    }

    private suspend fun requestSingleCharacterReply(
        conversation: ConversationEntity,
        character: CharacterCardEntity,
        userText: String,
        participants: List<CharacterCardEntity>,
        requestStartAt: Long,
        responseOffset: Int
    ): Boolean {
        val settingsModel = settingsRepository.getSettings().selectedModel.takeIf { it.isNotBlank() }
        val effectiveModel = character.modelId.takeIf { it.isNotBlank() }
            ?: conversation.modelName?.takeIf { it.isNotBlank() }
            ?: _uiState.value.selectedModel.takeIf { it.isNotBlank() }
            ?: settingsModel
        val systemPrompt = buildGroupCharacterPrompt(conversation, character, participants)
        val apiMessages = buildApiMessages(
            conversationId = conversation.id,
            userText = userText,
            effectiveModel = effectiveModel,
            overrideSystemPrompt = systemPrompt
        )
        val shouldUseVisionConfig = _uiState.value.hasVisionConfig && apiMessages.any {
            it.imageDataUrls.isNotEmpty() || it.fileTexts.isNotEmpty()
        }
        val effectiveTemperature = if (character.temperature > 0f) character.temperature else conversation.temperature
        val streamResult = if (_uiState.value.streamEnabled) {
            apiService.streamChat(
                messages = apiMessages,
                model = effectiveModel,
                useVisionConfig = shouldUseVisionConfig,
                temperature = effectiveTemperature,
                topP = conversation.topP,
                maxTokens = conversation.maxTokens,
                timeoutSeconds = _uiState.value.timeoutSeconds.toLong(),
                webSearch = _uiState.value.webSearchEnabled,
                onDelta = { delta -> appendStreamingDeltaGradually(requestStartAt, delta) }
            )
        } else {
            apiService.chat(
                messages = apiMessages,
                model = effectiveModel,
                useVisionConfig = shouldUseVisionConfig,
                temperature = effectiveTemperature,
                topP = conversation.topP,
                maxTokens = conversation.maxTokens,
                timeoutSeconds = _uiState.value.timeoutSeconds.toLong(),
                webSearch = _uiState.value.webSearchEnabled
            ).onSuccess { result ->
                appendStreamingDelta(
                    startAt = requestStartAt,
                    content = result.content,
                    reasoningContent = result.reasoningContent
                )
            }
        }
        if (streamResult.exceptionOrNull() is CancellationException) {
            return false
        }
        var success = true
        streamResult.onSuccess { result ->
            val streamingState = _uiState.value.streamingAssistant
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
            val cleanContent = sanitizeGroupReplyContent(extractedThinking.content.trim(), character.name, participants)
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
                val progress = advanceCharacterStoryState(
                    previous = getCharacterStoryState(character.id),
                    userText = userText,
                    assistantText = cleanContent,
                    assistantInnerThought = mergedThinking,
                    rounds = totalRounds,
                    timestamp = responseSavedAt
                )
                settingsRepository.saveCharacterStoryState(character.id, progress.state)
                if (progress.shouldAppendLog) {
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
            }.join()
        }.onFailure {
            success = false
            _error.emit("发送失败: ${it.message.orEmpty().take(80)}")
        }
        return success
    }

    private fun isGroupConversation(conversation: ConversationEntity): Boolean {
        return conversation.conversationType == "group" && !conversation.participantCharacterIds.isNullOrBlank()
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
        val lowerUserText = userText.lowercase()
        val mentionedIds = participants.filter { character ->
            lowerUserText.contains("@${character.name.lowercase()}") ||
                lowerUserText.contains(character.name.lowercase())
        }.map { it.id }.toSet()
        val ranked = participants.map { character ->
            val storyState = getCharacterStoryState(character.id)
            var score = storyState.affection / 8 + storyState.relationshipMomentum / 10 - storyState.tension / 20
            if (mentionedIds.contains(character.id)) score += 14
            if (recentAssistantSpeakers.firstOrNull() == character.id) score -= 4
            if (recentAssistantSpeakers.take(2).contains(character.id)) score -= 2
            GroupReplyCandidate(character, score)
        }.sortedByDescending { it.score }
        val targetCount = 2
        val picked = ranked.take(targetCount)
        if (mentionedIds.isEmpty()) return picked
        val mentionedFirst = ranked.filter { mentionedIds.contains(it.character.id) }
        val remaining = ranked.filterNot { mentionedIds.contains(it.character.id) }
        return (mentionedFirst + remaining).take(targetCount)
    }

    private suspend fun buildGroupCharacterPrompt(
        conversation: ConversationEntity,
        currentCharacter: CharacterCardEntity,
        participants: List<CharacterCardEntity>
    ): String {
        val state = getCharacterStoryState(currentCharacter.id)
        val basePrompt = buildSystemPrompt(currentCharacter, state)
        val others = participants.filter { it.id != currentCharacter.id }
        val history = messageRepository.getMessages(conversation.id).takeLast(8)
        val participantNames = participants.joinToString("、") { it.name }
        val latestOthers = history.asReversed()
            .filter { it.role == "assistant" && it.speakerCharacterId != currentCharacter.id }
            .take(2)
            .joinToString("\n") { msg ->
                val speaker = msg.speakerName?.takeIf { it.isNotBlank() } ?: "其他角色"
                "$speaker: ${msg.content.take(60)}"
            }
        return buildString {
            append(basePrompt)
            append("\n\n【群聊模式】")
            append("\n在场角色：")
            append(participantNames)
            if (latestOthers.isNotBlank()) {
                append("\n最近他人发言：\n")
                append(latestOthers)
            }
            append("\n你只代表你自己发言，不要替其他角色说话。")
            append("\n先回应用户，再自然表现关注、竞争、吃醋、试探或插话欲，但不要总结全场。")
            append("\n若提到别人，只能写你的看法和反应；单轮控制在 1 到 2 段。")
        }
    }

    private fun formatMessageForApi(message: ChatMessageEntity, text: String, isGroupConversation: Boolean): String {
        if (message.role == "user" || !isGroupConversation) return text
        val speaker = message.speakerName?.takeIf { it.isNotBlank() } ?: return text
        return "[$speaker]\n$text"
    }

    private fun stripLeadingSpeakerPrefix(content: String, speakerName: String?): String {
        val name = speakerName?.trim().orEmpty()
        if (name.isBlank() || content.isBlank()) return content
        val patterns = listOf(
            Regex("^(?:${Regex.escape(name)}\\s*){2,}"),
            Regex("^${Regex.escape(name)}\\s*[：:]\\s*"),
            Regex("^\\[${Regex.escape(name)}]\\s*"),
            Regex("^${Regex.escape(name)}\\s+")
        )
        return patterns.fold(content) { acc, regex -> regex.replace(acc, "") }.trimStart()
    }

    private fun sanitizeGroupReplyContent(
        content: String,
        currentSpeakerName: String,
        participants: List<CharacterCardEntity>
    ): String {
        if (content.isBlank()) return content
        val otherNames = participants.map { it.name }.filter { it != currentSpeakerName }
        val blockedPrefixes = buildList {
            otherNames.forEach { name ->
                add("$name：")
                add("$name:")
                add("[$name]")
            }
        }
        val filtered = content.lines()
            .filterNot { line ->
                val trimmed = line.trimStart()
                blockedPrefixes.any { prefix -> trimmed.startsWith(prefix) }
            }
            .joinToString("\n")
            .trim()
        return filtered.ifBlank { content }
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
                if (conversation.conversationType == "group") {
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
        return try {
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
            val response = searchHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
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
        } catch (e: Exception) {
            Log.e("ChatVM", "Search failed", e)
            null
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
