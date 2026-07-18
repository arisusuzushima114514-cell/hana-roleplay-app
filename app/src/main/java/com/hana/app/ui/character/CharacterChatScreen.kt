package com.hana.app.ui.character

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.heightIn
import coil.compose.AsyncImage
import com.hana.app.R
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.ConversationEntity
import com.hana.app.data.settings.CharacterStoryLogEntry
import com.hana.app.data.settings.CharacterStoryState
import com.hana.app.ui.chat.ThinkingBlock
import com.hana.app.ui.chat.ChatBackgroundArtwork
import com.hana.app.ui.chat.ThinkingIndicator
import com.hana.app.ui.theme.LocalHanaBubbleColors
import com.hana.app.viewmodel.ChatUiState
import com.hana.app.viewmodel.relationshipStageLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CharacterEditorScreen {
    CharacterPersona,
    UserPersona,
    CreativePreset
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CharacterChatScreen(
    character: CharacterCardEntity,
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onDeleteMessage: (ChatMessageEntity) -> Unit,
    onRegenerateMessage: (ChatMessageEntity) -> Unit,
    onEditMessage: (ChatMessageEntity, String) -> Unit,
    onStopGeneration: () -> Unit,
    onRetryLastUserMessage: () -> Unit,
    onUpdateConversationParameters: (String?, Float, Float, Int, Int, String?) -> Unit,
    onUpdateSystemPrompt: (String, String?) -> Unit,
    onToggleWebSearch: () -> Unit,
    onClearHistory: (String) -> Unit,
    onDeleteLastRound: (String) -> Unit,
    onSaveCharacter: (CharacterCardEntity) -> Unit,
    getCharacterStoryState: (String) -> CharacterStoryState,
    getCharacterStoryLogs: (String) -> List<CharacterStoryLogEntry>,
    creativePresetText: String,
    creativePresetEnabledForCharacter: Boolean,
    creativePresetAffectsPersonaForCharacter: Boolean,
    onCreativePresetEnabledChange: (String, Boolean) -> Unit,
    onCreativePresetAffectsPersonaChange: (String, Boolean) -> Unit,
    onCreativePresetTextChange: (String) -> Unit,
    backgroundIntensity: String = "soft",
    onPickBackground: () -> Unit,
    onOpenBackgroundLibrary: () -> Unit,
    onClearBackground: () -> Unit,
    getCharacterById: (String) -> CharacterCardEntity?,
    getConversationByCharacterId: (String) -> ConversationEntity?,
    errorFlow: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onGenerateSceneImage: ((String) -> Unit)? = null,
    // 场景捕捉状态（由外部管理）
    isGeneratingScene: Boolean = false,
    sceneImageUrls: List<String> = emptyList(),
    sceneImageError: String? = null,
    onDismissSceneImage: () -> Unit = {}
) {
    val liveCharacter = getCharacterById(character.id) ?: character
    val bubbleColors = LocalHanaBubbleColors.current
    val conversation = getConversationByCharacterId(liveCharacter.id)
    val conversationId = conversation?.id.orEmpty()
    val creativePresetModeLabel = when {
        !creativePresetEnabledForCharacter -> "预设未启用"
        creativePresetAffectsPersonaForCharacter -> "允许影响人格"
        else -> "仅模型补充"
    }
    val creativePresetModeDescription = when {
        !creativePresetEnabledForCharacter -> "未启用"
        creativePresetAffectsPersonaForCharacter -> "允许影响人格"
        else -> "仅模型补充"
    }
    val messages = try {
        buildList {
            if (conversationId.isBlank()) {
                if (liveCharacter.greeting.isNotBlank()) {
                    add(ChatMessageEntity(id = -1L, conversationId = "", role = "assistant", speakerCharacterId = liveCharacter.id, speakerName = liveCharacter.name, content = liveCharacter.greeting, thinkingContent = null, thinkingDuration = null, timestamp = System.currentTimeMillis()))
                }
            } else {
                addAll(uiState.messages.filter { it.id > 0L && it.conversationId == conversationId })
            }
            uiState.streamingAssistant?.let {
                if (uiState.currentConversationId == conversationId) {
                    add(ChatMessageEntity(id = Long.MIN_VALUE, conversationId = conversationId, role = "assistant", speakerCharacterId = it.speakerCharacterId, speakerName = it.speakerName ?: liveCharacter.name, content = it.content, thinkingContent = null, thinkingDuration = null, timestamp = System.currentTimeMillis()))
                }
            }
            // 场景捕捉：生成中的加载提示
            if (isGeneratingScene) {
                add(ChatMessageEntity(
                    id = -997L, conversationId = conversationId, role = "system",
                    speakerCharacterId = liveCharacter.id, speakerName = liveCharacter.name,
                    content = "🎨 正在生成场景图...", thinkingContent = null,
                    thinkingDuration = null, timestamp = System.currentTimeMillis()
                ))
            }
            // 场景捕捉：生成结果
            sceneImageUrls.firstOrNull()?.let { url ->
                add(ChatMessageEntity(
                    id = -998L, conversationId = conversationId, role = "system",
                    speakerCharacterId = liveCharacter.id, speakerName = liveCharacter.name,
                    content = url, // 用 content 传递图片URL，渲染时特殊处理
                    thinkingContent = null, thinkingDuration = null,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }.distinctBy { it.id }
    } catch (e: Exception) {
        emptyList()
    }

    val listState = rememberLazyListState()
    var inputBarHeightPx by remember { mutableStateOf(0) }
    val bottomAnchorIndex = maxOf(0, messages.size - 1)
    val density = LocalDensity.current
    val bottomContentPadding = with(density) { inputBarHeightPx.toDp() } + 24.dp

    // 进入角色卡 / 新消息到达时，瞬间滚到底部（不用动画，避免长对话"超绝大滑动"）
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(bottomAnchorIndex)
        }
    }

    // 流式输出进行中：仅在用户已在底部时，动画跟随新内容
    LaunchedEffect(uiState.isSending) {
        while (uiState.isSending) {
            if (messages.isNotEmpty() && listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == bottomAnchorIndex) {
                listState.animateScrollToItem(bottomAnchorIndex)
            }
            delay(450)
        }
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showParameters by remember { mutableStateOf(false) }
    var showSystemPrompt by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showStorylineSheet by remember { mutableStateOf(false) }
    var activeEditorScreen by remember { mutableStateOf<CharacterEditorScreen?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var showInnerThoughtEntry by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = showSettingsSheet || showParameters || showSystemPrompt || showClearConfirm || moreMenuExpanded || showStorylineSheet) {
        when {
            showStorylineSheet -> showStorylineSheet = false
            moreMenuExpanded -> moreMenuExpanded = false
            showClearConfirm -> showClearConfirm = false
            showSystemPrompt -> showSystemPrompt = false
            showParameters -> showParameters = false
            showSettingsSheet -> showSettingsSheet = false
        }
    }

    LaunchedEffect(Unit) {
        errorFlow?.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("新建对话") },
            text = { Text("将清空当前角色的聊天记录，角色卡配置不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory(character.id)
                    showClearConfirm = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showParameters && conversation != null) {
        ParametersBottomSheet(
            conversation = conversation,
            uiState = uiState,
            onDismiss = { showParameters = false },
            onUpdateParameters = { model, temp, topP, maxTokens, ctxLimit ->
                onUpdateConversationParameters(model, temp, topP, maxTokens, ctxLimit, conversation.id)
            },
            onEditSystemPrompt = {
                showParameters = false
                showSystemPrompt = true
            }
        )
    }

    if (showSystemPrompt && conversation != null) {
        var promptText by remember { mutableStateOf(conversation.systemPrompt ?: "") }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("系统提示词", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    minLines = 12
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSystemPrompt = false }) { Text("取消") }
                    Button(
                        onClick = {
                            onUpdateSystemPrompt(promptText, conversation.id)
                            showSystemPrompt = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存") }
                }
            }
        }
    }

    when (activeEditorScreen) {
        CharacterEditorScreen.CharacterPersona -> {
            CharacterPersonaEditorScreen(
                character = liveCharacter,
                onBack = { activeEditorScreen = null },
                onSave = {
                    onSaveCharacter(it)
                    activeEditorScreen = null
                }
            )
            return
        }

        CharacterEditorScreen.UserPersona -> {
            CharacterUserPersonaEditorScreen(
                character = liveCharacter,
                onBack = { activeEditorScreen = null },
                onSave = {
                    onSaveCharacter(it)
                    activeEditorScreen = null
                }
            )
            return
        }

        CharacterEditorScreen.CreativePreset -> {
            CharacterCreativePresetScreen(
                characterName = liveCharacter.name,
                enabled = creativePresetEnabledForCharacter,
                affectsPersona = creativePresetAffectsPersonaForCharacter,
                presetText = creativePresetText,
                onBack = { activeEditorScreen = null },
                onEnabledChange = { enabled ->
                    onCreativePresetEnabledChange(liveCharacter.id, enabled)
                },
                onAffectsPersonaChange = { enabled ->
                    onCreativePresetAffectsPersonaChange(liveCharacter.id, enabled)
                },
                onPresetTextChange = onCreativePresetTextChange
            )
            return
        }

        null -> Unit
    }

    Scaffold(
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (liveCharacter.avatarUrl.isNotBlank()) {
                            CharacterAvatar(avatarUrl = liveCharacter.avatarUrl, modifier = Modifier.size(38.dp))
                        } else {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(38.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(liveCharacter.name.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 名字 + 角色主线（紧凑排列）
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(liveCharacter.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { showStorylineSheet = true },
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("角色主线", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // 三点菜单（收纳所有功能）
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.character_more))
                        }
                        DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.character_regenerate)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    val lastAssistant = messages.firstOrNull { it.role == "assistant" && it.id > 0L && it.id != Long.MIN_VALUE }
                                    if (lastAssistant != null) onRegenerateMessage(lastAssistant) else onRetryLastUserMessage()
                                },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.character_retract_last)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onDeleteLastRound(conversationId)
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) }
                            )
                            if (onGenerateSceneImage != null) {
                                DropdownMenuItem(
                                    text = { Text("场景捕捉") },
                                    onClick = {
                                        moreMenuExpanded = false
                                        if (isGeneratingScene) return@DropdownMenuItem
                                        val prompt = buildSceneImagePrompt(liveCharacter, messages)
                                        onGenerateSceneImage(prompt)
                                    },
                                    leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                                    enabled = !isGeneratingScene
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("模型设定") },
                                onClick = { moreMenuExpanded = false; showSettingsSheet = true },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(creativePresetModeLabel) },
                                onClick = { moreMenuExpanded = false; showSettingsSheet = true },
                                leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.webSearchEnabled) "联网：开" else "联网：关") },
                                onClick = { moreMenuExpanded = false; onToggleWebSearch() },
                                leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("面板") },
                                onClick = { moreMenuExpanded = false; showSettingsSheet = true },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("背景库") },
                                onClick = { moreMenuExpanded = false; onOpenBackgroundLibrary() },
                                leadingIcon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("背景") },
                                onClick = { moreMenuExpanded = false; onPickBackground() },
                                leadingIcon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            val maxBubbleWidth = maxWidth * 0.85f

            ChatBackgroundArtwork(uiState.backgroundBitmap, backgroundIntensity)

            if (messages.isEmpty() && conversationId.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            onClick = { showStorylineSheet = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "角色主线",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "点击查看好感度、信任感、接受度和关系时间线",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                            tonalElevation = 3.dp,
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                ) {
                                    Text(stringResource(R.string.character_opening), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (liveCharacter.avatarUrl.isNotBlank()) {
                                        CharacterAvatar(avatarUrl = liveCharacter.avatarUrl, modifier = Modifier.size(80.dp))
                                    } else {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(80.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = liveCharacter.name.take(1),
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = liveCharacter.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = liveCharacter.greeting,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
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
                    item(key = "character-storyline-entry") {
                        Surface(
                            onClick = { showStorylineSheet = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "角色主线",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "点击查看好感度、信任感、接受度和关系时间线",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val isStreaming = message.id == Long.MIN_VALUE
                        val isGreeting = message.id == -1L && message.role == "assistant"
                        val isSceneImage = message.id == -998L
                        val isSceneLoading = message.id == -997L

                        if (isSceneImage) {
                            // 场景捕捉结果：显示图片
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(500)) + scaleIn()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        "${liveCharacter.name} 此刻的画面",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                                    )
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    ) {
                                        AsyncImage(
                                            model = message.content,
                                            contentDescription = "场景图",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 400.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        } else if (isSceneLoading) {
                            // 场景捕捉：生成中
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { 30 }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "正在生成 ${liveCharacter.name} 的场景图...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            CharacterMessageBubble(
                                    message = message,
                                    thinkingContent = if (isStreaming) {
                                        uiState.streamingAssistant?.thinkingContent.orEmpty()
                                    } else {
                                        message.thinkingContent.orEmpty()
                                    },
                                    thinkingDuration = if (isStreaming) null else message.thinkingDuration,
                                    streamingStartAt = if (isStreaming) uiState.streamingAssistant?.startedAt else null,
                                    maxWidth = maxBubbleWidth,
                                    bubbleColors = bubbleColors,
                                    showInnerThoughtEntry = showInnerThoughtEntry,
                                    onDeleteMessage = if (isGreeting) null else onDeleteMessage,
                                    onRegenerateMessage = if (isGreeting) null else onRegenerateMessage,
                                    onEditMessage = if (isGreeting) null else onEditMessage,
                                    onRetryLastUserMessage = onRetryLastUserMessage,
                                    snackbarHostState = snackbarHostState
                            )
                        }
                    }
                    item(key = "character-bottom-anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            CharacterChatInputBar(
                value = uiState.input,
                isSending = uiState.isSending,
                characterName = liveCharacter.name,
                onValueChange = onInputChange,
                onSend = onSend,
                onStop = onStopGeneration,
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
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.character_actions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        messages.firstOrNull { it.role == "assistant" && it.id != -1L && it.id != Long.MIN_VALUE }
                            ?.let { onRegenerateMessage(it) }
                            ?: onRetryLastUserMessage()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.character_regenerate), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        onDeleteLastRound(conversationId)
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.character_retract_last), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.character_settings), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        showParameters = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.character_text_model_settings), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        activeEditorScreen = CharacterEditorScreen.CharacterPersona
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.character_profile_edit), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        onPickBackground()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.character_set_bg), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        onClearBackground()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.character_clear_bg), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        activeEditorScreen = CharacterEditorScreen.UserPersona
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("我的人设", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        showStorylineSheet = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.character_storyline), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = {
                        showSettingsSheet = false
                        activeEditorScreen = CharacterEditorScreen.CreativePreset
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("创作预设", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "当前角色：$creativePresetModeDescription",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("内心想法入口", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (showInnerThoughtEntry) "开启后，有内心内容的回复会显示查看入口" else "关闭后，不显示内心想法入口",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = showInnerThoughtEntry,
                            onCheckedChange = { showInnerThoughtEntry = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        showSettingsSheet = false
                        showClearConfirm = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("新建对话", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showStorylineSheet) {
        CharacterStorylineSheet(
            character = liveCharacter,
            conversation = conversation,
            messages = messages,
            savedState = getCharacterStoryState(liveCharacter.id),
            logs = getCharacterStoryLogs(liveCharacter.id),
            onDismiss = { showStorylineSheet = false }
        )
    }

    // 场景捕捉错误通过 Snackbar 显示
    LaunchedEffect(sceneImageError) {
        sceneImageError?.let { error ->
            snackbarHostState.showSnackbar(message = "场景生图失败: $error", duration = SnackbarDuration.Short)
            onDismissSceneImage()
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CharacterStorylineSheet(
    character: CharacterCardEntity,
    conversation: ConversationEntity?,
    messages: List<ChatMessageEntity>,
    savedState: CharacterStoryState,
    logs: List<CharacterStoryLogEntry>,
    onDismiss: () -> Unit
) {
    val nonStreamingMessages = messages.filter { it.id > 0L }
    val userCount = nonStreamingMessages.count { it.role == "user" }
    val assistantCount = nonStreamingMessages.count { it.role == "assistant" }
    val rounds = minOf(userCount, assistantCount)
    val affection = savedState.affection
    val trust = savedState.trust
    val acceptance = (100 - savedState.tension).coerceIn(0, 100)
    val relationshipStage = relationshipStageLabel(
        affection = affection,
        trust = trust,
        tension = savedState.tension,
        relationshipAnchor = savedState.relationshipAnchor,
        intimacyBaseline = savedState.intimacyBaseline,
        relationshipMomentum = savedState.relationshipMomentum
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色主线", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(character.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        character.description.ifBlank { "当前还没有填写更详细的角色设定。" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill("好感度", "$affection / 100")
                StatusPill("信任感", "$trust / 100")
                StatusPill("接受度", "$acceptance / 100")
                StatusPill("关系锚点", savedState.relationshipAnchor)
                StatusPill("亲密基线", "${savedState.intimacyBaseline} / 100")
                StatusPill("升温势能", "${savedState.relationshipMomentum} / 100")
                StatusPill("角色状态", relationshipStage)
                StatusPill("当前状态", savedState.statusNote)
                StatusPill("剧情进度", savedState.progressNote)
                StatusPill("互动轮数", "$rounds 轮")
            }
            if (savedState.recentEventSummary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("最近关键互动", fontWeight = FontWeight.SemiBold)
                        Text(savedState.recentEventSummary, style = MaterialTheme.typography.bodySmall)
                        if (conversation != null) {
                            Text(
                                "当前使用模型：${conversation.modelName ?: "跟随全局"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (logs.isNotEmpty()) {
                Text("时间线", fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    logs.take(12).forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (entry.note.isNotBlank()) {
                                    Text(entry.note, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "好感 ${entry.affection} · 信任 ${entry.trust} · 接受 ${ (100 - entry.tension).coerceIn(0, 100) }",
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
}

@Composable
private fun StatusPill(title: String, value: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterCreativePresetScreen(
    characterName: String,
    enabled: Boolean,
    affectsPersona: Boolean,
    presetText: String,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onAffectsPersonaChange: (Boolean) -> Unit,
    onPresetTextChange: (String) -> Unit
) {
    var localPresetText by remember(presetText) { mutableStateOf(presetText) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创作预设", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "这里直接编辑创作预设文本，并控制当前角色是否使用它。你可以在角色扮演中途随时改文本或切换开关，下一轮回复就会按新状态生效，不会影响其他角色的开关。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("对角色 $characterName 启用创作预设", style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = affectsPersona,
                    onCheckedChange = onAffectsPersonaChange,
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("允许影响人格", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = when {
                    !enabled -> "当前生效链路：模型 -> 角色卡预设 -> 输出"
                    affectsPersona -> "当前生效链路：模型 -> 全局创作预设（可影响人格） -> 角色卡预设 -> 输出"
                    else -> "当前生效链路：模型 -> 全局创作预设（仅模型补充） -> 角色卡预设 -> 输出"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = localPresetText,
                onValueChange = {
                    localPresetText = it
                    onPresetTextChange(it)
                },
                label = { Text("创作预设文本") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 10
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterPersonaEditorScreen(
    character: CharacterCardEntity,
    onBack: () -> Unit,
    onSave: (CharacterCardEntity) -> Unit
) {
    var nameEdit by remember(character.id, character.name) { mutableStateOf(character.name) }
    var descEdit by remember(character.id, character.description) { mutableStateOf(character.description) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色人设编辑", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "这里编辑的是角色本体的人设和展示名称，会直接影响这个角色后续的所有对话。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = nameEdit,
                onValueChange = { nameEdit = it },
                label = { Text("角色名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = descEdit,
                onValueChange = { descEdit = it },
                label = { Text("角色描述 / 人设") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 10
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("取消") }
                Button(
                    onClick = { onSave(character.copy(name = nameEdit.trim(), description = descEdit.trim())) },
                    modifier = Modifier.weight(1f),
                    enabled = nameEdit.trim().isNotBlank()
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterUserPersonaEditorScreen(
    character: CharacterCardEntity,
    onBack: () -> Unit,
    onSave: (CharacterCardEntity) -> Unit
) {
    var personaEdit by remember(character.id, character.userPersona) { mutableStateOf(character.userPersona) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的人设", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "这里定义你在这个角色场景里的身份、名字、关系设定。角色会把这段内容当作用户侧人设来理解。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = personaEdit,
                onValueChange = { personaEdit = it },
                label = { Text("你在这个场景中的身份 / 名字") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 8
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("取消") }
                Button(
                    onClick = { onSave(character.copy(userPersona = personaEdit.trim())) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterMessageBubble(
    message: ChatMessageEntity,
    thinkingContent: String,
    thinkingDuration: Int?,
    streamingStartAt: Long?,
    maxWidth: Dp,
    bubbleColors: com.hana.app.ui.theme.HanaBubbleColors,
    showInnerThoughtEntry: Boolean,
    onDeleteMessage: ((ChatMessageEntity) -> Unit)?,
    onRegenerateMessage: ((ChatMessageEntity) -> Unit)?,
    onEditMessage: ((ChatMessageEntity, String) -> Unit)?,
    onRetryLastUserMessage: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val isUser = message.role == "user"
    val isStreaming = message.id == Long.MIN_VALUE
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var editDialogOpen by remember { mutableStateOf(false) }
    var editValue by remember(message.id) { mutableStateOf(message.content) }
    val innerThought = remember(message.content) { extractInnerThought(message.content) }
    var innerExpanded by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = maxWidth),
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
                    title = "思考过程",
                    titleStreaming = "思考中",
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isUser) {
                        bubbleColors.userBubble
                    } else {
                        bubbleColors.aiBubble
                    },
                    shadowElevation = if (isUser || isStreaming) 0.dp else 2.dp,
                    modifier = Modifier
                        .animateContentSize()
                ) {
                    val rawText = stripInnerThought(message.content).ifBlank {
                        if (!isUser && streamingStartAt != null) "..." else ""
                    }
                    val displayText = rawText
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        val selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
                            handleColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                            backgroundColor = if (isUser) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                            SelectionContainer {
                            Text(
                                text = displayText + if (isStreaming) "|" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) bubbleColors.userBubbleOnSurface else bubbleColors.aiBubbleOnSurface
                            )
                        }
                        }
                        if (message.isError) {
                            TextButton(onClick = onRetryLastUserMessage, modifier = Modifier.align(Alignment.End)) {
                                Text("重试")
                            }
                        }
                        if (!isStreaming && onDeleteMessage != null) {
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
                                        tint = (if (isUser) bubbleColors.userBubbleOnSurface else bubbleColors.aiBubbleOnSurface).copy(alpha = 0.72f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (onDeleteMessage != null) {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制") },
                            onClick = {
                                menuExpanded = false
                                clipboardManager.setText(AnnotatedString(message.content))
                                scope.launch { snackbarHostState.showSnackbar("已复制") }
                            }
                        )
                        if (!isUser && onRegenerateMessage != null) {
                            DropdownMenuItem(
                                text = { Text("重新生成") },
                                onClick = {
                                    menuExpanded = false
                                    onRegenerateMessage(message)
                                }
                            )
                        }
                        if (isUser && onEditMessage != null) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                onClick = {
                                    menuExpanded = false
                                    editValue = message.content
                                    editDialogOpen = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                onDeleteMessage(message)
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
                                text = innerThought.ifBlank { "这一句没有额外内心描写。" },
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
            title = { Text("编辑消息") },
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
                        onEditMessage?.invoke(message, editValue)
                    },
                    enabled = editValue.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editDialogOpen = false }) { Text("取消") }
            }
        )
    }
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

@Composable
fun RichRoleText(
    text: String,
    style: TextStyle,
    baseColor: Color,
    accentColor: Color,
    secondaryColor: Color
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, accentColor, secondaryColor) {
        buildRichRoleAnnotated(text, accentColor, secondaryColor)
    }
    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        style = style.copy(color = baseColor),
        onClick = { offset ->
            annotated
                .getStringAnnotations("url", offset, offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        }
    )
}

private fun buildRichRoleAnnotated(
    text: String,
    accentColor: Color,
    secondaryColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")

            var cursor = 0
            while (cursor < line.length) {
                val remaining = line.substring(cursor)

                val inQuoteMatch = Regex("""“(.*?)”""").find(remaining)
                val inParenMatch = Regex("""（(.*?)）|[\(](.*?)[\)]""").find(remaining)

                val nextSpecial = listOfNotNull(
                    inQuoteMatch?.range?.first?.let { it to "quote" },
                    inParenMatch?.range?.first?.let { it to "paren" }
                ).minByOrNull { it.first }

                if (nextSpecial == null) {
                    append(remaining)
                    break
                }

                val (specialStart, type) = nextSpecial
                if (specialStart > 0) {
                    append(remaining.substring(0, specialStart))
                }

                when (type) {
                    "quote" -> {
                        val match = inQuoteMatch!!
                        withStyle(SpanStyle(color = accentColor)) {
                            append(match.value)
                        }
                        cursor += specialStart + match.value.length
                    }
                    "paren" -> {
                        val match = inParenMatch!!
                        withStyle(SpanStyle(color = secondaryColor, fontStyle = FontStyle.Italic)) {
                            append(match.value)
                        }
                        cursor += specialStart + match.value.length
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CharacterChatInputBar(
    value: String,
    isSending: Boolean,
    characterName: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionScale by animateFloatAsState(
        targetValue = if (isSending) 0.96f else 1f,
        animationSpec = tween(180),
        label = "characterActionScale"
    )

    Surface(
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer {
                    scaleX = actionScale
                    scaleY = actionScale
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("回复 ${characterName}...") },
                enabled = !isSending,
                maxLines = 5,
                minLines = 1,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            if (isSending) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(40.dp).graphicsLayer {
                        scaleX = actionScale
                        scaleY = actionScale
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.onError)
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    modifier = Modifier.size(40.dp).graphicsLayer {
                        scaleX = actionScale
                        scaleY = actionScale
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    SmoothCharacterTriangleIcon(color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun SmoothCharacterTriangleIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParametersBottomSheet(
    conversation: ConversationEntity,
    uiState: ChatUiState,
    onDismiss: () -> Unit,
    onUpdateParameters: (String?, Float, Float, Int, Int) -> Unit,
    onEditSystemPrompt: () -> Unit
) {
    var modelValue by remember { mutableStateOf(conversation.modelName ?: uiState.selectedModel) }
    var temperature by remember { mutableStateOf(conversation.temperature) }
    var topP by remember { mutableStateOf(conversation.topP) }
    var maxTokensText by remember { mutableStateOf(conversation.maxTokens.toString()) }
    var contextLimit by remember { mutableStateOf(conversation.contextLimit) }
    var modelExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("角色参数设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("这里控制当前角色对话的回复风格、采样范围和单次输出长度。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    listOf(modelValue, uiState.selectedModel).distinct().filter { it.isNotBlank() }.forEach { model ->
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
            Text("数值越高，角色说话越活、越跳脱；越低则更稳、更克制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f)
            Text("采样范围 ${"%.2f".format(topP)}", fontWeight = FontWeight.SemiBold)
            Text("数值越大，措辞变化越丰富；越小则更集中、更保守。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = topP, onValueChange = { topP = it }, valueRange = 0f..1f)
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { maxTokensText = it.filter(Char::isDigit).take(6) },
                label = { Text("单次最大回复长度") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("值越大，角色单次回复越长，但生成时间和消耗也会更高。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("上下文轮数: $contextLimit")
            Text("控制角色一次能记住多少轮最近对话。值越大，延续性更强。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = contextLimit.toFloat(),
                onValueChange = { contextLimit = it.toInt().coerceIn(1, 999) },
                valueRange = 1f..999f
            )
            Button(
                onClick = onEditSystemPrompt,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("编辑系统提示词")
            }
            Button(
                onClick = {
                    onUpdateParameters(
                        modelValue,
                        temperature,
                        topP,
                        maxTokensText.toIntOrNull() ?: 8192,
                        contextLimit
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存参数")
            }
        }
    }
}

/**
 * 构建场景捕捉生图提示词
 * 从角色卡信息 + 最近对话中提取场景，生成POV视角的英文提示词
 * 核心思路：让AI"看到"你此刻眼中的角色，而不是凭空画一个角色
 */
private fun buildSceneImagePrompt(character: CharacterCardEntity, messages: List<ChatMessageEntity>): String {
    val name = character.name.ifBlank { "character" }

    // 1. 提取角色外观——尽可能完整保留角色卡的人设描述
    val appearance = character.description
        .take(600)
        .ifBlank { character.greeting.take(300) }
        .ifBlank { "anime style character" }

    // 2. 从最近对话中提取「此刻正在发生什么」
    val recentMessages = messages.takeLast(4)
    val lastUserMsg = recentMessages.lastOrNull { it.role == "user" }?.content?.take(200).orEmpty()
    val lastAssistantMsg = recentMessages.lastOrNull { it.role == "assistant" }?.content?.take(200).orEmpty()

    // 3. 提取角色当前的情绪/动作关键词
    val emotionKeywords = listOf(
        "害羞", "脸红", "微笑", "笑", "开心", "温柔", "担心", "难过", "生气",
        "靠在", "躺在", "坐在", "抱着", "牵着", "看着", "靠近", "依偎", "撒娇",
        "blush", "smile", "shy", "gentle", "tender", "happy", "worried", "leaning",
        "lying on", "sitting", "holding", "looking at", "cuddling", "embracing"
    )
    val actionCues = buildString {
        for (keyword in emotionKeywords) {
            if (lastAssistantMsg.contains(keyword, ignoreCase = true)) {
                append("$keyword, ")
            }
        }
    }.trimEnd(',', ' ')

    // 4. 拼接最终提示词——POV视角 + 外观 + 当前场景 + 情绪
    return buildString {
        // POV 视角——明确告诉AI这是第一人称视角
        append("(masterpiece, best quality), ")
        append("POV shot, first-person perspective. ")
        append("You are looking at $name. ")

        // 角色外观——从角色卡继承
        append("$name looks exactly like this: $appearance. ")

        // 当前场景——从对话中提取
        if (lastUserMsg.isNotBlank() || lastAssistantMsg.isNotBlank()) {
            append("Current scene: ")
            if (lastUserMsg.isNotBlank()) {
                append("you and $name are interacting, $lastUserMsg. ")
            }
            if (lastAssistantMsg.isNotBlank()) {
                append("$name's response: $lastAssistantMsg. ")
            }
        }

        // 情绪动作——从对话中提取的标签
        if (actionCues.isNotBlank()) {
            append("$name is showing these emotions: $actionCues. ")
        }

        // 风格和质量
        append("Anime art style, soft cel shading, detailed eyes, beautiful lighting, ")
        append("cinematic composition, depth of field, from above POV angle.")
    }
}
