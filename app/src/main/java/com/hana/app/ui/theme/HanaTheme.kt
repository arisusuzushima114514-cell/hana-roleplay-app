package com.hana.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate

data class HanaChatDensityMetrics(
    val messageSpacing: Dp,
    val bubbleHorizontalPadding: Dp,
    val bubbleVerticalPadding: Dp,
    val inputVerticalPadding: Dp
)

fun hanaChatDensityMetrics(density: String): HanaChatDensityMetrics = when (density) {
    "compact" -> HanaChatDensityMetrics(6.dp, 12.dp, 8.dp, 8.dp)
    "comfortable" -> HanaChatDensityMetrics(18.dp, 18.dp, 14.dp, 16.dp)
    else -> HanaChatDensityMetrics(12.dp, 14.dp, 12.dp, 12.dp)
}

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class ThemePalette(val value: String, val displayName: String) {
    VIOLET("violet", "紫罗兰"),
    OCEAN("ocean", "海水蓝"),
    SAKURA("sakura", "樱花粉"),
    FOREST("forest", "森林绿"),
    SUNSET("sunset", "落日橙");

    companion object {
        fun fromValue(value: String): ThemePalette =
            entries.firstOrNull { it.value == value } ?: VIOLET
    }
}

@Immutable
data class HanaBubbleColors(
    val aiBubble: Color,
    val userBubble: Color,
    val aiBubbleOnSurface: Color,
    val userBubbleOnSurface: Color,
    val userCorner: Dp,
    val userTailCorner: Dp,
    val aiCorner: Dp
)

private fun bubbleColors(palette: ThemePalette, dark: Boolean): HanaBubbleColors = when (palette) {
    ThemePalette.VIOLET -> HanaBubbleColors(
        aiBubble = if (dark) Color(0xFF1E2036) else Color(0xFFF0F0F5),
        userBubble = if (dark) Color(0xFF7C3AED) else Color(0xFF6B4CE6),
        aiBubbleOnSurface = if (dark) Color(0xFFDCD8E8) else Color(0xFF1A1A2E),
        userBubbleOnSurface = Color.White, userCorner = 20.dp, userTailCorner = 7.dp, aiCorner = 22.dp
    )
    ThemePalette.OCEAN -> HanaBubbleColors(
        aiBubble = if (dark) Color(0xFF132B36) else Color(0xFFE7F5F8),
        userBubble = if (dark) Color(0xFF087F9C) else Color(0xFF087F9C),
        aiBubbleOnSurface = if (dark) Color(0xFFD7F3F8) else Color(0xFF12343D),
        userBubbleOnSurface = Color.White, userCorner = 24.dp, userTailCorner = 4.dp, aiCorner = 16.dp
    )
    ThemePalette.SAKURA -> HanaBubbleColors(
        aiBubble = if (dark) Color(0xFF35232D) else Color(0xFFFFEFF5),
        userBubble = if (dark) Color(0xFFC44C7A) else Color(0xFFD95788),
        aiBubbleOnSurface = if (dark) Color(0xFFFFE1EB) else Color(0xFF482331),
        userBubbleOnSurface = Color.White, userCorner = 26.dp, userTailCorner = 12.dp, aiCorner = 26.dp
    )
    ThemePalette.FOREST -> HanaBubbleColors(
        aiBubble = if (dark) Color(0xFF1D2B25) else Color(0xFFEAF4ED),
        userBubble = if (dark) Color(0xFF34785A) else Color(0xFF397D5B),
        aiBubbleOnSurface = if (dark) Color(0xFFD9EDE0) else Color(0xFF183828),
        userBubbleOnSurface = Color.White, userCorner = 14.dp, userTailCorner = 3.dp, aiCorner = 14.dp
    )
    ThemePalette.SUNSET -> HanaBubbleColors(
        aiBubble = if (dark) Color(0xFF38251E) else Color(0xFFFFF0E8),
        userBubble = if (dark) Color(0xFFD0643C) else Color(0xFFE06A3B),
        aiBubbleOnSurface = if (dark) Color(0xFFFFE2D4) else Color(0xFF492419),
        userBubbleOnSurface = Color.White, userCorner = 18.dp, userTailCorner = 2.dp, aiCorner = 10.dp
    )
}

