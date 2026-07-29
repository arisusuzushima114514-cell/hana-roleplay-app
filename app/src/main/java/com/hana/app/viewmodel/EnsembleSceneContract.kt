package com.hana.app.viewmodel

import com.hana.app.data.db.entity.SubCharacterProfile
import org.json.JSONArray
import org.json.JSONObject

internal const val SCENE_PARTICIPATION_PUBLIC = "public"
internal const val SCENE_PARTICIPATION_INNER_ONLY = "inner-only"
internal const val SCENE_PARTICIPATION_UNAVAILABLE = "unavailable"

data class SceneRoleState(
    val roleId: String,
    val name: String,
    val participation: String,
    val status: String = "active",
    val context: String = ""
)

data class EnsembleCoverageBudget(
    val availableTokens: Int,
    val reservedNarrativeTokens: Int,
    val requiredTokens: Int,
    val isTight: Boolean
)

data class EnsembleCoverageResult(
    val expectedObligations: Int,
    val completedObligations: Int,
    val missingObligations: List<String>
) {
    val isComplete: Boolean get() = missingObligations.isEmpty()
}

data class EnsembleCoverageDiagnostic(
    val expectedObligations: Int,
    val completedObligations: Int,
    val missingObligations: List<String>,
    val availableTokens: Int,
    val requiredTokens: Int,
    val checkedAt: Long,
    val repairAttempted: Boolean = false,
    val repairSucceeded: Boolean = false,
    val repairRequestFailed: Boolean = false
) {
    val isComplete: Boolean get() = missingObligations.isEmpty()
}

internal enum class EnsembleRepairOutcome {
    FIRST_DRAFT_ACCEPTED,
    REPAIR_ACCEPTED,
    REPAIR_INCOMPLETE,
    REPAIR_REQUEST_FAILED
}

/**
 * Stable, scene-level facts for structured multi-character cards.  This layer never infers
 * unavailability from a generated reply: only explicit card/scene facts may exclude a role.
 */
internal object EnsembleSceneContract {
    fun initialStates(profiles: List<SubCharacterProfile>): List<SceneRoleState> = profiles.map { profile ->
        val description = "${profile.description}\n${profile.greeting}"
        val participation = when {
            containsUnavailableFact(description) -> SCENE_PARTICIPATION_UNAVAILABLE
            containsInnerOnlyFact(description) -> SCENE_PARTICIPATION_INNER_ONLY
            else -> SCENE_PARTICIPATION_PUBLIC
        }
        SceneRoleState(
            roleId = profile.id,
            name = profile.name.trim(),
            participation = participation,
            status = if (participation == SCENE_PARTICIPATION_UNAVAILABLE) "unavailable" else "active",
            context = description.trim().take(240)
        )
    }.filter { it.name.isNotBlank() }

    fun parse(raw: String): List<SceneRoleState> = runCatching {
        val root = JSONObject(raw)
        val roles = root.optJSONArray("roles") ?: JSONArray()
        (0 until roles.length()).mapNotNull { index ->
            val item = roles.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            val participation = item.optString("participation").takeIf {
                it in setOf(SCENE_PARTICIPATION_PUBLIC, SCENE_PARTICIPATION_INNER_ONLY, SCENE_PARTICIPATION_UNAVAILABLE)
            } ?: SCENE_PARTICIPATION_PUBLIC
            SceneRoleState(
                roleId = item.optString("roleId").trim().ifBlank { name },
                name = name,
                participation = participation,
                status = item.optString("status").trim().ifBlank { "active" },
                context = item.optString("context").trim()
            )
        }
    }.getOrDefault(emptyList())

    fun serialize(states: List<SceneRoleState>): String = JSONObject().apply {
        put("version", 1)
        put("roles", JSONArray().apply {
            states.forEach { state ->
                put(JSONObject().apply {
                    put("roleId", state.roleId)
                    put("name", state.name)
                    put("participation", state.participation)
                    put("status", state.status)
                    put("context", state.context)
                })
            }
        })
    }.toString()

