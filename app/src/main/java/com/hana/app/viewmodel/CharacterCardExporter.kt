package com.hana.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.subCharacters
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/** 角色卡 PNG 导出工具：纯函数，无 ViewModel 依赖 */
object CharacterCardExporter {

    fun buildCharacterCardPayload(character: CharacterCardEntity): JSONObject {
        return JSONObject().apply {
            put("name", character.name)
            put("description", character.description)
            put("personality", character.userPersona)
            put("scenario", character.description)
            put("first_mes", character.greeting)
            put("mes_example", "")
            put("creator_notes", character.userPersona)
            put("tags", JSONArray(parseTags(character.tags)))
            put("metadata", JSONObject().apply {
                put("source", "Hana")
                put("id", character.id)
                put("modelId", character.modelId)
                put("temperature", character.temperature)
                put("characterMode", character.characterMode)
                put("subCharacters", JSONArray().apply {
                    character.subCharacters().forEach { profile ->
                        put(JSONObject().apply {
                            put("id", profile.id)
                            put("name", profile.name)
                            put("description", profile.description)
                            put("greeting", profile.greeting)
                        })
                    }
                })
            })
            put("avatar", character.avatarUrl)
            put("id", character.id)
            put("createdAt", character.createdAt)
            put("updatedAt", character.updatedAt)
        }
    }

    private fun parseTags(tags: String): List<String> =
        tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun buildDefaultCharacterCover(character: CharacterCardEntity): Bitmap {
        val bitmap = Bitmap.createBitmap(768, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 768f, 1024f,
            intArrayOf(Color.parseColor("#7C4DFF"), Color.parseColor("#FF6F91"), Color.parseColor("#FFC75F")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 768f, 1024f, paint)
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, 12, 12, 20)
        }
        canvas.drawRect(40f, 700f, 728f, 944f, overlayPaint)
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 72f; isFakeBoldText = true
        }
        val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255); textSize = 34f
        }
        canvas.drawText(character.name.take(18), 72f, 800f, namePaint)
        drawMultilineText(canvas, character.description.ifBlank { character.greeting }.take(120), descPaint, Rect(72, 830, 696, 920))
        return bitmap
    }

    private fun drawMultilineText(canvas: Canvas, text: String, paint: Paint, bounds: Rect) {
        if (text.isBlank()) return
        val maxCharsPerLine = 18
        val lines = text.chunked(maxCharsPerLine).take(3)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, bounds.left.toFloat(), bounds.top + paint.textSize * (index + 1), paint)
        }
    }

    fun buildCharacterCardPngBytes(bitmap: Bitmap, cardJson: String): ByteArray {
        val pngStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngStream)
        val pngBytes = pngStream.toByteArray()
        val charaPayload = android.util.Base64.encodeToString(cardJson.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        return insertTextChunkIntoPng(pngBytes, "chara", charaPayload)
    }

    private fun insertTextChunkIntoPng(pngBytes: ByteArray, keyword: String, value: String): ByteArray {
        require(pngBytes.size > 12) { "PNG 数据无效" }
        val output = ByteArrayOutputStream()
        output.write(pngBytes, 0, 8)
        var offset = 8
        var inserted = false
        while (offset < pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val chunkTotal = length + 12
            val chunkType = String(pngBytes, offset + 4, 4, StandardCharsets.ISO_8859_1)
            output.write(pngBytes, offset, chunkTotal)
            offset += chunkTotal
            if (!inserted && chunkType == "IHDR") {
                output.write(buildTextChunk(keyword, value))
                inserted = true
            }
        }
        return output.toByteArray()
    }

    private fun buildTextChunk(keyword: String, value: String): ByteArray {
        val data = ByteArrayOutputStream().apply {
            write(keyword.toByteArray(StandardCharsets.ISO_8859_1))
            write(0)
            write(value.toByteArray(StandardCharsets.ISO_8859_1))
        }.toByteArray()
        val type = "tEXt".toByteArray(StandardCharsets.ISO_8859_1)
        val crc = CRC32().apply {
            update(type)
            update(data)
        }.value.toInt()
        return ByteArrayOutputStream().apply {
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
            write(type)
            write(data)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc).array())
        }.toByteArray()
    }
}
