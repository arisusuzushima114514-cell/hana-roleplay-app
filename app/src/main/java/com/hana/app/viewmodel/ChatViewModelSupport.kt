package com.hana.app.viewmodel

import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.settings.CharacterStoryLogEntry
import com.hana.app.data.settings.CharacterStoryState

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
    val strongNegative: Boolean = false
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

fun buildCharacterSystemPrompt(character: CharacterCardEntity?, state: CharacterStoryState? = null): String {
    if (character == null) return DEFAULT_CHINO_PROMPT
    return buildString {
        // --- 核心格式指令（放最前面，防止被长文本挤出注意力窗口）---
        append("【输出格式】先输出角色说出口的话与动作；每轮回复末尾必须用 <inner>...</inner> 包裹角色的内心真实想法，禁止省略内心。")
        append("\n<inner> 保持1-2句，写具体情绪、身体反应或心口不一，禁止写成机械分析或元叙事。")
        append("\n不要把内心混进正文台词。")
        append("\n\n")
        // --- 角色身份 ---
        if (character.userPersona.isNotBlank()) {
            append("${character.userPersona}\n\n")
        }
        append("你是${character.name}。")
        if (character.description.isNotBlank()) {
            append("\n\n")
            append(character.description)
        }
        // --- 关系状态（精简版，减少token消耗）---
        if (state != null) {
            val effectiveAffection = maxOf(state.affection, state.intimacyBaseline)
            val acceptance = (100 - state.tension).coerceIn(0, 100)
            val relationshipDirective = when {
                state.affection <= -70 && state.intimacyBaseline < 45 -> "你厌恶用户。回复冷硬、防备、抗拒，允许讽刺。禁止示好、撒娇、暧昧、依赖、温柔、心疼、亲昵。"
                state.affection <= -35 && state.intimacyBaseline < 45 -> "你对用户有明显反感。回复冷淡、疏远、带防备。禁止温柔、心疼、依赖、撒娇、亲近、宠溺。"
                state.affection < 0 && state.intimacyBaseline < 45 -> "你对用户态度偏冷。回复礼貌但保留距离感，不要写成已经亲近。"
                effectiveAffection >= 80 -> "你对用户高度亲近。回复体现偏爱、在意、依赖，主动靠近和袒露心情。"
                effectiveAffection >= 50 -> "你对用户较为亲近。回复自然表现信任和关心，保持角色性格。"
                else -> "你仍在观察试探阶段。回复保持克制，自然推进关系。"
            }
            val trustDirective = when {
                state.trust <= -40 -> "你几乎不信任用户，不要交底。"
                state.trust < 0 -> "你对用户仍有怀疑，保留重要信息。"
                state.trust >= 60 -> "你较信任用户，可坦白表达。"
                else -> "信任仍在建立，保留分寸。"
            }
            val acceptanceDirective = when {
                acceptance <= 20 -> "接受度很低，保留边界，亲密推进会引起防备。"
                acceptance <= 45 -> "接受度偏低，保持谨慎，不要轻易亲密推进。"
                acceptance >= 80 -> "接受度很高，可自然接住靠近和亲密。"
                acceptance >= 60 -> "对用户有明显接纳，可自然接受靠近。"
                else -> "接受度有波动，视情况决定是否接纳。"
            }
            append("\n【关系状态】")
            append(" 锚点:${state.relationshipAnchor} 基线:${state.intimacyBaseline}")
            append(" 好感:${state.affection} 信任:${state.trust} 接受:${acceptance}")
            append("\n进度:${state.progressNote} 状态:${state.statusNote}")
            if (state.recentEventSummary.isNotBlank()) {
                append(" 最近:${state.recentEventSummary}")
            }
            append("\n$relationshipDirective")
            append("\n$trustDirective")
            append("\n$acceptanceDirective")
            append("\n请严格按此状态决定态度，既定亲密关系不能被小波动抹掉。台词、动作和<inner>都必须与状态一致。")
        }
    }
}

