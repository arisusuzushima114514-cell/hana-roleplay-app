package com.hana.app.data.api.models

data class StreamDelta(
    val content: String = "",
    val reasoningContent: String = ""
)

data class StreamResult(
    val content: String,
    val reasoningContent: String,
    val totalTokens: Int? = null,
    val finishReason: String? = null
)

class IncompleteStreamException(
    val partialResult: StreamResult
) : Exception("流式响应未收到 [DONE]，回复可能不完整")

class UpstreamContentBlockedException(
    val reason: String,
    val partialResult: StreamResult? = null
) : Exception("上游模型或服务商已拦截本次内容：$reason")

class OutputTruncatedException(
    val partialResult: StreamResult
) : Exception("模型达到输出长度上限，回复未完整结束")
