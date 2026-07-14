package com.hana.app.data.api

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.hana.app.data.db.entity.SavedModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

data class ImageGenerationRequest(
    val prompt: String,
    val negativePrompt: String,
    val model: String,
    val batchCount: Int,
    val aspectRatio: String,
    val referenceImageDataUrls: List<String> = emptyList()
)

data class ImageGenerationResult(
    val imageUrls: List<String>,
    val revisedPrompt: String? = null
)

class ImageGenerationService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()
) {
    private val activeCall = AtomicReference<okhttp3.Call?>(null)

    suspend fun generate(
        provider: SavedModelEntity,
        requestData: ImageGenerationRequest
    ): Result<ImageGenerationResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(provider.baseUrl.isNotBlank()) { "生图服务商地址为空" }
            require(provider.apiKey.isNotBlank()) { "生图服务商密钥为空" }
            require(requestData.model.isNotBlank()) { "生图模型为空" }

            val endpoint = "${provider.baseUrl.trim().trimEnd('/')}/images/generations"
            val requestBodies = listOf(
                buildBody(requestData, responseFormat = "url"),
                buildBody(requestData, responseFormat = null),
                buildBody(requestData, responseFormat = "b64_json")
            )

            var lastError: String? = null
            var finalResult: ImageGenerationResult? = null
            for (body in requestBodies) {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${provider.apiKey.trim()}")
                    .build()

                val call = client.newCall(request)
                activeCall.set(call)
                call.execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}: ${responseBody.ifBlank { response.message }}"
                        return@use
                    }
                    val root = JSONObject(responseBody)
                    val urls = extractImages(root)
                    if (urls.isNotEmpty()) {
                        finalResult = ImageGenerationResult(
                            imageUrls = urls,
                            revisedPrompt = root.optString("revised_prompt").takeIf { it.isNotBlank() }
                        )
                        return@use
                    }
                    lastError = "接口返回成功，但没有图片数据"
                }
                activeCall.compareAndSet(call, null)
                if (finalResult != null) break
            }
            finalResult ?: error(lastError ?: "生图接口未返回可用结果")
        }
    }

    fun cancelActiveGeneration() {
        activeCall.getAndSet(null)?.cancel()
    }

    private fun buildBody(requestData: ImageGenerationRequest, responseFormat: String?) = JSONObject().apply {
        put("model", requestData.model)
        put("prompt", requestData.prompt)
        put("n", requestData.batchCount.coerceIn(1, 8))
        put("size", toSize(requestData.aspectRatio))
        if (requestData.negativePrompt.isNotBlank()) {
            put("negative_prompt", requestData.negativePrompt)
        }
        if (!responseFormat.isNullOrBlank()) {
            put("response_format", responseFormat)
        }
        requestData.referenceImageDataUrls.firstOrNull()?.takeIf { it.isNotBlank() }?.let { imageDataUrl ->
            put("image", imageDataUrl)
            put("input_image", imageDataUrl)
            put("reference_image", imageDataUrl)
            put("image_url", imageDataUrl)
            put("edit_mode", true)
        }
        if (requestData.referenceImageDataUrls.isNotEmpty()) {
            put("reference_images", JSONArray().also { arr -> requestData.referenceImageDataUrls.forEach(arr::put) })
            put("input_images", JSONArray().also { arr -> requestData.referenceImageDataUrls.forEach(arr::put) })
        }
    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun extractImages(root: JSONObject): List<String> {
        val arrays = listOfNotNull(
            root.optJSONArray("data"),
            root.optJSONArray("images"),
            root.optJSONObject("result")?.optJSONArray("images"),
            root.optJSONObject("output")?.optJSONArray("images")
        )
        return buildList {
            arrays.forEach { data ->
                for (i in 0 until data.length()) {
                    val item = data.opt(i)
                    when (item) {
                        is JSONObject -> {
                            val url = item.optString("url").ifBlank {
                                item.optString("image_url").ifBlank { item.optString("image") }
                            }
                            val b64 = item.optString("b64_json").ifBlank { item.optString("base64") }
                            when {
                                url.isNotBlank() -> add(url)
                                b64.isNotBlank() -> add("data:image/png;base64,$b64")
                            }
                        }
                        is String -> if (item.isNotBlank()) add(item)
                    }
                }
            }
            root.optString("image_url").takeIf { it.isNotBlank() }?.let(::add)
            root.optString("url").takeIf { it.isNotBlank() }?.let(::add)
            root.optString("b64_json").takeIf { it.isNotBlank() }?.let { add("data:image/png;base64,$it") }
        }.distinct()
    }

    private fun toSize(aspectRatio: String): String {
        return when (aspectRatio) {
            "21:9" -> "1792x768"
            "16:9" -> "1792x1024"
            "3:2" -> "1536x1024"
            "2:3" -> "1024x1536"
            "3:4" -> "1024x1365"
            "9:16" -> "1024x1792"
            "4:3" -> "1536x1024"
            "5:4" -> "1280x1024"
            "4:5" -> "1024x1280"
            "1:2" -> "896x1792"
            "2:1" -> "1792x896"
            else -> "1024x1024"
        }
    }

    suspend fun saveImage(context: Context, url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val (mimeType, imageBytes) = readImageBytes(url)
            val extension = when (mimeType.lowercase()) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/webp" -> "webp"
                else -> "png"
            }
            val fileName = "hana_image_${System.currentTimeMillis()}.$extension"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Hana")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建图片文件")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    ByteArrayInputStream(imageBytes).use { input -> input.copyTo(output) }
                } ?: error("无法写入图片")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                }
                fileName
            } catch (t: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw t
            }
        }
    }

    private fun readImageBytes(url: String): Pair<String, ByteArray> {
        if (url.startsWith("data:image/")) {
            val mimeType = url.substringAfter("data:", "image/png").substringBefore(';').ifBlank { "image/png" }
            val payload = url.substringAfter("base64,", "")
            val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            require(bytes.isNotEmpty()) { "图片数据为空" }
            return mimeType to bytes
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Hana/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("下载图片失败: HTTP ${response.code}")
            }
            val body = response.body ?: error("图片响应为空")
            val bytes = body.bytes()
            require(bytes.isNotEmpty()) { "下载到的图片为空" }
            val mimeType = body.contentType()?.toString()?.substringBefore(';').orEmpty().ifBlank { "image/png" }
            return mimeType to bytes
        }
    }
}
