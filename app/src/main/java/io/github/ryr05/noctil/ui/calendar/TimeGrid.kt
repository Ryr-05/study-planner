package io.github.ryr05.noctil.ui.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryr05.noctil.calendar.PhoneEvent
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Something with a start and end time on a given day: a task, a class, or a phone calendar event. */
sealed interface CalBlock {
    val key: String
    val date: LocalDate
    val start: Int // minutes after midnight
    val end: Int
    val color: Color
    val title: String
    val subtitle: String?

    data class OfTask(val task: TaskItem, override val date: LocalDate, override val start: Int, override val end: Int) : CalBlock {
        override val key get() = "t${task.id}"
        override val color get() = Palette.Lavender
        override val title get() = task.title
        override val subtitle: String? get() = null
    }

    data class OfClass(val cls: SchoolClass, override val date: LocalDate) : CalBlock {
        override val key get() = "c${cls.id}-$date"
        override val start get() = cls.start.hour * 60 + cls.start.minute
        override val end get() = cls.end.hour * 60 + cls.end.minute
        override val color get() = cls.color
        override val title get() = cls.name
        override val subtitle get() = cls.room.ifBlank { null }
    }

    data class OfEvent(val event: PhoneEvent, override val date: LocalDate, override val start: Int, override val end: Int) : CalBlock {
        override val key get() = "e${event.eventId}-${event.begin}"
        override val color get() = event.color
        override val title get() = event.title
        override val subtitle get() = event.location.ifBlank { null }
    }
}

/** All timed blocks for each day in [days]. */
fun timedBlocks(days: List<LocalDate>, snap: Snapshot, events: List<PhoneEvent>): Map<LocalDate, List<CalBlock>> {
    val zone = ZoneId.systemDefault()
    return days.associateWith { day ->
        val tasks = snap.tasks.filter { it.date == day && it.time != null && (it.parentId == null || !it.done) }.map {
            val start = it.time!!.hour * 60 + it.time.minute
            CalBlock.OfTask(it, day, start, (start + it.blockMinutes).coerceAtMost(24 * 60))
        }
        val classes = snap.classes.filter { it.meetsOn(day) }.map { CalBlock.OfClass(it, day) }
        val evs = events.filter { !it.allDay && it.startDate(zone) == day }.map {
            val s = Instant.ofEpochMilli(it.begin).atZone(zone)
            val e = Instant.ofEpochMilli(it.end).atZone(zone)
            val startMin = s.hour * 60 + s.minute
            val endMin = if (e.toLocalDate().isAfter(day)) 24 * 60 else e.hour * 60 + e.minute
            CalBlock.OfEvent(it, day, startMin, endMin.coerceAtLeast(startMin + 15))
        }
        classes + evs + tasks
    }
}

private data class Placed(val block: CalBlock, val lane: Int, val lanes: Int)

/** Puts overlapping blocks side by side. */
private fun layOut(blocks: List<CalBlock>): List<Placed> {
    val sorted = blocks.sortedWith(compareBy({ it.start }, { -it.end }))
    val result = mutableListOf<Placed>()
    var cluster = mutableListOf<Pair<CalBlock, Int>>()
    val laneEnds = mutableListOf<Int>()
    var clusterEnd = -1
    fun flush() {
        val lanes = laneEnds.size.coerceAtLeast(1)
        cluster.forEach { (b, lane) -> result += Placed(b, lane, lanes) }
        cluster = mutableListOf()
        laneEnds.clear()
    }
    for (b in sorted) {
        val visibleEnd = maxOf(b.end, b.start + 20)
        if (b.start >= clusterEnd) {
            flush()
            clusterEnd = visibleEnd
        } else {
            clusterEnd = maxOf(clusterEnd, visibleEnd)
        }
        val lane = laneEnds.indexOfFirst { it <= b.start }.takeIf { it >= 0 } ?: laneEnds.size.also { laneEnds += 0 }
        laneEnds[lane] = visibleEnd
        cluster += b to lane
    }
    flush()
    return result
}

val HourHeight = 56.dp
private val LabelWidth = 48.dp

fun snapTo15(minutes: Int): Int = ((minutes / 15f).roundToInt() * 15).coerceIn(0, 24 * 60 - 15)

/**
 * A scrollable day grid with one column per day. Tap an empty slot to plan something there.
 * Long-press a task and drag to move it (it snaps to 15 minutes; sideways changes the day).
 */
