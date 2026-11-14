package io.github.ryr05.noctil.ui.assistant

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import io.github.ryr05.noctil.ui.components.RoundIconButton
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.topContentPadding
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import io.github.ryr05.noctil.plan.AiPlanning
import io.github.ryr05.noctil.ui.onboarding.TERMS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.plan.Planner
import io.github.ryr05.noctil.ui.components.GlassSheet
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalDateTime


/**
 * Noctil AI, its own tab: recent chats (kept a week, on this phone only), or the open chat.
 */
@Composable
fun AssistantScreen(
    session: AssistantSession?,
    snapshot: Snapshot,
    today: LocalDate,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onCloseChat: () -> Unit,
    onOpenMe: () -> Unit,
    onFocusStarted: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = topContentPadding(), start = 20.dp, end = 20.dp)) {
        if (session == null) ChatList(onNewChat, onOpenChat, onOpenMe, onFocusStarted)
        else ChatView(session, snapshot, today, onCloseChat, onFocusStarted)
    }
}

@Composable
private fun ColumnScope.ChatList(onNewChat: () -> Unit, onOpenChat: (String) -> Unit, onOpenMe: () -> Unit, onFocusStarted: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var chats by remember { mutableStateOf<List<ChatStore.Chat>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }
    var aiOn by remember { mutableStateOf(AiPlanning.status(context) == AiPlanning.Status.Ready) }
    LaunchedEffect(refresh) { chats = withContext(Dispatchers.IO) { ChatStore.list(context) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Noctil AI", style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        RoundIconButton(Icons.Rounded.Person, "Me and settings", onClick = onOpenMe)
    }
    AiStatusRow(aiOn) { on ->
        AiPlanning.setEnabled(context, on)
        aiOn = AiPlanning.status(context) == AiPlanning.Status.Ready
    }
    io.github.ryr05.noctil.ui.components.TodayPlanCard(Modifier.padding(top = 14.dp), onStarted = onFocusStarted)
    Spacer(Modifier.height(16.dp))
    PrimaryButton("New chat", enabled = true, onClick = onNewChat)
    Spacer(Modifier.height(20.dp))
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = bottomContentPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "label") {
            Text("Recent chats", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        if (chats.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Your chats from the last ${ChatStore.KEEP_DAYS} days show up here. They stay on this phone.\n\n" +
                        "Try: \"What's due this week?\", \"Plan my week\", \"Move my essay start to tonight\" or \"Drink water 8 times a day\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
        items(chats, key = { it.id }) { chat ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.Surface.copy(alpha = 0.8f))
                    .clickable(onClickLabel = "Open chat") { onOpenChat(chat.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(ago(chat.updatedAt), style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                }
                if (chat.preview.isNotBlank()) {
                    Text(chat.preview, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (chats.isNotEmpty()) {
            item(key = "clear") {
                Text(
                    "Clear chats",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.PriorityHigh,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { confirmClear = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Palette.SurfaceHigh,
            title = { Text("Clear all chats?", color = Palette.TextPrimary) },
            text = { Text("This deletes your saved chats from this phone. Your tasks and habits stay as they are.", color = Palette.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    ChatStore.clear(context)
                    confirmClear = false
                    refresh++
                }) { Text("Clear", color = Palette.PriorityHigh) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        )
    }
}

private fun ago(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 48 * 60 -> "yesterday"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

/** "Gemini AI, 34 left today" or "Offline mode", with the switch (and its one-time consent). */
@Composable
private fun AiStatusRow(on: Boolean, refresh: Any = Unit, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val status = remember(on, refresh) { AiPlanning.status(context) }
    val left = remember(on, refresh) { AiPlanning.DAILY_LIMIT - AiPlanning.usedToday(context) }
    var consent by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            when (status) {
                // The count only shows near the end, so the limit doesn't hang over every chat.
                AiPlanning.Status.Ready -> if (left <= 10) "Gemini AI, $left messages left today" else "Gemini AI"
                AiPlanning.Status.Limit -> "Offline mode until tomorrow (daily AI limit reached)"
                AiPlanning.Status.Region -> "Offline mode (Gemini isn't offered in your region)"
                else -> "Offline mode"
            },
            style = MaterialTheme.typography.labelLarge,
            color = Palette.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (status != AiPlanning.Status.NotInBuild && status != AiPlanning.Status.Region) {
            Pill(text = if (on) "AI on" else "Use AI", selected = on, leading = Icons.Rounded.AutoAwesome) {
                when {
                    on -> onChange(false)
                    AiPlanning.enabled(context) || status == AiPlanning.Status.Limit -> onChange(true)
                    else -> consent = true
                }
            }
        }
    }
    if (consent) {
        AiConsentDialog(
            title = "Use Gemini AI?",
            onDismiss = { consent = false },
            onTurnOn = {
                consent = false
                onChange(true)
            },
        )
    }
}

private val STARTERS = listOf(
    "due" to "What's due this week?",
    "session" to "Plan my session",
    "week" to "Plan my week",
    "exam" to "When's my next exam?",
    "add" to "Add tasks",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ChatView(session: AssistantSession, snapshot: Snapshot, today: LocalDate, onClose: () -> Unit, onFocusStarted: () -> Unit) {
    val candidates = remember(snapshot.tasks, today, session.selected) {
        val base = Planner.candidates(snapshot.tasks, today)
        val extra = snapshot.tasks.filter { it.id in session.selected && !it.done && base.none { b -> b.id == it.id } }
        Planner.rank(base + extra, Planner.Answers(), LocalDateTime.now()).map { it.task }
    }
    LaunchedEffect(candidates) { if (session.phase == AssistantSession.Phase.Pick) session.preselect(candidates, today) }
    val listState = rememberLazyListState()
    LaunchedEffect(session.messages.size, session.thinking) {
        if (session.messages.isNotEmpty()) listState.animateScrollToItem(session.messages.lastIndex)
    }
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Above the tab bar, or right above the keyboard when it's open.
    val bottom = if (ime > nav) ime + 8.dp else nav + 96.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "All chats", onClick = onClose)
        Spacer(Modifier.width(12.dp))
        Text(session.title, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
    AiStatusRow(session.ai, refresh = session.thinking) { session.switchAi(it) }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().weight(1f).padding(bottom = bottom)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(session.messages, key = { i, _ -> i }) { i, msg ->
                when (msg) {
                    is ChatMessage.Bot -> BotBubble(msg.text)
                    is ChatMessage.User -> UserBubble(msg.text)
                    is ChatMessage.Note -> NoteLine(msg.text)
                    ChatMessage.Starters -> if (session.messages.none { it is ChatMessage.User }) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            STARTERS.forEach { (value, label) -> Pill(label, selected = false) { session.starter(value, label, snapshot) } }
                        }
                    }
                    ChatMessage.PickTasks -> PickCard(
                        tasks = candidates,
                        selected = session.selected,
                        active = session.phase == AssistantSession.Phase.Pick && session.messages.lastIndexOf(ChatMessage.PickTasks) == i && !session.thinking,
                        today = today,
                        onToggle = session::toggle,
                        onPlan = { session.planSelected(snapshot) },
                    )
                    is ChatMessage.Ask -> Column {
                        BotBubble(msg.question.text)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            msg.question.options.forEach { o ->
                                Pill(o.label, selected = msg.answer == o.label) {
                                    if (msg.answer == null) session.answer(msg.question, o.value, o.label, snapshot)
                                }
                            }
                        }
                    }
                    is ChatMessage.Result -> PlanCard(
                        plan = msg.plan,
                        onFocus = { if (session.startFocus(msg.plan)) onFocusStarted() },
                        onRemind = { session.remind(msg.plan) },
                        onRestart = { session.send("Plan my session", snapshot) },
                    )
                    is ChatMessage.Edited -> Column {
                        BotBubble(if (msg.undone) "Undone. Everything is back the way it was." else msg.text)
                        if (!msg.undone && msg.undo != null) {
                            Spacer(Modifier.height(8.dp))
                            Pill("Undo", selected = false) { session.undo(i) }
                        }
                    }
                    is ChatMessage.ConfirmDeadline -> DeadlineCard(msg, today) { change -> session.answerDeadline(i, change) }
                    is ChatMessage.WeekPlan -> WeekPlanCard(msg, today) { apply -> session.answerWeekPlan(i, apply) }
                    is ChatMessage.Setup -> SetupCard(session, msg, i, snapshot, today)
                }
            }
        }
        if (session.thinking) {
            Spacer(Modifier.height(8.dp))
            ThinkingBubble()
        }
        Spacer(Modifier.height(10.dp))
        InputBar(
            hint = if (session.ai) "Message Noctil AI" else "Ask, or type tasks one per line",
            onSend = { session.send(it, snapshot) },
        )
    }
}

/**
 * Setting up a new task: "When would you like to begin?", then subject, priority and (for big
 * tasks) "keep at it" nudges. One tap each, and "Skip, use suggestions" is always there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupCard(session: AssistantSession, msg: ChatMessage.Setup, index: Int, snapshot: Snapshot, today: LocalDate) {
    var picking by remember { mutableStateOf(false) }
    if (msg.step == null) {
        BotBubble("All set for ${msg.title}" + if (msg.notes.isEmpty()) "." else ": " + msg.notes.joinToString(", ") + ".")
        return
    }
    Column {
        BotBubble(session.setupQuestion(msg))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            session.setupOptions(msg, snapshot).forEach { (label, value) ->
                Pill(label, selected = false) {
                    if (value == "pick") picking = true else session.answerSetup(index, value, label)
                }
            }
        }
        Text(
            "Skip, use suggestions",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { session.answerSetup(index, "skip", "") }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
    if (picking) {
        val task = snapshot.byId[msg.taskId]
        io.github.ryr05.noctil.ui.components.StartPickerDialog(
            initial = null,
            dueDate = task?.date,
            dueTime = task?.time,
            today = today,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                session.answerSetupStart(index, it)
            },
        )
    }
}

/** A card from a saved chat, read back as a quiet note. */
@Composable
private fun NoteLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(start = 38.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    }
}

/** The week's start times, by day, with Apply. Nothing changes until it's tapped. */
@Composable
private fun WeekPlanCard(msg: ChatMessage.WeekPlan, today: LocalDate, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .padding(16.dp),
    ) {
        msg.slots.groupBy { it.start.toLocalDate() }.toSortedMap().forEach { (day, slots) ->
            Text(relativeDay(day, today), style = MaterialTheme.typography.labelLarge, color = Palette.Lavender, modifier = Modifier.padding(top = 6.dp))
            slots.sortedBy { it.start }.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(s.start.toLocalTime().format(timeFormat), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, modifier = Modifier.width(76.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.task.title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
                        s.task.date?.let { due ->
                            Text("Due ${relativeDay(due, today)}" + (s.task.time?.let { ", ${it.format(timeFormat)}" } ?: ""), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (msg.answer) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Apply", selected = true) { onAnswer(true) }
                Pill("Not now", selected = false) { onAnswer(false) }
            }
            true -> Text("Applied.", style = MaterialTheme.typography.labelLarge, color = Palette.Lavender)
            false -> Text("Left as it was.", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        }
    }
}

/** The one-time "what gets sent" dialog before AI is used anywhere in the app. */
@Composable
fun AiConsentDialog(title: String, onDismiss: () -> Unit, onTurnOn: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Palette.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text(title, color = Palette.TextPrimary) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        TERMS.first { it.first.startsWith("Noctil AI") }.second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Up to ${AiPlanning.DAILY_LIMIT} AI messages a day. Without AI, Noctil AI still works in a simpler offline mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onTurnOn) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        )
}

@Composable
private fun ThinkingBubble() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000)
        slow = true
    }
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dots")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Palette.Lavender.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Palette.Lavender.copy(alpha = alpha), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (slow) "Still thinking. The free AI can be slow sometimes..." else "Thinking...",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary.copy(alpha = alpha),
        )
    }
}

/** "Change the deadline?" with old and new, so the AI never moves a deadline on its own. */
@Composable
private fun DeadlineCard(msg: ChatMessage.ConfirmDeadline, today: LocalDate, onAnswer: (Boolean) -> Unit) {
    fun due(date: LocalDate?, time: java.time.LocalTime?) =
        date?.let { relativeDay(it, today) + (time?.let { t -> ", ${t.format(timeFormat)}" } ?: "") } ?: "no date"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.PriorityMedium.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(
            if (msg.edits.size == 1) "Change this deadline?" else "Change these deadlines?",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
        )
        msg.edits.forEach { e ->
            Spacer(Modifier.height(8.dp))
            Text(e.task.title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(
                "${due(e.oldDate, e.oldTime)}  to  ${due(e.newDate, e.newTime)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        when (msg.answer) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Change deadline", selected = true) { onAnswer(true) }
                Pill("Keep it", selected = false) { onAnswer(false) }
            }
            true -> Text("Deadline changed.", style = MaterialTheme.typography.labelLarge, color = Palette.Lavender)
            false -> Text("Kept the deadline.", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        }
    }
}

@Composable
private fun BotBubble(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Palette.Lavender.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.TextPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(Palette.Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.OnLavender,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(Palette.Lavender)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PickCard(
    tasks: List<TaskItem>,
    selected: Set<Long>,
    active: Boolean,
    today: LocalDate,
    onToggle: (Long) -> Unit,
    onPlan: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .padding(vertical = 6.dp),
    ) {
        if (tasks.isEmpty()) {
            Text(
                "No tasks due this week yet. Type a few below, like \"essay draft fri 5pm\".",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
        tasks.take(12).forEach { t ->
            val on = t.id in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = active, role = Role.Checkbox) { onToggle(t.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = "${t.title}, ${if (on) "picked" else "not picked"}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (on) Palette.Lavender else Palette.Surface)
                        .border(2.dp, if (on) Palette.Lavender else Palette.Outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        t.date?.let { relativeDay(it, today) + (t.time?.let { tm -> ", " + tm.format(timeFormat) } ?: "") },
                        t.durationMinutes?.let { "$it min" },
                    ).joinToString("  |  ")
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = if (t.date?.isBefore(today) == true) Palette.PriorityHigh else Palette.TextSecondary)
                }
            }
        }
        if (active) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                PrimaryButton(
                    text = if (selected.isEmpty()) "Pick at least one" else "Plan these (${selected.size})",
                    enabled = selected.isNotEmpty(),
                    onClick = onPlan,
                )
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Planner.Plan, onFocus: () -> Unit, onRemind: () -> Unit, onRestart: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .padding(16.dp),
    ) {
        plan.steps.forEach { step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Text(
                    "${step.start.format(timeFormat)}\n${step.end.format(timeFormat)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.width(76.dp),
                )
                when (step) {
                    is Planner.Step.Work -> Column(Modifier.weight(1f)) {
                        Text(
                            step.title + if (step.parts > 1) " (part ${step.part} of ${step.parts})" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.TextPrimary,
                        )
                        Text("${step.minutes} min  |  ${step.reason}", style = MaterialTheme.typography.labelMedium, color = Palette.Lavender)
                    }
                    is Planner.Step.Break -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Coffee, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Short break", style = MaterialTheme.typography.bodyMedium, color = Palette.TextTertiary)
                    }
                    is Planner.Step.Class -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.School, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Class: ${step.name}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    }
                }
            }
        }
        if (plan.later.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Later: " + plan.later.joinToString(", ") { it.title },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
        }
        if (plan.work.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Start focus on the first one", enabled = true, onClick = onFocus)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Remind me", selected = false, onClick = onRemind)
                Pill("Start over", selected = false, onClick = onRestart)
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Pill("Start over", selected = false, onClick = onRestart)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(
                if (enabled) Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender))
                else Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.SurfaceHigh)),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (enabled) Palette.OnLavender else Palette.TextTertiary)
    }
}

@Composable
private fun InputBar(hint: String, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.Bottom) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.SurfaceHigh)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = Palette.TextTertiary)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                cursorBrush = SolidColor(Palette.Lavender),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (text.isNotBlank()) Palette.Lavender else Palette.SurfaceHigh)
                .clickable(enabled = text.isNotBlank(), role = Role.Button) {
                    onSend(text)
                    text = ""
                }
                .semantics { contentDescription = "Send" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = if (text.isNotBlank()) Palette.OnLavender else Palette.TextTertiary)
        }
    }
}

