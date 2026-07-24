package com.hana.app.data.db.entity

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val CHARACTER_MODE_SINGLE = "single"
const val CHARACTER_MODE_MULTI = "multi"
const val EMPTY_SUB_CHARACTERS_JSON = "{\"version\":1,\"profiles\":[]}"

data class SubCharacterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val greeting: String = ""
)

fun parseSubCharacterProfiles(raw: String): List<SubCharacterProfile> = runCatching {
    val root = JSONObject(raw.ifBlank { EMPTY_SUB_CHARACTERS_JSON })
    val profiles = root.optJSONArray("profiles") ?: JSONArray()
    (0 until profiles.length()).mapNotNull { index ->
        val item = profiles.optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("name").trim()
        if (name.isBlank()) return@mapNotNull null
        SubCharacterProfile(
            id = item.optString("id").trim().ifBlank { UUID.randomUUID().toString() },
            name = name,
            description = item.optString("description").trim(),
            greeting = item.optString("greeting").trim()
        )
    }
}.getOrDefault(emptyList())

fun serializeSubCharacterProfiles(profiles: List<SubCharacterProfile>): String = JSONObject().apply {
    put("version", 1)
    put("profiles", JSONArray().apply {
        profiles.filter { it.name.isNotBlank() }.forEach { profile ->
            put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name.trim())
                put("description", profile.description.trim())
                put("greeting", profile.greeting.trim())
            })
        }
    })
}.toString()

fun CharacterCardEntity.subCharacters(): List<SubCharacterProfile> =
    if (characterMode == CHARACTER_MODE_MULTI) parseSubCharacterProfiles(subCharactersJson) else emptyList()
