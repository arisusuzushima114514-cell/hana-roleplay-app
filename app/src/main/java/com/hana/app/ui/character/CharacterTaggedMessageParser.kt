package com.hana.app.ui.character

data class CharacterVisibleSegment(val characterName: String?, val text: String)
data class CharacterInnerThought(val characterName: String?, val text: String)
data class ParsedCharacterMessage(
    val visibleSegments: List<CharacterVisibleSegment>,
    val innerThoughts: List<CharacterInnerThought>
)

fun parseCharacterTaggedMessage(content: String, primaryName: String?): ParsedCharacterMessage {
    val innerRegex = Regex(
        "<inner(?:\\s+character\\s*=\\s*['\"]([^'\"]+)['\"])?\\s*>(.*?)</inner>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val thoughts = mutableListOf<CharacterInnerThought>()
    var withoutInner = innerRegex.replace(content) { match ->
        val text = match.groupValues[2].trim()
        if (text.isNotBlank()) thoughts += CharacterInnerThought(match.groupValues[1].trim().ifBlank { primaryName }, text)
        ""
    }
    val legacyInnerPatterns = listOf(
        Regex("【内心】(.*?)(?:【/内心】|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("（内心[:：](.*?)）", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\(内心[:：](.*?)\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    )
    legacyInnerPatterns.forEach { regex ->
        withoutInner = regex.replace(withoutInner) { match ->
            match.groupValues[1].trim().takeIf { it.isNotBlank() }
                ?.let { thoughts += CharacterInnerThought(primaryName, it) }
            ""
        }
    }
    // A streaming response can end in a partial inner tag. Hide the unfinished tail
    // instead of briefly rendering private content as public dialogue.
    withoutInner = withoutInner.replace(
        Regex("<inner(?:\\s+character\\s*=\\s*['\"][^'\"]*['\"])?\\s*>[^<]*$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        ""
    )
    val speakerPrefix = Regex("^\\s*([^：:\\n]{1,32})\\s*[：:]\\s*(.*)$")
    val segments = mutableListOf<CharacterVisibleSegment>()
    val pending = StringBuilder()
    var activeSpeaker = primaryName
    fun flush() {
        pending.toString().trim().takeIf(String::isNotBlank)?.let { segments += CharacterVisibleSegment(activeSpeaker, it) }
        pending.clear()
    }
    withoutInner.lines().forEach { line ->
        val match = speakerPrefix.matchEntire(line)
        if (match != null) {
            flush()
            activeSpeaker = match.groupValues[1].trim().ifBlank { primaryName }
            pending.append(match.groupValues[2].trim())
        } else if (line.isNotBlank()) {
            if (pending.isNotEmpty()) pending.append('\n')
            pending.append(line.trim())
        }
    }
    flush()
    return ParsedCharacterMessage(segments, thoughts.distinctBy { it.characterName to it.text })
}
