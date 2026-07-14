package com.hana.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ModelService(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "API Key is required" }

            val request = Request.Builder()
                .url("${baseUrl.trim().trimEnd('/')}/models")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    error("HTTP ${response.code}: ${errorBody.ifBlank { response.message }}")
                }

                val body = response.body?.string().orEmpty()
                val data = JSONObject(body).optJSONArray("data")
                    ?: return@use emptyList()

                buildList {
                    for (index in 0 until data.length()) {
                        val id = data.optJSONObject(index)?.optString("id").orEmpty()
                        if (id.isNotBlank()) add(id)
                    }
                }
            }
        }
    }
}
