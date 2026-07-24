package com.hana.app.ui.character

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hana.app.R
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.CHARACTER_MODE_SINGLE
import com.hana.app.data.db.entity.EMPTY_SUB_CHARACTERS_JSON
import java.util.UUID

private val presetTags = listOf("治愈", "二次元", "学习", "编程", "语言", "娱乐", "日常", "倾诉", "冒险", "奇幻")
private val characterModelSuggestions = listOf("gpt-4o", "gpt-4.1", "grok-4", "grok-4.5", "claude-3.7-sonnet", "gemini-2.5-pro")

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(
    initialCharacter: CharacterCardEntity?,
    onBack: () -> Unit,
    onSave: (CharacterCardEntity) -> Unit,
    onDelete: (CharacterCardEntity) -> Unit
) {
    val isEdit = initialCharacter != null
    var name by remember { mutableStateOf(initialCharacter?.name.orEmpty()) }
    var avatarUrl by remember { mutableStateOf(initialCharacter?.avatarUrl.orEmpty()) }
    var description by remember { mutableStateOf(initialCharacter?.description.orEmpty()) }
    var greeting by remember { mutableStateOf(initialCharacter?.greeting.orEmpty()) }
    var userPersona by remember { mutableStateOf(initialCharacter?.userPersona.orEmpty()) }
    var tags by remember { mutableStateOf(initialCharacter?.tags.orEmpty()) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSmartImportDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var charModelId by remember { mutableStateOf(initialCharacter?.modelId.orEmpty()) }
    var charTemperature by remember { mutableStateOf(initialCharacter?.temperature ?: 0.9f) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            avatarUrl = uri.toString()
            showAvatarDialog = false
        }
    }

    val tagList = remember(tags) {
        tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun toggleTag(tag: String) {
        val current = tagList.toMutableList()
        if (current.contains(tag)) current.remove(tag) else current.add(tag)
        tags = current.joinToString(",")
    }

    fun buildCharacter(): CharacterCardEntity {
        val now = System.currentTimeMillis()
        return CharacterCardEntity(
            id = initialCharacter?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            avatarUrl = avatarUrl.trim(),
            description = description.trim(),
            greeting = greeting.trim(),
            userPersona = userPersona.trim(),
            tags = tags.trim(),
            modelId = charModelId.trim(),
            temperature = charTemperature,
            createdAt = initialCharacter?.createdAt ?: now,
            updatedAt = now,
            lastMessageAt = initialCharacter?.lastMessageAt ?: 0L,
            lastMessagePreview = initialCharacter?.lastMessagePreview.orEmpty(),
            characterMode = initialCharacter?.characterMode ?: CHARACTER_MODE_SINGLE,
            subCharactersJson = initialCharacter?.subCharactersJson ?: EMPTY_SUB_CHARACTERS_JSON
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) "编辑角色卡" else "创建角色卡",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("角色构建器", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { showAvatarDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            CharacterAvatar(
                                avatarUrl = avatarUrl,
                                modifier = Modifier.size(92.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(if (isEdit) "继续雕刻这个角色的世界观与说话方式" else "从头像、开场白和人设开始，搭出一个有生命力的角色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "点击头像设置形象",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showSmartImportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text("智能导入", color = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("角色身份", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("角色名称") },
                        placeholder = { Text(stringResource(R.string.character_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("世界观与人设", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("角色描述/人设") },
                        placeholder = { Text(stringResource(R.string.character_desc_hint)) },
                        minLines = 4,
                        maxLines = 20,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        supportingText = {
                            Text("${description.length} 字", style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("第一句话", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = greeting,
                        onValueChange = { greeting = it },
                        label = { Text("开场白") },
                        placeholder = { Text(stringResource(R.string.character_greeting_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("你在这个剧本里的身份", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = userPersona,
                        onValueChange = { userPersona = it },
                        label = { Text(stringResource(R.string.my_persona)) },
                        placeholder = { Text(stringResource(R.string.character_user_persona_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.tags_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetTags.forEach { tag ->
                            FilterChip(
                                selected = tagList.contains(tag),
                                onClick = { toggleTag(tag) },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("模型与参数", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = charModelId,
                        onValueChange = { charModelId = it },
                        label = { Text("绑定模型（留空=全局默认）") },
                        placeholder = { Text("如: gpt-4o, grok-4.5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "模型辅助",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        characterModelSuggestions.forEach { modelName ->
                            FilterChip(
                                selected = charModelId == modelName,
                                onClick = { charModelId = modelName },
                                modifier = Modifier.combinedClickable(
                                    onClick = { charModelId = modelName },
                                    onLongClick = {
                                        clipboardManager.setText(AnnotatedString(modelName))
                                        charModelId = modelName
                                    }
                                ),
                                label = { Text(modelName) }
                            )
                        }
                    }
                    Text(
                        "点按直接填入，长按会复制模型名并填入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Temperature: ${"%.1f".format(charTemperature)}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = charTemperature,
                        onValueChange = { charTemperature = it },
                        valueRange = 0f..2f
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isBlank() || isSaving) return@Button
                    isSaving = true
                    onSave(buildCharacter())
                },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.save), color = MaterialTheme.colorScheme.onPrimary)
            }

            if (isEdit) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.delete_character),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAvatarDialog) {
        var urlInput by remember { mutableStateOf(avatarUrl) }
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text("设置头像/封面") },
            text = {
                Column {
                    Button(
                        onClick = { avatarPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("选择本地图片")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("图片链接或本地 Uri") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (urlInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center
                        ) {
                            CharacterAvatar(avatarUrl = urlInput, modifier = Modifier.size(80.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        avatarUrl = urlInput.trim()
                        showAvatarDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_character)) },
            text = { Text(stringResource(R.string.delete_character_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(buildCharacter())
                        showDeleteConfirm = false
                        onBack()
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSmartImportDialog) {
        var importText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSmartImportDialog = false },
            title = { Text("智能导入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "粘贴角色卡的描述文本，系统会自动识别并填入对应字段。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        minLines = 6,
                        placeholder = { Text("粘贴非结构化文本...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importText.isNotBlank()) {
                            val result = parseCharacterImport(importText)
                            name = result.name.ifBlank { name }
                            description = result.description.ifBlank { description }
                            greeting = result.greeting.ifBlank { greeting }
                            userPersona = result.userPersona.ifBlank { userPersona }
                            if (result.tags.isNotEmpty()) tags = result.tags
                            showSmartImportDialog = false
                        }
                    }
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showSmartImportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

}

private data class ImportResult(
    val name: String = "",
    val description: String = "",
    val greeting: String = "",
    val userPersona: String = "",
    val tags: String = ""
)

private fun parseCharacterImport(text: String): ImportResult {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    var name = ""
    var description = ""
    var greeting = ""
    var userPersona = ""
    var tags = ""

    var currentSection = ""
    val descLines = mutableListOf<String>()
    val greetingLines = mutableListOf<String>()
    val personaLines = mutableListOf<String>()

    for (line in lines) {
        val lower = line.lowercase()
        when {
            lower.contains("名字") || lower.contains("名称") || lower.contains("角色名") || lower.contains("你叫") ->
                name = line.replace(Regex("""(名字[：:是]|名称[：:是]|角色名[：:是]|你叫|你是|我叫)"""), "").trim().removeSuffix("。")
            lower.startsWith("开场") || lower.startsWith("对话示例") || lower.startsWith("示例") -> {
                currentSection = "greeting"
                val content = line.substringAfter("：").substringAfter(":").trim()
                if (content.isNotBlank()) greetingLines.add(content)
            }
            lower.startsWith("人设") || lower.startsWith("设定") || lower.startsWith("描述") || lower.startsWith("性格") || lower.startsWith("背景") -> {
                currentSection = "description"
                val content = line.substringAfter("：").substringAfter(":").trim()
                if (content.isNotBlank()) descLines.add(content)
            }
            lower.startsWith("用户") || lower.startsWith("我") || lower.startsWith("身份") || lower.contains("我的人设") || lower.contains("我的设定") -> {
                currentSection = "persona"
                val content = line.substringAfter("：").substringAfter(":").trim()
                if (content.isNotBlank()) personaLines.add(content)
            }
            lower.startsWith("标签") || lower.startsWith("tag") -> {
                tags = line.substringAfter("：").substringAfter(":").trim()
                    .split(",", "，", "、", " ")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(",")
            }
            else -> {
                when (currentSection) {
                    "greeting" -> greetingLines.add(line)
                    "description" -> descLines.add(line)
                    "persona" -> personaLines.add(line)
                    else -> descLines.add(line)  // 未识别到标题时，默认归入描述
                }
            }
        }
    }

    return ImportResult(
        name = name,
        description = descLines.joinToString("\n").ifBlank { text },
        greeting = greetingLines.joinToString("\n"),
        userPersona = personaLines.joinToString("\n"),
        tags = tags
    )
}
