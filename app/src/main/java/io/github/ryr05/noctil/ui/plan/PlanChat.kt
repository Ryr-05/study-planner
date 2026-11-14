package io.github.ryr05.noctil.ui.plan

import android.content.Context
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
import io.github.ryr05.noctil.plan.AiReply
import io.github.ryr05.noctil.plan.GeminiPlanner
import io.github.ryr05.noctil.ui.onboarding.TERMS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.focus.FocusEngine
import io.github.ryr05.noctil.focus.FocusKind
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.plan.AiChange
import io.github.ryr05.noctil.plan.AiChangeGuard
import io.github.ryr05.noctil.plan.PlanReminders
import io.github.ryr05.noctil.reminders.StartHabits
import io.github.ryr05.noctil.reminders.StartReminders
import io.github.ryr05.noctil.data.startAt
import io.github.ryr05.noctil.data.toMinutes
import io.github.ryr05.noctil.data.withStart
import io.github.ryr05.noctil.plan.Planner
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.ui.components.GlassSheet
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalDateTime

/** One line in the planning chat. */
sealed interface ChatMessage {
    data class Bot(val text: String) : ChatMessage
    data class User(val text: String) : ChatMessage
    data object PickTasks : ChatMessage
    data class Ask(val question: Planner.Question, val answer: String? = null) : ChatMessage
    data class Result(val plan: Planner.Plan, val from: LocalDateTime) : ChatMessage
    /** Start times the AI moved, with Undo. */
    data class Changed(val text: String, val edits: List<AiChangeGuard.StartEdit>, val undone: Boolean = false) : ChatMessage
    /** A deadline change the student asked for, waiting for them to confirm. */
    data class ConfirmDeadline(val edits: List<AiChangeGuard.DeadlineEdit>, val answer: Boolean? = null) : ChatMessage
}

/**
 * The conversation behind "Plan my session". It runs offline on the [Planner], and when AI
 * planning is on it lets Gemini lead the conversation while the [Planner] still builds the plan.
 */
class PlanSession(private val context: Context) {
    enum class Phase { Pick, Questions, Done }

    val messages = mutableStateListOf<ChatMessage>()
    var phase by mutableStateOf(Phase.Pick)
        private set
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set
    var ai by mutableStateOf(AiPlanning.status(context) == AiPlanning.Status.Ready)
        private set
    var thinking by mutableStateOf(false)
        private set
    private var answers = Planner.Answers()
    private var preselected = false
    private var lastPlan: Planner.Plan? = null
    private var gemini = GeminiPlanner()
    private var asks = 0
    // What the AI was last shown and asked, for the app's own checks on any change it suggests.
    private var lastKnown: Map<Long, TaskItem> = emptyMap()
    private var lastStudentMessage = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (context.applicationContext as NoctilApplication).repository

    init {
        start()
    }

    fun close() = scope.cancel()

    fun start() {
        messages.clear()
        answers = Planner.Answers()
        phase = Phase.Pick
        preselected = false
        selected = emptySet()
        lastPlan = null
        gemini = GeminiPlanner()
        messages += ChatMessage.Bot(
            if (ai) "Hi! Tell me what's on your plate, or pick tasks below and tap Plan these. I'll ask what I need and put them in order."
            else "Hi! I'll put your tasks in an order that fits your time. Pick what to work on, or type new tasks below, one per line.",
        )
        messages += ChatMessage.PickTasks
    }

    /** Turns AI planning on or off for this chat (and from now on). */
    fun switchAi(on: Boolean) {
        AiPlanning.setEnabled(context, on)
        ai = on && AiPlanning.status(context) == AiPlanning.Status.Ready
        messages += ChatMessage.Bot(
            when {
                ai -> "AI is on. Gemini sees the tasks you pick and your next two weeks of tasks (titles and dates), so you can say things like \"start the essay tonight\"."
                on && AiPlanning.status(context) == AiPlanning.Status.Region -> "AI planning isn't offered in your region, so I'll keep planning offline."
                on && AiPlanning.status(context) == AiPlanning.Status.Limit -> "AI planning is resting until tomorrow. I'll plan offline for now."
                else -> "AI planning is off. I'll plan on your phone."
            },
        )
    }

