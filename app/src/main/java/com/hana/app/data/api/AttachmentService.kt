package com.hana.app.data.api

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.hana.app.ui.chat.AttachmentKind
import com.hana.app.ui.chat.ChatAttachment
import com.hana.app.ui.chat.decodeChatContent
import com.hana.app.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AttachmentService(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val imageClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun persistPickedUri(uri: Uri, fallbackName: String, kind: AttachmentKind): ChatAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = appContext.contentResolver
            val mimeType = contentResolver.getType(uri).orEmpty().ifBlank {
                if (kind == AttachmentKind.IMAGE) "image/png" else "application/octet-stream"
            }
            val fileName = guessFileName(uri, fallbackName, mimeType)
            val file = File(attachmentsDir(), fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    if (kind == AttachmentKind.IMAGE) input.copyToLimited(output, MAX_IMAGE_BYTES) else input.copyTo(output)
                }
            } ?: return@runCatching null
            ChatAttachment(
                name = file.name,
                mimeType = mimeType,
                uri = Uri.fromFile(file).toString(),
                kind = kind,
                sizeBytes = file.length(),
                previewText = if (kind == AttachmentKind.FILE) extractTextPreview(file, mimeType) else ""
            )
        }.getOrNull()
    }

    suspend fun persistCameraBitmap(bitmap: Bitmap): ChatAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(attachmentsDir(), "camera_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            ChatAttachment(
                name = file.name,
                mimeType = "image/png",
                uri = Uri.fromFile(file).toString(),
                kind = AttachmentKind.IMAGE,
                sizeBytes = file.length(),
                previewText = ""
            )
        }.getOrNull()
    }

    suspend fun persistGeneratedImage(url: String, fallbackName: String): ChatAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = when {
                url.startsWith("data:image/") -> {
                    val payload = url.substringAfter("base64,", "")
                    require(payload.length <= MAX_BASE64_IMAGE_CHARS) { "图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB 限制" }
                    android.util.Base64.decode(payload, android.util.Base64.DEFAULT).also {
                        require(it.size <= MAX_IMAGE_BYTES) { "图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB 限制" }
                    }
                }
                else -> downloadImageBytes(url).second
            }
            if (bytes.isEmpty()) return@runCatching null
            val mimeType = when {
                url.startsWith("data:image/") -> url.substringAfter("data:", "image/png").substringBefore(';').ifBlank { "image/png" }
                else -> "image/png"
            }
            val fileName = guessFileName(Uri.parse(url), fallbackName, mimeType)
            val file = File(attachmentsDir(), fileName)
            FileOutputStream(file).use { output -> output.write(bytes) }
            ChatAttachment(
                name = file.name,
                mimeType = mimeType,
                uri = Uri.fromFile(file).toString(),
                kind = AttachmentKind.IMAGE,
                sizeBytes = file.length(),
                previewText = ""
            )
        }.getOrNull()
    }

    suspend fun asImageDataUrl(attachment: ChatAttachment): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openUri(attachment.uri)?.use { it.readBytesLimited(MAX_IMAGE_BYTES) }
                ?: return@runCatching null
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:${attachment.mimeType.ifBlank { "image/png" }};base64,$base64"
        }.getOrNull()
    }

    suspend fun extractReadableText(attachment: ChatAttachment, maxChars: Int = 12000): String? = withContext(Dispatchers.IO) {
        if (!isReadableText(attachment)) return@withContext null
        runCatching {
            openUri(attachment.uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readText().take(maxChars)
            }
        }.getOrNull()
    }

    suspend fun saveImageToGallery(uriString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "hana_image_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = appContext.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Hana")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                ) ?: error("无法创建相册文件")
                try {
                    appContext.contentResolver.openOutputStream(uri)?.use { output ->
                        openUri(uriString)?.use { input -> input.copyTo(output) } ?: error("无法读取图片")
                    } ?: error("无法写入图片")
                    appContext.contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                    uri.toString()
                } catch (t: Throwable) {
                    appContext.contentResolver.delete(uri, null, null)
                    throw t
                }
            } else {
                requireLegacyGalleryPermission()
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Hana")
                    .apply { if (!exists() && !mkdirs()) error("无法创建相册目录") }
                val file = File(directory, fileName)
                openUri(uriString)?.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
                    ?: error("无法读取图片")
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.insertImage(appContext.contentResolver, file.absolutePath, file.name, null)
                file.absolutePath
            }
        }
    }

    suspend fun loadImageBytes(uriString: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { openUri(uriString)?.use { it.readBytesLimited(MAX_IMAGE_BYTES) } }.getOrNull()
    }

    suspend fun loadImageBitmap(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openUri(uriString)?.use { it.readBytesLimited(MAX_IMAGE_BYTES) } ?: return@runCatching null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    /** 将图片转为线稿风格（用于非视觉模型识别图片内容） */
    suspend fun toLineArtBase64(uriString: String, maxWidth: Int = 512): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val original = loadImageBitmap(uriString) ?: return@runCatching null
            // 缩放到合理尺寸
            val scale = maxWidth.toFloat() / maxOf(original.width, original.height)
            val w = (original.width * scale).toInt().coerceAtLeast(1)
            val h = (original.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(original, w, h, true)

            // 灰度 + 边缘检测（Sobel 简化版）
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            val gray = IntArray(w * h) { i ->
                val c = pixels[i]
                ((android.graphics.Color.red(c) * 0.299 + android.graphics.Color.green(c) * 0.587 + android.graphics.Color.blue(c) * 0.114)).toInt()
            }

            val edge = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val edgePixels = IntArray(w * h)
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    val gx = -gray[idx - w - 1] - 2 * gray[idx - 1] - gray[idx + w - 1] +
                        gray[idx - w + 1] + 2 * gray[idx + 1] + gray[idx + w + 1]
                    val gy = -gray[idx - w - 1] - 2 * gray[idx - w] - gray[idx - w + 1] +
                        gray[idx + w - 1] + 2 * gray[idx + w] + gray[idx + w + 1]
                    val mag = (kotlin.math.sqrt((gx * gx + gy * gy).toDouble())).toInt().coerceIn(0, 255)
                    // 反转：白底黑线
                    val inv = 255 - mag
                    edgePixels[idx] = android.graphics.Color.rgb(inv, inv, inv)
                }
            }
            edge.setPixels(edgePixels, 0, w, 0, 0, w, h)

            val stream = java.io.ByteArrayOutputStream()
            edge.compress(Bitmap.CompressFormat.PNG, 80, stream)
            val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            Pair("data:image/png;base64,$base64", "（图片尺寸：${original.width}x${original.height}）")
        }.getOrNull()
    }

    /** 判断当前模型是否为非视觉模型（需要线稿转换） */
    fun needsLineArtConversion(modelName: String): Boolean {
        val lower = modelName.lowercase()
        val visionKeywords = listOf("vision", "vl", "gpt-4o", "gpt-4v", "gemini", "claude", "grok", "pixtral", "cogvlm", "llava", "minicpm-v", "internvl", "step-1v")
        return visionKeywords.none { lower.contains(it) }
    }

    private fun attachmentsDir(): File {
        val dir = File(appContext.filesDir, "chat_attachments")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 删除一批消息里引用的所有附件文件 */
    fun deleteAttachmentsForMessages(messages: List<ChatMessageEntity>) {
        messages.forEach { msg ->
            runCatching {
                val decoded = decodeChatContent(msg.content)
                decoded.attachments.forEach { attachment ->
                    val uri = android.net.Uri.parse(attachment.uri)
                    val file = when (uri.scheme?.lowercase(Locale.US)) {
                        "file" -> File(uri.path.orEmpty())
                        else -> null
                    }
                    file?.takeIf { it.exists() && it.startsWith(attachmentsDir()) }?.delete()
                }
            }
        }
    }

    private fun guessFileName(uri: Uri, fallbackName: String, mimeType: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType).orEmpty()
            .ifBlank { if (mimeType.startsWith("image/")) "png" else "bin" }
        val safeFallback = fallbackName.ifBlank { "attachment" }
        // 文件名中嵌入时间戳，避免同名文件静默覆盖
        val ts = System.currentTimeMillis()
        val base = if (safeFallback.contains('.')) safeFallback.substringBeforeLast('.') else safeFallback
        val origExt = if (safeFallback.contains('.')) safeFallback.substringAfterLast('.') else ext
        return "${base}_$ts.$origExt"
    }

    private fun openUri(uriString: String) = runCatching {
        val uri = Uri.parse(uriString)
        when (uri.scheme?.lowercase(Locale.US)) {
            "file" -> File(uri.path.orEmpty()).inputStream()
            "content" -> appContext.contentResolver.openInputStream(uri)
            else -> File(uriString.removePrefix("file://")).takeIf { it.exists() }?.inputStream()
        }
    }.getOrNull()

    private suspend fun downloadImageBytes(url: String): Pair<String, ByteArray> = suspendCancellableCoroutine { continuation ->
        val call = imageClient.newCall(Request.Builder().url(url).header("User-Agent", "Hana/1.0").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) error("下载图片失败: HTTP ${response.code}")
                        val body = response.body ?: error("图片响应为空")
                        require(body.contentLength() < 0 || body.contentLength() <= MAX_IMAGE_BYTES) {
                            "图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB 限制"
                        }
                        val bytes = body.byteStream().use { it.readBytesLimited(MAX_IMAGE_BYTES) }
                        val mime = body.contentType()?.toString()?.substringBefore(';').orEmpty().ifBlank { "image/png" }
                        if (continuation.isActive) continuation.resume(mime to bytes)
                    } catch (t: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(t)
                    }
                }
            }
        })
    }

    private fun requireLegacyGalleryPermission() {
        check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            "Android 9 及以下保存到相册需要存储权限，请授权后重试"
        }
    }

    private fun java.io.InputStream.readBytesLimited(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "图片超过 ${maxBytes / 1024 / 1024}MB 限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun java.io.InputStream.copyToLimited(output: java.io.OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "图片超过 ${maxBytes / 1024 / 1024}MB 限制" }
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
        const val MAX_BASE64_IMAGE_CHARS = 28 * 1024 * 1024
    }

    private fun isReadableText(attachment: ChatAttachment): Boolean {
        val mime = attachment.mimeType.lowercase(Locale.US)
        val name = attachment.name.lowercase(Locale.US)
        return mime.startsWith("text/") ||
            mime.contains("json") ||
            mime.contains("xml") ||
            mime.contains("javascript") ||
            name.endsWith(".md") ||
            name.endsWith(".txt") ||
            name.endsWith(".json") ||
            name.endsWith(".kt") ||
            name.endsWith(".java") ||
            name.endsWith(".py") ||
            name.endsWith(".js") ||
            name.endsWith(".ts") ||
            name.endsWith(".csv")
    }

    private fun extractTextPreview(file: File, mimeType: String): String {
        if (!isReadableText(ChatAttachment(file.name, mimeType, file.toURI().toString(), AttachmentKind.FILE))) return ""
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText().replace(Regex("\\s+"), " ").trim().take(140)
            }
        }.getOrDefault("")
    }
}
