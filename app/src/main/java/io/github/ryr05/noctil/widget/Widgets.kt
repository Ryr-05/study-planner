package io.github.ryr05.noctil.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ryr05.noctil.Launch
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.tasks.nextExam
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * Home screen widgets, built with Jetpack Glance. They read the same on-device data as the app
 * and refresh whenever tasks change (see Surfaces.refreshAll) and at midnight.
 * Widgets can't blur what's behind them, so they use a frosted-purple panel instead of live glass.
 */

private object W {
    val text = ColorProvider(Color(0xFFF2EEFA))
    val secondary = ColorProvider(Color(0xFFB5AACB))
    val tertiary = ColorProvider(Color(0xFF948AAD))
    val lavender = ColorProvider(Color(0xFFB9A3FF))
    val onLavender = ColorProvider(Color(0xFF1B1033))
    val rose = ColorProvider(Color(0xFFFF8FA3))
    val amber = ColorProvider(Color(0xFFFFC978))
    val blue = ColorProvider(Color(0xFF8DB8FF))
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun style(size: TextUnit, color: ColorProvider = W.text, weight: FontWeight = FontWeight.Normal, align: TextAlign? = null) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = FontFamily.SansSerif, textAlign = align)

private suspend fun loadedSnapshot(context: Context): Snapshot =
    (context.applicationContext as NoctilApplication).repository.snapshot.first { it.loaded }

@Composable
private fun liveSnapshot(context: Context, initial: Snapshot): Snapshot {
    val snap by (context.applicationContext as NoctilApplication).repository.snapshot.collectAsState(initial)
    return if (snap.loaded) snap else initial
}

@Composable
private fun Panel(modifier: GlanceModifier = GlanceModifier, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .cornerRadius(24.dp)
            .padding(padding),
        content = content,
    )
}

// ---------------- Today ----------------

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = loadedSnapshot(context)
        provideContent {
            val snap = liveSnapshot(LocalContext.current, initial)
            TodayContent(snap, LocalDate.now())
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

internal fun todayTasks(snap: Snapshot, today: LocalDate): List<TaskItem> =
    snap.tasks
        .filter { it.parentId == null && !it.done && it.date != null && !it.date.isAfter(today) }
        .sortedWith(compareBy<TaskItem>({ !it.date!!.isBefore(today) }, { it.time ?: LocalTime.MAX }, { -it.priority.value }, { it.sortOrder }))

@Composable
private fun TodayContent(snap: Snapshot, today: LocalDate) {
    val context = LocalContext.current
    val tasks = todayTasks(snap, today)
    val overdue = tasks.count { it.date!!.isBefore(today) }
    Panel {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(Launch.today(context)))) {
                Text("Today", style = style(18.sp, weight = FontWeight.Bold))
                Text(
                    text = when {
                        tasks.isEmpty() -> today.format(shortDate)
                        overdue > 0 -> "${tasks.size} left, $overdue overdue"
                        tasks.size == 1 -> "1 task left"
                        else -> "${tasks.size} tasks left"
                    },
                    style = style(12.sp, if (overdue > 0) W.rose else W.secondary),
                )
            }
            Image(
                provider = ImageProvider(R.drawable.widget_add_button),
                contentDescription = "Add task",
                modifier = GlanceModifier.size(36.dp).clickable(actionStartActivity(Launch.quickAdd(context))),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (tasks.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("All clear for today", style = style(14.sp, weight = FontWeight.Medium))
                    Text("Tap + to add a task", style = style(12.sp, W.secondary))
                }
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.id }) { task -> TaskLine(task, today) }
            }
        }
    }
}

@Composable
private fun TaskLine(task: TaskItem, today: LocalDate) {
    val context = LocalContext.current
    val ring = when (task.priority) {
        Priority.High -> W.rose
        Priority.Medium -> W.amber
        Priority.Low -> W.blue
        Priority.None -> W.tertiary
    }
    val overdue = task.date!!.isBefore(today)
    val meta = listOfNotNull(
        if (overdue) "Overdue" else null,
        task.time?.format(timeFormat),
        task.list?.name,
    ).joinToString(", ")
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(actionStartActivity(Launch.openTask(context, task.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(36.dp)
                .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to task.id))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_ring),
                contentDescription = "Finish ${task.title}",
                colorFilter = ColorFilter.tint(ring),
                modifier = GlanceModifier.size(20.dp),
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(task.title, style = style(14.sp), maxLines = 1)
            if (meta.isNotEmpty()) {
                Text(meta, style = style(11.sp, if (overdue) W.rose else W.secondary), maxLines = 1)
            }
        }
    }
}

val TaskIdKey = ActionParameters.Key<Long>("task_id")

