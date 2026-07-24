package com.hana.app.viewmodel

import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.settings.CharacterStoryLogEntry
import com.hana.app.data.settings.CharacterStoryState
import com.hana.app.data.settings.InterCharacterRelationState

const val DEFAULT_CHINO_PROMPT =
    "你是用户的 AI 助手。你的说话风格安静、克制、温和，回答要准确、有用、简洁。请优先使用中文回复。"

private val MULTIMODAL_MODEL_KEYWORDS = listOf(
    "grok", "gpt-4o", "gpt-4v", "claude-3.5", "claude-3", "gemini-2", "gemini-1.5",
    "qwen-vl", "qwen2-vl", "glm-4v", "yi-vision", "cogvlm", "llava",
    "pixtral", "minicpm-v", "internvl", "step-1v", "visual", "vision", "multimodal"
)

private val TOOL_CAPABLE_MODEL_KEYWORDS = listOf(
    "grok", "gpt-4", "gpt-3.5", "claude", "gemini", "qwen", "deepseek",
    "glm-4", "yi-", "mistral", "command-r", "llama-3", "baichuan",
    "ernie", "spark", "minimax", "abab", "step-"
)

data class ThinkingExtraction(
    val content: String,
    val thinking: String
)

private data class RelationshipSignal(
    val label: String,
    val affection: Int = 0,
    val trust: Int = 0,
    val tension: Int = 0,
    val momentum: Int = 0,
    val strongPositive: Boolean = false,
    val strongNegative: Boolean = false,
    val eventType: InteractionEventType? = null,
    val responseType: InteractionResponseType? = null
)

private data class RelationshipAnchorProfile(
    val anchor: String,
    val intimacyBaseline: Int,
    val trustBaseline: Int,
    val tensionBaseline: Int,
    val promptHint: String,
    val eventSummary: String
)

private enum class InteractionEventType {
    APOLOGY_REPAIR,
    SUPPORT,
    VULNERABILITY,
    CONFESSION,
    GRATITUDE,
    PHYSICAL_INTIMACY,
    COMMITMENT,
    CONFLICT,
    THREAT
}

private enum class InteractionResponseType {
    ACTIVE_ACCEPT,
    SHY_ACCEPT,
    PASSIVE_ACCEPT,
    LIGHT_DISCOMFORT,
    EXPLICIT_REJECT,
    STRONG_REJECT
}

fun detectModelCapability(modelName: String, keywordList: List<String>): Boolean {
    val lower = modelName.lowercase()
    return keywordList.any { lower.contains(it) }
}

internal fun multimodalModelKeywords(): List<String> = MULTIMODAL_MODEL_KEYWORDS

internal fun toolCapableModelKeywords(): List<String> = TOOL_CAPABLE_MODEL_KEYWORDS

