package io.github.ryr05.noctil.ui.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.data.Completion
import io.github.ryr05.noctil.data.model.ItemColors
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.startAt
import io.github.ryr05.noctil.data.toMinutes
import io.github.ryr05.noctil.data.withStart
import io.github.ryr05.noctil.focus.FocusEngine
import io.github.ryr05.noctil.focus.FocusKind
import io.github.ryr05.noctil.parse.ParsedInput
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.plan.AiChange
import io.github.ryr05.noctil.plan.AiChangeGuard
import io.github.ryr05.noctil.plan.AiContext
import io.github.ryr05.noctil.plan.AiHabit
import io.github.ryr05.noctil.plan.AiPlanning
import io.github.ryr05.noctil.plan.AiReply
import io.github.ryr05.noctil.plan.GeminiAssistant
import io.github.ryr05.noctil.plan.PlanReminders
import io.github.ryr05.noctil.plan.Planner
import io.github.ryr05.noctil.plan.WeekPlanner
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.reminders.StartHabits
import io.github.ryr05.noctil.reminders.StartReminders
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** One line in a Noctil AI chat. */
sealed interface ChatMessage {
    data class Bot(val text: String) : ChatMessage
    data class User(val text: String) : ChatMessage
    /** A card from a saved chat, read back as text. */
    data class Note(val text: String) : ChatMessage
    /** Suggestions to tap in a new chat. */
    data object Starters : ChatMessage
    data object PickTasks : ChatMessage
    data class Ask(val question: Planner.Question, val answer: String? = null) : ChatMessage
    data class Result(val plan: Planner.Plan, val from: LocalDateTime) : ChatMessage
    /** Something the assistant changed, with Undo. */
    data class Edited(val text: String, val undo: (() -> Unit)?, val undone: Boolean = false) : ChatMessage
    /** A deadline change the student asked for, waiting for them to confirm. */
    data class ConfirmDeadline(val edits: List<AiChangeGuard.DeadlineEdit>, val answer: Boolean? = null) : ChatMessage
    /** Start times for the week, waiting for the student's OK. */
    data class WeekPlan(val slots: List<WeekPlanner.Slot>, val answer: Boolean? = null) : ChatMessage
    /** Setting up a new task, one tap-able question at a time. [step] is null once it's all set. */
    data class Setup(val taskId: Long, val title: String, val step: SetupStep?, val notes: List<String> = emptyList()) : ChatMessage
}

enum class SetupStep { Begin, Subject, Priority, Keep }

/**
 * One Noctil AI conversation. With AI on, Gemini reads the message and the app carries out what
 * it asks for, after its own checks. Without AI it still works: it adds tasks, plans a session or
 * the week, and answers simple questions from the student's own data.
 */
class AssistantSession(private val context: Context, saved: ChatStore.Chat? = null) {
    enum class Phase { Idle, Pick, Questions, Done }

    val id: String = saved?.id ?: UUID.randomUUID().toString()
    var title by mutableStateOf(saved?.title ?: NEW_TITLE)
        private set
    val messages = mutableStateListOf<ChatMessage>()
    var phase by mutableStateOf(Phase.Idle)
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
    private var asks = 0
    private var gemini = GeminiAssistant(
        saved?.lines.orEmpty().filter { it.role != ChatStore.Role.Note }.map { (it.role == ChatStore.Role.User) to it.text },
    )
    // What the AI was last shown and asked, for the app's own checks on what it suggests.
    private var lastKnown: Map<Long, TaskItem> = emptyMap()
    private var lastStudentMessage = ""
    // New tasks still waiting for their setup questions.
    private val setupQueue = ArrayDeque<Long>()
    // Text the offline assistant wasn't sure about, kept for "Add it as a task".
    private var pendingText = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (context.applicationContext as NoctilApplication).repository

    init {
        if (saved != null) {
            messages += saved.lines.map {
                when (it.role) {
                    ChatStore.Role.User -> ChatMessage.User(it.text)
                    ChatStore.Role.Bot -> ChatMessage.Bot(it.text)
                    ChatStore.Role.Note -> ChatMessage.Note(it.text)
                }
            }
        } else {
            messages += ChatMessage.Bot(greeting())
            messages += ChatMessage.Starters
        }
        // Saved on the phone after every change, once the student has said something.
        scope.launch {
            snapshotFlow { messages.toList() to thinking }.collect { (_, busy) ->
                if (!busy) withContext(Dispatchers.IO) { ChatStore.save(context, toChat()) }
            }
        }
    }

