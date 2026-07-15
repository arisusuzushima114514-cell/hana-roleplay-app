package com.hana.app.ui.chat

import org.json.JSONArray
import org.json.JSONObject

private const val ATTACHMENT_PREFIX = "[[hana-attachments]]"

data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val uri: String,
    val kind: AttachmentKind,
    val sizeBytes: Long = 0L,
    val previewText: String = "",
    val caption: String = ""
)

enum class AttachmentKind {
    IMAGE,
    FILE
}

data class DecodedChatContent(
    val text: String,
    val attachments: List<ChatAttachment>
)

fun encodeChatContent(text: String, attachments: List<ChatAttachment>): String {
    if (attachments.isEmpty()) return text.trim()
    val json = JSONObject()
        .put("text", text.trim())
        .put(
            "attachments",
            JSONArray().also { array ->
                attachments.forEach { attachment ->
                    array.put(
                        JSONObject()
                            .put("name", attachment.name)
                            .put("mimeType", attachment.mimeType)
                            .put("uri", attachment.uri)
                            .put("kind", attachment.kind.name)
                            .put("sizeBytes", attachment.sizeBytes)
                            .put("previewText", attachment.previewText)
                            .put("caption", attachment.caption)
                    )
                }
            }
        )
    return ATTACHMENT_PREFIX + json.toString()
}

fun decodeChatContent(content: String): DecodedChatContent {
    if (!content.startsWith(ATTACHMENT_PREFIX)) {
        return DecodedChatContent(text = content, attachments = emptyList())
    }
    return runCatching {
        val payload = JSONObject(content.removePrefix(ATTACHMENT_PREFIX))
        val attachments = payload.optJSONArray("attachments")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ChatAttachment(
                            name = item.optString("name"),
                            mimeType = item.optString("mimeType"),
                            uri = item.optString("uri"),
                            kind = runCatching { AttachmentKind.valueOf(item.optString("kind")) }.getOrDefault(AttachmentKind.FILE),
                            sizeBytes = item.optLong("sizeBytes"),
                            previewText = item.optString("previewText"),
                            caption = item.optString("caption")
                        )
                    )
                }
            }
        }.orEmpty()
        DecodedChatContent(
            text = payload.optString("text"),
            attachments = attachments
        )
    }.getOrDefault(DecodedChatContent(text = content, attachments = emptyList()))
}