fun applyCreativePresetToCharacterPrompt(
    basePrompt: String,
    creativePreset: String,
    enabled: Boolean,
    allowPersonaInfluence: Boolean
): String {
    val cleanPreset = creativePreset.trim()
    if (!enabled || cleanPreset.isBlank()) return basePrompt
    val personaRelated = isPersonaRelatedCreativePreset(cleanPreset)
    return buildString {
        // Use prefix concatenation only. Persona-related presets are allowed to more strongly guide
        // tone, temperament and speaking style; non-persona presets remain a normal supplemental prefix.
        append("【全局创作预设】\n")
        append(
            if (personaRelated && allowPersonaInfluence) {
                "检测到这段创作预设与角色性格、语气、说话方式或人格表现直接相关。请优先按这段预设来塑造本轮回复的人格表现、语气、风格和情绪呈现，再继续遵守后面的角色卡设定、关系状态与输出格式要求。"
            } else if (personaRelated) {
                "检测到这段创作预设包含人格相关内容，但当前角色未允许它影响人格。请仅把它当作模型层补充信息使用，不要据此改写角色性格、气质、语气底色或核心人设。"
            } else {
                "这段创作预设作为本轮回复的前置补充信息使用。只有当它与角色表达方式直接相关时，才影响人格与语气；否则不要强行改写角色原本的性格。"
            }
        )
        append("\n")
        append(cleanPreset)
        append("\n【全局创作预设结束】\n\n")
        append(basePrompt)
    }
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
    val relationText = listOf(character.userPersona, character.description, character.greeting)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    val profile = detectInitialRelationshipProfile(relationText)
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

private fun detectInitialRelationshipProfile(text: String): InitialRelationshipProfile? {
    val normalized = text.lowercase()
    fun containsAny(keywords: List<String>): Boolean = keywords.any { normalized.contains(it) }

    return when {
        containsAny(listOf("男女朋友", "男朋友", "女朋友", "恋人", "情侣", "爱人", "伴侣", "未婚夫", "未婚妻", "老公", "老婆", "丈夫", "妻子", "boyfriend", "girlfriend", "lover", "partner", "husband", "wife")) -> {
            InitialRelationshipProfile(
                relationshipAnchor = "伴侣/婚姻",
                intimacyBaseline = 62,
                affection = 72,
                trust = 68,
                tension = 18,
                progressNote = "关系设定已存在明确亲密基础",
                statusNote = "默认亲近且带熟悉感",
                recentEventSummary = "角色设定中已明确存在恋爱/伴侣关系"
            )
        }
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
        containsAny(listOf("家人", "哥哥", "妹妹", "姐姐", "弟弟", "兄妹", "姐弟", "亲人", "家属", "family", "sibling")) -> {
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
        containsAny(listOf("老师", "学生", "学长", "学姐", "前辈", "后辈", "师徒", "mentor", "teacher", "student")) -> {
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
        containsAny(listOf("同学", "同桌", "同事", "室友", "队友", "classmate", "coworker", "roommate", "teammate")) -> {
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
        containsAny(listOf("仇人", "敌人", "宿敌", "死对头", "冤家", "enemy", "rival", "nemesis")) -> {
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
        containsAny(listOf("主仆", "主人", "女仆", "执事", "侍从", "属下", "上司", "下属", "master", "servant", "maid", "butler")) -> {
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

fun advanceCharacterStoryState(
    previous: CharacterStoryState,
    userText: String,
    assistantText: String,
    assistantInnerThought: String,
    rounds: Int,
    timestamp: Long = System.currentTimeMillis()
): CharacterStoryProgressResult {
    val assistantCombined = listOf(assistantText, assistantInnerThought)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    val userWarmScore = scoreKeywordHits(userText, USER_WARM_KEYWORDS)
    val userHostileScore = scoreKeywordHits(userText, USER_HOSTILE_KEYWORDS)
    val assistantWarmScore = scoreKeywordHits(assistantCombined, ASSISTANT_WARM_KEYWORDS)
    val assistantColdScore = scoreKeywordHits(assistantCombined, ASSISTANT_COLD_KEYWORDS)
    val relationshipSignals = collectRelationshipSignals(userText, assistantText, assistantInnerThought)
    val signalAffection = relationshipSignals.sumOf { it.affection }
    val signalTrust = relationshipSignals.sumOf { it.trust }
    val signalTension = relationshipSignals.sumOf { it.tension }
    val signalMomentum = relationshipSignals.sumOf { it.momentum }
    val strongPositive = relationshipSignals.any { it.strongPositive }
    val strongNegative = relationshipSignals.any { it.strongNegative }
    val anchorProfile = relationshipAnchorProfile(previous.relationshipAnchor)
    val warmContactDetected = detectWarmPhysicalIntimacy(userText, assistantCombined)
    val explicitRomanceDetected = detectExplicitRomanceProgress(userText, assistantCombined)
    val rapidRomanceMomentum = when {
        relationshipSignals.count { it.strongPositive } >= 2 -> 4
        relationshipSignals.any { it.label.contains("感情表达", ignoreCase = true) } -> 3
        relationshipSignals.any { it.label.contains("依靠", ignoreCase = true) || it.label.contains("袒露", ignoreCase = true) } -> 3
        assistantWarmScore >= 3 && userWarmScore >= 2 -> 2
        else -> 0
    }
    val reciprocalWarmth = if (userWarmScore > 0 && assistantWarmScore > 0) 2 else 0
    val conflictSignal = if (userHostileScore > 0 && assistantColdScore > 0) 1 else 0
    val baselineProtection = when {
        previous.intimacyBaseline >= 55 && warmContactDetected && !strongNegative -> 4
        previous.intimacyBaseline >= 35 && warmContactDetected && !strongNegative -> 2
        else -> 0
    }
    val acceleratedRomanceBoost = when {
        explicitRomanceDetected && !strongNegative -> 6
        signalMomentum >= 6 && !strongNegative -> 4
        signalMomentum >= 3 && !strongNegative -> 2
        else -> 0
    }

    val softAffectionDelta = when {
        assistantWarmScore > assistantColdScore && userWarmScore > 0 -> 3
        assistantColdScore > assistantWarmScore && userHostileScore > 0 -> -2
        assistantWarmScore > assistantColdScore -> 2
        assistantColdScore > assistantWarmScore -> -1
        else -> 0
    }
    val softTrustDelta = when {
        userWarmScore > 0 && assistantWarmScore > 0 -> 2
        userHostileScore > 0 && assistantColdScore > 0 -> -2
        assistantColdScore > assistantWarmScore -> -1
        else -> 0
    }
    val softTensionDelta = when {
        userHostileScore > 0 && assistantColdScore > 0 -> 3
        assistantColdScore > assistantWarmScore -> 1
        assistantWarmScore > 0 && userWarmScore > 0 -> -1
        else -> 0
    }

    val rawAffectionDelta = (signalAffection + softAffectionDelta + reciprocalWarmth + rapidRomanceMomentum + baselineProtection + acceleratedRomanceBoost - conflictSignal).coerceIn(-14, 14)
    val rawTrustDelta = (signalTrust + softTrustDelta + reciprocalWarmth + rapidRomanceMomentum + (acceleratedRomanceBoost / 2) - conflictSignal).coerceIn(-11, 11)
    val rawTensionDelta = (signalTension + softTensionDelta + conflictSignal - reciprocalWarmth).coerceIn(-8, 8)

    val affectionDelta = stabilizeRelationshipDelta(previous.affection, rawAffectionDelta, strongPositive, strongNegative, previous.intimacyBaseline)
    val trustDelta = stabilizeRelationshipDelta(previous.trust, rawTrustDelta, strongPositive, strongNegative, anchorProfile.trustBaseline)
    val tensionDelta = stabilizeTensionDelta(previous.tension, rawTensionDelta, strongPositive, strongNegative)

    val momentum = updateRelationshipMomentum(previous.relationshipMomentum, signalMomentum + acceleratedRomanceBoost + rapidRomanceMomentum - conflictSignal, strongPositive, strongNegative)
    val affectionFloor = if (baselineProtection > 0 && previous.intimacyBaseline > 0) previous.intimacyBaseline / 2 else -100
    val affection = maxOf(affectionFloor, (previous.affection + affectionDelta).coerceIn(-100, 100))
    val trust = (previous.trust + trustDelta).coerceIn(-100, 100)
    val tension = (previous.tension + tensionDelta).coerceIn(0, 100)
    val relationshipStage = relationshipStageLabel(affection, trust, tension, previous.relationshipAnchor, previous.intimacyBaseline, momentum)
    val mood = statusNoteLabel(affection, trust, tension, assistantWarmScore, assistantColdScore, userHostileScore)
    val progress = progressNoteLabel(rounds, relationshipStage, momentum)
    val recentEventSummary = relationshipSummary(relationshipSignals, userWarmScore, userHostileScore, assistantWarmScore, assistantColdScore, previous.relationshipAnchor)

    val next = CharacterStoryState(
        relationshipAnchor = previous.relationshipAnchor,
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
        append(" 关系锚点：${previous.relationshipAnchor}。")
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
    userHostileScore: Int
): String {
    val acceptance = (100 - tension).coerceIn(0, 100)
    return when {
        affection <= -45 || userHostileScore >= 2 -> "明显生气或抗拒"
        acceptance <= 20 -> "明显不接受靠近"
        assistantColdScore > assistantWarmScore -> "态度转冷"
        affection >= 60 && trust >= 50 && acceptance >= 65 -> "明显亲近"
        trust >= 40 && acceptance >= 55 -> "开始愿意接纳与交心"
        else -> "情绪仍在波动"
    }
}

private fun scoreKeywordHits(text: String, keywords: List<String>): Int {
    return keywords.sumOf { keyword ->
        Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE).findAll(text).count()
    }
}

private fun collectRelationshipSignals(
    userText: String,
    assistantText: String,
    assistantInnerThought: String
): List<RelationshipSignal> {
    val assistantCombined = listOf(assistantText, assistantInnerThought)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    val signals = mutableListOf<RelationshipSignal>()

    classifyInteractionEvent(userText)?.let { eventType ->
        classifyInteractionResponse(eventType, assistantCombined)?.let { responseType ->
            buildInteractionSignal(eventType, responseType)?.let { signals += it }
        }
    }
    if (containsAny(assistantCombined, listOf("怀疑你", "不信你", "骗我", "利用我"))) {
        signals += RelationshipSignal("角色明显起疑", affection = -2, trust = -4, tension = 3, momentum = -1)
    }
    if (containsAny(assistantCombined, listOf("保护你", "挡在你前面", "先顾你", "陪你去", "我来承担"))) {
        signals += RelationshipSignal("角色主动承担或保护", affection = 3, trust = 2, tension = -1, momentum = 2, strongPositive = true)
    }
    if (containsAny(assistantCombined, listOf("保持距离", "先这样", "别再往前", "我还不能信你"))) {
        signals += RelationshipSignal("角色主动设下边界", affection = -1, trust = -1, tension = 2, momentum = -1)
    }

    return signals
}

private fun classifyInteractionEvent(userText: String): InteractionEventType? {
    return when {
        containsAny(userText, listOf("威胁", "杀了", "弄死", "伤害你", "报复你")) -> InteractionEventType.THREAT
        containsAny(userText, listOf("讨厌你", "滚", "闭嘴", "离我远点", "别碰我")) -> InteractionEventType.CONFLICT
        containsAny(userText, listOf("做我女朋友", "做我男朋友", "嫁给我", "和我在一起", "我们交往吧", "交往", "告白")) -> InteractionEventType.COMMITMENT
        containsAny(userText, listOf("喜欢你", "我爱你", "想和你在一起", "你对我很重要")) -> InteractionEventType.CONFESSION
        containsAny(userText, listOf("亲", "吻", "抱", "贴", "牵手", "蹭", "摸头")) -> InteractionEventType.PHYSICAL_INTIMACY
        containsAny(userText, listOf("谢谢", "辛苦了", "你帮了我", "多亏你")) -> InteractionEventType.GRATITUDE
        containsAny(userText, listOf("相信你", "告诉我", "你可以依赖我", "我不会离开")) -> InteractionEventType.VULNERABILITY
        containsAny(userText, listOf("我会陪着你", "我陪你", "我会保护你", "别怕", "有我在")) -> InteractionEventType.SUPPORT
        containsAny(userText, listOf("对不起", "抱歉", "是我不好", "我错了")) -> InteractionEventType.APOLOGY_REPAIR
        else -> null
    }
}

private fun classifyInteractionResponse(
    eventType: InteractionEventType,
    assistantCombined: String
): InteractionResponseType? {
    val activeAccept = containsAny(assistantCombined, listOf(
        "回吻", "追上来亲", "抱紧", "主动贴上来", "揪住你", "顺势亲回去", "我也", "我愿意", "答应你",
        "靠过来", "抱住", "贴近", "让你亲", "谢谢", "安心", "那就拜托你了", "我相信你", "能帮到你就好"
    ))
    val shyAccept = containsAny(assistantCombined, listOf(
        "耳尖发烫", "脸红", "呼吸一滞", "心跳乱了", "害羞", "睫毛颤了颤", "脸一下热了", "脑子一片空白",
        "尾巴炸开", "手足无措", "不知所措"
    ))
    val passiveAccept = containsAny(assistantCombined, listOf(
        "没躲", "没有躲", "没有推开", "没推开", "愣住", "僵住", "默认", "没有拒绝", "不反抗", "默许",
        "顺从", "任由你亲", "任你抱着", "算了", "我接受", "原谅", "没关系", "好", "嗯"
    ))
    val lightDiscomfort = containsAny(assistantCombined, listOf(
        "后退", "退了一步", "僵硬", "别闹", "不自在", "有点慌", "避开目光", "下意识躲"
    ))
    val explicitReject = containsAny(assistantCombined, listOf(
        "推开", "躲开", "别这样", "不可以", "住手", "抗拒", "拒绝", "生气", "保持距离", "我还不能信你"
    ))
    val strongReject = containsAny(assistantCombined, listOf(
        "恶心", "厌恶", "滚开", "别碰我", "你有病", "愤怒", "反胃", "嫌恶", "警告", "走开"
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
            InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("冲突后出现修复", affection = 3, trust = 4, tension = -3, momentum = 1, strongPositive = true)
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
            InteractionResponseType.ACTIVE_ACCEPT -> RelationshipSignal("感情表达得到回应", affection = 5, trust = 3, tension = -2, momentum = 4, strongPositive = true)
            InteractionResponseType.SHY_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("感情表达被接住", affection = 4, trust = 2, tension = -1, momentum = 3, strongPositive = true)
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
            InteractionResponseType.ACTIVE_ACCEPT, InteractionResponseType.PASSIVE_ACCEPT -> RelationshipSignal("关系被明确推进", affection = 8, trust = 5, tension = -3, momentum = 8, strongPositive = true)
            InteractionResponseType.SHY_ACCEPT -> RelationshipSignal("关系推进被羞涩接纳", affection = 6, trust = 4, tension = -1, momentum = 6, strongPositive = true)
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