    fun resolveStates(raw: String, profiles: List<SubCharacterProfile>): List<SceneRoleState> {
        val saved = parse(raw)
        if (saved.isEmpty()) return initialStates(profiles)
        val savedById = saved.associateBy { it.roleId }
        return profiles.map { profile ->
            savedById[profile.id]
                ?: saved.firstOrNull { it.name.equals(profile.name, ignoreCase = true) }
                ?: initialStates(listOf(profile)).first()
        }
    }

    fun budget(maxOutputTokens: Int, states: List<SceneRoleState>): EnsembleCoverageBudget {
        val available = maxOutputTokens.coerceAtLeast(1)
        val publicCount = states.count { it.participation == SCENE_PARTICIPATION_PUBLIC }
        val innerCount = states.count { it.participation != SCENE_PARTICIPATION_UNAVAILABLE }
        val narrative = 180
        val required = narrative + publicCount * 56 + innerCount * 36
        return EnsembleCoverageBudget(
            availableTokens = available,
            reservedNarrativeTokens = narrative,
            requiredTokens = required,
            isTight = available < required
        )
    }

    fun evaluate(content: String, states: List<SceneRoleState>): EnsembleCoverageResult {
        val publicText = ChatMessageBuilder.publicGroupContent(content)
        val missing = buildList {
            states.filter { it.participation == SCENE_PARTICIPATION_PUBLIC }.forEach { role ->
                if (!hasPublicParticipation(publicText, role.name)) add("${role.name} 缺少公开表达")
            }
            states.filter { it.participation != SCENE_PARTICIPATION_UNAVAILABLE }.forEach { role ->
                if (!hasNamedInner(content, role.name)) add("${role.name} 缺少具名inner")
            }
        }
        val expected = states.count { it.participation == SCENE_PARTICIPATION_PUBLIC } +
            states.count { it.participation != SCENE_PARTICIPATION_UNAVAILABLE }
        return EnsembleCoverageResult(expected, expected - missing.size, missing)
    }

    fun diagnostic(
        result: EnsembleCoverageResult,
        budget: EnsembleCoverageBudget,
        checkedAt: Long,
        repairAttempted: Boolean = false,
        repairSucceeded: Boolean = false,
        repairRequestFailed: Boolean = false
    ): EnsembleCoverageDiagnostic =
        EnsembleCoverageDiagnostic(
            expectedObligations = result.expectedObligations,
            completedObligations = result.completedObligations,
            missingObligations = result.missingObligations,
            availableTokens = budget.availableTokens,
            requiredTokens = budget.requiredTokens,
            checkedAt = checkedAt,
            repairAttempted = repairAttempted,
            repairSucceeded = repairSucceeded,
            repairRequestFailed = repairRequestFailed
        )

    fun serializeDiagnostic(diagnostic: EnsembleCoverageDiagnostic): String = JSONObject().apply {
        put("version", 1)
        put("expectedObligations", diagnostic.expectedObligations)
        put("completedObligations", diagnostic.completedObligations)
        put("missingObligations", JSONArray(diagnostic.missingObligations))
        put("availableTokens", diagnostic.availableTokens)
        put("requiredTokens", diagnostic.requiredTokens)
        put("checkedAt", diagnostic.checkedAt)
        put("repairAttempted", diagnostic.repairAttempted)
        put("repairSucceeded", diagnostic.repairSucceeded)
        put("repairRequestFailed", diagnostic.repairRequestFailed)
    }.toString()

    fun parseDiagnostic(raw: String): EnsembleCoverageDiagnostic? = runCatching {
        val root = JSONObject(raw)
        val expected = root.optInt("expectedObligations", -1)
        if (expected < 0) return@runCatching null
        val missing = root.optJSONArray("missingObligations") ?: JSONArray()
        EnsembleCoverageDiagnostic(
            expectedObligations = expected,
            completedObligations = root.optInt("completedObligations", 0),
            missingObligations = (0 until missing.length()).mapNotNull { missing.optString(it).takeIf(String::isNotBlank) },
            availableTokens = root.optInt("availableTokens", 0),
            requiredTokens = root.optInt("requiredTokens", 0),
            checkedAt = root.optLong("checkedAt", 0L),
            repairAttempted = root.optBoolean("repairAttempted", false),
            repairSucceeded = root.optBoolean("repairSucceeded", false),
            repairRequestFailed = root.optBoolean("repairRequestFailed", false)
        )
    }.getOrNull()