val LocalHanaBubbleColors = staticCompositionLocalOf { bubbleColors(ThemePalette.VIOLET, true) }
data class HanaBubbleImages(val aiPath: String = "", val userPath: String = "", val aiFixedEdgePercent: Int = 24, val userFixedEdgePercent: Int = 24)
val LocalHanaBubbleImages = staticCompositionLocalOf { HanaBubbleImages() }
data class HanaChatDisplaySettings(
    val fontSize: String = "standard",
    val density: String = "standard",
    val bubbleWidthPercent: Int = 78,
    val showAvatars: Boolean = true,
    val showTime: Boolean = false,
    val inputBarSize: String = "standard",
    val inputBarWidthPercent: Int = 100
)
val LocalHanaChatDisplaySettings = staticCompositionLocalOf { HanaChatDisplaySettings() }

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E7FF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF4F5D75),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EEF9),
    onSecondaryContainer = Color(0xFF1D1741),
    tertiary = Color(0xFF8A4F88),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E1F6),
    onTertiaryContainer = Color(0xFF31101D),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF171A2A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A2A),
    surfaceVariant = Color(0xFFE9EDF5),
    onSurfaceVariant = Color(0xFF657089),
    outline = Color(0xFFD9E0EB),
    outlineVariant = Color(0xFFCCD4E0),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFCFBDFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1E1035),
    primaryContainer = Color(0xFF312155),
    onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = Color(0xFF9292AD),
    onSecondary = Color(0xFF1E1E30),
    secondaryContainer = Color(0xFF2A2B40),
    onSecondaryContainer = Color(0xFFD4D4E8),
    tertiary = Color(0xFFF0A5C7),
    onTertiary = Color(0xFF3D1430),
    tertiaryContainer = Color(0xFF4E2A45),
    onTertiaryContainer = Color(0xFFFFD6E8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A0B12),
    onBackground = Color(0xFFE3E1EC),
    surface = Color(0xFF141520),
    onSurface = Color(0xFFE3E1EC),
    surfaceVariant = Color(0xFF1D1F2E),
    onSurfaceVariant = Color(0xFF9C95A8),
    outline = Color(0xFF2B2E3E),
    outlineVariant = Color(0xFF202334),
    inverseSurface = Color(0xFFE3E1EC),
    inverseOnSurface = Color(0xFF2F3039),
    inversePrimary = Color(0xFF6B4CE6)
)

