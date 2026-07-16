package com.hana.app.ui.chat

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.hana.app.R
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.ConversationEntity
import com.hana.app.data.db.entity.ModelInfo
import com.hana.app.ui.character.CharacterAvatar
import com.hana.app.ui.chat.AttachmentKind.FILE
import com.hana.app.ui.chat.AttachmentKind.IMAGE
import com.hana.app.ui.settings.detectNativeWebSearchSupport
import com.hana.app.viewmodel.ChatUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import java.util.Locale

private const val PAGE_SIZE = 20

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendText: (String) -> Unit,
    onDeleteMessage: (ChatMessageEntity) -> Unit,
    onRegenerateMessage: (ChatMessageEntity) -> Unit,
    onEditMessage: (ChatMessageEntity, String) -> Unit,
    onEditMessageToInput: (ChatMessageEntity) -> Unit = {},
    onRetryFromMessage: (ChatMessageEntity) -> Unit = {},
    onPersistImageAttachment: (Uri, (ChatAttachment?) -> Unit) -> Unit = { _, _ -> },
    onPersistFileAttachment: (Uri, (ChatAttachment?) -> Unit) -> Unit = { _, _ -> },
    onPersistCameraAttachment: (Bitmap, (ChatAttachment?) -> Unit) -> Unit = { _, _ -> },
    onSaveAttachmentImage: (String) -> Unit = {},
    onStopGeneration: () -> Unit,
    onRetryLastUserMessage: () -> Unit,
    onUpdateConversationParameters: (String?, Float, Float, Int, Int, String?) -> Unit,
    onUpdateSystemPrompt: (String, String?) -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleFavorite: (ChatMessageEntity) -> Unit,
    backgroundIntensity: String = "soft",
    onSelectModel: (ModelInfo) -> Unit = {},
    onToggleModelFavorite: (String) -> Unit = {},
    onScrollTrigger: kotlinx.coroutines.flow.SharedFlow<Unit>? = null,
    errorFlow: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelPicker by remember { mutableStateOf(false) }
    var inputBarHeightPx by remember { mutableStateOf(0) }
    var loadedMessageCount by remember(uiState.currentConversationId) { mutableStateOf(PAGE_SIZE) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val hasMoreHistory = uiState.messages.size > loadedMessageCount
    val currentConversation = uiState.conversations.firstOrNull { it.id == uiState.currentConversationId }
    val characterAvatarById = remember(uiState.characters) {
        uiState.characters.associate { it.id to it.avatarUrl }
    }
    var showInnerThoughtEntry by remember(currentConversation?.id) { mutableStateOf(true) }
    val contextCutoffId = remember(uiState.messages, currentConversation?.contextLimit) {
        val limit = currentConversation?.contextLimit ?: 999
        val contextMessages = uiState.messages.filter { it.role != "system" }.takeLast(limit)
        contextMessages.firstOrNull()?.id
    }
    val displayMessages = buildList {
        val visibleHistory = uiState.messages.takeLast(loadedMessageCount)
        addAll(visibleHistory)
        uiState.streamingAssistant?.let {
            add(ChatMessageEntity(
                id = Long.MIN_VALUE, conversationId = uiState.currentConversationId.orEmpty(),
                role = "assistant", speakerCharacterId = it.speakerCharacterId, speakerName = it.speakerName, content = it.content,
                thinkingContent = null, thinkingDuration = null, timestamp = System.currentTimeMillis()
            ))
        }
    }
    val bottomAnchorIndex = maxOf(0, displayMessages.size - 1)
    val density = LocalDensity.current
    val bottomContentPadding = with(density) { inputBarHeightPx.toDp() } + 24.dp

    // 加载更多历史（滚动到顶部时触发）
    LaunchedEffect(displayMessages.size, hasMoreHistory) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: 0 }
            .distinctUntilChanged()
            .collect { firstVisibleIndex ->
                if (hasMoreHistory && !isLoadingMore && firstVisibleIndex <= 0) {
                    isLoadingMore = true
                    delay(250)
                    loadedMessageCount += PAGE_SIZE
                    isLoadingMore = false
                }
            }
    }

    // 新消息 / 流式开始时平滑滚到底
    LaunchedEffect(displayMessages.size, uiState.streamingAssistant != null) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(bottomAnchorIndex)
        }
    }

    // 外部触发滚动
    LaunchedEffect(Unit) {
        onScrollTrigger?.collect {
            if (displayMessages.isNotEmpty()) {
                listState.animateScrollToItem(bottomAnchorIndex)
            }
        }
    }

    // 错误提示
    LaunchedEffect(Unit) {
        errorFlow?.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // 发送中持续跟随底部（用户手动上滑时停止跟随）
    LaunchedEffect(uiState.isSending) {
        while (uiState.isSending) {
            if (displayMessages.isNotEmpty()) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                // 用户未手动上滑（最后可见项接近底部）时才自动滚到底
                if (lastVisible != null && lastVisible.index >= bottomAnchorIndex - 1) {
                    listState.animateScrollToItem(bottomAnchorIndex)
                }
            }
            delay(400)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxBubbleWidth = maxWidth * 0.75f

        ChatBackgroundArtwork(uiState.backgroundBitmap, backgroundIntensity)

        if (displayMessages.isEmpty()) {
            EmptyChatHint()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 18.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayMessages, key = { it.id }) { message ->
                    val isStreaming = message.id == Long.MIN_VALUE
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) +
                            slideInVertically(animationSpec = tween(300)) { 30 }
                    ) {
                        MessageBubble(
                            message = message,
                            thinkingContent = if (isStreaming) {
                                uiState.streamingAssistant?.thinkingContent.orEmpty()
                            } else {
                                message.thinkingContent.orEmpty()
                            },
                            thinkingDuration = if (isStreaming) {
                                null
                            } else {
                                message.thinkingDuration
                            },
                            streamingStartAt = if (isStreaming) {
                                uiState.streamingAssistant?.startedAt
                            } else {
                                null
                            },
                            maxWidth = maxBubbleWidth,
                            onDeleteMessage = onDeleteMessage,
                            onRegenerateMessage = onRegenerateMessage,
                            onEditMessage = onEditMessage,
                            onEditMessageToInput = onEditMessageToInput,
                            onRetryFromMessage = onRetryFromMessage,
                            onSaveAttachmentImage = onSaveAttachmentImage,
                            onRetryLastUserMessage = onRetryLastUserMessage,
                            onToggleFavorite = onToggleFavorite,
                            speakerAvatarUrl = message.speakerCharacterId?.let(characterAvatarById::get),
                            showInnerThoughtEntry = showInnerThoughtEntry,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    if (!isStreaming && message.id == contextCutoffId && uiState.messages.count { it.role != "system" }                         > (currentConversation?.contextLimit ?: 999)) {
                        ContextDivider()
                    }
                }
                if (hasMoreHistory || isLoadingMore) {
                    item(key = "history-loader") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                item(key = "main-bottom-anchor") {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }

        val currentModelInfo = remember(uiState.selectedModel, uiState.modelList) {
            uiState.modelList.firstOrNull { it.name == uiState.selectedModel }
        }
        val modelSupportsWebSearch = remember(currentModelInfo) {
            if (currentModelInfo != null) {
                detectNativeWebSearchSupport(
                    modelName = currentModelInfo.name,
                    providerName = currentModelInfo.provider,
                    baseUrl = currentModelInfo.baseUrl
                ).supported
            } else false
        }

        ChatInputBar(
            value = uiState.input,
            isSending = uiState.isSending,
            onValueChange = onInputChange,
            onSend = onSend,
            onStop = onStopGeneration,
            onQuickSend = onSendText,
            onPersistImageAttachment = onPersistImageAttachment,
            onPersistFileAttachment = onPersistFileAttachment,
            onPersistCameraAttachment = onPersistCameraAttachment,
            onSaveAttachmentImage = onSaveAttachmentImage,
            currentConversation = currentConversation,
            defaultModel = uiState.selectedModel,
            onUpdateConversationParameters = onUpdateConversationParameters,
            onUpdateSystemPrompt = onUpdateSystemPrompt,
            webSearchEnabled = uiState.webSearchEnabled,
            supportsImage = uiState.supportsImage,
            supportsFile = uiState.supportsFile,
            hasVisionConfig = uiState.hasVisionConfig,
            selectedModel = uiState.selectedModel,
            modelSupportsWebSearch = modelSupportsWebSearch,
            onOpenModelPicker = { showModelPicker = true },
            onToggleWebSearch = onToggleWebSearch,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { inputBarHeightPx = it.size.height }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 80.dp)
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = uiState.modelList,
            favorites = uiState.modelFavorites,
            currentModelName = uiState.selectedModel,
            onDismiss = { showModelPicker = false },
            onSelectModel = { model ->
                onSelectModel(model)
                showModelPicker = false
            },
            onToggleFavorite = onToggleModelFavorite
        )
    }
}

