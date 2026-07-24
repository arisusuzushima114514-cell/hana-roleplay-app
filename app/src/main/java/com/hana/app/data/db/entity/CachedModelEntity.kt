package com.hana.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_models",
    indices = [Index("baseUrl")]
)
@androidx.compose.runtime.Stable
data class CachedModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val isFavorite: Boolean = false,
    val capabilities: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)

@androidx.compose.runtime.Stable
data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String = "",
    val isFavorite: Boolean = false,
    val capabilities: Set<Capability> = emptySet()
)

enum class Capability(val label: String, val emoji: String) {
    REASONING("思考", "\u2699"),   // ⚙️
    VISION("视觉", "\uD83D\uDC41"), // 👁
    TOOLS("工具", "\uD83D\uDD27");  // 🔧

    companion object {
        fun fromString(str: String): Set<Capability> {
            return str.split(",").mapNotNull { s ->
                entries.firstOrNull { it.name.equals(s.trim(), ignoreCase = true) }
            }.toSet()
        }

        fun toString(caps: Set<Capability>): String {
            return caps.joinToString(",") { it.name }
        }
    }
}

object ModelCapabilityMap {
    private val exactMap = mapOf(
        "grok-4.5" to setOf(Capability.VISION, Capability.REASONING, Capability.TOOLS),
        "grok-4" to setOf(Capability.REASONING, Capability.TOOLS),
        "grok-3" to setOf(Capability.REASONING, Capability.TOOLS),
        "gpt-4o" to setOf(Capability.VISION, Capability.REASONING, Capability.TOOLS),
        "gpt-4o-mini" to setOf(Capability.VISION, Capability.TOOLS),
        "gpt-4-turbo" to setOf(Capability.VISION, Capability.TOOLS),
        "gpt-4-vision" to setOf(Capability.VISION, Capability.TOOLS),
        "gpt-4" to setOf(Capability.TOOLS),
        "gpt-3.5-turbo" to setOf(Capability.TOOLS),
        "claude-3.5-sonnet" to setOf(Capability.VISION, Capability.REASONING, Capability.TOOLS),
        "claude-3-opus" to setOf(Capability.VISION, Capability.TOOLS),
        "claude-3-haiku" to setOf(Capability.VISION, Capability.TOOLS),
        "gemini-2.5-pro" to setOf(Capability.VISION, Capability.REASONING, Capability.TOOLS),
        "gemini-2.5-flash" to setOf(Capability.VISION, Capability.TOOLS),
        "gemini-2.0-flash" to setOf(Capability.VISION, Capability.TOOLS),
        "gemini-1.5-pro" to setOf(Capability.VISION, Capability.TOOLS),
        "deepseek-chat" to setOf(Capability.TOOLS),
        "deepseek-reasoner" to setOf(Capability.REASONING, Capability.TOOLS),
        "deepseek-v3" to setOf(Capability.TOOLS),
        "qwen-vl-max" to setOf(Capability.VISION, Capability.TOOLS),
        "qwen-max" to setOf(Capability.TOOLS),
        "qwen-plus" to setOf(Capability.TOOLS),
        "glm-4v" to setOf(Capability.VISION, Capability.TOOLS),
        "glm-4" to setOf(Capability.TOOLS),
        "yi-vision" to setOf(Capability.VISION, Capability.TOOLS),
        "yi-large" to setOf(Capability.TOOLS),
    )

    private val visionPatterns = listOf(
        "vision", "visual", "-vl",
        "gpt-4o", "gpt-4.", "gpt-4-",
        "claude-3", "claude-opus", "claude-sonnet", "claude-haiku", "claude-4",
        "gemini-2", "gemini-1.5",
        "grok-4.5", "grok-vision",
        "pixtral", "llava", "cogvlm", "minicpm-v", "internvl",
        "qwen-vl", "qwen2-vl", "step-1v"
    )

    private val reasoningPatterns = listOf(
        "reasoner", "reasoning", "thinking",
        "o1-", "o3-", "o4-",
        "deepseek-r1", "grok-3"
    )

    fun get(modelId: String): Set<Capability> {
        val lower = modelId.lowercase().trim()
        for ((key, value) in exactMap) {
            if (lower.contains(key)) return value
        }

        val caps = mutableSetOf<Capability>()

        if (visionPatterns.any { lower.contains(it) }) {
            caps.add(Capability.VISION)
        }

        if (reasoningPatterns.any { lower.contains(it) }) {
            caps.add(Capability.REASONING)
        }

        caps.add(Capability.TOOLS)
        return caps
    }
}

fun guessProvider(modelName: String, baseUrl: String): String {
    val lower = modelName.lowercase()
    val url = baseUrl.lowercase()
    if (url.contains("openai") || lower.startsWith("gpt") || lower.startsWith("o1") || lower.startsWith("o3")) return "OpenAI"
    if (url.contains("anthropic") || lower.startsWith("claude")) return "Anthropic"
    if (url.contains("deepseek")) return "DeepSeek"
    if (url.contains("google") || url.contains("generativelanguage") || lower.startsWith("gemini")) return "Google"
    if (url.contains("siliconflow") || url.contains("silicon")) return "SiliconFlow"
    if (url.contains("dashscope") || url.contains("aliyun") || lower.startsWith("qwen")) return "Alibaba"
    if (url.contains("zhipu") || lower.startsWith("glm")) return "Zhipu"
    if (url.contains("x.ai") || url.contains("xai") || lower.startsWith("grok")) return "xAI"
    if (lower.startsWith("yi-")) return "01.AI"
    return "其他"
}
