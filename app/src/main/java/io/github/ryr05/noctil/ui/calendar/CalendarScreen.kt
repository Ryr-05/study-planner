package io.github.ryr05.noctil.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.components.RoundIconButton
import io.github.ryr05.noctil.ui.tasks.TaskActions
import io.github.ryr05.noctil.ui.tasks.openTaskOrder
import io.github.ryr05.noctil.ui.components.ScreenTitle
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.TaskRow
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.topContentPadding
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("MMMM")
private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val a11yDayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")

@Composable
fun CalendarScreen(
    snapshot: Snapshot,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    actions: TaskActions,
) {
    var month by rememberSaveable { mutableStateOf(YearMonth.from(selected)) }
    val busyDays = remember(snapshot) { snapshot.tasks.filter { !it.done && it.date != null }.mapNotNull { it.date }.toSet() }
    val dayTasks = snapshot.tasks
        .filter { it.date == selected && (it.parentId == null || !it.done) }
        .sortedWith(compareBy<TaskItem>({ it.done }).then(openTaskOrder))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding(),
            bottom = bottomContentPadding(),
        ),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(
                    title = month.format(monthFormat),
                    overline = month.year.toString(),
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(Icons.Rounded.ChevronLeft, "Previous month") { month = month.minusMonths(1) }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.ChevronRight, "Next month") { month = month.plusMonths(1) }
            }
        }
        item(key = "grid") {
            MonthGrid(
                month = month,
                today = today,
                selected = selected,
                busyDays = busyDays,
                onSelect = onSelect,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        item(key = "day-title") {
            SectionLabel(
                text = if (selected == today) "Today" else selected.format(dayTitleFormat),
            )
        }
        if (dayTasks.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Nothing planned. Tap + to add a task on this day.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            items(count = dayTasks.size, key = { "cal-${dayTasks[it].id}" }) { index ->
                TaskRow(
                    task = dayTasks[index],
                    index = index,
                    count = dayTasks.size,
                    today = today,
                    showDate = false,
                    onToggle = actions.onToggle,
                    onOpen = actions.onOpen,
                    onMoveToTomorrow = actions.onMoveToTomorrow,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    busyDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekDays = remember(firstDayOfWeek) { (0L until 7L).map { firstDayOfWeek.plus(it) } }
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = leading + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach { day: DayOfWeek ->
                Text(
                    text = day.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - leading + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            hasTasks = date in busyDays,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasTasks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = when {
        isSelected -> Palette.OnLavender
        isToday -> Palette.Lavender
        else -> Palette.TextPrimary
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (isSelected) Palette.Lavender else Color.Transparent)
            .then(
                if (isToday && !isSelected) Modifier.border(1.5.dp, Palette.Lavender.copy(alpha = 0.6f), CircleShape)
                else Modifier,
            )
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(date.format(a11yDayFormat))
                    if (isToday) append(", today")
                    if (hasTasks) append(", has tasks")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = textColor,
        )
        if (hasTasks) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Palette.OnLavender else Palette.Lavender),
            )
        }
    }
}
