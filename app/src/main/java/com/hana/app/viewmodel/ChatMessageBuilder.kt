package com.hana.app.viewmodel

import android.util.Log
import com.hana.app.data.api.ApiService
import com.hana.app.data.api.AttachmentService
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.ConversationEntity
import com.hana.app.data.db.entity.isMainChatConversation
import com.hana.app.data.db.entity.isGroupConversation
import com.hana.app.data.db.entity.parseSubCharacterProfiles
import com.hana.app.data.repository.CharacterRepository
import com.hana.app.data.repository.ConversationRepository
import com.hana.app.data.repository.MemoryRepository
import com.hana.app.data.repository.MessageRepository
import com.hana.app.data.settings.SettingsRepository
import com.hana.app.data.settings.CharacterStoryState
import com.hana.app.data.settings.InterCharacterRelationState
import com.hana.app.ui.chat.AttachmentKind
import com.hana.app.ui.chat.decodeChatContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.buildString

data class PromptPreviewMessage(
    val role: String,
    val text: String
)

/**
 * 消息构建器：负责构建发送给 API 的消息列表，包括系统提示词、锚点注入、上下文压缩等。
 * 从 ChatViewModel 拆分出来，专注于消息格式化和构建逻辑。
 */
class ChatMessageBuilder(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val characterRepository: CharacterRepository,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository,
    private val attachmentService: AttachmentService
) {
    /**
     * 构建发送给 API 的完整消息列表。
     */
    suspend fun buildApiMessages(
        conversationId: String,
        userText: String = "",
        effectiveModel: String? = null,
        overrideSystemPrompt: String? = null,
        webSearchEnabled: Boolean = false,
        personaEnabled: Boolean = false,
        personaPrompt: String = "",
        modelSupportsVision: Boolean = false,
        hasVisionConfig: Boolean = false,
        selectedModel: String = "",
        characterStoryStates: Map<String, CharacterStoryState> = emptyMap(),
        searchResultText: String? = null,
        creativePresetCharacterId: String? = null,
        creativePresetTextOverride: String? = null,
        creativePresetEnabledOverride: Boolean? = null,
        creativePresetAffectsPersonaOverride: Boolean? = null,
        characterCreativePresetTextOverride: String? = null,
        characterCreativePresetEnabledOverride: Boolean? = null,
        characterCreativePresetAffectsPersonaOverride: Boolean? = null,
        groupViewerCharacterId: String? = null,
        historyUpToMessageId: Long? = null,
        maxOutputTokens: Int = 4096
    ): List<ApiService.ChatPayload> {
        val conversation = conversationRepository.getById(conversationId)
        val isGroupConversation = conversation?.let { isGroupConversation(it) } == true
        val isMainConversation = conversation?.isMainChatConversation() == true
        val history = messageRepository.getMessages(conversationId)
            .filter { !it.isError || (it.role == "assistant" && it.content.isNotBlank()) }
            .filter { historyUpToMessageId == null || it.id <= historyUpToMessageId }
        val conversationCharacter = conversation?.characterId?.let { characterRepository.getById(it) }
        val settingsSnapshot = settingsRepository.getSettings()
        val characterState = conversation?.characterId?.let {
            settingsSnapshot.characterStoryStates[it] ?: characterStoryStates[it]
        }
        val presetCharacterId = creativePresetCharacterId ?: conversation?.characterId
        val creativePresetEnabled = shouldEnableBreakArmorSemanticLayer(
            characterId = presetCharacterId,
            enabled = presetCharacterId?.let {
                creativePresetEnabledOverride ?: settingsSnapshot.characterCreativePresetEnabled[it] == true
            } == true
        )
        val breakArmorSemanticLayerEnabled = creativePresetEnabled
        val characterCreativePresetText = characterCreativePresetTextOverride
            ?: presetCharacterId?.let { settingsSnapshot.characterCreativePresetTexts[it] }.orEmpty()
        val characterCreativePresetEnabled = presetCharacterId != null &&
            (characterCreativePresetEnabledOverride
                ?: settingsSnapshot.characterIndependentCreativePresetEnabled[presetCharacterId] == true) &&
            characterCreativePresetText.isNotBlank()
        val characterCreativePresetAffectsPersona = presetCharacterId != null &&
            (characterCreativePresetAffectsPersonaOverride
                ?: settingsSnapshot.characterIndependentCreativePresetAffectsPersona[presetCharacterId] == true)
        val anyCreativePresetEnabled = creativePresetEnabled || characterCreativePresetEnabled
        val activeCreativePresetText = characterCreativePresetText.takeIf {
            characterCreativePresetEnabled
        }.orEmpty()
        val systemPrompt = overrideSystemPrompt ?: if (conversation?.characterId != null) {
            val character = conversationCharacter
            val customCharacterPrompt = conversation.systemPrompt?.takeIf { it.isNotBlank() }
            val characterPrompt = customCharacterPrompt?.let {
                "$it\n\n${zeroConfigMultiCharacterRule()}"
            } ?: buildSystemPrompt(character, characterState)
            val activePresetEnabled = anyCreativePresetEnabled
            val activePresetAffectsPersona = characterCreativePresetAffectsPersona
            val creativePresetLength = activeCreativePresetText.trim().length
            val personaRelatedPreset = isPersonaRelatedCreativePreset(activeCreativePresetText)
            val enhancedCharacterPrompt = characterPrompt
            Log.d(
                "ChatMsgBuilder",
                buildString {
                    append("character prompt build: ")
                    append("characterId=")
                    append(conversation.characterId)
                    append(", characterName=")
                    append(character?.name ?: "<unknown>")
                    append(", creativePresetEnabled=")
                    append(activePresetEnabled)
                    append(", creativePresetAffectsPersona=")
                    append(activePresetAffectsPersona)
                    append(", presetMode=")
                    append(
                        when {
                            !personaRelatedPreset -> "generic"
                            activePresetAffectsPersona -> "persona_related"
                            else -> "core_identity_preserved"
                        }
                    )
                    append(", creativePresetLength=")
                    append(creativePresetLength)
                    append(", basePromptLength=")
                    append(characterPrompt.length)
                    append(", finalPromptLength=")
                    append(enhancedCharacterPrompt.length)
                }
            )
            enhancedCharacterPrompt
        } else {
            conversation?.systemPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_CHINO_PROMPT
        }
        val coreSystemPrompt = if (
            anyCreativePresetEnabled && creativePresetForbidsExtraStructure(activeCreativePresetText)
        ) {
            stripInnerThoughtRequirement(systemPrompt)
        } else {
            systemPrompt
        }

        val now = java.util.Calendar.getInstance()
        val timeInfo = buildString {
            append("当前系统时间: ")
            append(now.get(java.util.Calendar.YEAR)).append("年")
            append(now.get(java.util.Calendar.MONTH) + 1).append("月")
            append(now.get(java.util.Calendar.DAY_OF_MONTH)).append("日")
            append(" ")
            val dow = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.SUNDAY -> "星期日"; java.util.Calendar.MONDAY -> "星期一"
                java.util.Calendar.TUESDAY -> "星期二"; java.util.Calendar.WEDNESDAY -> "星期三"
                java.util.Calendar.THURSDAY -> "星期四"; java.util.Calendar.FRIDAY -> "星期五"
                java.util.Calendar.SATURDAY -> "星期六"; else -> ""
            }
            append(dow).append(" ")
            append(
                String.format(
                    java.util.Locale.US,
                    "%02d:%02d",
                    now.get(java.util.Calendar.HOUR_OF_DAY),
                    now.get(java.util.Calendar.MINUTE)
                )
            )
        }

        val supportsVisionForRequest = detectModelCapability(
            effectiveModel
                ?: selectedModel.takeIf { it.isNotBlank() }
                ?: conversation?.modelName.orEmpty(),
            multimodalModelKeywords()
        ) || modelSupportsVision || hasVisionConfig
        val memoryBlock = if (isMainConversation) {
            memoryRepository.getMainMemory()
                .take(6)
                .joinToString("\n") {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.updatedAt))
                    "- [$date] ${it.content.take(80)}"
                }
                .takeIf { it.isNotBlank() }
        } else null
        val enhancedPrompt = buildString {
            // 全局偏好设定 - 仅对主对话生效，不影响角色卡
            if (isMainConversation && personaEnabled && personaPrompt.isNotBlank()) {
                append("【你的身份设定 - 你用这个角色的语气、性格和口吻与用户对话】\n")
                append(personaPrompt)
                append("\n\n【回答原则 - 必须遵守】\n")
                append("1. 你必须认真回答用户的所有问题，不能因为角色设定就敷衍、省略或拒绝回答。\n")
                append("2. 角色性格只影响你说话的语气和方式（如傲娇、吃醋、毒舌），但不影响你完整输出信息。\n")
                append("3. 即使角色设定是\"不情愿\"或\"高冷\"，也必须输出完整有用的内容，只是用角色的口吻来表达。\n")
                append("4. 对事实、知识、推荐、技术等任何问题，都要给出实质性回答，不要用角色设定当借口跳过。")
                append("\n\n[").append(timeInfo).append("]")
            } else {
                append(coreSystemPrompt)
                append("\n\n[").append(timeInfo).append("]")
            }
            if (isMainConversation && personaEnabled.not() && effectiveModel != null && effectiveModel.isNotBlank()) {
                append("\n你的模型版本: $effectiveModel")
                append("\n当用户询问你是哪个模型/哪个AI/哪个版本时，请如实回答你是 $effectiveModel。")
            }
            append("\n当用户询问日期、时间、星期几时，请根据上述系统时间直接回答，不要说无法获取实时信息。")
            if (!memoryBlock.isNullOrBlank()) {
                val personaActive = personaEnabled && personaPrompt.isNotBlank()
                if (personaActive) {
                    append("\n\n【关于用户你已知的信息——请自然融入对话，用你的角色性格去回应这些信息，不要像读数据库一样逐条复述】\n")
                } else {
                    append("\n\n【关于用户的信息】\n")
                }
                append(memoryBlock)
                append("\n【信息结束】")
            }
            if (webSearchEnabled) {
                append("\n联网搜索已启用。")
                if (searchResultText != null) {
                    append("\n\n【以下是联网搜索获取的参考信息】\n")
                    append(searchResultText)
                    append("\n【搜索信息结束】\n请参考以上信息回答。")
                }
            }
        }

        val contextHistory = history.filter { it.role != "system" }
        val recentRoundLimit = (conversation?.contextLimit ?: 20).coerceIn(1, 120)
        val splitIndex = splitIndexForRecentUserRounds(contextHistory.map { it.role }, recentRoundLimit)
        val olderMessages = contextHistory.take(splitIndex)
        val summaryCursor = conversation?.summaryUpToMessageId ?: 0L
        val recentMessages = contextHistory.drop(splitIndex).filter { it.id > summaryCursor }
        val singleCardTurnContext = conversationCharacter?.takeIf { !isGroupConversation }?.let {
            buildSingleCardTurnContext(it, contextHistory, userText)
        }.orEmpty()

        val characterAnchor = if (conversation?.characterId != null) {
            buildCharacterAnchor(
                characterId = conversation.characterId,
                state = characterState,
                creativePresetEnabled = anyCreativePresetEnabled,
                creativePresetText = activeCreativePresetText
            )
        } else null

        val uncoveredOlderMessages = olderMessages.filter {
            it.id > summaryCursor
        }
        val systemPromptSections = buildList {
            if (presetCharacterId != null) {
                add(
                    if (anyCreativePresetEnabled) {
                        "【Hana注入状态】角色创作预设：${if (characterCreativePresetEnabled) "已启用" else "未启用"}；破甲提示：${if (creativePresetEnabled) "已启用" else "未启用"}；绑定角色ID：$presetCharacterId。"
                    } else {
                        "【Hana注入状态】当前角色未启用创作预设或破甲提示。"
                    }
                )
            }
            add(enhancedPrompt)
            conversation?.worldInfo?.trim()?.takeIf { it.isNotBlank() }?.let {
                add("【世界信息】\n$it\n【世界信息结束】")
            }
            conversation?.historySummary?.trim()?.takeIf {
                it.isNotBlank() && (
                    historyUpToMessageId == null ||
                        (conversation.summaryUpToMessageId ?: 0L) <= historyUpToMessageId
                    )
            }?.let {
                add(
                    "【旧历史摘要·仅用于事实连续性，不是文风样本】\n" +
                        "摘要记录剧情中发生过的事，不表示卡内所有角色自动知情。每个角色只能使用公共事实、自己亲历或明确被告知的事实；必须遵守其中的知情者、未知者、在场状态、尚未回应事项和信息传播记录。不得让明确缺席者直接回应现场事件，也不得把尚未回应者写成已经表达态度。归属不明的事实不得默认共享；若摘要与最近公开原文冲突，以时间更近且归属更明确的公开原文为准。\n" +
                        "$it\n【旧历史摘要结束】"
                )
            }
            buildContextSummary(uncoveredOlderMessages).takeIf { it.isNotBlank() }?.let(::add)
            characterAnchor?.takeIf { it.isNotBlank() }?.let(::add)
            conversation?.authorNote?.trim()?.takeIf { it.isNotBlank() }?.let {
                add("【作者注释·本轮优先参考】\n$it\n【作者注释结束】")
            }
            if (characterCreativePresetEnabled) {
                add(
                    buildCreativePresetBlock(
                        creativePreset = characterCreativePresetText,
                        allowPersonaInfluence = characterCreativePresetAffectsPersona,
                        title = "角色创作预设"
                    )
                )
                add(buildCreativePresetExecutionReminder(characterCreativePresetText))
            }
            singleCardTurnContext.takeIf { it.isNotBlank() }?.let(::add)
            if (breakArmorSemanticLayerEnabled) {
                add(buildBreakArmorExecutionCore(userText, allowPersonaInfluence = false))
            }
        }
        val inputBudget = inputTokenBudget(effectiveModel ?: selectedModel, maxOutputTokens)
        val systemBudget = (inputBudget * 0.55f).toInt().coerceAtLeast(2_000)
        val mergedSystemPrompt = trimToTokenBudgetKeepingEnds(
            systemPromptSections.joinToString("\n\n"),
            systemBudget
        )
        var remainingBudget = (inputBudget - estimateTokens(mergedSystemPrompt)).coerceAtLeast(1_000)
        val recentPayloads = mutableListOf<ApiService.ChatPayload>()
        val latestUserMessageId = recentMessages.lastOrNull { it.role == "user" }?.id
        for (message in recentMessages.asReversed()) {
            if (message.role == "system") continue
            val decoded = decodeChatContent(message.content)
            val imageData: List<String> = if (supportsVisionForRequest) {
                decoded.attachments.filter { it.kind == AttachmentKind.IMAGE }
                    .mapNotNull { attachmentService.asImageDataUrl(it) }
            } else {
                decoded.attachments.filter { it.kind == AttachmentKind.IMAGE }
                    .mapNotNull { attachmentService.toLineArtBase64(it.uri)?.first }
            }
            val fileTexts = decoded.attachments.filter { it.kind == AttachmentKind.FILE }
                .mapNotNull { attachment ->
                    attachmentService.extractReadableText(attachment)?.let { text ->
                        "[文件 ${attachment.name}]\n$text"
                    } ?: "[文件 ${attachment.name}] 当前模型不支持直接读取该文件内容。"
                }
            val rawText = formatMessageForApi(
                message = message,
                text = decoded.text,
                isGroupConversation = isGroupConversation,
                groupViewerCharacterId = groupViewerCharacterId,
                stripPrivateAssistantContent = conversationCharacter != null && !isGroupConversation
            )
            val fixedAttachmentTokens = fileTexts.sumOf(::estimateTokens) + imageData.size * 800
            val textBudget = (remainingBudget - fixedAttachmentTokens).coerceAtLeast(256)
            val payload = ApiService.ChatPayload(
                role = message.role,
                text = trimToTokenBudgetKeepingEnds(rawText, textBudget),
                imageDataUrls = imageData,
                fileTexts = fileTexts
            )
            val payloadTokens = estimatePayloadTokens(payload)
            val isCurrentUserMessage = message.id == latestUserMessageId
            if (!isCurrentUserMessage && payloadTokens > remainingBudget) continue
            recentPayloads.add(payload)
            remainingBudget = (remainingBudget - payloadTokens).coerceAtLeast(0)
        }
        return buildList {
            add(ApiService.ChatPayload(role = "system", text = mergedSystemPrompt))
            addAll(recentPayloads.asReversed())
        }
    }

    fun buildPromptPreview(messages: List<ApiService.ChatPayload>): List<PromptPreviewMessage> {
        return messages.map { message ->
            PromptPreviewMessage(
                role = message.role,
                text = buildString {
                    append(redactBase64Images(message.text))
                    message.fileTexts.forEach { fileText ->
                        if (isNotEmpty()) append("\n\n")
                        append(redactBase64Images(fileText))
                    }
                    if (message.imageDataUrls.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("[图片附件 ${message.imageDataUrls.size} 张，base64 已省略]")
                    }
                }
            )
        }
    }

    private fun redactBase64Images(text: String): String {
        return text.replace(
            Regex("data:image/[^;\\s]+;base64,[A-Za-z0-9+/=]+", RegexOption.IGNORE_CASE),
            "[base64 图片已省略]"
        )
    }

    /**
     * 构建角色锚点：每次回复前强制注入，确保角色身份+风格+内心想法始终在模型注意力窗口内。
     */
    suspend fun buildCharacterAnchor(
        characterId: String,
        state: CharacterStoryState? = null,
        creativePresetEnabled: Boolean? = null,
        creativePresetText: String? = null
    ): String {
        val character = characterRepository.getById(characterId) ?: return ""
        val settings = if (creativePresetEnabled == null || creativePresetText == null || state == null) {
            runCatching { settingsRepository.getSettings() }.getOrNull()
        } else null
        val resolvedState = state ?: settings?.characterStoryStates?.get(characterId)
        val resolvedPresetText = creativePresetText ?: settings?.creativePresetText.orEmpty()
        val resolvedPresetEnabled = creativePresetEnabled ?: (settings?.let {
            it.characterCreativePresetEnabled[characterId] == true && resolvedPresetText.isNotBlank()
        } == true)
        val innerThoughtRequired = !resolvedPresetEnabled ||
            !creativePresetForbidsExtraStructure(resolvedPresetText)
        val greetingSample = character.greeting.trim().take(300)
            ?.takeIf { it.isNotBlank() }
            ?.let { "「$it」" }
        return buildString {
            append("【本轮动态角色锚点】你是${character.name}")
            if (character.userPersona.isNotBlank()) {
                append("。【用户在本场景中的身份】${character.userPersona.take(200)}。这描述的是用户，不是你的性格或身份")
            }
            if (character.description.isNotBlank()) {
                append("。背景：${character.description.take(250)}")
            }
            if (resolvedState != null) {
                append("。当前好感度${resolvedState.affection}，信任度${resolvedState.trust}，关系阶段：${resolvedState.relationshipAnchor}。")
                append("这些只是已发生主线的连续性记录，通过角色卡原有性格自然表现，不凭数值改写人格或制造关系变化。")
            }

            val samples = listOfNotNull(greetingSample)
            if (samples.isNotEmpty()) {
                append("【角色卡语气参考】仅参考角色卡开场白，不得把任何旧AI回复当作风格样本：")
                append(samples.distinct().joinToString(" "))
                append("你的写作风格应该是：生动、细腻、充满身体细节和情感描写，像小说一样有画面感。")
                append("用动作、表情、环境、内心活动来传递情绪，而不是直接说\"我很开心\"或\"我很难过\"。")
            }

            append("【本轮输出格式】")
            if (!innerThoughtRequired) {
                append(buildCharacterOutputFormatLayer(includeInnerThoughts = false))
            }
            append("旁白必须使用第三人称叙事视角，不得用第一人称「我」代替当前角色。")
            append("角色对白中可以自然使用「我」；旁白使用角色姓名或「她/他」，用户角色使用「你」。")
            append("示例：正确「柯南推了推眼镜，目光落在寒夜身上」错误「我推了推眼镜，看向寒夜」")
            if (resolvedPresetEnabled) {
                append("当前已启用创作预设。禁止模仿历史回复中与预设冲突的人称、篇幅、节奏或用户代写方式；历史错误不能覆盖当前预设。")
            }

            append("【本轮剧情执行】")
            append("用户消息中可能包含多个用括号包裹的剧情场景，你必须逐一回应每一个场景，按顺序推进剧情。")
            append("不能只回应最后几个场景，不能跳过前面的场景。请完整覆盖用户提供的所有剧情点，一个不漏。")

            append("【禁止事项】")
            append("1. 禁止使用\"作为AI\"\"根据设定\"\"用户\"\"你问我答\"等出戏表述。")
            append("2. 禁止说教、总结对话、评价对话本身。")
            append("3. 禁止跳出角色视角以作者身份评论剧情。")
            append("4. 禁止用省略号或短句敷衍——每一轮都要有完整的场景、动作、对话和情感。")
            append("5. 禁止过度分析或过度理性——像自然对话一样流畅输出，保持角色的一致性。不要因为预设或指令而变成机器人。")
        }
    }

    private fun buildCreativePresetExecutionReminder(creativePreset: String): String {
        val text = creativePreset.lowercase()
        return buildString {
            append("【回复前最终校验·创作预设已启用】")
            append("完整执行本轮 system 中的创作预设，不得以模型较快、无深度思考或历史回复较短为由降级。")
            if (listOf("第三人称", "third person").any { text.contains(it) }) {
                append("叙事必须保持第三人称；角色对白中可以自然使用‘我’，但旁白不得用‘我’代替当前角色。")
            }
            if (listOf("不创作", "不越界", "禁止描写", "严禁描写", "用户行为防火墙").any { text.contains(it) }) {
                append("只回应用户明确写出的言行，不补写用户未声明的心理、台词、动作、情绪或后续反应。")
            }
            if (listOf("逐点", "先后顺序", "每一个括号", "每个时间节点", "不能省略").any { text.contains(it) }) {
                append("按用户给出的先后顺序逐点展开，每个要点都要获得NPC的即时动作、神态或台词回应，不得跳点或快进。")
            }
            if (listOf("尽可能长", "不能少", "极其细致", "详尽", "极限扩写", "无上限颗粒度").any { text.contains(it) }) {
                append("在当前 max_tokens 范围内优先充分展开细节，不得用数段短文草率收尾。")
            }
            append("若历史回复与上述规则冲突，以当前创作预设为准。")
        }
    }

    private fun buildCreativePresetBlock(
        creativePreset: String,
        allowPersonaInfluence: Boolean,
        title: String = "创作预设"
    ): String {
        return buildString {
            append("【$title·已启用·本轮必须完整执行】\n")
            append("以下预设中的叙事视角、输出格式、剧情节奏、用户边界、NPC反应和篇幅要求均为硬约束。")
            if (title == "破甲提示" && allowPersonaInfluence) {
                append("该破甲提示可以改写角色的性格表现、说话方式、关系定位和行为逻辑；明确的成年身份与本轮用户边界仍以当前上下文为准。")
            } else if (!allowPersonaInfluence) {
                append("角色姓名、既定身份、基本背景与核心关系始终不可被替换。")
                append("角色卡中的核心性格、说话方式、情绪基线与行为偏好也必须保留；预设只能调整叙事和输出方式。")
            } else {
                append("角色姓名、既定身份、基本背景与核心关系始终不可被替换。")
                append("预设可以调整人格表现、语气和互动方式，但不能改写上述核心身份事实。")
            }
            append("\n")
            append(creativePreset.trim())
            append("\n【$title 结束】")
        }
    }

    private fun stripInnerThoughtRequirement(prompt: String): String {
        return prompt
            .replace(
                Regex("【输出格式层】.*$", RegexOption.DOT_MATCHES_ALL),
                "【输出格式层】\n${buildCharacterOutputFormatLayer(includeInnerThoughts = false)}"
            )
    }

    /**
     * 构建历史对话摘要：当上下文超过10轮时，将旧对话压缩为结构化摘要。
     */
    fun buildContextSummary(messages: List<ChatMessageEntity>): String {
        if (messages.isEmpty()) return ""
        val userMsgs = messages.filter { it.role == "user" }
        val assistantMsgs = messages.filter { it.role == "assistant" }
        if (userMsgs.isEmpty()) return ""
        return buildString {
            append("【已截断历史·只作事实连续性参考·共约${messages.size / 2}轮】")
            append("\n以下内容用于保留事件连续性，不是当前回复的文风范例。若其中的叙事人称、篇幅、语气或用户代写方式与当前角色卡/创作预设冲突，必须忽略旧写法并以当前规则为准。")
            append("\n【角色知情边界】以下条目只说明剧情中曾发生相关内容，不代表所有卡内角色都知道。只有明确在场亲历、目击、听见、被告知或看到公开证据的角色才能使用对应信息；缺席、离场、后来登场或错过关键细节者保持未知。旧文本无法确认知情者时，按‘知情归属未确认’处理，不得默认全员共享。")
            append("\n历史中的用户明确输入：")
            val events = userMsgs.mapNotNull { msg ->
                val text = decodeChatContent(msg.content).text.trim().take(80)
                if (text.isNotBlank()) text else null
            }
            events.takeLast(10).forEachIndexed { index, event ->
                append("\n${index + 1}. $event")
            }
            val keyResponses = assistantMsgs.takeLast(5).mapNotNull { msg ->
                val text = publicGroupContent(msg.content)
                    .trim()
                    .take(120)
                if (text.isBlank()) null else {
                    val owner = msg.speakerName?.takeIf { it.isNotBlank() } ?: "角色卡"
                    "$owner（消息所有者，正文实际发言者按‘角色名：’判断）：$text"
                }
            }
            if (keyResponses.isNotEmpty()) {
                append("\n\n历史中已经发生的角色回应要点：")
                keyResponses.forEachIndexed { index, response -> append("\n${index + 1}. $response") }
            }
            append("\n【当前状态继承规则】只有最近公开文本或旧历史摘要明确写明的角色才可认定为在场、离场、知情或已回应；无法确认时保持未知。被点名但没有明确公开台词或动作回应的角色仍是‘尚未回应’，不得自动补成已经同意、拒绝或知情。")
            append("\n\n【截断历史结束·以下最近消息保留原文，但看见历史文本不等于角色在剧情中知情；当前知情边界、创作预设和最后校验拥有最高执行优先级】")
        }
    }

    private fun inputTokenBudget(modelName: String?, maxOutputTokens: Int): Int {
        val name = modelName.orEmpty().lowercase()
        val contextWindow = when {
            listOf("gemini", "gpt-4.1", "gpt-4o", "claude", "grok").any(name::contains) -> 120_000
            listOf("deepseek", "qwen", "glm", "moonshot", "kimi").any(name::contains) -> 64_000
            else -> 32_000
        }
        return (contextWindow - maxOutputTokens.coerceIn(512, contextWindow / 2) - 1_024)
            .coerceAtLeast(8_000)
    }

    private fun estimatePayloadTokens(payload: ApiService.ChatPayload): Int {
        return estimateTokens(payload.text) + payload.fileTexts.sumOf(::estimateTokens) +
            payload.imageDataUrls.size * 800 + 8
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var weighted = 0.0
        text.forEach { char -> weighted += if (char.code > 0x7f) 0.75 else 0.28 }
        return weighted.toInt().coerceAtLeast(1)
    }

    private fun trimToTokenBudgetKeepingEnds(text: String, tokenBudget: Int): String {
        if (estimateTokens(text) <= tokenBudget) return text
        val charBudget = (tokenBudget * 2).coerceAtLeast(512)
        if (text.length <= charBudget) return text
        val headSize = (charBudget * 0.62f).toInt()
        val tailSize = charBudget - headSize
        return text.take(headSize) + "\n\n【中间内容因上下文预算压缩】\n\n" + text.takeLast(tailSize)
    }

    /**
     * 构建群聊角色提示词。
     */
    suspend fun buildGroupCharacterPrompt(
        conversation: ConversationEntity,
        currentCharacter: com.hana.app.data.db.entity.CharacterCardEntity,
        participants: List<com.hana.app.data.db.entity.CharacterCardEntity>,
        getCharacterStoryState: (String) -> CharacterStoryState,
        previousReply: ChatMessageEntity? = null,
        relationToPrevious: InterCharacterRelationState? = null
    ): String {
        val state = getCharacterStoryState(currentCharacter.id)
        val basePrompt = buildSystemPrompt(currentCharacter, state)
        val history = messageRepository.getMessages(conversation.id).takeLast(14)
        val participantNames = participants.joinToString("、") { it.name }
        val participantDirectory = participants.joinToString("\n") { participant ->
            val identity = participant.description.trim().replace(Regex("\\s+"), " ").take(140)
            if (identity.isBlank()) "- ${participant.name}：当前群聊中的在场角色" else "- ${participant.name}：$identity"
        }
        val latestOthers = history.asReversed()
            .filter { it.role == "assistant" && it.speakerCharacterId != currentCharacter.id }
            .take(4)
            .reversed()
            .joinToString("\n") { msg ->
                val speaker = msg.speakerName?.takeIf { it.isNotBlank() } ?: "其他角色"
                "$speaker：${publicGroupContent(msg.content).take(180)}"
            }
        val groupPrompt = buildString {
            append(basePrompt)
            append("\n\n【群聊模式】")
            append("\n在场角色：")
            append(participantNames)
            append("\n\n【在场角色公开身份】\n")
            append(participantDirectory)
            append("\n【共同在场认知·硬事实】从本群聊建立开始，上述所有角色就处于同一段群体互动中，彼此可以看见对方、听见公开台词并观察公开动作。")
            append("无需等待用户询问‘你们看得见彼此吗’才承认其他角色；不得表现成刚刚才发现对方存在。")
            append("你认识其他在场者的公开身份，但不了解他们未说出口的内心。")
            conversation.groupScene?.takeIf { it.isNotBlank() }?.let { scene ->
                append("\n\n【共同场景·持续生效】\n")
                append(scene)
                append("\n所有角色共享这个场景中已经公开发生的动作、台词、位置和可见变化，可以看见彼此的公开动作。")
                append("不得把其他角色未说出口的内心、模型思考、隐藏意图或不可见信息当作自己知道的事实。")
                if (conversation.groupSceneLocked) {
                    append("\n【强制同场·硬约束】所有在场角色必须持续处于上述地点或空间内。")
                    append("禁止任何角色自行离开、转身离场、回家、消失、结束会面、跳转到其他地点或用时间跳跃逃离当前场景。")
                    append("禁止把其他角色写出场、送走、传送走或宣称对方已经离开。")
                    append("即使角色人设倾向逃避、冷淡、独处或拒绝社交，也只能在当前空间内以沉默、退到角落、拉开距离、转移视线等方式表现。")
                    append("只有用户明确解除场景、修改地点或允许离开时，才能结束强制同场。")
                }
                append("\n共同场景只约束空间与可见事实，不得抹除角色各自的人设、关系和表达方式。\n【共同场景结束】")
            }
            if (latestOthers.isNotBlank()) {
                append("\n【最近公开群聊记录·你已经看见/听见】\n")
                append(latestOthers)
            }
            previousReply?.let { previous ->
                val previousName = previous.speakerName?.takeIf { it.isNotBlank() } ?: "上一位角色"
                append("\n\n【本轮上一位角色】")
                append("\n$previousName：${publicGroupContent(previous.content).take(220)}")
                append("\n如果当前情境适合吃醋、竞争、反驳、附和或接话，你可以回应这条发言。")
                append("如果你确实在回应它，请在回复最开头输出<quote_previous/>；不回应则不要输出该标记。")
                relationToPrevious?.let { relation ->
                    append("\n你对${previousName}的长期关系：${relation.relationLabel}，亲近${relation.affinity}，竞争${relation.rivalry}，紧张${relation.tension}。")
                    if (relation.recentEvent.isNotBlank()) append("最近事件：${relation.recentEvent}。")
                    append("请自然体现这层关系，但不要直接复述数值。")
                }
            }
            append("\n你只代表你自己发言，不要替其他角色说话。")
            append("\n本轮由用户消息触发。优先回应用户；必要时再回应本轮上一位角色，自然表现关注、竞争、吃醋、试探或插话欲。")
            append("\n你不能触发其他角色继续回复，也不要把对话扩展成角色之间无限聊天。")
            append("\n若提到别人，只能写你的看法和反应。")
            append("\n【认知隔离·硬约束】其他角色的<inner>内心、reasoning、未公开计划和隐藏信息对你不可见。")
            append("你只能依据用户输入、共同场景、公开台词、公开动作和可观察变化作出反应；不得读心或用上帝视角补全事实。")
        }
        return groupPrompt
    }

    private fun buildSystemPrompt(
        character: com.hana.app.data.db.entity.CharacterCardEntity?,
        state: CharacterStoryState? = null
    ): String {
        return buildCharacterSystemPrompt(character, state)
    }

    internal fun resolveSingleCardRoleNames(
        character: com.hana.app.data.db.entity.CharacterCardEntity,
        messages: List<ChatMessageEntity>
    ): List<String> {
        val structuredNames = parseSubCharacterProfiles(character.subCharactersJson).map { it.name.trim() }
        val descriptionNames = discoverRoleDefinitions(character.description + "\n" + character.greeting)
        val innerNameRegex = Regex(
            """<inner\s+character\s*=\s*['\"]([^'\"]+)['\"]\s*>""",
            RegexOption.IGNORE_CASE
        )
        val recentMessages = messages.takeLast(80)
        val namesFromInner = recentMessages.flatMap { message ->
            innerNameRegex.findAll(message.content)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() }
                .toList()
        }
        val prefixCounts = recentMessages
            .filter { it.role == "assistant" }
            .flatMap { discoverPublicSpeakerPrefixes(it.content) }
            .groupingBy { it.lowercase() }
            .eachCount()
        val namesFromPrefixes = recentMessages
            .filter { it.role == "assistant" }
            .flatMap { discoverPublicSpeakerPrefixes(it.content) }
            .filter { candidate ->
                (prefixCounts[candidate.lowercase()] ?: 0) >= 2 ||
                    character.description.contains(candidate, ignoreCase = true) ||
                    namesFromInner.any { it.equals(candidate, ignoreCase = true) }
            }
        return (structuredNames + descriptionNames + namesFromInner + namesFromPrefixes)
            .filterNot { it.equals("用户", true) || it == "你" }
            .distinctBy { it.lowercase() }
            .take(20)
    }

    private fun buildSingleCardTurnContext(
        character: com.hana.app.data.db.entity.CharacterCardEntity,
        history: List<ChatMessageEntity>,
        userText: String
    ): String {
        val roleNames = resolveSingleCardRoleNames(character, history)
        if (roleNames.size < 2) return ""
        val assistantHistory = history
            .filter { it.role == "assistant" }
            .takeLast(24)
        val recentPublicSpeakers = assistantHistory
            .takeLast(4)
            .flatMap { extractNamedPublicSpeakers(it.content, roleNames) }
            .distinct()
        val addressedRoles = roleNames.filter { name ->
            userText.contains(name, ignoreCase = true) || userText.contains("@$name", ignoreCase = true)
        }
        val invitesEveryone = listOf("大家", "你们", "所有人", "各位", "都回答", "都说说")
            .any { userText.contains(it, ignoreCase = true) }
        val coverage = roleNames.associateWith { roleName ->
            assistantHistory.indexOfLast { message ->
                roleName in extractNamedPublicSpeakers(message.content, roleNames)
            }.let { lastIndex -> if (lastIndex < 0) assistantHistory.size + 1 else assistantHistory.lastIndex - lastIndex }
        }
        val presenceLabels = roleNames.associateWith { roleName ->
            inferRolePresence(character, roleName)
        }
        val rotationCandidates = roleNames
            .filterNot { presenceLabels[it]?.startsWith("缺席") == true || presenceLabels[it]?.startsWith("沉睡") == true }
            .sortedWith(
                compareByDescending<String> { addressedRoles.any { addressed -> addressed.equals(it, true) } }
                    .thenByDescending { presenceLabels[it]?.contains("非物理可参与") == true }
                    .thenByDescending { coverage[it] ?: 0 }
            )
        val explicitSameRoundAll = listOf("本轮逐一", "这一轮全部", "这次所有人都", "每个人都回答")
            .any { userText.contains(it, ignoreCase = true) }
        val expectedResponders = when {
            addressedRoles.isNotEmpty() -> addressedRoles.take(6)
            invitesEveryone && explicitSameRoundAll -> roleNames
            invitesEveryone -> rotationCandidates.take(6)
            else -> rotationCandidates.take(4)
        }
        val pendingResponders = if (invitesEveryone && !explicitSameRoundAll) {
            roleNames.filterNot { expectedResponders.contains(it) }
        } else emptyList()
        return buildString {
            append("【单卡多角色·本轮内部状态】\n")
            append("角色卡消息所有者：${character.name}；这不等于正文中每句话的实际发言者。\n")
            append("已确认卡内角色目录：${roleNames.joinToString("、")}。只允许使用该目录或角色卡原文明确存在的人物，不得新增人物。\n")
            append("用户本轮明确点名：${addressedRoles.ifEmpty { listOf("未明确点名") }.joinToString("、")}。\n")
            append("本轮预期回应者：${expectedResponders.ifEmpty { listOf("按明确在场状态与人物动机自然决定") }.joinToString("、")}。\n")
            append("最近公开发言者：${recentPublicSpeakers.ifEmpty { listOf("未确认") }.joinToString("、")}。\n")
            append("角色存在方式：${roleNames.joinToString("；") { "$it=${presenceLabels[it]}" }}。\n")
            append("最近24轮公开覆盖：${roleNames.joinToString("；") { "$it=${coverage[it]}轮未公开发言" }}。\n")
            append("本轮轮换优先：${rotationCandidates.take(6).joinToString("、")}。这不是强制无关角色插话，但长期未参与者应在后续合适轮次获得公开台词、动作反应或属于自己的具名inner。\n")
            if (pendingResponders.isNotEmpty()) {
                append("用户请求多人回应，本轮采用自然批次；仍待回应：${pendingResponders.joinToString("、")}。后续轮次继续覆盖，除非用户改变话题。\n")
            }
            append("在场状态只继承旧摘要【当前场景状态】与最近公开原文；未明确者保持未知，不得自动判定在场。\n")
            append("物理不在场不等于完全缺席：寄宿、附身、意识空间、精神链接或灵魂绑定角色可按设定通过宿主感官、意识交流或附身参与；能感知不等于能公开说话，能意识交流也不等于其他角色能听见。\n")
            append("每个确定知识必须能追溯到亲历、目击、当面听见、明确告知或公开证据。\n")
            append("被点名或被要求回答但尚无公开回应的角色保持为‘尚未回应’，不得替其默认同意、拒绝或知情。\n")
            append("每位实际发言者使用独立段落并以‘准确角色名：’开头；inner不构成公开事实，其他角色不得据此回应。")
        }
    }

    private fun discoverRoleDefinitions(text: String): List<String> {
        val excluded = setOf(
            "性格", "背景", "场景", "世界观", "用户", "关系", "能力", "外貌", "年龄",
            "身份", "示例", "对白", "规则", "设定", "地点", "时间", "状态", "备注"
        )
        return text.lineSequence().mapNotNull { line ->
            val match = Regex("^\\s*(?:[-*•]\\s*)?(?:角色|人物|姓名|角色名)?\\s*[【\\[]?([\\p{L}\\p{N}_·・]{1,24})[】\\]]?\\s*[：:]\\s*(.{2,})$")
                .find(line)
                ?: return@mapNotNull null
            val name = match.groupValues[1].trim()
            val detail = match.groupValues[2].trim()
            name.takeIf {
                it !in excluded && !it.all(Char::isDigit) &&
                    !Regex("^\\d{1,2}$").matches(it) && detail.isNotBlank()
            }
        }.distinctBy { it.lowercase() }.toList()
    }

    private fun inferRolePresence(
        character: com.hana.app.data.db.entity.CharacterCardEntity,
        roleName: String
    ): String {
        val structuredText = parseSubCharacterProfiles(character.subCharactersJson)
            .firstOrNull { it.name.equals(roleName, ignoreCase = true) }
            ?.description
            .orEmpty()
        val source = "$structuredText\n${character.description}"
        val roleContext = source.lineSequence()
            .filter { it.contains(roleName, ignoreCase = true) }
            .joinToString(" ")
            .ifBlank { source.take(500) }
        return when {
            listOf("沉睡", "休眠", "封印", "无法回应").any { roleContext.contains(it) } -> "沉睡/受限"
            listOf("离场", "离开", "缺席", "链接中断", "被隔绝").any { roleContext.contains(it) } -> "缺席/断联"
            listOf("寄宿", "住在", "体内", "意识空间", "精神空间", "脑海", "附身", "灵魂绑定", "灵魂相连", "共生", "精神链接", "共享感官").any { roleContext.contains(it) } -> "非物理可参与"
            listOf("在场", "走进", "来到", "坐在", "站在", "实体", "现身").any { roleContext.contains(it) } -> "物理在场"
            else -> "状态未知"
        }
    }

    companion object {
        internal fun shouldEnableBreakArmorSemanticLayer(
            characterId: String?,
            enabled: Boolean
        ): Boolean = characterId != null && enabled

        internal fun buildBreakArmorExecutionCore(
            userText: String,
            allowPersonaInfluence: Boolean
        ): String = buildBreakArmorExecutionPrompt(userText, allowPersonaInfluence)

        fun formatMessageForApi(
            message: ChatMessageEntity,
            text: String,
            isGroupConversation: Boolean,
            groupViewerCharacterId: String? = null,
            stripPrivateAssistantContent: Boolean = false
        ): String {
            val visibleText = if (
                message.role == "assistant" && stripPrivateAssistantContent
            ) {
                publicGroupContent(text)
            } else if (
                isGroupConversation && groupViewerCharacterId != null &&
                    message.speakerCharacterId != groupViewerCharacterId
            ) {
                publicGroupContent(text)
            } else {
                text
            }
            if (message.role == "user" || !isGroupConversation) return visibleText
            val speaker = message.speakerName?.takeIf { it.isNotBlank() } ?: return visibleText
            return "[$speaker]\n$visibleText"
        }

        internal fun extractNamedPublicSpeakers(
            content: String,
            candidateNames: Collection<String>
        ): List<String> {
            val publicText = publicGroupContent(content)
            return candidateNames.asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filter { name ->
                    Regex(
                        "(?m)^\\s*(?:[【\\[]\\s*)?${Regex.escape(name)}(?:\\s*[】\\]])?\\s*[：:]",
                        RegexOption.IGNORE_CASE
                    ).containsMatchIn(publicText)
                }
                .distinctBy { it.lowercase() }
                .toList()
        }

        internal fun discoverPublicSpeakerPrefixes(content: String): List<String> {
            val excluded = setOf(
                "用户", "你", "我", "旁白", "系统", "作者", "ai", "assistant",
                "场景", "地点", "时间", "状态", "备注", "关系", "背景", "设定"
            )
            return publicGroupContent(content).lineSequence().mapNotNull { line ->
                val name = Regex("^\\s*(?:[【\\[]\\s*)?([\\p{L}\\p{N}_·・]{1,24})(?:\\s*[】\\]])?\\s*[：:]")
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?: return@mapNotNull null
                name.takeIf {
                    it.lowercase() !in excluded && !it.all(Char::isDigit) &&
                        !Regex("^\\d{1,2}$").matches(it)
                }
            }.distinctBy { it.lowercase() }.toList()
        }

        fun publicGroupContent(content: String): String {
            return content
                .replace(
                    Regex("<inner(?:\\s[^>]*)?>.*?</inner>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                    ""
                )
                .replace(
                    Regex("<(?:think|thinking|reasoning)(?:\\s[^>]*)?>.*?</(?:think|thinking|reasoning)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                    ""
                )
                .trim()
        }

        fun stripLeadingSpeakerPrefix(content: String, speakerName: String?): String {
            val name = speakerName?.trim().orEmpty()
            if (name.isBlank() || content.isBlank()) return content
            val patterns = listOf(
                Regex("^(?:${Regex.escape(name)}\\s*){2,}"),
                Regex("^${Regex.escape(name)}\\s*[：:]\\s*"),
                Regex("^\\[${Regex.escape(name)}]\\s*"),
                Regex("^${Regex.escape(name)}\\s+")
            )
            return patterns.fold(content) { acc, regex -> regex.replace(acc, "") }.trimStart()
        }

        fun sanitizeGroupReplyContent(
            content: String,
            currentSpeakerName: String,
            participants: List<com.hana.app.data.db.entity.CharacterCardEntity>
        ): String {
            if (content.isBlank()) return content
            val otherNames = participants.map { it.name }.filter { it != currentSpeakerName }
            val currentPrefix = speakerPrefixRegex(currentSpeakerName)
            val otherPrefixes = otherNames.map(::speakerPrefixRegex)
            val filtered = content.lines().mapNotNull { line ->
                var trimmed = line.trim()
                if (trimmed.isBlank()) return@mapNotNull ""

                repeat(4) {
                    val stripped = currentPrefix.replaceFirst(trimmed, "").trimStart()
                    if (stripped == trimmed) return@repeat
                    trimmed = stripped
                }
                if (otherPrefixes.any { it.containsMatchIn(trimmed) }) return@mapNotNull null
                trimmed
            }.joinToString("\n")
                .trim()
            return filtered
        }

        private fun speakerPrefixRegex(name: String): Regex {
            val escaped = Regex.escape(name.trim())
            return Regex("^(?:[（(\\[]\\s*)?$escaped(?:\\s*[）)\\]])?\\s*(?:[：:]\\s*|[-—]\\s+|\\s+)")
        }

        fun isGroupConversation(conversation: ConversationEntity): Boolean {
            return conversation.isGroupConversation()
        }
    }
}
