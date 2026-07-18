package com.hana.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hana.app.BuildConfig
import com.hana.app.core.AppContainer
import com.hana.app.data.settings.SettingsRepository
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
import kotlinx.coroutines.delay
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val initialThemeMode = runBlocking {
                SettingsRepository(applicationContext).getSettings().themeMode
            }
            applyThemeMode(initialThemeMode)
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
    val imageGenerationViewModel: ImageGenerationViewModel = viewModel(
        factory = ImageGenerationViewModelFactory(appContainer)
    )
    val memoryViewModel: MemoryViewModel = viewModel(
        factory = MemoryViewModelFactory(appContainer)
    )

    val uiState by chatViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val imageState by imageGenerationViewModel.uiState.collectAsState()
    val memoryState by memoryViewModel.uiState.collectAsState()
    val language by settingsViewModel.language.collectAsState(initial = settingsState.language)
    val keyboardController = LocalSoftwareKeyboardController.current
    val appSnackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
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

    HanaTheme(themeMode = settingsState.themeMode) {
        var currentTab by rememberSaveable { mutableStateOf(MainTab.Chat) }
        var editingCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
        var creatingCharacter by rememberSaveable { mutableStateOf(false) }
        var creatingStory by rememberSaveable { mutableStateOf(false) }
        var selectedCharacterIdForChat by rememberSaveable { mutableStateOf<String?>(null) }
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
        var pendingCharacterExportId by remember { mutableStateOf<String?>(null) }
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
        var pendingCharacterPngExportId by remember { mutableStateOf<String?>(null) }
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

        if (selectedCharacterForChat != null) {
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
                onDeleteLastRound = { convId -> chatViewModel.deleteLastRound(convId) },
                onSaveCharacter = { updated -> chatViewModel.saveCharacter(updated) },
                getCharacterStoryState = chatViewModel::getCharacterStoryState,
                getCharacterStoryLogs = chatViewModel::getCharacterStoryLogs,
                creativePresetText = settingsState.creativePresetText,
                creativePresetEnabledForCharacter = settingsState.characterCreativePresetEnabled[selectedCharacterForChat.id] == true,
                creativePresetAffectsPersonaForCharacter = settingsState.characterCreativePresetAffectsPersona[selectedCharacterForChat.id] == true,
                onCreativePresetEnabledChange = settingsViewModel::onCharacterCreativePresetEnabledChange,
                onCreativePresetAffectsPersonaChange = settingsViewModel::onCharacterCreativePresetAffectsPersonaChange,
                onCreativePresetTextChange = settingsViewModel::onCreativePresetTextChange,
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
                }
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentTab == MainTab.Chat,
                            onClick = {
                                currentTab = MainTab.Chat
                                selectedCharacterIdForChat = null
                                imageModeEnabled = false
                                chatViewModel.returnToMainConversation()
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                            label = { Text(context.getString(R.string.tab_chat)) }
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.Character,
                            onClick = { currentTab = MainTab.Character },
                            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            label = { Text(context.getString(R.string.tab_character)) }
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.Settings,
                            onClick = { currentTab = MainTab.Settings },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text(context.getString(R.string.tab_settings)) }
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
                                        Toast.makeText(
                                            context,
                                            if (success) "已导出到 Downloads: $message" else "导出失败: $message",
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
                                    chatViewModel.saveAttachmentImage(uriString) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                                onContinueImageFromResult = imageGenerationViewModel::continueFromImage
                            )
                        }

                        MainTab.Character -> {
                            if (creatingStory) {
                                StoryCreateScreen(
                                    onBack = { creatingStory = false },
                                    onStartStory = { title, premise, style, length, model ->
                                        chatViewModel.createStoryConversation(title, premise, style, length, model) {
                                            creatingStory = false
                                            currentTab = MainTab.Chat
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
                                        chatViewModel.createGroupConversation(characters)
                                        currentTab = MainTab.Chat
                                        selectedCharacterIdForChat = null
                                    },
                                    onSelectConversation = {
                                        chatViewModel.selectConversation(it)
                                        currentTab = MainTab.Chat
                                        selectedCharacterIdForChat = null
                                    },
                                    onRenameConversation = chatViewModel::renameConversation,
                                    onDeleteConversation = chatViewModel::deleteConversation,
                                    onExportConversation = { conversationId, asMarkdown ->
                                        chatViewModel.exportConversation(context, conversationId, asMarkdown) { success, message ->
                                            Toast.makeText(
                                                context,
                                                if (success) "已导出到 Downloads: $message" else "导出失败: $message",
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
                                        chatViewModel.createCharacterConversation(character)
                                        selectedCharacterIdForChat = character.id
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
                                onImportMemory = { memoryImportLauncher.launch(arrayOf("application/json", "text/plain")) }
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

        LaunchedEffect(Unit) {
            settingsRepo.getLastSeenChangelogVersion().collect { seen ->
                if (!hasShownThisSession && seen != currentVersion && currentVersion.isNotBlank()) {
                    showChangelog = true
                    hasShownThisSession = true
                }
            }
        }

        if (showChangelog) {
            UpdateChangelogDialog(
                versionName = currentVersion,
                changelog = CHANGELOG_V1_7_5,
                onDismiss = { showChangelog = false },
                onDontShowAgain = { dontShow ->
                    appScope.launch {
                        if (dontShow) {
                            settingsRepo.markChangelogSeen(currentVersion)
                        }
                    }
                }
            )
        }
    }
}

/**
 * v1.7.5 更新内容，供弹窗显示。
 */
private val CHANGELOG_V1_7_5 = """
### 回复质量大幅优化（重要）

- 锚点新增「剧情回应要求」：强制逐一回应所有剧情点，不再只读最后两个
- 风格样本精简（8条→5条，每条400字→300字），释放 ~1000 tokens 给用户消息
- 上下文压缩阈值 30轮→10轮，让摘要机制默认生效，防止 token 溢出
- 单条消息截断 16000字→8000字，防止超长消息撑爆上下文
- 历史摘要优化为结构化事件列表，参考 OpenTavern 总结机制

### Bug 修复

- 角色卡导入头像空白：PNG角色卡导入时自动保存图片为本地头像
- 更新推送弹窗重复弹出：增加 hasShownThisSession 守卫，每次启动只弹一次

### 交互优化

- 用户消息支持一键复制（三点菜单），与角色消息一致
- 蓝色气泡内选中文字可见性修复：高亮色改为半透明白色
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
