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
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

@Immutable
data class HanaBubbleColors(
    val aiBubble: Color,
    val userBubble: Color,
    val aiBubbleOnSurface: Color,
    val userBubbleOnSurface: Color
)

private val DarkHanaBubbleColors = HanaBubbleColors(
    aiBubble = Color(0xFF2D2D4A),
    userBubble = Color(0xFF7C5CFC),
    aiBubbleOnSurface = Color(0xFFE8E8F0),
    userBubbleOnSurface = Color.White
)

private val LightHanaBubbleColors = HanaBubbleColors(
    aiBubble = Color(0xFFF0F0F5),
    userBubble = Color(0xFF6B4CE6),
    aiBubbleOnSurface = Color(0xFF1A1A2E),
    userBubbleOnSurface = Color.White
)

val LocalHanaBubbleColors = staticCompositionLocalOf { DarkHanaBubbleColors }

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
    primary = Color(0xFF8A7BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E315C),
    onPrimaryContainer = Color(0xFFEBE5FF),
    secondary = Color(0xFFB8C7E3),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF283245),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFE6B3E5),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF4B3150),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1220),
    onBackground = Color(0xFFEAF0FF),
    surface = Color(0xFF171B2C),
    onSurface = Color(0xFFEAF0FF),
    surfaceVariant = Color(0xFF21283D),
    onSurfaceVariant = Color(0xFF98A3BE),
    outline = Color(0xFF273048),
    outlineVariant = Color(0xFF1E263A),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6B4CE6)
)

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
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    Crossfade(targetState = darkTheme, animationSpec = tween(durationMillis = 650), label = "hana_theme_mode") { isDark ->
        val colorScheme = if (isDark) DarkColors else LightColors
        val bubbleColors = if (isDark) DarkHanaBubbleColors else LightHanaBubbleColors

        CompositionLocalProvider(LocalHanaBubbleColors provides bubbleColors) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                content = content
            )
        }
    }
}