@Composable
private fun EmptyChatHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.empty_chat_badge), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.empty_chat_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.empty_chat_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                        Text(stringResource(R.string.empty_chat_multimodal), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                        Text(stringResource(R.string.empty_chat_image_mode), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 1.dp)
        ) {}
        Text(
            text = stringResource(R.string.chat_context_divider),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 1.dp)
        ) {}
    }
}

@Composable
private fun blinkingCursor(): String {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            visible = !visible
        }
    }
    return if (visible) "|" else ""
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    thinkingContent: String,
    thinkingDuration: Int?,
    streamingStartAt: Long?,
    maxWidth: Dp,
    onDeleteMessage: (ChatMessageEntity) -> Unit,
    onRegenerateMessage: (ChatMessageEntity) -> Unit,
    onEditMessage: (ChatMessageEntity, String) -> Unit,
    onEditMessageToInput: (ChatMessageEntity) -> Unit,
    onRetryFromMessage: (ChatMessageEntity) -> Unit,
    onSaveAttachmentImage: (String) -> Unit,
    onRetryLastUserMessage: () -> Unit,
    onToggleFavorite: (ChatMessageEntity) -> Unit,
    speakerAvatarUrl: String?,
    showInnerThoughtEntry: Boolean,
    snackbarHostState: SnackbarHostState
) {
    val isUser = message.role == "user"
    val isStreaming = message.id == Long.MIN_VALUE
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.message_copied)
    var menuExpanded by remember { mutableStateOf(false) }
    var editDialogOpen by remember { mutableStateOf(false) }
    var editValue by remember(message.id) { mutableStateOf(message.content) }
    var expanded by remember(message.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val sanitizedContent = remember(message.content) {
        if (isUser) message.content else stripInnerThought(message.content)
    }
    val decodedContent = remember(sanitizedContent) { decodeChatContent(sanitizedContent) }
    val innerThought = remember(message.content) { extractInnerThought(message.content) }
    var innerExpanded by remember(message.id) { mutableStateOf(false) }
    val showPresetCopyButton = remember(message.content, isUser) {
        !isUser && shouldShowQuickCopy(decodedContent.text.ifBlank { message.content })
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = if (isUser) Modifier.widthIn(max = maxWidth) else Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser && streamingStartAt != null && thinkingContent.isBlank() && message.content.isBlank()) {
                ThinkingIndicator(startAt = streamingStartAt)
            }

            if (!isUser && thinkingContent.isNotBlank()) {
                ThinkingBlock(
                    thinkingContent = thinkingContent,
                    thinkingDuration = thinkingDuration,
                    streamingStartAt = streamingStartAt,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Box {
                Surface(
                    shape = if (isUser) {
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 8.dp
                        )
                    } else {
                        RoundedCornerShape(24.dp)
                    },
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    },
                    tonalElevation = if (isUser) 0.dp else 2.dp,
                    shadowElevation = if (isUser) 0.dp else 4.dp,
                    modifier = Modifier
                        .widthIn(max = if (isUser) maxWidth else maxWidth * 1.08f)
                        .animateContentSize()
                ) {
                    val rawText = decodedContent.text.ifBlank {
                        if (!isUser && streamingStartAt != null) "..." else ""
                    }
                    val shouldCollapse = rawText.length > 500 && !expanded && !isStreaming
                    val displayText = if (shouldCollapse) rawText.take(200) else rawText
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (!isUser) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!speakerAvatarUrl.isNullOrBlank()) {
                                        CharacterAvatar(avatarUrl = speakerAvatarUrl, modifier = Modifier.size(24.dp))
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = (message.speakerName?.takeIf { it.isNotBlank() } ?: "H").take(1),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = message.speakerName?.takeIf { it.isNotBlank() } ?: "Hana",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (!isStreaming) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (showPresetCopyButton) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(decodedContent.text.ifBlank { message.content }))
                                                        scope.launch { snackbarHostState.showSnackbar("角色卡预设已复制") }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.ContentCopy,
                                                        contentDescription = "复制角色卡预设",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuExpanded = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.MoreHoriz,
                                                contentDescription = "更多操作",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        SelectionContainer {
                            MessageContent(
                                text = displayText + if (isStreaming) blinkingCursor() else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                snackbarHostState = snackbarHostState
                            )
                        }
                        if (decodedContent.attachments.isNotEmpty()) {
                            AttachmentPreviewList(
                                attachments = decodedContent.attachments,
                                onSaveAttachmentImage = onSaveAttachmentImage,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        if (shouldCollapse) {
                            TextButton(onClick = { expanded = true }) {
                                Text("展开全文")
                            }
                        }
                        if (!isUser && message.tokenCount != null) {
                            Text(
                                text = "${message.tokenCount} tokens",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                        if (message.isError) {
                            TextButton(onClick = onRetryLastUserMessage, modifier = Modifier.align(Alignment.End)) {
                                Text("重试")
                            }
                        }
                        if (isUser && !isStreaming) {
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.MoreHoriz,
                                        contentDescription = "更多操作",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                    )
                                }
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_text)) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            clipboardManager.setText(AnnotatedString(decodedContent.text.ifBlank { message.content }))
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        }
                    )
                    if (!isUser) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.regenerate)) },
                            onClick = {
                                menuExpanded = false
                                onRegenerateMessage(message)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_message)) },
                            onClick = {
                                menuExpanded = false
                                editValue = decodedContent.text
                                editDialogOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制到输入框") },
                            onClick = {
                                menuExpanded = false
                                onEditMessageToInput(message)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("从这条重新生成") },
                            onClick = {
                                menuExpanded = false
                                onRetryFromMessage(message)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_message)) },
                        onClick = {
                            menuExpanded = false
                            onDeleteMessage(message)
                        }
                    )
                    if (!isUser) {
                        DropdownMenuItem(
                            text = { Text(if (message.isFavorite) "取消收藏" else "收藏") },
                            leadingIcon = {
                                Icon(
                                    if (message.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = null,
                                    tint = if (message.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite(message)
                            }
                        )
                    }
                }
            }

            if (!isUser && showInnerThoughtEntry && innerThought.isNotBlank()) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    TextButton(onClick = { innerExpanded = !innerExpanded }) {
                        Text(if (innerExpanded) "收起内心想法" else "查看内心想法")
                    }
                    if (innerExpanded) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = innerThought,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (editDialogOpen) {
        AlertDialog(
            onDismissRequest = { editDialogOpen = false },
            title = { Text(stringResource(R.string.edit_message)) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editDialogOpen = false
                        onEditMessage(message, editValue)
                    },
                    enabled = editValue.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AttachmentThumbnailRow(
    attachments: List<ChatAttachment>,
    onRemove: (Int) -> Unit,
    onCaptionChange: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEachIndexed { index, attachment ->
            when (attachment.kind) {
                IMAGE -> {
                    Column(
                        modifier = Modifier.width(100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 缩略图 + 删除按钮 + 序号
                        Box(modifier = Modifier.size(72.dp)) {
                            AsyncImage(
                                model = attachment.uri,
                                contentDescription = attachment.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { onRemove(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.55f),
                                        RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (attachments.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        // 图片说明文字输入
                        OutlinedTextField(
                            value = attachment.caption,
                            onValueChange = { onCaptionChange(index, it) },
                            placeholder = { Text("图${index + 1}说明", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                FILE -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.width(120.dp).height(72.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.AttachFile, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    attachment.name.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatBytes(attachment.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { onRemove(index) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "移除", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        // 添加更多按钮
        if (attachments.isNotEmpty() && attachments.size < 6) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Add, "添加更多", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${attachments.size}/6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewList(
    attachments: List<ChatAttachment>,
    onSaveAttachmentImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            when (attachment.kind) {
                IMAGE -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSaveAttachmentImage(attachment.uri) }
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsyncImage(
                                model = attachment.uri,
                                contentDescription = attachment.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(attachment.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("点击保存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                FILE -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(attachment.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(
                                        buildString {
                                            append(attachment.mimeType.ifBlank { "文件" })
                                            if (attachment.sizeBytes > 0) append(" · ${formatBytes(attachment.sizeBytes)}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = when {
                                    attachment.previewText.isNotBlank() -> "已读取文本预览：${attachment.previewText}"
                                    attachment.mimeType.contains("pdf", ignoreCase = true) || attachment.name.endsWith(".pdf", ignoreCase = true) -> "PDF 当前仅作为附件保存，暂不做深度解析"
                                    else -> "该文件当前仅作为附件保存，复杂格式暂不做深度解析"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1fKB", bytes / 1024f)
    return String.format(Locale.US, "%.1fMB", bytes / (1024f * 1024f))
}

@Composable
private fun MessageContent(
    text: String,
    style: TextStyle,
    color: Color,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val parts = remember(text) { splitCodeBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parts.forEach { part ->
            if (part.isCode) {
                CodeBlock(code = part.content, snackbarHostState = snackbarHostState)
            } else if (part.content.isNotBlank()) {
                MarkdownText(text = part.content, style = style, color = color)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, snackbarHostState: SnackbarHostState) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.message_copied)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 36.dp, end = 12.dp, bottom = 12.dp)
            )
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.copy_text)
                )
            }
        }
    }
}

@Composable
private fun MarkdownText(text: String, style: TextStyle, color: Color) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.startsWith(">") -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InlineMarkdownText(
                            text = line.removePrefix(">").trimStart(),
                            style = style,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            linkColor = linkColor,
                            onOpenUrl = uriHandler::openUri,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                line.isMarkdownListLine() -> {
                    InlineMarkdownText(
                        text = line.toDisplayListLine(),
                        style = style,
                        color = color,
                        linkColor = linkColor,
                        onOpenUrl = uriHandler::openUri
                    )
                }
                else -> {
                    InlineMarkdownText(
                        text = line,
                        style = style,
                        color = color,
                        linkColor = linkColor,
                        onOpenUrl = uriHandler::openUri
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, linkColor) {
        buildInlineMarkdown(text, linkColor)
    }
    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated
                .getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()
                ?.let { onOpenUrl(it.item) }
        }
    )
}

private fun buildInlineMarkdown(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            when {
                text.startsWith("**", cursor) -> {
                    val end = text.indexOf("**", startIndex = cursor + 2)
                    if (end > cursor) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendTextWithLinks(text.substring(cursor + 2, end), linkColor)
                        }
                        cursor = end + 2
                    } else {
                        append(text[cursor])
                        cursor++
                    }
                }
                text[cursor] == '*' -> {
                    val end = text.indexOf('*', startIndex = cursor + 1)
                    if (end > cursor) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            appendTextWithLinks(text.substring(cursor + 1, end), linkColor)
                        }
                        cursor = end + 1
                    } else {
                        append(text[cursor])
                        cursor++
                    }
                }
                text[cursor] == '`' -> {
                    val end = text.indexOf('`', startIndex = cursor + 1)
                    if (end > cursor) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x1F000000)
                            )
                        ) {
                            append(text.substring(cursor + 1, end))
                        }
                        cursor = end + 1
                    } else {
                        append(text[cursor])
                        cursor++
                    }
                }
                else -> {
                    val next = listOf(
                        text.indexOf("**", startIndex = cursor).takeIf { it >= 0 },
                        text.indexOf('*', startIndex = cursor).takeIf { it >= 0 },
                        text.indexOf('`', startIndex = cursor).takeIf { it >= 0 }
                    ).filterNotNull().minOrNull() ?: text.length
                    appendTextWithLinks(text.substring(cursor, next), linkColor)
                    cursor = next
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendTextWithLinks(text: String, linkColor: Color) {
    var cursor = 0
    val matcher = WEB_URL_PATTERN.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        append(text.substring(cursor, start))
        val url = text.substring(start, end)
        pushStringAnnotation(tag = URL_TAG, annotation = normalizeUrl(url))
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append(url)
        }
        pop()
        cursor = end
    }
    append(text.substring(cursor))
}

private fun String.isMarkdownListLine(): Boolean {
    val trimmed = trimStart()
    return trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
        ORDERED_LIST_PATTERN.matcher(trimmed).find()
}

private fun String.toDisplayListLine(): String {
    val leadingSpaces = takeWhile { it == ' ' || it == '\t' }
    val trimmed = trimStart()
    return when {
        trimmed.startsWith("- ") -> "$leadingSpaces\u2022 ${trimmed.removePrefix("- ")}"
        trimmed.startsWith("* ") -> "$leadingSpaces\u2022 ${trimmed.removePrefix("* ")}"
        else -> this
    }
}

private data class MessagePart(val content: String, val isCode: Boolean)

private fun splitCodeBlocks(text: String): List<MessagePart> {
    if (!text.contains("```")) return listOf(MessagePart(text, isCode = false))
    val parts = mutableListOf<MessagePart>()
    var cursor = 0
    var inCode = false
    while (cursor < text.length) {
        val marker = text.indexOf("```", startIndex = cursor)
        if (marker == -1) {
            parts += MessagePart(text.substring(cursor), isCode = inCode)
            break
        }
        if (marker > cursor) {
            parts += MessagePart(text.substring(cursor, marker), isCode = inCode)
        }
        cursor = marker + 3
        inCode = !inCode
    }
    return parts.map { part ->
        if (!part.isCode) {
            part
        } else {
            val content = part.content.trim('\n')
            val firstLineEnd = content.indexOf('\n')
            val withoutLanguage = if (firstLineEnd > 0 && content.substring(0, firstLineEnd).all { it.isLetterOrDigit() }) {
                content.substring(firstLineEnd + 1)
            } else {
                content
            }
            part.copy(content = withoutLanguage)
        }
    }
}

private fun normalizeUrl(url: String): String {
    return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
}

private fun shouldShowQuickCopy(text: String): Boolean {
    val normalized = text.lowercase()
    val presetHitCount = listOf(
        "名字", "名称", "角色名", "开场", "开场白", "对话示例", "人设", "设定", "描述", "性格", "背景", "用户", "我的人设", "标签"
    ).count { normalized.contains(it) }
    val labeledLineCount = text.lines().count { line ->
        val trimmed = line.trim()
        trimmed.contains(":") || trimmed.contains("：")
    }
    val bulletLineCount = text.lines().count { line ->
        val trimmed = line.trimStart()
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || ORDERED_LIST_PATTERN.matcher(trimmed).find()
    }
    val hasCodeBlock = text.contains("```")
    val looksLikeJson = normalized.trim().startsWith("{") && normalized.contains("\"") && normalized.contains(":")
    val looksLikePromptPack = normalized.contains("prompt") && (normalized.contains("negative") || normalized.contains("tags") || normalized.contains("style"))
    val looksLikeTemplate = presetHitCount >= 3 || labeledLineCount >= 4 || bulletLineCount >= 5
    return hasCodeBlock || looksLikeJson || looksLikePromptPack || looksLikeTemplate || text.length >= 700
}

private fun extractInnerThought(content: String): String {
    val patterns = listOf(
        Regex("<inner>(.*?)</inner>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("【内心】(.*?)(?:【/内心】|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("（内心[:：](.*?)）", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\(内心[:：](.*?)\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    )
    return patterns
        .flatMap { regex -> regex.findAll(content).map { it.groupValues[1].trim() }.toList() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n\n")
}

private fun stripInnerThought(content: String): String {
    val patterns = listOf(
        Regex("<inner>.*?</inner>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("【内心】.*?(?:【/内心】|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("（内心[:：].*?）", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\(内心[:：].*?\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    )
    return patterns.fold(content) { acc, regex -> acc.replace(regex, "") }.trim()
}

private const val URL_TAG = "url"
private val WEB_URL_PATTERN: Pattern = Pattern.compile(
    """((https?://)?[A-Za-z0-9.-]+\.[A-Za-z]{2,}(/[^\s]*)?)"""
)
private val ORDERED_LIST_PATTERN: Pattern = Pattern.compile("""^\d+\.\s+.+""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onQuickSend: (String) -> Unit,
    onPersistImageAttachment: (Uri, (ChatAttachment?) -> Unit) -> Unit,
    onPersistFileAttachment: (Uri, (ChatAttachment?) -> Unit) -> Unit,
    onPersistCameraAttachment: (Bitmap, (ChatAttachment?) -> Unit) -> Unit,
    onSaveAttachmentImage: (String) -> Unit,
    currentConversation: ConversationEntity?,
    defaultModel: String,
    onUpdateConversationParameters: (String?, Float, Float, Int, Int, String?) -> Unit,
    onUpdateSystemPrompt: (String, String?) -> Unit,
    webSearchEnabled: Boolean,
    supportsImage: Boolean,
    supportsFile: Boolean,
    hasVisionConfig: Boolean,
    selectedModel: String = "",
    modelSupportsWebSearch: Boolean = false,
    onOpenModelPicker: () -> Unit = {},
    onToggleWebSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showParameters by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var pendingAttachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    var showSystemPromptEditor by remember { mutableStateOf(false) }
    var modelValue by remember(currentConversation?.id, currentConversation?.modelName, defaultModel) {
        mutableStateOf(currentConversation?.modelName ?: defaultModel)
    }
    var temperature by remember(currentConversation?.id, currentConversation?.temperature) {
        mutableStateOf(currentConversation?.temperature ?: 0.7f)
    }
    var topP by remember(currentConversation?.id, currentConversation?.topP) {
        mutableStateOf(currentConversation?.topP ?: 1f)
    }
    var maxTokensText by remember(currentConversation?.id, currentConversation?.maxTokens) {
        mutableStateOf((currentConversation?.maxTokens ?: 4096).toString())
    }
    var contextLimit by remember(currentConversation?.id, currentConversation?.contextLimit) {
        mutableStateOf(currentConversation?.contextLimit ?: 999)
    }
    var systemPromptText by remember(currentConversation?.id, currentConversation?.systemPrompt) {
        mutableStateOf(currentConversation?.systemPrompt.orEmpty())
    }
    var modelExpanded by remember { mutableStateOf(false) }
    val modelOptions = remember(modelValue, defaultModel) {
        listOf(modelValue, defaultModel).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val imageUploadLabel = stringResource(R.string.image_upload)
    val fileUploadLabel = stringResource(R.string.file_upload)
    val cameraUploadLabel = stringResource(R.string.photo)
    val moreActionsLabel = stringResource(R.string.more_actions)
    val webSearchLabel = stringResource(R.string.web_search)
    val haptic = LocalHapticFeedback.current
    val stopLabel = stringResource(R.string.stop)
    val imageNotSupportedMessage = stringResource(R.string.image_not_supported)
    val fileNotSupportedMessage = stringResource(R.string.file_not_supported)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val newUris = uris.take(6)
        if (newUris.isEmpty()) return@rememberLauncherForActivityResult
        val remaining = 6 - pendingAttachments.size
        if (remaining <= 0) {
            Toast.makeText(context, "最多上传6张图片", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val toAdd = newUris.take(remaining)
        var added = 0
        toAdd.forEach { uri ->
            onPersistImageAttachment(uri) { attachment ->
                if (attachment != null) {
                    pendingAttachments = pendingAttachments + attachment
                    added++
                }
            }
        }
        if (added == 0 && toAdd.isNotEmpty()) {
            Toast.makeText(context, "图片添加失败", Toast.LENGTH_SHORT).show()
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPersistFileAttachment(uri) { attachment ->
                if (attachment != null) pendingAttachments = pendingAttachments + attachment
                else Toast.makeText(context, "文件添加失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            onPersistCameraAttachment(bitmap) { attachment ->
                if (attachment != null) pendingAttachments = pendingAttachments + attachment
                else Toast.makeText(context, "拍照添加失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun submitMessage() {
        if (pendingAttachments.isEmpty()) {
            onSend()
        } else {
            val captions = pendingAttachments
                .filter { it.kind == AttachmentKind.IMAGE && it.caption.isNotBlank() }
                .mapIndexed { i, a -> "[图${i + 1}] ${a.caption}" }
            val captionText = if (captions.isNotEmpty()) "\n" + captions.joinToString("\n") else ""
            onQuickSend(encodeChatContent(value + captionText, pendingAttachments))
            pendingAttachments = emptyList()
        }
    }

    val effectiveDisplayedModel = currentConversation?.modelName?.takeIf { it.isNotBlank() } ?: selectedModel
    val imageCapabilityEnabled = supportsImage || hasVisionConfig || effectiveDisplayedModel.lowercase().let { name ->
        name.contains("vision") || name.contains("-vl") || name.startsWith("gpt-4o") || name.startsWith("gemini-") || name.startsWith("claude-") || name.startsWith("grok-")
    }
    val fileCapabilityEnabled = supportsFile || hasVisionConfig || imageCapabilityEnabled
    val capabilityHint = when {
        imageCapabilityEnabled && fileCapabilityEnabled -> "当前模型支持图片与文件"
        imageCapabilityEnabled -> "当前模型支持图片输入"
        fileCapabilityEnabled -> "当前模型支持文件输入"
        else -> "当前模型仅支持文本"
    }
    val isGroupConversation = currentConversation?.conversationType == "group"
    val groupReplyHint = if (isGroupConversation) {
        "群聊模式：可直接 @角色名 或提到名字点名，未点名时会自动选择 2~3 个角色回复"
    } else {
        null
    }

    val actionScale by animateFloatAsState(
        targetValue = if (isSending) 0.96f else 1f,
        animationSpec = tween(180),
        label = "chatActionScale"
    )

    Surface(
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(modifier = Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onOpenModelPicker,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(effectiveDisplayedModel.ifBlank { "选择模型" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 模型联网能力指示灯
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (modelSupportsWebSearch) Color(0xFF16A34A).copy(alpha = 0.15f) else Color(0xFF9CA3AF).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (modelSupportsWebSearch) Color(0xFF16A34A) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(7.dp)
                        ) {}
                        Text(
                            if (modelSupportsWebSearch) "联网" else "离线",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (modelSupportsWebSearch) Color(0xFF15803D) else Color(0xFF6B7280)
                        )
                    }
                }
            }
            Text(
                text = capabilityHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (!groupReplyHint.isNullOrBlank()) {
                Text(
                    text = groupReplyHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (pendingAttachments.isNotEmpty()) {
                AttachmentThumbnailRow(
                    attachments = pendingAttachments,
                    onRemove = { index -> pendingAttachments = pendingAttachments.toMutableList().also { it.removeAt(index) } },
                    onCaptionChange = { index, caption ->
                        pendingAttachments = pendingAttachments.toMutableList().also {
                            it[index] = it[index].copy(caption = caption)
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = value, onValueChange = onValueChange,
                        placeholder = { Text(stringResource(R.string.input_message)) },
                        enabled = !isSending, maxLines = 5, minLines = 1,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)
                    )
                }
                Spacer(modifier = Modifier.size(6.dp))
                if (isSending) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(44.dp).graphicsLayer {
                            scaleX = actionScale
                            scaleY = actionScale
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Stop, stopLabel, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(
                        onClick = { submitMessage() },
                        enabled = value.isNotBlank() || pendingAttachments.isNotEmpty(),
                        modifier = Modifier.size(48.dp).graphicsLayer {
                            scaleX = actionScale
                            scaleY = actionScale
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        SmoothTriangleIcon(color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAttachSheet = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Add, moreActionsLabel, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleWebSearch()
                }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Public, webSearchLabel, modifier = Modifier.size(18.dp),
                        tint = if (webSearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                Surface(
                    onClick = { showParameters = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("参数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(modifier = Modifier.padding(20.dp).padding(bottom = 32.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AttachAction(Icons.Filled.PhotoCamera, cameraUploadLabel, enabled = imageCapabilityEnabled) {
                        if (!imageCapabilityEnabled) {
                            Toast.makeText(context, imageNotSupportedMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            showAttachSheet = false
                            cameraLauncher.launch(null)
                        }
                    }
                    AttachAction(Icons.Filled.ImageIcon, imageUploadLabel, enabled = imageCapabilityEnabled) {
                        if (!imageCapabilityEnabled) {
                            Toast.makeText(context, imageNotSupportedMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            showAttachSheet = false
                            imagePicker.launch("image/*")
                        }
                    }
                    AttachAction(Icons.Filled.AttachFile, fileUploadLabel, enabled = fileCapabilityEnabled) {
                        if (!fileCapabilityEnabled) {
                            Toast.makeText(context, fileNotSupportedMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            showAttachSheet = false
                            filePicker.launch(arrayOf("*/*"))
                        }
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))
                Surface(
                    onClick = { showAttachSheet = false; onOpenModelPicker() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83E\uDD16", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("切换模型", style = MaterialTheme.typography.bodyMedium)
                            Text(effectiveDisplayedModel.ifBlank { "未选择" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                Surface(
                    onClick = { showAttachSheet = false; showParameters = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("参数设置")
                    }
                }
            }
        }
    }

    if (showParameters) {
        ModalBottomSheet(
            onDismissRequest = { showParameters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("对话参数设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("这里控制当前对话的回复风格、生成长度和上下文记忆范围。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    OutlinedTextField(
                        value = modelValue,
                        onValueChange = { modelValue = it },
                        label = { Text("模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        modelOptions.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    modelValue = model
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
                Text("回复自由度 ${"%.1f".format(temperature)}", fontWeight = FontWeight.SemiBold)
                Text("越高越灵活、越有变化；越低越稳、更贴近固定风格。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f)
                Text("采样范围 ${"%.2f".format(topP)}", fontWeight = FontWeight.SemiBold)
                Text("控制措辞分布范围。一般保持默认即可，想让回答更灵活时再调高。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = topP, onValueChange = { topP = it }, valueRange = 0f..1f)
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it.filter(Char::isDigit).take(6) },
                    label = { Text("单次最大回复长度") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("值越大，单次回答可能越长，但生成速度和消耗也会提高。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.context_limit, contextLimit))
                Text("控制当前对话会带上多少轮最近消息。值越高，越能延续前文。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = contextLimit.toFloat(),
                    onValueChange = { contextLimit = it.toInt().coerceIn(1, 999) },
                    valueRange = 1f..50f,
                    steps = 48
                )
                Button(
                    onClick = { showSystemPromptEditor = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.edit_system_prompt))
                }
                Button(
                    onClick = {
                        onUpdateConversationParameters(
                            modelValue,
                            temperature,
                            topP,
                            maxTokensText.toIntOrNull() ?: 4096,
                            contextLimit,
                            currentConversation?.id
                        )
                        showParameters = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save_parameters))
                }
            }
        }
    }

    if (showSystemPromptEditor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.system_prompt), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = systemPromptText,
                    onValueChange = { systemPromptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    minLines = 12
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSystemPromptEditor = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onUpdateSystemPrompt(systemPromptText, currentConversation?.id)
                            showSystemPromptEditor = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmoothTriangleIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.12f)
            lineTo(size.width * 0.84f, size.height * 0.5f)
            lineTo(size.width * 0.18f, size.height * 0.88f)
            close()
        }
        drawPath(path = path, color = color, style = Fill)
    }
}

@Composable
private fun AttachAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun quickPhrasePayload(label: String): String {
    return when (label) {
        "继续" -> "请继续"
        "更详细" -> "请更详细地描述"
        "换个方式" -> "请换一种方式重新回答"
        "总结" -> "请总结以上对话"
        "留在角色中" -> "[OOC: 请保持角色扮演，不要跳出角色]"
        else -> label
    }
}
