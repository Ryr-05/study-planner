package io.github.ryr05.noctil.ui.quickadd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.parse.ParsedInput
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.parse.Token
import io.github.ryr05.noctil.parse.TokenKind
import io.github.ryr05.noctil.ui.components.color
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Quick add. As you type, the parts it understands light up in the text and appear as chips
 * underneath. Tap a chip's x to keep that part as plain text instead.
 */
@Composable
fun QuickAddOverlay(
    visible: Boolean,
    defaultDate: LocalDate?,
    defaultListId: Long?,
    snapshot: Snapshot,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (ParsedInput, Long?) -> Unit,
    initialText: String = "",
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Shadow.copy(alpha = 0.55f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            QuickAddPanel(defaultDate, defaultListId, snapshot, today, onSubmit, initialText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddPanel(
    defaultDate: LocalDate?,
    defaultListId: Long?,
    snapshot: Snapshot,
    today: LocalDate,
    onSubmit: (ParsedInput, Long?) -> Unit,
    initialText: String,
) {
    var field by rememberSaveable(initialText, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    val text = field.text
    var disabled by remember { mutableStateOf(setOf<TokenKind>()) }
    val focus = remember { FocusRequester() }
    val parsed = remember(text, disabled, defaultDate) {
        SmartParser.parse(text, LocalDateTime.now(), disabled, defaultDate)
    }
    val canAdd = text.isNotBlank()
    val submit = { if (canAdd) onSubmit(parsed, defaultListId) }
    val defaultList = defaultListId?.let { id -> snapshot.lists.firstOrNull { it.id == id } }

    LaunchedEffect(Unit) { focus.requestFocus() }

    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New task", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                val context = listOfNotNull(
                    if (parsed.listName == null) defaultList?.name else null,
                    if (parsed.dateIsDefault) parsed.date?.let { relativeDay(it, today) } else null,
                ).joinToString(", ")
                if (context.isNotEmpty()) {
                    Text("Adds to $context", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                    cursorBrush = SolidColor(Palette.Lavender),
                    visualTransformation = TokenHighlight(parsed.tokens),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .semantics { contentDescription = "Task, with optional date, time, priority and tags" },
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            if (text.isEmpty()) {
                                Text("Essay draft fri 5pm !1", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canAdd) Palette.Lavender else Palette.SurfaceHigh)
                        .clickable(enabled = canAdd, role = Role.Button, onClick = { submit() })
                        .semantics { contentDescription = "Add task" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = if (canAdd) Palette.OnLavender else Palette.TextTertiary)
                }
            }

            val chips = chipsFor(parsed, today)
            if (chips.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { chip ->
                        ParsedChip(chip) { disabled = disabled + chip.kind }
                    }
                }
            }
            if (disabled.isNotEmpty()) {
                Text(
                    text = "Show all suggestions again",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Lavender,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { disabled = emptySet() }
                        .padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Type it naturally: friday next week 5pm, tmrw 3-5pm, every monday, in 2 hours, !1, #exam, ~Chemistry",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
            )
        }
    }
}

private data class Chip(val kind: TokenKind, val icon: ImageVector, val label: String, val color: Color)

private fun chipsFor(p: ParsedInput, today: LocalDate): List<Chip> = buildList {
    if (p.date != null && !p.dateIsDefault) {
        val label = p.startDate?.let { "${relativeDay(it, today)} to ${relativeDay(p.date, today)}" } ?: relativeDay(p.date, today)
        add(Chip(TokenKind.Date, Icons.Rounded.CalendarMonth, label, Palette.Lavender))
    }
    val lengthTyped = p.tokens.any { it.kind == TokenKind.Duration }
    p.time?.let { t ->
        val range = p.durationMinutes?.takeIf { !lengthTyped }?.let { t.plusMinutes(it.toLong()) }
        val label = if (range != null) "${t.format(timeFormat)} to ${range.format(timeFormat)}" else t.format(timeFormat)
        add(Chip(TokenKind.Time, Icons.Rounded.Schedule, label, Palette.Lavender))
    }
    if (lengthTyped) p.durationMinutes?.let { add(Chip(TokenKind.Duration, Icons.Rounded.Timelapse, lengthLabel(it), Palette.Lavender)) }
    p.recurrence?.let { add(Chip(TokenKind.Repeat, Icons.Rounded.EventRepeat, it.label(), Palette.Lavender)) }
    if (p.priority != Priority.None) add(Chip(TokenKind.Priority, Icons.Rounded.Flag, p.priority.label, p.priority.color()))
    p.listName?.let { add(Chip(TokenKind.List, Icons.Rounded.FolderOpen, it, Palette.Lavender)) }
    if (p.tags.isNotEmpty()) add(Chip(TokenKind.Tag, Icons.Rounded.Sell, p.tags.joinToString(" ") { "#$it" }, Palette.Lavender))
}

private fun lengthLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}

@Composable
private fun ParsedChip(chip: Chip, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(CircleShape)
            .background(chip.color.copy(alpha = 0.16f))
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(chip.icon, contentDescription = null, tint = chip.color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(chip.label, style = MaterialTheme.typography.labelLarge, color = Palette.TextPrimary, maxLines = 1)
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Keep as text", role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = "Keep ${chip.label} as text" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(14.dp))
        }
    }
}

/** Colors the understood parts of the typed text lavender. */
private class TokenHighlight(private val tokens: List<Token>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            tokens.forEach { t ->
                if (t.end <= text.length) {
                    addStyle(SpanStyle(color = Palette.Lavender, background = Palette.Lavender.copy(alpha = 0.14f)), t.start, t.end)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is TokenHighlight && other.tokens == tokens
    override fun hashCode(): Int = tokens.hashCode()
}
