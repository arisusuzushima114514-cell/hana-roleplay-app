package com.hana.app.data.remote

import com.hana.app.data.model.Message
import com.hana.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

interface ApiService {
    suspend fun sendMessageStream(
        messages: List<Message>,
        onDelta: suspend (String) -> Unit
    ): Result<Unit>

    suspend fun generateTitle(
        userMessage: String,
        assistantMessage: String
    ): Result<String>
}

class OkHttpApiService(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : ApiService {
    override suspend fun sendMessageStream(
        messages: List<Message>,
        onDelta: suspend (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.getApiSettings()
            require(settings.apiKey.isNotBlank()) { "API Key is required" }

            val request = Request.Builder()
                .url("${settings.baseUrl.trim().trimEnd('/')}/chat/completions")
                .post(buildChatRequestBody(settings.modelName, messages, stream = true))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    error("HTTP ${response.code}: ${errorBody.ifBlank { response.message }}")
                }

                val body = response.body ?: error("Empty response body")
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val payload = line.removePrefix("data:").trim()
                        if (!line.startsWith("data:") || payload.isBlank()) continue
                        if (payload == "[DONE]") break

                        val delta = parseDeltaContent(payload)
                        if (delta.isNotEmpty()) {
                            onDelta(delta)
                        }
                    }
                }
            }
        }
    }

    override suspend fun generateTitle(
        userMessage: String,
        assistantMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.getApiSettings()
            require(settings.apiKey.isNotBlank()) { "API Key is required" }

            val prompt = buildString {
                appendLine("请根据以下对话内容，生成一个简短的对话标题（不超过15个字，不要引号）：")
                appendLine()
                appendLine("用户：$userMessage")
                appendLine("助手：$assistantMessage")
            }.trim()

            val request = Request.Builder()
                .url("${settings.baseUrl.trim().trimEnd('/')}/chat/completions")
                .post(
                    buildChatRequestBody(
                        modelName = settings.modelName,
                        messages = listOf(
                            Message(
                                role = Message.Role.User.value,
                                content = prompt
                            )
                        ),
                        stream = false
                    )
                )
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    error("HTTP ${response.code}: ${errorBody.ifBlank { response.message }}")
                }

                val body = response.body?.string().orEmpty()
                val title = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                    .trim()
                    .removePrefix("\"")
                    .removeSuffix("\"")

                require(title.isNotBlank()) { "Empty title response" }
                title.take(15)
            }
        }
    }

    private fun buildChatRequestBody(
        modelName: String,
        messages: List<Message>,
        stream: Boolean
    ) = JSONObject()
        .put("model", modelName)
        .put("stream", stream)
        .put(
            "messages",
            JSONArray().also { array ->
                messages.forEach { message ->
                    array.put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", message.content)
                    )
                }
            }
        )
        .toString()
        .toRequestBody(JSON_MEDIA_TYPE)

    private fun parseDeltaContent(payload: String): String {
        return JSONObject(payload)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content")
            .orEmpty()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
