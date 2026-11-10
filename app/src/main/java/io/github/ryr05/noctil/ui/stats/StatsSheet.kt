package io.github.ryr05.noctil.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.ui.components.GlassSheet
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.tasks.completedOn
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

enum class StatRange(val label: String) { Week("Week"), Month("Month"), Year("Year") }

/** One bar: [value] in the chart's own unit, [label] under the bar (blank for most), [title] when tapped. */
data class Bar(val value: Float, val label: String, val title: String)

private val dayTitle = DateTimeFormatter.ofPattern("EEE, MMM d")
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")

/** Daily numbers for the three charts. Pure, so the same code feeds every range. */
private class Daily(snap: Snapshot) {
    private val zone = ZoneId.systemDefault()
    val tasks: Map<LocalDate, Int> = snap.tasks.mapNotNull { it.completedOn() }.groupingBy { it }.eachCount()
    val focus: Map<LocalDate, Int> = snap.focus.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .mapValues { (_, s) -> s.sumOf { it.minutes } }
    private val habits = snap.habits

    /** (done, due) habit check-ins for a day. Today only counts finished habits as due. */
    fun habitDay(day: LocalDate, today: LocalDate): Pair<Int, Int> {
        val due = habits.filter { it.isDue(day) && (day != today || it.isDone(day)) }
        return due.count { it.isDone(day) } to due.size
    }
}

@Composable
fun StatsSheet(visible: Boolean, snapshot: Snapshot, today: LocalDate, onDismiss: () -> Unit) {
    GlassSheet(visible = visible, title = "Statistics", onDismiss = onDismiss) {
        var range by rememberSaveable { mutableStateOf(StatRange.Week) }
        val daily = remember(snapshot) { Daily(snapshot) }
        val days: List<LocalDate> = when (range) {
            StatRange.Week -> (6 downTo 0).map { today.minusDays(it.toLong()) }
            StatRange.Month -> (29 downTo 0).map { today.minusDays(it.toLong()) }
            StatRange.Year -> emptyList()
        }
        val months = (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
        val locale = Locale.getDefault()

        fun dayLabel(d: LocalDate, i: Int): String = when (range) {
            StatRange.Week -> d.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale)
            else -> if (i % 7 == 2 || d == today) d.dayOfMonth.toString() else ""
        }
        fun monthDays(m: YearMonth) = (1..m.lengthOfMonth()).map { m.atDay(it) }.filter { !it.isAfter(today) }

        val taskBars = if (range == StatRange.Year) {
            months.map { m -> Bar(monthDays(m).sumOf { daily.tasks[it] ?: 0 }.toFloat(), m.month.getDisplayName(JavaTextStyle.NARROW, locale), m.format(monthTitle)) }
        } else days.mapIndexed { i, d -> Bar((daily.tasks[d] ?: 0).toFloat(), dayLabel(d, i), d.format(dayTitle)) }
        val focusBars = if (range == StatRange.Year) {
            months.map { m -> Bar(monthDays(m).sumOf { daily.focus[it] ?: 0 }.toFloat(), m.month.getDisplayName(JavaTextStyle.NARROW, locale), m.format(monthTitle)) }
        } else days.mapIndexed { i, d -> Bar((daily.focus[d] ?: 0).toFloat(), dayLabel(d, i), d.format(dayTitle)) }
        val habitBars = if (range == StatRange.Year) {
            months.map { m ->
                val (done, due) = monthDays(m).map { daily.habitDay(it, today) }.fold(0 to 0) { a, b -> (a.first + b.first) to (a.second + b.second) }
                Bar(if (due == 0) 0f else done * 100f / due, m.month.getDisplayName(JavaTextStyle.NARROW, locale), m.format(monthTitle))
            }
        } else days.mapIndexed { i, d ->
            val (done, due) = daily.habitDay(d, today)
            Bar(if (due == 0) 0f else done * 100f / due, dayLabel(d, i), d.format(dayTitle))
        }

        val totalTasks = taskBars.sumOf { it.value.toInt() }
        val totalFocus = focusBars.sumOf { it.value.toInt() }
        val habitTotals = (if (range == StatRange.Year) months.flatMap { monthDays(it) } else days)
            .map { daily.habitDay(it, today) }.fold(0 to 0) { a, b -> (a.first + b.first) to (a.second + b.second) }
        val consistency = if (habitTotals.second == 0) null else habitTotals.first * 100 / habitTotals.second

        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRange.entries.forEach { r -> Pill(r.label, selected = range == r) { range = r } }
            }
            ChartCard(
                title = "Tasks finished",
                headline = "$totalTasks",
                unit = if (totalTasks == 1) "task" else "tasks",
                bars = taskBars,
                format = { v -> "${v.toInt()} ${if (v.toInt() == 1) "task" else "tasks"}" },
                key = range,
            )
            ChartCard(
                title = "Focus time",
                headline = hours(totalFocus),
                unit = "",
                bars = focusBars,
                format = { v -> hours(v.toInt()) },
                key = range,
            )
            ChartCard(
                title = "Habit consistency",
                headline = consistency?.let { "$it%" } ?: "-",
                unit = if (consistency == null) "no habits due yet" else "of due habits done",
                bars = habitBars,
                format = { v -> "${v.toInt()}% done" },
                key = range,
                max = 100f,
            )
        }
    }
}

