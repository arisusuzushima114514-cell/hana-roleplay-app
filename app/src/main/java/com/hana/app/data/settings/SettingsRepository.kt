package com.hana.app.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.hana.app.ui.theme.ThemeMode
import com.hana.app.ui.theme.ThemePalette
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private fun normalizeSecret(value: String): String = value.trim().trim('"', '\'', ' ', '\n', '\r', '\t')

fun interCharacterRelationKey(fromCharacterId: String, toCharacterId: String): String =
    "$fromCharacterId->$toCharacterId"

val DEFAULT_QUICK_PHRASES = listOf("继续", "更详细", "换个方式", "总结")

@androidx.compose.runtime.Stable
data class SettingsData(
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    val apiKey: String = "",
    val selectedModel: String = DEFAULT_MODEL_NAME,
    val language: String = DEFAULT_LANGUAGE_CODE,
    val timeoutSeconds: Int = 60,
    val historicalTokens: Long = 0L,
    val quickPhrases: List<String> = DEFAULT_QUICK_PHRASES,
    val visionBaseUrl: String = "",
    val visionApiKey: String = "",
    val visionModelName: String = "",
    val summaryBaseUrl: String = "",
    val summaryApiKey: String = "",
    val summaryModelName: String = "",
    val autoSummaryThreshold: Int = DEFAULT_AUTO_SUMMARY_THRESHOLD,
    val supportsImage: Boolean = false,
    val supportsFile: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: ThemePalette = ThemePalette.VIOLET,
    val voiceInputEnabled: Boolean = true,
    val streamEnabled: Boolean = true,
    val searchIndependentMode: Boolean = true,
    val searchProviderUrl: String = "",
    val searchProviderKey: String = "",
    val personaEnabled: Boolean = false,
    val personaPrompt: String = "",
    val webSearchEnabled: Boolean = false,
    val creativePresetText: String = "",
    val characterCreativePresetEnabled: Map<String, Boolean> = emptyMap(),
    val characterCreativePresetAffectsPersona: Map<String, Boolean> = emptyMap(),
    val characterCreativePresetTexts: Map<String, String> = emptyMap(),
    val characterIndependentCreativePresetEnabled: Map<String, Boolean> = emptyMap(),
    val characterIndependentCreativePresetAffectsPersona: Map<String, Boolean> = emptyMap(),
    val autoThemeSuggestionEnabled: Boolean = true,
    val lastAutoThemeSuggestionTag: String = "",
    val imageProviderId: Long = 0L,
    val imageModelName: String = "",
    val backgroundIntensity: String = "soft",
    val characterStoryStateMigrationVersion: Int = 0,
    val characterStoryStates: Map<String, CharacterStoryState> = emptyMap(),
    val characterStoryLogs: Map<String, List<CharacterStoryLogEntry>> = emptyMap(),
    val interCharacterRelations: Map<String, InterCharacterRelationState> = emptyMap()
)

@androidx.compose.runtime.Stable
data class CharacterStoryState(
    val affection: Int = 0,
    val trust: Int = 0,
    val tension: Int = 20,
    val relationshipAnchor: String = "未知",
    val relationshipAnchorLocked: Boolean = false,
    val intimacyBaseline: Int = 0,
    val relationshipMomentum: Int = 0,
    val progressNote: String = "仍在起步",
    val statusNote: String = "情绪平稳",
    val recentEventSummary: String = "",
    val baselineMessageTimestamp: Long = 0L
)

@androidx.compose.runtime.Stable
data class CharacterStoryLogEntry(
    val id: String,
    val timestamp: Long,
    val title: String,
    val note: String,
    val affection: Int,
    val trust: Int,
    val tension: Int
)

