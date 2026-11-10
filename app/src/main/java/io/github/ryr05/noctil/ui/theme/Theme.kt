package io.github.ryr05.noctil.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp

private val NightScheme = darkColorScheme(
    primary = Palette.Lavender,
    onPrimary = Palette.OnLavender,
    primaryContainer = Palette.SurfaceHigh,
    onPrimaryContainer = Palette.TextPrimary,
    secondary = Palette.Lavender,
    onSecondary = Palette.OnLavender,
    background = Palette.Night,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    outline = Palette.Outline,
    outlineVariant = Palette.Hairline,
    error = Palette.PriorityHigh,
    onError = Palette.OnLavender,
)

@Composable
fun StudyTheme(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val density = remember(base) {
        if (base.fontScale <= 1.05f) base else GentleTextDensity(base.density, base.fontScale.coerceAtMost(MAX_TEXT_SCALE))
    }
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(
            colorScheme = NightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** The most Noctil's text grows when the phone's text size is turned up. */
const val MAX_TEXT_SCALE = 1.6f

/**
 * Follows the phone's text size, but like Android 14's own scaling, big text (titles) grows less
 * than small text, and nothing grows past [MAX_TEXT_SCALE]. Small text stays easy to read and
 * screens keep their shape on phones set to the largest text.
 */
private data class GentleTextDensity(override val density: Float, override val fontScale: Float) : Density {
    private val bigGrowth = 1f + (fontScale - 1f) / 2f

    override fun TextUnit.toDp(): Dp {
        check(type == TextUnitType.Sp) { "Only Sp can convert to Dp" }
        val v = value
        return Dp(if (v <= KNEE) v * fontScale else KNEE * fontScale + (v - KNEE) * bigGrowth)
    }

    override fun Dp.toSp(): TextUnit {
        val v = value
        return (if (v <= KNEE * fontScale) v / fontScale else KNEE + (v - KNEE * fontScale) / bigGrowth).sp
    }

    private companion object {
        const val KNEE = 16f
    }
}

/** True when the person turned animations off in Android settings. Motion then snaps instead. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
