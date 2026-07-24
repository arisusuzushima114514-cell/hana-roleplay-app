package com.hana.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hana.app.BuildConfig
import com.hana.app.core.AppContainer
import com.hana.app.data.settings.SettingsRepository
import com.hana.app.data.db.entity.isMainChatConversation
import com.hana.app.manager.BackgroundTarget
import com.hana.app.manager.LanguageManager
import com.hana.app.manager.SavedBackgroundInfo
import com.hana.app.ui.character.CharacterChatScreen
import com.hana.app.ui.character.CharacterEditScreen
import com.hana.app.ui.character.CharacterListScreen
import com.hana.app.ui.character.StoryCreateScreen
import com.hana.app.ui.chat.ChatScreenWithDrawer
import com.hana.app.ui.components.UpdateChangelogDialog
import com.hana.app.ui.settings.SettingsScreen
import com.hana.app.ui.theme.HanaTheme
import com.hana.app.ui.theme.ThemeMode
import com.hana.app.ui.theme.applyThemeMode
import com.hana.app.viewmodel.ChatViewModel
import com.hana.app.viewmodel.ImageGenerationViewModel
import com.hana.app.viewmodel.MemoryViewModel
import com.hana.app.viewmodel.SettingsViewModel
import com.hana.app.viewmodel.CustomizationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

private enum class MainTab {
    Chat,
    Character,
    Settings
}

private enum class AutoThemeSuggestionMode {
    SuggestDark,
    SuggestLight
}

private data class AutoThemeSuggestion(
    val mode: AutoThemeSuggestionMode,
    val tag: String
)