private fun hours(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** A single-series bar chart. Tap a bar to see its value; the latest bar is shown first. */
@Composable
private fun ChartCard(
    title: String,
    headline: String,
    unit: String,
    bars: List<Bar>,
    format: (Float) -> String,
    key: Any,
    max: Float? = null,
) {
    var selected by remember(key) { mutableStateOf(bars.lastIndex) }
    val pick = bars.getOrNull(selected) ?: bars.lastOrNull()
    val top = max ?: (bars.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(1f)
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface.copy(alpha = 0.75f))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(headline, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextPrimary)
            if (unit.isNotEmpty()) {
                Text(" $unit", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Text(
            pick?.let { "${it.title}: ${format(it.value)}" } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Lavender,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(bars.size, key) {
                    detectTapGestures { pos ->
                        if (bars.isNotEmpty()) selected = (pos.x / (size.width / bars.size)).toInt().coerceIn(0, bars.lastIndex)
                    }
                }
                .semantics { contentDescription = "$title chart. ${pick?.title}: ${pick?.let { format(it.value) }}. Tap a bar for its value." },
        ) {
            drawBars(bars, top, selected)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEach { b ->
                Text(
                    b.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Palette.TextTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun DrawScope.drawBars(bars: List<Bar>, top: Float, selected: Int) {
    if (bars.isEmpty()) return
    val gap = 2.dp.toPx()
    val slot = size.width / bars.size
    val barWidth = (slot - gap).coerceAtLeast(1f)
    val radius = minOf(4.dp.toPx(), barWidth / 2)
    // Baseline.
    drawLine(Palette.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    bars.forEachIndexed { i, b ->
        val h = (b.value / top).coerceIn(0f, 1f) * size.height
        if (h <= 0f) return@forEachIndexed
        val left = i * slot + gap / 2
        val color = if (i == selected) Palette.Lavender else Palette.Lavender.copy(alpha = 0.55f)
        // Rounded only at the top (the data end); square on the baseline.
        val path = Path().apply {
            val t = size.height - h
            val r = minOf(radius, h)
            moveTo(left, size.height)
            lineTo(left, t + r)
            quadraticTo(left, t, left + r, t)
            lineTo(left + barWidth - r, t)
            quadraticTo(left + barWidth, t, left + barWidth, t + r)
            lineTo(left + barWidth, size.height)
            close()
        }
        drawPath(path, color)
    }
}
