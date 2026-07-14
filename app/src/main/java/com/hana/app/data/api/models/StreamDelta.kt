package com.hana.app.data.api.models

data class StreamDelta(
    val content: String = "",
    val reasoningContent: String = ""
)

data class StreamResult(
    val content: String,
    val reasoningContent: String,
    val totalTokens: Int? = null
)
