package com.hana.app.data.api

import com.hana.app.data.api.models.StreamDelta
import com.hana.app.data.api.models.StreamResult
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
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ApiService(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
    private val okHttpClient: OkHttpClient = trustAllSslClient()
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
            val call = client.newCall(request)
            activeCall.set(call)

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    error("HTTP ${response.code}: ${errorBody.ifBlank { response.message }}")
                }

                val body = response.body ?: error("Empty response body")
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        parseUsage(line)?.let { totalTokens = it }
                        val delta = parseSseChunk(line) ?: continue

                        if (delta.reasoningContent.isNotBlank()) {
                            reasoningBuilder.append(delta.reasoningContent)
                        }
                        if (delta.content.isNotBlank()) {
                            contentBuilder.append(delta.content)
                        }
                        try { onDelta(delta) } catch (_: Exception) { /* ignore UI thread errors */ }
                    }
                }
            }

            Result.success(
                StreamResult(
                    content = contentBuilder.toString(),
                    reasoningContent = reasoningBuilder.toString(),
                    totalTokens = totalTokens
                )
            )
        } catch (cancelled: CancellationException) {
            Result.failure(cancelled)
        } catch (interrupted: InterruptedIOException) {
            Result.failure(CancellationException("Request cancelled"))
        } catch (timeout: SocketTimeoutException) {
            Result.failure(Exception("请求超时，请稍后重试"))
        } catch (connect: ConnectException) {
            Result.failure(Exception("连接失败，请检查网络或 API 地址"))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        } finally {
            activeCall.set(null)
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

            val result = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    error("HTTP ${response.code}: ${errorBody.ifBlank { response.message }}")
                }

                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val message = root
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                val usage = root.optJSONObject("usage")
                StreamResult(
                    content = message?.optString("content").orEmpty(),
                    reasoningContent = message?.optString("reasoning_content").orEmpty(),
                    totalTokens = usage?.optInt("total_tokens")
                )
            }
            Result.success(result)
        } catch (timeout: SocketTimeoutException) {
            Result.failure(Exception("请求超时，请稍后重试"))
        } catch (connect: ConnectException) {
            Result.failure(Exception("连接失败，请检查网络或 API 地址"))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
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
                .post(buildRequestBody(settings.modelName, messages, stream = false, enableTools = false))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey.trim()}")
                .build()

            val value = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    error("HTTP ${response.code}: ${errorBody.ifBlank { response.message }}")
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
        webSearch: Boolean = false,
        enableTools: Boolean = true
    ) = JSONObject()
        .put("model", model)
        .put("stream", stream)
        .put("temperature", temperature.toDouble())
        .put("top_p", topP.toDouble())
        .put("max_tokens", maxTokens)
        .also { body ->
            if (enableTools && modelSupportsTools(model)) {
                val toolsArr = JSONArray()
                toolsArr.put(JSONObject()
                    .put("type", "function")
                    .put("function", JSONObject()
                        .put("name", "get_current_time")
                        .put("description", "Get the current date, time, and day of week")
                        .put("parameters", JSONObject()
                            .put("type", "object")
                            .put("properties", JSONObject())
                            .put("required", JSONArray())
                        )
                    )
                )
                if (webSearch) {
                    body.put("web_search", true)
                    body.put("enable_search", true)
                    body.put("search_enabled", true)
                    toolsArr.put(JSONObject()
                        .put("type", "function")
                        .put("function", JSONObject()
                            .put("name", "web_search")
                            .put("description", "Search the web for real-time information, news, weather, data")
                            .put("parameters", JSONObject()
                                .put("type", "object")
                                .put("properties", JSONObject()
                                    .put("query", JSONObject()
                                        .put("type", "string")
                                        .put("description", "Search query")
                                    )
                                )
                                .put("required", JSONArray().put("query"))
                            )
                        )
                    )
                }
                body.put("tools", toolsArr)
                body.put("tool_choice", "auto")
            } else if (webSearch) {
                // 模型不支持 function calling 时，仅设置厂商私有搜索字段
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

        private fun trustAllSslClient(): OkHttpClient {
            return try {
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
                    override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAll, java.security.SecureRandom())
                OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()
            } catch (_: Exception) {
                OkHttpClient()
            }
        }
    }
}
