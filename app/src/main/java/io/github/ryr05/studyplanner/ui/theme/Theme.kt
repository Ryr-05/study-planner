package io.github.ryr05.studyplanner.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
    MaterialTheme(
        colorScheme = NightScheme,
        typography = AppTypography,
        content = content,
    )
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
