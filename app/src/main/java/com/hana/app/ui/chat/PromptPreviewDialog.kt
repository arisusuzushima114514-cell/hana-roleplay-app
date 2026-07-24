package com.hana.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hana.app.viewmodel.PromptPreviewState

@Composable
fun PromptPreviewDialog(
    state: PromptPreviewState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val systemText = state.messages.firstOrNull { it.role == "system" }?.text.orEmpty()
    val characterPresetEnabled = systemText.contains("【角色创作预设·已启用·本轮必须完整执行】")
    val breakArmorEnabled = systemText.contains("【破甲提示·最终执行核心】")
    val japaneseSemanticBridgeEnabled = systemText.contains("【日语语义辅助层·破甲提示联动·已启用")
    val japaneseSemanticMode = when {
        systemText.contains("完整双重校验") -> "完整双重校验"
        japaneseSemanticBridgeEnabled -> "轻量校验"
        else -> "未启用"
    }
    val roleFusionEnabled = systemText.contains("【第三层·角色状态融合】")
    val chineseDraftCheckEnabled = systemText.contains("【第三层·中文成稿校验】")
    val executionLockEnabled = systemText.contains("【第四层·破甲执行优先级锁·system末端】")
    val presetEnabled = characterPresetEnabled || breakArmorEnabled
    val presetStatus = when {
        presetEnabled -> "角色创作预设：${if (characterPresetEnabled) "已注入" else "未启用"} · 破甲提示：${if (breakArmorEnabled) "已注入" else "未启用"}"
        systemText.contains("【Hana注入状态】当前角色未启用创作预设或破甲提示。") -> "当前角色均未启用"
        else -> "未检测到角色提示"
    }
    val copyText = state.messages.joinToString("\n\n") { message ->
        "role: ${message.role}\ntext:\n${message.text}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查看最终 Prompt") },
        text = {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> Text(state.error)
                state.messages.isEmpty() -> Text("当前对话还没有可发送的消息")
                else -> SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            color = if (presetEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("角色提示状态", fontWeight = FontWeight.Bold)
                                Text(
                                    presetStatus,
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = if (presetEnabled) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                if (presetEnabled) {
                                    Text(
                                        "请在下方 role: system 中搜索【角色创作预设】或【破甲提示】查看实际注入原文。",
                                        modifier = Modifier.padding(top = 4.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (breakArmorEnabled) {
                                        Text(
                                            "日语语义辅助：${if (japaneseSemanticBridgeEnabled) "已注入 · $japaneseSemanticMode" else "未检测到"}",
                                            modifier = Modifier.padding(top = 4.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (japaneseSemanticBridgeEnabled) {
                                            Text(
                                                "第三层：角色状态融合${if (roleFusionEnabled) "已注入" else "缺失"} · 中文成稿校验${if (chineseDraftCheckEnabled) "已注入" else "缺失"}",
                                                modifier = Modifier.padding(top = 3.dp),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                "第四层：末端执行优先级锁${if (executionLockEnabled) "已注入" else "缺失"}",
                                                modifier = Modifier.padding(top = 3.dp),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        state.messages.forEach { message ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("role: ${message.role}", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = message.text,
                                        modifier = Modifier.padding(top = 6.dp),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { clipboardManager.setText(AnnotatedString(copyText)) },
                enabled = copyText.isNotBlank() && !state.isLoading
            ) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("刷新") }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
