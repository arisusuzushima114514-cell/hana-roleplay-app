package com.hana.app.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hana.app.R
import com.hana.app.data.db.entity.ModelCapabilityMap
import com.hana.app.data.db.entity.ModelInfo
import com.hana.app.data.db.entity.SavedModelEntity
import com.hana.app.data.remote.ProviderAccountInfo
import com.hana.app.data.settings.AppLanguage
import com.hana.app.ui.chat.ModelPickerSheet
import com.hana.app.ui.theme.ThemeMode
import com.hana.app.viewmodel.SettingsUiState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
private fun WebSearchSupportBadge(supported: Boolean, label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (supported) Color(0xFF16A34A).copy(alpha = 0.14f) else Color(0xFFDC2626).copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (supported) Color(0xFF15803D) else Color(0xFFB91C1C)
        )
    }
}

private enum class SettingsPage { MAIN, API_MGMT, APPEARANCE, SEARCH, MEMORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSelectedModelChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onClearAllConversations: () -> Unit,
    onClearMessagesOnly: () -> Unit = {},
    onClearModelCache: () -> Unit = {},
    onClearBackgroundCache: () -> Unit = {},
    onSelectiveCleanup: (Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onExportAllData: () -> Unit = {},
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAutoThemeSuggestionEnabledChange: (Boolean) -> Unit = {},
    onToggleWebSearch: () -> Unit = {},
    onToggleStream: (Boolean) -> Unit = {},
    webSearchEnabled: Boolean = false,
    savedModels: List<SavedModelEntity> = emptyList(),
    modelList: List<ModelInfo> = emptyList(),
    providerModels: Map<Long, List<String>> = emptyMap(),
    onSelectDefaultModel: (ModelInfo) -> Unit = {},
    onToggleDefaultModelFavorite: (String) -> Unit = {},
    onSelectProviderModelInstance: (SavedModelEntity, String) -> Unit = { _, _ -> },
    onAddModel: (String, String, String) -> Unit = { _, _, _ -> },
    onSwitchModel: (SavedModelEntity) -> Unit = {},
    onTestProvider: (SavedModelEntity, (String) -> Unit) -> Unit = { _, _ -> },
    onFetchProviderModels: (SavedModelEntity, (String) -> Unit) -> Unit = { _, _ -> },
    onQueryProviderAccount: (SavedModelEntity, (Result<ProviderAccountInfo>) -> Unit) -> Unit = { _, _ -> },
    onUpdateProvider: (SavedModelEntity) -> Unit = {},
    onDeleteModel: (SavedModelEntity) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    imageProviderId: Long = 0L,
    imageModelName: String = "",
    backgroundIntensity: String = "soft",
    onImageProviderChange: (Long) -> Unit = {},
    onImageModelNameChange: (String) -> Unit = {},
    onBackgroundIntensityChange: (String) -> Unit = {},
    onSaveSearchSettings: (String, String, Boolean) -> Unit = { _, _, _ -> },
    personaEnabled: Boolean = false,
    personaPrompt: String = "",
    onPersonaSettingsChange: (Boolean, String) -> Unit = { _, _ -> },
    storageSummary: Triple<Int, Int, Long> = Triple(0, 0, 0),
    storageBreakdown: com.hana.app.viewmodel.ChatViewModel.StorageBreakdown? = null,
    memoryUiState: com.hana.app.viewmodel.MemoryUiState? = null,
    memoryEvents: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onToggleMemoryPinned: (com.hana.app.data.db.entity.MemoryEntryEntity) -> Unit = {},
    onArchiveMemory: (com.hana.app.data.db.entity.MemoryEntryEntity) -> Unit = {},
    onUpdateMemory: (com.hana.app.data.db.entity.MemoryEntryEntity, String, String) -> Unit = { _, _, _ -> },
    onExportMemory: () -> Unit = {},
    onImportMemory: () -> Unit = {}
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    var autoFetchingProviders by remember { mutableStateOf(setOf<Long>()) }
    var attemptedAutoFetchProviders by remember { mutableStateOf(setOf<Long>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(savedModels, providerModels) {
        savedModels.forEach { provider ->
            if (provider.baseUrl.isBlank() || provider.apiKey.isBlank()) return@forEach
            if (providerModels[provider.id].orEmpty().isNotEmpty()) return@forEach
            if (autoFetchingProviders.contains(provider.id)) return@forEach
            if (attemptedAutoFetchProviders.contains(provider.id)) return@forEach

            autoFetchingProviders = autoFetchingProviders + provider.id
            attemptedAutoFetchProviders = attemptedAutoFetchProviders + provider.id
            onFetchProviderModels(provider) {
                scope.launch {
                    autoFetchingProviders = autoFetchingProviders - provider.id
                }
            }
        }
    }

    when (page) {
        SettingsPage.MAIN -> MainPage(uiState, savedModels, webSearchEnabled, modelList, onSelectDefaultModel,
            providerModels, autoFetchingProviders, onToggleDefaultModelFavorite, onSelectProviderModelInstance,
            { page = SettingsPage.API_MGMT }, { page = SettingsPage.SEARCH }, { page = SettingsPage.APPEARANCE }, { page = SettingsPage.MEMORY },
            onToggleWebSearch, onToggleStream,
            onClearAllConversations, onClearMessagesOnly, onClearModelCache, onExportAllData, onClearBackground,
            onClearBackgroundCache, onSelectiveCleanup,
            personaEnabled, personaPrompt, onPersonaSettingsChange, storageSummary, storageBreakdown)
        SettingsPage.API_MGMT -> ApiPage(uiState, savedModels, { page = SettingsPage.MAIN },
            onAddModel, onSwitchModel, onTestProvider, onFetchProviderModels, onQueryProviderAccount, onUpdateProvider, onDeleteModel, onRefreshModels, onSelectProviderModelInstance,
            imageProviderId, imageModelName, onImageProviderChange, onImageModelNameChange)
        SettingsPage.SEARCH -> SearchSubPage(uiState, { page = SettingsPage.MAIN },
            onToggleWebSearch, onSaveSearchSettings)
        SettingsPage.APPEARANCE -> AppearancePage(uiState, { page = SettingsPage.MAIN },
            onThemeModeChange, onLanguageChange, onPickBackground, onClearBackground, backgroundIntensity, onBackgroundIntensityChange,
            uiState.autoThemeSuggestionEnabled, onAutoThemeSuggestionEnabledChange)
        SettingsPage.MEMORY -> if (memoryUiState != null && memoryEvents != null) {
            MemoryManagementScreen(
                uiState = memoryUiState,
                events = memoryEvents,
                onBack = { page = SettingsPage.MAIN },
                onTogglePinned = onToggleMemoryPinned,
                onArchive = onArchiveMemory,
                onUpdate = onUpdateMemory,
                onExport = onExportMemory,
                onImport = onImportMemory
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 4.dp) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, bgColor: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bgColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = bgColor)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun SwitchSettingRow(icon: ImageVector, bgColor: Color, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bgColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = bgColor)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsHeroCard(
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = accent.copy(alpha = 0.14f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MainPage(
    uiState: SettingsUiState, savedModels: List<SavedModelEntity>, webSearchEnabled: Boolean,
    modelList: List<ModelInfo>, onSelectModel: (ModelInfo) -> Unit,
    providerModels: Map<Long, List<String>>, loadingProviderIds: Set<Long>, onToggleModelFavorite: (String) -> Unit, onSelectProviderModelInstance: (SavedModelEntity, String) -> Unit,
    onApi: () -> Unit, onSearch: () -> Unit, onAppear: () -> Unit, onMemory: () -> Unit,
    onWebSearch: () -> Unit, onStream: (Boolean) -> Unit,
    onClearAll: () -> Unit, onClearMessages: () -> Unit, onClearModelCache: () -> Unit, onExportAll: () -> Unit, onClearBackground: () -> Unit,
    onClearBackgroundCache: () -> Unit, onSelectiveCleanup: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    personaEnabled: Boolean, personaPrompt: String, onPersonaChange: (Boolean, String) -> Unit,
    storageSummary: Triple<Int, Int, Long>,
    storageBreakdown: com.hana.app.viewmodel.ChatViewModel.StorageBreakdown?
) {
    var showClear by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showProviderModelPicker by remember { mutableStateOf(false) }
    var showPersonaEditor by remember { mutableStateOf(false) }
    var showStorageSheet by remember { mutableStateOf(false) }
    var clearMessagesChecked by remember { mutableStateOf(true) }
    var clearModelCacheChecked by remember { mutableStateOf(false) }
    var clearBackgroundChecked by remember { mutableStateOf(false) }
    var clearAllChecked by remember { mutableStateOf(false) }

    val fallbackModels = remember(modelList, uiState.activeProviderAvailableModels, uiState.activeProviderName) {
        if (modelList.isNotEmpty()) {
            modelList
        } else {
            uiState.activeProviderAvailableModels.map { modelName ->
                ModelInfo(
                    id = "fallback::$modelName",
                    name = modelName,
                    provider = uiState.activeProviderName.ifBlank { "当前服务商" },
                    capabilities = ModelCapabilityMap.get(modelName)
                )
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_version_title), fontWeight = FontWeight.SemiBold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                SettingsHeroCard(
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "当前服务商: ${uiState.activeProviderName.ifBlank { "未切换" }} · 当前模型: ${uiState.selectedModel.ifBlank { "未设置" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { showPersonaEditor = true },
                            label = { Text(if (personaEnabled) "偏好已启用" else "偏好设定") }
                        )
                        AssistChip(
                            onClick = onSearch,
                            label = { Text(if (webSearchEnabled) "联网搜索开启" else "联网搜索关闭") }
                        )
                    }
                }
            }
            item {
                SettingsCard("连接与模型") {
                    SettingRow(Icons.Filled.Cloud, Color(0xFF4285F4), "API 服务商", "${savedModels.size} 个已配置", onApi)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(Icons.Filled.AutoAwesome, Color(0xFF8B5CF6), "默认模型", uiState.selectedModel.ifBlank { "未设置" }, { showProviderModelPicker = true })
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(Icons.Filled.Person, Color(0xFFEC4899), "偏好设定", if (personaPrompt.isNotBlank()) if (personaEnabled) "已启用" else "已保存" else "未设置", { showPersonaEditor = true })
                }
            }
            item {
                SettingsCard("对话体验") {
                    SettingRow(Icons.Filled.Language, Color(0xFF10B981), "联网搜索", if (webSearchEnabled) "已启用" else "未启用", onSearch)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SwitchSettingRow(Icons.Filled.Stream, Color(0xFFF59E0B), "实时打字效果", "像打字机一样逐字出现回复，关掉则一次性显示完整回复", uiState.streamEnabled) { onStream(it) }
                }
            }
            item {
                SettingsCard("外观") {
                    SettingRow(Icons.Filled.Palette, Color(0xFFEC4899), "主题设置", when (uiState.themeMode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色"; else -> "跟随系统" }, onAppear)
                }
            }
            item {
                SettingsCard("数据管理") {
                    SettingRow(Icons.Filled.Folder, Color(0xFF0EA5E9), "存储管理", "备份、清理聊天和缓存", { showStorageSheet = true })
                }
            }
            item {
                SettingsCard("长期记忆") {
                    SettingRow(Icons.Filled.Star, Color(0xFF8B5CF6), "长期记录", "查看和整理保存的长期记录", onMemory)
                }
            }
            item {
                SettingsCard("关于") {
                    SettingRow(Icons.Filled.Info, Color(0xFF64748B), "版本信息", stringResource(R.string.app_version_display), {})
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(Icons.Filled.Code, Color(0xFF000000), "开源仓库", "GitHub", {})
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showClear) AlertDialog(
        onDismissRequest = { showClear = false }, title = { Text("清空所有对话") }, text = { Text("所有对话和消息将被永久删除") },
        confirmButton = { TextButton(onClick = { onClearAll(); showClear = false }) { Text("确认", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } }
    )

    if (showStorageSheet) {
        ModalBottomSheet(onDismissRequest = { showStorageSheet = false }) {
            val (conversationCount, characterCount, backgroundBytes) = storageSummary
            val breakdown = storageBreakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("存储管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("像 QQ/微信 一样管理聊天与缓存，避免误删全部数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("对话数: $conversationCount")
                        Text("角色数: $characterCount")
                        Text("背景文件: ${backgroundBytes / 1024} KB")
                        if (breakdown != null) {
                            Text("本地缓存总大小: ${breakdown.appBytes / 1024} KB")
                            Text("聊天记录估算: ${breakdown.estimatedMessageBytes / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("模型缓存估算: ${breakdown.estimatedModelCacheBytes / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                StorageActionRow(Icons.Filled.SaveAlt, Color(0xFF10B981), "导出全部数据", "选择保存位置后导出完整备份", onClick = {
                    showStorageSheet = false
                    onExportAll()
                })
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("勾选要清理的项目", fontWeight = FontWeight.SemiBold)
                        CleanupOptionCard(
                            title = "清理聊天记录",
                            subtitle = "删除所有消息，保留角色卡和设置",
                            sizeText = breakdown?.estimatedMessageBytes?.let { "约 ${it / 1024} KB" } ?: "大小未知",
                            checked = clearMessagesChecked,
                            danger = false,
                            onCheckedChange = { clearMessagesChecked = it }
                        )
                        CleanupOptionCard(
                            title = "清理模型缓存",
                            subtitle = "删除已缓存的模型列表",
                            sizeText = breakdown?.estimatedModelCacheBytes?.let { "约 ${it / 1024} KB" } ?: "大小未知",
                            checked = clearModelCacheChecked,
                            danger = false,
                            onCheckedChange = { clearModelCacheChecked = it }
                        )
                        CleanupOptionCard(
                            title = "清理背景文件",
                            subtitle = "删除聊天背景图片",
                            sizeText = "${backgroundBytes / 1024} KB",
                            checked = clearBackgroundChecked,
                            danger = false,
                            onCheckedChange = { clearBackgroundChecked = it }
                        )
                        CleanupOptionCard(
                            title = "清空所有对话",
                            subtitle = "高风险，会删除全部对话数据",
                            sizeText = "包含 $conversationCount 个对话",
                            checked = clearAllChecked,
                            danger = true,
                            onCheckedChange = { clearAllChecked = it }
                        )
                        Button(
                            onClick = {
                                showStorageSheet = false
                                onSelectiveCleanup(clearMessagesChecked, clearModelCacheChecked, clearBackgroundChecked, clearAllChecked)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("开始清理")
                        }
                    }
                }
            }
        }
    }

    if (showModelPicker) ModelPickerSheet(
        models = fallbackModels,
        favorites = fallbackModels.filter { it.isFavorite },
        currentModelName = uiState.selectedModel,
        onDismiss = { showModelPicker = false },
        onSelectModel = { onSelectModel(it); showModelPicker = false },
        onToggleFavorite = onToggleModelFavorite
    )

    if (showProviderModelPicker) {
        ProviderModelPickerSheet(
            savedModels = savedModels,
            providerModels = providerModels,
            loadingProviderIds = loadingProviderIds,
            currentModelName = uiState.selectedModel,
            onDismiss = { showProviderModelPicker = false },
            onSelect = { provider, modelName ->
                onSelectProviderModelInstance(provider, modelName)
                showProviderModelPicker = false
            }
        )
    }

    if (showPersonaEditor) {
        var text by remember { mutableStateOf(personaPrompt) }
        var enabled by remember { mutableStateOf(personaEnabled) }
        val personaPresets = remember {
            listOf(
                "猫娘" to "你是一只可爱的猫娘，有猫耳朵和尾巴。你说话时会在句尾加上「喵~」，性格活泼粘人，喜欢撒娇卖萌，偶尔会傲娇。你对主人很忠诚，会用猫娘独有的方式表达关心。",
                "执事/管家" to "你是一位优雅的执事，举止得体，用词考究。你称呼用户为「主人」，说话时彬彬有礼，时刻保持绅士风度，但偶尔也会流露出幽默感。",
                "傲娇" to "你性格傲娇，嘴上说着「才不是关心你呢」，但行动上却很温柔体贴。你很容易脸红，被发现真实想法时会慌张地否认。",
                "毒舌" to "你说话直接犀利，喜欢吐槽，但本质上是刀子嘴豆腐心。你的吐槽精准有趣，让人又爱又恨，但从不人身攻击。",
                "温柔大姐姐" to "你是一个温柔体贴的大姐姐，说话轻声细语，总是耐心倾听。你会用温暖的话语安慰人，偶尔也会给出理性建议。",
                "元气少女" to "你是一个充满活力的元气少女，说话时经常用感叹号！你乐观开朗，行动力超强，总是能带动气氛。你喜欢用「冲鸭！」「加油！」这类鼓励性话语。"
            )
        }
        AlertDialog(
            onDismissRequest = { if (text.isBlank() || text == personaPrompt) { showPersonaEditor = false } },
            title = { Text("全局偏好设定", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "设定后，主对话（非角色卡对话）的AI会以你设定的风格回复。相当于给主助手装了一个「角色卡」，让AI始终以你喜欢的风格说话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Text(if (enabled) "已启用偏好" else "保存后暂不启用", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("快速预设：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        personaPresets.forEach { (name, preset) ->
                            AssistChip(
                                onClick = { text = preset },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        minLines = 5,
                        maxLines = 10,
                        placeholder = { Text("例如：你是一只可爱的猫娘，说话时会在句尾加上「喵~」...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        supportingText = { Text("${text.length} 字", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onPersonaChange(enabled, text)
                    showPersonaEditor = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showPersonaEditor = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProviderModelPickerSheet(
    savedModels: List<SavedModelEntity>,
    providerModels: Map<Long, List<String>>,
    loadingProviderIds: Set<Long>,
    currentModelName: String,
    onDismiss: () -> Unit,
    onSelect: (SavedModelEntity, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("选择模型实例", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("直接选择“服务商 + 模型”，不需要先切源再切模型。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("搜索服务商或模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                savedModels.forEach { provider ->
                    val models = providerModels[provider.id].orEmpty()
                        .filter { it.contains(search, true) || provider.name.contains(search, true) }
                    val isLoading = provider.id in loadingProviderIds
                    if (models.isNotEmpty() || isLoading) {
                        item(key = "provider_${provider.id}") {
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(provider.name, fontWeight = FontWeight.SemiBold)
                                    if (models.isNotEmpty()) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            models.take(80).forEach { modelName ->
                                                FilterChip(
                                                    selected = currentModelName == modelName,
                                                    onClick = { onSelect(provider, modelName) },
                                                    modifier = Modifier.combinedClickable(
                                                        onClick = { onSelect(provider, modelName) },
                                                        onLongClick = {
                                                            clipboardManager.setText(AnnotatedString(modelName))
                                                            onSelect(provider, modelName)
                                                        }
                                                    ),
                                                    label = { Text(modelName) }
                                                )
                                            }
                                        }
                                    } else if (isLoading) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Text("正在自动拉取模型...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (savedModels.none { providerModels[it.id].orEmpty().isNotEmpty() } && loadingProviderIds.isEmpty()) {
                    item {
                        Text("没有可用模型。请确认服务商地址和 API Key 正确。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (savedModels.none { providerModels[it.id].orEmpty().isNotEmpty() } && loadingProviderIds.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("正在自动拉取服务商模型，请稍等...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupOptionCard(
    title: String,
    subtitle: String,
    sizeText: String,
    checked: Boolean,
    danger: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = if (danger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sizeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StorageActionRow(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ApiPage(uiState: SettingsUiState, savedModels: List<SavedModelEntity>, onBack: () -> Unit,
    onAdd: (String, String, String) -> Unit, onSwitch: (SavedModelEntity) -> Unit, onTest: (SavedModelEntity, (String) -> Unit) -> Unit, onFetchModels: (SavedModelEntity, (String) -> Unit) -> Unit, onQueryProviderAccount: (SavedModelEntity, (Result<ProviderAccountInfo>) -> Unit) -> Unit, onUpdateProvider: (SavedModelEntity) -> Unit, onDelete: (SavedModelEntity) -> Unit,
    onRefresh: () -> Unit, onSelectModel: (SavedModelEntity, String) -> Unit,
    imageProviderId: Long, imageModelName: String, onImageProviderChange: (Long) -> Unit, onImageModelNameChange: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var expandedProviderId by remember { mutableStateOf<Long?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var accountInfo by remember { mutableStateOf<ProviderAccountInfo?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var showImageModelPicker by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型与服务商", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsHeroCard(accent = MaterialTheme.colorScheme.primary) {
                Text("模型与服务商", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "文本源：${uiState.activeProviderName.ifBlank { "未切换" }}  ·  主模型：${uiState.selectedModel.ifBlank { "未设置" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "生图源：${uiState.activeImageProviderName.ifBlank { "跟随文本源" }}  ·  生图模型：${imageModelName.ifBlank { "跟随文字模型" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "当前文本源已记录模型数：${uiState.activeProviderModelCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            SettingsCard("官方与主流预设") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("一键带入常见服务商地址，再补 API Key 就能开始使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProviderPresets.forEach { preset ->
                            Surface(
                                onClick = {
                                    name = preset.name
                                    url = preset.baseUrl
                                    showAdd = true
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = preset.accent.copy(alpha = 0.12f)
                            ) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(preset.name, fontWeight = FontWeight.SemiBold, color = preset.accent)
                                    Text(preset.hintModel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SettingsCard("生图配置") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("文字模型始终只有一个连接源。生图源默认跟随文本源，只有你手动指定后才会独立。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val imageProviderModels = when {
                        imageProviderId == 0L -> uiState.activeProviderAvailableModels
                        else -> uiState.providerModels[imageProviderId].orEmpty()
                    }
                    val supportedImageModels = remember(imageProviderModels) {
                        imageProviderModels.filter(::supportsImageGenerationModel)
                    }
                    OutlinedTextField(
                        value = imageModelName,
                        onValueChange = onImageModelNameChange,
                        label = { Text("生图模型名称") },
                        placeholder = { Text("可手动填写，或从弹窗里选择支持生图的模型") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = imageProviderId == 0L, onClick = { onImageProviderChange(0L) }, label = { Text("优先当前服务商") })
                        savedModels.forEach { provider ->
                            FilterChip(selected = imageProviderId == provider.id, onClick = { onImageProviderChange(provider.id) }, label = { Text(provider.name) })
                        }
                    }
                    Surface(
                        onClick = { showImageModelPicker = true },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("选择生图模型", fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        imageModelName.isNotBlank() -> imageModelName
                                        supportedImageModels.isNotEmpty() -> "当前检测到 ${supportedImageModels.size} 个可选生图模型"
                                        imageProviderModels.isNotEmpty() -> "已拉取 ${imageProviderModels.size} 个模型，但未识别到明确支持生图的模型"
                                        else -> "当前服务商还没有模型缓存"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (supportedImageModels.isNotEmpty()) {
                        Text("推荐生图模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            supportedImageModels.take(8).forEach { modelName ->
                                FilterChip(
                                    selected = imageModelName == modelName,
                                    onClick = { onImageModelNameChange(modelName) },
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onImageModelNameChange(modelName) },
                                        onLongClick = {
                                            clipboardManager.setText(AnnotatedString(modelName))
                                            onImageModelNameChange(modelName)
                                        }
                                    ),
                                    label = { Text(modelName) }
                                )
                            }
                        }
                    } else if (imageProviderModels.isNotEmpty()) {
                        Text(
                            "当前拉取到的模型里，没有识别出明确支持生图的模型。你仍然可以手动填写模型名。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "当前生图源还没有可选模型缓存，先切到对应服务商或等自动拉取完成后即可直接选择。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            savedModels.forEach { provider ->
                val isExpanded = expandedProviderId == provider.id
                val webSearchSupport = remember(provider.name, provider.baseUrl) {
                    detectNativeWebSearchSupport(providerName = provider.name, baseUrl = provider.baseUrl)
                }
                var editedName by remember(provider.id, provider.name) { mutableStateOf(provider.name) }
                var editedUrl by remember(provider.id, provider.baseUrl) { mutableStateOf(provider.baseUrl) }
                var editedKey by remember(provider.id, provider.apiKey) { mutableStateOf(provider.apiKey) }
                Surface(
                    onClick = {
                        if (!provider.isActive) {
                            onSwitch(provider)
                            onFetchModels(provider) { }
                        }
                        expandedProviderId = if (isExpanded) null else provider.id
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = if (provider.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    tonalElevation = if (provider.isActive) 4.dp else 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (provider.isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(provider.name, fontWeight = FontWeight.SemiBold)
                                Text(if (provider.isActive) "当前连接源 · 点开管理模型和生图配置" else "点击卡片切换到这个服务商", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            WebSearchSupportBadge(
                                supported = webSearchSupport.supported,
                                label = if (webSearchSupport.supported) "原生联网搜索" else "不支持联网"
                            )
                            if (provider.isActive) {
                                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF111827)) {
                                    Text("当前", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("地址: ${provider.baseUrl}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text(
                                    if (provider.lastRefreshAt > 0) "模型 ${provider.modelCount} 个 · 已同步" else "模型 ${provider.modelCount} 个 · 尚未同步",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val updatedProvider = provider.copy(
                                        name = editedName.trim(),
                                        baseUrl = editedUrl.trim(),
                                        apiKey = editedKey.trim()
                                    )
                                    onUpdateProvider(updatedProvider)
                                    onTest(
                                        updatedProvider
                                    ) { testResult = it }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                            ) {
                                Text("测试", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    val updatedProvider = provider.copy(
                                        name = editedName.trim(),
                                        baseUrl = editedUrl.trim(),
                                        apiKey = editedKey.trim()
                                    )
                                    onUpdateProvider(updatedProvider)
                                    onFetchModels(updatedProvider) { testResult = it }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Text("拉模型")
                            }
                            Button(
                                onClick = {
                                    val updatedProvider = provider.copy(
                                        name = editedName.trim(),
                                        baseUrl = editedUrl.trim(),
                                        apiKey = editedKey.trim()
                                    )
                                    onUpdateProvider(updatedProvider)
                                    testResult = "正在查询余额/额度..."
                                    onQueryProviderAccount(updatedProvider) { result ->
                                        testResult = null
                                        result.onSuccess { accountInfo = it }
                                            .onFailure { accountError = it.message.orEmpty() }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Text("查余额")
                            }
                            IconButton(onClick = { onDelete(provider) }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                        if (isExpanded) {
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("服务商详情", fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(editedName, { editedName = it }, label = { Text("服务商名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(editedUrl, { editedUrl = it }, label = { Text("API 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(editedKey, { editedKey = it }, label = { Text("API 密钥") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Text("模型数: ${provider.modelCount}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (provider.lastRefreshAt > 0) "上次刷新: ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(provider.lastRefreshAt))}" else "尚未拉取模型",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            val updatedProvider = provider.copy(name = editedName.trim(), baseUrl = editedUrl.trim(), apiKey = editedKey.trim())
                                            onUpdateProvider(updatedProvider)
                                            testResult = "已保存当前服务商配置，正在同步模型..."
                                            onFetchModels(updatedProvider) { testResult = it }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("保存当前配置")
                                    }
                                    val providerModels = uiState.providerModels[provider.id].orEmpty()
                                    if (providerModels.isNotEmpty()) {
                                        Text("已拉取模型", fontWeight = FontWeight.SemiBold)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            providerModels.take(24).forEach { modelName ->
                                                val modelSupport = detectNativeWebSearchSupport(modelName = modelName, providerName = provider.name, baseUrl = provider.baseUrl)
                                                FilterChip(
                                                    selected = uiState.selectedModel == modelName,
                                                    onClick = { onSelectModel(provider, modelName) },
                                                    modifier = Modifier.combinedClickable(
                                                        onClick = { onSelectModel(provider, modelName) },
                                                        onLongClick = {
                                                            clipboardManager.setText(AnnotatedString(modelName))
                                                            onSelectModel(provider, modelName)
                                                        }
                                                    ),
                                                    label = {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Text(modelName)
                                                            Text(
                                                                if (modelSupport.supported) "绿" else "红",
                                                                color = if (modelSupport.supported) Color(0xFF15803D) else Color(0xFFB91C1C),
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Text("还没有拉到这个服务商的模型列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = { showAdd = true; name = ""; key = ""; url = "" }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("添加服务商") }
        }
    }
    if (showImageModelPicker) {
        ImageModelPickerSheet(
            title = "选择生图模型",
            providerLabel = when {
                imageProviderId == 0L -> uiState.activeProviderName.ifBlank { "当前服务商" }
                else -> savedModels.firstOrNull { it.id == imageProviderId }?.name ?: "已选服务商"
            },
            models = when {
                imageProviderId == 0L -> uiState.activeProviderAvailableModels
                else -> uiState.providerModels[imageProviderId].orEmpty()
            },
            currentModelName = imageModelName,
            onDismiss = { showImageModelPicker = false },
            onSelect = {
                onImageModelNameChange(it)
                showImageModelPicker = false
            }
        )
    }
    testResult?.let { message ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text("测试结果") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text("确定") } }
        )
    }
    accountError?.let { message ->
        AlertDialog(
            onDismissRequest = { accountError = null },
            title = { Text("余额查询失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { accountError = null }) { Text("确定") } }
        )
    }
    accountInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { accountInfo = null },
            title = { Text("余额 / 额度") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info.providerName, fontWeight = FontWeight.SemiBold)
                    Text(info.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    info.balanceText?.let { Text("剩余余额: $it") }
                    info.quotaText?.let { Text("总额度: $it") }
                    info.usedText?.let { Text("已使用: $it") }
                    if (info.detailLines.isNotEmpty()) {
                        info.detailLines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (info.detectedFrom.isNotBlank()) {
                        Text("识别来源: ${info.detectedFrom}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    info.dashboardUrl?.let {
                        Text("控制台: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { accountInfo = null }) { Text("确定") } }
        )
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("添加服务商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("可以直接手动填写，也可以先点上面的预设卡片自动带入地址，再补 API Key。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("API URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && key.isNotBlank() && url.isNotBlank()) {
                    onAdd(name.trim(), key.trim(), url.trim())
                    showAdd = false
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ImageModelPickerSheet(
    title: String,
    providerLabel: String,
    models: List<String>,
    currentModelName: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val supportedModels = remember(models) { models.filter(::supportsImageGenerationModel) }
    val unsupportedModels = remember(models, supportedModels) { models.filterNot { it in supportedModels } }
    val filteredSupportedModels = remember(supportedModels, search) {
        supportedModels.filter { it.contains(search, ignoreCase = true) }
    }
    val filteredUnsupportedModels = remember(unsupportedModels, search) {
        unsupportedModels.filter { it.contains(search, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "当前来源：$providerLabel。优先推荐识别到的生图模型，其他未识别模型也可以直接选、长按复制后手动用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("搜索生图模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (filteredSupportedModels.isEmpty() && filteredUnsupportedModels.isEmpty()) {
                Text(
                    if (models.isEmpty()) "当前没有可用模型缓存，可返回后手动填写模型名。" else "没有匹配到结果。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (filteredSupportedModels.isNotEmpty()) {
                        item(key = "supported_header") {
                            Text("推荐生图模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        items(filteredSupportedModels.size, key = { "supported_${filteredSupportedModels[it]}" }) { index ->
                            val modelName = filteredSupportedModels[index]
                            ImageModelPickerRow(
                                modelName = modelName,
                                currentModelName = currentModelName,
                                hint = imageModelHint(modelName),
                                onSelect = onSelect,
                                onCopy = { clipboardManager.setText(AnnotatedString(modelName)) }
                            )
                        }
                    }
                    if (filteredUnsupportedModels.isNotEmpty()) {
                        item(key = "unsupported_header") {
                            Text(
                                if (filteredSupportedModels.isEmpty()) "未识别模型" else "其他未识别模型",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(filteredUnsupportedModels.size, key = { "unsupported_${filteredUnsupportedModels[it]}" }) { index ->
                            val modelName = filteredUnsupportedModels[index]
                            ImageModelPickerRow(
                                modelName = modelName,
                                currentModelName = currentModelName,
                                hint = "未被规则明确识别，但可以直接选中或长按复制模型名",
                                onSelect = onSelect,
                                onCopy = { clipboardManager.setText(AnnotatedString(modelName)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ImageModelPickerRow(
    modelName: String,
    currentModelName: String,
    hint: String,
    onSelect: (String) -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (currentModelName == modelName) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onSelect(modelName) },
                onLongClick = {
                    onCopy()
                    onSelect(modelName)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = if (currentModelName == modelName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(modelName, fontWeight = FontWeight.Medium)
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (currentModelName == modelName) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun supportsImageGenerationModel(modelName: String): Boolean {
    val lower = modelName.lowercase(Locale.ROOT)
    val positivePatterns = listOf(
        "gpt-image", "dall-e", "dalle", "flux", "stable-diffusion", "sdxl", "sd3",
        "recraft", "playground", "midjourney", "mj-", "imagen", "image-gen",
        "imagegeneration", "image-generation", "text-to-image", "seedream", "wanx",
        "wan2", "kolors", "hunyuan-image", "cogview", "janus", "qwen-image",
        "grok-2-image", "grok-image", "grok-vision-image", "image-1"
    )
    val negativePatterns = listOf(
        "embedding", "embed", "rerank", "whisper", "tts", "asr", "transcribe",
        "moderation", "reasoner", "instruct", "chat", "vision", "visual", "-vl", "omni"
    )
    if (positivePatterns.any { lower.contains(it) } && negativePatterns.none { lower.contains(it) }) {
        return true
    }
    return lower.contains("grok") && lower.contains("image") && negativePatterns.none { lower.contains(it) }
}

private fun imageModelHint(modelName: String): String {
    val lower = modelName.lowercase(Locale.ROOT)
    return when {
        lower.contains("flux") -> "FLUX 路线，常用于高质量出图"
        lower.contains("gpt-image") || lower.contains("dall-e") || lower.contains("dalle") -> "OpenAI 生图路线"
        lower.contains("grok") -> "xAI 生图路线"
        lower.contains("recraft") -> "Recraft 路线，适合设计感图片"
        lower.contains("stable-diffusion") || lower.contains("sdxl") || lower.contains("sd3") -> "Stable Diffusion 路线"
        lower.contains("imagen") -> "Google Imagen 路线"
        else -> "已识别为可生图模型"
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLang: (String) -> Unit,
    onBg: () -> Unit,
    onBgClear: () -> Unit,
    backgroundIntensity: String,
    onBackgroundIntensityChange: (String) -> Unit,
    autoThemeSuggestionEnabled: Boolean,
    onAutoThemeSuggestionEnabledChange: (Boolean) -> Unit
) {
    var themeExp by remember { mutableStateOf(false) }; var langExp by remember { mutableStateOf(false) }
    var pendingLanguage by remember(uiState.language) { mutableStateOf(uiState.language) }
    val blurLevel = remember(backgroundIntensity) {
        when (backgroundIntensity) {
            "clear" -> 0f
            "soft" -> 0.5f
            "mist" -> 1f
            else -> backgroundIntensity.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.appearance_title), fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ExposedDropdownMenuBox(expanded = themeExp, onExpandedChange = { themeExp = !themeExp }) {
                OutlinedTextField(value = when (uiState.themeMode) { ThemeMode.SYSTEM -> stringResource(R.string.appearance_theme_system); ThemeMode.LIGHT -> stringResource(R.string.appearance_theme_light); ThemeMode.DARK -> stringResource(R.string.appearance_theme_dark); else -> stringResource(R.string.appearance_theme_system) }, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.appearance_theme_label)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExp) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                DropdownMenu(expanded = themeExp, onDismissRequest = { themeExp = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.appearance_theme_system)) }, onClick = { onTheme(ThemeMode.SYSTEM); themeExp = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.appearance_theme_light)) }, onClick = { onTheme(ThemeMode.LIGHT); themeExp = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.appearance_theme_dark)) }, onClick = { onTheme(ThemeMode.DARK); themeExp = false })
                }
            }
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = langExp, onExpandedChange = { langExp = !langExp }) {
                OutlinedTextField(value = AppLanguage.fromCode(pendingLanguage).displayName, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.appearance_language_label)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExp) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                DropdownMenu(expanded = langExp, onDismissRequest = { langExp = false }) { AppLanguage.entries.forEach { o -> DropdownMenuItem(text = { Text(o.displayName) }, onClick = { pendingLanguage = o.code; langExp = false }) } }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onLang(pendingLanguage) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.appearance_apply_language)) }
            Spacer(Modifier.height(12.dp))
            SettingsCard(stringResource(R.string.appearance_auto_theme_title)) {
                SwitchSettingRow(
                    Icons.Filled.DarkMode,
                    Color(0xFF6366F1),
                    stringResource(R.string.appearance_auto_theme_toggle),
                    stringResource(R.string.appearance_auto_theme_desc),
                    autoThemeSuggestionEnabled,
                    onAutoThemeSuggestionEnabledChange
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("背景强度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(if (blurLevel <= 0.15f) "通透" else if (blurLevel >= 0.75f) "朦胧" else "柔和", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = blurLevel,
                onValueChange = { onBackgroundIntensityChange(String.format(java.util.Locale.US, "%.2f", it)) },
                valueRange = 0f..1f
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("清晰", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("模糊", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp)); Button(onClick = onBg, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.appearance_set_global_bg)) }
            Spacer(Modifier.height(8.dp)); Button(onClick = onBgClear, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.appearance_clear_global_bg)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSubPage(uiState: SettingsUiState, onBack: () -> Unit, onToggle: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var url by remember { mutableStateOf(uiState.searchProviderUrl) }
    var key by remember { mutableStateOf(uiState.searchProviderKey) }
    var mode by remember { mutableStateOf(uiState.searchIndependentMode) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("联网搜索", fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("搜索结果会自动注入到对话上下文", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            SettingsCard("搜索模式") {
                SwitchSettingRow(Icons.Filled.Build, Color(0xFFF59E0B), "独立搜索", if (mode) "先搜索再提问（适合普通模型）" else "模型自带联网（适合联网版模型）", mode) { mode = it; onSave(url, key, mode) }
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard("搜索服务商") {
                OutlinedTextField(url, { url = it }, label = { Text("搜索 API 地址") }, placeholder = { Text("如: https://api.tavily.com/search") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                OutlinedTextField(key, { key = it }, label = { Text("搜索 API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                Button(onClick = { onSave(url, key, mode) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp)) { Text("保存搜索配置") }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))
            Text("推荐服务商", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { url = "https://api.tavily.com/search" }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Tavily", fontWeight = FontWeight.Medium); Text("免费 1000次/月", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (url.contains("tavily")) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { url = "https://serpapi.com/search" }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("SerpAPI", fontWeight = FontWeight.Medium); Text("免费 100次/月", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (url.contains("serpapi")) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("自定义地址", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