@androidx.compose.runtime.Stable
data class InterCharacterRelationState(
    val affinity: Int = 0,
    val rivalry: Int = 0,
    val tension: Int = 10,
    val relationLabel: String = "普通同伴",
    val recentEvent: String = "",
    val updatedAt: Long = 0L
)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = companionDataStore(appContext)

    @Volatile
    private var cachedSettings: SettingsData = SettingsData()

    val settingsFlow: Flow<SettingsData> = dataStore.data
        .catch { e ->
            Log.e("SettingsRepo", "DataStore flow error, emitting defaults", e)
            emit(emptyPreferences())
        }
        .map { preferences ->
        try {
        SettingsData(
            apiBaseUrl = preferences[KEY_API_BASE_URL] ?: DEFAULT_API_BASE_URL,
            apiKey = preferences[KEY_API_KEY] ?: "",
            selectedModel = preferences[KEY_SELECTED_MODEL].orEmpty(),
            language = preferences[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE_CODE,
            timeoutSeconds = preferences[KEY_TIMEOUT_SECONDS] ?: 60,
            historicalTokens = preferences[KEY_HISTORICAL_TOKENS] ?: 0L,
            quickPhrases = (preferences[KEY_QUICK_PHRASES] ?: DEFAULT_QUICK_PHRASES.joinToString("\n"))
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() },
            visionBaseUrl = preferences[KEY_VISION_BASE_URL] ?: "",
            visionApiKey = preferences[KEY_VISION_API_KEY] ?: "",
            visionModelName = preferences[KEY_VISION_MODEL_NAME] ?: "",
            summaryBaseUrl = preferences[KEY_SUMMARY_BASE_URL] ?: "",
            summaryApiKey = preferences[KEY_SUMMARY_API_KEY] ?: "",
            summaryModelName = preferences[KEY_SUMMARY_MODEL_NAME] ?: "",
            autoSummaryThreshold = (preferences[KEY_AUTO_SUMMARY_THRESHOLD] ?: DEFAULT_AUTO_SUMMARY_THRESHOLD)
                .coerceIn(4, 500),
            supportsImage = preferences[KEY_SUPPORTS_IMAGE] ?: false,
            supportsFile = preferences[KEY_SUPPORTS_FILE] ?: false,
            themeMode = ThemeMode.fromValue(preferences[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.value),
            themePalette = ThemePalette.fromValue(preferences[KEY_THEME_PALETTE] ?: ThemePalette.VIOLET.value),
            voiceInputEnabled = preferences[KEY_VOICE_INPUT_ENABLED] ?: true,
            streamEnabled = preferences[KEY_STREAM_ENABLED] ?: true,
            searchIndependentMode = preferences[KEY_SEARCH_MODE] ?: true,
            searchProviderUrl = preferences[KEY_SEARCH_URL] ?: "",
            searchProviderKey = preferences[KEY_SEARCH_KEY] ?: "",
            personaEnabled = preferences[KEY_PERSONA_ENABLED] ?: false,
            personaPrompt = preferences[KEY_PERSONA_PROMPT] ?: "",
            webSearchEnabled = preferences[KEY_WEB_SEARCH_ENABLED] ?: false,
            creativePresetText = preferences[KEY_CREATIVE_PRESET_TEXT] ?: "",
            characterCreativePresetEnabled = parseCharacterCreativePresetEnabled(preferences[KEY_CHARACTER_CREATIVE_PRESET_ENABLED].orEmpty()),
            characterCreativePresetAffectsPersona = parseCharacterCreativePresetEnabled(preferences[KEY_CHARACTER_CREATIVE_PRESET_AFFECTS_PERSONA].orEmpty()),
            characterCreativePresetTexts = parseCharacterTextMap(preferences[KEY_CHARACTER_CREATIVE_PRESET_TEXTS].orEmpty()),
            characterIndependentCreativePresetEnabled = parseCharacterCreativePresetEnabled(preferences[KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_ENABLED].orEmpty()),
            characterIndependentCreativePresetAffectsPersona = parseCharacterCreativePresetEnabled(preferences[KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_AFFECTS_PERSONA].orEmpty()),
            autoThemeSuggestionEnabled = preferences[KEY_AUTO_THEME_SUGGESTION_ENABLED] ?: true,
            lastAutoThemeSuggestionTag = preferences[KEY_LAST_AUTO_THEME_SUGGESTION_TAG] ?: "",
            imageProviderId = preferences[KEY_IMAGE_PROVIDER_ID] ?: 0L,
            imageModelName = preferences[KEY_IMAGE_MODEL_NAME] ?: "",
            backgroundIntensity = preferences[KEY_BACKGROUND_INTENSITY] ?: "soft",
            characterStoryStateMigrationVersion = preferences[KEY_CHARACTER_STORY_STATE_MIGRATION_VERSION] ?: 0,
            characterStoryStates = parseCharacterStoryStates(preferences[KEY_CHARACTER_STORY_STATES].orEmpty()),
            characterStoryLogs = parseCharacterStoryLogs(preferences[KEY_CHARACTER_STORY_LOGS].orEmpty()),
            interCharacterRelations = parseInterCharacterRelations(preferences[KEY_INTER_CHARACTER_RELATIONS].orEmpty())
        ).also { cachedSettings = it }
        } catch (e: Exception) {
            Log.e("SettingsRepo", "Parse preferences failed, using defaults", e)
            SettingsData().also { cachedSettings = it }
        }
    }

    val languageFlow: Flow<String> = settingsFlow.map { it.language }

    /** 获取缓存的 settings，避免每次调用都走 DataStore 的 first() */
    suspend fun getSettings(): SettingsData {
        return cachedSettings
    }

    suspend fun getApiSettings(): ApiSettings {
        return try {
            val settings = getSettings()
            ApiSettings(
                baseUrl = settings.apiBaseUrl,
                apiKey = settings.apiKey,
                modelName = settings.selectedModel
            )
        } catch (e: Exception) {
            Log.e("SettingsRepo", "getApiSettings failed", e)
            ApiSettings("", "", "")
        }
    }

    suspend fun getVisionApiSettings(): ApiSettings {
        return try {
            val settings = getSettings()
            ApiSettings(
                baseUrl = settings.visionBaseUrl,
                apiKey = settings.visionApiKey,
                modelName = settings.visionModelName,
                isVisionConfig = true
            )
        } catch (e: Exception) {
            Log.e("SettingsRepo", "getVisionApiSettings failed", e)
            ApiSettings("", "", "", isVisionConfig = true)
        }
    }

    suspend fun getSummaryApiSettings(): ApiSettings {
        val settings = getSettings()
        return ApiSettings(
            baseUrl = settings.summaryBaseUrl,
            apiKey = settings.summaryApiKey,
            modelName = settings.summaryModelName
        )
    }

    suspend fun saveApiSettings(settings: ApiSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_API_BASE_URL] = settings.baseUrl.trim().trimEnd('/')
            preferences[KEY_API_KEY] = normalizeSecret(settings.apiKey)
            preferences[KEY_SELECTED_MODEL] = settings.modelName.trim()
        }
    }

    suspend fun saveLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[KEY_LANGUAGE] = languageCode
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_THEME_MODE] = mode.value
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveThemeMode failed", e) }
    }

    suspend fun saveThemePalette(palette: ThemePalette) {
        cachedSettings = cachedSettings.copy(themePalette = palette)
        try {
            dataStore.edit { preferences -> preferences[KEY_THEME_PALETTE] = palette.value }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveThemePalette failed", e) }
    }

    suspend fun saveBaseUrl(baseUrl: String) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_API_BASE_URL] = baseUrl.trim()
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveBaseUrl failed", e) }
    }

    suspend fun saveApiKey(apiKey: String) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_API_KEY] = normalizeSecret(apiKey)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveApiKey failed", e) }
    }

    suspend fun saveSelectedModel(model: String) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_SELECTED_MODEL] = model.trim()
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveSelectedModel failed", e) }
    }

    suspend fun saveTimeoutSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_TIMEOUT_SECONDS] = seconds.coerceIn(5, 300)
        }
    }

    suspend fun saveQuickPhrases(phrases: List<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_QUICK_PHRASES] = phrases
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
        }
    }

    suspend fun saveVisionSettings(baseUrl: String, apiKey: String, modelName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_VISION_BASE_URL] = baseUrl.trim().trimEnd('/')
            preferences[KEY_VISION_API_KEY] = normalizeSecret(apiKey)
            preferences[KEY_VISION_MODEL_NAME] = modelName.trim()
        }
    }

    suspend fun saveSummarySettings(baseUrl: String, apiKey: String, modelName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SUMMARY_BASE_URL] = baseUrl.trim().trimEnd('/')
            preferences[KEY_SUMMARY_API_KEY] = normalizeSecret(apiKey)
            preferences[KEY_SUMMARY_MODEL_NAME] = modelName.trim()
        }
    }

    suspend fun saveAutoSummaryThreshold(threshold: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_SUMMARY_THRESHOLD] = threshold.coerceIn(4, 500)
        }
    }

    suspend fun saveSupportsImage(value: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_SUPPORTS_IMAGE] = value }
    }

    suspend fun saveSupportsFile(value: Boolean) {
        try { dataStore.edit { preferences -> preferences[KEY_SUPPORTS_FILE] = value } } catch (e: Exception) { Log.e("SettingsRepo", "saveSupportsFile", e) }
    }

    suspend fun saveVoiceInputEnabled(value: Boolean) {
        try { dataStore.edit { preferences -> preferences[KEY_VOICE_INPUT_ENABLED] = value } } catch (e: Exception) { Log.e("SettingsRepo", "saveVoiceInput", e) }
    }

    suspend fun saveStreamEnabled(value: Boolean) {
        try { dataStore.edit { p -> p[KEY_STREAM_ENABLED] = value } } catch (e: Exception) { Log.e("SettingsRepo", "saveStream", e) }
    }

    suspend fun saveSearchSettings(url: String, key: String, mode: Boolean) {
        try {
            dataStore.edit { p ->
                p[KEY_SEARCH_URL] = url.trim()
                p[KEY_SEARCH_KEY] = key.trim()
                p[KEY_SEARCH_MODE] = mode
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveSearchSettings", e) }
    }

    suspend fun savePersonaSettings(enabled: Boolean, prompt: String) {
        try {
            dataStore.edit { p ->
                p[KEY_PERSONA_ENABLED] = enabled
                p[KEY_PERSONA_PROMPT] = prompt.trim()
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "savePersonaSettings", e) }
    }

    suspend fun saveWebSearchEnabled(enabled: Boolean) {
        try {
            dataStore.edit { p -> p[KEY_WEB_SEARCH_ENABLED] = enabled }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveWebSearchEnabled", e) }
    }

    suspend fun saveCharacterCreativePresetEnabled(characterId: String, enabled: Boolean) {
        cachedSettings = cachedSettings.copy(
            characterCreativePresetEnabled = cachedSettings.characterCreativePresetEnabled.toMutableMap().apply {
                put(characterId, enabled)
            }
        )
        try {
            dataStore.edit { p ->
                val current = parseCharacterCreativePresetEnabled(p[KEY_CHARACTER_CREATIVE_PRESET_ENABLED].orEmpty()).toMutableMap()
                current[characterId] = enabled
                p[KEY_CHARACTER_CREATIVE_PRESET_ENABLED] = serializeCharacterCreativePresetEnabled(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCharacterCreativePresetEnabled", e) }
    }

    suspend fun saveCharacterCreativePresetAffectsPersona(characterId: String, enabled: Boolean) {
        cachedSettings = cachedSettings.copy(
            characterCreativePresetAffectsPersona = cachedSettings.characterCreativePresetAffectsPersona.toMutableMap().apply {
                put(characterId, enabled)
            }
        )
        try {
            dataStore.edit { p ->
                val current = parseCharacterCreativePresetEnabled(p[KEY_CHARACTER_CREATIVE_PRESET_AFFECTS_PERSONA].orEmpty()).toMutableMap()
                current[characterId] = enabled
                p[KEY_CHARACTER_CREATIVE_PRESET_AFFECTS_PERSONA] = serializeCharacterCreativePresetEnabled(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCharacterCreativePresetAffectsPersona", e) }
    }

    suspend fun saveCreativePresetText(value: String) {
        cachedSettings = cachedSettings.copy(creativePresetText = value.trim())
        try {
            dataStore.edit { p -> p[KEY_CREATIVE_PRESET_TEXT] = value.trim() }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCreativePresetText", e) }
    }

    suspend fun saveCharacterCreativePresetText(characterId: String, value: String) {
        val normalized = value.trim()
        cachedSettings = cachedSettings.copy(
            characterCreativePresetTexts = cachedSettings.characterCreativePresetTexts.toMutableMap().apply {
                if (normalized.isBlank()) remove(characterId) else put(characterId, normalized)
            }
        )
        try {
            dataStore.edit { p ->
                val current = parseCharacterTextMap(p[KEY_CHARACTER_CREATIVE_PRESET_TEXTS].orEmpty()).toMutableMap()
                if (normalized.isBlank()) current.remove(characterId) else current[characterId] = normalized
                p[KEY_CHARACTER_CREATIVE_PRESET_TEXTS] = serializeCharacterTextMap(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCharacterCreativePresetText", e) }
    }

    suspend fun saveCharacterIndependentCreativePresetEnabled(characterId: String, enabled: Boolean) {
        cachedSettings = cachedSettings.copy(
            characterIndependentCreativePresetEnabled = cachedSettings.characterIndependentCreativePresetEnabled.toMutableMap().apply {
                put(characterId, enabled)
            }
        )
        try {
            dataStore.edit { p ->
                val current = parseCharacterCreativePresetEnabled(p[KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_ENABLED].orEmpty()).toMutableMap()
                current[characterId] = enabled
                p[KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_ENABLED] = serializeCharacterCreativePresetEnabled(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCharacterIndependentCreativePresetEnabled", e) }
    }

    suspend fun saveCharacterIndependentCreativePresetAffectsPersona(characterId: String, enabled: Boolean) {
        cachedSettings = cachedSettings.copy(
            characterIndependentCreativePresetAffectsPersona = cachedSettings.characterIndependentCreativePresetAffectsPersona.toMutableMap().apply {
                put(characterId, enabled)
            }
        )
        try {
            dataStore.edit { p ->
                val current = parseCharacterCreativePresetEnabled(p[KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_AFFECTS_PERSONA].orEmpty()).toMutableMap()
                current[characterId] = enabled
                p[KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_AFFECTS_PERSONA] = serializeCharacterCreativePresetEnabled(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCharacterIndependentCreativePresetAffectsPersona", e) }
    }

    suspend fun saveAutoThemeSuggestionEnabled(enabled: Boolean) {
        try {
            dataStore.edit { p -> p[KEY_AUTO_THEME_SUGGESTION_ENABLED] = enabled }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveAutoThemeSuggestionEnabled", e) }
    }

    suspend fun saveLastAutoThemeSuggestionTag(tag: String) {
        try {
            dataStore.edit { p -> p[KEY_LAST_AUTO_THEME_SUGGESTION_TAG] = tag.trim() }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveLastAutoThemeSuggestionTag", e) }
    }

    suspend fun saveImageProviderId(providerId: Long) {
        try {
            dataStore.edit { p -> p[KEY_IMAGE_PROVIDER_ID] = providerId.coerceAtLeast(0L) }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveImageProviderId", e) }
    }

    suspend fun saveImageModelName(modelName: String) {
        try {
            dataStore.edit { p -> p[KEY_IMAGE_MODEL_NAME] = modelName.trim() }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveImageModelName", e) }
    }

    suspend fun saveBackgroundIntensity(value: String) {
        try {
            dataStore.edit { p -> p[KEY_BACKGROUND_INTENSITY] = value.trim().ifBlank { "soft" } }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveBackgroundIntensity", e) }
    }

    suspend fun saveCharacterStoryState(characterId: String, state: CharacterStoryState) {
        cachedSettings = cachedSettings.copy(
            characterStoryStates = cachedSettings.characterStoryStates.toMutableMap().apply {
                put(characterId, state)
            }
        )
        try {
            dataStore.edit { p ->
                val current = parseCharacterStoryStates(p[KEY_CHARACTER_STORY_STATES].orEmpty()).toMutableMap()
                current[characterId] = state
                p[KEY_CHARACTER_STORY_STATES] = serializeCharacterStoryStates(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCharacterStoryState", e) }
    }

    suspend fun appendCharacterStoryLog(characterId: String, entry: CharacterStoryLogEntry) {
        cachedSettings = cachedSettings.copy(
            characterStoryLogs = cachedSettings.characterStoryLogs.toMutableMap().apply {
                put(characterId, (get(characterId).orEmpty().toMutableList().apply { add(0, entry) }).take(50))
            }
        )
        try {
            dataStore.edit { p ->
                val current = parseCharacterStoryLogs(p[KEY_CHARACTER_STORY_LOGS].orEmpty()).toMutableMap()
                val updated = current[characterId].orEmpty().toMutableList()
                updated.add(0, entry)
                current[characterId] = updated.take(50)
                p[KEY_CHARACTER_STORY_LOGS] = serializeCharacterStoryLogs(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "appendCharacterStoryLog", e) }
    }

    suspend fun saveCharacterStoryStateMigrationVersion(version: Int) {
        try {
            dataStore.edit { p ->
                p[KEY_CHARACTER_STORY_STATE_MIGRATION_VERSION] = version.coerceAtLeast(0)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveCharacterStoryStateMigrationVersion", e) }
    }

    suspend fun clearCharacterStoryState(characterId: String) {
        cachedSettings = cachedSettings.copy(
            characterStoryStates = cachedSettings.characterStoryStates - characterId,
            characterStoryLogs = cachedSettings.characterStoryLogs - characterId
        )
        try {
            dataStore.edit { p ->
                val states = parseCharacterStoryStates(p[KEY_CHARACTER_STORY_STATES].orEmpty()).toMutableMap()
                states.remove(characterId)
                p[KEY_CHARACTER_STORY_STATES] = serializeCharacterStoryStates(states)

                val logs = parseCharacterStoryLogs(p[KEY_CHARACTER_STORY_LOGS].orEmpty()).toMutableMap()
                logs.remove(characterId)
                p[KEY_CHARACTER_STORY_LOGS] = serializeCharacterStoryLogs(logs)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "clearCharacterStoryState", e) }
    }

    suspend fun saveInterCharacterRelation(fromCharacterId: String, toCharacterId: String, state: InterCharacterRelationState) {
        val key = interCharacterRelationKey(fromCharacterId, toCharacterId)
        cachedSettings = cachedSettings.copy(
            interCharacterRelations = cachedSettings.interCharacterRelations.toMutableMap().apply { put(key, state) }
        )
        try {
            dataStore.edit { p ->
                val current = parseInterCharacterRelations(p[KEY_INTER_CHARACTER_RELATIONS].orEmpty()).toMutableMap()
                current[key] = state
                p[KEY_INTER_CHARACTER_RELATIONS] = serializeInterCharacterRelations(current)
            }
        } catch (e: Exception) { Log.e("SettingsRepo", "saveInterCharacterRelation", e) }
    }

    suspend fun addHistoricalTokens(tokens: Int) {
        if (tokens <= 0) return
        dataStore.edit { preferences ->
            preferences[KEY_HISTORICAL_TOKENS] = (preferences[KEY_HISTORICAL_TOKENS] ?: 0L) + tokens
        }
    }

    /** 获取用户上次已查看过的更新日志版本号 */
    fun getLastSeenChangelogVersion(): kotlinx.coroutines.flow.Flow<String> =
        dataStore.data.map { it[KEY_LAST_SEEN_CHANGELOG_VERSION] ?: "" }

    /** 记录用户已查看当前版本的更新日志，下次不再弹出 */
    suspend fun markChangelogSeen(version: String) {
        dataStore.edit { it[KEY_LAST_SEEN_CHANGELOG_VERSION] = version }
    }

    suspend fun saveAll(
        apiBaseUrl: String,
        apiKey: String,
        selectedModel: String,
        language: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_API_BASE_URL] = apiBaseUrl.trim().trimEnd('/')
            preferences[KEY_API_KEY] = normalizeSecret(apiKey)
            preferences[KEY_SELECTED_MODEL] = selectedModel.trim()
            preferences[KEY_LANGUAGE] = language
        }
    }

    companion object {
        @Volatile
        private var DATA_STORE: DataStore<Preferences>? = null

        private fun companionDataStore(context: Context): DataStore<Preferences> {
            return DATA_STORE ?: synchronized(this) {
                DATA_STORE ?: PreferenceDataStoreFactory.create(
                    produceFile = { context.applicationContext.preferencesDataStoreFile("workspace_settings.preferences_pb") }
                ).also { DATA_STORE = it }
            }
        }

        private val KEY_API_BASE_URL = stringPreferencesKey("api_base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        private val KEY_HISTORICAL_TOKENS = longPreferencesKey("historical_tokens")
        private val KEY_QUICK_PHRASES = stringPreferencesKey("quick_phrases")
        private val KEY_VISION_BASE_URL = stringPreferencesKey("vision_base_url")
        private val KEY_VISION_API_KEY = stringPreferencesKey("vision_api_key")
        private val KEY_VISION_MODEL_NAME = stringPreferencesKey("vision_model_name")
        private val KEY_SUMMARY_BASE_URL = stringPreferencesKey("summary_base_url")
        private val KEY_SUMMARY_API_KEY = stringPreferencesKey("summary_api_key")
        private val KEY_SUMMARY_MODEL_NAME = stringPreferencesKey("summary_model_name")
        private val KEY_AUTO_SUMMARY_THRESHOLD = intPreferencesKey("auto_summary_threshold")
        private val KEY_SUPPORTS_IMAGE = booleanPreferencesKey("supports_image")
        private val KEY_SUPPORTS_FILE = booleanPreferencesKey("supports_file")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")
        private val KEY_VOICE_INPUT_ENABLED = booleanPreferencesKey("voice_input")
        private val KEY_STREAM_ENABLED = booleanPreferencesKey("stream_enabled")
        private val KEY_SEARCH_URL = stringPreferencesKey("search_url")
        private val KEY_SEARCH_KEY = stringPreferencesKey("search_key")
        private val KEY_SEARCH_MODE = booleanPreferencesKey("search_mode")
        private val KEY_PERSONA_ENABLED = booleanPreferencesKey("persona_enabled")
        private val KEY_PERSONA_PROMPT = stringPreferencesKey("persona_prompt")
        private val KEY_WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        private val KEY_CREATIVE_PRESET_TEXT = stringPreferencesKey("creative_preset_text")
        private val KEY_CHARACTER_CREATIVE_PRESET_ENABLED = stringPreferencesKey("character_creative_preset_enabled")
        private val KEY_CHARACTER_CREATIVE_PRESET_AFFECTS_PERSONA = stringPreferencesKey("character_creative_preset_affects_persona")
        private val KEY_CHARACTER_CREATIVE_PRESET_TEXTS = stringPreferencesKey("character_creative_preset_texts")
        private val KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_ENABLED = stringPreferencesKey("character_independent_creative_preset_enabled")
        private val KEY_CHARACTER_INDEPENDENT_CREATIVE_PRESET_AFFECTS_PERSONA = stringPreferencesKey("character_independent_creative_preset_affects_persona")
        private val KEY_AUTO_THEME_SUGGESTION_ENABLED = booleanPreferencesKey("auto_theme_suggestion_enabled")
        private val KEY_LAST_AUTO_THEME_SUGGESTION_TAG = stringPreferencesKey("last_auto_theme_suggestion_tag")
        private val KEY_IMAGE_PROVIDER_ID = longPreferencesKey("image_provider_id")
        private val KEY_IMAGE_MODEL_NAME = stringPreferencesKey("image_model_name")
        private val KEY_BACKGROUND_INTENSITY = stringPreferencesKey("background_intensity")
        private val KEY_CHARACTER_STORY_STATE_MIGRATION_VERSION = intPreferencesKey("character_story_state_migration_version")
        private val KEY_CHARACTER_STORY_STATES = stringPreferencesKey("character_story_states")
        private val KEY_CHARACTER_STORY_LOGS = stringPreferencesKey("character_story_logs")
        private val KEY_INTER_CHARACTER_RELATIONS = stringPreferencesKey("inter_character_relations")
        private val KEY_LAST_SEEN_CHANGELOG_VERSION = stringPreferencesKey("last_seen_changelog_version")

        private fun parseCharacterStoryStates(raw: String): Map<String, CharacterStoryState> {
            if (raw.isBlank()) return emptyMap()
            return runCatching {
                val root = JSONObject(raw)
                buildMap {
                    val iterator = root.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        val item = root.optJSONObject(key) ?: continue
                        put(
                            key,
                            CharacterStoryState(
                                affection = item.optInt("affection", 0).coerceIn(-100, 100),
                                trust = item.optInt("trust", 0).coerceIn(-100, 100),
                                tension = item.optInt("tension", 20).coerceIn(0, 100),
                                relationshipAnchor = item.optString("relationshipAnchor").ifBlank { "未知" },
                                relationshipAnchorLocked = item.optBoolean("relationshipAnchorLocked", false),
                                intimacyBaseline = item.optInt("intimacyBaseline", 0).coerceIn(-100, 100),
                                relationshipMomentum = item.optInt("relationshipMomentum", 0).coerceIn(-100, 100),
                                progressNote = item.optString("progressNote").ifBlank { "仍在起步" },
                                statusNote = item.optString("statusNote").ifBlank { "情绪平稳" },
                                recentEventSummary = item.optString("recentEventSummary"),
                                baselineMessageTimestamp = item.optLong("baselineMessageTimestamp", 0L)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyMap())
        }

        private fun serializeCharacterStoryStates(states: Map<String, CharacterStoryState>): String {
            return JSONObject().also { root ->
                states.forEach { (id, state) ->
                    root.put(
                        id,
                        JSONObject().apply {
                            put("affection", state.affection)
                            put("trust", state.trust)
                            put("tension", state.tension)
                            put("relationshipAnchor", state.relationshipAnchor)
                            put("relationshipAnchorLocked", state.relationshipAnchorLocked)
                            put("intimacyBaseline", state.intimacyBaseline)
                            put("relationshipMomentum", state.relationshipMomentum)
                            put("progressNote", state.progressNote)
                            put("statusNote", state.statusNote)
                            put("recentEventSummary", state.recentEventSummary)
                            put("baselineMessageTimestamp", state.baselineMessageTimestamp)
                        }
                    )
                }
            }.toString()
        }

        private fun parseCharacterCreativePresetEnabled(raw: String): Map<String, Boolean> {
            if (raw.isBlank()) return emptyMap()
            return runCatching {
                val root = JSONObject(raw)
                buildMap {
                    val iterator = root.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        put(key, root.optBoolean(key, false))
                    }
                }
            }.getOrDefault(emptyMap())
        }

        private fun serializeCharacterCreativePresetEnabled(states: Map<String, Boolean>): String {
            return JSONObject().also { root ->
                states.forEach { (characterId, enabled) ->
                    root.put(characterId, enabled)
                }
            }.toString()
        }

        private fun parseCharacterTextMap(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            return runCatching {
                val root = JSONObject(raw)
                buildMap {
                    val iterator = root.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        root.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
                    }
                }
            }.getOrDefault(emptyMap())
        }

        private fun serializeCharacterTextMap(states: Map<String, String>): String {
            return JSONObject().also { root ->
                states.forEach { (characterId, text) -> root.put(characterId, text) }
            }.toString()
        }

        private fun parseCharacterStoryLogs(raw: String): Map<String, List<CharacterStoryLogEntry>> {
            if (raw.isBlank()) return emptyMap()
            return runCatching {
                val root = JSONObject(raw)
                buildMap {
                    val iterator = root.keys()
                    while (iterator.hasNext()) {
                        val characterId = iterator.next()
                        val array = root.optJSONArray(characterId) ?: continue
                        val entries = buildList {
                            for (i in 0 until array.length()) {
                                val obj = array.optJSONObject(i) ?: continue
                                add(
                                    CharacterStoryLogEntry(
                                        id = obj.optString("id"),
                                        timestamp = obj.optLong("timestamp"),
                                        title = obj.optString("title"),
                                        note = obj.optString("note"),
                                        affection = obj.optInt("affection", 35),
                                        trust = obj.optInt("trust", 25),
                                        tension = obj.optInt("tension", 20)
                                    )
                                )
                            }
                        }
                        put(characterId, entries)
                    }
                }
            }.getOrDefault(emptyMap())
        }

        private fun serializeCharacterStoryLogs(logs: Map<String, List<CharacterStoryLogEntry>>): String {
            return JSONObject().also { root ->
                logs.forEach { (characterId, entries) ->
                    val array = org.json.JSONArray()
                    entries.forEach { entry ->
                        array.put(
                            JSONObject().apply {
                                put("id", entry.id)
                                put("timestamp", entry.timestamp)
                                put("title", entry.title)
                                put("note", entry.note)
                                put("affection", entry.affection)
                                put("trust", entry.trust)
                                put("tension", entry.tension)
                            }
                        )
                    }
                    root.put(characterId, array)
                }
            }.toString()
        }

        private fun parseInterCharacterRelations(raw: String): Map<String, InterCharacterRelationState> {
            if (raw.isBlank()) return emptyMap()
            return runCatching {
                val root = JSONObject(raw)
                buildMap {
                    val iterator = root.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        val item = root.optJSONObject(key) ?: continue
                        put(
                            key,
                            InterCharacterRelationState(
                                affinity = item.optInt("affinity", 0).coerceIn(-100, 100),
                                rivalry = item.optInt("rivalry", 0).coerceIn(0, 100),
                                tension = item.optInt("tension", 10).coerceIn(0, 100),
                                relationLabel = item.optString("relationLabel").ifBlank { "普通同伴" },
                                recentEvent = item.optString("recentEvent"),
                                updatedAt = item.optLong("updatedAt", 0L)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyMap())
        }

        private fun serializeInterCharacterRelations(relations: Map<String, InterCharacterRelationState>): String {
            return JSONObject().also { root ->
                relations.forEach { (key, state) ->
                    root.put(key, JSONObject().apply {
                        put("affinity", state.affinity)
                        put("rivalry", state.rivalry)
                        put("tension", state.tension)
                        put("relationLabel", state.relationLabel)
                        put("recentEvent", state.recentEvent)
                        put("updatedAt", state.updatedAt)
                    })
                }
            }.toString()
        }
    }
}
