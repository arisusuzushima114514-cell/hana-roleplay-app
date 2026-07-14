package com.hana.app.data.api

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.hana.app.ui.chat.AttachmentKind
import com.hana.app.ui.chat.ChatAttachment
import com.hana.app.ui.chat.decodeChatContent
import com.hana.app.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale

class AttachmentService(
    private val context: Context
) {
    private val appContext = context.applicationContext

    suspend fun persistPickedUri(uri: Uri, fallbackName: String, kind: AttachmentKind): ChatAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = appContext.contentResolver
            val mimeType = contentResolver.getType(uri).orEmpty().ifBlank {
                if (kind == AttachmentKind.IMAGE) "image/png" else "application/octet-stream"
            }
            val fileName = guessFileName(uri, fallbackName, mimeType)
            val file = File(attachmentsDir(), fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
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
                    android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
                }
                else -> URL(url).openStream().use { it.readBytes() }
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
            val bytes = openUri(attachment.uri)?.readBytes() ?: return@runCatching null
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
            val uri = appContext.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "hana_image_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Hana")
                }
            ) ?: error("无法创建相册文件")
            appContext.contentResolver.openOutputStream(uri)?.use { output ->
                openUri(uriString)?.use { input -> input.copyTo(output) } ?: error("无法读取图片")
            } ?: error("无法写入图片")
            "已保存到相册"
        }
    }

    suspend fun loadImageBytes(uriString: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { openUri(uriString)?.use { it.readBytes() } }.getOrNull()
    }

    suspend fun loadImageBitmap(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openUri(uriString)?.use { it.readBytes() } ?: return@runCatching null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
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
