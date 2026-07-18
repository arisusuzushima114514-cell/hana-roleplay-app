package com.hana.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hana.app.data.db.entity.Capability
import com.hana.app.data.db.entity.ModelInfo
import com.hana.app.ui.settings.detectNativeWebSearchSupport
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelInfo>,
    favorites: List<ModelInfo>,
    currentModelName: String,
    onDismiss: () -> Unit,
    onSelectModel: (ModelInfo) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    val clipboardManager = LocalClipboardManager.current

    val favForProvider = remember(models, favorites) {
        favorites.filter { fav -> models.any { it.id == fav.id } }
    }

    val displayList = remember(tab, models, favForProvider, search) {
        val base = when (tab) {
            1 -> favForProvider
            else -> models
        }
        if (search.isBlank()) base
        else base.filter { it.name.contains(search, true) || it.provider.contains(search, true) }
    }

    val grouped = remember(displayList) { displayList.groupBy { it.provider } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }
            }
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("全部 (${models.size})") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("收藏 (${favForProvider.size})") })
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("搜索模型...") },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            if (tab == 1 && favForProvider.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Star, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("还没有收藏模型", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("切换到全部标签浏览所有可用模型", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { tab = 0 },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Filled.Explore, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("浏览全部模型")
                        }
                    }
                }
            } else if (displayList.isEmpty() && search.isNotBlank()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("未找到匹配的模型", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("试试其他关键词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { search = "" }, shape = RoundedCornerShape(12.dp)) { Text("清空搜索") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    grouped.forEach { (provider, items) ->
                        val sampleUrl = items.firstOrNull()?.baseUrl.orEmpty()
                        item(key = "h-$provider") {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(provider, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.weight(1f))
                                    Text("${items.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (sampleUrl.isNotBlank()) {
                                    Text(sampleUrl.take(50), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Surface(Modifier.fillMaxWidth().height(0.5.dp), color = MaterialTheme.colorScheme.outline) {}
                            Spacer(Modifier.height(2.dp))
                        }
                        items(items, key = { it.id }) { model ->
                            val isCurrent = model.name == currentModelName
                            val webSearchSupport = remember(model.name, model.provider, model.baseUrl) {
                                detectNativeWebSearchSupport(modelName = model.name, providerName = model.provider, baseUrl = model.baseUrl)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onSelectModel(model) },
                                        onLongClick = {
                                            clipboardManager.setText(AnnotatedString(model.name))
                                            onSelectModel(model)
                                        }
                                    )
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isCurrent) "●" else "○", fontSize = 12.sp, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(model.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            model.capabilities.forEach { Text(it.emoji, fontSize = 12.sp) }
                                            // 联网能力指示灯
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (webSearchSupport.supported) Color(0xFF16A34A).copy(alpha = 0.15f) else Color(0xFF9CA3AF).copy(alpha = 0.12f),
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(50),
                                                        color = if (webSearchSupport.supported) Color(0xFF16A34A) else Color(0xFF9CA3AF),
                                                        modifier = Modifier.size(7.dp)
                                                    ) {}
                                                    Text(
                                                        if (webSearchSupport.supported) "联网" else "离线",
                                                        fontSize = 11.sp,
                                                        color = if (webSearchSupport.supported) Color(0xFF15803D) else Color(0xFF6B7280),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(onClick = { onToggleFavorite(model.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = if (model.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
