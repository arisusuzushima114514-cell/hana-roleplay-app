package com.hana.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Surface
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hana.app.R
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.ui.chat.ChatAttachment
import com.hana.app.ui.character.CharacterAvatar
import com.hana.app.ui.image.ImageChatPanel
import com.hana.app.ui.sidebar.ConversationDrawer
import com.hana.app.viewmodel.ChatUiState
import com.hana.app.viewmodel.ImageGenerationUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenWithDrawer(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendText: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onExportConversation: (String, Boolean) -> Unit,
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
    onUpdateConversationParameters: (String?, Float, Float, Int, Int) -> Unit,
    onUpdateSystemPrompt: (String) -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleFavorite: (ChatMessageEntity) -> Unit,
    onTogglePinned: (String) -> Unit = {},
    onToggleConvFav: (String) -> Unit = {},
    backgroundIntensity: String = "soft",
    onSelectModel: (com.hana.app.data.db.entity.ModelInfo) -> Unit = {},
    onToggleModelFavorite: (String) -> Unit = {},
    scrollTrigger: kotlinx.coroutines.flow.SharedFlow<Unit>? = null,
    errorFlow: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    personaEnabled: Boolean = false,
    onTogglePersona: () -> Unit = {},
    onPickBackground: () -> Unit = {},
    onOpenBackgroundLibrary: () -> Unit = {},
    onClearBackground: () -> Unit = {},
    imageModeEnabled: Boolean = false,
    onToggleImageMode: () -> Unit = {},
    imageUiState: ImageGenerationUiState? = null,
    imageEvents: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onImageInputChange: (String) -> Unit = {},
    onImageStyleChange: (String) -> Unit = {},
    onImageRatioChange: (String) -> Unit = {},
    onImageBatchCountChange: (Int) -> Unit = {},
    onImageNegativePromptChange: (String) -> Unit = {},
    onImageReferencePicked: (List<Uri>) -> Unit = {},
    onRemoveImageReference: (String) -> Unit = {},
    onClearImageReference: () -> Unit = {},
    onGenerateImage: () -> Unit = {},
    onStopImageGeneration: () -> Unit = {},
    onSaveGeneratedImage: (String) -> Unit = {},
    onReuseImagePrompt: (com.hana.app.viewmodel.ImageConversationItem) -> Unit = {},
    onContinueImageFromResult: (com.hana.app.viewmodel.ImageConversationItem, String) -> Unit = { _, _ -> }
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentConversation = uiState.conversations.firstOrNull { it.id == uiState.currentConversationId }
    val groupParticipants = remember(currentConversation, uiState.characters) {
        if (currentConversation?.conversationType != "group") {
            emptyList()
        } else {
            currentConversation.participantCharacterIds
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { id -> uiState.characters.firstOrNull { it.id == id } }
        }
    }
    val networkBannerState = rememberNetworkBannerState()
    var dismissedNetworkState by rememberSaveable { mutableStateOf<NetworkBannerState?>(null) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var editingTitleValue by remember { mutableStateOf("") }
    var topBarMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(networkBannerState) {
        if (networkBannerState != NetworkBannerState.Connected) {
            dismissedNetworkState = null
        }
    }

    LaunchedEffect(Unit) {
        dismissedNetworkState = null
    }

    BackHandler(enabled = drawerState.isOpen || isEditingTitle || topBarMenuExpanded) {
        when {
            topBarMenuExpanded -> {
                topBarMenuExpanded = false
            }
            isEditingTitle -> {
                isEditingTitle = false
                keyboardController?.hide()
            }
            drawerState.isOpen -> {
                scope.launch { drawerState.close() }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = uiState.conversations,
                selectedConversationId = uiState.currentConversationId,
                favorites = uiState.favorites,
                onNewConversation = {
                    onNewConversation()
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = {
                    onSelectConversation(it)
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = onRenameConversation,
                onDeleteConversation = onDeleteConversation,
                onExportConversation = onExportConversation,
                onTogglePinned = onTogglePinned,
                onToggleFav = onToggleConvFav
            )
        }
    ) {
        Scaffold(
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.99f), tonalElevation = 0.dp, shadowElevation = 0.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.chat_cd_open_conversations))
                            }
                            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                                Text(
                                    text = if (imageModeEnabled) stringResource(R.string.chat_title_image) else stringResource(R.string.chat_title_main),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (imageModeEnabled) stringResource(R.string.chat_subtitle_image) else (currentConversation?.title ?: stringResource(R.string.chat_subtitle_new)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!imageModeEnabled && currentConversation != null && !isEditingTitle) {
                                IconButton(
                                    onClick = {
                                        editingTitleValue = currentConversation.title
                                        isEditingTitle = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.chat_cd_edit_title), modifier = Modifier.size(18.dp))
                                }
                            }
                            Box {
                                IconButton(onClick = { topBarMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.more_actions),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = topBarMenuExpanded,
                                    onDismissRequest = { topBarMenuExpanded = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_cd_preferences)) },
                                        onClick = {
                                            topBarMenuExpanded = false
                                            onTogglePersona()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = if (personaEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                    if (!imageModeEnabled) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chat_chip_clear_bg)) },
                                            onClick = {
                                                topBarMenuExpanded = false
                                                onClearBackground()
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) }
                                        )
                                    }
                                }
                            }
                        }

                        if (!imageModeEnabled && groupParticipants.isNotEmpty()) {
                            GroupParticipantStrip(participants = groupParticipants)
                        }

                        if (isEditingTitle) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    BasicTextField(
                                        value = editingTitleValue,
                                        onValueChange = { editingTitleValue = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                            .onKeyEvent { event ->
                                                if (event.key == Key.Enter) {
                                                    onRenameConversation(currentConversation?.id ?: "", editingTitleValue)
                                                    isEditingTitle = false
                                                    true
                                                } else false
                                            }
                                    )
                                }
                                IconButton(onClick = {
                                    onRenameConversation(currentConversation?.id ?: "", editingTitleValue)
                                    isEditingTitle = false
                                }) {
                                    Icon(Icons.Filled.Check, "确认")
                                }
                                IconButton(onClick = { isEditingTitle = false }) {
                                    Icon(Icons.Filled.Close, "取消")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !imageModeEnabled,
                                onClick = {
                                    if (imageModeEnabled) onToggleImageMode()
                                },
                                label = { Text(stringResource(R.string.chat_chip_mode_chat)) },
                                leadingIcon = if (!imageModeEnabled) ({ Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp)) }) else null
                            )
                            FilterChip(
                                selected = imageModeEnabled,
                                onClick = {
                                    if (!imageModeEnabled) onToggleImageMode()
                                },
                                label = { Text(stringResource(R.string.chat_chip_mode_image)) },
                                leadingIcon = if (imageModeEnabled) ({ Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp)) }) else null
                            )
                            if (!imageModeEnabled) {
                                Spacer(Modifier.weight(1f))
                                AssistChip(
                                    onClick = onOpenBackgroundLibrary,
                                    label = { Text("背景库") },
                                    leadingIcon = { Icon(Icons.Filled.Wallpaper, null, modifier = Modifier.size(16.dp)) }
                                )
                                AssistChip(
                                    onClick = onPickBackground,
                                    label = { Text(stringResource(R.string.chat_chip_main_bg)) },
                                    leadingIcon = { Icon(Icons.Filled.Wallpaper, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(innerPadding)
            ) {
                NetworkStatusNotification(
                    state = networkBannerState,
                    dismissedState = dismissedNetworkState,
                    onDismiss = { dismissedNetworkState = networkBannerState },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                )
                Column(modifier = Modifier.fillMaxSize()) {
                    if (imageModeEnabled && imageUiState != null && imageEvents != null) {
                        ImageChatPanel(
                            uiState = imageUiState,
                            events = imageEvents,
                            onInputChange = onImageInputChange,
                        onStyleChange = onImageStyleChange,
                        onRatioChange = onImageRatioChange,
                        onBatchCountChange = onImageBatchCountChange,
                        onNegativePromptChange = onImageNegativePromptChange,
                        onReferenceImagesPicked = onImageReferencePicked,
                        onRemoveReferenceImage = onRemoveImageReference,
                        onClearReferenceImages = onClearImageReference,
                        onGenerate = onGenerateImage,
                        onStopGeneration = onStopImageGeneration,
                        onSaveImage = onSaveGeneratedImage,
                        onReusePrompt = onReuseImagePrompt,
                        onContinueFromImage = onContinueImageFromResult,
                        modifier = Modifier.weight(1f)
                        )
                    } else {
                        ChatScreen(
                            uiState = uiState,
                            onInputChange = onInputChange,
                            onSend = onSend,
                            onSendText = onSendText,
                            onDeleteMessage = onDeleteMessage,
                            onRegenerateMessage = onRegenerateMessage,
                            onEditMessage = onEditMessage,
                            onEditMessageToInput = onEditMessageToInput,
                            onRetryFromMessage = onRetryFromMessage,
                            onPersistImageAttachment = onPersistImageAttachment,
                            onPersistFileAttachment = onPersistFileAttachment,
                            onPersistCameraAttachment = onPersistCameraAttachment,
                            onSaveAttachmentImage = onSaveAttachmentImage,
                            onStopGeneration = onStopGeneration,
                            onRetryLastUserMessage = onRetryLastUserMessage,
                            onUpdateConversationParameters = onUpdateConversationParameters,
                            onUpdateSystemPrompt = onUpdateSystemPrompt,
                            onToggleWebSearch = onToggleWebSearch,
                            onToggleFavorite = onToggleFavorite,
                            backgroundIntensity = backgroundIntensity,
                            onSelectModel = onSelectModel,
                            onToggleModelFavorite = onToggleModelFavorite,
                            onScrollTrigger = scrollTrigger,
                            errorFlow = errorFlow,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupParticipantStrip(participants: List<CharacterCardEntity>) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("群聊中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        participants.forEach { participant ->
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (participant.avatarUrl.isNotBlank()) {
                        CharacterAvatar(avatarUrl = participant.avatarUrl, modifier = Modifier.size(24.dp))
                    } else {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(participant.name.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(participant.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

private enum class NetworkBannerState {
    Connected,
    Disconnected,
    Reconnected
}

@Composable
private fun rememberNetworkBannerState(): NetworkBannerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(NetworkBannerState.Connected) }
    var wasDisconnected by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun isConnected(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        state = if (isConnected()) NetworkBannerState.Connected else NetworkBannerState.Disconnected
        wasDisconnected = state == NetworkBannerState.Disconnected

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    if (wasDisconnected) {
                        state = NetworkBannerState.Reconnected
                    } else {
                        state = NetworkBannerState.Connected
                    }
                    wasDisconnected = false
                }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    wasDisconnected = true
                    state = NetworkBannerState.Disconnected
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                scope.launch {
                    val connected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (connected) {
                        if (wasDisconnected) {
                            state = NetworkBannerState.Reconnected
                            wasDisconnected = false
                        }
                    } else {
                        wasDisconnected = true
                        state = NetworkBannerState.Disconnected
                    }
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    LaunchedEffect(state) {
        if (state == NetworkBannerState.Reconnected) {
            delay(2_000)
            state = NetworkBannerState.Connected
        }
    }

    return state
}

@Composable
private fun NetworkStatusNotification(
    state: NetworkBannerState,
    dismissedState: NetworkBannerState? = null,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var lastHapticState by remember { mutableStateOf(NetworkBannerState.Connected) }
    val visible = state != NetworkBannerState.Connected || dismissedState != state

    LaunchedEffect(state) {
        if (state != lastHapticState) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticState = state

        if (state == NetworkBannerState.Connected) {
            delay(1800)
            onDismiss()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "networkBanner")
    val disconnectedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "networkBannerAlpha"
    )
    val targetColor = when (state) {
        NetworkBannerState.Disconnected -> MaterialTheme.colorScheme.error.copy(alpha = disconnectedAlpha)
        NetworkBannerState.Reconnected -> Color(0xFF2E7D32)
        NetworkBannerState.Connected -> Color.Transparent
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(250),
        label = "networkBannerColor"
    )
    val containerColor = when (state) {
        NetworkBannerState.Disconnected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f)
        NetworkBannerState.Reconnected -> Color(0xFFE8F5E9)
        NetworkBannerState.Connected -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val contentColor = when (state) {
        NetworkBannerState.Disconnected -> MaterialTheme.colorScheme.onErrorContainer
        NetworkBannerState.Reconnected -> Color(0xFF1B5E20)
        NetworkBannerState.Connected -> MaterialTheme.colorScheme.onSurface
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + androidx.compose.animation.slideInVertically(
            animationSpec = tween(320),
            initialOffsetY = { -it }
        ),
        exit = fadeOut(tween(220)) + androidx.compose.animation.slideOutVertically(
            animationSpec = tween(220),
            targetOffsetY = { -it / 3 }
        )
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            Surface(
                color = containerColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                shadowElevation = if (state == NetworkBannerState.Connected) 6.dp else 14.dp,
                tonalElevation = if (state == NetworkBannerState.Connected) 2.dp else 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(if (state == NetworkBannerState.Connected) 0.72f else 0.92f)
                    .widthIn(max = if (state == NetworkBannerState.Connected) 280.dp else 420.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = color,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = when (state) {
                                NetworkBannerState.Disconnected -> Icons.Filled.CloudOff
                                NetworkBannerState.Reconnected -> Icons.Filled.CloudDone
                                NetworkBannerState.Connected -> Icons.Filled.Check
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state) {
                                NetworkBannerState.Disconnected -> stringResource(R.string.network_disconnected)
                                NetworkBannerState.Reconnected -> stringResource(R.string.network_reconnected)
                                NetworkBannerState.Connected -> stringResource(R.string.network_connected)
                            },
                            color = contentColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when (state) {
                                NetworkBannerState.Disconnected -> stringResource(R.string.network_disconnected_detail)
                                NetworkBannerState.Reconnected -> stringResource(R.string.network_reconnected_detail)
                                NetworkBannerState.Connected -> stringResource(R.string.network_connected_detail)
                            },
                            color = contentColor.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (state != NetworkBannerState.Connected) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