    fun close() = scope.cancel()

    private fun greeting() =
        if (ai) "Hi! I'm Noctil AI. Ask me what's due, plan a session or your week, add or change tasks, or set up a habit."
        else "Hi! I'm Noctil AI in offline mode, so I keep it simple: I can add tasks, plan a session or your week, " +
            "and tell you what's due. Turn on AI to just talk to me."

    /** Turns AI on or off for this chat (and from now on). */
    fun switchAi(on: Boolean) {
        AiPlanning.setEnabled(context, on)
        ai = on && AiPlanning.status(context) == AiPlanning.Status.Ready
        messages += ChatMessage.Bot(
            when {
                ai -> "AI is on. Talk to me normally, like \"move my essay start to tonight\" or \"what's due friday?\"."
                on && AiPlanning.status(context) == AiPlanning.Status.Region -> "AI isn't offered in your region, so I'll stay in offline mode."
                on && AiPlanning.status(context) == AiPlanning.Status.Limit -> "AI is resting until tomorrow. I'll stay in offline mode for now."
                else -> "Offline mode. I can still add tasks, plan, and tell you what's due."
            },
        )
    }

    // ---------- Sending ----------

    /** A tap on a starter suggestion. "Add tasks" just explains, so nothing is added by accident. */
    fun starter(value: String, label: String, snapshot: Snapshot) {
        if (value == "add") {
            nameFrom(label)
            messages += ChatMessage.User(label)
            messages += ChatMessage.Bot("Type them below, one per line, like \"essay fri 5pm\" or \"chem quiz thu 9am #exam\". You can paste a whole list too.")
            return
        }
        send(label, snapshot)
    }

