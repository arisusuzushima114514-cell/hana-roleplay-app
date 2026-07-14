package com.hana.app.data.repository

import android.util.Log
import android.util.Base64
import com.hana.app.data.db.dao.CharacterCardDao
import com.hana.app.data.db.entity.CharacterCardEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

class CharacterRepository(
    private val dao: CharacterCardDao
) {
    fun observeCharacters(): Flow<List<CharacterCardEntity>> = dao.observeAll()

    suspend fun ensurePresetCharacters() {}

    suspend fun getAll(): List<CharacterCardEntity> = dao.getAll()
    suspend fun getById(id: String): CharacterCardEntity? = dao.getById(id)

    suspend fun save(character: CharacterCardEntity): Boolean {
        val entity = character.copy(
            updatedAt = System.currentTimeMillis(),
            name = character.name.trim(),
            description = character.description.trim(),
            greeting = character.greeting.trim(),
            userPersona = character.userPersona.trim(),
            tags = character.tags.trim(),
            modelId = character.modelId.trim(),
            temperature = character.temperature.coerceIn(0f, 2f),
            lastMessagePreview = character.lastMessagePreview.trim()
        )
        return try {
            dao.insert(entity)
            true
        } catch (e: Exception) {
            Log.e("CharacterRepo", "Save FAILED: id=${character.id} name=${character.name} err=${e.message}", e)
            false
        }
    }

    suspend fun delete(character: CharacterCardEntity) { dao.delete(character) }

    suspend fun updateLastMessage(characterId: String, preview: String) {
        val entity = dao.getById(characterId) ?: return
        dao.update(entity.copy(lastMessageAt = System.currentTimeMillis(), lastMessagePreview = preview))
    }

    suspend fun importCharacterCardJson(json: String): CharacterCardEntity {
        val normalized = json.trim().removePrefix("\uFEFF")
        require(normalized.isNotBlank()) { "JSON 文件内容为空" }

        val imported = when {
            normalized.startsWith("[") -> parseFromArray(JSONArray(normalized))
            else -> parseFromObject(JSONObject(normalized))
        }

        val now = System.currentTimeMillis()
        val existing = imported.id.takeIf { it.isNotBlank() }?.let { dao.getById(it) }
        val entity = imported.copy(
            id = existing?.id ?: imported.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = existing?.createdAt ?: imported.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
            lastMessageAt = existing?.lastMessageAt ?: imported.lastMessageAt,
            lastMessagePreview = existing?.lastMessagePreview ?: imported.lastMessagePreview
        )
        save(entity)
        return entity
    }

    suspend fun importCharacterCardBytes(bytes: ByteArray): CharacterCardEntity {
        require(bytes.isNotEmpty()) { "文件内容为空" }
        return if (isPng(bytes)) {
            val json = extractCharacterJsonFromPng(bytes)
            importCharacterCardJson(json)
        } else {
            importCharacterCardJson(bytes.toString(Charsets.UTF_8))
        }
    }

    suspend fun getAllTags(): List<String> =
        dao.getAll().flatMap { parseTags(it.tags) }.distinct().sorted()

    private fun parseFromArray(array: JSONArray): CharacterCardEntity {
        require(array.length() > 0) { "JSON 数组为空，找不到角色卡" }
        val candidate = (0 until array.length())
            .mapNotNull { index -> array.opt(index) as? JSONObject }
            .firstNotNullOfOrNull { obj -> runCatching { parseFromObject(obj) }.getOrNull() }
        return candidate ?: error("JSON 数组里没有可识别的角色卡")
    }

    private fun parseFromObject(root: JSONObject): CharacterCardEntity {
        val payload = root.optJSONObject("data")
            ?: root.optJSONObject("character")
            ?: root.optJSONObject("card")
            ?: root

        val now = System.currentTimeMillis()
        val name = firstNonBlank(
            payload.optString("name"),
            root.optString("name"),
            payload.optString("char_name"),
            payload.optString("title")
        )
        require(name.isNotBlank()) { "没有识别到角色名，无法导入角色卡" }

        val description = joinNonBlank(
            firstNonBlank(
                payload.optString("description"),
                root.optString("description"),
                payload.optString("desc"),
                payload.optString("persona"),
                payload.optString("character_description")
            ),
            firstNonBlank(
                payload.optString("personality"),
                payload.optString("scenario"),
                payload.optString("world_scenario")
            )?.takeIf { it.isNotBlank() }
                ?.let { "补充设定：$it" },
            firstNonBlank(
                payload.optString("mes_example"),
                payload.optString("example_dialogue"),
                payload.optString("example_dialogues")
            )?.takeIf { it.isNotBlank() }
                ?.let { "示例对话：\n$it" }
        )

        val greeting = firstNonBlank(
            payload.optString("greeting"),
            payload.optString("first_mes"),
            payload.optString("first_message"),
            root.optString("greeting")
        )

        val userPersona = firstNonBlank(
            payload.optString("userPersona"),
            payload.optString("user_persona"),
            payload.optString("creator_notes"),
            root.optString("userPersona")
        )

        val tags = parseTagsField(payload, root)
        val avatarUrl = firstNonBlank(
            payload.optString("avatarUrl"),
            payload.optString("avatar"),
            payload.optString("image"),
            root.optString("avatarUrl")
        )

        val temperature = firstFloat(
            payload.opt("temperature"),
            root.opt("temperature"),
            payload.opt("temp")
        ) ?: 0.9f

        return CharacterCardEntity(
            id = firstNonBlank(
                payload.optString("id"),
                root.optString("id"),
                payload.optString("character_id")
            ).ifBlank { UUID.randomUUID().toString() },
            name = name,
            avatarUrl = avatarUrl,
            description = description.ifBlank { "未提供详细设定" },
            greeting = greeting,
            userPersona = userPersona,
            tags = tags,
            modelId = firstNonBlank(payload.optString("modelId"), root.optString("modelId")),
            temperature = temperature.coerceIn(0f, 2f),
            createdAt = firstLong(payload.opt("createdAt"), root.opt("createdAt")) ?: now,
            updatedAt = firstLong(payload.opt("updatedAt"), root.opt("updatedAt")) ?: now
        )
    }

    private fun parseTagsField(payload: JSONObject, root: JSONObject): String {
        val direct = firstNonBlank(payload.optString("tags"), root.optString("tags"))
        if (direct.isNotBlank()) return direct
        val arrays = listOfNotNull(payload.optJSONArray("tags"), root.optJSONArray("tags"))
        arrays.forEach { array ->
            val tags = (0 until array.length()).mapNotNull { index -> array.optString(index).trim().takeIf { it.isNotBlank() } }
            if (tags.isNotEmpty()) return tags.joinToString(",")
        }
        return ""
    }

    private fun isPng(bytes: ByteArray): Boolean {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        return bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }
    }

    private fun extractCharacterJsonFromPng(bytes: ByteArray): String {
        val stream = DataInputStream(ByteArrayInputStream(bytes))
        val signature = ByteArray(8)
        stream.readFully(signature)
        while (stream.available() > 0) {
            val length = stream.readInt()
            val typeBytes = ByteArray(4)
            stream.readFully(typeBytes)
            val type = String(typeBytes, StandardCharsets.ISO_8859_1)
            val data = ByteArray(length)
            stream.readFully(data)
            stream.skipBytes(4)

            if (type == "tEXt" || type == "iTXt") {
                extractCharacterPayloadFromTextChunk(type, data)?.let { return it }
            }
        }
        error("这个 PNG 里没有识别到角色卡 metadata")
    }

    private fun extractCharacterPayloadFromTextChunk(type: String, data: ByteArray): String? {
        val zeroIndex = data.indexOf(0)
        if (zeroIndex <= 0) return null
        val keyword = String(data, 0, zeroIndex, StandardCharsets.ISO_8859_1)
        if (keyword.lowercase() !in setOf("chara", "ccv3")) return null

        val rawText = when (type) {
            "tEXt" -> String(data, zeroIndex + 1, data.size - zeroIndex - 1, StandardCharsets.ISO_8859_1)
            "iTXt" -> extractInternationalTextValue(data, zeroIndex + 1)
            else -> null
        }?.trim().orEmpty()

        require(rawText.isNotBlank()) { "PNG 里的角色卡 metadata 为空" }
        return decodeCharacterPayload(rawText)
    }

    private fun extractInternationalTextValue(data: ByteArray, startIndex: Int): String? {
        var index = startIndex
        if (index + 2 >= data.size) return null
        index += 2
        while (index < data.size && data[index].toInt() != 0) index++
        index++
        while (index < data.size && data[index].toInt() != 0) index++
        index++
        if (index >= data.size) return null
        return String(data, index, data.size - index, StandardCharsets.UTF_8)
    }

    private fun decodeCharacterPayload(rawText: String): String {
        if (rawText.startsWith("{") || rawText.startsWith("[")) return rawText
        val normalized = rawText.replace("\n", "").replace("\r", "")
        return runCatching {
            String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse {
            error("PNG 里的角色卡 metadata 不是可识别的 JSON/base64")
        }
    }
}

fun parseTags(tagsString: String): List<String> =
    tagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }

private fun firstNonBlank(vararg values: String?): String {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}

private fun firstLong(vararg values: Any?): Long? {
    return values.firstNotNullOfOrNull { value ->
        when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    }
}

private fun firstFloat(vararg values: Any?): Float? {
    return values.firstNotNullOfOrNull { value ->
        when (value) {
            is Number -> value.toFloat()
            is String -> value.trim().toFloatOrNull()
            else -> null
        }
    }
}

private fun joinNonBlank(vararg parts: String?): String {
    return parts.mapNotNull { it?.trim()?.takeIf { part -> part.isNotBlank() } }.joinToString("\n\n")
}
