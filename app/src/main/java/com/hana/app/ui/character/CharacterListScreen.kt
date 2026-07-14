package com.hana.app.ui.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.hana.app.R
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CharacterSection {
    Group,
    Character,
    Story
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    characters: List<CharacterCardEntity>,
    conversations: List<ConversationEntity>,
    creativePresetEnabledByCharacter: Map<String, Boolean> = emptyMap(),
    creativePresetAffectsPersonaByCharacter: Map<String, Boolean> = emptyMap(),
    onCreateCharacter: () -> Unit,
    onCreateStory: () -> Unit,
    onCreateGroupChat: (List<CharacterCardEntity>) -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onExportConversation: (String, Boolean) -> Unit,
    onEditCharacter: (CharacterCardEntity) -> Unit,
    onDeleteCharacter: (CharacterCardEntity) -> Unit,
    onSelectCharacter: (CharacterCardEntity) -> Unit,
    onExportCharacter: (CharacterCardEntity) -> Unit = {},
    onExportCharacterJson: (CharacterCardEntity) -> Unit = {},
    onExportCharacterPng: (CharacterCardEntity) -> Unit = {},
    onImportCharacterJson: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var selectedGroupIds by remember { mutableStateOf(setOf<String>()) }
    var currentSection by remember { mutableStateOf(CharacterSection.Character) }
    val filteredCharacters = remember(characters, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) characters else characters.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true) ||
            it.tags.contains(q, ignoreCase = true)
        }
    }
    val filteredGroupConversations = remember(conversations, searchQuery) {
        val q = searchQuery.trim()
        conversations.filter { it.conversationType == "group" }.filter {
            q.isBlank() || it.title.contains(q, ignoreCase = true) || it.lastMessage.orEmpty().contains(q, ignoreCase = true)
        }
    }
    val filteredStoryConversations = remember(conversations, searchQuery) {
        val q = searchQuery.trim()
        conversations.filter { it.title.startsWith("故事：") }.filter {
            q.isBlank() || it.title.contains(q, ignoreCase = true) || it.lastMessage.orEmpty().contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                TopAppBar(
                    title = { Text("角色宇宙", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("酒馆角色厅", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(filteredCharacters.size.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("独立角色池", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("群聊、角色卡、故事都从这里分开进入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentSection == CharacterSection.Group,
                        onClick = { currentSection = CharacterSection.Group },
                        label = { Text("群聊（测试）") },
                        leadingIcon = if (currentSection == CharacterSection.Group) ({ Icon(Icons.Filled.Forum, null, Modifier.size(16.dp)) }) else null
                    )
                    FilterChip(
                        selected = currentSection == CharacterSection.Character,
                        onClick = { currentSection = CharacterSection.Character },
                        label = { Text("角色卡") },
                        leadingIcon = if (currentSection == CharacterSection.Character) ({ Icon(Icons.Filled.ChatBubbleOutline, null, Modifier.size(16.dp)) }) else null
                    )
                    FilterChip(
                        selected = currentSection == CharacterSection.Story,
                        onClick = { currentSection = CharacterSection.Story },
                        label = { Text("故事") },
                        leadingIcon = if (currentSection == CharacterSection.Story) ({ Icon(Icons.Filled.MenuBook, null, Modifier.size(16.dp)) }) else null
                    )
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            when (currentSection) {
                                CharacterSection.Group -> "搜索群聊..."
                                CharacterSection.Character -> "搜索角色..."
                                CharacterSection.Story -> "搜索故事..."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(50.dp)
                )
            }
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showCreateMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.create_character),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                DropdownMenu(expanded = showCreateMenu, onDismissRequest = { showCreateMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("创建角色卡") },
                        onClick = { showCreateMenu = false; onCreateCharacter() }
                    )
                    DropdownMenuItem(
                        text = { Text("导入角色卡") },
                        leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                        onClick = {
                            showCreateMenu = false
                            onImportCharacterJson()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("创建故事") },
                        onClick = { showCreateMenu = false; onCreateStory() }
                    )
                    DropdownMenuItem(
                        text = { Text("创建群聊") },
                        leadingIcon = { Icon(Icons.Filled.Forum, contentDescription = null) },
                        onClick = {
                            showCreateMenu = false
                            selectedGroupIds = emptySet()
                            showGroupDialog = true
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (showGroupDialog) {
            AlertDialog(
                onDismissRequest = { showGroupDialog = false },
                title = { Text("创建角色群聊") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("勾选 2 到 6 个角色，创建一个会依次回复的群聊。")
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredCharacters, key = { it.id }) { character ->
                                val checked = selectedGroupIds.contains(character.id)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedGroupIds = if (checked) {
                                                selectedGroupIds - character.id
                                            } else {
                                                (selectedGroupIds + character.id).take(6).toSet()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                selectedGroupIds = if (it) {
                                                    (selectedGroupIds + character.id).take(6).toSet()
                                                } else {
                                                    selectedGroupIds - character.id
                                                }
                                            }
                                        )
                                        Text(character.name, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onCreateGroupChat(characters.filter { selectedGroupIds.contains(it.id) })
                            showGroupDialog = false
                        },
                        enabled = selectedGroupIds.size >= 2
                    ) {
                        Text("创建")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGroupDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
        if (currentSection == CharacterSection.Character && characters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_characters_yet),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.no_characters_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            when (currentSection) {
                CharacterSection.Character -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            end = 16.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCharacters, key = { it.id }) { character ->
                            CharacterListItem(
                                character = character,
                                creativePresetEnabled = creativePresetEnabledByCharacter[character.id] == true,
                                creativePresetAffectsPersona = creativePresetAffectsPersonaByCharacter[character.id] == true,
                                onClick = { onSelectCharacter(character) },
                                onEdit = { onEditCharacter(character) },
                                onDelete = { onDeleteCharacter(character) },
                                onExport = { onExportCharacter(character) },
                                onExportJson = { onExportCharacterJson(character) },
                                onExportPng = { onExportCharacterPng(character) }
                            )
                        }
                    }
                }
                CharacterSection.Group -> {
                    ConversationSectionList(
                        items = filteredGroupConversations,
                        characters = characters,
                        emptyTitle = "还没有群聊",
                        emptyDesc = "点右下角加号，选择“创建群聊”后勾选角色。",
                        innerPadding = innerPadding,
                        onOpen = onSelectConversation,
                        onRenameConversation = onRenameConversation,
                        onDeleteConversation = onDeleteConversation,
                        onExportConversation = onExportConversation,
                        topAction = {
                            FilledTonalButton(onClick = {
                                selectedGroupIds = emptySet()
                                showGroupDialog = true
                            }) {
                                Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("创建群聊")
                            }
                        }
                    )
                }
                CharacterSection.Story -> {
                    ConversationSectionList(
                        items = filteredStoryConversations,
                        characters = characters,
                        emptyTitle = "还没有故事",
                        emptyDesc = "点右下角加号，选择“创建故事”开始。",
                        innerPadding = innerPadding,
                        onOpen = onSelectConversation,
                        onRenameConversation = onRenameConversation,
                        onDeleteConversation = onDeleteConversation,
                        onExportConversation = onExportConversation,
                        topAction = {
                            TextButton(onClick = onCreateStory) { Text("创建故事") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationSectionList(
    items: List<ConversationEntity>,
    characters: List<CharacterCardEntity>,
    emptyTitle: String,
    emptyDesc: String,
    innerPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onRenameConversation: ((String, String) -> Unit)? = null,
    onDeleteConversation: ((String) -> Unit)? = null,
    onExportConversation: ((String, Boolean) -> Unit)? = null,
    topAction: @Composable (() -> Unit)? = null
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(emptyDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (topAction != null) {
                    Spacer(Modifier.height(10.dp))
                    topAction()
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            end = 16.dp,
            bottom = 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (topAction != null) {
            item(key = "top-action") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    topAction()
                }
            }
        }
        items(items, key = { it.id }) { conversation ->
            var menuExpanded by remember(conversation.id) { mutableStateOf(false) }
            var renameOpen by remember(conversation.id) { mutableStateOf(false) }
            var renameValue by remember(conversation.id, conversation.title) { mutableStateOf(conversation.title) }
            val participantIds = conversation.participantCharacterIds
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val participantCharacters = participantIds.mapNotNull { id -> characters.firstOrNull { it.id == id } }
            val isGroup = conversation.conversationType == "group"
            val isStory = conversation.title.startsWith("故事：")
            val timeText = remember(conversation.updatedAt) { formatTimeAgo(conversation.updatedAt) }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(conversation.id) }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isGroup) {
                            GroupAvatarStack(participants = participantCharacters)
                        } else if (isStory) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("故事", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text(conversation.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                if (onRenameConversation != null) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        onClick = {
                                            menuExpanded = false
                                            renameOpen = true
                                        }
                                    )
                                }
                                if (onExportConversation != null) {
                                    DropdownMenuItem(
                                        text = { Text("导出 Markdown") },
                                        onClick = {
                                            menuExpanded = false
                                            onExportConversation(conversation.id, true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("导出文本") },
                                        onClick = {
                                            menuExpanded = false
                                            onExportConversation(conversation.id, false)
                                        }
                                    )
                                }
                                if (onDeleteConversation != null) {
                                    DropdownMenuItem(
                                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            onDeleteConversation(conversation.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (isGroup) {
                        Text(
                            buildString {
                                append("${participantCharacters.size} 位角色")
                                if (participantCharacters.isNotEmpty()) {
                                    append(" · ")
                                    append(participantCharacters.joinToString("、") { it.name }.take(36))
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (isGroup) "最近活跃 · $timeText" else timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!conversation.lastMessage.isNullOrBlank()) {
                        Text(
                            conversation.lastMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (renameOpen && onRenameConversation != null) {
                AlertDialog(
                    onDismissRequest = { renameOpen = false },
                    title = { Text("重命名") },
                    text = {
                        OutlinedTextField(
                            value = renameValue,
                            onValueChange = { renameValue = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onRenameConversation(conversation.id, renameValue)
                                renameOpen = false
                            },
                            enabled = renameValue.isNotBlank()
                        ) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameOpen = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupAvatarStack(participants: List<CharacterCardEntity>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp), verticalAlignment = Alignment.CenterVertically) {
        participants.take(3).forEach { participant ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(28.dp)
            ) {
                if (participant.avatarUrl.isNotBlank()) {
                    CharacterAvatar(avatarUrl = participant.avatarUrl, modifier = Modifier.size(28.dp))
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(participant.name.take(1), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterListItem(
    character: CharacterCardEntity,
    creativePresetEnabled: Boolean,
    creativePresetAffectsPersona: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onExportJson: () -> Unit,
    onExportPng: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeText = remember(character.lastMessageAt, character.updatedAt) {
        val ts = if (character.lastMessageAt > 0L) character.lastMessageAt else character.updatedAt
        formatTimeAgo(ts)
    }
    val creativePresetLabel = when {
        !creativePresetEnabled -> null
        creativePresetAffectsPersona -> "影响人格"
        else -> "模型补充"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .clickable { onClick() }
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onExport() }) },
                contentAlignment = Alignment.Center
            ) {
                if (character.avatarUrl.isNotBlank()) {
                    CharacterAvatar(
                        avatarUrl = character.avatarUrl,
                        modifier = Modifier.size(60.dp)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = character.name.take(1),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (creativePresetLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (creativePresetAffectsPersona) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (creativePresetAffectsPersona) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = creativePresetLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (creativePresetAffectsPersona) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = character.lastMessagePreview.ifBlank {
                        character.greeting.take(40)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("导出对话") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            onClick = { menuExpanded = false; onExport() }
                        )
                        DropdownMenuItem(
                            text = { Text("导出角色卡 JSON") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            onClick = { menuExpanded = false; onExportJson() }
                        )
                        DropdownMenuItem(
                            text = { Text("导出角色卡 PNG") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            onClick = { menuExpanded = false; onExportPng() }
                        )
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterAvatar(
    avatarUrl: String,
    modifier: Modifier = Modifier
) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(avatarUrl)
            .crossfade(true)
            .build(),
        contentDescription = "角色头像",
        modifier = modifier.clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        },
        error = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    )
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}