    /** Picks the most pressing tasks the first time the list shows up. */
    fun preselect(candidates: List<TaskItem>, today: LocalDate) {
        if (preselected || candidates.isEmpty()) return
        preselected = true
        selected = Planner.rank(candidates, Planner.Answers(), LocalDateTime.now())
            .filter { it.task.date?.let { d -> !d.isAfter(today.plusDays(1)) } ?: false || it.task.priority.value >= 3 }
            .take(5)
            .map { it.task.id }
            .toSet()
    }

    fun toggle(id: Long) {
        if (phase != Phase.Pick && !ai) return
        selected = if (id in selected) selected - id else selected + id
    }

    fun send(text: String, snapshot: Snapshot) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || thinking) return
        if (ai) {
            markAiQuestionAnswered(null)
            messages += ChatMessage.User(trimmed)
            askGemini(trimmed, snapshot)
            return
        }
        val pending = (messages.lastOrNull() as? ChatMessage.Ask)?.takeIf { it.answer == null }
        if (phase == Phase.Questions && pending != null) {
            val value = typedAnswer(pending.question, trimmed)
            if (value != null) {
                answer(pending.question, value.first, value.second, snapshot)
            } else {
                messages += ChatMessage.User(trimmed)
                messages += ChatMessage.Bot("Tap one of the answers above, or type something like \"45 min\".")
            }
            return
        }
        // Anything else typed is a list of new tasks, understood like quick add.
        messages += ChatMessage.User(trimmed)
        val names = addTasks(trimmed.split('\n', ';'))
        messages += ChatMessage.Bot(
            (if (names.size == 1) "Added to your tasks: " else "Added ${names.size} to your tasks: ") + names.joinToString(", ") + ". Tap Plan these when you're ready.",
        )
        if (phase != Phase.Pick) {
            phase = Phase.Pick
            answers = Planner.Answers()
            messages += ChatMessage.PickTasks
        }
    }

    /** Same start suggestion as quick add, so a task added here gets its "time to start" nudge too. */
    private fun withSuggestedStart(p: io.github.ryr05.noctil.parse.ParsedInput, now: LocalDateTime) =
        if (p.startAt != null) p else {
            val size = StartReminders.sizeOf(p.title, p.tags, p.priority, p.durationMinutes)
            p.copy(startAt = StartReminders.suggest(p.date, p.time, p.recurrence != null, now, StartHabits.leadFor(context, size)))
        }

    /** Adds each line as a task (parsed like quick add) and picks it. Returns short labels. */
    private fun addTasks(lines: List<String>): List<String> {
        val now = LocalDateTime.now()
        return lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parsed = withSuggestedStart(SmartParser.parse(line, now), now)
            repo.addTask(parsed, listId = null) { id -> selected = selected + id }
            buildString {
                append(parsed.title)
                parsed.date?.let { append(" (").append(relativeDay(it, now.toLocalDate())); parsed.time?.let { t -> append(", ").append(t.format(timeFormat)) }; append(")") }
            }
        }
    }

    fun planSelected(snapshot: Snapshot) {
        if (selected.isEmpty() || thinking) return
        val count = selected.size
        val text = if (count == 1) "Plan this task" else "Plan these $count tasks"
        messages += ChatMessage.User(text)
        if (ai) {
            askGemini(text, snapshot)
            return
        }
        phase = Phase.Questions
        askNext(snapshot)
    }

    fun answer(question: Planner.Question, value: String, label: String, snapshot: Snapshot) {
        if (thinking) return
        if (question.id.startsWith("ai:")) {
            markAiQuestionAnswered(label)
            messages += ChatMessage.User(label)
            askGemini(label, snapshot)
            return
        }
        val index = messages.indexOfLast { it is ChatMessage.Ask && it.question.id == question.id && it.answer == null }
        if (index < 0) return
        messages[index] = ChatMessage.Ask(question, label)
        messages += ChatMessage.User(label)
        answers = Planner.answer(answers, question, value)
        askNext(snapshot)
    }

    private fun markAiQuestionAnswered(label: String?) {
        val index = messages.indexOfLast { it is ChatMessage.Ask && it.question.id.startsWith("ai:") && it.answer == null }
        if (index >= 0) messages[index] = ChatMessage.Ask((messages[index] as ChatMessage.Ask).question, label ?: "")
    }

    private fun tasks(snapshot: Snapshot): List<TaskItem> = snapshot.tasks.filter { it.id in selected && !it.done }

    private fun askNext(snapshot: Snapshot) {
        val now = LocalDateTime.now()
        val tasks = tasks(snapshot)
        val q = Planner.nextQuestion(tasks, answers, now)
        if (q != null) {
            messages += ChatMessage.Ask(q)
            return
        }
        showPlan(snapshot, null)
    }

    private fun showPlan(snapshot: Snapshot, aiText: String?) {
        val now = LocalDateTime.now()
        val tasks = tasks(snapshot)
        if (tasks.isEmpty()) {
            messages += ChatMessage.Bot(aiText?.let { "$it " }.orEmpty() + "Pick at least one task above first.")
            return
        }
        val plan = Planner.plan(tasks, answers, now, snapshot.classes)
        lastPlan = plan
        phase = Phase.Done
        // Written from the plan itself, so the words always match the order shown.
        messages += ChatMessage.Bot(summary(plan))
        messages += ChatMessage.Result(plan, now)
    }

    private fun summary(plan: Planner.Plan): String {
        val work = plan.work.distinctBy { it.taskId }
        if (work.isEmpty()) return "Nothing fits in that time. Try more time, or pick shorter tasks."
        val first = work.first()
        val parts = mutableListOf("Start with ${first.title} (${first.reason.replaceFirstChar { it.lowercase() }}).")
        if (work.size > 1) parts += "Then " + work.drop(1).joinToString(", then ") { it.title } + "."
        if (plan.later.isNotEmpty()) parts += "The rest stays on your list for later."
        return parts.joinToString(" ")
    }

    /** One turn with Gemini. Any failure falls back to the offline planner for this chat. */
    private fun askGemini(text: String, snapshot: Snapshot) {
        val status = AiPlanning.status(context)
        if (status != AiPlanning.Status.Ready) {
            ai = false
            messages += ChatMessage.Bot(
                if (status == AiPlanning.Status.Limit) "AI planning is resting until tomorrow, so I'll plan on your phone."
                else "I'll plan on your phone instead.",
            )
            offlineContinue(snapshot)
            return
        }
        thinking = true
        AiPlanning.countOne(context)
        val picked = tasks(snapshot)
        val others = AiChangeGuard.visibleTasks(snapshot.tasks, LocalDate.now())
        lastKnown = (picked + others).associateBy { it.id }
        lastStudentMessage = text
        scope.launch {
            val reply = runCatching {
                withTimeout(50_000) { withContext(Dispatchers.IO) { gemini.send(text, picked, answers, LocalDateTime.now(), others) } }
            }
            // Tasks Gemini picked out of the message are added first, so the plan can include them.
            val added = reply.getOrNull()?.newTasks?.takeIf { it.isNotEmpty() }?.let { lines ->
                withTimeoutOrNull(5_000) { addTasksAwait(lines) }
            }.orEmpty()
            thinking = false
            reply.onSuccess { apply(it, repo.snapshot.value, added) }.onFailure { e ->
                android.util.Log.w("NoctilAI", "Gemini request failed: ${e.javaClass.simpleName}")
                ai = false
                // Keep what the student already said, like "I have 2 hours".
                if (answers.minutesAvailable == null) {
                    typedAnswer(Planner.Question("time", "", emptyList()), text)?.first?.toIntOrNull()?.let {
                        answers = answers.copy(minutesAvailable = it, asked = answers.asked + "time")
                    }
                }
                messages += ChatMessage.Bot(
                    "I couldn't reach Gemini just now, so I'll plan on your phone. " +
                        if (selected.isEmpty()) "Type your tasks one per line, or pick some above." else "",
                )
                offlineContinue(snapshot)
            }
        }
    }

    private fun offlineContinue(snapshot: Snapshot) {
        if (selected.isNotEmpty() && phase != Phase.Done) {
            phase = Phase.Questions
            askNext(snapshot)
        }
    }

    /** Adds tasks (parsed like quick add), picks them, and waits until they show up. */
    private suspend fun addTasksAwait(lines: List<String>): List<String> {
        val now = LocalDateTime.now()
        val labels = mutableListOf<String>()
        val pending = lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parsed = withSuggestedStart(SmartParser.parse(line, now), now)
            labels += buildString {
                append(parsed.title)
                parsed.date?.let { append(" (").append(relativeDay(it, now.toLocalDate())); parsed.time?.let { t -> append(", ").append(t.format(timeFormat)) }; append(")") }
            }
            CompletableDeferred<Long>().also { d -> repo.addTask(parsed, listId = null) { id -> d.complete(id) } }
        }
        val ids = pending.map { it.await() }
        selected = selected + ids
        withTimeoutOrNull(3_000) { repo.snapshot.first { s -> ids.all { id -> s.tasks.any { it.id == id } } } }
        return labels
    }

    private fun apply(r: AiReply, snapshot: Snapshot, added: List<String> = emptyList()) {
        if (added.isNotEmpty()) messages += ChatMessage.Bot("Added to your tasks: " + added.joinToString(", ") + ".")
        if (r.action == "change") {
            // Only the app says what changed, so the chat can't claim a change that didn't happen.
            applyChanges(r.changes, expected = true)
            return
        }
        if (r.changes.isNotEmpty()) applyChanges(r.changes, expected = false)
        answers = answers.copy(
            minutesAvailable = r.minutesAvailable ?: answers.minutesAvailable,
            lengths = answers.lengths + r.lengths.filterKeys { it in selected },
            favored = answers.favored + r.favored.filter { it in selected },
        )
        when (r.action) {
            "add_tasks", "ask" -> if (r.options.isEmpty()) {
                messages += ChatMessage.Bot(r.reply)
            } else {
                asks++
                messages += ChatMessage.Ask(Planner.Question("ai:$asks", r.reply, r.options.map { Planner.Option(it, it) }))
            }
            "plan" -> showPlan(snapshot, r.reply)
            // The app confirms what it actually did, so the chat never claims a reminder that wasn't set.
            "remind" -> lastPlan?.let { remind(it) } ?: run { messages += ChatMessage.Bot("Let's make a plan first, then I can remind you.") }
            else -> messages += ChatMessage.Bot(r.reply)
        }
    }

    /**
     * Carries out the AI's changes after the app's own checks: start times right away (with
     * Undo), deadlines only on a card the student confirms.
     */
    private fun applyChanges(changes: List<AiChange>, expected: Boolean) {
        val checked = AiChangeGuard.check(changes, lastKnown, lastStudentMessage, LocalDateTime.now())
        val today = LocalDate.now()
        if (checked.starts.isNotEmpty()) {
            checked.starts.forEach { repo.setStart(it.task.id, it.new) }
            val text = checked.starts.joinToString(" ") { e ->
                val new = e.new
                if (new == null) "Removed the start reminder for ${e.task.title}."
                else "${e.task.title} now starts ${StartReminders.label(new, today)}. I'll remind you then."
            }
            messages += ChatMessage.Changed(text, checked.starts)
        }
        if (checked.deadlines.isNotEmpty()) messages += ChatMessage.ConfirmDeadline(checked.deadlines)
        checked.startsRefused.firstOrNull()?.let { r ->
            val day = r.at.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            messages += ChatMessage.Bot(
                if (r.afterDeadline) {
                    val due = r.task.date?.let { relativeDay(it, today) + (r.task.time?.let { t -> ", ${t.format(timeFormat)}" } ?: "") }
                    "${r.task.title} is due $due, so it can't start $day. I left the deadline as it is. If the due date changed, " +
                        "say \"change the deadline to $day\" and I'll ask you to confirm."
                } else {
                    "${StartReminders.label(r.at, today)} has already passed. Try a later time for ${r.task.title}."
                },
            )
        }
        if (checked.deadlinesRefused > 0) {
            messages += ChatMessage.Bot("I didn't change the deadline. If the due date really changed, say something like \"change the deadline to Friday\" and I'll ask you to confirm.")
        }
        val nothing = checked.starts.isEmpty() && checked.deadlines.isEmpty() && checked.deadlinesRefused == 0 && checked.startsRefused.isEmpty()
        if (expected && nothing) {
            messages += ChatMessage.Bot("I couldn't tell which task to change. Try its name, like \"start the essay tonight\".")
        }
    }

    fun undoStarts(index: Int) {
        val m = messages.getOrNull(index) as? ChatMessage.Changed ?: return
        if (m.undone) return
        m.edits.forEach { repo.setStart(it.task.id, it.old) }
        messages[index] = m.copy(undone = true)
    }

    fun answerDeadline(index: Int, change: Boolean) {
        val m = messages.getOrNull(index) as? ChatMessage.ConfirmDeadline ?: return
        if (m.answer != null) return
        if (change) {
            m.edits.forEach { e ->
                repo.update(e.task.id) { t ->
                    val moved = t.copy(dueDate = e.newDate.toEpochDay(), dueTime = e.newTime?.toMinutes(), reminder = t.reminder ?: 0)
                    // A start after the new deadline is no use.
                    val deadline = e.newDate.atTime(e.newTime ?: java.time.LocalTime.MAX)
                    if (moved.startAt()?.isBefore(deadline) == false) moved.withStart(null) else moved
                }
            }
        }
        messages[index] = m.copy(answer = change)
    }

    fun startFocus(plan: Planner.Plan): Boolean {
        val first = plan.work.firstOrNull() ?: return false
        FocusEngine.select(context, FocusKind.Focus)
        FocusEngine.setMinutes(context, first.minutes.coerceIn(5, 180))
        FocusEngine.setTask(context, first.taskId)
        FocusEngine.start(context)
        return true
    }

    fun remind(plan: Planner.Plan) {
        if (!Notifications.canNotify(context)) {
            messages += ChatMessage.Bot("Reminders are off. Turn them on in Me, then tap Remind me again.")
            return
        }
        val n = PlanReminders.schedule(context, plan)
        messages += ChatMessage.Bot(
            when (n) {
                0 -> "Everything in this plan starts right now, so there's nothing to remind you about."
                1 -> "Done. I'll nudge you when the next one starts."
                else -> "Done. I'll nudge you when each of the $n tasks starts."
            },
        )
    }

    /** Understands typed answers like "90", "1.5 hours", "2h" or part of a task's name. */
    private fun typedAnswer(q: Planner.Question, text: String): Pair<String, String>? {
        if (q.id.startsWith("pick:")) {
            val match = q.options.firstOrNull { text.contains(it.label, ignoreCase = true) || it.label.contains(text, ignoreCase = true) }
            return match?.let { it.value to it.label }
        }
        if (q.id == "time" && Regex("rest|all day|whole|tonight", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return Planner.REST_OF_DAY.toString() to "Rest of the day"
        }
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours|m|min|mins|minutes)?", RegexOption.IGNORE_CASE).find(text) ?: return null
        val number = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = m.groupValues[2].lowercase()
        val minutes = if (unit.startsWith("h") || (unit.isEmpty() && number <= 8)) (number * 60).toInt() else number.toInt()
        if (minutes !in 5..16 * 60) return null
        return minutes.toString() to text
    }
}

