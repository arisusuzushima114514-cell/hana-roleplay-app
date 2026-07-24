package com.hana.app.data.api

import com.hana.app.data.api.models.StreamDelta
import com.hana.app.data.api.models.IncompleteStreamException
import com.hana.app.data.api.models.StreamResult
import com.hana.app.data.api.models.UpstreamContentBlockedException
import com.hana.app.data.api.models.OutputTruncatedException
import com.hana.app.data.repository.ModelRepository
import com.hana.app.data.settings.ApiSettings
import com.hana.app.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ApiService(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
    private val okHttpClient: OkHttpClient = defaultClient()
) {
    private val activeCall = AtomicReference<okhttp3.Call?>(null)

    data class ChatPayload(
        val role: String,
        val text: String,
        val imageDataUrls: List<String> = emptyList(),
        val fileTexts: List<String> = emptyList()
    )

    suspend fun streamChat(
        messages: List<ChatPayload>,
        model: String? = null,
        useVisionConfig: Boolean = false,
        temperature: Float = 0.7f,
        topP: Float = 1f,
        maxTokens: Int = 4096,
        timeoutSeconds: Long = 60L,
        webSearch: Boolean = false,
        onDelta: suspend (StreamDelta) -> Unit
    ): Result<StreamResult> = withContext(Dispatchers.IO) {
        var call: okhttp3.Call? = null
        try {
            val settings = resolveApiSettings(useVisionConfig = useVisionConfig, preferredModel = model)
            require(settings.baseUrl.isNotBlank()) { "Base URL is required" }
            require(settings.apiKey.isNotBlank()) { "API Key is required" }
            val modelName = model?.takeIf { it.isNotBlank() } ?: settings.modelName
            require(modelName.isNotBlank()) { "Model is required" }

            val request = Request.Builder()
                .url("${settings.baseUrl.trim().trimEnd('/')}/chat/completions")
                .post(
                    buildRequestBody(
                        model = modelName,
                        messages = messages,
                        stream = true,
                        temperature = temperature,
                        topP = topP,
                        maxTokens = maxTokens,
                        webSearch = webSearch
                    )
                )
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey.trim()}")
                .build()

            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var totalTokens: Int? = null
            val client = okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout((timeoutSeconds * 2).coerceAtLeast(120), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout((timeoutSeconds * 2).coerceAtLeast(180), TimeUnit.SECONDS)
                .build()
            call = client.newCall(request)
            activeCall.set(call)
            var receivedDone = false
            var finishReason: String? = null
            val pendingContent = StringBuilder()
            val pendingReasoning = StringBuilder()
            var lastUiFlushAt = System.nanoTime()

            suspend fun flushPendingDelta() {
                if (pendingContent.isEmpty() && pendingReasoning.isEmpty()) return
                val pending = StreamDelta(
                    content = pendingContent.toString(),
                    reasoningContent = pendingReasoning.toString()
                )
                pendingContent.setLength(0)
                pendingReasoning.setLength(0)
                lastUiFlushAt = System.nanoTime()
                try { onDelta(pending) } catch (_: Exception) { /* ignore UI thread errors */ }
            }

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw httpFailure(response.code, errorBody.ifBlank { response.message })
                }

                val body = response.body ?: error("Empty response body")
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.removePrefix("data:").trim() == "[DONE]") {
                            receivedDone = true
                            continue
                        }
                        parseFinishReason(line)?.let { finishReason = it }
                        parseUsage(line)?.let { totalTokens = it }
                        val delta = parseSseChunk(line) ?: continue

                        if (delta.reasoningContent.isNotBlank()) {
                            reasoningBuilder.append(delta.reasoningContent)
                            pendingReasoning.append(delta.reasoningContent)
                        }
                        if (delta.content.isNotBlank()) {
                            contentBuilder.append(delta.content)
                            pendingContent.append(delta.content)
                        }
                        val elapsedMs = (System.nanoTime() - lastUiFlushAt) / 1_000_000L
                        if (elapsedMs >= 32L || pendingContent.length + pendingReasoning.length >= 256) {
                            flushPendingDelta()
                        }
                    }
                    flushPendingDelta()
                }
            }

            val result = StreamResult(
                content = contentBuilder.toString(),
                reasoningContent = reasoningBuilder.toString(),
                totalTokens = totalTokens,
                finishReason = finishReason
            )
            if (isBlockedFinishReason(finishReason)) {
                Result.failure(UpstreamContentBlockedException(finishReason.orEmpty(), result))
            } else if (isLengthFinishReason(finishReason)) {
                Result.failure(OutputTruncatedException(result))
            } else if (!receivedDone && finishReason == null) {
                Result.failure(IncompleteStreamException(result))
            } else {
                Result.success(result)
            }
        } catch (cancelled: CancellationException) {
            Result.failure(cancelled)
        } catch (timeout: SocketTimeoutException) {
            Result.failure(Exception("请求超时，请稍后重试"))
        } catch (interrupted: InterruptedIOException) {
            Result.failure(CancellationException("Request cancelled"))
        } catch (connect: ConnectException) {
            Result.failure(Exception("连接失败，请检查网络或 API 地址"))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        } finally {
            call?.let { activeCall.compareAndSet(it, null) }
        }
    }

    suspend fun chat(
        messages: List<ChatPayload>,
        model: String? = null,
        useVisionConfig: Boolean = false,
        temperature: Float = 0.7f,
        topP: Float = 1f,
        maxTokens: Int = 4096,
        timeoutSeconds: Long = 60L,
        webSearch: Boolean = false
    ): Result<StreamResult> = withContext(Dispatchers.IO) {
        var call: okhttp3.Call? = null
        try {
            val settings = resolveApiSettings(useVisionConfig = useVisionConfig, preferredModel = model)
            require(settings.baseUrl.isNotBlank()) { "Base URL is required" }
            require(settings.apiKey.isNotBlank()) { "API Key is required" }
            val modelName = model?.takeIf { it.isNotBlank() } ?: settings.modelName
            require(modelName.isNotBlank()) { "Model is required" }

            val request = Request.Builder()
                .url("${settings.baseUrl.trim().trimEnd('/')}/chat/completions")
                .post(
                    buildRequestBody(
                        model = modelName,
                        messages = messages,
                        stream = false,
                        temperature = temperature,
                        topP = topP,
                        maxTokens = maxTokens,
                        webSearch = webSearch
                    )
                )
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey.trim()}")
                .build()

            val client = okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout((timeoutSeconds * 2).coerceAtLeast(120), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout((timeoutSeconds * 2).coerceAtLeast(180), TimeUnit.SECONDS)
                .build()

            call = client.newCall(request)
            activeCall.set(call)
            val result = call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw httpFailure(response.code, errorBody.ifBlank { response.message })
                }

                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val message = root
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                val usage = root.optJSONObject("usage")
                val finishReason = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optString("finish_reason")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val blockReason = findBlockReason(root)
                if (blockReason != null || isBlockedFinishReason(finishReason)) {
                    throw UpstreamContentBlockedException(blockReason ?: finishReason.orEmpty())
                }
                val result = StreamResult(
                    content = message?.optString("content").orEmpty(),
                    reasoningContent = message?.optString("reasoning_content").orEmpty(),
                    totalTokens = usage?.optInt("total_tokens"),
                    finishReason = finishReason
                )
                if (isLengthFinishReason(finishReason)) throw OutputTruncatedException(result)
                result
            }
            Result.success(result)
        } catch (cancelled: CancellationException) {
            Result.failure(cancelled)
        } catch (timeout: SocketTimeoutException) {
            Result.failure(Exception("请求超时，请稍后重试"))
        } catch (interrupted: InterruptedIOException) {
            Result.failure(CancellationException("Request cancelled"))
        } catch (connect: ConnectException) {
            Result.failure(Exception("连接失败，请检查网络或 API 地址"))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        } finally {
            call?.let { activeCall.compareAndSet(it, null) }
        }
    }

    fun cancelActiveStream() {
        activeCall.getAndSet(null)?.cancel()
    }

    suspend fun generateTitle(userText: String, assistantText: String): Result<String> {
        val prompt = "请根据以下对话生成一个不超过 10 个字的简短标题，只输出标题。用户：$userText 助手：$assistantText"
        return requestSingleText(
            messages = listOf(mapOf("role" to "user", "content" to prompt))
                .map { ChatPayload(role = it["role"].orEmpty(), text = it["content"].orEmpty()) }
        ).map { it.take(10).trim().ifBlank { "新对话" } }
    }

    suspend fun summarize(
        messages: List<ChatPayload>,
        baseUrl: String? = null,
        apiKey: String? = null,
        model: String? = null,
        maxTokens: Int = 2048,
        timeoutSeconds: Long = 60L
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mainSettings = resolveApiSettings(useVisionConfig = false, preferredModel = null)
            val summarySettings = settingsRepository.getSummaryApiSettings()
            val resolvedBaseUrl = baseUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: summarySettings.baseUrl.trim().takeIf { it.isNotBlank() }
                ?: mainSettings.baseUrl
            val resolvedApiKey = apiKey?.trim()?.takeIf { it.isNotBlank() }
                ?: summarySettings.apiKey.trim().takeIf { it.isNotBlank() }
                ?: mainSettings.apiKey
            val resolvedModel = model?.trim()?.takeIf { it.isNotBlank() }
                ?: summarySettings.modelName.trim().takeIf { it.isNotBlank() }
                ?: mainSettings.modelName

            require(resolvedBaseUrl.isNotBlank()) { "Base URL is required" }
            require(resolvedApiKey.isNotBlank()) { "API Key is required" }
            require(resolvedModel.isNotBlank()) { "Model is required" }
            require(messages.isNotEmpty()) { "Messages are required" }

            val request = Request.Builder()
                .url("${resolvedBaseUrl.trimEnd('/')}/chat/completions")
                .post(
                    buildRequestBody(
                        model = resolvedModel,
                        messages = messages,
                        stream = false,
                        temperature = 0.2f,
                        topP = 1f,
                        maxTokens = maxTokens.coerceIn(1, 32768),
                        webSearch = false
                    )
                )
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $resolvedApiKey")
                .build()
            val client = okHttpClient.newBuilder()
                .readTimeout(timeoutSeconds.coerceIn(10L, 300L), TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds.coerceIn(10L, 300L), TimeUnit.SECONDS)
                .build()

            val summary = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw httpFailure(response.code, errorBody.ifBlank { response.message })
                }
                JSONObject(response.body?.string().orEmpty())
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                    .trim()
            }
            require(summary.isNotBlank()) { "Empty summary response" }
            Result.success(summary)
        } catch (timeout: SocketTimeoutException) {
            Result.failure(Exception("请求超时，请稍后重试"))
        } catch (connect: ConnectException) {
            Result.failure(Exception("连接失败，请检查网络或 API 地址"))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private suspend fun requestSingleText(
        messages: List<ChatPayload>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val settings = resolveApiSettings(useVisionConfig = false, preferredModel = null)
            require(settings.baseUrl.isNotBlank()) { "Base URL is required" }
            require(settings.apiKey.isNotBlank()) { "API Key is required" }
            require(settings.modelName.isNotBlank()) { "Model is required" }

            val request = Request.Builder()
                .url("${settings.baseUrl.trim().trimEnd('/')}/chat/completions")
                .post(buildRequestBody(settings.modelName, messages, stream = false))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey.trim()}")
                .build()

            val value = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw httpFailure(response.code, errorBody.ifBlank { response.message })
                }

                val body = response.body?.string().orEmpty()
                JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                    .trim()
                    .removePrefix("\"")
                    .removeSuffix("\"")
            }
            Result.success(value)
        } catch (timeout: SocketTimeoutException) {
            Result.failure(Exception("请求超时，请稍后重试"))
        } catch (connect: ConnectException) {
            Result.failure(Exception("连接失败，请检查网络或 API 地址"))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    suspend fun summarizeConversation(
        previousSummary: String?,
        transcript: String
    ): Result<String> {
        val prompt = buildString {
            append("请把对话压缩为供后续角色扮演继续使用的角色知识账本。只输出摘要，不要解释。\n")
            append("消息envelope中的owner只表示承载该消息的角色卡，不等于正文中每句话的实际发言者；publicSpeakers才是已识别的公开发言者。publicSpeakers=未确认时不得擅自归属。\n")
            append("保留：已发生事件及顺序、人物关系变化、承诺/冲突、重要物品地点、未完成目标、待回应事项、用户明确设定，以及每条关键信息的知情者、未知者、在场状态和传播过程。\n")
            append("必须区分：公共事实；各角色亲历、目击或听见的事实；明确被告知的事实；尚未公开或知情归属未确认的事实。A知道的事不能自动记为B或后来登场的C知道；同场但错过视线、被遮挡、隔音、昏睡或正在私聊的角色也不能自动知情。\n")
            append("只有公开说出、明确转述或展示公开证据后，才能把信息加入对应角色的已知事实。无法确认谁知道时标记‘知情归属未确认’，不要假定全员知晓。\n")
            append("使用以下结构：\n【当前场景状态】地点；明确在场者；明确离场/缺席者；状态依据\n【公共事实】事实；公开来源；发生顺序\n【角色所知：角色名】事实；来源=亲历/目击/听见/被明确告知/公开证据\n【角色未知/错过：角色名】事实；原因\n【信息传播】谁在何时把什么告诉了谁\n【尚未回应/未完成事项】发起者；对象；内容；哪些角色尚未明确回应\n【发言归属未确认】无法可靠判断实际说话者的文本\n没有内容的区块可省略。\n")
            append("忽略：AI回复的文风、措辞、篇幅、叙事人称、inner thought、reasoning、未公开计划和元说明。不得把私密内心升级为任何角色已知或公共事实。\n")
            append("摘要应简洁、客观，不能创造未发生事实；增量合并时不得抹掉已有的知情边界。\n")
            previousSummary?.takeIf { it.isNotBlank() }?.let {
                append("\n已有摘要（在此基础上增量合并）：\n")
                append(it.take(8_000))
                append("\n")
            }
            append("\n本次新增对话：\n")
            append(transcript)
        }
        return summarize(
            messages = listOf(
                ChatPayload(
                    role = "system",
                    text = "你是独立的角色知识、发言归属和信息可见性压缩器，不参与角色扮演，也不模仿对话中的写作风格。角色卡消息的owner不等于正文中每句话的发言者；只有明确的‘角色名：’前缀、publicSpeakers元数据或无歧义叙述才能确定发言归属。必须持续保留当前在场者、离场者、信息来源和尚未回应者，不能把叙事中发生过的事默认变成所有角色的共同知识。"
                ),
                ChatPayload(role = "user", text = prompt)
            ),
            maxTokens = 1200,
            timeoutSeconds = 45L
        )
    }

    private suspend fun resolveApiSettings(
        useVisionConfig: Boolean,
        preferredModel: String?
    ): ApiSettings {
        if (useVisionConfig) {
            val vision = settingsRepository.getVisionApiSettings()
            if (vision.baseUrl.isNotBlank() && vision.apiKey.isNotBlank()) {
                return vision.copy(modelName = preferredModel?.takeIf { it.isNotBlank() } ?: vision.modelName)
            }
        }

        val settings = settingsRepository.getSettings()
        val activeProvider = modelRepository.getActive()
        val fallbackProvider = activeProvider ?: modelRepository.getAll().firstOrNull {
            it.baseUrl.isNotBlank() && it.apiKey.isNotBlank()
        }
        val resolvedBaseUrl = fallbackProvider?.baseUrl?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: settings.apiBaseUrl.trim().trimEnd('/')
        val resolvedApiKey = fallbackProvider?.apiKey?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: settings.apiKey.trim()
        val resolvedModel = preferredModel?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel.trim()

        return ApiSettings(
            baseUrl = resolvedBaseUrl,
            apiKey = resolvedApiKey,
            modelName = resolvedModel
        )
    }

    private fun buildRequestBody(
        model: String,
        messages: List<ChatPayload>,
        stream: Boolean,
        temperature: Float = 0.7f,
        topP: Float = 1f,
        maxTokens: Int = 4096,
        webSearch: Boolean = false
    ) = JSONObject()
        .put("model", model)
        .put("stream", stream)
        .put("temperature", temperature.toDouble())
        .put("top_p", topP.toDouble())
        .put("max_tokens", maxTokens)
        .also { body ->
            if (webSearch) {
                // 搜索由厂商字段或上层独立搜索实现，不声明本地未实现的 function tools。
                body.put("web_search", true)
                body.put("enable_search", true)
                body.put("search_enabled", true)
            }
        }
        .put(
            "messages",
            JSONArray().also { array ->
                messages.forEach { message ->
                    val contentArray = JSONArray()
                    if (message.text.isNotBlank()) {
                        contentArray.put(JSONObject().put("type", "text").put("text", message.text))
                    }
                    message.fileTexts.forEach { fileText ->
                        contentArray.put(JSONObject().put("type", "text").put("text", fileText))
                    }
                    message.imageDataUrls.forEach { dataUrl ->
                        contentArray.put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", dataUrl))
                        )
                    }
                    val contentValue: Any = if (contentArray.length() <= 1 && message.imageDataUrls.isEmpty() && message.fileTexts.isEmpty()) {
                        message.text
                    } else {
                        contentArray
                    }
                    array.put(JSONObject().put("role", message.role).put("content", contentValue))
                }
            }
        )
        .also { body ->
            if (stream) body.put("stream_options", JSONObject().put("include_usage", true))
        }
        .toString()
        .toRequestBody(JSON_MEDIA_TYPE)

    private fun parseSseChunk(line: String): StreamDelta? {
        if (!line.startsWith("data:")) return null
        val data = line.removePrefix("data:").trim()
        if (data.isBlank() || data == "[DONE]") return null

        val delta = runCatching {
            JSONObject(data)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
        }.getOrNull() ?: return null

        val content = if (delta.has("content") && !delta.isNull("content")) {
            delta.optString("content")
        } else {
            null
        }
        val reasoningContent = when {
            delta.has("reasoning_content") && !delta.isNull("reasoning_content") ->
                delta.optString("reasoning_content")
            delta.has("reasoningContent") && !delta.isNull("reasoningContent") ->
                delta.optString("reasoningContent")
            else -> null
        }

        if (content == null && reasoningContent == null) return null
        return StreamDelta(content = content.orEmpty(), reasoningContent = reasoningContent.orEmpty())
    }

    private fun parseUsage(line: String): Int? {
        if (!line.startsWith("data:")) return null
        val data = line.removePrefix("data:").trim()
        if (data.isBlank() || data == "[DONE]") return null
        return runCatching {
            JSONObject(data).optJSONObject("usage")?.optInt("total_tokens")?.takeIf { it > 0 }
        }.getOrNull()
    }

    private fun parseFinishReason(line: String): String? {
        if (!line.startsWith("data:")) return null
        val data = line.removePrefix("data:").trim()
        if (data.isBlank() || data == "[DONE]") return null
        return runCatching {
            JSONObject(data)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optString("finish_reason")
                ?.takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()
    }

    private fun isBlockedFinishReason(reason: String?): Boolean {
        val normalized = reason.orEmpty().lowercase()
        return normalized.contains("content_filter") || normalized.contains("safety") ||
            normalized.contains("blocked") || normalized.contains("prohibited")
    }

    private fun isLengthFinishReason(reason: String?): Boolean {
        val normalized = reason.orEmpty().lowercase()
        return normalized == "length" || normalized.contains("max_tokens") ||
            normalized.contains("max_output_tokens")
    }

    private fun httpFailure(code: Int, detail: String): Throwable {
        val normalized = detail.lowercase()
        val isPolicyBlock = listOf(
            "prohibited use policy", "prompt could not be submitted", "sensitive words",
            "content_filter", "safety", "blocked"
        ).any(normalized::contains)
        return if (isPolicyBlock) {
            UpstreamContentBlockedException("HTTP $code: ${detail.take(160)}")
        } else {
            IllegalStateException("HTTP $code: $detail")
        }
    }

    private fun findBlockReason(root: JSONObject): String? {
        val promptReason = root.optJSONObject("promptFeedback")
            ?.optString("blockReason")
            ?.takeIf { it.isNotBlank() && it != "null" && it != "BLOCK_REASON_UNSPECIFIED" }
        if (promptReason != null) return promptReason
        val directReason = root.optString("block_reason")
            .takeIf { it.isNotBlank() && it != "null" }
        if (directReason != null) return directReason
        val errorText = root.optJSONObject("error")?.toString().orEmpty()
        return errorText.takeIf {
            val text = it.lowercase()
            text.contains("safety") || text.contains("content_filter") || text.contains("blocked")
        }?.take(160)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val TOOL_CAPABLE_KEYWORDS = listOf(
            "grok", "gpt-4", "gpt-3.5", "claude", "gemini", "qwen", "deepseek",
            "glm-4", "yi-", "mistral", "command-r", "llama-3", "baichuan",
            "ernie", "spark", "minimax", "abab", "step-"
        )

        fun modelSupportsTools(modelName: String): Boolean {
            val lower = modelName.lowercase()
            return TOOL_CAPABLE_KEYWORDS.any { lower.contains(it) }
        }

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