private fun themedColors(palette: ThemePalette, dark: Boolean) = when (palette) {
    ThemePalette.VIOLET -> if (dark) DarkColors else LightColors
    ThemePalette.OCEAN -> if (dark) DarkColors.copy(
        primary = Color(0xFF62D4EB), onPrimary = Color(0xFF003640), primaryContainer = Color(0xFF074B5B),
        onPrimaryContainer = Color(0xFFB8F1FC), secondary = Color(0xFF73B9C8), tertiary = Color(0xFF6EC9B5),
        background = Color(0xFF071216), surface = Color(0xFF0D1E24), surfaceVariant = Color(0xFF163039)
    ) else LightColors.copy(
        primary = Color(0xFF087F9C), onPrimary = Color.White, primaryContainer = Color(0xFFC5F1FA),
        onPrimaryContainer = Color(0xFF003640), secondary = Color(0xFF397A89), tertiary = Color(0xFF23836F),
        background = Color(0xFFF2FAFC), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFDDEFF3)
    )
    ThemePalette.SAKURA -> if (dark) DarkColors.copy(
        primary = Color(0xFFFF9ABB), onPrimary = Color(0xFF56102C), primaryContainer = Color(0xFF702743),
        onPrimaryContainer = Color(0xFFFFD9E5), secondary = Color(0xFFD5A1B2), tertiary = Color(0xFFE6A173),
        background = Color(0xFF160C11), surface = Color(0xFF24151C), surfaceVariant = Color(0xFF35232D)
    ) else LightColors.copy(
        primary = Color(0xFFD95788), primaryContainer = Color(0xFFFFD9E5), onPrimaryContainer = Color(0xFF52152C),
        secondary = Color(0xFF9B6075), tertiary = Color(0xFFB56B3D), background = Color(0xFFFFF7FA),
        surfaceVariant = Color(0xFFF8E7ED)
    )
    ThemePalette.FOREST -> if (dark) DarkColors.copy(
        primary = Color(0xFF8DD5A8), onPrimary = Color(0xFF06391F), primaryContainer = Color(0xFF205238),
        onPrimaryContainer = Color(0xFFC1F2D1), secondary = Color(0xFF93BDA1), tertiary = Color(0xFFD1B56B),
        background = Color(0xFF09130E), surface = Color(0xFF122019), surfaceVariant = Color(0xFF1D2B25)
    ) else LightColors.copy(
        primary = Color(0xFF397D5B), primaryContainer = Color(0xFFCDEDD7), onPrimaryContainer = Color(0xFF103824),
        secondary = Color(0xFF527461), tertiary = Color(0xFF856F26), background = Color(0xFFF4F9F5),
        surfaceVariant = Color(0xFFE1EEE5)
    )
    ThemePalette.SUNSET -> if (dark) DarkColors.copy(
        primary = Color(0xFFFFA27D), onPrimary = Color(0xFF5B1B07), primaryContainer = Color(0xFF793319),
        onPrimaryContainer = Color(0xFFFFDBCC), secondary = Color(0xFFD8A48F), tertiary = Color(0xFFE6BA69),
        background = Color(0xFF170D09), surface = Color(0xFF271710), surfaceVariant = Color(0xFF38251E)
    ) else LightColors.copy(
        primary = Color(0xFFE06A3B), primaryContainer = Color(0xFFFFDBCC), onPrimaryContainer = Color(0xFF55200D),
        secondary = Color(0xFF93604D), tertiary = Color(0xFF947126), background = Color(0xFFFFF8F4),
        surfaceVariant = Color(0xFFF7E8E0)
    )
}

fun applyThemeMode(themeMode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(
        when (themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    )
}

@Composable
fun HanaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.VIOLET,
    aiBubbleImagePath: String = "",
    userBubbleImagePath: String = "",
    aiBubbleFixedEdgePercent: Int = 24,
    userBubbleFixedEdgePercent: Int = 24,
    chatFontSize: String = "standard",
    chatDensity: String = "standard",
    bubbleWidthPercent: Int = 78,
    showMessageAvatars: Boolean = true,
    showMessageTime: Boolean = false,
    inputBarSize: String = "standard",
    inputBarWidthPercent: Int = 100,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    Crossfade(
        targetState = darkTheme to palette,
        animationSpec = tween(durationMillis = 650),
        label = "hana_theme_mode"
    ) { (isDark, activePalette) ->
        val colorScheme = themedColors(activePalette, isDark)
        val bubbleColors = bubbleColors(activePalette, isDark)

        CompositionLocalProvider(
            LocalHanaBubbleColors provides bubbleColors,
            LocalHanaBubbleImages provides HanaBubbleImages(aiBubbleImagePath, userBubbleImagePath, aiBubbleFixedEdgePercent, userBubbleFixedEdgePercent),
            LocalHanaChatDisplaySettings provides HanaChatDisplaySettings(chatFontSize, chatDensity, bubbleWidthPercent, showMessageAvatars, showMessageTime, inputBarSize, inputBarWidthPercent)
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                content = content
            )
        }
    }
}
