package com.hana.app.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun ChatBackgroundArtwork(bitmap: Bitmap?, intensity: String = "soft") {
    if (bitmap == null) return

    val level = parseBackgroundLevel(intensity)
    val blurRadius = (10f * level).dp
    val overlayTop = lerpFloat(0.02f, 0.08f, level)
    val overlayMid = lerpFloat(0.04f, 0.12f, level)
    val overlayBottom = lerpFloat(0.10f, 0.18f, level)
    val radialColors = if (level <= 0.05f) {
        listOf(
            Color.White.copy(alpha = 0.04f),
            Color.Transparent,
            MaterialTheme.colorScheme.background.copy(alpha = 0.03f)
        )
    } else {
        listOf(
            Color.White.copy(alpha = lerpFloat(0.07f, 0.10f, level)),
            Color.Transparent,
            MaterialTheme.colorScheme.background.copy(alpha = lerpFloat(0.06f, 0.08f, level))
        )
    }
    val glazeAlpha = lerpFloat(0.04f, 0.10f, level)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = glazeAlpha)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = overlayTop),
                            MaterialTheme.colorScheme.background.copy(alpha = overlayMid),
                            MaterialTheme.colorScheme.background.copy(alpha = overlayBottom)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = radialColors,
                        radius = 1200f
                    )
                )
        )
    }
}

private fun parseBackgroundLevel(intensity: String): Float {
    return when (intensity) {
        "clear" -> 0f
        "soft" -> 0.5f
        "mist" -> 1f
        else -> intensity.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
    }
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}