class MainActivity : AppCompatActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    override fun attachBaseContext(newBase: android.content.Context) {
        val localizedContext = try {
            val language = runBlocking { SettingsRepository(newBase).settingsFlow.first().language }
            LanguageManager.applyLocaleConfig(newBase, language)
        } catch (_: Exception) {
            newBase
        }
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val initialSettings = runBlocking {
                SettingsRepository(applicationContext).settingsFlow.first()
            }
            applyThemeMode(initialSettings.themeMode)
        } catch (_: Exception) {
            applyThemeMode(com.hana.app.ui.theme.ThemeMode.SYSTEM)
        }

        try {
            setContent {
                WorkspaceApp(appContainer = appContainer)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "App crashed during init, attempting recovery", e)
            Toast.makeText(this, "启动异常: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceApp(appContainer: AppContainer) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(appContainer)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(appContainer)
    )
    val customizationViewModel: CustomizationViewModel = viewModel(factory = CustomizationViewModelFactory(appContainer))
    val imageGenerationViewModel: ImageGenerationViewModel = viewModel(
        factory = ImageGenerationViewModelFactory(appContainer)
    )
    val memoryViewModel: MemoryViewModel = viewModel(
        factory = MemoryViewModelFactory(appContainer)
    )

    val uiState by chatViewModel.uiState.collectAsState()
    val promptPreviewState by chatViewModel.promptPreviewState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val customizationState by customizationViewModel.uiState.collectAsState()
    val imageState by imageGenerationViewModel.uiState.collectAsState()
    val memoryState by memoryViewModel.uiState.collectAsState()
    val language by settingsViewModel.language.collectAsState(initial = settingsState.language)
    val keyboardController = LocalSoftwareKeyboardController.current
    val appSnackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var imageModeEnabled by rememberSaveable { mutableStateOf(false) }
    var autoThemeSuggestion by rememberSaveable { mutableStateOf<AutoThemeSuggestion?>(null) }
    var disableAutoThemeSuggestion by rememberSaveable { mutableStateOf(false) }
    val currentTimeBucket by produceState(initialValue = currentAutoThemeSuggestion()) {
        while (true) {
            value = currentAutoThemeSuggestion()
            delay(60_000)
        }
    }

    LaunchedEffect(language) {
        LanguageManager.applyLanguage(language)
    }

    LaunchedEffect(settingsState.themeMode) {
        applyThemeMode(settingsState.themeMode)
    }

    LaunchedEffect(Unit) {
        chatViewModel.checkSpeechAvailability(context)
    }

    LaunchedEffect(Unit) {
        chatViewModel.ensureModelCacheLoaded()
    }

    LaunchedEffect(customizationState.generationCompleteHapticEnabled) {
        chatViewModel.generationCompleted.collect {
            if (customizationState.generationCompleteHapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    LaunchedEffect(currentTimeBucket, settingsState.themeMode, settingsState.autoThemeSuggestionEnabled) {
        val suggestion = currentTimeBucket ?: return@LaunchedEffect
        if (!settingsState.autoThemeSuggestionEnabled || autoThemeSuggestion != null) {
            return@LaunchedEffect
        }
        if (settingsState.themeMode == ThemeMode.SYSTEM) {
            return@LaunchedEffect
        }
        val themeNeedsSwitch = when (suggestion.mode) {
            AutoThemeSuggestionMode.SuggestDark -> settingsState.themeMode != ThemeMode.DARK
            AutoThemeSuggestionMode.SuggestLight -> settingsState.themeMode != ThemeMode.LIGHT
        }
        if (!themeNeedsSwitch || settingsState.lastAutoThemeSuggestionTag == suggestion.tag) {
            return@LaunchedEffect
        }
        autoThemeSuggestion = suggestion
        disableAutoThemeSuggestion = false
        settingsViewModel.markAutoThemeSuggestionShown(suggestion.tag)
    }

    HanaTheme(
        themeMode = settingsState.themeMode,
        palette = settingsState.themePalette,
        aiBubbleImagePath = customizationState.aiBubbleImagePath,
        userBubbleImagePath = customizationState.userBubbleImagePath,
        aiBubbleFixedEdgePercent = customizationState.aiBubbleFixedEdgePercent,
        userBubbleFixedEdgePercent = customizationState.userBubbleFixedEdgePercent,
        chatFontSize = customizationState.chatFontSize,
        chatDensity = customizationState.chatDensity,
        bubbleWidthPercent = customizationState.bubbleWidthPercent,
        showMessageAvatars = customizationState.showMessageAvatars,
        showMessageTime = customizationState.showMessageTime,
        inputBarSize = customizationState.inputBarSize,
        inputBarWidthPercent = customizationState.inputBarWidthPercent
    ) {
        val bottomIcons = Triple(Icons.AutoMirrored.Filled.Chat, Icons.Filled.Person, Icons.Filled.Settings)
        @Composable fun BottomIcon(path: String, fallback: androidx.compose.ui.graphics.vector.ImageVector) {
            val file = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            if (file != null) AsyncImage(model = file, contentDescription = null, modifier = Modifier.size(25.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
            else Icon(fallback, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        var currentTab by rememberSaveable { mutableStateOf(MainTab.Chat) }
        var editingCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
        var creatingCharacter by rememberSaveable { mutableStateOf(false) }
        var creatingStory by rememberSaveable { mutableStateOf(false) }
        var selectedCharacterIdForChat by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedUniverseConversationId by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingBackgroundTarget by rememberSaveable { mutableStateOf("global") }
        var backgroundChoices by remember { mutableStateOf<List<BackgroundChoice>>(emptyList()) }
        var backgroundSourceChoices by remember { mutableStateOf<List<PendingBackgroundSelection>>(emptyList()) }
        var showBackgroundLibrary by remember { mutableStateOf(false) }
        var renamingBackground by remember { mutableStateOf<SavedBackgroundInfo?>(null) }
        var sceneGeneratedUrls by remember { mutableStateOf<List<String>>(emptyList()) }
        var sceneGenError by remember { mutableStateOf<String?>(null) }
        var isGeneratingScene by remember { mutableStateOf(false) }
        var lastBackPressedAt by rememberSaveable { mutableStateOf(0L) }
        val editingCharacter = uiState.characters.firstOrNull { it.id == editingCharacterId }
        val selectedCharacterForChat = uiState.characters.firstOrNull { it.id == selectedCharacterIdForChat }
        val backgroundIntensity = remember(settingsState.backgroundIntensity) { settingsState.backgroundIntensity }

        fun resolveBackgroundTarget(): BackgroundTarget {
            return when {
                pendingBackgroundTarget == "main" -> BackgroundTarget.MainChat
                pendingBackgroundTarget.startsWith("character:") -> BackgroundTarget.Character(
                    pendingBackgroundTarget.removePrefix("character:")
                )
                else -> BackgroundTarget.Global
            }
        }

        fun setPendingBackgroundTarget(target: BackgroundTarget) {
            pendingBackgroundTarget = when (target) {
                BackgroundTarget.Global -> "global"
                BackgroundTarget.MainChat -> "main"
                is BackgroundTarget.Character -> "character:${target.characterId}"
            }
        }

        fun requestBackgroundSelection(choices: List<BackgroundChoice>) {
            backgroundChoices = choices
        }

        BackHandler {
            when {
                backgroundChoices.isNotEmpty() -> {
                    backgroundChoices = emptyList()
                }
                backgroundSourceChoices.isNotEmpty() -> {
                    backgroundSourceChoices = emptyList()
                }
                showBackgroundLibrary -> {
                    showBackgroundLibrary = false
                }
                renamingBackground != null -> {
                    renamingBackground = null
                }
                selectedCharacterIdForChat != null -> {
                    selectedCharacterIdForChat = null
                    chatViewModel.returnToMainConversation()
                }
                selectedUniverseConversationId != null -> {
                    selectedUniverseConversationId = null
                    chatViewModel.returnToMainConversation()
                }
                currentTab == MainTab.Character && creatingStory -> {
                    creatingStory = false
                }
                currentTab == MainTab.Character && creatingCharacter -> {
                    creatingCharacter = false
                }
                currentTab == MainTab.Character && editingCharacterId != null -> {
                    editingCharacterId = null
                }
                currentTab == MainTab.Settings -> {
                    currentTab = MainTab.Chat
                }
                currentTab == MainTab.Character -> {
                    currentTab = MainTab.Chat
                }
                currentTab == MainTab.Chat && imageModeEnabled -> {
                    imageModeEnabled = false
                }
                else -> {
                    keyboardController?.hide()
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressedAt < 2000L) {
                        activity?.finish()
                    } else {
                        lastBackPressedAt = now
                        appScope.launch {
                            appSnackbarHostState.currentSnackbarData?.dismiss()
                            appSnackbarHostState.showSnackbar(context.getString(R.string.back_press_exit))
                        }
                    }
                }
            }
        }

        val backgroundPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                chatViewModel.saveCustomBackground(uri, resolveBackgroundTarget())
            }
        }
        val backupExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                chatViewModel.exportAllDataToUri(context, uri) { ok, msg ->
                    Toast.makeText(context, if (ok) msg else "导出失败: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val memoryExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                memoryViewModel.export(context, uri)
            }
        }
        val memoryImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                memoryViewModel.import(context, uri)
            }
        }
        val characterImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                chatViewModel.importCharacterCard(context, uri) { ok, msg ->
                    Toast.makeText(context, if (ok) msg else "导入失败: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }
        var pendingAttachmentImageToSave by rememberSaveable { mutableStateOf<String?>(null) }
        val galleryPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val pending = pendingAttachmentImageToSave
            pendingAttachmentImageToSave = null
            if (granted && pending != null) {
                chatViewModel.saveAttachmentImage(pending) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } else if (!granted) {
                Toast.makeText(context, "Android 9 及以下保存图片需要存储权限", Toast.LENGTH_SHORT).show()
            }
        }
        var pendingCharacterExportId by rememberSaveable { mutableStateOf<String?>(null) }
        val characterExportJsonLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val characterId = pendingCharacterExportId
            pendingCharacterExportId = null
            if (uri != null && characterId != null) {
                chatViewModel.exportCharacterCardJson(context, characterId, uri) { ok, msg ->
                    Toast.makeText(context, if (ok) "已导出: $msg" else "导出失败: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }
        var pendingCharacterPngExportId by rememberSaveable { mutableStateOf<String?>(null) }
        val characterExportPngLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("image/png")
        ) { uri ->
            val characterId = pendingCharacterPngExportId
            pendingCharacterPngExportId = null
            if (uri != null && characterId != null) {
                chatViewModel.exportCharacterCardPng(context, characterId, uri) { ok, msg ->
                    Toast.makeText(context, if (ok) "已导出: $msg" else "导出失败: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }

        BackgroundScopeDialog(
            backgroundChoices = backgroundChoices,
            onDismiss = { backgroundChoices = emptyList() },
            onChoiceSelected = { choice ->
                backgroundChoices = emptyList()
                backgroundSourceChoices = listOf(PendingBackgroundSelection(choice.target, choice.label))
            }
        )

        BackgroundSourceDialog(
            backgroundSourceChoices = backgroundSourceChoices,
            chatViewModel = chatViewModel,
            backgroundPicker = backgroundPicker,
            onDismiss = { backgroundSourceChoices = emptyList() },
            onOpenLibrary = { showBackgroundLibrary = true },
            onTargetSelected = ::setPendingBackgroundTarget
        )

        BackgroundLibrarySheet(
            showBackgroundLibrary = showBackgroundLibrary,
            chatViewModel = chatViewModel,
            selectedCharacterForChatId = selectedCharacterForChat?.id,
            resolveBackgroundTarget = ::resolveBackgroundTarget,
            backgroundPicker = backgroundPicker,
            onDismiss = { showBackgroundLibrary = false },
            onTargetSelected = ::setPendingBackgroundTarget,
            onRename = { renamingBackground = it }
        )

        BackgroundRenameDialog(
            background = renamingBackground,
            chatViewModel = chatViewModel,
            onDismiss = { renamingBackground = null }
        )

        // 切换角色时清除场景捕捉残留状态
        LaunchedEffect(selectedCharacterForChat?.id) {
            sceneGeneratedUrls = emptyList()
            sceneGenError = null
            isGeneratingScene = false
        }

        if (selectedUniverseConversationId != null) {
            ChatScreenWithDrawer(
                uiState = uiState,
                onInputChange = chatViewModel::onInputChange,
                onSend = { chatViewModel.sendMessage() },
                onSendText = { chatViewModel.sendMessage(it) },
                onSelectConversation = chatViewModel::selectConversation,
                onNewConversation = { chatViewModel.createNormalConversation() },
                onRenameConversation = chatViewModel::renameConversation,
                onDeleteConversation = chatViewModel::deleteConversation,
                onExportConversation = { conversationId, asMarkdown ->
                    chatViewModel.exportConversation(context, conversationId, asMarkdown) { success, message ->
                        Toast.makeText(context, if (success) "已导出: $message" else "导出失败: $message", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteMessage = chatViewModel::deleteMessage,
                onRegenerateMessage = chatViewModel::regenerateAssistantMessage,
                onEditMessage = chatViewModel::editUserMessage,
                onEditMessageToInput = chatViewModel::editMessageToInput,
                onRetryFromMessage = chatViewModel::retryFromUserMessage,
                onPersistImageAttachment = chatViewModel::persistImageAttachment,
                onPersistFileAttachment = chatViewModel::persistFileAttachment,
                onPersistCameraAttachment = chatViewModel::persistCameraAttachment,
                onSaveAttachmentImage = { uriString -> chatViewModel.saveAttachmentImage(uriString) },
                onStopGeneration = chatViewModel::stopGeneration,
                onRetryLastUserMessage = chatViewModel::retryLastUserMessage,
                onUpdateConversationParameters = chatViewModel::updateConversationParameters,
                onUpdateSystemPrompt = chatViewModel::updateSystemPrompt,
                onToggleWebSearch = chatViewModel::toggleWebSearch,
                onToggleFavorite = chatViewModel::toggleFavorite,
                onTogglePinned = chatViewModel::toggleConversationPinned,
                onToggleConvFav = chatViewModel::toggleConversationFavorite,
                onSelectModel = chatViewModel::switchModelAndApply,
                onToggleModelFavorite = chatViewModel::toggleModelFavorite,
                scrollTrigger = chatViewModel.scrollTrigger,
                errorFlow = chatViewModel.errorFlow,
                personaEnabled = uiState.personaEnabled,
                onTogglePersona = chatViewModel::togglePersona,
                backgroundIntensity = backgroundIntensity,
                onOpenBackgroundLibrary = {
                    setPendingBackgroundTarget(BackgroundTarget.Global)
                    showBackgroundLibrary = true
                },
                onPickBackground = { requestBackgroundSelection(listOf(BackgroundChoice("全局", BackgroundTarget.Global))) },
                onClearBackground = { chatViewModel.clearCustomBackground(BackgroundTarget.Global) },
                promptPreviewState = promptPreviewState,
                interCharacterRelations = uiState.interCharacterRelations,
                onUpdateGroupScene = chatViewModel::updateGroupScene,
                onRefreshPromptPreview = { chatViewModel.refreshPromptPreview() },
                onNavigateBack = {
                    selectedUniverseConversationId = null
                    chatViewModel.returnToMainConversation()
                },
                appDisplayName = customizationState.appDisplayName
            )
        } else if (selectedCharacterForChat != null) {
            CharacterChatScreen(
                character = selectedCharacterForChat,
                uiState = uiState,
                onInputChange = chatViewModel::onInputChange,
                onSend = { chatViewModel.sendMessage() },
                onBack = {
                    selectedCharacterIdForChat = null
                    chatViewModel.returnToMainConversation()
                },
                onDeleteMessage = chatViewModel::deleteMessage,
                onRegenerateMessage = chatViewModel::regenerateAssistantMessage,
                onEditMessage = chatViewModel::editUserMessage,
                onStopGeneration = chatViewModel::stopGeneration,
                onRetryLastUserMessage = chatViewModel::retryLastUserMessage,
                onUpdateConversationParameters = chatViewModel::updateConversationParameters,
                onUpdateSystemPrompt = chatViewModel::updateSystemPrompt,
                onToggleWebSearch = chatViewModel::toggleWebSearch,
                onClearHistory = { characterId -> chatViewModel.clearCharacterChatHistory(characterId) },
                onSaveCharacter = { updated -> chatViewModel.saveCharacter(updated) },
                getCharacterStoryState = chatViewModel::getCharacterStoryState,
                getCharacterStoryLogs = chatViewModel::getCharacterStoryLogs,
                onUpdateRelationshipAnchor = chatViewModel::updateRelationshipAnchor,
                onRestoreAutomaticRelationshipAnchor = chatViewModel::restoreAutomaticRelationshipAnchor,
                creativePresetText = settingsState.creativePresetText,
                creativePresetEnabledForCharacter = settingsState.characterCreativePresetEnabled[selectedCharacterForChat.id] == true,
                creativePresetAffectsPersonaForCharacter = settingsState.characterCreativePresetAffectsPersona[selectedCharacterForChat.id] == true,
                onCreativePresetEnabledChange = { characterId, enabled ->
                    settingsViewModel.onCharacterCreativePresetEnabledChange(characterId, enabled)
                    chatViewModel.updateCreativePresetSnapshot(characterId, enabled = enabled)
                },
                onCreativePresetAffectsPersonaChange = { characterId, enabled ->
                    settingsViewModel.onCharacterCreativePresetAffectsPersonaChange(characterId, enabled)
                    chatViewModel.updateCreativePresetSnapshot(characterId, affectsPersona = enabled)
                },
                onCreativePresetTextChange = { text ->
                    settingsViewModel.onCreativePresetTextChange(text)
                    chatViewModel.updateCreativePresetSnapshot(selectedCharacterForChat.id, text = text)
                },
                characterCreativePresetText = settingsState.characterCreativePresetTexts[selectedCharacterForChat.id].orEmpty(),
                characterCreativePresetEnabled = settingsState.characterIndependentCreativePresetEnabled[selectedCharacterForChat.id] == true,
                characterCreativePresetAffectsPersona = settingsState.characterIndependentCreativePresetAffectsPersona[selectedCharacterForChat.id] == true,
                onCharacterCreativePresetTextChange = { text ->
                    settingsViewModel.onCharacterCreativePresetTextChange(selectedCharacterForChat.id, text)
                    chatViewModel.updateIndependentCreativePresetSnapshot(selectedCharacterForChat.id, text = text)
                },
                onCharacterCreativePresetEnabledChange = { characterId, enabled ->
                    settingsViewModel.onCharacterIndependentCreativePresetEnabledChange(characterId, enabled)
                    chatViewModel.updateIndependentCreativePresetSnapshot(characterId, enabled = enabled)
                },
                onCharacterCreativePresetAffectsPersonaChange = { characterId, enabled ->
                    settingsViewModel.onCharacterIndependentCreativePresetAffectsPersonaChange(characterId, enabled)
                    chatViewModel.updateIndependentCreativePresetSnapshot(characterId, affectsPersona = enabled)
                },
                backgroundIntensity = backgroundIntensity,
                onPickBackground = {
                    requestBackgroundSelection(
                        listOf(
                            BackgroundChoice("仅当前角色", BackgroundTarget.Character(selectedCharacterForChat.id)),
                            BackgroundChoice("主对话", BackgroundTarget.MainChat),
                            BackgroundChoice("全局", BackgroundTarget.Global)
                        )
                    )
                },
                onClearBackground = {
                    chatViewModel.clearCustomBackground(BackgroundTarget.Character(selectedCharacterForChat.id))
                },
                onOpenBackgroundLibrary = {
                    setPendingBackgroundTarget(BackgroundTarget.Character(selectedCharacterForChat.id))
                    showBackgroundLibrary = true
                },
                getCharacterById = chatViewModel::getCharacterById,
                getConversationByCharacterId = { characterId ->
                    uiState.conversations.firstOrNull { it.characterId == characterId }
                },
                errorFlow = chatViewModel.errorFlow,
                onGenerateSceneImage = { prompt ->
                    isGeneratingScene = true
                    sceneGeneratedUrls = emptyList()
                    sceneGenError = null
                    imageGenerationViewModel.generateSceneImage(prompt) { urls, error ->
                        isGeneratingScene = false
                        if (urls.isNotEmpty()) {
                            sceneGeneratedUrls = urls
                        } else {
                            sceneGenError = error
                        }
                    }
                },
                isGeneratingScene = isGeneratingScene,
                sceneImageUrls = sceneGeneratedUrls,
                sceneImageError = sceneGenError,
                onDismissSceneImage = {
                    sceneGeneratedUrls = emptyList()
                    sceneGenError = null
                },
                promptPreviewState = promptPreviewState,
                onRefreshPromptPreview = {
                    chatViewModel.refreshPromptPreview(
                        conversationId = uiState.conversations.firstOrNull { it.characterId == selectedCharacterForChat.id }?.id,
                        characterId = selectedCharacterForChat.id
                    )
                },
                onUpdateContextLayers = chatViewModel::updateConversationContextLayers,
                onSummarizeHistory = chatViewModel::summarizeConversationNow,
                onClearHistorySummary = chatViewModel::clearConversationHistorySummary
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        val navigationColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.Chat,
                            onClick = {
                                currentTab = MainTab.Chat
                                selectedCharacterIdForChat = null
                                imageModeEnabled = false
                                chatViewModel.returnToMainConversation()
                            },
                            icon = { BottomIcon(customizationState.chatTabIconPath, bottomIcons.first) },
                            label = if (customizationState.showBottomBarLabels) ({ Text(context.getString(R.string.tab_chat)) }) else null,
                            alwaysShowLabel = customizationState.showBottomBarLabels,
                            colors = navigationColors
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.Character,
                            onClick = { currentTab = MainTab.Character },
                            icon = { BottomIcon(customizationState.characterTabIconPath, bottomIcons.second) },
                            label = if (customizationState.showBottomBarLabels) ({ Text(context.getString(R.string.tab_character)) }) else null,
                            alwaysShowLabel = customizationState.showBottomBarLabels,
                            colors = navigationColors
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.Settings,
                            onClick = { currentTab = MainTab.Settings },
                            icon = { BottomIcon(customizationState.settingsTabIconPath, bottomIcons.third) },
                            label = if (customizationState.showBottomBarLabels) ({ Text(context.getString(R.string.tab_settings)) }) else null,
                            alwaysShowLabel = customizationState.showBottomBarLabels,
                            colors = navigationColors
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        MainTab.Chat -> {
                            val mainConversations = uiState.conversations.filter { it.isMainChatConversation() }
                            val mainConversationId = uiState.currentConversationId
                                ?.takeIf { currentId -> mainConversations.any { it.id == currentId } }
                                ?: mainConversations.firstOrNull()?.id
                            val mainUiState = uiState.copy(
                                conversations = mainConversations,
                                currentConversationId = mainConversationId,
                                messages = if (uiState.currentConversationId == mainConversationId) uiState.messages else emptyList(),
                                streamingAssistant = uiState.streamingAssistant?.takeIf { it.conversationId == mainConversationId }
                            )
                            ChatScreenWithDrawer(
                                uiState = mainUiState,
                                onInputChange = chatViewModel::onInputChange,
                                onSend = { chatViewModel.sendMessage() },
                                onSendText = { chatViewModel.sendMessage(it) },
                                onSelectConversation = chatViewModel::selectConversation,
                                onNewConversation = { chatViewModel.createNormalConversation() },
                                onRenameConversation = chatViewModel::renameConversation,
                                onDeleteConversation = chatViewModel::deleteConversation,
                                onExportConversation = { conversationId, asMarkdown ->
                                    chatViewModel.exportConversation(context, conversationId, asMarkdown) { success, message ->
                                        Toast.makeText(
                                            context,
                                            if (success) "已导出: $message" else "导出失败: $message",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDeleteMessage = chatViewModel::deleteMessage,
                                onRegenerateMessage = chatViewModel::regenerateAssistantMessage,
                                onEditMessage = chatViewModel::editUserMessage,
                                onEditMessageToInput = chatViewModel::editMessageToInput,
                                onPersistImageAttachment = chatViewModel::persistImageAttachment,
                                onPersistFileAttachment = chatViewModel::persistFileAttachment,
                                onPersistCameraAttachment = chatViewModel::persistCameraAttachment,
                                 onSaveAttachmentImage = { uriString ->
                                     if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                         ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                     ) {
                                         pendingAttachmentImageToSave = uriString
                                         galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                     } else {
                                         chatViewModel.saveAttachmentImage(uriString) { msg ->
                                             Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                         }
                                     }
                                 },
                                onStopGeneration = chatViewModel::stopGeneration,
                                onRetryLastUserMessage = chatViewModel::retryLastUserMessage,
                                onRetryFromMessage = chatViewModel::retryFromUserMessage,
                                onUpdateConversationParameters = chatViewModel::updateConversationParameters,
                                onUpdateSystemPrompt = chatViewModel::updateSystemPrompt,
                                onToggleWebSearch = chatViewModel::toggleWebSearch,
                                onToggleFavorite = chatViewModel::toggleFavorite,
                                onTogglePinned = chatViewModel::toggleConversationPinned,
                                onToggleConvFav = chatViewModel::toggleConversationFavorite,
                                onSelectModel = chatViewModel::switchModelAndApply,
                                onToggleModelFavorite = chatViewModel::toggleModelFavorite,
                                scrollTrigger = chatViewModel.scrollTrigger,
                                errorFlow = chatViewModel.errorFlow,
                                personaEnabled = uiState.personaEnabled,
                                onTogglePersona = chatViewModel::togglePersona,
                                backgroundIntensity = backgroundIntensity,
                                onOpenBackgroundLibrary = {
                                    setPendingBackgroundTarget(BackgroundTarget.MainChat)
                                    showBackgroundLibrary = true
                                },
                                onPickBackground = {
                                    setPendingBackgroundTarget(BackgroundTarget.MainChat)
                                    requestBackgroundSelection(
                                        listOf(
                                            BackgroundChoice("仅主对话", BackgroundTarget.MainChat),
                                            BackgroundChoice("全局", BackgroundTarget.Global)
                                        )
                                    )
                                },
                                onClearBackground = {
                                    chatViewModel.clearCustomBackground(BackgroundTarget.MainChat)
                                },
                                imageModeEnabled = imageModeEnabled,
                                onToggleImageMode = { imageModeEnabled = !imageModeEnabled },
                                imageUiState = imageState,
                                imageEvents = imageGenerationViewModel.events,
                                onImageInputChange = imageGenerationViewModel::onInputChange,
                                onImageStyleChange = imageGenerationViewModel::onStyleChange,
                                onImageRatioChange = imageGenerationViewModel::onRatioChange,
                                onImageBatchCountChange = imageGenerationViewModel::onBatchCountChange,
                                onImageNegativePromptChange = imageGenerationViewModel::onNegativePromptChange,
                                onImageReferencePicked = imageGenerationViewModel::onReferenceImagesPicked,
                                onRemoveImageReference = imageGenerationViewModel::removeReferenceImage,
                                 onClearImageReference = imageGenerationViewModel::clearReferenceImages,
                                 onGenerateImage = imageGenerationViewModel::generate,
                                 onStopImageGeneration = imageGenerationViewModel::stopGeneration,
                                 onSaveGeneratedImage = { imageGenerationViewModel.saveImage(context, it) },
                                 onReuseImagePrompt = imageGenerationViewModel::reusePrompt,
                                 onContinueImageFromResult = imageGenerationViewModel::continueFromImage,
                                 promptPreviewState = promptPreviewState,
                                 interCharacterRelations = uiState.interCharacterRelations,
                                 onUpdateGroupScene = chatViewModel::updateGroupScene,
                                 onRefreshPromptPreview = { chatViewModel.refreshPromptPreview() },
                                 appDisplayName = customizationState.appDisplayName
                             )
                        }

                        MainTab.Character -> {
                            if (creatingStory) {
                                StoryCreateScreen(
                                    onBack = { creatingStory = false },
                                    onStartStory = { title, premise, style, length, model ->
                                        chatViewModel.createStoryConversation(title, premise, style, length, model) { conversationId ->
                                            creatingStory = false
                                            selectedUniverseConversationId = conversationId
                                        }
                                    }
                                )
                            } else if (creatingCharacter || editingCharacter != null) {
                                CharacterEditScreen(
                                    initialCharacter = editingCharacter,
                                    onBack = {
                                        creatingCharacter = false
                                        editingCharacterId = null
                                    },
                                    onSave = { character ->
                                        chatViewModel.saveCharacter(character) {
                                            creatingCharacter = false
                                            editingCharacterId = null
                                        }
                                    },
                                    onDelete = { character ->
                                        chatViewModel.deleteCharacter(character)
                                        creatingCharacter = false
                                        editingCharacterId = null
                                    }
                                )
                            } else {
                                CharacterListScreen(
                                    characters = uiState.characters,
                                    conversations = uiState.conversations,
                                    creativePresetEnabledByCharacter = settingsState.characterCreativePresetEnabled,
                                    creativePresetAffectsPersonaByCharacter = settingsState.characterCreativePresetAffectsPersona,
                                    characterPresetEnabledByCharacter = settingsState.characterIndependentCreativePresetEnabled,
                                    characterPresetAffectsPersonaByCharacter = settingsState.characterIndependentCreativePresetAffectsPersona,
                                    onCreateCharacter = {
                                        creatingCharacter = true
                                        creatingStory = false
                                        editingCharacterId = null
                                    },
                                    onCreateStory = {
                                        creatingStory = true
                                        creatingCharacter = false
                                        editingCharacterId = null
                                    },
                                    onCreateGroupChat = { characters ->
                                        chatViewModel.createGroupConversation(characters) { conversationId ->
                                            selectedUniverseConversationId = conversationId
                                        }
                                    },
                                    onSelectConversation = {
                                        chatViewModel.selectConversation(it)
                                        selectedUniverseConversationId = it
                                    },
                                    onRenameConversation = chatViewModel::renameConversation,
                                    onDeleteConversation = chatViewModel::deleteConversation,
                                    onExportConversation = { conversationId, asMarkdown ->
                                        chatViewModel.exportConversation(context, conversationId, asMarkdown) { success, message ->
                                            Toast.makeText(
                                                context,
                                                 if (success) "已导出: $message" else "导出失败: $message",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onEditCharacter = {
                                        editingCharacterId = it.id
                                        creatingCharacter = false
                                    },
                                    onDeleteCharacter = chatViewModel::deleteCharacter,
                                    onSelectCharacter = { character ->
                                        chatViewModel.createCharacterConversation(character) {
                                            selectedCharacterIdForChat = character.id
                                        }
                                    },
                                    onExportCharacter = { character ->
                                        chatViewModel.exportCharacterConversation(context, character.id) { ok, msg ->
                                            Toast.makeText(context, if (ok) "已导出: $msg" else "导出失败: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onExportCharacterJson = { character ->
                                        pendingCharacterExportId = character.id
                                        val safeName = character.name.replace(Regex("""[\\/:*?\"<>|]"""), "_")
                                        characterExportJsonLauncher.launch("${safeName.ifBlank { "character" }}.json")
                                    },
                                    onExportCharacterPng = { character ->
                                        pendingCharacterPngExportId = character.id
                                        val safeName = character.name.replace(Regex("""[\\/:*?\"<>|]"""), "_")
                                        characterExportPngLauncher.launch("${safeName.ifBlank { "character" }}.png")
                                    },
                                    onImportCharacterJson = {
                                        characterImportLauncher.launch(arrayOf("application/json", "text/plain", "image/png"))
                                    }
                                )
                            }
                        }

                        MainTab.Settings -> {
                            SettingsScreen(
                                uiState = settingsState,
                                onSelectedModelChange = settingsViewModel::onSelectedModelChange,
                                onLanguageChange = settingsViewModel::onLanguageChange,
                                onClearAllConversations = {
                                    selectedCharacterIdForChat = null
                                    currentTab = MainTab.Chat
                                    imageModeEnabled = false
                                    chatViewModel.clearAllConversations()
                                },
                                onClearMessagesOnly = chatViewModel::clearAllMessagesOnly,
                                onClearModelCache = chatViewModel::clearModelCache,
                                onExportAllData = {
                                    backupExportLauncher.launch("hana_backup_${System.currentTimeMillis()}.json")
                                },
                                onPickBackground = {
                                    setPendingBackgroundTarget(BackgroundTarget.Global)
                                    requestBackgroundSelection(
                                        listOf(
                                            BackgroundChoice("全局", BackgroundTarget.Global),
                                            BackgroundChoice("主对话", BackgroundTarget.MainChat)
                                        )
                                    )
                                },
                                onClearBackground = { chatViewModel.clearCustomBackground(BackgroundTarget.Global) },
                                onClearBackgroundCache = chatViewModel::clearAllBackgrounds,
                                onSelectiveCleanup = chatViewModel::performSelectiveCleanup,
                                 onThemeModeChange = settingsViewModel::onThemeModeChange,
                                 onThemePaletteChange = settingsViewModel::onThemePaletteChange,
                                onAutoThemeSuggestionEnabledChange = settingsViewModel::onAutoThemeSuggestionEnabledChange,
                                onToggleWebSearch = chatViewModel::toggleWebSearch,
                                onToggleStream = settingsViewModel::toggleStream,
                                webSearchEnabled = uiState.webSearchEnabled,
                                savedModels = uiState.savedModels,
                                modelList = uiState.modelList,
                                providerModels = settingsState.providerModels,
                                onSelectDefaultModel = chatViewModel::switchDefaultModel,
                                onToggleDefaultModelFavorite = chatViewModel::toggleModelFavorite,
                                onSelectProviderModelInstance = chatViewModel::switchProviderAndModel,
                                onAddModel = { name, key, url -> chatViewModel.addModel(name, key, url) },
                                onSwitchModel = { chatViewModel.switchModel(it) },
                                onTestProvider = { provider, onResult -> settingsViewModel.testProvider(provider, onResult) },
                                onFetchProviderModels = { provider, onResult -> settingsViewModel.fetchProviderModels(provider, onResult) },
                                onQueryProviderAccount = { provider, onResult -> settingsViewModel.queryProviderAccount(provider, onResult) },
                                onUpdateProvider = chatViewModel::updateSavedProvider,
                                onDeleteModel = { chatViewModel.deleteModel(it) },
                                onRefreshModels = { chatViewModel.refreshModelList() },
                                imageProviderId = settingsState.imageProviderId,
                                imageModelName = settingsState.imageModelName,
                                backgroundIntensity = settingsState.backgroundIntensity,
                                onImageProviderChange = settingsViewModel::onImageProviderChange,
                                onImageModelNameChange = settingsViewModel::onImageModelNameChange,
                                onSummaryBaseUrlChange = settingsViewModel::onSummaryBaseUrlChange,
                                onSummaryApiKeyChange = settingsViewModel::onSummaryApiKeyChange,
                                onSummaryModelNameChange = settingsViewModel::onSummaryModelNameChange,
                                onAutoSummaryThresholdChange = settingsViewModel::onAutoSummaryThresholdChange,
                                onBackgroundIntensityChange = settingsViewModel::onBackgroundIntensityChange,
                                onSaveSearchSettings = settingsViewModel::saveSearchSettings,
                                personaEnabled = settingsState.personaEnabled,
                                personaPrompt = settingsState.personaPrompt,
                                onPersonaSettingsChange = settingsViewModel::savePersonaSettings,
                                storageSummary = chatViewModel.storageSummary(),
                                storageBreakdown = chatViewModel.storageBreakdown(),
                                memoryUiState = memoryState,
                                memoryEvents = memoryViewModel.events,
                                onToggleMemoryPinned = memoryViewModel::togglePinned,
                                onArchiveMemory = memoryViewModel::archive,
                                onUpdateMemory = memoryViewModel::update,
                                onExportMemory = { memoryExportLauncher.launch("hana_memory_${System.currentTimeMillis()}.json") },
                                onImportMemory = { memoryImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                                customization = customizationState,
                                onCustomizationDisplayNameChange = customizationViewModel::saveDisplayName,
                                onCustomizationSplashDurationChange = customizationViewModel::saveSplashDuration,
                                onCustomizationCroppedSplashSave = customizationViewModel::saveCroppedSplash,
                                onCustomizationSplashClear = customizationViewModel::clearSplash,
                                onCustomizationGenerationHapticChange = customizationViewModel::saveGenerationHaptic,
                                onCustomizationChatDisplaySave = customizationViewModel::saveChatDisplay,
                                onCustomizationNavigationIconsClear = customizationViewModel::clearNavigationIcons,
                                onCustomizationCroppedAssetSave = customizationViewModel::saveCroppedAsset,
                                onCustomizationBubbleImagesClear = customizationViewModel::clearBubbleImages,
                                onCustomizationAssetClear = customizationViewModel::clearAsset,
                                onCustomizationBubbleFixedEdgeChange = customizationViewModel::saveBubbleFixedEdge,
                                onCustomizationDesktopShortcutRequest = customizationViewModel::requestDesktopShortcut
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(
                hostState = appSnackbarHostState,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }

        autoThemeSuggestion?.let { suggestion ->
            val targetMode = when (suggestion.mode) {
                AutoThemeSuggestionMode.SuggestDark -> ThemeMode.DARK
                AutoThemeSuggestionMode.SuggestLight -> ThemeMode.LIGHT
            }
            val titleRes = when (suggestion.mode) {
                AutoThemeSuggestionMode.SuggestDark -> R.string.auto_theme_night_title
                AutoThemeSuggestionMode.SuggestLight -> R.string.auto_theme_day_title
            }
            val messageRes = when (suggestion.mode) {
                AutoThemeSuggestionMode.SuggestDark -> R.string.auto_theme_night_message
                AutoThemeSuggestionMode.SuggestLight -> R.string.auto_theme_day_message
            }
            AlertDialog(
                onDismissRequest = {
                    if (disableAutoThemeSuggestion) {
                        settingsViewModel.onAutoThemeSuggestionEnabledChange(false)
                        appScope.launch {
                            appSnackbarHostState.showSnackbar(context.getString(R.string.auto_theme_settings_hint))
                        }
                    }
                    autoThemeSuggestion = null
                },
                title = { Text(context.getString(titleRes)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(context.getString(messageRes))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = disableAutoThemeSuggestion,
                                onCheckedChange = { disableAutoThemeSuggestion = it }
                            )
                            Text(
                                text = context.getString(R.string.auto_theme_disable_future),
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        settingsViewModel.onThemeModeChange(targetMode)
                        if (disableAutoThemeSuggestion) {
                            settingsViewModel.onAutoThemeSuggestionEnabledChange(false)
                            appScope.launch {
                                appSnackbarHostState.showSnackbar(context.getString(R.string.auto_theme_settings_hint))
                            }
                        }
                        autoThemeSuggestion = null
                    }) {
                        Text(context.getString(R.string.auto_theme_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (disableAutoThemeSuggestion) {
                            settingsViewModel.onAutoThemeSuggestionEnabledChange(false)
                            appScope.launch {
                                appSnackbarHostState.showSnackbar(context.getString(R.string.auto_theme_settings_hint))
                            }
                        }
                        autoThemeSuggestion = null
                    }) {
                        Text(context.getString(R.string.auto_theme_dismiss))
                    }
                }
            )
        }

        // ===== 版本更新日志弹窗 =====
        var showChangelog by remember { mutableStateOf(false) }
        var hasShownThisSession by remember { mutableStateOf(false) }
        val settingsRepo = remember { SettingsRepository(context) }
        val currentVersion = BuildConfig.VERSION_NAME
        val currentChangelogId = "$currentVersion-relationship-r3"

        LaunchedEffect(Unit) {
            settingsRepo.getLastSeenChangelogVersion().collect { seen ->
                if (!hasShownThisSession && seen != currentChangelogId && currentVersion.isNotBlank()) {
                    showChangelog = true
                    hasShownThisSession = true
                }
            }
        }

        if (showChangelog) {
            UpdateChangelogDialog(
                versionName = currentVersion,
                changelog = CHANGELOG_V1_8_0,
                onDismiss = { showChangelog = false },
                onAcknowledge = {
                    appScope.launch { settingsRepo.markChangelogSeen(currentChangelogId) }
                }
            )
        }
    }
}

/**
 * v1.8.0 更新内容，供弹窗显示。
 */
private val CHANGELOG_V1_8_0 = """
### 大量个性化

- 新增跟随系统、浅色、深色与五组推荐颜色
- 支持应用内名称、自定义桌面入口、开屏图片与显示时长
- 支持三个底栏图标独立选择、裁剪、清除和整组恢复
- 支持 AI 与用户气泡图片、九宫格拉伸和固定边缘调整
- 个性化配置和原图独立持久化，覆盖更新后继续保留

### 聊天显示

- 新增字体大小、聊天密度、气泡最大宽度和输入框宽度
- 输入框高度会真实改变最小高度和可见行数
- 可控制消息头像、消息时间与底栏文字
- 主对话、群聊和角色聊天统一使用同一套显示配置
- 设置页提供即时预览、草稿状态和标准方案恢复

### 桌面入口与资源管理

- 自定义桌面入口支持更新已有名称和图标
- 支持单独清除任意底栏图标、桌面图标或一侧气泡素材
- 图片保存失败时恢复旧文件，避免个性化资源丢失
- 超大相册图片使用采样解码，降低裁剪和旧设备开屏崩溃风险

### 角色聊天与安全操作

- 模型设定、破甲提示和角色面板使用独立入口
- 角色模型和参数按照真实请求优先级保存
- 重新生成只针对有效助手回复
- 重置当前对话会恢复开场白并明确重置关系状态
- 会话、服务商和清空全部对话等危险操作补充确认

### 关系与内心算法迭代

- 好感、信任和紧张只由用户事件与角色公开回应成对确认，内心不再直接改写长期关系
- 一轮复合互动按事件类型去重分析，威胁和冲突不会与告白、支持等信号重复累计
- 引用、假设、否定、第三方转述和其他角色台词不会污染当前角色关系
- 内心想法只用于即时状态解释，并与其他角色的公开认知严格隔离
- 多角色公开正文按角色名前缀拆分，未闭合内心标签不会在流式输出时泄漏到正文
- 角色间关系分析忽略私密内心、引用和假设，只记录明确公开的支持、竞争或敌意

### 稳定性

- 修复角色会话初始化期间快速发送可能串到主对话的问题
- 修复数据库半迁移时重复字段导致的启动崩溃
- 修复语言切换重建、Prompt 状态检测和生图配置污染问题
- 补齐英文资源并通过 Debug Lint 与 Release Lint Vital
""".trimIndent()

/**
 * v1.7.4 更新内容，供弹窗显示。
 */
private val CHANGELOG_V1_7_4 = """
### 长对话防退化（重要）

- 每次回复前强制注入角色卡锚点，确保角色身份始终在模型注意力窗口内
- 锚点包含角色身份、风格样本、内心想法示例和禁止事项
- 上下文压缩：超过30轮自动摘要旧对话，保留最近30轮完整内容
- 不再出现40轮后角色失忆变人机的问题

### 内心想法强化

- 内心想法指令前置到锚点最前面，不再被忽略
- 提取角色真实内心想法作为格式示例
- 精炼指令：要精不要长，一两句话戳中核心

### 风格样本大幅增强

- 从对话开头提取高质量回复作为风格参照（前8条，每条400字）
- 额外注入角色开场白作为风格样本（500字）
- 避免人机化回复污染风格，保持角色一致性

### 角色对话界面精简

- 顶部栏精简为单行，冗余功能收入三点菜单
- 底部输入栏紧凑化，缩小圆角和阴影
- 键盘弹出时对话区域不再被挤压，可正常阅读

### 多机型适配

- 参数面板支持垂直滚动，小屏不裁切按钮
- Slider 流畅度优化，低端设备不卡顿
- 上下文轮数范围统一为 1~999

### 其他优化

- 默认最大回复长度 4096 → 8192 tokens
- 参数保存后即时同步 UI，不显示旧值
- 深色模式配色优化
- 新增版本更新推送弹窗，每次更新自动展示""".trimIndent()

private fun currentAutoThemeSuggestion(now: Calendar = Calendar.getInstance()): AutoThemeSuggestion? {
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val dayTag = String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH) + 1,
        now.get(Calendar.DAY_OF_MONTH)
    )
    return when {
        hour >= 22 -> AutoThemeSuggestion(AutoThemeSuggestionMode.SuggestDark, "night-$dayTag")
        hour < 6 -> {
            val previous = now.clone() as Calendar
            previous.add(Calendar.DAY_OF_YEAR, -1)
            val previousTag = String.format(
                java.util.Locale.US,
                "%04d-%02d-%02d",
                previous.get(Calendar.YEAR),
                previous.get(Calendar.MONTH) + 1,
                previous.get(Calendar.DAY_OF_MONTH)
            )
            AutoThemeSuggestion(AutoThemeSuggestionMode.SuggestDark, "night-$previousTag")
        }
        hour in 6..21 -> AutoThemeSuggestion(AutoThemeSuggestionMode.SuggestLight, "day-$dayTag")
        else -> null
    }
}
