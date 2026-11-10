package io.github.ryr05.noctil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDateTime

/**
 * What an empty screen shows: a calm icon, one line on what goes here, and a few example
 * tasks. Tapping an example opens quick add with it typed in, so people learn the typing
 * shortcuts by trying them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyHint(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    examples: List<String> = emptyList(),
    onExample: ((String) -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(top = 36.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Palette.Lavender.copy(alpha = 0.10f))
                .border(1.dp, Palette.Lavender.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, textAlign = TextAlign.Center)
        if (examples.isNotEmpty() && onExample != null) {
            Spacer(Modifier.height(20.dp))
            Text("Tap one to try it", style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                examples.forEach { ExampleChip(it) { onExample(it) } }
            }
        }
    }
}

/** An example task with the parts Noctil understands (dates, times, tags) in lavender. */
@Composable
private fun ExampleChip(text: String, onClick: () -> Unit) {
    val styled = remember(text) {
        val tokens = SmartParser.parse(text, LocalDateTime.now()).tokens
        buildAnnotatedString {
            append(text)
            tokens.forEach { addStyle(SpanStyle(color = Palette.Lavender), it.start, it.end) }
        }
    }
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(Palette.Surface)
            .border(1.dp, Palette.Lavender.copy(alpha = 0.25f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Try: $text" }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(styled, style = MaterialTheme.typography.labelLarge, color = Palette.TextPrimary)
    }
}