@Composable
fun TimeGrid(
    days: List<LocalDate>,
    today: LocalDate,
    blocks: Map<LocalDate, List<CalBlock>>,
    scroll: ScrollState,
    bottomPadding: Dp,
    onSlotTap: (LocalDate, LocalTime) -> Unit,
    onMoveTask: (TaskItem, LocalDate, LocalTime) -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    onOpenEvent: (PhoneEvent) -> Unit,
    onOpenClass: (SchoolClass) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val hourLabel = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a") }
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }
    val slotTap by rememberUpdatedState(onSlotTap)

    LaunchedEffect(Unit) {
        val startHour = if (today in days) (now.hour - 1).coerceIn(0, 18) else 7
        scroll.scrollTo(with(density) { (HourHeight * startHour).roundToPx() })
    }

    Column(modifier.verticalScroll(scroll)) {
        Row(Modifier.fillMaxWidth().height(HourHeight * 24)) {
            Column(Modifier.width(LabelWidth)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(HourHeight)) {
                        if (h > 0) {
                            Text(
                                LocalTime.of(h, 0).format(hourLabel),
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextTertiary,
                                modifier = Modifier.offset(y = (-7).dp).padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                val colWidth = maxWidth / days.size
                val colPx = with(density) { colWidth.toPx() }
                val hourPx = with(density) { HourHeight.toPx() }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(days) {
                            detectTapGestures { pos ->
                                val dayIndex = (pos.x / colPx).toInt().coerceIn(0, days.lastIndex)
                                val minutes = ((pos.y / hourPx * 60).toInt() / 30 * 30).coerceIn(0, 23 * 60 + 30)
                                slotTap(days[dayIndex], LocalTime.of(minutes / 60, minutes % 60))
                            }
                        }
                        .semantics { contentDescription = "Calendar grid. Tap a time to plan something." },
                ) {
                    for (h in 1 until 24) {
                        drawLine(Palette.Hairline, Offset(0f, h * hourPx), Offset(size.width, h * hourPx), 1f)
                    }
                    for (d in 1 until days.size) {
                        drawLine(Palette.Hairline, Offset(d * colPx, 0f), Offset(d * colPx, size.height), 1f)
                    }
                }
                days.forEachIndexed { dayIndex, day ->
                    layOut(blocks[day].orEmpty()).forEach { placed ->
                        BlockView(
                            placed = placed,
                            dayIndex = dayIndex,
                            dayCount = days.size,
                            colWidth = colWidth,
                            colPx = colPx,
                            hourPx = hourPx,
                            days = days,
                            onMoveTask = onMoveTask,
                            onOpenTask = onOpenTask,
                            onOpenEvent = onOpenEvent,
                            onOpenClass = onOpenClass,
                        )
                    }
                    if (day == today) {
                        val y = HourHeight * ((now.hour * 60 + now.minute) / 60f)
                        Box(
                            Modifier
                                .offset(x = colWidth * dayIndex, y = y - 1.dp)
                                .width(colWidth)
                                .height(2.dp)
                                .background(Palette.PriorityHigh),
                        )
                        Box(
                            Modifier
                                .offset(x = colWidth * dayIndex - 4.dp, y = y - 5.dp)
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Palette.PriorityHigh),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
private fun BlockView(
    placed: Placed,
    dayIndex: Int,
    dayCount: Int,
    colWidth: Dp,
    colPx: Float,
    hourPx: Float,
    days: List<LocalDate>,
    onMoveTask: (TaskItem, LocalDate, LocalTime) -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    onOpenEvent: (PhoneEvent) -> Unit,
    onOpenClass: (SchoolClass) -> Unit,
) {
    val block = placed.block
    val haptics = LocalHapticFeedback.current
    val laneWidth = colWidth / placed.lanes
    val x = colWidth * dayIndex + laneWidth * placed.lane
    val y = HourHeight * (block.start / 60f)
    val height = (HourHeight * ((block.end - block.start).coerceAtLeast(20) / 60f)) - 2.dp
    var drag by remember(block.key) { mutableStateOf(Offset.Zero) }
    var dragging by remember(block.key) { mutableStateOf(false) }
    val move by rememberUpdatedState(onMoveTask)
    val task = (block as? CalBlock.OfTask)?.task
    val compact = dayCount > 1

    val previewStart = if (dragging) snapTo15(block.start + (drag.y / hourPx * 60).roundToInt()) else block.start
    val timeText = LocalTime.of(previewStart / 60, previewStart % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    val (fill, border) = when (block) {
        is CalBlock.OfTask -> Palette.Lavender.copy(alpha = if (block.task.done) 0.10f else 0.24f) to Palette.Lavender.copy(alpha = 0.55f)
        is CalBlock.OfClass -> block.color.copy(alpha = 0.20f) to block.color.copy(alpha = 0.0f)
        is CalBlock.OfEvent -> block.color.copy(alpha = 0.10f) to block.color.copy(alpha = 0.60f)
    }

    Box(
        modifier = Modifier
            .offset { IntOffset((x.roundToPx() + drag.x).roundToInt(), (y.roundToPx() + drag.y).roundToInt()) }
            .width(laneWidth - 2.dp)
            .height(height)
            .graphicsLayer { if (dragging) { scaleX = 1.03f; scaleY = 1.03f } }
            .then(if (dragging) Modifier.shadow(12.dp, RoundedCornerShape(8.dp)) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(if (dragging) Palette.SurfaceHigh else Palette.Night)
            .background(fill)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable {
                when (block) {
                    is CalBlock.OfTask -> onOpenTask(block.task)
                    is CalBlock.OfClass -> onOpenClass(block.cls)
                    is CalBlock.OfEvent -> onOpenEvent(block.event)
                }
            }
            .then(
                if (task != null && !task.done) {
                    Modifier.pointerInput(block.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                val minutes = snapTo15(block.start + (drag.y / hourPx * 60).roundToInt())
                                val newDay = (dayIndex + (drag.x / colPx).roundToInt()).coerceIn(0, days.lastIndex)
                                dragging = false
                                drag = Offset.Zero
                                move(task, days[newDay], LocalTime.of(minutes / 60, minutes % 60))
                            },
                            onDragCancel = {
                                dragging = false
                                drag = Offset.Zero
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                drag += amount
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = buildString {
                    append(block.title)
                    append(", ")
                    append(timeText)
                    if (task != null) append(". Long-press and drag to move.")
                }
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Column {
            Text(
                block.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (compact) 11.sp else 13.sp,
                    textDecoration = if (task?.done == true) TextDecoration.LineThrough else null,
                ),
                color = if (task?.done == true) Palette.TextTertiary else Palette.TextPrimary,
                maxLines = if (height < 36.dp) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (height >= 36.dp || dragging) {
                Text(
                    if (dragging) timeText else listOfNotNull(if (compact) null else timeText, block.subtitle).joinToString(", ").ifEmpty { timeText },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (dragging) Palette.Lavender else Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
