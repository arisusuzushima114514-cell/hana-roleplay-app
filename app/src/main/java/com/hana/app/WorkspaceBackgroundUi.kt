package com.hana.app

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hana.app.manager.BackgroundTarget
import com.hana.app.manager.SavedBackgroundInfo
import com.hana.app.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class BackgroundChoice(
    val label: String,
    val target: BackgroundTarget
)

internal data class PendingBackgroundSelection(
    val target: BackgroundTarget,
    val label: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackgroundScopeDialog(
    backgroundChoices: List<BackgroundChoice>,
    onDismiss: () -> Unit,
    onChoiceSelected: (BackgroundChoice) -> Unit
) {
    if (backgroundChoices.isEmpty()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bg_scope_title)) },
        text = {
            Column {
                Text(stringResource(R.string.bg_scope_message))
                backgroundChoices.forEach { choice ->
                    TextButton(onClick = { onChoiceSelected(choice) }) {
                        Text(choice.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackgroundSourceDialog(
    backgroundSourceChoices: List<PendingBackgroundSelection>,
    chatViewModel: ChatViewModel,
    backgroundPicker: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    onDismiss: () -> Unit,
    onOpenLibrary: (BackgroundTarget) -> Unit,
    onTargetSelected: (BackgroundTarget) -> Unit
) {
    if (backgroundSourceChoices.isEmpty()) return
    val targetSelection = backgroundSourceChoices.first()
    val savedBackgrounds = chatViewModel.savedBackgrounds()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("背景来源") },
        text = {
            Column {
                Text("为 ${targetSelection.label} 选择背景来源")
                TextButton(
                    onClick = {
                        onTargetSelected(targetSelection.target)
                        onDismiss()
                        backgroundPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("从相册选择")
                }
                TextButton(
                    onClick = {
                        onTargetSelected(targetSelection.target)
                        onDismiss()
                        onOpenLibrary(targetSelection.target)
                    }
                ) {
                    Text("打开背景库")
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    savedBackgrounds.take(8).forEach { bg ->
                        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(bg.updatedAt))
                        Surface(
                            onClick = {
                                onDismiss()
                                chatViewModel.applySavedBackground(bg.filePath, targetSelection.target)
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AsyncImage(
                                    model = bg.previewUri,
                                    contentDescription = bg.name,
                                    modifier = Modifier.size(96.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Text(bg.name, maxLines = 1)
                                Text(
                                    date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundLibrarySheet(
    showBackgroundLibrary: Boolean,
    chatViewModel: ChatViewModel,
    selectedCharacterForChatId: String?,
    resolveBackgroundTarget: () -> BackgroundTarget,
    backgroundPicker: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    onDismiss: () -> Unit,
    onTargetSelected: (BackgroundTarget) -> Unit,
    onRename: (SavedBackgroundInfo) -> Unit
) {
    if (!showBackgroundLibrary) return
    val savedBackgrounds = chatViewModel.savedBackgrounds()
    val currentGlobalPath = chatViewModel.currentBackgroundPath(BackgroundTarget.Global)
    val currentMainPath = chatViewModel.currentBackgroundPath(BackgroundTarget.MainChat)
    val currentCharacterPath = selectedCharacterForChatId?.let {
        chatViewModel.currentBackgroundPath(BackgroundTarget.Character(it))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "本地背景库",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "已保存的背景可以直接复用，不用每次重新从相册选择。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    onTargetSelected(resolveBackgroundTarget())
                    onDismiss()
                    backgroundPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Text("从相册选择")
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                savedBackgrounds.forEach { bg ->
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AsyncImage(
                                model = bg.previewUri,
                                contentDescription = bg.name,
                                modifier = Modifier.size(110.dp),
                                contentScale = ContentScale.Crop
                            )
                            Text(bg.name, maxLines = 1)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (bg.filePath == currentGlobalPath) {
                                    AssistChip(onClick = {}, label = { Text("当前全局") })
                                }
                                if (bg.filePath == currentMainPath) {
                                    AssistChip(onClick = {}, label = { Text("当前主聊") })
                                }
                                if (currentCharacterPath != null && bg.filePath == currentCharacterPath) {
                                    AssistChip(onClick = {}, label = { Text("当前角色") })
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(onClick = {
                                    chatViewModel.applySavedBackground(bg.filePath, resolveBackgroundTarget())
                                    onDismiss()
                                }) { Text("应用") }
                                TextButton(onClick = {
                                    chatViewModel.applySavedBackground(bg.filePath, BackgroundTarget.Global)
                                }) { Text("全局") }
                                TextButton(onClick = {
                                    chatViewModel.applySavedBackground(bg.filePath, BackgroundTarget.MainChat)
                                }) { Text("主聊") }
                                if (selectedCharacterForChatId != null) {
                                    TextButton(onClick = {
                                        chatViewModel.applySavedBackground(
                                            bg.filePath,
                                            BackgroundTarget.Character(selectedCharacterForChatId)
                                        )
                                    }) { Text("角色") }
                                }
                                TextButton(onClick = { onRename(bg) }) { Text("重命名") }
                                TextButton(onClick = {
                                    chatViewModel.deleteSavedBackground(bg.filePath)
                                }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackgroundRenameDialog(
    background: SavedBackgroundInfo?,
    chatViewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    background ?: return
    var text by remember(background.filePath) { mutableStateOf(background.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名背景") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (chatViewModel.renameSavedBackground(background.filePath, text)) {
                    onDismiss()
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