    fun buildPromptLayer(states: List<SceneRoleState>, budget: EnsembleCoverageBudget): String {
        if (states.isEmpty()) return ""
        return buildString {
            append("【当前场景角色契约】\n")
            states.forEach { state ->
                append("${state.name}=${state.participation}/${state.status}")
                if (state.context.isNotBlank()) append("（${state.context.take(80)}）")
                append("；")
            }
            append("\npublic角色本轮须有自然的公开台词或可见动作/反应，并各有独立具名inner；inner-only角色须有独立具名inner，意识交流不得自动成为他人可听见的公开事实。unavailable角色不强行参与。")
            append("模型漏写不能改变上述状态；只有用户或明确剧情事实才能把角色改为不可参与。")
            if (budget.isTight) {
                append("\n输出预算紧张（最低覆盖约${budget.requiredTokens} tokens，可用${budget.availableTokens}）：压缩环境描写、重复动作和单人长台词，保留每位参与角色的最低参与与inner，不得用删角色换篇幅。")
            }
        }
    }

    fun buildRepairProtocol(missingObligations: List<String>): String = buildString {
        append("【群像覆盖修复·内部指令】完整重写本轮连续小说，只输出重写后的正文。")
        append("不解释、不道歉、不提遗漏、验收、规则、检查或修复；不改变用户给出的剧情、既有事实、叙事视角和节奏。")
        append("补足以下义务：${missingObligations.joinToString("；")}。公开参与保持自然小说表达，不能写成点名册；意识角色保持意识层，除非设定明确允许，否则其他人不可听见。")
    }

    fun repairOutcome(
        firstCoverage: EnsembleCoverageResult,
        repairRequestSucceeded: Boolean? = null,
        repairCoverage: EnsembleCoverageResult? = null
    ): EnsembleRepairOutcome = when {
        firstCoverage.isComplete -> EnsembleRepairOutcome.FIRST_DRAFT_ACCEPTED
        repairRequestSucceeded != true -> EnsembleRepairOutcome.REPAIR_REQUEST_FAILED
        repairCoverage?.isComplete == true -> EnsembleRepairOutcome.REPAIR_ACCEPTED
        else -> EnsembleRepairOutcome.REPAIR_INCOMPLETE
    }

    private fun hasPublicParticipation(publicText: String, name: String): Boolean {
        val quotedSpeech = Regex("(?m)^\\s*(?:[【\\[]\\s*)?${Regex.escape(name)}(?:\\s*[】\\]])?\\s*[：:]")
        if (quotedSpeech.containsMatchIn(publicText)) return true
        // A named visible action is valid participation even when the character naturally stays silent.
        val action = Regex("${Regex.escape(name)}.{0,32}(?:点头|摇头|抬眼|看向|转身|上前|后退|伸手|皱眉|微笑|沉默|挡住|走近|停下|侧过)")
        return action.containsMatchIn(publicText)
    }

    private fun hasNamedInner(content: String, name: String): Boolean = Regex(
        "<inner\\s+character\\s*=\\s*['\\\"]${Regex.escape(name)}['\\\"]\\s*>.+?</inner>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).containsMatchIn(content)

    private fun containsUnavailableFact(text: String): Boolean = listOf(
        "离场", "已离开", "沉睡", "休眠", "封印", "死亡", "断联", "链接中断", "无法回应", "被隔绝"
    ).any(text::contains)

    private fun containsInnerOnlyFact(text: String): Boolean = listOf(
        "寄宿", "体内", "意识空间", "精神空间", "精神链接", "灵魂绑定", "共享感官", "附身", "共生"
    ).any(text::contains)
}
