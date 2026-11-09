package io.github.ryr05.noctil.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.ui.theme.Palette

/** Height reserved at the bottom of every screen for the floating tab bar. */
val BottomChromeHeight = 104.dp

@Composable
fun bottomContentPadding(): Dp =
    BottomChromeHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun topContentPadding(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp

/**
 * Static, low-contrast aurora behind everything. It gives the glass something to refract
 * without competing with text. Drawn once, never animated, so it costs no battery.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Palette.Night)
        fun glow(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
            val center = Offset(size.width * cx, size.height * cy)
            val radius = size.width * r
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        glow(Palette.AuroraViolet, 0.42f, 0.10f, 0.04f, 0.95f)
        glow(Palette.AuroraPlum, 0.24f, 1.00f, 0.38f, 0.75f)
        glow(Palette.AuroraIndigo, 0.38f, 0.35f, 1.02f, 1.05f)
    }
}

/** Corner shape for a row inside an inset group: round only the outer corners. */
fun groupShape(index: Int, count: Int, radius: Dp = 24.dp): Shape {
    val top = if (index == 0) radius else 0.dp
    val bottom = if (index == count - 1) radius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextSecondary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp),
    )
}

/** A small colored label for a list or tag name. */
@Composable
fun ColorTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Round 44dp icon button on a solid surface. */
@Composable
fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Palette.SurfaceHigh)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = Palette.TextPrimary)
    }
}

/** A pill-shaped choice used for priorities, repeat options and similar. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Palette.Lavender,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.20f) else Palette.SurfaceHigh)
            .border(1.dp, if (selected) color.copy(alpha = 0.55f) else Color.Transparent, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            androidx.compose.material3.Icon(
                leading, contentDescription = null,
                tint = if (selected) color else Palette.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, overline: String? = null) {
    androidx.compose.foundation.layout.Column(modifier) {
        if (overline != null) {
            Text(
                text = overline,
                style = MaterialTheme.typography.labelLarge,
                color = Palette.TextSecondary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = Palette.TextPrimary,
        )
    }
}
