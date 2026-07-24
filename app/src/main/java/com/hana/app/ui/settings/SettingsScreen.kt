package com.hana.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hana.app.R
import com.hana.app.data.db.entity.ModelCapabilityMap
import com.hana.app.data.db.entity.ModelInfo
import com.hana.app.data.db.entity.SavedModelEntity
import com.hana.app.data.remote.ProviderAccountInfo
import com.hana.app.data.settings.AppLanguage
import com.hana.app.data.customization.CustomizationSettings
import com.hana.app.data.customization.DesktopShortcutResult
import com.hana.app.ui.chat.ModelPickerSheet
import com.hana.app.ui.theme.ThemeMode
import com.hana.app.ui.theme.ThemePalette
import com.hana.app.ui.theme.NineSliceImage
import com.hana.app.ui.theme.hanaChatDensityMetrics
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

private enum class SettingsPage { MAIN, API_MGMT, CUSTOMIZATION, SEARCH, MEMORY }

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
    onThemePaletteChange: (ThemePalette) -> Unit = {},
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
    onSummaryBaseUrlChange: (String) -> Unit = {},
    onSummaryApiKeyChange: (String) -> Unit = {},
    onSummaryModelNameChange: (String) -> Unit = {},
    onAutoSummaryThresholdChange: (String) -> Unit = {},
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
    onImportMemory: () -> Unit = {},
    customization: CustomizationSettings = CustomizationSettings(),
    onCustomizationDisplayNameChange: (String) -> Unit = {},
    onCustomizationSplashDurationChange: (Int) -> Unit = {},
    onCustomizationCroppedSplashSave: (Bitmap, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onCustomizationSplashClear: () -> Unit = {},
    onCustomizationGenerationHapticChange: (Boolean) -> Unit = {},
    onCustomizationChatDisplaySave: (String, String, Int, Boolean, Boolean, Boolean, String, Int) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onCustomizationNavigationIconsClear: () -> Unit = {},
    onCustomizationCroppedAssetSave: (String, Bitmap, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onCustomizationBubbleImagesClear: () -> Unit = {},
    onCustomizationAssetClear: (String) -> Unit = {},
    onCustomizationBubbleFixedEdgeChange: (String, Int) -> Unit = { _, _ -> },
    onCustomizationDesktopShortcutRequest: (String) -> DesktopShortcutResult = { DesktopShortcutResult.Failed }
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
            { page = SettingsPage.API_MGMT }, { page = SettingsPage.SEARCH }, { page = SettingsPage.CUSTOMIZATION }, { page = SettingsPage.MEMORY },
            onToggleWebSearch, onToggleStream,
            onClearAllConversations, onClearMessagesOnly, onClearModelCache, onExportAllData, onClearBackground,
            onClearBackgroundCache, onSelectiveCleanup,
            personaEnabled, personaPrompt, onPersonaSettingsChange, storageSummary, storageBreakdown)
        SettingsPage.API_MGMT -> ApiPage(uiState, savedModels, { page = SettingsPage.MAIN },
            onAddModel, onSwitchModel, onTestProvider, onFetchProviderModels, onQueryProviderAccount, onUpdateProvider, onDeleteModel, onRefreshModels, onSelectProviderModelInstance,
            imageProviderId, imageModelName, onImageProviderChange, onImageModelNameChange,
            onSummaryBaseUrlChange, onSummaryApiKeyChange, onSummaryModelNameChange, onAutoSummaryThresholdChange)
        SettingsPage.SEARCH -> SearchSubPage(uiState, { page = SettingsPage.MAIN },
            onToggleWebSearch, onSaveSearchSettings)
        SettingsPage.CUSTOMIZATION -> CustomizationPage(
            uiState = uiState,
            settings = customization,
            onBack = { page = SettingsPage.MAIN },
            onDisplayNameChange = onCustomizationDisplayNameChange,
            onSplashDurationChange = onCustomizationSplashDurationChange,
            onCroppedSplashSave = onCustomizationCroppedSplashSave,
            onSplashClear = onCustomizationSplashClear,
            onGenerationHapticChange = onCustomizationGenerationHapticChange,
            onChatDisplaySave = onCustomizationChatDisplaySave,
            onNavigationIconsClear = onCustomizationNavigationIconsClear,
            onCroppedAssetSave = onCustomizationCroppedAssetSave,
            onBubbleImagesClear = onCustomizationBubbleImagesClear,
            onAssetClear = onCustomizationAssetClear,
            onBubbleFixedEdgeChange = onCustomizationBubbleFixedEdgeChange,
            onDesktopShortcutRequest = onCustomizationDesktopShortcutRequest,
            onThemeModeChange = onThemeModeChange,
            onThemePaletteChange = onThemePaletteChange,
            onLanguageChange = onLanguageChange,
            onPickBackground = onPickBackground,
            onClearBackground = onClearBackground,
            backgroundIntensity = backgroundIntensity,
            onBackgroundIntensityChange = onBackgroundIntensityChange,
            onAutoThemeSuggestionEnabledChange = onAutoThemeSuggestionEnabledChange
        )
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
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Column(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(vertical = 4.dp)) { content() }
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
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsStatusBadge(text: String, active: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (active) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline)
            )
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    status: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (status.isBlank()) subtitle else status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsQuickToggle(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (checked) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline))
        }
    }
}

