package io.github.ryr05.noctil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryr05.noctil.ui.CappedText
import io.github.ryr05.noctil.ui.theme.Palette

/**
 * A row of equal pills where one is chosen (List / Board / Matrix / Timeline).
 * Labels shrink a little to fit rather than being cut off when the phone's text is large.
 */
@Composable
fun Segmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 38.dp,
    enabled: (Int) -> Boolean = { true },
) {
    CappedText(1.3f) {
        Row(modifier.fillMaxWidth().clip(CircleShape).background(Palette.Surface).padding(4.dp)) {
            labels.forEachIndexed { i, label ->
                val isSelected = i == selected
                val style = MaterialTheme.typography.labelLarge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = minHeight)
                        .clip(CircleShape)
                        .background(if (isSelected) Palette.SurfaceHigh else Color.Transparent)
                        .selectable(selected = isSelected, enabled = enabled(i), role = Role.Tab) { onSelect(i) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = label,
                        style = style.copy(
                            color = if (isSelected) Palette.TextPrimary else Palette.TextSecondary,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
                    )
                }
            }
        }
    }
}
