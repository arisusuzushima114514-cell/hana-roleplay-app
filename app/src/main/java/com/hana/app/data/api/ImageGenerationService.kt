package com.hana.app.data.api

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.hana.app.data.db.entity.SavedModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

            val baseUrl = provider.baseUrl.trim().trimEnd('/')
            val requests = if (requestData.referenceImageDataUrls.isEmpty()) {
                listOf("url", null, "b64_json").map { responseFormat ->
                    "$baseUrl/images/generations" to buildBody(requestData, responseFormat)
                }
            } else {
                buildEditRequests(baseUrl, requestData)
            }

            var lastError: String? = null
            var finalResult: ImageGenerationResult? = null
            for ((endpoint, body) in requests) {
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
    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun buildEditRequests(
        baseUrl: String,
        requestData: ImageGenerationRequest
    ): List<Pair<String, RequestBody>> {
        val images = requestData.referenceImageDataUrls.mapIndexed { index, dataUrl ->
            decodeReferenceImage(dataUrl, index)
        }
        val endpoint = "$baseUrl/images/edits"
        val formats = listOf("url", null, "b64_json")
        return buildList {
            if (images.size > 1) {
                formats.forEach { format ->
                    add(endpoint to buildEditBody(requestData, images, format, useArrayField = true))
                }
            }
            formats.forEach { format ->
                add(endpoint to buildEditBody(requestData, images.take(1), format, useArrayField = false))
            }
        }
    }

    private fun buildEditBody(
        requestData: ImageGenerationRequest,
        images: List<ReferenceImage>,
        responseFormat: String?,
        useArrayField: Boolean
    ): RequestBody {
        val prompt = buildString {
            append(requestData.prompt)
            if (requestData.negativePrompt.isNotBlank()) {
                append("\nAvoid: ")
                append(requestData.negativePrompt)
            }
        }
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", requestData.model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", requestData.batchCount.coerceIn(1, 8).toString())
            .addFormDataPart("size", toSize(requestData.aspectRatio))
            .apply {
                responseFormat?.let { addFormDataPart("response_format", it) }
                images.forEach { image ->
                    addFormDataPart(
                        if (useArrayField) "image[]" else "image",
                        image.fileName,
                        image.bytes.toRequestBody(image.mimeType.toMediaType())
                    )
                }
            }
            .build()
    }

    private fun decodeReferenceImage(dataUrl: String, index: Int): ReferenceImage {
        require(dataUrl.startsWith("data:image/")) { "参考图格式无效" }
        val mimeType = dataUrl.substringAfter("data:").substringBefore(';').ifBlank { "image/png" }
        val payload = dataUrl.substringAfter("base64,", "")
        require(payload.isNotBlank() && payload.length <= MAX_BASE64_IMAGE_CHARS) { "参考图数据无效或过大" }
        val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) { "参考图数据无效或过大" }
        val extension = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        return ReferenceImage(mimeType, "reference_${index + 1}.$extension", bytes)
    }

    private data class ReferenceImage(
        val mimeType: String,
        val fileName: String,
        val bytes: ByteArray
    )

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Hana")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建图片文件")
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        ByteArrayInputStream(imageBytes).use { input -> input.copyTo(output) }
                    } ?: error("无法写入图片")
                    context.contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                    uri.toString()
                } catch (t: Throwable) {
                    context.contentResolver.delete(uri, null, null)
                    throw t
                }
            } else {
                check(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    "Android 9 及以下保存到相册需要存储权限，请授权后重试"
                }
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Hana")
                    .apply { if (!exists() && !mkdirs()) error("无法创建相册目录") }
                val file = File(directory, fileName)
                FileOutputStream(file).use { it.write(imageBytes) }
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.insertImage(context.contentResolver, file.absolutePath, file.name, null)
                file.absolutePath
            }
        }
    }

    private suspend fun readImageBytes(url: String): Pair<String, ByteArray> {
        if (url.startsWith("data:image/")) {
            val mimeType = url.substringAfter("data:", "image/png").substringBefore(';').ifBlank { "image/png" }
            val payload = url.substringAfter("base64,", "")
            require(payload.length <= MAX_BASE64_IMAGE_CHARS) { "图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB 限制" }
            val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            require(bytes.isNotEmpty()) { "图片数据为空" }
            require(bytes.size <= MAX_IMAGE_BYTES) { "图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB 限制" }
            return mimeType to bytes
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Hana/1.0")
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) error("下载图片失败: HTTP ${response.code}")
                            val body = response.body ?: error("图片响应为空")
                            require(body.contentLength() < 0 || body.contentLength() <= MAX_IMAGE_BYTES) {
                                "图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB 限制"
                            }
                            val bytes = body.byteStream().use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= MAX_IMAGE_BYTES) { "图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB 限制" }
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            require(bytes.isNotEmpty()) { "下载到的图片为空" }
                            val mime = body.contentType()?.toString()?.substringBefore(';').orEmpty().ifBlank { "image/png" }
                            if (continuation.isActive) continuation.resume(mime to bytes)
                        } catch (t: Throwable) {
                            if (continuation.isActive) continuation.resumeWithException(t)
                        }
                    }
                }
            })
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
        const val MAX_BASE64_IMAGE_CHARS = 28 * 1024 * 1024
    }
}