/** Tapping a task's circle in the widget finishes it. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val app = context.applicationContext as NoctilApplication
        app.repository.setDoneNow(id, true)
        TodayWidget().updateAll(context)
    }
}

// ---------------- Month calendar ----------------

class CalendarWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = loadedSnapshot(context)
        provideContent {
            val snap = liveSnapshot(LocalContext.current, initial)
            CalendarContent(snap, LocalDate.now())
        }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

@Composable
private fun CalendarContent(snap: Snapshot, today: LocalDate) {
    val context = LocalContext.current
    val size = LocalSize.current
    val month = YearMonth.from(today)
    val locale = Locale.getDefault()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val rows = (leading + month.lengthOfMonth() + 6) / 7
    val busy = snap.tasks.filter { !it.done && it.date != null }.mapNotNull { it.date }.toSet()
    val cellHeight = ((size.height.value - 14f * 2 - 26f - 18f) / rows).coerceIn(18f, 44f).dp

    Panel(padding = 12.dp) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(Launch.openDate(context, today))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                month.month.getDisplayName(JavaTextStyle.FULL, locale),
                style = style(16.sp, weight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(month.year.toString(), style = style(12.sp, W.secondary))
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            (0L until 7L).forEach { offset ->
                Text(
                    firstDayOfWeek.plus(offset).getDisplayName(JavaTextStyle.NARROW, locale),
                    style = style(10.sp, W.tertiary, align = TextAlign.Center),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        for (row in 0 until rows) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - leading + 1
                    if (day < 1 || day > month.lengthOfMonth()) {
                        Spacer(GlanceModifier.defaultWeight().height(cellHeight))
                    } else {
                        val date = month.atDay(day)
                        DayCell(date, date == today, date in busy, cellHeight, GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, hasTasks: Boolean, height: Dp, modifier: GlanceModifier) {
    val context = LocalContext.current
    val circle = minOf(height.value, 28f).dp
    Box(
        modifier = modifier.height(height).clickable(actionStartActivity(Launch.openDate(context, date))),
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            Image(ImageProvider(R.drawable.widget_today_cell), contentDescription = null, modifier = GlanceModifier.size(circle))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = style(12.sp, if (isToday) W.onLavender else W.text, if (isToday) FontWeight.Bold else FontWeight.Normal),
            )
            if (hasTasks && !isToday) {
                Image(ImageProvider(R.drawable.widget_dot), contentDescription = null, modifier = GlanceModifier.size(4.dp))
            }
        }
    }
}

// ---------------- Next exam ----------------

class ExamWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = loadedSnapshot(context)
        provideContent {
            val snap = liveSnapshot(LocalContext.current, initial)
            ExamContent(snap, LocalDate.now())
        }
    }
}

class ExamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExamWidget()
}

@Composable
private fun ExamContent(snap: Snapshot, today: LocalDate) {
    val context = LocalContext.current
    val exam = nextExam(snap, today)
    if (exam == null) {
        Panel(modifier = GlanceModifier.clickable(actionStartActivity(Launch.quickAdd(context)))) {
            Text("Next exam", style = style(12.sp, W.secondary))
            Spacer(GlanceModifier.defaultWeight())
            Text("No exams coming up", style = style(14.sp, weight = FontWeight.Medium))
            Text("Tag a task #exam to count down", style = style(11.sp, W.secondary))
        }
        return
    }
    val days = ChronoUnit.DAYS.between(today, exam.date)
    Panel(modifier = GlanceModifier.clickable(actionStartActivity(Launch.openTask(context, exam.id)))) {
        Text("Next exam", style = style(12.sp, W.secondary))
        Text(exam.title, style = style(15.sp, weight = FontWeight.Medium), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (days == 0L) "Today" else days.toString(),
                style = style(if (days == 0L) 26.sp else 36.sp, W.lavender, FontWeight.Bold),
            )
            if (days > 0) {
                Spacer(GlanceModifier.width(6.dp))
                Text(if (days == 1L) "day left" else "days left", style = style(12.sp, W.secondary), modifier = GlanceModifier.padding(bottom = 6.dp))
            }
        }
        Text(exam.date!!.format(shortDate) + (exam.time?.let { ", " + it.format(timeFormat) } ?: ""), style = style(11.sp, W.secondary))
    }
}

// ---------------- Quick add ----------------

class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickAddContent() }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

@Composable
private fun QuickAddContent() {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .cornerRadius(999.dp)
            .padding(horizontal = 10.dp)
            .clickable(actionStartActivity(Launch.quickAdd(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(R.drawable.widget_add_button), contentDescription = null, modifier = GlanceModifier.size(36.dp))
        Spacer(GlanceModifier.width(10.dp))
        Text("Add a task", style = style(15.sp, W.text, FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
    }
}