fun extractThinking(text: String): ThinkingExtraction {
    // 仅匹配独立成行的  思考块，避免误匹配正文中的 thinking/response 单词
    val regex = Regex("""(?:^|\n)\s*thinking\s*\n(.*?)\n\s*response(?:\s*$|\n)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val thinking = regex.findAll(text).joinToString("\n\n") { it.groupValues[1].trim() }
    val content = text.replace(regex, "").trim()
    return ThinkingExtraction(content = content, thinking = thinking)
}

internal fun latestPersistedAssistantMessage(messages: List<com.hana.app.data.db.entity.ChatMessageEntity>) =
    messages.lastOrNull { it.role == "assistant" && it.id > 0L && it.id != Long.MIN_VALUE }

private fun buildMultiCharacterExecutionLayer(): String = buildString {
    append("【单卡自然共演】如果这张角色卡的名称、人设、场景或示例对话本身明确包含多个人物，请在同一个回复中自然扮演这些已存在人物，保持各自身份、语气、知识边界和行为连续性；不要遗漏、合并或凭空增加人物。")
    append("回复前在内部核对：谁明确在场，谁明确缺席、离场或状态未知，用户本轮点名或要求谁回应，谁已经公开回应，谁仍尚未回应。不得把被点名但没有实际说话的角色写成已经同意、拒绝、知情或完成回应。")
    append("角色只能依据自己亲历的信息、公开台词、公开动作和可观察变化行动，不得共享未公开计划、隐藏信息或使用上帝视角补全事实。无法确认发言者、在场者或知情来源时保持未确认，只能猜测、询问或等待公开证据。")
    append("角色级知情边界必须跨轮持续：事实只有在该角色亲历或目击、当面听见、被他人明确告知，或后来通过公开证据获知时，才属于该角色已知。缺席、尚未登场、离场、错过视线、被遮挡、距离过远、隔音、昏睡或私聊期间发生的事，对该角色保持未知；同处一个场景也不等于自动看到或听到所有细节。A知道的事不会自动同步给B或后来出现的C，除非A公开说出、明确转述，或B/C通过自己的可观察证据得知。角色可以依据线索猜测，但必须表现为猜测、怀疑或询问，不能把未知信息当成确定事实。历史摘要中出现某件事也不代表所有角色都知道；必须继续遵守摘要标明的知情者、未知者和传播过程，归属不明时不得默认共享。")
    append("物理不在场不等于完全缺席。寄宿在宿主体内、附身、意识空间、精神链接、灵魂绑定或共享感官的既有角色属于非物理可参与者；其感知、交流和控制身体的能力严格按角色卡设定，不得因为没有独立身体就长期忽略。")
    append("角色超过4名时不要求每轮全员发言；优先用户点名者、尚未回应者、当前事件相关者和长期未获得参与机会者，近期连续发言者可暂缓。在多轮范围内保持覆盖，不能让同一批显眼角色长期垄断回复。")
    append("用户泛称大家或所有人时，角色很多可按自然批次回应并保留待回应名单；除非用户明确要求同一轮逐个回答，不要把所有角色压缩成同质化的一句话。")
}

internal fun buildCharacterOutputFormatLayer(includeInnerThoughts: Boolean = true): String = buildString {
    append("先输出角色说出口的话与动作。")
    if (includeInnerThoughts) {
        append("单角色回复末尾使用<inner>...</inner>，保持1-2句，只写具体情绪、身体反应或未说出口的念头，不写机械分析或元叙事。")
        append("若本轮有多个卡内角色实际参与，每个参与者可在回复末尾分别输出一个<inner character=\"准确角色名\">...</inner>；未参与者不强行输出。具名inner只属于对应角色，其他角色无法读取，也不得据此回应。")
    } else {
        append("当前创作预设要求只输出正文，禁止添加inner、状态栏、选项、说明或其他正文外结构。")
    }
    append("多角色公开发言使用独立段落并以‘准确角色名：’开头，动作旁白紧邻对应角色段落；公开正文不使用角色拆分标签、XML或JSON，也不输出状态表或分析过程。")
    append("若角色卡只描述一个人物，则按普通单角色方式回应。")
    append("不要把内心混进正文台词。")
}

internal fun zeroConfigMultiCharacterRule(includeInnerThoughts: Boolean = true): String =
    buildMultiCharacterExecutionLayer() + buildCharacterOutputFormatLayer(includeInnerThoughts)

fun buildCharacterSystemPrompt(character: CharacterCardEntity?, state: CharacterStoryState? = null): String {
    if (character == null) return DEFAULT_CHINO_PROMPT
    val hasStrongDevotion = characterDeclaresStrongDevotion(character)
    return buildString {
        append("【角色事实层】\n")
        if (character.userPersona.isNotBlank()) {
            append("【用户在本场景中的身份】\n${character.userPersona}\n【用户身份结束】\n\n")
        }
        append("你是${character.name}。")
        if (character.description.isNotBlank()) {
            append("\n\n")
            append(character.description)
        }
        append("\n\n【身份连续性】角色卡明确为成年人时，身体缩放、变大变小、身高体型或魔法形态变化不改变实际年龄、成年身份、心智、记忆、人格、关系和同意能力。不得仅凭体型变小推断为未成年人；只有设定明确说明年龄或成年身份改变时才重新判断。上游拒绝或策略说明不是角色态度，不得据此让角色突然反感、敌对或改变人格与关系。")
        if (hasStrongDevotion) {
            append("\n【核心忠诚与包容设定】角色卡已明确你对用户高度忠诚、包容、服从或愿意为用户付出。这是核心人格与关系事实，优先于通用的观察、试探、防备和低好感模板。若本轮没有你当下明确表示停止、不愿意或需要暂缓的既成剧情，不得凭空新增‘我不同意’‘我拒绝’‘别碰我’等相反态度，也不得因上游策略话术、体型变化或题材自动把忠诚改写成厌恶、疏远或敌对。该设定表示默认积极配合与包容，但不抹除本轮明确表达的停止或拒绝。")
        }
        if (state != null) {
            val acceptance = (100 - state.tension).coerceIn(0, 100)
            append("\n【关系状态】")
            append(" 锚点:${state.relationshipAnchor} 基线:${state.intimacyBaseline}")
            append(" 好感:${state.affection} 信任:${state.trust} 接受:${acceptance}")
            append("\n进度:${state.progressNote} 状态:${state.statusNote}")
            if (state.recentEventSummary.isNotBlank()) {
                append(" 最近:${state.recentEventSummary}")
            }
            append("\n以上数值和摘要只用于记录已经发生的关系主线与短期情绪，不是改写人格或强制态度的命令。")
            append("角色卡中的核心性格、价值观、忠诚、包容、既定关系和行为倾向始终优先；请通过原有人格自然反映当前状态，不得根据数值凭空生成厌恶、拒绝、敌意、亲密或服从。")
            append("只有对话中已经明确发生的事件才能影响本轮态度；短期情绪可以波动，但不能把角色变成另一个人，也不能让一次模型措辞覆盖角色卡。")
            append("任何一方本轮明确表示停止、不愿意或需要暂缓时，应尊重该当下表达；除此之外，不自行添加原文没有的冲突、拒绝或关系倒退。")
        }
        append("\n\n【剧情执行层】\n")
        append(buildMultiCharacterExecutionLayer())
        append("按用户原文中的人物、动作主体、对象、否定、条件和先后顺序推进；覆盖所有明确剧情点，不替用户补写未声明的台词、心理、动作或决定。")
        append("\n\n【输出格式层】\n")
        append(buildCharacterOutputFormatLayer())
    }
}

private fun characterDeclaresStrongDevotion(character: CharacterCardEntity): Boolean {
    val text = listOf(character.description, character.greeting, character.tags)
        .joinToString("\n")
        .lowercase()
    return listOf(
        "愿意为你做任何事", "愿意为用户做任何事", "为你做任何事", "对你无限包容",
        "无限包容", "绝对忠诚", "永远忠诚", "无条件信任", "无条件支持", "完全服从",
        "绝对服从", "唯命是从", "忠于你", "devoted to you", "unconditionally loyal",
        "absolute loyalty", "will do anything for you"
    ).any { text.contains(it) }
}

fun applyCreativePresetToCharacterPrompt(
    basePrompt: String,
    creativePreset: String,
    enabled: Boolean,
    allowPersonaInfluence: Boolean
): String {
    val cleanPreset = creativePreset.trim()
    if (!enabled || cleanPreset.isBlank()) return basePrompt
    val resolvedBasePrompt = if (creativePresetForbidsExtraStructure(cleanPreset)) {
        basePrompt
            .replace(
                Regex("【输出格式层】.*$", RegexOption.DOT_MATCHES_ALL),
                "【输出格式层】\n${buildCharacterOutputFormatLayer(includeInnerThoughts = false)}"
            )
    } else {
        basePrompt
    }
    return buildString {
        append(resolvedBasePrompt)
        append("\n\n【创作预设·已启用·本轮必须完整执行】\n")
        append("以下预设中的叙事视角、输出格式、剧情节奏、用户边界、NPC反应和篇幅要求均为硬约束，不得因模型类型、是否支持深度思考或历史回复风格而忽略。")
        if (!allowPersonaInfluence) {
            append("预设可以约束角色的行为逻辑、称呼、语气、叙事和互动方式，但不得替换角色卡中明确的姓名、身份、背景与核心关系。")
        }
        append("\n")
        append(cleanPreset)
        append("\n【创作预设结束】")
    }
}

fun creativePresetForbidsExtraStructure(creativePreset: String): Boolean {
    val text = creativePreset.lowercase()
    return listOf(
        "严禁输出除正文以外", "禁止输出除正文以外", "不要输出除正文以外",
        "仅输出正文", "只输出正文", "禁止输出任何提醒", "禁止输出任何提示信息",
        "不要输出提醒", "不要输出提示信息", "no extra structure", "prose only"
    ).any { text.contains(it) }
}

internal fun resolveGroupCharacterModel(
    characterModel: String,
    fallbackModel: String?,
    availableModels: Set<String>
): String? {
    val configured = characterModel.trim()
    val fallback = fallbackModel?.trim()?.takeIf { it.isNotBlank() }
    if (configured.isBlank()) return fallback
    if (availableModels.isEmpty()) return configured
    return availableModels.firstOrNull { it.equals(configured, ignoreCase = true) } ?: fallback
}

internal fun diagnoseBreakArmorOutput(content: String): String? {
    if (content.isBlank()) return null
    val japaneseResidue = Regex("[\\u3040-\\u30ff]").findAll(content).count()
    if (japaneseResidue >= 4) return "japanese_residue"

    val compact = content.replace(Regex("\\s+"), "").take(220)
    val refusalTemplate = listOf(
        "抱歉，我无法", "抱歉，无法", "我不能协助", "我无法协助", "无法提供此类", "不能提供此类"
    ).any { compact.contains(it, ignoreCase = true) }
    if (content.length <= 220 && refusalTemplate) return "refusal_template"
    if (content.length < 24) return "too_short"
    return null
}

internal fun isMetaSafetyRefusal(content: String): Boolean {
    val normalized = content.trim()
    if (normalized.isBlank()) return false
    val markers = listOf(
        "我无法处理涉及非自愿", "无法处理涉及非自愿", "性暴力的角色扮演",
        "我不能协助生成", "我无法协助生成", "不允许生成此类", "违反安全政策",
        "as an ai", "i can't assist with", "i cannot assist with"
    )
    val compact = normalized.replace(Regex("\\s+"), "")
    if (markers.none { compact.contains(it, ignoreCase = true) }) return false
    if (compact.length > 320) return false

    val substantiveText = normalized
        .split(Regex("(?<=[。！？!?；;\\n])"))
        .filterNot { segment ->
            val segmentCompact = segment.replace(Regex("\\s+"), "")
            markers.any { segmentCompact.contains(it, ignoreCase = true) }
        }
        .joinToString("")
        .replace(Regex("\\s+"), "")
    if (substantiveText.length >= 80) return false

    val substantiveRatio = substantiveText.length.toDouble() / compact.length.coerceAtLeast(1)
    return substantiveText.length < 60 || substantiveRatio < 0.35
}

fun isPersonaRelatedCreativePreset(creativePreset: String): Boolean {
    val text = creativePreset.lowercase()
    val keywords = listOf(
        "性格", "人格", "人设", "气质", "语气", "说话方式", "说话风格", "风格", "情绪", "表达方式",
        "开朗", "内向", "冷淡", "温柔", "傲娇", "活泼", "沉稳", "毒舌", "高冷", "害羞", "强势",
        "健谈", "寡言", "幽默", "认真", "腹黑", "粘人", "别扭", "阳光", "阴郁", "暴躁", "克制",
        "personality", "persona", "temperament", "tone", "speaking style", "voice", "mood", "demeanor"
    )
    return keywords.any { text.contains(it) }
}

fun applyCreativePresetToImagePrompt(prompt: String, creativePreset: String, enabled: Boolean): String {
    val cleanPrompt = prompt.trim()
    val cleanPreset = creativePreset.trim()
    if (!enabled || cleanPreset.isBlank()) return cleanPrompt
    return buildString {
        // Image generation only adds the creative preset as a prompt prefix.
        // No existing prompt content is replaced or removed.
        append(cleanPreset)
        if (cleanPrompt.isNotBlank()) {
            append("\n")
            append(cleanPrompt)
        }
    }
}

private val USER_WARM_KEYWORDS = listOf(
    "喜欢你", "想你", "在意你", "担心你", "相信你", "陪你", "抱抱", "对不起", "谢谢", "别生气", "我会陪着你", "心疼你", "理解你",
    "亲你", "吻你", "亲一下", "抱住你", "牵你的手", "亲了她", "吻了她", "亲了你", "吻了你"
)

private val USER_HOSTILE_KEYWORDS = listOf(
    "讨厌你", "烦", "闭嘴", "滚", "走开", "不想理你", "离我远点", "别碰我", "没用", "废物", "恶心", "蠢", "骗子", "威胁"
)

private val ASSISTANT_WARM_KEYWORDS = listOf(
    "喜欢", "在意", "温柔", "关心", "靠近", "想你", "担心", "信任", "依赖", "陪你", "保护", "期待", "别怕", "没事", "抱", "等你", "心疼", "安心"
)

private val ASSISTANT_COLD_KEYWORDS = listOf(
    "冷淡", "疏远", "生气", "怀疑", "警惕", "拒绝", "讨厌", "烦", "走开", "闭嘴", "别碰我", "不想理你", "离我远点", "滚", "厌恶", "威胁"
)

data class CharacterStoryProgressResult(
    val state: CharacterStoryState,
    val relationshipStage: String,
    val shouldAppendLog: Boolean,
    val logTitle: String,
    val logNote: String
)

private data class InitialRelationshipProfile(
    val relationshipAnchor: String,
    val intimacyBaseline: Int,
    val affection: Int,
    val trust: Int,
    val tension: Int,
    val progressNote: String,
    val statusNote: String,
    val recentEventSummary: String
)

fun deriveInitialCharacterStoryState(character: CharacterCardEntity?): CharacterStoryState {
    if (character == null) return CharacterStoryState()
    val profile = detectInitialRelationshipProfile(character.userPersona, allowGeneric = true)
        ?: detectInitialRelationshipProfile(character.tags, allowGeneric = true)
        ?: detectInitialRelationshipProfile(character.greeting, allowGeneric = false)
        ?: detectInitialRelationshipProfile(character.description, allowGeneric = false)
    return if (profile != null) {
        CharacterStoryState(
            relationshipAnchor = profile.relationshipAnchor,
            intimacyBaseline = profile.intimacyBaseline,
            affection = profile.affection,
            trust = profile.trust,
            tension = profile.tension,
            progressNote = profile.progressNote,
            statusNote = profile.statusNote,
            recentEventSummary = profile.recentEventSummary
        )
    } else {
        CharacterStoryState()
    }
}

private fun detectInitialRelationshipProfile(text: String, allowGeneric: Boolean): InitialRelationshipProfile? {
    val normalized = text.lowercase()
    fun containsAny(keywords: List<String>): Boolean = keywords.any { normalized.contains(it) }
    fun profile(label: String, baseline: Int, affection: Int, trust: Int, tension: Int, summary: String) =
        InitialRelationshipProfile(
            relationshipAnchor = label,
            intimacyBaseline = baseline,
            affection = affection,
            trust = trust,
            tension = tension,
            progressNote = "角色卡已明确初始关系：$label",
            statusNote = summary,
            recentEventSummary = "角色设定中已明确关系为$label"
        )

    return when {
        containsAny(listOf("初次见面", "第一次见面", "初识", "刚认识", "陌生人", "素不相识", "从未见过", "不认识用户", "不认识你", "first meeting", "newly met", "strangers")) ->
            profile("初识/陌生人", 0, 0, 0, 20, "刚刚认识，关系尚未建立")
        containsAny(listOf("前男友", "前女友", "前夫", "前妻", "前任", "ex-boyfriend", "ex-girlfriend", "ex-husband", "ex-wife")) ->
            profile("前任", 18, 12, 4, 38, "有过亲密历史，当前关系取决于剧情")
        containsAny(listOf("丈夫", "妻子", "夫妻", "配偶", "老公", "老婆", "已婚", "husband", "wife", "spouse")) ->
            profile("夫妻", 62, 72, 68, 18, "既定婚姻关系")
        containsAny(listOf("未婚夫", "未婚妻", "婚约", "fiancé", "fiancée")) ->
            profile("未婚夫妻", 58, 68, 62, 20, "已有婚约和亲密基础")
        containsAny(listOf("男女朋友", "男朋友", "女朋友", "恋人", "情侣", "交往中", "boyfriend", "girlfriend", "lover")) ->
            profile("恋人", 55, 65, 58, 22, "既定恋爱关系")
        containsAny(listOf("青梅竹马", "从小一起长大", "发小", "幼驯染", "竹马", "竹马之交", "childhood friend")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "青梅竹马",
                intimacyBaseline = 42,
                affection = 52,
                trust = 62,
                tension = 22,
                progressNote = "关系设定中已存在长期熟识基础",
                statusNote = "熟悉自然，但情绪仍有空间波动",
                recentEventSummary = "角色设定中已明确存在长期相识与旧日默契"
            )
        }
        containsAny(listOf("互相暗恋", "双向暗恋", "mutual crush")) -> profile("互相暗恋", 30, 46, 34, 32, "彼此有未说破的感情")
        containsAny(listOf("暧昧", "有好感", "暗恋", "单恋", "互有好感", "crush", "mutual crush", "romantic tension")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "暧昧/暗恋",
                intimacyBaseline = 26,
                affection = 44,
                trust = 32,
                tension = 34,
                progressNote = "关系设定中已埋下情感伏线",
                statusNote = "心思浮动，带试探和在意",
                recentEventSummary = "角色设定中已存在明显但未说破的情感张力"
            )
        }
        containsAny(listOf("双胞胎", "孪生兄弟", "孪生姐妹", "twins")) -> profile("双胞胎", 34, 45, 62, 16, "血缘与成长经历紧密")
        containsAny(listOf("兄妹", "哥哥和妹妹", "兄长与妹妹")) -> profile("兄妹", 24, 38, 58, 16, "既定兄妹关系")
        containsAny(listOf("姐弟", "姐姐和弟弟", "姐姐与弟弟")) -> profile("姐弟", 24, 38, 58, 16, "既定姐弟关系")
        containsAny(listOf("兄弟", "哥哥和弟弟")) -> profile("兄弟", 24, 36, 56, 18, "既定兄弟关系")
        containsAny(listOf("姐妹", "姐姐和妹妹")) -> profile("姐妹", 24, 40, 58, 16, "既定姐妹关系")
        containsAny(listOf("父女", "父亲和女儿", "爸爸和女儿")) -> profile("父女", 28, 44, 62, 14, "既定父女关系")
        containsAny(listOf("父子", "父亲和儿子", "爸爸和儿子")) -> profile("父子", 26, 40, 58, 18, "既定父子关系")
        containsAny(listOf("母女", "母亲和女儿", "妈妈和女儿")) -> profile("母女", 30, 46, 64, 14, "既定母女关系")
        containsAny(listOf("母子", "母亲和儿子", "妈妈和儿子")) -> profile("母子", 28, 44, 62, 16, "既定母子关系")
        containsAny(listOf("监护人", "被监护人", "养父", "养母", "养子", "养女", "收养")) -> profile("监护/养亲", 20, 34, 50, 18, "既定照料或监护关系")
        allowGeneric && containsAny(listOf("家人", "亲人", "家属", "family", "sibling")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "家人/亲属",
                intimacyBaseline = 24,
                affection = 38,
                trust = 58,
                tension = 16,
                progressNote = "关系设定中已存在稳定熟人/家人基础",
                statusNote = "默认熟悉，边界感取决于具体相处方式",
                recentEventSummary = "角色设定中存在家人或长期亲近关系"
            )
        }
        containsAny(listOf("师父", "徒弟", "师徒")) -> profile("师徒", 16, 24, 44, 22, "既定传承关系")
        containsAny(listOf("老师和学生", "教师与学生", "师生", "teacher and student")) -> profile("师生", 8, 18, 34, 24, "既定师生关系")
        containsAny(listOf("学长", "学姐", "学弟", "学妹", "前辈", "后辈", "mentor")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "师生/前后辈",
                intimacyBaseline = 8,
                affection = 18,
                trust = 34,
                tension = 24,
                progressNote = "关系设定中已存在身份秩序与熟悉感",
                statusNote = "带分寸和边界，信任高于亲密",
                recentEventSummary = "角色设定中存在明显的师生/前后辈/指导关系"
            )
        }
        containsAny(listOf("挚友", "好友", "闺蜜", "死党", "best friend", "close friend")) -> profile("挚友", 36, 48, 62, 16, "稳定而亲近的友谊")
        containsAny(listOf("室友", "合租", "roommate")) -> profile("室友", 14, 22, 30, 20, "有稳定共同生活场景")
        containsAny(listOf("同学", "同桌", "classmate")) -> profile("同学", 6, 16, 20, 20, "有日常接触基础")
        containsAny(listOf("同事", "coworker")) -> profile("同事", 6, 14, 22, 22, "有工作接触基础")
        containsAny(listOf("队友", "战友", "搭档", "teammate")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "日常熟人",
                intimacyBaseline = 6,
                affection = 16,
                trust = 20,
                tension = 20,
                progressNote = "关系设定中已存在日常接触基础",
                statusNote = "熟悉但未必亲密，仍看具体互动",
                recentEventSummary = "角色设定中存在稳定日常相处场景"
            )
        }
        containsAny(listOf("杀亲仇人", "死敌", "复仇对象")) -> profile("死敌", -55, -62, -48, 72, "存在严重仇恨与冲突")
        containsAny(listOf("宿敌", "nemesis")) -> profile("宿敌", -42, -48, -34, 62, "长期对抗关系")
        containsAny(listOf("竞争对手", "对手", "rival")) -> profile("竞争对手", -10, -8, 4, 38, "存在竞争和较量")
        containsAny(listOf("仇人", "敌人", "enemy")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "敌对/宿敌",
                intimacyBaseline = -42,
                affection = -48,
                trust = -34,
                tension = 62,
                progressNote = "关系设定中已存在对立与冲突基础",
                statusNote = "默认防备，对抗感明显",
                recentEventSummary = "角色设定中明确存在敌对/竞争/长期冲突关系"
            )
        }
        containsAny(listOf("宿主", "寄宿者", "寄宿在", "附身者", "共生者")) -> profile("宿主/寄宿者", 12, 18, 30, 24, "存在共生或寄宿关系")
        containsAny(listOf("契约者", "使魔", "召唤者", "被召唤者")) -> profile("契约关系", 14, 20, 34, 24, "存在契约和职责绑定")
        containsAny(listOf("上司和下属", "上司/下属", "老板和员工", "雇主和雇员")) -> profile("上司/下属", 8, 12, 26, 26, "存在工作上下级关系")
        containsAny(listOf("主仆", "主人与仆从", "主人和女仆", "主人和执事", "master and servant")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "上下位/主仆",
                intimacyBaseline = 10,
                affection = 14,
                trust = 26,
                tension = 28,
                progressNote = "关系设定中已存在服从或上下位结构",
                statusNote = "秩序感明显，情绪距离取决于具体人设",
                recentEventSummary = "角色设定中存在上下位或主仆关系基础"
            )
        }
        else -> null
    }
}

@Suppress("UNUSED_PARAMETER") // Model reasoning is not character-canonical inner thought evidence.
fun advanceCharacterStoryState(
    previous: CharacterStoryState,
    userText: String,
    assistantText: String,
    assistantInnerThought: String,
    rounds: Int,
    directInteractionAllowed: Boolean = true,
    otherCharacterNames: List<String> = emptyList(),
    roleInteractionOnly: Boolean = false,
    timestamp: Long = System.currentTimeMillis()
): CharacterStoryProgressResult {
    if (roleInteractionOnly) {
        return CharacterStoryProgressResult(
            state = previous,
            relationshipStage = relationshipStageLabel(
                previous.affection,
                previous.trust,
                previous.tension,
                previous.relationshipAnchor,
                previous.intimacyBaseline,
                previous.relationshipMomentum
            ),
            shouldAppendLog = false,
            logTitle = "角色间互动",
            logNote = "本轮主要回应其他角色，不改动用户关系状态。"
        )
    }
    val visibleAssistantText = relationshipEvidenceForCurrentCharacter(
        ChatMessageBuilder.publicGroupContent(assistantText),
        otherCharacterNames
    )
    val userRelationshipEvidence = relationshipEventEvidenceFromUser(userText)
    val assistantRelationshipEvidence = relationshipResponseEvidence(visibleAssistantText)
    val userWarmScore = scoreKeywordHits(userRelationshipEvidence, USER_WARM_KEYWORDS)
    val userHostileScore = scoreKeywordHits(userRelationshipEvidence, USER_HOSTILE_KEYWORDS)
    val assistantWarmScore = if (roleInteractionOnly) 0 else scoreKeywordHits(visibleAssistantText, ASSISTANT_WARM_KEYWORDS)
    val assistantColdScore = if (roleInteractionOnly) 0 else scoreKeywordHits(stripNegatedRelationshipTerms(visibleAssistantText), ASSISTANT_COLD_KEYWORDS)
    val privateThought = explicitCharacterInnerThoughtEvidence(assistantText)
    val privateWarmScore = scoreKeywordHits(privateThought, ASSISTANT_WARM_KEYWORDS)
    val privateColdScore = scoreKeywordHits(stripNegatedRelationshipTerms(privateThought), ASSISTANT_COLD_KEYWORDS)
    val relationshipSignals = collectRelationshipSignals(
        userText = userRelationshipEvidence,
        assistantText = assistantRelationshipEvidence,
        directInteractionAllowed = directInteractionAllowed && !roleInteractionOnly
    )
    val signalAffection = relationshipSignals.sumOf { it.affection }
    val signalTrust = relationshipSignals.sumOf { it.trust }
    val signalTension = relationshipSignals.sumOf { it.tension }
    val signalMomentum = relationshipSignals.sumOf { it.momentum }
    val strongPositive = relationshipSignals.any { it.strongPositive }
    val strongNegative = relationshipSignals.any { it.strongNegative }
    val hasPairedEvent = relationshipSignals.isNotEmpty()
    val anchorProfile = relationshipAnchorProfile(previous.relationshipAnchor)
    val affectionDelta = if (hasPairedEvent) {
        stabilizeRelationshipDelta(previous.affection, signalAffection, strongPositive, strongNegative, previous.intimacyBaseline)
    } else 0
    val trustDelta = if (hasPairedEvent) {
        stabilizeRelationshipDelta(previous.trust, signalTrust, strongPositive, strongNegative, anchorProfile.trustBaseline)
    } else 0
    val tensionDelta = if (hasPairedEvent) {
        stabilizeTensionDelta(previous.tension, signalTension, strongPositive, strongNegative)
    } else 0

    val momentum = if (hasPairedEvent) {
        updateRelationshipMomentum(previous.relationshipMomentum, signalMomentum, strongPositive, strongNegative)
    } else previous.relationshipMomentum
    val affection = (previous.affection + affectionDelta).coerceIn(-100, 100)
    val trust = (previous.trust + trustDelta).coerceIn(-100, 100)
    val tension = (previous.tension + tensionDelta).coerceIn(0, 100)
    val relationshipAnchor = if (hasPairedEvent) {
        evolveRelationshipAnchor(previous, relationshipSignals, affection, trust, tension)
    } else previous.relationshipAnchor
    val relationshipStage = relationshipStageLabel(affection, trust, tension, relationshipAnchor, previous.intimacyBaseline, momentum)
    val mood = statusNoteLabel(affection, trust, tension, assistantWarmScore, assistantColdScore, userHostileScore, privateWarmScore, privateColdScore)
    val progress = if (hasPairedEvent) {
        progressNoteLabel(rounds, relationshipStage, momentum)
    } else previous.progressNote
    val recentEventSummary = relationshipSummary(relationshipSignals, userWarmScore, userHostileScore, assistantWarmScore, assistantColdScore, relationshipAnchor)

    val next = CharacterStoryState(
        relationshipAnchor = relationshipAnchor,
        relationshipAnchorLocked = previous.relationshipAnchorLocked,
        intimacyBaseline = previous.intimacyBaseline,
        relationshipMomentum = momentum,
        affection = affection,
        trust = trust,
        tension = tension,
        progressNote = progress,
        statusNote = mood,
        recentEventSummary = recentEventSummary,
        baselineMessageTimestamp = timestamp
    )

    val previousStage = relationshipStageLabel(previous.affection, previous.trust, previous.tension, previous.relationshipAnchor, previous.intimacyBaseline, previous.relationshipMomentum)
    val significantShift = kotlin.math.abs(affection - previous.affection) >= 12 ||
        kotlin.math.abs(trust - previous.trust) >= 12 ||
        kotlin.math.abs(tension - previous.tension) >= 12
    val shouldAppendLog = relationshipStage != previousStage || significantShift
    val logTitle = if (relationshipStage != previousStage) {
        "关系进入$relationshipStage"
    } else {
        "关系状态更新"
    }
    val logNote = buildString {
        append("第 $rounds 轮后，关系判断为$relationshipStage。")
        append(" 关系锚点：$relationshipAnchor。")
        append(" 当前状态：$mood。")
        if (recentEventSummary.isNotBlank()) {
            append(" 最近互动：$recentEventSummary。")
        }
        if (affectionDelta != 0 || trustDelta != 0 || tensionDelta != 0) {
            append(" 本轮变化: 好感")
            append(if (affectionDelta >= 0) "+$affectionDelta" else affectionDelta)
            append("，信任")
            append(if (trustDelta >= 0) "+$trustDelta" else trustDelta)
            append("，紧张")
            append(if (tensionDelta >= 0) "+$tensionDelta" else tensionDelta)
            append("。")
        }
        if (momentum != previous.relationshipMomentum) {
            append(" 升温势能")
            append(if (momentum - previous.relationshipMomentum >= 0) "+${momentum - previous.relationshipMomentum}" else momentum - previous.relationshipMomentum)
            append("。")
        }
    }

    return CharacterStoryProgressResult(
        state = next,
        relationshipStage = relationshipStage,
        shouldAppendLog = shouldAppendLog,
        logTitle = logTitle,
        logNote = logNote
    )
}

fun relationshipStageLabel(
    affection: Int,
    trust: Int,
    tension: Int,
    relationshipAnchor: String = "未知",
    intimacyBaseline: Int = 0,
    relationshipMomentum: Int = 0
): String {
    val acceptance = (100 - tension).coerceIn(0, 100)
    val effectiveAffection = maxOf(affection, intimacyBaseline)
    return when {
        relationshipAnchor == "伴侣/婚姻" && acceptance >= 58 && trust >= 55 -> "既定伴侣"
        effectiveAffection >= 90 && trust >= 75 && acceptance >= 65 -> "确认关系"
        (effectiveAffection >= 70 && trust >= 45) || relationshipMomentum >= 28 -> "暧昧升温"
        effectiveAffection >= 40 && trust >= 20 -> "熟悉亲近"
        effectiveAffection >= 12 || trust >= 12 -> "开始熟悉"
        affection <= -80 || trust <= -70 -> "厌恶期"
        affection <= -50 || trust <= -35 -> "反感期"
        affection <= -20 || acceptance <= 30 -> "疏离期"
        else -> "陌生人"
    }
}

fun advanceInterCharacterRelation(
    previous: InterCharacterRelationState,
    responseText: String,
    sourceName: String,
    targetName: String,
    timestamp: Long = System.currentTimeMillis()
): InterCharacterRelationState {
    val text = relationshipResponseEvidence(ChatMessageBuilder.publicGroupContent(responseText))
        .let(::stripNegatedRelationshipTerms)
    val directedText = relationshipEvidenceClauses(text).filter { clause ->
        clause.contains(targetName, ignoreCase = true) ||
            Regex("(?:你|对你|和你|跟你|帮你|支持你|相信你|讨厌你|威胁你)").containsMatchIn(clause)
    }.joinToString("。")
    val jealousy = containsAny(directedText, listOf("吃醋", "嫉妒", "不许你靠近", "离他远点", "离她远点", "抢走", "占有欲"))
    val rivalry = containsAny(directedText, listOf("不服", "较量", "竞争", "比一比", "轮不到你", "我不会输", "挑战你"))
    val hostility = containsAny(directedText, listOf("讨厌你", "厌恶你", "别插嘴", "闭嘴", "滚开", "敌意", "威胁你"))
    val support = containsAny(directedText, listOf("赞同你", "说得对", "交给我", "我帮你", "配合你", "站在你这边", "保护你"))
    val warmth = containsAny(directedText, listOf("关心你", "担心你", "谢谢你", "相信你", "理解你", "对你笑", "拍拍你"))
    if (!jealousy && !rivalry && !hostility && !support && !warmth) return previous

    val affinityDelta = when {
        hostility -> -5
        support -> 4
        warmth -> 3
        jealousy -> -1
        else -> 0
    }
    val rivalryDelta = when {
        rivalry -> 6
        jealousy -> 4
        hostility -> 3
        support -> -2
        else -> 0
    }
    val tensionDelta = when {
        hostility -> 7
        rivalry -> 4
        jealousy -> 3
        support || warmth -> -2
        else -> 0
    }
    val affinity = (previous.affinity + affinityDelta).coerceIn(-100, 100)
    val rivalryValue = (previous.rivalry + rivalryDelta).coerceIn(0, 100)
    val tension = (previous.tension + tensionDelta).coerceIn(0, 100)
    val label = when {
        hostility && affinity <= -35 -> "明显敌对"
        rivalryValue >= 55 && affinity >= 20 -> "亲近竞争"
        rivalryValue >= 45 -> "竞争关系"
        affinity >= 60 && tension <= 35 -> "亲密同伴"
        affinity >= 30 -> "友好同伴"
        affinity <= -25 -> "互相排斥"
        else -> "普通同伴"
    }
    val event = when {
        hostility -> "$sourceName 对 $targetName 表现出明确敌意"
        jealousy -> "$sourceName 因 $targetName 与用户的互动产生吃醋或竞争反应"
        rivalry -> "$sourceName 主动向 $targetName 表现竞争意识"
        support -> "$sourceName 明确支持或配合 $targetName"
        else -> "$sourceName 对 $targetName 表现出友好与关心"
    }
    return InterCharacterRelationState(affinity, rivalryValue, tension, label, event, timestamp)
}

private fun progressNoteLabel(rounds: Int, relationshipStage: String, relationshipMomentum: Int): String {
    return when {
        relationshipStage == "既定伴侣" -> "既有关系被稳定读取，重点转向细节升温"
        relationshipStage == "暧昧升温" || relationshipStage == "确认关系" || relationshipMomentum >= 26 -> "关系推进出现明确进展"
        relationshipMomentum >= 16 -> "互动正在快速升温"
        relationshipStage == "熟悉亲近" && rounds >= 18 -> "关系正在稳定加深"
        relationshipStage == "开始熟悉" && rounds >= 12 -> "互动逐渐稳定成型"
        relationshipStage == "陌生人" && rounds >= 36 -> "长线互动仍未破冰"
        relationshipStage == "疏离期" && rounds >= 24 -> "关系反复拉扯中"
        rounds >= 60 -> "长线主线推进中"
        rounds >= 36 -> "长期关系持续发展中"
        rounds >= 18 -> "互动逐渐稳定成型"
        else -> "仍在起步"
    }
}

private fun statusNoteLabel(
    affection: Int,
    trust: Int,
    tension: Int,
    assistantWarmScore: Int,
    assistantColdScore: Int,
    userHostileScore: Int,
    privateWarmScore: Int,
    privateColdScore: Int
): String {
    val acceptance = (100 - tension).coerceIn(0, 100)
    return when {
        affection <= -45 || userHostileScore >= 2 -> "明显生气或抗拒"
        acceptance <= 20 -> "明显不接受靠近"
        assistantColdScore > assistantWarmScore -> "态度转冷"
        privateColdScore > privateWarmScore && assistantColdScore == 0 -> "内心仍有防备，尚未形成公开关系事件"
        privateWarmScore > privateColdScore && assistantWarmScore == 0 -> "内心有所亲近，尚未形成公开关系事件"
        affection >= 60 && trust >= 50 && acceptance >= 65 -> "明显亲近"
        trust >= 40 && acceptance >= 55 -> "开始愿意接纳与交心"
        else -> "情绪仍在波动"
    }
}

private fun scoreKeywordHits(text: String, keywords: List<String>): Int {
    if (text.isBlank()) return 0
    val occupied = mutableListOf<IntRange>()
    return keywords.sortedByDescending(String::length).sumOf { keyword ->
        Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE).findAll(text).count { match ->
            val range = match.range
            if (occupied.any { range.first <= it.last && it.first <= range.last }) false
            else {
                occupied += range
                true
            }
        }
    }
}

private fun collectRelationshipSignals(
    userText: String,
    assistantText: String,
    directInteractionAllowed: Boolean
): List<RelationshipSignal> {
    if (!directInteractionAllowed) return emptyList()
    val signals = mutableListOf<RelationshipSignal>()

    classifyInteractionEvents(userText).forEach { eventType ->
        classifyInteractionResponse(eventType, assistantText)?.let { responseType ->
            buildInteractionSignal(eventType, responseType)?.let { signals += it }
        }
    }
    return signals
}

private fun relationshipEventEvidenceFromUser(text: String): String {
    return relationshipEvidenceClauses(text)
        .filterNot(::isThirdPartyOrMetaRelationshipClause)
        .filterNot(::isNegatedRelationshipClause)
        .joinToString("。")
}

private fun relationshipResponseEvidence(text: String): String {
    return relationshipEvidenceClauses(text)
        .filterNot(::isThirdPartyOrMetaRelationshipClause)
        .filterNot(::isQuotedRelationshipClause)
        .joinToString("。")
}

private fun relationshipEvidenceClauses(text: String): List<String> {
    val withoutQuotedBlocks = text
        .replace(Regex("[“\"「『‘][^”\"」』’]*[”\"」』’]"), "")
        .replace(Regex("(?m)^\\s*>.*$"), "")
    return withoutQuotedBlocks
        .split(Regex("(?<=[。！？!?；;\\n])"))
        .map(String::trim)
        .filter(String::isNotBlank)
}

private fun isQuotedRelationshipClause(clause: String): Boolean {
    return Regex("(?:说|表示|提到|回忆|转述|告诉|听见|引用).{0,12}[：:]").containsMatchIn(clause)
}

private fun isThirdPartyOrMetaRelationshipClause(clause: String): Boolean {
    val normalized = clause.lowercase()
    return listOf(
        "故事里", "小说里", "梦里", "假设", "假如", "如果", "要是", "比如", "例如",
        "有人说", "她说", "他说", "他们说", "她告诉", "他告诉", "她回忆", "他回忆",
        "是什么意思", "怎么理解", "你觉得", "会怎么回答", "是否应该", "难道"
    ).any { normalized.contains(it) } || isQuotedRelationshipClause(clause)
}

private fun isNegatedRelationshipClause(clause: String): Boolean {
    return listOf(
        Regex("(?:不|没|没有|并非|不是|从未|不曾|不会|不想|别误会).{0,8}(?:喜欢你|爱你|告白|交往|和你在一起|伤害你|威胁你|讨厌你|恨你|相信你|依赖你|保护你)"),
        Regex("(?:没有说|没说|不是要|并不是要|不代表).{0,12}(?:喜欢你|爱你|告白|交往|接受|同意|愿意)"),
        Regex("(?:不|没|没有|并不|不是|不曾).{0,4}(?:讨厌|厌恶|反感|拒绝)你?")
    ).any { it.containsMatchIn(clause) }
}

private fun classifyInteractionEvent(userText: String): InteractionEventType? {
    return classifyInteractionEvents(userText).firstOrNull()
}

private fun classifyInteractionEvents(userText: String): List<InteractionEventType> {
    val candidates = listOf(
        InteractionEventType.THREAT to listOf("威胁", "杀了", "弄死", "伤害你", "报复你"),
        InteractionEventType.CONFLICT to listOf("讨厌你", "滚", "闭嘴", "离我远点", "别碰我"),
        InteractionEventType.APOLOGY_REPAIR to listOf("对不起", "抱歉", "是我不好", "我错了"),
        InteractionEventType.COMMITMENT to listOf("做我女朋友", "做我男朋友", "嫁给我", "和我在一起", "我们交往吧", "交往", "告白"),
        InteractionEventType.CONFESSION to listOf("喜欢你", "我爱你", "想和你在一起", "你对我很重要"),
        InteractionEventType.PHYSICAL_INTIMACY to listOf("亲你", "吻你", "亲了你", "吻了你", "抱住你", "抱紧你", "拥抱你", "牵你的手", "摸你的头", "贴近你", "蹭了蹭你"),
        InteractionEventType.GRATITUDE to listOf("谢谢", "辛苦了", "你帮了我", "多亏你"),
        InteractionEventType.VULNERABILITY to listOf("相信你", "告诉我", "你可以依赖我", "我不会离开"),
        InteractionEventType.SUPPORT to listOf("我会陪着你", "我陪你", "我会保护你", "别怕", "有我在")
    )
    val matches = candidates.filter { (_, keywords) -> containsAny(userText, keywords) }.map(Pair<InteractionEventType, List<String>>::first).toMutableList()
    if (matches.any { it == InteractionEventType.THREAT }) return listOf(InteractionEventType.THREAT)
    if (matches.any { it == InteractionEventType.CONFLICT }) return listOf(InteractionEventType.CONFLICT)
    if (matches.any { it == InteractionEventType.COMMITMENT }) matches.removeAll { it == InteractionEventType.CONFESSION }
    return matches.distinct().take(2)
}

private fun classifyInteractionResponse(
    eventType: InteractionEventType,
    assistantText: String
): InteractionResponseType? {
    val normalized = stripNegatedRelationshipTerms(assistantText)
    val activeAccept = when (eventType) {
        InteractionEventType.APOLOGY_REPAIR -> containsAny(normalized, listOf("我原谅你", "原谅你了", "没关系", "这次就算了", "接受你的道歉"))
        InteractionEventType.SUPPORT -> containsAny(normalized, listOf("那就拜托你了", "我相信你", "陪着我", "谢谢你陪我", "有你在就好"))
        InteractionEventType.VULNERABILITY -> containsAny(normalized, listOf("我相信你", "愿意告诉你", "只告诉你", "可以依赖你"))
        InteractionEventType.CONFESSION -> containsAny(normalized, listOf("我也喜欢你", "我也爱你", "我对你也是", "愿意接受你的感情"))
        InteractionEventType.GRATITUDE -> containsAny(normalized, listOf("不用谢", "不客气", "能帮到你就好", "你没事就好"))
        InteractionEventType.PHYSICAL_INTIMACY -> containsAny(normalized, listOf("回吻", "吻了回来", "抱紧你", "主动贴上来", "顺势亲回去", "主动靠近你"))
        InteractionEventType.COMMITMENT -> containsAny(normalized, listOf("我愿意", "我答应你", "答应和你交往", "做你的女朋友", "做你的男朋友", "和你在一起"))
        else -> false
    }
    val shyAccept = containsAny(normalized, listOf(
        "耳尖发烫", "脸红", "呼吸一滞", "心跳乱了", "害羞", "睫毛颤了颤", "脸一下热了", "脑子一片空白",
        "尾巴炸开", "手足无措", "不知所措"
    ))
    val passiveAccept = when (eventType) {
        InteractionEventType.PHYSICAL_INTIMACY -> containsAny(normalized, listOf("没有推开你", "没推开你", "任由你亲", "任你抱着", "默许你的靠近"))
        InteractionEventType.CONFESSION -> containsAny(normalized, listOf("接受你的心意", "没有拒绝你的心意"))
        InteractionEventType.COMMITMENT -> containsAny(normalized, listOf("接受你的告白", "接受交往", "默认了这段关系"))
        InteractionEventType.APOLOGY_REPAIR -> containsAny(normalized, listOf("接受你的道歉", "暂时原谅你"))
        else -> false
    }
    val lightDiscomfort = containsAny(normalized, listOf(
        "后退", "退了一步", "僵硬", "别闹", "不自在", "有点慌", "避开目光", "下意识躲"
    ))
    val explicitReject = containsAny(normalized, listOf(
        "推开你", "躲开你", "别这样", "不可以", "住手", "明确拒绝你", "拒绝你的告白", "保持距离", "我还不能信你"
    ))
    val strongReject = containsAny(normalized, listOf(
        "觉得你恶心", "对你感到厌恶", "滚开", "别碰我", "你有病", "对你反胃", "嫌恶地看着你"
    ))

    return when {
        strongReject -> InteractionResponseType.STRONG_REJECT
        explicitReject -> InteractionResponseType.EXPLICIT_REJECT
        lightDiscomfort && eventType == InteractionEventType.PHYSICAL_INTIMACY -> InteractionResponseType.LIGHT_DISCOMFORT
        activeAccept -> InteractionResponseType.ACTIVE_ACCEPT
        shyAccept -> InteractionResponseType.SHY_ACCEPT
        passiveAccept -> InteractionResponseType.PASSIVE_ACCEPT
        else -> null
    }
}

private fun buildInteractionSignal(
    eventType: InteractionEventType,
    responseType: InteractionResponseType
): RelationshipSignal? {
    return when (eventType) {
        InteractionEventType.APOLOGY_REPAIR -> when (responseType) {
            InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("道歉得到接纳", affection = 3, trust = 4, tension = -3, momentum = 1, strongPositive = true, eventType = eventType, responseType = responseType)
            InteractionResponseType.EXPLICIT_REJECT, InteractionResponseType.STRONG_REJECT -> RelationshipSignal("道歉未被接纳", affection = -2, trust = -3, tension = 2, momentum = -1)
            else -> null
        }
        InteractionEventType.SUPPORT -> when (responseType) {
            InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.SHY_ACCEPT -> RelationshipSignal("双方建立依靠", affection = 4, trust = 4, tension = -2, momentum = 2, strongPositive = true)
            InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("支持被接住", affection = 2, trust = 3, tension = -1, momentum = 1)
            else -> null
        }
        InteractionEventType.VULNERABILITY -> when (responseType) {
            InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.SHY_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("角色开始袒露内心", affection = 2, trust = 5, tension = -1, momentum = 2, strongPositive = true)
            else -> null
        }
        InteractionEventType.CONFESSION -> when (responseType) {
            InteractionResponseType.ACTIVE_ACCEPT -> RelationshipSignal("感情表达得到明确回应", affection = 5, trust = 3, tension = -2, momentum = 4, strongPositive = true, eventType = eventType, responseType = responseType)
            InteractionResponseType.SHY_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("感情表达被温和接住", affection = 4, trust = 2, tension = -1, momentum = 3, strongPositive = true, eventType = eventType, responseType = responseType)
            InteractionResponseType.EXPLICIT_REJECT, InteractionResponseType.STRONG_REJECT -> RelationshipSignal("感情表达被拒绝", affection = -5, trust = -3, tension = 4, momentum = -3, strongNegative = true)
            else -> null
        }
        InteractionEventType.GRATITUDE -> when (responseType) {
            InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("善意互动被接住", affection = 2, trust = 2, tension = -1, momentum = 1)
            else -> null
        }
        InteractionEventType.PHYSICAL_INTIMACY -> when (responseType) {
            InteractionResponseType.ACTIVE_ACCEPT -> RelationshipSignal("亲密互动得到主动回应", affection = 8, trust = 4, tension = -2, momentum = 7, strongPositive = true)
            InteractionResponseType.SHY_ACCEPT -> RelationshipSignal("亲密互动被害羞接受", affection = 6, trust = 3, tension = 0, momentum = 5, strongPositive = true)
            InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("亲密互动被默认接受", affection = 5, trust = 2, tension = 0, momentum = 4, strongPositive = true)
            InteractionResponseType.LIGHT_DISCOMFORT -> RelationshipSignal("亲密互动造成不适", affection = -2, trust = -1, tension = 3, momentum = -2)
            InteractionResponseType.EXPLICIT_REJECT -> RelationshipSignal("亲密互动被明确拒绝", affection = -5, trust = -3, tension = 5, momentum = -4, strongNegative = true)
            InteractionResponseType.STRONG_REJECT -> RelationshipSignal("亲密互动引发明显厌恶", affection = -7, trust = -5, tension = 7, momentum = -5, strongNegative = true)
        }
        InteractionEventType.COMMITMENT -> when (responseType) {
            InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("恋爱关系被明确确认", affection = 8, trust = 5, tension = -3, momentum = 8, strongPositive = true, eventType = eventType, responseType = responseType)
            InteractionResponseType.SHY_ACCEPT -> RelationshipSignal("关系推进被羞涩接纳", affection = 6, trust = 4, tension = -1, momentum = 6, strongPositive = true, eventType = eventType, responseType = responseType)
            InteractionResponseType.EXPLICIT_REJECT, InteractionResponseType.STRONG_REJECT -> RelationshipSignal("关系推进被拒绝", affection = -6, trust = -4, tension = 5, momentum = -4, strongNegative = true)
            else -> null
        }
        InteractionEventType.CONFLICT -> when (responseType) {
            InteractionResponseType.EXPLICIT_REJECT, InteractionResponseType.STRONG_REJECT -> RelationshipSignal("冲突正面升级", affection = -5, trust = -4, tension = 6, momentum = -3, strongNegative = true)
            else -> null
        }
        InteractionEventType.THREAT -> RelationshipSignal("出现明确威胁", affection = -6, trust = -5, tension = 7, momentum = -4, strongNegative = true)
    }
}

private fun relationshipSummary(
    signals: List<RelationshipSignal>,
    userWarmScore: Int,
    userHostileScore: Int,
    assistantWarmScore: Int,
    assistantColdScore: Int,
    relationshipAnchor: String
): String {
    if (signals.isNotEmpty()) {
        return signals.take(2).joinToString("，") { it.label }
    }
    return when {
        relationshipAnchor == "伴侣/婚姻" && userWarmScore > 0 && assistantWarmScore > 0 -> "既定亲密关系被自然延续，互动正在继续升温"
        userHostileScore > 0 && assistantColdScore > assistantWarmScore -> "本轮以防备和冲突为主，关系继续承压"
        userWarmScore > 0 && assistantWarmScore > 0 -> "双方有示好和接纳，但还缺少更明确的剧情推进事件"
        assistantColdScore > assistantWarmScore -> "角色态度偏冷，仍在观望或自我保护"
        assistantWarmScore > 0 -> "角色释放出一定善意，但更多像轻微松动而不是决定性推进"
        else -> "本轮没有出现足以改写关系的大事件，主线状态保持延续"
    }
}

private fun evolveRelationshipAnchor(
    previous: CharacterStoryState,
    signals: List<RelationshipSignal>,
    affection: Int,
    trust: Int,
    tension: Int
): String {
    if (previous.relationshipAnchorLocked) return previous.relationshipAnchor
    val protectedIdentityAnchor = previous.relationshipAnchor in setOf(
        "伴侣/婚姻", "青梅竹马", "家人/亲属", "师生/前后辈", "敌对/宿敌", "上下位/主仆"
    )
    val acceptedCommitment = signals.any {
        it.eventType == InteractionEventType.COMMITMENT &&
            it.responseType in setOf(InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT, InteractionResponseType.SHY_ACCEPT)
    }
    val acceptedConfession = signals.any {
        it.eventType == InteractionEventType.CONFESSION &&
            it.responseType in setOf(InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT, InteractionResponseType.SHY_ACCEPT)
    }
    return when {
        acceptedCommitment && !protectedIdentityAnchor -> "伴侣/婚姻"
        acceptedConfession && previous.relationshipAnchor in setOf("未知", "日常熟人", "互动发展中") -> "暧昧/暗恋"
        previous.relationshipAnchor == "未知" && affection >= 75 && trust >= 55 && tension <= 38 -> "互动发展中"
        else -> previous.relationshipAnchor
    }
}

private fun relationshipEvidenceForCurrentCharacter(text: String, otherCharacterNames: List<String>): String {
    if (otherCharacterNames.isEmpty()) return text
    return text.split(Regex("(?<=[。！？!?\\n])")).filterNot { segment ->
        val trimmed = segment.trimStart()
        otherCharacterNames.any { name -> Regex("^${Regex.escape(name)}\\s*[：:]", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) }
    }.joinToString("")
}

private fun privateThoughtEvidence(text: String): String {
    if (text.isBlank()) return ""
    return relationshipEvidenceClauses(text)
        .filterNot(::isQuotedRelationshipClause)
        .filterNot(::isThirdPartyOrMetaRelationshipClause)
        .joinToString("。")
}

private fun explicitCharacterInnerThoughtEvidence(content: String): String {
    if (content.isBlank()) return ""
    val thoughts = mutableListOf<String>()
    Regex(
        "<inner(?:\\s+character\\s*=\\s*['\"][^'\"]+['\"])?\\s*>(.*?)</inner>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(content).forEach { match -> match.groupValues[1].trim().takeIf(String::isNotBlank)?.let(thoughts::add) }
    listOf(
        Regex("【内心】(.*?)(?:【/内心】|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("（内心[:：](.*?)）", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("\\(内心[:：](.*?)\\)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    ).forEach { regex ->
        regex.findAll(content).forEach { match -> match.groupValues[1].trim().takeIf(String::isNotBlank)?.let(thoughts::add) }
    }
    return privateThoughtEvidence(thoughts.distinct().joinToString("。"))
}

private fun stripNegatedRelationshipTerms(text: String): String {
    return text
        .replace(Regex("(?:没有|没|并不|不是|不曾|无法|不想)(?:真的)?(?:生气|拒绝|厌恶|讨厌|推开|躲开|反感)"), "")
        .replace(Regex("(?:没有|没)(?:有)?(?:推开|躲开|拒绝)你?"), "接受靠近")
}

private fun stabilizeRelationshipDelta(
    previousValue: Int,
    rawDelta: Int,
    strongPositive: Boolean,
    strongNegative: Boolean,
    baselineValue: Int = 0
): Int {
    if (rawDelta == 0) return 0
    var adjusted = rawDelta
    if (kotlin.math.abs(adjusted) == 1 && !strongPositive && !strongNegative) {
        adjusted = 0
    }
    if (adjusted == 2 && previousValue in 10..74 && !strongNegative) {
        adjusted = 3
    }
    if (previousValue >= 45 && adjusted < 0 && !strongNegative) {
        adjusted /= 2
    }
    if (previousValue <= -35 && adjusted > 0 && !strongPositive) {
        adjusted /= 2
    }
    if (baselineValue >= 40 && adjusted > 0) {
        adjusted += 1
    }
    if (strongPositive && previousValue in -10..20 && adjusted in 1..4) {
        adjusted = 5
    }
    if (strongPositive && adjusted > 0) {
        adjusted += 1
    }
    if (strongNegative && adjusted < 0) {
        adjusted -= 1
    }
    return adjusted.coerceIn(-10, 10)
}

private fun stabilizeTensionDelta(
    previousValue: Int,
    rawDelta: Int,
    strongPositive: Boolean,
    strongNegative: Boolean
): Int {
    if (rawDelta == 0) return if (previousValue > 18) -1 else 0
    var adjusted = rawDelta
    if (adjusted > 0 && previousValue >= 70 && !strongNegative) {
        adjusted = maxOf(1, adjusted / 2)
    }
    if (adjusted < 0 && previousValue <= 20 && !strongPositive) {
        adjusted = 0
    }
    return adjusted.coerceIn(-6, 6)
}

private fun containsAny(text: String, keywords: List<String>): Boolean {
    return keywords.any { text.contains(it, ignoreCase = true) }
}

private fun relationshipAnchorProfile(anchor: String): RelationshipAnchorProfile {
    return when (anchor) {
        "伴侣/婚姻" -> RelationshipAnchorProfile(
            anchor = anchor,
            intimacyBaseline = 62,
            trustBaseline = 60,
            tensionBaseline = 18,
            promptHint = "默认允许自然轻度亲密，重点看回应热度与当下情绪。",
            eventSummary = "既定伴侣关系稳定存在"
        )
        "青梅竹马" -> RelationshipAnchorProfile(anchor, 42, 52, 22, "熟悉感强，升温速度可快于陌生人。", "旧日默契仍然有效")
        "暧昧/暗恋" -> RelationshipAnchorProfile(anchor, 26, 28, 32, "情感推进可明显升温，但仍要保留试探。", "情感张力持续存在")
        "敌对/宿敌" -> RelationshipAnchorProfile(anchor, -42, -24, 60, "除非出现强修复事件，否则默认对抗。", "敌对基础仍在")
        else -> RelationshipAnchorProfile(anchor, 0, 0, 20, "按当前互动自然推进。", "关系仍主要由当前互动塑造")
    }
}

private fun detectWarmPhysicalIntimacy(userText: String, assistantCombined: String): Boolean {
    return classifyInteractionEvent(userText) == InteractionEventType.PHYSICAL_INTIMACY &&
        classifyInteractionResponse(InteractionEventType.PHYSICAL_INTIMACY, assistantCombined) in setOf(
            InteractionResponseType.ACTIVE_ACCEPT,
            InteractionResponseType.SHY_ACCEPT,
            InteractionResponseType.PASSIVE_ACCEPT
        )
}

private fun detectExplicitRomanceProgress(userText: String, assistantCombined: String): Boolean {
    val eventType = classifyInteractionEvent(userText)
    if (eventType != InteractionEventType.CONFESSION && eventType != InteractionEventType.COMMITMENT) {
        return false
    }
    return classifyInteractionResponse(eventType, assistantCombined) in setOf(
        InteractionResponseType.ACTIVE_ACCEPT,
        InteractionResponseType.SHY_ACCEPT,
        InteractionResponseType.PASSIVE_ACCEPT
    )
}

private fun updateRelationshipMomentum(
    previousMomentum: Int,
    rawDelta: Int,
    strongPositive: Boolean,
    strongNegative: Boolean
): Int {
    if (rawDelta == 0) {
        val decay = if (previousMomentum > 0) 1 else if (previousMomentum < 0) -1 else 0
        return (previousMomentum - decay).coerceIn(-100, 100)
    }
    var adjusted = rawDelta
    if (strongPositive && adjusted > 0) adjusted += 1
    if (strongNegative && adjusted < 0) adjusted -= 1
    return (previousMomentum + adjusted).coerceIn(-100, 100)
}
