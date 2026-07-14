package com.hana.app.ui.settings

data class WebSearchSupport(
    val supported: Boolean,
    val label: String
)

fun detectNativeWebSearchSupport(
    modelName: String = "",
    providerName: String = "",
    baseUrl: String = ""
): WebSearchSupport {
    val model = modelName.lowercase()
    val provider = providerName.lowercase()
    val url = baseUrl.lowercase()

    val supportedModelPatterns = listOf(
        "grok", "sonar", "search", "ground", "web", "gemini", "claude", "perplexity"
    )
    val unsupportedModelPatterns = listOf(
        "embedding", "embed", "rerank", "tts", "asr", "whisper", "moderation", "sd", "flux", "image"
    )

    val supportedProviderPatterns = listOf(
        "perplexity", "gemini", "google", "grok", "xai", "claude", "anthropic"
    )
    val unsupportedProviderPatterns = listOf(
        "deepseek", "siliconflow", "openrouter", "oneapi", "newapi", "openai-compatible", "aliyun", "volces"
    )

    val modelSupported = supportedModelPatterns.any { model.contains(it) } && unsupportedModelPatterns.none { model.contains(it) }
    val providerSupported = supportedProviderPatterns.any { provider.contains(it) || url.contains(it) }
    val providerLikelyUnsupported = unsupportedProviderPatterns.any { provider.contains(it) || url.contains(it) }

    return when {
        modelSupported || providerSupported -> WebSearchSupport(true, "支持原生联网搜索")
        providerLikelyUnsupported -> WebSearchSupport(false, "不支持原生联网搜索")
        else -> WebSearchSupport(false, "未识别原生联网搜索")
    }
}