@Composable
private fun SettingsSectionHeader(eyebrow: String, title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eyebrow.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModelControlPanel(
    provider: String,
    model: String,
    providerCount: Int,
    webSearchEnabled: Boolean,
    personaEnabled: Boolean,
    onSwitchModel: () -> Unit
) {
    Surface(
        onClick = onSwitchModel,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (provider.isBlank()) Color(0xFFF59E0B) else Color(0xFF53E1A7)))
                        Text(if (provider.isBlank()) stringResource(R.string.settings_not_selected_model) else "已配置", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_sources_count, providerCount), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(provider.ifBlank { stringResource(R.string.settings_waiting_config) }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(model.ifBlank { stringResource(R.string.settings_not_selected_model) }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SettingsDarkTag(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.08f)) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.5.sp)
    }
}

private fun openExternalLink(context: android.content.Context, uri: String, chooserTitle: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "没有找到可以打开此链接的应用", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "暂时无法打开，请稍后重试", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutHanaDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showGitHubLanguage by remember { mutableStateOf(false) }
    if (!showGitHubLanguage) AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = { Text(stringResource(R.string.settings_about), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_about_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AboutLinkRow(
                    icon = { Icon(painterResource(R.drawable.ic_penguin), null, Modifier.size(25.dp), tint = Color.Unspecified) },
                    accent = Color(0xFF12B7F5),
                    title = "QQ",
                    subtitle = "409192804",
                    onClick = { openExternalLink(context, "mqqwpa://im/chat?chat_type=wpa&uin=409192804&version=1&src_type=web", "选择 QQ") }
                )
                AboutLinkRow(
                    icon = { Icon(Icons.Filled.LiveTv, null, Modifier.size(24.dp), tint = Color(0xFFFB7299)) },
                    accent = Color(0xFFFB7299),
                    title = "哔哩哔哩",
                    subtitle = "开发者的 B 站主页",
                    onClick = { openExternalLink(context, "https://space.bilibili.com/168640889?spm_id_from=333.1007.0.0", "选择打开 B 站主页的应用") }
                )
                AboutLinkRow(
                    icon = { Icon(Icons.Filled.Code, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface) },
                    accent = MaterialTheme.colorScheme.onSurface,
                    title = "GitHub",
                    subtitle = "hana-roleplay-app",
                    onClick = { showGitHubLanguage = true }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } }
    )

    if (showGitHubLanguage) {
        AlertDialog(
            onDismissRequest = { showGitHubLanguage = false },
            icon = { Icon(Icons.Filled.Code, null) },
            title = { Text(stringResource(R.string.settings_github_open), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.settings_github_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            showGitHubLanguage = false
                            openExternalLink(context, "https://github.com/arisusuzushima114514-cell/hana-roleplay-app?locale=zh-CN", "选择打开 GitHub 中文站的应用")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(stringResource(R.string.settings_github_chinese)) }
                    OutlinedButton(
                        onClick = {
                            showGitHubLanguage = false
                            openExternalLink(context, "https://github.com/arisusuzushima114514-cell/hana-roleplay-app?locale=en", "选择打开 GitHub English 的应用")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(stringResource(R.string.settings_github_english)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGitHubLanguage = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun AboutLinkRow(
    icon: @Composable () -> Unit,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MainPage(
    uiState: SettingsUiState, savedModels: List<SavedModelEntity>, webSearchEnabled: Boolean,
    modelList: List<ModelInfo>, onSelectModel: (ModelInfo) -> Unit,
    providerModels: Map<Long, List<String>>, loadingProviderIds: Set<Long>, onToggleModelFavorite: (String) -> Unit, onSelectProviderModelInstance: (SavedModelEntity, String) -> Unit,
    onApi: () -> Unit, onSearch: () -> Unit, onCustomization: () -> Unit, onMemory: () -> Unit,
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
    var showAboutDialog by remember { mutableStateOf(false) }
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { p ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    ModelControlPanel(
                        provider = uiState.activeProviderName,
                        model = uiState.selectedModel,
                        providerCount = savedModels.size,
                        webSearchEnabled = webSearchEnabled,
                        personaEnabled = personaEnabled,
                        onSwitchModel = { showProviderModelPicker = true }
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_live_controls), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            SettingsQuickToggle(Modifier.weight(1f), Icons.Filled.Stream, stringResource(R.string.settings_stream_output), uiState.streamEnabled, onStream)
                            SettingsQuickToggle(Modifier.weight(1f), Icons.Filled.Language, stringResource(R.string.web_search), webSearchEnabled) { onWebSearch() }
                        }
                        Surface(onClick = { showPersonaEditor = true }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.settings_persona), fontWeight = FontWeight.SemiBold)
                                    Text(if (personaPrompt.isBlank()) stringResource(R.string.settings_persona_unset) else if (personaEnabled) stringResource(R.string.settings_persona_enabled) else stringResource(R.string.settings_persona_saved_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = personaEnabled, onCheckedChange = { onPersonaChange(it, personaPrompt) })
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("功能", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsCard("") {
                            SettingsCategoryCard(Modifier.fillMaxWidth(), Icons.Filled.Cloud, MaterialTheme.colorScheme.primary, stringResource(R.string.settings_models_connections), stringResource(R.string.settings_models_connections_desc), stringResource(R.string.settings_sources_count, savedModels.size), onApi)
                            HorizontalDivider(Modifier.padding(start = 56.dp))
                            SettingsCategoryCard(Modifier.fillMaxWidth(), Icons.Filled.Palette, MaterialTheme.colorScheme.primary, "个性化", "主题、背景、图标和气泡", uiState.themePalette.displayName, onCustomization)
                            HorizontalDivider(Modifier.padding(start = 56.dp))
                            SettingsCategoryCard(Modifier.fillMaxWidth(), Icons.Filled.TravelExplore, MaterialTheme.colorScheme.primary, "联网与搜索", stringResource(R.string.settings_web_capability_desc), if (webSearchEnabled) stringResource(R.string.settings_running) else stringResource(R.string.settings_off), onSearch)
                            HorizontalDivider(Modifier.padding(start = 56.dp))
                            SettingsCategoryCard(Modifier.fillMaxWidth(), Icons.Filled.Memory, MaterialTheme.colorScheme.primary, "记忆", stringResource(R.string.settings_memory_hub_desc), stringResource(R.string.settings_enter_memory), onMemory)
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("数据", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsCategoryCard(Modifier.fillMaxWidth(), Icons.Filled.Folder, MaterialTheme.colorScheme.primary, stringResource(R.string.settings_backup_storage), stringResource(R.string.settings_backup_storage_desc), "${storageSummary.first} · ${storageSummary.second}", { showStorageSheet = true })
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_system_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsCard("") {
                            SettingRow(Icons.Filled.Info, Color(0xFF64748B), stringResource(R.string.settings_about), stringResource(R.string.app_version_display), { showAboutDialog = true })
                            HorizontalDivider(Modifier.padding(start = 72.dp))
                            SettingRow(Icons.Filled.Code, Color(0xFF64748B), stringResource(R.string.settings_open_source), "GitHub · Bilibili · QQ", { showAboutDialog = true })
                        }
                    }
                }
                item { Spacer(Modifier.height(36.dp)) }
            }
        }
    }

    if (showAboutDialog) AboutHanaDialog(onDismiss = { showAboutDialog = false })

    if (showClear) AlertDialog(
        onDismissRequest = { showClear = false }, title = { Text("清空所有对话") }, text = { Text("所有对话和消息将被永久删除") },
        confirmButton = { TextButton(onClick = {
            onSelectiveCleanup(clearMessagesChecked, clearModelCacheChecked, clearBackgroundChecked, true)
            showClear = false
        }) { Text("确认", color = MaterialTheme.colorScheme.error) } },
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSectionHeader("Local vault", stringResource(R.string.settings_local_vault))
                Text(stringResource(R.string.settings_storage_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF171629), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        Text("LOCAL STORAGE", color = Color.White.copy(alpha = 0.48f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(
                            breakdown?.appBytes?.let { "${it / 1024} KB" } ?: "正在统计",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsDarkTag("$conversationCount 对话")
                            SettingsDarkTag("$characterCount 角色")
                            SettingsDarkTag("${backgroundBytes / 1024} KB 背景")
                        }
                        if (breakdown != null) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.settings_chat_history), color = Color.White.copy(alpha = 0.60f), style = MaterialTheme.typography.bodySmall)
                                Text("${breakdown.estimatedMessageBytes / 1024} KB", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.settings_model_cache), color = Color.White.copy(alpha = 0.60f), style = MaterialTheme.typography.bodySmall)
                                Text("${breakdown.estimatedModelCacheBytes / 1024} KB", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                StorageActionRow(Icons.Filled.SaveAlt, Color(0xFF10B981), "导出全部数据", "选择保存位置后导出完整备份", onClick = {
                    showStorageSheet = false
                    onExportAll()
                })
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(26.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_select_cleanup), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_cleanup_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                if (clearAllChecked) showClear = true
                                else onSelectiveCleanup(clearMessagesChecked, clearModelCacheChecked, clearBackgroundChecked, false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.settings_start_cleanup))
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
    imageProviderId: Long, imageModelName: String, onImageProviderChange: (Long) -> Unit, onImageModelNameChange: (String) -> Unit,
    onSummaryBaseUrlChange: (String) -> Unit, onSummaryApiKeyChange: (String) -> Unit,
    onSummaryModelNameChange: (String) -> Unit, onAutoSummaryThresholdChange: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var expandedProviderId by remember { mutableStateOf<Long?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var resultTitle by remember { mutableStateOf("操作结果") }
    var providerPendingDelete by remember { mutableStateOf<SavedModelEntity?>(null) }
    var showImageModelPicker by remember { mutableStateOf(false) }
    var advancedConfigExpanded by remember { mutableStateOf(false) }
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
                    Text("选择服务商后会自动填入兼容协议地址；API Key 和模型仍需由你确认。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProviderPresetGroup.entries.forEach { group ->
                        Text(group.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        ProviderPresets.filter { it.group == group }.forEach { preset ->
                            Surface(
                                onClick = {
                                    name = preset.name
                                    url = preset.baseUrl
                                    showAdd = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(preset.accent))
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(preset.name, fontWeight = FontWeight.SemiBold)
                                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            if (preset.recommendedModels.isEmpty()) preset.protocol else "${preset.protocol} · ${preset.recommendedModels.joinToString(" / ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = preset.accent,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (preset.note.isNotBlank()) Text(preset.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = { advancedConfigExpanded = !advancedConfigExpanded },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("高级模型配置", fontWeight = FontWeight.SemiBold)
                        Text("生图模型与长对话摘要（可选）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (advancedConfigExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (advancedConfigExpanded) {
            Spacer(Modifier.height(10.dp))
            SettingsCard("生图模型") {
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
            SettingsCard("长对话摘要（可选）") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "用于把超长对话中的旧消息压缩成事实摘要。你不需要单独配置；下面全部留空时会自动跟随主 API 和当前模型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.summaryBaseUrl,
                        onValueChange = onSummaryBaseUrlChange,
                        label = { Text("摘要 API 地址（可选）") },
                        placeholder = { Text("留空则跟随主 API") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.summaryApiKey,
                        onValueChange = onSummaryApiKeyChange,
                        label = { Text("摘要 API Key（可选）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.summaryModelName,
                        onValueChange = onSummaryModelNameChange,
                        label = { Text("摘要模型（可选）") },
                        placeholder = { Text("例如 gemini-2.5-flash / deepseek-chat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.autoSummaryThreshold.toString(),
                        onValueChange = onAutoSummaryThresholdChange,
                        label = { Text("自动摘要触发消息数") },
                        supportingText = { Text("达到该数量后压缩超出最近窗口的旧消息，建议 24-40。") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                                Text(if (provider.isActive) "当前连接源 · 点开管理模型和生图配置" else "点击展开并管理此服务商", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    resultTitle = "连接测试结果"
                                    onTest(
                                        updatedProvider
                                    ) { testResult = it }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                            ) {
                                Text("保存并测试", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    val updatedProvider = provider.copy(
                                        name = editedName.trim(),
                                        baseUrl = editedUrl.trim(),
                                        apiKey = editedKey.trim()
                                    )
                                    onUpdateProvider(updatedProvider)
                                    resultTitle = "模型同步结果"
                                    onFetchModels(updatedProvider) { testResult = it }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Text("保存并拉取")
                            }
                            IconButton(onClick = { providerPendingDelete = provider }) { Icon(Icons.Filled.Delete, "删除服务商", tint = MaterialTheme.colorScheme.error) }
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
            title = { Text(resultTitle) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text("确定") } }
        )
    }
    providerPendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { providerPendingDelete = null },
            title = { Text("删除服务商") },
            text = { Text(if (provider.isActive) "“${provider.name}”是当前连接源。删除后当前模型连接将不可用，且无法撤销。" else "确定删除“${provider.name}”及其 API 配置吗？此操作无法撤销。") },
            confirmButton = { TextButton(onClick = { onDelete(provider); providerPendingDelete = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { providerPendingDelete = null }) { Text("取消") } }
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
private fun CustomizationPage(
    uiState: SettingsUiState,
    settings: CustomizationSettings,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSplashDurationChange: (Int) -> Unit,
    onCroppedSplashSave: (Bitmap, (Boolean) -> Unit) -> Unit,
    onSplashClear: () -> Unit,
    onGenerationHapticChange: (Boolean) -> Unit,
    onChatDisplaySave: (String, String, Int, Boolean, Boolean, Boolean, String, Int) -> Unit,
    onNavigationIconsClear: () -> Unit
    ,onCroppedAssetSave: (String, Bitmap, (Boolean) -> Unit) -> Unit
    ,onBubbleImagesClear: () -> Unit
    ,onAssetClear: (String) -> Unit
    ,onBubbleFixedEdgeChange: (String, Int) -> Unit
    ,onDesktopShortcutRequest: (String) -> DesktopShortcutResult
    ,onThemeModeChange: (ThemeMode) -> Unit
    ,onThemePaletteChange: (ThemePalette) -> Unit
    ,onLanguageChange: (String) -> Unit
    ,onPickBackground: () -> Unit
    ,onClearBackground: () -> Unit
    ,backgroundIntensity: String
    ,onBackgroundIntensityChange: (String) -> Unit
    ,onAutoThemeSuggestionEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(settings.appDisplayName) { mutableStateOf(settings.appDisplayName) }
    var pendingLanguage by remember(uiState.language) { mutableStateOf(uiState.language) }
    var chatFontSize by remember(settings.chatFontSize) { mutableStateOf(settings.chatFontSize) }
    var chatDensity by remember(settings.chatDensity) { mutableStateOf(settings.chatDensity) }
    var bubbleWidthPercent by remember(settings.bubbleWidthPercent) { mutableStateOf(settings.bubbleWidthPercent.toFloat()) }
    var showMessageAvatars by remember(settings.showMessageAvatars) { mutableStateOf(settings.showMessageAvatars) }
    var showMessageTime by remember(settings.showMessageTime) { mutableStateOf(settings.showMessageTime) }
    var showBottomBarLabels by remember(settings.showBottomBarLabels) { mutableStateOf(settings.showBottomBarLabels) }
    var inputBarSize by remember(settings.inputBarSize) { mutableStateOf(settings.inputBarSize) }
    var inputBarWidthPercent by remember(settings.inputBarWidthPercent) { mutableStateOf(settings.inputBarWidthPercent.toFloat()) }
    var showChatDisplayPreview by remember { mutableStateOf(false) }
    var showBubblePreview by remember { mutableStateOf(false) }
    var submittedChatDisplay by remember { mutableStateOf<String?>(null) }
    val persistedChatDisplay = listOf(settings.chatFontSize, settings.chatDensity, settings.bubbleWidthPercent, settings.showMessageAvatars, settings.showMessageTime, settings.showBottomBarLabels, settings.inputBarSize, settings.inputBarWidthPercent).joinToString("|")
    val draftChatDisplay = listOf(chatFontSize, chatDensity, bubbleWidthPercent.toInt(), showMessageAvatars, showMessageTime, showBottomBarLabels, inputBarSize, inputBarWidthPercent.toInt()).joinToString("|")
    LaunchedEffect(persistedChatDisplay) {
        if (submittedChatDisplay == persistedChatDisplay) submittedChatDisplay = null
    }
    val chatDisplayDirty = draftChatDisplay != persistedChatDisplay && draftChatDisplay != submittedChatDisplay
    val backgroundLevel = remember(backgroundIntensity) {
        when (backgroundIntensity) {
            "clear" -> 0f
            "soft" -> 0.5f
            "mist" -> 1f
            else -> backgroundIntensity.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
        }
    }
    var pendingNavigationSlot by remember { mutableStateOf("chat") }
    var cropSlot by remember { mutableStateOf<String?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    fun openCropper(slot: String, uri: Uri) {
        val bitmap = decodeSampledBitmap(context, uri)
        if (bitmap != null) {
            cropSlot = slot
            cropBitmap = bitmap
        } else Toast.makeText(context, "无法读取图片", Toast.LENGTH_SHORT).show()
    }
    val navigationIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openCropper("${pendingNavigationSlot}_tab", uri)
    }
    var pendingAssetSlot by remember { mutableStateOf("desktop") }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openCropper(pendingAssetSlot, uri)
    }
    val splashPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openCropper("splash", uri)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("个性化", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }
    ) { padding ->
        if (cropBitmap != null && cropSlot != null) {
            ImageCropEditor(
                bitmap = cropBitmap!!,
                title = when (cropSlot) {
                    "splash" -> "裁剪开屏图片"
                    "desktop" -> "裁剪桌面图标"
                    "bubble_user" -> "裁剪用户气泡素材"
                    "bubble_ai" -> "裁剪 AI 气泡素材"
                    else -> "裁剪底栏图标"
                },
                onCancel = { cropBitmap = null; cropSlot = null },
                onConfirm = { cropped ->
                    val slot = cropSlot ?: return@ImageCropEditor
                    if (slot == "splash") {
                        onCroppedSplashSave(cropped) { ok ->
                            Toast.makeText(context, if (ok) "开屏图片已保存" else "开屏图片保存失败", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onCroppedAssetSave(slot, cropped) { ok ->
                            Toast.makeText(context, if (ok) "图片已保存" else "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                    cropBitmap = null
                    cropSlot = null
                },
                frameAspectRatio = if (cropSlot == "splash") 9f / 16f else 1f,
                outputSize = if (cropSlot == "splash") IntSize(1080, 1920) else IntSize(512, 512)
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                SettingsCard("主题") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色").forEachIndexed { index, (mode, label) ->
                                SegmentedButton(selected = uiState.themeMode == mode, onClick = { onThemeModeChange(mode) }, shape = SegmentedButtonDefaults.itemShape(index, 3), label = { Text(label) })
                            }
                        }
                        Text("推荐颜色", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePalette.entries.forEach { palette ->
                                FilterChip(selected = uiState.themePalette == palette, onClick = { onThemePaletteChange(palette) }, label = { Text(palette.displayName) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("自动建议", fontWeight = FontWeight.SemiBold)
                                Text("根据时间提示切换明暗主题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = uiState.autoThemeSuggestionEnabled, onCheckedChange = onAutoThemeSuggestionEnabledChange)
                        }
                    }
                }
            }
            item {
                SettingsCard("背景") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("强度 ${(backgroundLevel * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = backgroundLevel, onValueChange = { onBackgroundIntensityChange(String.format(java.util.Locale.US, "%.2f", it)) }, valueRange = 0f..1f)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onPickBackground, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("选择背景") }
                            OutlinedButton(onClick = onClearBackground, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("清除") }
                        }
                    }
                }
            }
            item {
                SettingsCard("语言") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { language ->
                                FilterChip(selected = pendingLanguage == language.code, onClick = { pendingLanguage = language.code }, label = { Text(language.displayName) })
                            }
                        }
                        Button(onClick = { onLanguageChange(pendingLanguage) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("应用语言") }
                    }
                }
            }
            item {
                SettingsCard("聊天显示") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("控制主聊天和角色聊天的阅读方式。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("聊天字号", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("small" to "小", "standard" to "标准", "large" to "大").forEach { (value, label) ->
                                FilterChip(selected = chatFontSize == value, onClick = { chatFontSize = value }, label = { Text(label) })
                            }
                        }
                        Text("只调整聊天正文和输入框字号，不会替换系统字体或改变代码块等宽字体。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("输入框宽度 ${inputBarWidthPercent.toInt()}%", fontWeight = FontWeight.SemiBold)
                        Slider(value = inputBarWidthPercent, onValueChange = { inputBarWidthPercent = it }, valueRange = 70f..100f, steps = 5)
                        Text("聊天密度", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("compact" to "紧凑", "standard" to "标准", "comfortable" to "舒适").forEach { (value, label) ->
                                FilterChip(selected = chatDensity == value, onClick = { chatDensity = value }, label = { Text(label) })
                            }
                        }
                        Text("紧凑、标准、舒适分别控制消息间距、气泡留白和输入框留白；三种聊天页面使用相同规则。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("气泡最大宽度 ${bubbleWidthPercent.toInt()}%", fontWeight = FontWeight.SemiBold)
                        Slider(value = bubbleWidthPercent, onValueChange = { bubbleWidthPercent = it }, valueRange = 60f..95f, steps = 6)
                        Text("输入框高度", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("compact" to "紧凑", "standard" to "标准", "comfortable" to "舒适").forEach { (value, label) ->
                                FilterChip(selected = inputBarSize == value, onClick = { inputBarSize = value }, label = { Text(label) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("显示消息头像", fontWeight = FontWeight.SemiBold); Text("角色和群聊消息仍会保留说话人名称。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = showMessageAvatars, onCheckedChange = { showMessageAvatars = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("显示消息时间", fontWeight = FontWeight.SemiBold); Text("在消息气泡下方显示发送时间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = showMessageTime, onCheckedChange = { showMessageTime = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("底栏显示文字", fontWeight = FontWeight.SemiBold); Text("关闭后底部导航只显示图标。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = showBottomBarLabels, onCheckedChange = { showBottomBarLabels = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (chatDisplayDirty) "有未应用的更改" else "当前设置已应用", fontWeight = FontWeight.SemiBold, color = if (chatDisplayDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(if (chatDisplayDirty) "先通过预览确认，再点击“应用聊天显示”写入全部聊天页面。" else "预览只展示聊天布局，不会覆盖真实消息。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showChatDisplayPreview = !showChatDisplayPreview }) { Text(if (showChatDisplayPreview) "收起预览" else "查看预览") }
                        }
                        if (showChatDisplayPreview) {
                            ChatDisplayPreview(
                                fontSize = chatFontSize,
                                density = chatDensity,
                                bubbleWidthPercent = bubbleWidthPercent.toInt(),
                                showAvatars = showMessageAvatars,
                                showTime = showMessageTime,
                                showBottomBarLabels = showBottomBarLabels,
                                inputBarSize = inputBarSize,
                                inputBarWidthPercent = inputBarWidthPercent.toInt()
                            )
                        }
                        Button(
                            onClick = {
                                submittedChatDisplay = draftChatDisplay
                                onChatDisplaySave(chatFontSize, chatDensity, bubbleWidthPercent.toInt(), showMessageAvatars, showMessageTime, showBottomBarLabels, inputBarSize, inputBarWidthPercent.toInt())
                            },
                            enabled = chatDisplayDirty,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(if (chatDisplayDirty) "应用聊天显示" else "聊天显示已应用") }
                        OutlinedButton(
                            onClick = {
                                chatFontSize = "standard"
                                chatDensity = "standard"
                                bubbleWidthPercent = 78f
                                showMessageAvatars = true
                                showMessageTime = false
                                showBottomBarLabels = true
                                inputBarSize = "standard"
                                inputBarWidthPercent = 100f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("恢复标准草稿（仍需应用）") }
                    }
                }
            }
            item {
                SettingsCard("名称与图标") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(displayName, { displayName = it.take(24) }, label = { Text("Hana 内显示名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onDisplayNameChange(displayName) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("保存应用内名称") }
                        Text("自定义桌面入口", fontWeight = FontWeight.SemiBold)
                        Text("Android 不允许直接修改系统应用图标。这里会创建自定义桌面入口；名称可单独更新，选择图标后可一并更新。部分桌面会缓存图标，请等待刷新。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(onClick = { pendingAssetSlot = "desktop"; assetPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                val file = settings.desktopIconPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
                                 if (file != null) AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)))
                                else Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text("选择并裁剪桌面图标", fontWeight = FontWeight.SemiBold); Text("支持拖动与双指缩放", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                 Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                             }
                         }
                         if (settings.desktopIconPath.isNotBlank()) {
                             TextButton(onClick = { onAssetClear("desktop") }, modifier = Modifier.fillMaxWidth()) { Text("清除桌面图标") }
                         }
                        Button(
                            onClick = {
                                onDisplayNameChange(displayName)
                                val message = when (onDesktopShortcutRequest(displayName)) {
                                    DesktopShortcutResult.Updated -> "已提交自定义桌面入口更新；名称通常立即刷新，图标可能需要桌面刷新"
                                    DesktopShortcutResult.AddRequested -> "已提交添加请求，请在系统或桌面确认后查看新入口"
                                    DesktopShortcutResult.Unsupported -> "当前桌面不支持添加固定快捷方式"
                                    DesktopShortcutResult.MissingIcon -> "请先选择并裁剪桌面图标"
                                    DesktopShortcutResult.Failed -> "桌面入口更新失败，请删除旧的自定义入口后重试"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("添加或更新桌面入口") }
                    }
                }
            }
            item {
                SettingsCard("开屏") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (settings.customSplashEnabled) "当前使用自定义开屏图片" else "当前随机使用内置开屏图片", fontWeight = FontWeight.SemiBold)
                        val splashFile = settings.customSplashPath.takeIf { settings.customSplashEnabled }?.let(::File)?.takeIf { it.isFile }
                        if (splashFile != null) {
                            AsyncImage(model = splashFile, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)))
                        }
                        Text("选择图片后会先进入 9:16 竖屏裁剪。开屏完全按此画面显示，不再由后台自动截取。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { splashPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Crop, null); Spacer(Modifier.width(8.dp)); Text(if (splashFile == null) "选择并裁剪开屏图片" else "重新裁剪开屏图片") }
                        if (settings.customSplashEnabled) OutlinedButton(onClick = onSplashClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("恢复内置随机开屏") }
                        Text("显示时长", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1200 to "快速", 2200 to "标准", 3500 to "沉浸").forEach { (millis, label) ->
                                FilterChip(selected = settings.splashDurationMillis == millis, onClick = { onSplashDurationChange(millis) }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard("反馈") {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Vibration, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("回复震动", fontWeight = FontWeight.SemiBold)
                            Text("仅在回复成功保存后触发。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.generationCompleteHapticEnabled, onCheckedChange = onGenerationHapticChange)
                    }
                }
            }
            item {
                SettingsCard("底栏与气泡") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("底栏图标", fontWeight = FontWeight.SemiBold)
                        Text("点选任意一栏，从相册选图后手动拖动、双指缩放并确认裁剪。自定义图标会保持原色。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(
                                Triple("chat", "对话", settings.chatTabIconPath),
                                Triple("character", "角色", settings.characterTabIconPath),
                                Triple("settings", "设置", settings.settingsTabIconPath)
                            ).forEach { (slot, label, path) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        onClick = { pendingNavigationSlot = slot; navigationIconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        val file = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
                                        if (file != null) AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary) }
                                 }
                                     Text(label, style = MaterialTheme.typography.labelMedium)
                                     if (path.isNotBlank()) TextButton(onClick = { onAssetClear(slot) }) { Text("清除") }
                                 }
                            }
                        }
                        OutlinedButton(onClick = onNavigationIconsClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("恢复默认底栏图标") }
                        HorizontalDivider()
                        Text("气泡", fontWeight = FontWeight.SemiBold)
                        Text("分别设置 AI 与用户气泡素材。选择后同样进入裁剪编辑器，并直接应用到真实聊天。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BubbleAssetPicker(Modifier.weight(1f), "AI 气泡", settings.aiBubbleImagePath, onClear = { onAssetClear("bubble_ai") }) { pendingAssetSlot = "bubble_ai"; assetPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            BubbleAssetPicker(Modifier.weight(1f), "用户气泡", settings.userBubbleImagePath, onClear = { onAssetClear("bubble_user") }) { pendingAssetSlot = "bubble_user"; assetPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        }
                        if (settings.aiBubbleImagePath.isNotBlank()) {
                            Text("AI 气泡固定边缘 ${settings.aiBubbleFixedEdgePercent}%", style = MaterialTheme.typography.labelMedium)
                            Slider(value = settings.aiBubbleFixedEdgePercent.toFloat(), onValueChange = { onBubbleFixedEdgeChange("bubble_ai", it.toInt()) }, valueRange = 8f..42f)
                        }
                        if (settings.userBubbleImagePath.isNotBlank()) {
                            Text("用户气泡固定边缘 ${settings.userBubbleFixedEdgePercent}%", style = MaterialTheme.typography.labelMedium)
                            Slider(value = settings.userBubbleFixedEdgePercent.toFloat(), onValueChange = { onBubbleFixedEdgeChange("bubble_user", it.toInt()) }, valueRange = 8f..42f)
                        }
                        Text("这是九宫格边缘保留，会同时影响上下左右和四角。数值越大，尾巴和边角越完整；中间区域用于适配长文本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showBubblePreview = !showBubblePreview }, modifier = Modifier.align(Alignment.End)) { Text(if (showBubblePreview) "收起气泡预览" else "查看气泡预览") }
                        if (showBubblePreview) BubbleImagePreview(settings.aiBubbleImagePath, settings.userBubbleImagePath, settings.aiBubbleFixedEdgePercent, settings.userBubbleFixedEdgePercent)
                        OutlinedButton(onClick = onBubbleImagesClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("清除气泡图片") }
                    }
                }
            }
        }
    }
}

private fun decodeSampledBitmap(context: android.content.Context, uri: Uri, maxEdge: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }.getOrNull()
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxEdge * 2) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
}

@Composable
private fun ChatDisplayPreview(
    fontSize: String,
    density: String,
    bubbleWidthPercent: Int,
    showAvatars: Boolean,
    showTime: Boolean,
    showBottomBarLabels: Boolean,
    inputBarSize: String,
    inputBarWidthPercent: Int
) {
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = when (fontSize) {
        "small" -> 14.sp
        "large" -> 18.sp
        else -> 16.sp
    })
    val densityMetrics = hanaChatDensityMetrics(density)
    val spacing = densityMetrics.messageSpacing
    val verticalPadding = densityMetrics.bubbleVerticalPadding
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
            val bubbleWidth = maxWidth * (bubbleWidthPercent.coerceIn(60, 95) / 100f)
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (showAvatars) {
                        Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Text("H", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = bubbleWidth)) {
                        Column(Modifier.padding(horizontal = densityMetrics.bubbleHorizontalPadding, vertical = verticalPadding)) {
                            Text("这是聊天显示的即时预览。", style = textStyle)
                            if (showTime) Text("12:30", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(max = bubbleWidth).align(Alignment.End)) {
                    Column(Modifier.padding(horizontal = densityMetrics.bubbleHorizontalPadding, vertical = verticalPadding)) {
                        Text("修改后点击应用即可保存。", style = textStyle, color = MaterialTheme.colorScheme.onPrimary)
                        if (showTime) Text("12:31", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f), modifier = Modifier.align(Alignment.End))
                    }
                }
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(inputBarWidthPercent.coerceIn(70, 100) / 100f).align(Alignment.CenterHorizontally)) {
                    Text(
                        "输入消息...",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = hanaChatDensityMetrics(inputBarSize).inputVerticalPadding),
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("对话", "角色", "设置").forEach { label ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Circle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            if (showBottomBarLabels) Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleAssetPicker(modifier: Modifier, title: String, path: String, onClear: () -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val file = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            if (file != null) AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            else Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary) }
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            if (file != null) TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("清除") }
        }
    }
}

@Composable
private fun BubbleImagePreview(aiPath: String, userPath: String, aiFixedEdgePercent: Int, userFixedEdgePercent: Int) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BubblePreviewItem(aiPath, aiFixedEdgePercent, "短消息", Alignment.Start)
            BubblePreviewItem(userPath, userFixedEdgePercent, "这是用于检查边角与中间拉伸效果的较长消息。", Alignment.End)
        }
    }
}

@Composable
private fun BubblePreviewItem(path: String, fixedEdgePercent: Int, text: String, alignment: Alignment.Horizontal) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (alignment == Alignment.End) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(20.dp))) {
            val file = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            if (file != null) NineSliceImage(path = file.absolutePath, fixedEdgePercent = fixedEdgePercent, modifier = Modifier.matchParentSize())
            else Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (file != null) 0.24f else 0f)))
            Text(text, modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp), color = if (file != null) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ImageCropEditor(
    bitmap: Bitmap,
    title: String,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    frameAspectRatio: Float = 1f,
    outputSize: IntSize = IntSize(512, 512)
) {
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0C10)).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, null, tint = Color.White) }
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("拖动调整位置，双指缩放。方框内是最终效果。", modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("复位", color = Color.White) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(frameAspectRatio)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .onSizeChanged { frameSize = it }
                .pointerInput(bitmap, frameSize) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (frameSize.width > 0 && frameSize.height > 0) {
                            val baseScale = maxOf(frameSize.width.toFloat() / bitmap.width, frameSize.height.toFloat() / bitmap.height)
                            val maxX = ((bitmap.width * baseScale * scale - frameSize.width) / 2f).coerceAtLeast(0f)
                            val maxY = ((bitmap.height * baseScale * scale - frameSize.height) / 2f).coerceAtLeast(0f)
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            )
            Box(Modifier.matchParentSize().border(2.dp, Color.White, RoundedCornerShape(24.dp)))
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("取消") }
            Button(
                onClick = { if (frameSize.width > 0 && frameSize.height > 0) onConfirm(cropTransformedBitmap(bitmap, frameSize, scale, offset, outputSize)) },
                modifier = Modifier.weight(1f)
            ) { Text("使用此裁剪") }
        }
    }
}

private fun cropTransformedBitmap(bitmap: Bitmap, frameSize: IntSize, userScale: Float, offset: Offset, outputSize: IntSize): Bitmap {
    val baseScale = maxOf(frameSize.width.toFloat() / bitmap.width, frameSize.height.toFloat() / bitmap.height)
    val totalScale = baseScale * userScale
    val displayedWidth = bitmap.width * totalScale
    val displayedHeight = bitmap.height * totalScale
    val leftOnFrame = (frameSize.width - displayedWidth) / 2f + offset.x
    val topOnFrame = (frameSize.height - displayedHeight) / 2f + offset.y
    val sourceLeft = (-leftOnFrame / totalScale).coerceIn(0f, bitmap.width - 1f)
    val sourceTop = (-topOnFrame / totalScale).coerceIn(0f, bitmap.height - 1f)
    val sourceWidth = (frameSize.width / totalScale).coerceAtMost(bitmap.width - sourceLeft).coerceAtLeast(1f)
    val sourceHeight = (frameSize.height / totalScale).coerceAtMost(bitmap.height - sourceTop).coerceAtLeast(1f)
    val cropped = Bitmap.createBitmap(bitmap, sourceLeft.toInt(), sourceTop.toInt(), sourceWidth.toInt().coerceAtLeast(1), sourceHeight.toInt().coerceAtLeast(1))
    return Bitmap.createScaledBitmap(cropped, outputSize.width, outputSize.height, true)
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
