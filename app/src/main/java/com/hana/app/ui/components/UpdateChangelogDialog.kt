package com.hana.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 版本更新推送弹窗。
 * 每次更新后首次打开 App 自动弹出，显示本次更新内容。
 * 用户可关闭或勾选"不再显示"。
 *
 * @param versionName 当前版本号，如 "1.7.4"
 * @param changelog 更新内容（Markdown 格式文本）
 * @param onDismiss 关闭回调
 * @param onDontShowAgain 用户勾选"不再显示"时的回调
 */
@Composable
fun UpdateChangelogDialog(
    versionName: String,
    changelog: String,
    onDismiss: () -> Unit,
    onDontShowAgain: (Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // 标题
                Text(
                    text = "更新日志",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v$versionName",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // 更新内容（可滚动）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    changelog.lines().forEach { line ->
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("### ") -> {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = trimmed.removePrefix("### "),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            trimmed.startsWith("- ") -> {
                                Row(
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                                ) {
                                    Text(
                                        text = "\u2022",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = trimmed.removePrefix("- "),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            trimmed.isBlank() -> {
                                Spacer(Modifier.height(8.dp))
                            }
                            else -> {
                                Text(
                                    text = trimmed,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // 底部按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "不再显示" 复选框
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { dontShowAgain = !dontShowAgain }
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { checked -> dontShowAgain = checked }
                        )
                        Text(
                            text = "不再显示",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 关闭按钮
                    Button(
                        onClick = {
                            onDontShowAgain(dontShowAgain)
                            onDismiss()
                        }
                    ) {
                        Text("知道了")
                    }
                }
            }
        }
    }
}