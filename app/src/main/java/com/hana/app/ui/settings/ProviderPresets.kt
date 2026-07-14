package com.hana.app.ui.settings

import androidx.compose.ui.graphics.Color

data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val hintModel: String,
    val accent: Color,
    val description: String
)

val ProviderPresets = listOf(
    ProviderPreset(
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        hintModel = "gpt-4o / gpt-image-1",
        accent = Color(0xFF10A37F),
        description = "通用能力强，适合文字、图片与多模态。"
    ),
    ProviderPreset(
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        hintModel = "deepseek-chat / deepseek-reasoner",
        accent = Color(0xFF4F6BFF),
        description = "中文推理强，适合作为主对话模型。"
    ),
    ProviderPreset(
        name = "DashScope",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        hintModel = "qwen-max / qwen-vl-max",
        accent = Color(0xFFF59E0B),
        description = "千问生态完整，适合文字与视觉模型组合。"
    ),
    ProviderPreset(
        name = "SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1",
        hintModel = "deepseek-v3 / qwen2-vl",
        accent = Color(0xFF7C3AED),
        description = "中转站常用，模型覆盖面广。"
    ),
    ProviderPreset(
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        hintModel = "openai / anthropic / google 路线模型",
        accent = Color(0xFF2563EB),
        description = "统一聚合多个模型来源，便于快速切换。"
    ),
    ProviderPreset(
        name = "Anthropic",
        baseUrl = "https://api.anthropic.com/v1",
        hintModel = "claude-3.5-sonnet",
        accent = Color(0xFFB45309),
        description = "Claude 路线，长文本和多模态体验稳定。"
    ),
    ProviderPreset(
        name = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        hintModel = "gemini-2.5-pro / gemini-2.5-flash",
        accent = Color(0xFF0EA5E9),
        description = "Google 路线，多模态能力较完整。"
    ),
    ProviderPreset(
        name = "自定义",
        baseUrl = "",
        hintModel = "你自己的中转站 / 私有部署",
        accent = Color(0xFF64748B),
        description = "手动填写兼容 OpenAI 的地址与密钥。"
    )
)
