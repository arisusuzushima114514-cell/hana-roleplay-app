package com.hana.app.data.customization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hana.app.R
import com.hana.app.SplashActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class CustomizationSettings(
    val schemaVersion: Int = 7,
    val appDisplayName: String = "Hana",
    val customSplashEnabled: Boolean = false,
    val customSplashPath: String = "",
    val splashDurationMillis: Int = 2200,
    val generationCompleteHapticEnabled: Boolean = true,
    val chatTabIconPath: String = "",
    val characterTabIconPath: String = "",
    val settingsTabIconPath: String = "",
    val desktopIconPath: String = "",
    val userBubbleImagePath: String = "",
    val aiBubbleImagePath: String = "",
    val userBubbleFixedEdgePercent: Int = 24,
    val aiBubbleFixedEdgePercent: Int = 24,
    val chatFontSize: String = "standard",
    val chatDensity: String = "standard",
    val bubbleWidthPercent: Int = 78,
    val showMessageAvatars: Boolean = true,
    val showMessageTime: Boolean = false,
    val showBottomBarLabels: Boolean = true,
    val inputBarSize: String = "standard",
    val inputBarWidthPercent: Int = 100
)

enum class DesktopShortcutResult {
    Updated,
    AddRequested,
    Unsupported,
    MissingIcon,
    Failed
}

class CustomizationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = customizationDataStore(appContext)
    private val assetDir = File(appContext.filesDir, "personalization/splash").apply { mkdirs() }
    private val splashFile = File(assetDir, "custom_splash.jpg")
    private val navigationDir = File(appContext.filesDir, "personalization/navigation").apply { mkdirs() }
    private val bubbleDir = File(appContext.filesDir, "personalization/bubbles").apply { mkdirs() }

    val settingsFlow: Flow<CustomizationSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.e("CustomizationRepo", "Failed to read customization settings; using defaults", error)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            CustomizationSettings(
                schemaVersion = preferences[KEY_SCHEMA_VERSION] ?: 7,
                appDisplayName = preferences[KEY_APP_DISPLAY_NAME] ?: "Hana",
                customSplashEnabled = preferences[KEY_CUSTOM_SPLASH_ENABLED] ?: false,
                customSplashPath = preferences[KEY_CUSTOM_SPLASH_PATH].orEmpty(),
                splashDurationMillis = (preferences[KEY_SPLASH_DURATION] ?: 2200).coerceIn(500, 6000),
                generationCompleteHapticEnabled = preferences[KEY_GENERATION_HAPTIC] ?: true,
                chatTabIconPath = preferences[KEY_CHAT_TAB_ICON_PATH].orEmpty(),
                characterTabIconPath = preferences[KEY_CHARACTER_TAB_ICON_PATH].orEmpty(),
                settingsTabIconPath = preferences[KEY_SETTINGS_TAB_ICON_PATH].orEmpty(),
                desktopIconPath = preferences[KEY_DESKTOP_ICON_PATH].orEmpty(),
                userBubbleImagePath = preferences[KEY_USER_BUBBLE_IMAGE_PATH].orEmpty(),
                aiBubbleImagePath = preferences[KEY_AI_BUBBLE_IMAGE_PATH].orEmpty(),
                userBubbleFixedEdgePercent = (preferences[KEY_USER_BUBBLE_FIXED_EDGE] ?: 24).coerceIn(8, 42),
                aiBubbleFixedEdgePercent = (preferences[KEY_AI_BUBBLE_FIXED_EDGE] ?: 24).coerceIn(8, 42),
                chatFontSize = preferences[KEY_CHAT_FONT_SIZE] ?: "standard",
                chatDensity = preferences[KEY_CHAT_DENSITY] ?: "standard",
                bubbleWidthPercent = (preferences[KEY_BUBBLE_WIDTH_PERCENT] ?: 78).coerceIn(60, 95),
                showMessageAvatars = preferences[KEY_SHOW_MESSAGE_AVATARS] ?: true,
                showMessageTime = preferences[KEY_SHOW_MESSAGE_TIME] ?: false,
                showBottomBarLabels = preferences[KEY_SHOW_BOTTOM_BAR_LABELS] ?: true,
                inputBarSize = preferences[KEY_INPUT_BAR_SIZE] ?: "standard",
                inputBarWidthPercent = (preferences[KEY_INPUT_BAR_WIDTH_PERCENT] ?: 100).coerceIn(70, 100)
            )
        }

    suspend fun saveDisplayName(value: String) {
        dataStore.edit { it[KEY_APP_DISPLAY_NAME] = value.trim().take(24).ifBlank { "Hana" } }
    }

    suspend fun saveSplashDuration(value: Int) {
        dataStore.edit { it[KEY_SPLASH_DURATION] = value.coerceIn(500, 6000) }
    }

    suspend fun saveGenerationHaptic(enabled: Boolean) {
        dataStore.edit { it[KEY_GENERATION_HAPTIC] = enabled }
    }

    suspend fun saveChatDisplay(
        fontSize: String,
        density: String,
        bubbleWidthPercent: Int,
        showAvatars: Boolean,
        showTime: Boolean,
        showBottomBarLabels: Boolean,
        inputBarSize: String,
        inputBarWidthPercent: Int
    ) {
        dataStore.edit {
            it[KEY_CHAT_FONT_SIZE] = fontSize.takeIf { value -> value in setOf("small", "standard", "large") } ?: "standard"
            it[KEY_CHAT_DENSITY] = density.takeIf { value -> value in setOf("compact", "standard", "comfortable") } ?: "standard"
            it[KEY_BUBBLE_WIDTH_PERCENT] = bubbleWidthPercent.coerceIn(60, 95)
            it[KEY_SHOW_MESSAGE_AVATARS] = showAvatars
            it[KEY_SHOW_MESSAGE_TIME] = showTime
            it[KEY_SHOW_BOTTOM_BAR_LABELS] = showBottomBarLabels
            it[KEY_INPUT_BAR_SIZE] = inputBarSize.takeIf { value -> value in setOf("compact", "standard", "comfortable") } ?: "standard"
            it[KEY_INPUT_BAR_WIDTH_PERCENT] = inputBarWidthPercent.coerceIn(70, 100)
            it[KEY_SCHEMA_VERSION] = 7
        }
    }

    suspend fun saveCroppedAsset(slot: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val directory = if (slot.startsWith("bubble_")) bubbleDir else navigationDir
            val target = File(directory, "$slot.png")
            val temp = File(directory, "$slot.tmp")
            FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            check(replaceFileSafely(temp, target))
            dataStore.edit { preferences ->
                preferences[keyForAssetSlot(slot)] = target.absolutePath
                preferences[KEY_SCHEMA_VERSION] = 7
            }
            true
        }.getOrDefault(false)
    }

    suspend fun clearBubbleImages() {
        withContext(Dispatchers.IO) { bubbleDir.listFiles()?.forEach(File::delete) }
        dataStore.edit {
            it.remove(KEY_USER_BUBBLE_IMAGE_PATH)
            it.remove(KEY_AI_BUBBLE_IMAGE_PATH)
        }
    }

    suspend fun clearAsset(slot: String) = withContext(Dispatchers.IO) {
        val file = when (slot) {
            "chat", "chat_tab" -> File(navigationDir, "chat_tab.png")
            "character", "character_tab" -> File(navigationDir, "character_tab.png")
            "settings", "settings_tab" -> File(navigationDir, "settings_tab.png")
            "desktop" -> File(navigationDir, "desktop.png")
            "bubble_ai" -> File(bubbleDir, "bubble_ai.png")
            "bubble_user" -> File(bubbleDir, "bubble_user.png")
            else -> return@withContext
        }
        file.delete()
        dataStore.edit { it.remove(keyForAssetSlot(slot)) }
    }

    suspend fun saveBubbleFixedEdge(slot: String, percent: Int) {
        dataStore.edit {
            it[if (slot == "bubble_ai") KEY_AI_BUBBLE_FIXED_EDGE else KEY_USER_BUBBLE_FIXED_EDGE] = percent.coerceIn(8, 42)
            it[KEY_SCHEMA_VERSION] = 7
        }
    }

    fun requestDesktopShortcut(name: String): DesktopShortcutResult {
        val iconFile = File(navigationDir, "desktop.png")
        val bitmap = iconFile.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        val intent = Intent(appContext, SplashActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcutBuilder = ShortcutInfoCompat.Builder(appContext, "hana_custom_desktop")
            .setShortLabel(name.trim().take(20).ifBlank { "Hana" })
            .setLongLabel(name.trim().take(40).ifBlank { "Hana" })
            .setIntent(intent)
        shortcutBuilder.setIcon(
            bitmap?.let(IconCompat::createWithBitmap)
                ?: IconCompat.createWithResource(appContext, R.mipmap.ic_launcher)
        )
        val shortcut = shortcutBuilder.build()
        return runCatching {
            // Launchers report pinned shortcuts inconsistently; update first instead of relying on a query.
            if (ShortcutManagerCompat.updateShortcuts(appContext, listOf(shortcut))) {
                DesktopShortcutResult.Updated
            } else if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) {
                DesktopShortcutResult.Unsupported
            } else if (ShortcutManagerCompat.requestPinShortcut(appContext, shortcut, null)) {
                DesktopShortcutResult.AddRequested
            } else {
                DesktopShortcutResult.Failed
            }
        }.getOrDefault(DesktopShortcutResult.Failed)
    }

    private fun keyForAssetSlot(slot: String): Preferences.Key<String> = when (slot) {
        "chat_tab", "chat" -> KEY_CHAT_TAB_ICON_PATH
        "character_tab", "character" -> KEY_CHARACTER_TAB_ICON_PATH
        "settings_tab", "settings" -> KEY_SETTINGS_TAB_ICON_PATH
        "desktop" -> KEY_DESKTOP_ICON_PATH
        "bubble_user" -> KEY_USER_BUBBLE_IMAGE_PATH
        "bubble_ai" -> KEY_AI_BUBBLE_IMAGE_PATH
        else -> error("Unknown asset slot")
    }

    suspend fun clearNavigationIcons() {
        withContext(Dispatchers.IO) {
            listOf("chat_tab.png", "character_tab.png", "settings_tab.png").forEach { File(navigationDir, it).delete() }
        }
        dataStore.edit {
            it.remove(KEY_CHAT_TAB_ICON_PATH)
            it.remove(KEY_CHARACTER_TAB_ICON_PATH)
            it.remove(KEY_SETTINGS_TAB_ICON_PATH)
        }
    }

    suspend fun saveCroppedSplash(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File(assetDir, "custom_splash.tmp")
            FileOutputStream(tempFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            check(replaceFileSafely(tempFile, splashFile)) { "Unable to store splash image" }
            dataStore.edit {
                it[KEY_CUSTOM_SPLASH_PATH] = splashFile.absolutePath
                it[KEY_CUSTOM_SPLASH_ENABLED] = true
            }
            true
        }.getOrDefault(false)
    }

    suspend fun clearSplash() {
        withContext(Dispatchers.IO) { splashFile.delete() }
        dataStore.edit {
            it[KEY_CUSTOM_SPLASH_ENABLED] = false
            it.remove(KEY_CUSTOM_SPLASH_PATH)
        }
    }

    private fun replaceFileSafely(temp: File, target: File): Boolean {
        val backup = File(target.parentFile, "${target.name}.bak")
        backup.delete()
        if (target.exists() && !target.renameTo(backup)) return false
        if (temp.renameTo(target)) {
            backup.delete()
            return true
        }
        if (backup.exists()) backup.renameTo(target)
        return false
    }

    private companion object {
        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        val KEY_APP_DISPLAY_NAME = stringPreferencesKey("app_display_name")
        val KEY_CUSTOM_SPLASH_ENABLED = booleanPreferencesKey("custom_splash_enabled")
        val KEY_CUSTOM_SPLASH_PATH = stringPreferencesKey("custom_splash_path")
        val KEY_SPLASH_DURATION = intPreferencesKey("splash_duration_millis")
        val KEY_GENERATION_HAPTIC = booleanPreferencesKey("generation_complete_haptic")
        val KEY_CHAT_TAB_ICON_PATH = stringPreferencesKey("chat_tab_icon_path")
        val KEY_CHARACTER_TAB_ICON_PATH = stringPreferencesKey("character_tab_icon_path")
        val KEY_SETTINGS_TAB_ICON_PATH = stringPreferencesKey("settings_tab_icon_path")
        val KEY_DESKTOP_ICON_PATH = stringPreferencesKey("desktop_icon_path")
        val KEY_USER_BUBBLE_IMAGE_PATH = stringPreferencesKey("user_bubble_image_path")
        val KEY_AI_BUBBLE_IMAGE_PATH = stringPreferencesKey("ai_bubble_image_path")
        val KEY_USER_BUBBLE_FIXED_EDGE = intPreferencesKey("user_bubble_fixed_edge_percent")
        val KEY_AI_BUBBLE_FIXED_EDGE = intPreferencesKey("ai_bubble_fixed_edge_percent")
        val KEY_CHAT_FONT_SIZE = stringPreferencesKey("chat_font_size")
        val KEY_CHAT_DENSITY = stringPreferencesKey("chat_density")
        val KEY_BUBBLE_WIDTH_PERCENT = intPreferencesKey("bubble_width_percent")
        val KEY_SHOW_MESSAGE_AVATARS = booleanPreferencesKey("show_message_avatars")
        val KEY_SHOW_MESSAGE_TIME = booleanPreferencesKey("show_message_time")
        val KEY_SHOW_BOTTOM_BAR_LABELS = booleanPreferencesKey("show_bottom_bar_labels")
        val KEY_INPUT_BAR_SIZE = stringPreferencesKey("input_bar_size")
        val KEY_INPUT_BAR_WIDTH_PERCENT = intPreferencesKey("input_bar_width_percent")

        @Volatile private var DATA_STORE: DataStore<Preferences>? = null
        fun customizationDataStore(context: Context): DataStore<Preferences> = DATA_STORE ?: synchronized(this) {
            DATA_STORE ?: PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("hana_customization") }.also { DATA_STORE = it }
        }
    }
}
