package io.github.ryr05.noctil.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.parse.BulkSplit
import io.github.ryr05.noctil.parse.ParsedInput
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.plan.AiPlanning
import io.github.ryr05.noctil.plan.GeminiTaskReader
import io.github.ryr05.noctil.reminders.StartHabits
import io.github.ryr05.noctil.reminders.StartReminders
import io.github.ryr05.noctil.ui.components.GlassSheet
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.plan.AiConsentDialog
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalDateTime

/** One task in the review list: the quick-add line, whether to add it, and whether to keep its start. */
private data class Draft(val key: Int, val line: String, val keep: Boolean = true, val startOff: Boolean = false)

/**
 * Add several tasks at once: one per line, a messy message, or a pasted syllabus. "Make tasks"
 * splits it on the phone; "Sort it out with AI" lets Gemini read it. Either way you see every
 * task before it's added and can untick or fix any of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSeveralSheet(
    visible: Boolean,
    initialText: String,
    snapshot: Snapshot,
    today: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (List<ParsedInput>) -> Unit,
) {
    GlassSheet(visible = visible, title = "Add several", onDismiss = onDismiss, maxHeightFraction = 0.94f) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var text by remember(visible, initialText) { mutableStateOf(initialText) }
        var drafts by remember(visible) { mutableStateOf<List<Draft>?>(null) }
        var note by remember(visible) { mutableStateOf<String?>(null) }
        var busy by remember(visible) { mutableStateOf(false) }
        var consent by remember { mutableStateOf(false) }
        val aiStatus = remember(visible, consent) { AiPlanning.status(context) }
        val aiOffered = aiStatus == AiPlanning.Status.Ready || aiStatus == AiPlanning.Status.Off

        fun toDrafts(lines: List<String>) = lines.mapIndexed { i, l -> Draft(i, l) }

        fun splitHere() {
            val lines = BulkSplit.split(text, LocalDateTime.now())
            drafts = toDrafts(lines)
            note = if (lines.isEmpty()) "Type a task on each line first." else null
        }

        fun askAi() {
            if (AiPlanning.status(context) != AiPlanning.Status.Ready) {
                if (AiPlanning.status(context) == AiPlanning.Status.Off) consent = true else splitHere()
                return
            }
            busy = true
            scope.launch {
                val result = runCatching {
                    withTimeout(50_000) { GeminiTaskReader().read(text, LocalDateTime.now(), snapshot.tags.map { it.name }) }
                }
                busy = false
                result.onSuccess { read ->
                    AiPlanning.countOne(context)
                    drafts = toDrafts(read.tasks)
                    note = read.note.ifBlank { null } ?: if (read.tasks.isEmpty()) "I couldn't find any tasks in that." else null
                }.onFailure {
                    splitHere()
                    note = "Couldn't reach the AI, so I split it by lines instead."
                }
            }
        }

        val current = drafts
        if (current == null) {
            Text(
                "One task per line, or paste a message or your syllabus. Dates, times, start times and #tags work like quick add.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                cursorBrush = SolidColor(Palette.Lavender),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .semantics { contentDescription = "Tasks, one per line" },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                "Essay due fri 11:59pm\nLab report thu 9am #chem\nRead chapter 4 by wed",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Palette.TextTertiary,
                            )
                        }
                        inner()
                    }
                },
            )
            note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Make tasks", primary = !aiOffered, enabled = text.isNotBlank() && !busy, modifier = Modifier.weight(1f)) { splitHere() }
                if (aiOffered) {
                    ActionButton(
                        if (busy) "Reading..." else "Sort it out with AI",
                        primary = true,
                        enabled = text.isNotBlank() && !busy,
                        icon = !busy,
                        modifier = Modifier.weight(1f),
                    ) { askAi() }
                }
            }
            if (busy) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Palette.Lavender, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Gemini is reading it. Long syllabi can take a little while.", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                }
            }
        } else {
            val now = LocalDateTime.now()
            val parsed = current.associate { d -> d.key to SmartParser.parse(d.line, now) }
            fun startFor(d: Draft): LocalDateTime? {
                if (d.startOff) return null
                val p = parsed.getValue(d.key)
                val size = StartReminders.sizeOf(p.title, p.tags, p.priority, p.durationMinutes)
                return p.startAt ?: StartReminders.suggest(p.date, p.time, p.recurrence != null, now, StartHabits.leadFor(context, size))
            }
            val chosen = current.filter { it.keep && parsed.getValue(it.key).title.isNotBlank() }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text(
                    if (current.isEmpty()) "No tasks found" else "Check these ${current.size} tasks",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Edit text",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Lavender,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { drafts = null }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 8.dp)) }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                current.forEach { d ->
                    val p = parsed.getValue(d.key)
                    val start = startFor(d)
                    DraftRow(
                        draft = d,
                        parsed = p,
                        start = start,
                        today = today,
                        onToggle = { drafts = current.map { if (it.key == d.key) it.copy(keep = !it.keep) else it } },
                        onEdit = { line -> drafts = current.map { if (it.key == d.key) it.copy(line = line) else it } },
                        onRemoveStart = { drafts = current.map { if (it.key == d.key) it.copy(startOff = true) else it } },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ActionButton(
                when (chosen.size) {
                    0 -> "Nothing to add"
                    1 -> "Add 1 task"
                    else -> "Add ${chosen.size} tasks"
                },
                primary = true,
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                onAdd(chosen.map { d -> parsed.getValue(d.key).copy(startAt = startFor(d)) })
            }
        }

        if (consent) {
            AiConsentDialog(
                title = "Use Gemini AI?",
                onDismiss = { consent = false },
                onTurnOn = {
                    consent = false
                    AiPlanning.setEnabled(context, true)
                    askAi()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftRow(
    draft: Draft,
    parsed: ParsedInput,
    start: LocalDateTime?,
    today: LocalDate,
    onToggle: () -> Unit,
    onEdit: (String) -> Unit,
    onRemoveStart: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface.copy(alpha = if (draft.keep) 0.8f else 0.4f))
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Checkbox, onClickLabel = if (draft.keep) "Leave out" else "Add") { onToggle() }
                .semantics { contentDescription = if (draft.keep) "Will be added" else "Left out" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (draft.keep) Palette.Lavender else Color.Transparent)
                    .border(2.dp, if (draft.keep) Palette.Lavender else Palette.TextTertiary, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (draft.keep) Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(15.dp))
            }
        }
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            BasicTextField(
                value = draft.line,
                onValueChange = onEdit,
                singleLine = false,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (draft.keep) Palette.TextPrimary else Palette.TextTertiary),
                cursorBrush = SolidColor(Palette.Lavender),
                visualTransformation = TokenHighlight(parsed.tokens),
                modifier = Modifier.fillMaxWidth(),
            )
            val facts = buildList {
                parsed.date?.let { d -> add("Due " + relativeDay(d, today) + (parsed.time?.let { ", ${it.format(timeFormat)}" } ?: "")) }
                if (parsed.tags.isNotEmpty()) add(parsed.tags.joinToString(" ") { "#$it" })
                parsed.listName?.let { add(it) }
            }
            if (facts.isNotEmpty() || start != null) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    facts.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary) }
                    if (start != null) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Palette.Lavender.copy(alpha = 0.14f))
                                .clickable(role = Role.Button, onClickLabel = "No start reminder") { onRemoveStart() }
                                .padding(start = 8.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Start " + StartReminders.label(start, today), style = MaterialTheme.typography.labelMedium, color = Palette.TextPrimary)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Close, contentDescription = "No start reminder", tint = Palette.TextSecondary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Palette.SurfaceHigh
        primary -> Palette.Lavender
        else -> Palette.Surface
    }
    val fg = when {
        !enabled -> Palette.TextTertiary
        primary -> Palette.OnLavender
        else -> Palette.TextPrimary
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (primary && enabled) Palette.Lavender else Palette.Outline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}
