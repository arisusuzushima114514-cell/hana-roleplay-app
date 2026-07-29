package com.hana.app.data.settings

data class ApiSettings(
    val baseUrl: String = DEFAULT_API_BASE_URL,
    val apiKey: String = "",
    val modelName: String = DEFAULT_MODEL_NAME,
    val isVisionConfig: Boolean = false
)

const val DEFAULT_API_BASE_URL = "https://api.deepseek.com/v1"
const val DEFAULT_MODEL_NAME = "deepseek-chat"
const val DEFAULT_AUTO_SUMMARY_THRESHOLD = 24

/** Keeps OpenAI-compatible provider roots in the form expected by the chat endpoints. */
fun normalizeApiBaseUrl(value: String): String {
    val normalized = value.trim().trimEnd('/')
    if (normalized.isBlank()) return ""
    return if (Regex("/v1$", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) {
        normalized
    } else {
        "$normalized/v1"
    }
}
