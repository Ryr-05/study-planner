package io.github.ryr05.noctil.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.ryr05.noctil.ui.theme.Palette

/**
 * Liquid-glass approximation for Android. This is not Apple's Liquid Glass (that only exists
 * on Apple platforms). It blurs the content behind the panel (Android 12+), tints it purple,
 * adds a bright rim that fades downward, and a soft top-left specular highlight.
 * On Android 8 to 11 there is no live blur, so the panel falls back to a near-solid tint.
 *
 * Glass is only used for controls that float above content (tab bar, add button, sheets).
 * Task content itself sits on solid surfaces so it stays easy to read.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

object GlassDefaults {
    val style = HazeStyle(
        backgroundColor = Palette.Night,
        tint = HazeTint(Palette.GlassTint.copy(alpha = 0.50f)),
        blurRadius = 30.dp,
        noiseFactor = 0.05f,
        fallbackTint = HazeTint(Palette.GlassFallback.copy(alpha = 0.96f)),
    )

    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.34f),
            Color.White.copy(alpha = 0.07f),
            Color.White.copy(alpha = 0.13f),
        ),
    )

    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.02f)),
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    elevation: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = LocalHazeState.current
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Palette.Shadow,
                spotColor = Palette.Shadow,
            )
            .clip(shape)
            .then(
                if (haze != null) {
                    Modifier.hazeEffect(state = haze, style = GlassDefaults.style)
                } else {
                    Modifier.background(Palette.GlassFallback.copy(alpha = 0.96f))
                },
            )
            .background(GlassDefaults.sheen)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.11f), Color.Transparent),
                        center = Offset(size.width * 0.18f, 0f),
                        radius = maxOf(size.width, size.height) * 0.6f,
                    ),
                )
            }
            .border(width = 1.dp, brush = GlassDefaults.rim, shape = shape),
        content = content,
    )
}