    fun send(text: String, snapshot: Snapshot) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || thinking) return
        nameFrom(trimmed)
        if (ai) {
            markAiQuestionAnswered(null)
            messages += ChatMessage.User(trimmed)
            askGemini(trimmed, snapshot)
            return
        }
        val pending = (messages.lastOrNull() as? ChatMessage.Ask)?.takeIf { it.answer == null && it.question.id.let { q -> !q.startsWith("ai:") && !q.startsWith("off:") } }
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
        messages += ChatMessage.User(trimmed)
        offline(trimmed, snapshot)
    }

    private fun nameFrom(text: String) {
        if (title == NEW_TITLE) title = ChatStore.titleFor(text)
    }

    /** The offline assistant: works out what was asked and answers from the student's own data. */
    private fun offline(text: String, snapshot: Snapshot) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        when (OfflineAssistant.intentOf(text, now)) {
            OfflineAssistant.Intent.PlanSession -> startPicking()
            OfflineAssistant.Intent.PlanWeek -> offerWeekPlan(
                WeekPlanner.plan(snapshot.tasks, snapshot.classes, now) { size -> StartHabits.leadFor(context, size) },
            )
            OfflineAssistant.Intent.Due -> messages += ChatMessage.Bot(OfflineAssistant.dueAnswer(text, snapshot.tasks, today))
            OfflineAssistant.Intent.NextExam -> messages += ChatMessage.Bot(OfflineAssistant.nextExamAnswer(snapshot.tasks, today))
            OfflineAssistant.Intent.Classes -> messages += ChatMessage.Bot(OfflineAssistant.classesAnswer(text, snapshot.classes, today))
            OfflineAssistant.Intent.Habits -> messages += ChatMessage.Bot(OfflineAssistant.habitsAnswer(snapshot.habits, today))
            OfflineAssistant.Intent.AddTasks -> {
                val body = OfflineAssistant.stripAdd(text)
                if (body.isBlank() || body.matches(Regex("(?i)(some )?(a )?tasks?"))) {
                    messages += ChatMessage.Bot("Type them below, one per line, like \"essay fri 5pm\".")
                } else {
                    val names = addTasks(body.split('\n', ';'))
                    messages += ChatMessage.Bot(
                        (if (names.size == 1) "Added to your tasks: " else "Added ${names.size} to your tasks: ") + names.joinToString(", ") + ".",
                    )
                }
            }
            OfflineAssistant.Intent.Unknown -> {
                pendingText = text
                val short = if (text.length > 28) text.take(28).trimEnd() + "..." else text
                asks++
                messages += ChatMessage.Ask(
                    Planner.Question(
                        "off:$asks",
                        "In offline mode I can't chat, but I can do these:",
                        listOf(
                            Planner.Option(label = "Add \"$short\" as a task", value = "add"),
                            Planner.Option(label = "Plan my session", value = "session"),
                            Planner.Option(label = "Plan my week", value = "week"),
                            Planner.Option(label = "What's due this week?", value = "due"),
                        ),
                    ),
                )
            }
        }
    }

    private fun startPicking() {
        phase = Phase.Pick
        answers = Planner.Answers()
        preselected = false
        selected = emptySet()
        messages += ChatMessage.Bot("Pick what you want to work on, then tap Plan these. I'll ask what I need and put them in order.")
        messages += ChatMessage.PickTasks
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
        if (phase != Phase.Pick) return
        selected = if (id in selected) selected - id else selected + id
    }

    fun planSelected(snapshot: Snapshot) {
        if (selected.isEmpty() || thinking) return
        val count = selected.size
        messages += ChatMessage.User(if (count == 1) "Plan this task" else "Plan these $count tasks")
        phase = Phase.Questions
        askNext(snapshot)
    }

    fun answer(question: Planner.Question, value: String, label: String, snapshot: Snapshot) {
        if (thinking) return
        val index = messages.indexOfLast { it is ChatMessage.Ask && it.question.id == question.id && it.answer == null }
        if (question.id.startsWith("ai:")) {
            markAiQuestionAnswered(label)
            messages += ChatMessage.User(label)
            askGemini(label, snapshot)
            return
        }
        if (index < 0) return
        messages[index] = ChatMessage.Ask(question, label)
        if (question.id.startsWith("off:")) {
            if (value == "add") {
                messages += ChatMessage.User("Add it as a task")
                val names = addTasks(listOf(pendingText))
                messages += ChatMessage.Bot("Added to your tasks: " + names.joinToString(", ") + ".")
            } else {
                messages += ChatMessage.User(label)
                offline(label, snapshot)
            }
            return
        }
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
        val q = Planner.nextQuestion(tasks(snapshot), answers, LocalDateTime.now())
        if (q != null) {
            messages += ChatMessage.Ask(q)
            return
        }
        showPlan(snapshot)
    }

    private fun showPlan(snapshot: Snapshot) {
        val now = LocalDateTime.now()
        val tasks = tasks(snapshot)
        if (tasks.isEmpty()) {
            messages += ChatMessage.Bot("There's nothing to plan yet. Add a task or two, then ask me again.")
            return
        }
        val plan = Planner.plan(tasks, answers, now, snapshot.classes)
        lastPlan = plan
        // Saved as today's plan, so it stays in Focus and on the Noctil AI tab after the chat closes.
        io.github.ryr05.noctil.plan.TodayPlan.save(context, plan)
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

    // ---------- Gemini ----------

    /** One turn with Gemini. Any failure falls back to offline mode for this chat. */
    private fun askGemini(text: String, snapshot: Snapshot) {
        val status = AiPlanning.status(context)
        if (status != AiPlanning.Status.Ready) {
            ai = false
            messages += ChatMessage.Bot(
                if (status == AiPlanning.Status.Limit) "AI is resting until tomorrow, so I'm in offline mode." else "I'm in offline mode now.",
            )
            offline(text, snapshot)
            return
        }
        thinking = true
        AiPlanning.countOne(context)
        val now = LocalDateTime.now()
        val ctx = AiContext(
            tasks = AiChangeGuard.visibleTasks(snapshot.tasks, now.toLocalDate()),
            classes = snapshot.classes,
            habits = snapshot.habits,
            lists = snapshot.lists.map { it.name },
            answers = answers,
        )
        lastKnown = ctx.tasks.associateBy { it.id }
        lastStudentMessage = text
        scope.launch {
            val reply = runCatching {
                withTimeout(50_000) { withContext(Dispatchers.IO) { gemini.send(text, ctx, now) } }
            }
            // Tasks Gemini picked out of the message are added first, so a plan can include them.
            val added = reply.getOrNull()?.newTasks?.takeIf { it.isNotEmpty() }?.let { lines ->
                withTimeoutOrNull(5_000) { addTasksAwait(lines) }
            }
            thinking = false
            reply.onSuccess { apply(it, repo.snapshot.value, added) }.onFailure { e ->
                android.util.Log.w("NoctilAI", "Gemini request failed: ${e.javaClass.simpleName}")
                ai = false
                messages += ChatMessage.Bot("I couldn't reach Gemini just now, so I'm in offline mode.")
                offline(text, snapshot)
            }
        }
    }

    private fun apply(r: AiReply, snapshot: Snapshot, added: Added?) {
        if (added != null && added.labels.isNotEmpty()) messages += ChatMessage.Bot("Added to your tasks: " + added.labels.joinToString(", ") + ".")
        answers = answers.copy(
            minutesAvailable = r.minutesAvailable ?: answers.minutesAvailable,
            lengths = answers.lengths + r.lengths,
            favored = answers.favored + r.favored,
        )
        when (r.action) {
            // Only the app says what changed, so the chat can't claim a change that didn't happen.
            "change" -> applyChanges(r.changes, expected = true, snapshot)
            "plan_week" -> {
                val checked = AiChangeGuard.check(r.changes.filter { it.start != null }, lastKnown, lastStudentMessage, LocalDateTime.now())
                offerWeekPlan(checked.starts.mapNotNull { e -> e.new?.let { WeekPlanner.Slot(e.task, it) } })
            }
            "add_habits" -> if (r.newHabits.isEmpty()) messages += ChatMessage.Bot(r.reply) else addHabits(r.newHabits, snapshot)
            "add_tasks", "ask" -> if (r.options.isEmpty()) {
                // "Added to your tasks" already said it; the AI's own "I have added..." would repeat it.
                if (!(r.action == "add_tasks" && added != null && added.labels.isNotEmpty())) messages += ChatMessage.Bot(r.reply)
            } else {
                asks++
                messages += ChatMessage.Ask(Planner.Question("ai:$asks", r.reply, r.options.map { Planner.Option(it, it) }))
            }
            "plan" -> {
                val open = snapshot.tasks.filter { !it.done }.map { it.id }.toSet()
                val chosen = r.planTaskIds.filter { it in open }.toSet() + added?.ids.orEmpty()
                selected = chosen.ifEmpty {
                    Planner.rank(Planner.candidates(snapshot.tasks, LocalDate.now()), answers, LocalDateTime.now()).take(5).map { it.task.id }.toSet()
                }
                answers = answers.copy(lengths = answers.lengths.filterKeys { it in selected }, favored = answers.favored.filter { it in selected }.toSet())
                showPlan(snapshot)
            }
            // The app confirms what it actually did, so the chat never claims a reminder that wasn't set.
            "remind" -> lastPlan?.let { remind(it) } ?: run { messages += ChatMessage.Bot("Let's make a plan first, then I can remind you.") }
            else -> messages += ChatMessage.Bot(r.reply)
        }
        if (r.action != "change" && r.action != "plan_week" && r.changes.isNotEmpty()) applyChanges(r.changes, expected = false, snapshot)
        if (r.action != "add_habits" && r.newHabits.isNotEmpty()) addHabits(r.newHabits, snapshot)
        // New tasks get their setup questions (unless the student is in the middle of planning).
        if (added != null && r.action != "plan") queueSetup(lastAdded)
    }

    /**
     * Carries out the AI's changes after the app's own checks: start times and other edits right
     * away (with Undo), deadlines only on a card the student confirms.
     */
    private fun applyChanges(changes: List<AiChange>, expected: Boolean, snapshot: Snapshot) {
        val checked = AiChangeGuard.check(changes, lastKnown, lastStudentMessage, LocalDateTime.now(), snapshot.lists)
        val today = LocalDate.now()
        if (checked.starts.isNotEmpty()) {
            val edits = checked.starts
            edits.forEach { repo.setStart(it.task.id, it.new) }
            val text = edits.joinToString(" ") { e ->
                val new = e.new
                if (new == null) "Removed the start reminder for ${e.task.title}."
                else "${e.task.title} now starts ${StartReminders.label(new, today)}. I'll remind you then."
            }
            messages += ChatMessage.Edited(text, undo = { edits.forEach { repo.setStart(it.task.id, it.old) } })
        }
        checked.fields.forEach(::applyFieldEdit)
        if (checked.deadlines.isNotEmpty()) messages += ChatMessage.ConfirmDeadline(checked.deadlines)
        checked.startsRefused.firstOrNull()?.let { r ->
            val day = r.at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
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
        checked.unknownLists.firstOrNull()?.let {
            messages += ChatMessage.Bot("You don't have a list called $it yet. Make it in your lists first, then ask me again.")
        }
        val nothing = checked.starts.isEmpty() && checked.deadlines.isEmpty() && checked.deadlinesRefused == 0 &&
            checked.startsRefused.isEmpty() && checked.fields.isEmpty() && checked.unknownLists.isEmpty()
        if (expected && nothing) {
            messages += ChatMessage.Bot("I couldn't tell what to change. Try the task's name, like \"start the essay tonight\".")
        }
    }

    /** Rename, priority, tags, list and done, each undoable. Never deletes anything. */
    private fun applyFieldEdit(f: AiChangeGuard.FieldEdit) {
        val id = f.task.id
        val parts = mutableListOf<String>()
        val undos = mutableListOf<() -> Unit>()
        f.title?.let { new ->
            val old = f.task.title
            repo.update(id) { it.copy(title = new) }
            undos += { repo.update(id) { it.copy(title = old) } }
            parts += "renamed to \"$new\""
        }
        f.priority?.let { new ->
            val old = f.task.priority
            repo.update(id) { it.copy(priority = new.value) }
            undos += { repo.update(id) { it.copy(priority = old.value) } }
            parts += if (new == Priority.None) "no priority" else "${new.label.lowercase()} priority"
        }
        f.list?.let { new ->
            val old = f.task.list?.id
            repo.update(id) { it.copy(listId = new.id) }
            undos += { repo.update(id) { it.copy(listId = old) } }
            parts += "moved to ${new.name}"
        }
        if (f.tagsAdd.isNotEmpty()) {
            f.tagsAdd.forEach { name ->
                repo.addTag(id, name)
                undos += { repo.removeTagByName(id, name) }
            }
            parts += "tagged " + f.tagsAdd.joinToString(" ") { "#$it" }
        }
        if (f.done) {
            var completion: Completion? = null
            repo.setDone(id, true) { completion = it }
            undos += { completion?.let(repo::undoCompletion) ?: repo.setDone(id, false) }
            parts += "marked done"
        }
        if (parts.isEmpty()) return
        messages += ChatMessage.Edited("${f.task.title}: ${parts.joinToString(", ")}.", undo = { undos.asReversed().forEach { it() } })
    }

    fun undo(index: Int) {
        val m = messages.getOrNull(index) as? ChatMessage.Edited ?: return
        if (m.undone) return
        m.undo?.invoke()
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
                    val deadline = e.newDate.atTime(e.newTime ?: LocalTime.MAX)
                    if (moved.startAt()?.isBefore(deadline) == false) moved.withStart(null) else moved
                }
            }
        }
        messages[index] = m.copy(answer = change)
    }

    // ---------- Week ----------

    private fun offerWeekPlan(slots: List<WeekPlanner.Slot>) {
        if (slots.isEmpty()) {
            messages += ChatMessage.Bot("Everything due this week already has a start time, or nothing big is due. You're set.")
            return
        }
        messages += ChatMessage.Bot("Here's when to start each one this week. Nothing changes until you tap Apply.")
        messages += ChatMessage.WeekPlan(slots)
    }

    fun answerWeekPlan(index: Int, apply: Boolean) {
        val m = messages.getOrNull(index) as? ChatMessage.WeekPlan ?: return
        if (m.answer != null) return
        messages[index] = m.copy(answer = apply)
        if (!apply) return
        val old = m.slots.map { it.task.id to it.task.startAt }
        m.slots.forEach { repo.setStart(it.task.id, it.start) }
        val n = m.slots.size
        messages += ChatMessage.Edited(
            if (n == 1) "Set 1 start reminder. I'll nudge you when it's time." else "Set $n start reminders. I'll nudge you each time.",
            undo = { old.forEach { (id, start) -> repo.setStart(id, start) } },
        )
    }

    // ---------- Habits ----------

    private fun addHabits(habits: List<AiHabit>, snapshot: Snapshot) {
        val existing = snapshot.habits.map { it.name.lowercase() }.toSet()
        val fresh = habits.filter { it.name.lowercase() !in existing }
        if (fresh.isEmpty()) {
            messages += ChatMessage.Bot("You already have that habit. Check it off in Habits.")
            return
        }
        val ids = mutableListOf<Long>()
        fresh.forEachIndexed { i, h ->
            val color = ItemColors[(snapshot.habits.size + i + 2) % ItemColors.size]
            repo.saveHabit(null, h.name, color, h.days, h.goal, h.reminder) { id -> ids += id }
        }
        val text = fresh.joinToString(" ") { h ->
            val goal = if (h.goal == 1) "once a day" else "${h.goal} times a day"
            val days = when {
                h.days.size == 7 -> "every day"
                h.days == DayOfWeek.entries.filter { it.value <= 5 }.toSet() -> "on weekdays"
                else -> "on " + h.days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            }
            "Added the habit ${h.name}: $goal, $days" + (h.reminder?.let { ", reminder at ${it.format(timeFormat)}" } ?: "") + "."
        }
        messages += ChatMessage.Edited(text, undo = { ids.forEach(repo::deleteHabit) })
    }

    // ---------- Adding tasks ----------

    /** Same start suggestion as quick add, so a task added here gets its "time to start" nudge too. */
    private fun withSuggestedStart(p: ParsedInput, now: LocalDateTime) =
        if (p.startAt != null) p else {
            val size = StartReminders.sizeOf(p.title, p.tags, p.priority, p.durationMinutes)
            p.copy(startAt = StartReminders.suggest(p.date, p.time, p.recurrence != null, now, StartHabits.leadFor(context, size)))
        }

    private fun label(p: ParsedInput, today: LocalDate) = buildString {
        append(p.title)
        p.date?.let { append(" (").append(relativeDay(it, today)); p.time?.let { t -> append(", ").append(t.format(timeFormat)) }; append(")") }
    }

    /** Adds each line as a task (parsed like quick add), then asks the setup questions. Returns short labels. */
    private fun addTasks(lines: List<String>): List<String> {
        val now = LocalDateTime.now()
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val ids = mutableListOf<Long>()
        return clean.map { line ->
            val parsed = withSuggestedStart(SmartParser.parse(line, now), now)
            repo.addTask(parsed, listId = null) { id ->
                selected = selected + id
                ids += id
                if (ids.size == clean.size) scope.launch { awaitTasks(ids); queueSetup(ids) }
            }
            label(parsed, now.toLocalDate())
        }
    }

    private suspend fun awaitTasks(ids: List<Long>) {
        withTimeoutOrNull(3_000) { repo.snapshot.first { s -> ids.all { id -> s.tasks.any { it.id == id } } } }
    }

    // ---------- Setting up new tasks ----------

    /** Asks the setup questions for a few new tasks; a long list (a syllabus) skips them. */
    private fun queueSetup(ids: List<Long>) {
        if (ids.isEmpty() || ids.size > 3) return
        setupQueue.addAll(ids)
        if (messages.none { it is ChatMessage.Setup && it.step != null }) nextSetup()
    }

    private fun nextSetup() {
        val id = setupQueue.removeFirstOrNull() ?: return
        val task = repo.snapshot.value.byId[id] ?: return nextSetup()
        val step = firstStep(task, null) ?: return nextSetup()
        messages += ChatMessage.Setup(id, task.title, step)
    }

    /** The next question worth asking, skipping what's already set (typed #chem, !1...). */
    private fun firstStep(task: TaskItem, after: SetupStep?): SetupStep? {
        val order = SetupStep.entries.dropWhile { after != null && it.ordinal <= after.ordinal }
        return order.firstOrNull { step ->
            when (step) {
                SetupStep.Begin -> true
                SetupStep.Subject -> task.tags.isEmpty() && (repo.snapshot.value.tags.isNotEmpty() || repo.snapshot.value.classes.isNotEmpty())
                SetupStep.Priority -> task.priority == Priority.None
                SetupStep.Keep -> task.date != null && !task.date.isBefore(LocalDate.now().plusDays(2)) && task.keepEvery == 0 &&
                    StartReminders.sizeOf(task.title, task.tags.map { it.name }, task.priority, task.durationMinutes) == StartReminders.Size.Big
            }
        }
    }

    /** The answers offered for a setup question, as (label, value). */
    fun setupOptions(m: ChatMessage.Setup, snapshot: Snapshot): List<Pair<String, String>> {
        val task = snapshot.byId[m.taskId] ?: return emptyList()
        val today = LocalDate.now()
        return when (m.step) {
            SetupStep.Begin -> buildList {
                task.startAt?.takeIf { it.isAfter(LocalDateTime.now()) }?.let { add("${StartReminders.label(it, today)} (suggested)" to "keep") }
                StartReminders.choices(task.date, task.time, LocalDateTime.now())
                    .filter { it.at != task.startAt }
                    .take(3)
                    .forEach { c -> add((c.hint?.let { h -> "$h, " } ?: "") + StartReminders.label(c.at, today) to "at:${c.at}") }
                add("Pick a day and time" to "pick")
                add("No start" to "none")
            }
            SetupStep.Subject -> buildList {
                // A subject from the task's own name first ("Chem lab report" offers #chem), then classes and tags.
                val fromTitle = task.title.split(' ').firstOrNull { w -> w.length >= 3 && w.lowercase() !in SETUP_SKIP_WORDS }?.lowercase()
                val names = (listOfNotNull(fromTitle) + snapshot.classes.map { it.name.split(' ').first().lowercase() } + snapshot.tags.map { it.name })
                    .map { it.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                names.take(6).forEach { add("#$it" to "tag:$it") }
                add("None" to "none")
            }
            SetupStep.Priority -> listOf("High" to "p:3", "Medium" to "p:2", "Low" to "p:1", "Not sure" to "p:0")
            SetupStep.Keep -> listOf("Every day" to "keep:1", "Every other day" to "keep:2", "No thanks" to "keep:0")
            null -> emptyList()
        }
    }

    fun setupQuestion(m: ChatMessage.Setup): String = when (m.step) {
        SetupStep.Begin -> "When would you like to begin with ${m.title}?"
        SetupStep.Subject -> "Which subject is it for?"
        SetupStep.Priority -> "How important is it?"
        SetupStep.Keep -> "It's a big one. Want a nudge to keep at it until it's due?"
        null -> ""
    }

    /** One tap on a setup answer: saves it and moves to the next question (or the next new task). */
    fun answerSetup(index: Int, value: String, label: String) {
        val m = messages.getOrNull(index) as? ChatMessage.Setup ?: return
        val step = m.step ?: return
        val id = m.taskId
        var note: String? = null
        when {
            value == "skip" -> {
                messages[index] = m.copy(step = null, notes = m.notes + "kept the suggestions")
                nextSetup()
                return
            }
            value == "keep" -> note = "start " + label.removeSuffix(" (suggested)")
            value.startsWith("at:") -> runCatching { LocalDateTime.parse(value.removePrefix("at:")) }.getOrNull()?.let {
                repo.setStart(id, it)
                note = "start " + StartReminders.label(it, LocalDate.now())
            }
            value == "none" && step == SetupStep.Begin -> {
                repo.setStart(id, null)
                note = "no start"
            }
            value.startsWith("tag:") -> {
                repo.addTag(id, value.removePrefix("tag:"))
                note = "#" + value.removePrefix("tag:")
            }
            value.startsWith("p:") -> {
                val p = Priority.of(value.removePrefix("p:").toInt())
                repo.update(id) { it.copy(priority = p.value) }
                if (p != Priority.None) note = p.label.lowercase() + " priority"
            }
            value.startsWith("keep:") -> {
                val days = value.removePrefix("keep:").toInt()
                repo.setKeepAtIt(id, days)
                if (days > 0) note = if (days == 1) "nudge every day" else "nudge every other day"
            }
        }
        val notes = m.notes + listOfNotNull(note)
        // Ask the next question from fresh data, so it sees the tag or priority just set.
        scope.launch {
            withTimeoutOrNull(1_000) { repo.snapshot.first { s -> s.byId[id] != null } }
            val task = repo.snapshot.value.byId[id]
            val next = task?.let { firstStep(it, step) }
            messages[index] = m.copy(step = next, notes = notes)
            if (next == null) nextSetup()
        }
    }

    /** A start picked from the full picker during setup. */
    fun answerSetupStart(index: Int, at: LocalDateTime) =
        answerSetup(index, "at:$at", StartReminders.label(at, LocalDate.now()))

    private class Added(val labels: List<String>, val ids: List<Long>)
    private var lastAdded: List<Long> = emptyList()

    /** Adds tasks, picks them, and waits until they show up, so a plan can include them. */
    private suspend fun addTasksAwait(lines: List<String>): Added {
        val now = LocalDateTime.now()
        val labels = mutableListOf<String>()
        val pending = lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parsed = withSuggestedStart(SmartParser.parse(line, now), now)
            labels += label(parsed, now.toLocalDate())
            CompletableDeferred<Long>().also { d -> repo.addTask(parsed, listId = null) { id -> d.complete(id) } }
        }
        val ids = pending.map { it.await() }
        selected = selected + ids
        awaitTasks(ids)
        lastAdded = ids
        return Added(labels, ids)
    }

    // ---------- Plan actions ----------

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

    // ---------- Saving ----------

    /** The chat as saved on the phone: cards become short notes. */
    fun toChat(): ChatStore.Chat {
        // Saved notes use real dates, since "tonight" means nothing when the chat is reread later.
        val today = LocalDate.of(1970, 1, 1)
        val lines = messages.mapNotNull { m ->
            when (m) {
                is ChatMessage.Bot -> ChatStore.Line(ChatStore.Role.Bot, m.text)
                is ChatMessage.User -> ChatStore.Line(ChatStore.Role.User, m.text)
                is ChatMessage.Note -> ChatStore.Line(ChatStore.Role.Note, m.text)
                is ChatMessage.Ask -> ChatStore.Line(ChatStore.Role.Bot, m.question.text)
                is ChatMessage.Result -> ChatStore.Line(ChatStore.Role.Note, "Plan: " + m.plan.work.joinToString("; ") { "${it.start.format(timeFormat)} ${it.title}" })
                is ChatMessage.Edited -> ChatStore.Line(ChatStore.Role.Note, if (m.undone) "Undone: ${m.text}" else m.text)
                is ChatMessage.ConfirmDeadline -> ChatStore.Line(
                    ChatStore.Role.Note,
                    m.edits.joinToString("; ") { "Deadline for ${it.task.title} to ${relativeDay(it.newDate, today)}" } +
                        when (m.answer) {
                            true -> " (changed)"
                            false -> " (kept as it was)"
                            null -> " (not answered)"
                        },
                )
                is ChatMessage.WeekPlan -> ChatStore.Line(
                    ChatStore.Role.Note,
                    "Week plan: " + m.slots.joinToString("; ") { "${it.task.title} ${StartReminders.label(it.start, today)}" } +
                        if (m.answer == true) " (applied)" else " (not applied)",
                )
                is ChatMessage.Setup -> if (m.step == null) ChatStore.Line(ChatStore.Role.Note, "${m.title}: " + m.notes.joinToString(", ")) else null
                ChatMessage.Starters, ChatMessage.PickTasks -> null
            }
        }
        return ChatStore.Chat(id, title, System.currentTimeMillis(), lines)
    }

    companion object {
        const val NEW_TITLE = "New chat"
        private val SETUP_SKIP_WORDS = setOf("read", "write", "study", "finish", "start", "review", "email", "call", "the", "my", "do", "make", "submit")
    }
}
