package com.hana.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hana.app.data.api.AttachmentService
import com.hana.app.data.api.ImageGenerationRequest
import com.hana.app.data.api.ImageGenerationService
import com.hana.app.data.repository.ModelRepository
import com.hana.app.data.settings.SettingsRepository
import com.hana.app.ui.chat.AttachmentKind
import com.hana.app.ui.chat.ChatAttachment
import com.hana.app.ui.image.ImagePromptForm
import com.hana.app.ui.image.ImagePromptTranslator
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ImageConversationItem(
    val id: String = UUID.randomUUID().toString(),
    val promptZh: String,
    val promptEn: String,
    val negativePromptEn: String,
    val negativePromptZh: String,
    val styleLabel: String,
    val aspectRatio: String,
    val batchCount: Int,
    val referenceImages: List<ChatAttachment> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ImageGenerationUiState(
    val messages: List<ImageConversationItem> = emptyList(),
    val input: String = "",
    val selectedStyle: String = "电影感插画",
    val selectedRatio: String = "1:1",
    val batchCount: Int = 1,
    val negativePromptZh: String = "",
    val isGenerating: Boolean = false,
    val generationProgressText: String = "",
    val referenceImages: List<ChatAttachment> = emptyList(),
    val imageModelName: String = "",
    val imageProviderName: String = "",
    val statusText: String = "请直接描述你想生成的图片",
    val draftSourceLabel: String = "",
    val lastPromptZh: String = "",
    val creativePresetEnabled: Boolean = false,
    val creativePresetSummary: String = ""
)

class ImageGenerationViewModel(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
    private val imageGenerationService: ImageGenerationService,
    private val attachmentService: AttachmentService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageGenerationUiState())
    val uiState: StateFlow<ImageGenerationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()
    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val provider = if (settings.imageProviderId > 0L && settings.imageModelName.isNotBlank()) {
                    modelRepository.getById(settings.imageProviderId)
                } else {
                    modelRepository.getActive()
                }
                _uiState.update {
                    it.copy(
                        imageModelName = settings.imageModelName.ifBlank { settings.selectedModel },
                        imageProviderName = if (settings.imageModelName.isBlank()) "跟随文本源" else (provider?.name ?: "当前文字服务商"),
                        negativePromptZh = it.negativePromptZh,
                        creativePresetEnabled = false,
                        creativePresetSummary = ""
                    )
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun onStyleChange(value: String) {
        _uiState.update { it.copy(selectedStyle = value) }
    }

    fun onRatioChange(value: String) {
        _uiState.update { it.copy(selectedRatio = value) }
    }

    fun onBatchCountChange(value: Int) {
        _uiState.update { it.copy(batchCount = value.coerceIn(1, 8)) }
    }

    fun onNegativePromptChange(value: String) {
        _uiState.update { it.copy(negativePromptZh = value) }
    }

    fun onReferenceImagesPicked(uris: List<Uri>) {
        viewModelScope.launch {
            val attachments = uris.mapNotNull { uri ->
                attachmentService.persistPickedUri(uri, "image_reference_${System.currentTimeMillis()}", AttachmentKind.IMAGE)
            }
            if (attachments.isNotEmpty()) {
                _uiState.update {
                    val merged = (it.referenceImages + attachments).distinctBy { attachment -> attachment.uri }.take(6)
                    it.copy(referenceImages = merged, statusText = "已添加 ${merged.size} 张参考图，可用于辅助改写提示词")
                }
            } else {
                _events.emit("参考图添加失败")
            }
        }
    }

    fun removeReferenceImage(uri: String) {
        _uiState.update {
            val updated = it.referenceImages.filterNot { attachment -> attachment.uri == uri }
            it.copy(referenceImages = updated, statusText = if (updated.isEmpty()) "参考图已移除" else "已保留 ${updated.size} 张参考图")
        }
    }

    fun clearReferenceImages() {
        _uiState.update { it.copy(referenceImages = emptyList(), statusText = "参考图已移除") }
    }

    fun generate() {
        val state = _uiState.value
        val rawInput = state.input.trim()
        if (rawInput.isBlank() || state.isGenerating) return

        activeGenerationJob = viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val referenceImageDataUrls = state.referenceImages.mapNotNull { attachmentService.asImageDataUrl(it) }
            val provider = if (settings.imageProviderId > 0L && settings.imageModelName.isNotBlank()) {
                modelRepository.getById(settings.imageProviderId)
            } else {
                modelRepository.getActive()
            }
            if (provider == null) {
                _events.emit("没有可用的生图服务商，请先去 API 管理里配置")
                return@launch
            }
            val modelName = settings.imageModelName.ifBlank { settings.selectedModel }
            if (modelName.isBlank()) {
                _events.emit("没有可用的生图模型，请先设置生图模型")
                return@launch
            }

            val form = ImagePromptForm(
                subject = buildString {
                    append(rawInput)
                    if (state.referenceImages.isNotEmpty()) append("，参考图风格对齐")
                },
                styleLabel = state.selectedStyle,
                batchCount = state.batchCount,
                aspectRatio = state.selectedRatio,
                negativePromptZh = state.negativePromptZh
            )
            val prompt = ImagePromptTranslator.build(form)
            val finalPrompt = prompt.englishPrompt
            Log.d(
                "ImageGenVM",
                buildString {
                    append("image prompt build: creativePresetLength=")
                    append(0)
                    append(", basePromptLength=")
                    append(prompt.englishPrompt.length)
                    append(", finalPromptLength=")
                    append(finalPrompt.length)
                    append(", model=")
                    append(modelName)
                    append(", provider=")
                    append(provider.name)
                }
            )
            val pending = ImageConversationItem(
                promptZh = rawInput,
                promptEn = finalPrompt,
                negativePromptEn = prompt.negativePromptEnglish,
                negativePromptZh = state.negativePromptZh,
                styleLabel = state.selectedStyle,
                aspectRatio = state.selectedRatio,
                batchCount = state.batchCount,
                referenceImages = state.referenceImages,
                isLoading = true
            )
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    generationProgressText = "正在请求模型...",
                    statusText = "正在调用 ${provider.name} / $modelName 生图...",
                    messages = it.messages + pending,
                    draftSourceLabel = "",
                    lastPromptZh = rawInput
                )
            }

            imageGenerationService.generate(
                provider = provider,
                requestData = ImageGenerationRequest(
                    prompt = finalPrompt,
                    negativePrompt = prompt.negativePromptEnglish,
                    model = modelName,
                    batchCount = state.batchCount,
                    aspectRatio = state.selectedRatio,
                    referenceImageDataUrls = referenceImageDataUrls
                )
            ).onSuccess { result ->
                val revisedPromptWithPreset = result.revisedPrompt
                val displayUrls = result.imageUrls.mapIndexedNotNull { index, url ->
                    val localUri = attachmentService.persistGeneratedImage(
                        url = url,
                        fallbackName = "generated_${System.currentTimeMillis()}_$index"
                    )?.uri
                    localUri ?: url.takeIf { it.isNotBlank() }
                }
                _uiState.update { current ->
                    current.copy(
                        isGenerating = false,
                        generationProgressText = "",
                        statusText = "已生成 ${displayUrls.size} 张图片",
                        messages = current.messages.map { msg ->
                            if (msg.id == pending.id) msg.copy(
                                isLoading = false,
                                imageUrls = displayUrls,
                                promptEn = revisedPromptWithPreset ?: msg.promptEn
                            ) else msg
                        }
                    )
                }
                if (displayUrls.isEmpty()) {
                    _events.emit("模型返回成功，但没有可展示的图片")
                } else if (displayUrls.size < result.imageUrls.size) {
                    _events.emit("部分图片未能完整缓存，本轮已尽量保留可显示结果")
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        isGenerating = false,
                        generationProgressText = "",
                        statusText = "生成失败",
                        messages = current.messages.map { msg ->
                            if (msg.id == pending.id) msg.copy(
                                isLoading = false,
                                errorMessage = throwable.message.orEmpty().ifBlank { "未知错误" }
                            ) else msg
                        }
                    )
                }
                _events.emit("生图失败: ${throwable.message.orEmpty()}")
            }
            activeGenerationJob = null
        }
    }

    fun stopGeneration() {
        imageGenerationService.cancelActiveGeneration()
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                generationProgressText = "",
                statusText = "已停止当前生图任务",
                messages = it.messages.map { item ->
                    if (item.isLoading) item.copy(isLoading = false, errorMessage = "已手动停止") else item
                }
            )
        }
    }

    fun saveImage(context: Context, url: String) {
        viewModelScope.launch {
            val saveResult = if (url.startsWith("file:") || url.startsWith("content:")) {
                attachmentService.saveImageToGallery(url)
            } else {
                imageGenerationService.saveImage(context, url)
            }
            saveResult
                .onSuccess { _events.emit("已保存到相册: $it") }
                .onFailure { _events.emit("保存失败: ${it.message.orEmpty()}") }
        }
    }

    fun reusePrompt(item: ImageConversationItem) {
        _uiState.update {
            it.copy(
                input = item.promptZh,
                selectedStyle = item.styleLabel,
                selectedRatio = item.aspectRatio,
                batchCount = item.batchCount,
                negativePromptZh = item.negativePromptZh,
                referenceImages = item.referenceImages,
                draftSourceLabel = "已载入上一轮提示词草稿",
                statusText = "你可以继续修改这次提示词后再生成"
            )
        }
    }

    fun continueFromImage(item: ImageConversationItem, imageUrl: String) {
        viewModelScope.launch {
            val attachment = attachmentService.persistGeneratedImage(
                url = imageUrl,
                fallbackName = "generated_reference_${System.currentTimeMillis()}"
            )
            if (attachment == null) {
                _events.emit("无法把生成图载入为参考图")
                return@launch
            }
            _uiState.update {
                it.copy(
                    input = item.promptZh,
                    selectedStyle = item.styleLabel,
                    selectedRatio = item.aspectRatio,
                    batchCount = item.batchCount,
                    negativePromptZh = item.negativePromptZh,
                    referenceImages = (item.referenceImages + attachment).distinctBy { img -> img.uri }.take(6),
                    draftSourceLabel = "已载入生成结果，可继续改图",
                    statusText = "当前已基于上一张结果图继续编辑"
                )
            }
            _events.emit("已把结果图设为新的参考图")
        }
    }

    /**
     * 场景捕捉生图：根据角色+对话上下文生成场景图
     * @param promptEn 英文提示词
     * @param onResult 回调：(imageUrls, errorMessage)
     */
    fun generateSceneImage(promptEn: String, onResult: (List<String>, String?) -> Unit) {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val provider = if (settings.imageProviderId > 0L && settings.imageModelName.isNotBlank()) {
                modelRepository.getById(settings.imageProviderId)
            } else {
                modelRepository.getActive()
            }
            if (provider == null) {
                onResult(emptyList(), "没有可用的生图服务商，请先去 API 管理里配置")
                return@launch
            }
            val modelName = settings.imageModelName.ifBlank { settings.selectedModel }
            if (modelName.isBlank()) {
                onResult(emptyList(), "没有可用的生图模型，请先设置生图模型")
                return@launch
            }

            val finalPrompt = promptEn

            imageGenerationService.generate(
                provider = provider,
                requestData = ImageGenerationRequest(
                    prompt = finalPrompt,
                    negativePrompt = "nsfw, low quality, blurry, distorted, bad anatomy, extra fingers, watermark",
                    model = modelName,
                    batchCount = 1,
                    aspectRatio = "3:4"
                )
            ).onSuccess { result ->
                val displayUrls = result.imageUrls.mapNotNull { url ->
                    attachmentService.persistGeneratedImage(
                        url = url,
                        fallbackName = "scene_${System.currentTimeMillis()}"
                    )?.uri ?: url.takeIf { it.isNotBlank() }
                }
                onResult(displayUrls, null)
            }.onFailure { throwable ->
                onResult(emptyList(), throwable.message.orEmpty().ifBlank { "生图失败" })
            }
        }
    }
}
