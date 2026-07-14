package com.hana.app.ui.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationDrawer(
    conversations: List<ConversationEntity>,
    selectedConversationId: String?,
    favorites: List<ChatMessageEntity> = emptyList(),
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onExportConversation: (String, Boolean) -> Unit,
    onTogglePinned: (String) -> Unit = {},
    onToggleFav: (String) -> Unit = {}
) {
    var menuConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var deletingConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var showFavorites by remember { mutableStateOf(false) }

    val filtered = remember(conversations, query) {
        val kw = query.trim()
        if (kw.isBlank()) conversations.filter { it.characterId == null }
        else conversations.filter { it.characterId == null && (it.title.contains(kw, true) || it.lastMessage.orEmpty().contains(kw, true)) }
    }

    val todayStart = remember { resetTime(0) }
    val yesterdayStart = remember { resetTime(-1) }

    val grouped = remember(filtered) {
        val pinned = filtered.filter { it.isPinned }
        val unpinned = filtered.filter { !it.isPinned }
        val today = unpinned.filter { it.updatedAt >= todayStart }
        val yesterday = unpinned.filter { it.updatedAt in yesterdayStart until todayStart }
        val earlier = unpinned.filter { it.updatedAt < yesterdayStart }
        mutableListOf<Pair<String, List<ConversationEntity>>>().apply {
            if (pinned.isNotEmpty()) add("置顶" to pinned)
            if (today.isNotEmpty()) add("今天" to today)
            if (yesterday.isNotEmpty()) add("昨天" to yesterday)
            if (earlier.isNotEmpty()) add("更早" to earlier)
        }
    }

    ModalDrawerSheet {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("会话导航", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("像酒馆类应用一样管理主线程、收藏和置顶历史。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${filtered.size} 个会话", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)) {
                            Text("主线", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("快速操作", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onNewConversation, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, "新对话", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (showFavorites) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("收藏夹", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showFavorites = false }) { Text("返回") }
                }
                Spacer(Modifier.height(8.dp))
                if (favorites.isEmpty()) {
                    Text("暂无收藏", color = Color.Gray)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(favorites) { msg ->
                            FavoriteItem(msg, conversations, onClick = { onSelectConversation(msg.conversationId); showFavorites = false })
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索对话或最后一句消息", style = MaterialTheme.typography.bodySmall) }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).height(48.dp)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onNewConversation, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Add, "新对话", modifier = Modifier.size(20.dp))
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                if (favorites.isNotEmpty()) {
                    item {
                        Card(
                            onClick = { showFavorites = true },
                            colors = CardDefaults.cardColors(containerColor = Color(0x14F59E0B)),
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107))
                                Spacer(Modifier.padding(start = 8.dp))
                                Text("收藏夹 (${favorites.size})", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                grouped.forEach { (sectionName, items) ->
                    item(key = "hdr-$sectionName") {
                        Text(sectionName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp))
                    }
                    items(items, key = { it.id }) { conversation ->
                        SwipeableConversationCard(
                            conversation = conversation,
                            selectedConversationId = selectedConversationId,
                            query = query,
                            onSelect = { onSelectConversation(conversation.id) },
                            onLongPress = { menuConversation = conversation; renameValue = conversation.title },
                            onDelete = { onDeleteConversation(conversation.id) },
                            onTogglePinned = { onTogglePinned(conversation.id) },
                            onToggleFav = { onToggleFav(conversation.id) }
                        )
                    }
                }
            }
        }
    }

    menuConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { menuConversation = null },
            title = { Text("对话操作") },
            text = {
                Column {
                    OutlinedTextField(renameValue, { renameValue = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { onTogglePinned(conv.id) }) { Text(if (conv.isPinned) "取消置顶" else "置顶") }
                        TextButton(onClick = { onToggleFav(conv.id) }) { Text(if (conv.isFavorite) "取消收藏" else "收藏") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRenameConversation(conv.id, renameValue); menuConversation = null }) { Text("保存") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { onExportConversation(conv.id, true); menuConversation = null }) { Text("导出 Markdown") }
                    TextButton(onClick = { onExportConversation(conv.id, false); menuConversation = null }) { Text("导出文本") }
                    TextButton(onClick = { deletingConversation = conv; menuConversation = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    deletingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { deletingConversation = null },
            title = { Text("删除对话") },
            text = { Text("删除后无法恢复。") },
            confirmButton = { TextButton(onClick = { onDeleteConversation(conv.id); deletingConversation = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingConversation = null }) { Text("取消") } }
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableConversationCard(
    conversation: ConversationEntity,
    selectedConversationId: String?,
    query: String,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleFav: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (conversation.id == selectedConversationId) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.isPinned) {
                    Icon(Icons.Filled.PushPin, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = highlightText(conversation.title, query),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    fontWeight = if (conversation.id == selectedConversationId) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.isFavorite) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Filled.Star, null, Modifier.size(11.dp), tint = Color(0xFFFFC107))
                }
                if (conversation.isPinned) {
                    Spacer(Modifier.width(2.dp))
                    Text("置顶", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.MoreVert, "更多", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (conversation.isPinned) "取消置顶" else "置顶") },
                            onClick = { onTogglePinned(); menuExpanded = false },
                            leadingIcon = { Icon(Icons.Filled.PushPin, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (conversation.isFavorite) "取消收藏" else "收藏") },
                            onClick = { onToggleFav(); menuExpanded = false },
                            leadingIcon = { Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107)) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(); menuExpanded = false },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
            if (!conversation.lastMessage.isNullOrBlank()) {
                Text(
                    highlightText(conversation.lastMessage.orEmpty(), query),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (conversation.characterName != null) {
                Text(conversation.characterName, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

@Composable
private fun FavoriteItem(
    message: ChatMessageEntity,
    conversations: List<ConversationEntity>,
    onClick: () -> Unit
) {
    val conv = conversations.firstOrNull { it.id == message.conversationId }
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(message.content.take(80) + if (message.content.length > 80) "..." else "", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row {
                Text(conv?.title ?: "未知对话", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(Modifier.padding(start = 8.dp))
                Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(message.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

private fun highlightText(text: String, query: String) = buildAnnotatedString {
    val kw = query.trim()
    if (kw.isBlank()) { append(text); return@buildAnnotatedString }
    var cursor = 0
    val lower = text.lowercase(Locale.getDefault())
    val lowerKw = kw.lowercase(Locale.getDefault())
    while (cursor < text.length) {
        val idx = lower.indexOf(lowerKw, cursor)
        if (idx == -1) { append(text.substring(cursor)); break }
        append(text.substring(cursor, idx))
        withStyle(SpanStyle(color = Color(0xFFFFA000), fontWeight = FontWeight.Bold)) { append(text.substring(idx, idx + kw.length)) }
        cursor = idx + kw.length
    }
}

private fun resetTime(dayOffset: Int): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, dayOffset)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