@Composable
fun PlanSheet(visible: Boolean, snapshot: Snapshot, today: LocalDate, onDismiss: () -> Unit, onFocusStarted: () -> Unit) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<PlanSession?>(null) }
    LaunchedEffect(visible) {
        if (visible && session == null) session = PlanSession(context.applicationContext)
        if (!visible) {
            session?.close()
            session = null
        }
    }
    GlassSheet(visible = visible, title = "Plan my session", onDismiss = onDismiss, maxHeightFraction = 0.94f) {
        val s = session ?: return@GlassSheet
        AiSwitch(s)
        Spacer(Modifier.height(8.dp))
        PlanChat(s, snapshot, today, onFocusStarted = { onFocusStarted() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanChat(session: PlanSession, snapshot: Snapshot, today: LocalDate, onFocusStarted: () -> Unit) {
    val candidates = remember(snapshot.tasks, today, session.selected) {
        val base = Planner.candidates(snapshot.tasks, today)
        val extra = snapshot.tasks.filter { it.id in session.selected && !it.done && base.none { b -> b.id == it.id } }
        Planner.rank(base + extra, Planner.Answers(), LocalDateTime.now()).map { it.task }
    }
    LaunchedEffect(candidates) { session.preselect(candidates, today) }
    val listState = rememberLazyListState()
    LaunchedEffect(session.messages.size) {
        if (session.messages.isNotEmpty()) listState.animateScrollToItem(session.messages.lastIndex)
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (session.thinking) {
                // Shown at the end while Gemini is answering.
            }
            itemsIndexed(session.messages, key = { i, _ -> i }) { i, msg ->
                when (msg) {
                    is ChatMessage.Bot -> BotBubble(msg.text)
                    is ChatMessage.User -> UserBubble(msg.text)
                    ChatMessage.PickTasks -> PickCard(
                        tasks = candidates,
                        selected = session.selected,
                        active = session.phase == PlanSession.Phase.Pick && session.messages.lastIndexOf(ChatMessage.PickTasks) == i && !session.thinking,
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
                        onRestart = session::start,
                    )
                    is ChatMessage.Changed -> Column {
                        BotBubble(if (msg.undone) "Put the start times back." else msg.text)
                        if (!msg.undone) {
                            Spacer(Modifier.height(8.dp))
                            Pill("Undo", selected = false) { session.undoStarts(i) }
                        }
                    }
                    is ChatMessage.ConfirmDeadline -> DeadlineCard(msg, today) { change -> session.answerDeadline(i, change) }
                }
            }
        }
        if (session.thinking) {
            Spacer(Modifier.height(8.dp))
            ThinkingBubble()
        }
        Spacer(Modifier.height(10.dp))
        InputBar(
            hint = when (session.phase) {
                PlanSession.Phase.Pick -> if (session.ai) "Message the planner" else "Add tasks, one per line"
                PlanSession.Phase.Questions -> "Or type an answer"
                PlanSession.Phase.Done -> if (session.ai) "Message the planner" else "Add more tasks"
            },
            onSend = { session.send(it, snapshot) },
        )
    }
}

/** "AI" on/off for planning, with a one-time consent dialog before anything is sent. */
@Composable
private fun AiSwitch(session: PlanSession) {
    val context = LocalContext.current
    val status = remember(session.ai) { AiPlanning.status(context) }
    if (status == AiPlanning.Status.NotInBuild) return
    var consent by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (session.ai) "Planning with Gemini AI" else "Planning on your phone",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Pill(
            text = if (session.ai) "AI on" else "Use AI",
            selected = session.ai,
            leading = Icons.Rounded.AutoAwesome,
        ) {
            when {
                session.ai -> session.switchAi(false)
                AiPlanning.enabled(context) || status == AiPlanning.Status.Region -> session.switchAi(true)
                else -> consent = true
            }
        }
    }
    if (consent) {
        AiConsentDialog(
            title = "Plan with Gemini AI?",
            onDismiss = { consent = false },
            onTurnOn = {
                consent = false
                session.switchAi(true)
            },
        )
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
                        TERMS.first { it.first.startsWith("AI planning") }.second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Up to ${AiPlanning.DAILY_LIMIT} AI messages a day. Without AI, planning still works on your phone.",
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
