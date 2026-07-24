package com.hana.app.ui.settings

import androidx.compose.ui.graphics.Color

enum class ProviderPresetGroup(val label: String) {
    OFFICIAL("官方服务"),
    AGGREGATOR("聚合平台"),
    CUSTOM("自定义")
}

data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val recommendedModels: List<String>,
    val accent: Color,
    val description: String,
    val group: ProviderPresetGroup,
    val protocol: String = "OpenAI Compatible",
    val note: String = ""
) {
    val hintModel: String get() = recommendedModels.joinToString(" / ")
}

val ProviderPresets = listOf(
    ProviderPreset(
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        recommendedModels = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o-mini"),
        accent = Color(0xFF10A37F),
        description = "OpenAI 官方接口，适合通用对话、工具调用与多模态任务。",
        group = ProviderPresetGroup.OFFICIAL
    ),
    ProviderPreset(
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        recommendedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        accent = Color(0xFF4F6BFF),
        description = "DeepSeek 官方兼容接口，中文对话和推理能力稳定。",
        group = ProviderPresetGroup.OFFICIAL
    ),
    ProviderPreset(
        name = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        recommendedModels = listOf("gemini-2.5-flash", "gemini-2.5-pro"),
        accent = Color(0xFF4285F4),
        description = "Google 官方 OpenAI 兼容端点，支持 Gemini 文本与多模态模型。",
        group = ProviderPresetGroup.OFFICIAL,
        note = "需要 Google AI Studio API Key。"
    ),
    ProviderPreset(
        name = "阿里云百炼",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        recommendedModels = listOf("qwen-plus", "qwen-max", "qwen-vl-max"),
        accent = Color(0xFFFF6A00),
        description = "阿里云百炼官方兼容模式，覆盖通义千问文本与视觉模型。",
        group = ProviderPresetGroup.OFFICIAL
    ),
    ProviderPreset(
        name = "xAI",
        baseUrl = "https://api.x.ai/v1",
        recommendedModels = listOf("grok-3", "grok-3-mini"),
        accent = Color(0xFF111827),
        description = "xAI 官方 OpenAI 兼容接口，适合 Grok 系列模型。",
        group = ProviderPresetGroup.OFFICIAL
    ),
    ProviderPreset(
        name = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1",
        recommendedModels = listOf("mistral-large-latest", "mistral-small-latest"),
        accent = Color(0xFFF97316),
        description = "Mistral 官方接口，兼容 OpenAI Chat Completions。",
        group = ProviderPresetGroup.OFFICIAL
    ),
    ProviderPreset(
        name = "Moonshot AI",
        baseUrl = "https://api.moonshot.cn/v1",
        recommendedModels = listOf("moonshot-v1-8k", "moonshot-v1-32k"),
        accent = Color(0xFF111827),
        description = "Moonshot 官方兼容接口，适合中文长文本和 Kimi 系列模型。",
        group = ProviderPresetGroup.OFFICIAL
    ),
    ProviderPreset(
        name = "智谱 BigModel",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        recommendedModels = listOf("glm-4-plus", "glm-4-flash"),
        accent = Color(0xFF2563EB),
        description = "智谱官方 OpenAI 兼容接口，适合 GLM 系列模型。",
        group = ProviderPresetGroup.OFFICIAL
    ),
    ProviderPreset(
        name = "SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1",
        recommendedModels = listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-VL-72B-Instruct"),
        accent = Color(0xFF7C3AED),
        description = "模型聚合平台，覆盖多种开源文本、推理和视觉模型。",
        group = ProviderPresetGroup.AGGREGATOR,
        note = "模型 ID 通常包含组织名前缀，请优先拉取模型列表。"
    ),
    ProviderPreset(
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        recommendedModels = listOf("openai/gpt-4.1-mini", "google/gemini-2.5-flash", "anthropic/claude-sonnet-4"),
        accent = Color(0xFF6366F1),
        description = "统一聚合多个厂商模型，适合在一个 Key 下快速切换。",
        group = ProviderPresetGroup.AGGREGATOR,
        note = "模型 ID 带厂商前缀；Claude 官方原生接口不兼容本应用，可通过这里使用。"
    ),
    ProviderPreset(
        name = "自定义 OpenAI 兼容",
        baseUrl = "",
        recommendedModels = emptyList(),
        accent = Color(0xFF64748B),
        description = "适用于 OneAPI、NewAPI、LiteLLM、私有部署或其他兼容服务。",
        group = ProviderPresetGroup.CUSTOM,
        note = "Base URL 应能直接拼接 /models 与 /chat/completions。"
    )
)
