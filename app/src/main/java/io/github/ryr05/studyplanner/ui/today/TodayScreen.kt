package io.github.ryr05.studyplanner.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ryr05.studyplanner.data.Exam
import io.github.ryr05.studyplanner.data.TaskStore
import io.github.ryr05.studyplanner.ui.components.ScreenTitle
import io.github.ryr05.studyplanner.ui.components.bottomContentPadding
import io.github.ryr05.studyplanner.ui.components.taskSection
import io.github.ryr05.studyplanner.ui.components.topContentPadding
import io.github.ryr05.studyplanner.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val headerDate = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
fun TodayScreen(store: TaskStore, today: LocalDate) {
    val tasks = store.tasks
    val overdue = tasks.filter { !it.done && it.date < today }.sortedBy { it.date }
    val dueToday = tasks.filter { !it.done && it.date == today }.sortedBy { it.time }
    val upcoming = tasks.filter { !it.done && it.date > today }.sortedWith(compareBy({ it.date }, { it.time }))
    val completed = tasks.filter { it.done }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding(),
            bottom = bottomContentPadding(),
        ),
    ) {
        item(key = "title") {
            Column {
                ScreenTitle(title = "Today", overline = today.format(headerDate))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary(dueToday.size, overdue.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                )
            }
        }
        item(key = "exam") {
            ExamCard(store.nextExam, today, Modifier.padding(top = 20.dp))
        }
        item(key = "sample-note") {
            SampleNote(Modifier.padding(top = 12.dp))
        }

        taskSection("overdue", "Overdue", overdue, today, showDate = true, onToggle = store::toggle, titleColor = Palette.PriorityHigh)
        taskSection("today", "Today", dueToday, today, showDate = false, onToggle = store::toggle)
        taskSection("upcoming", "Coming up", upcoming, today, showDate = true, onToggle = store::toggle)
        taskSection("done", "Completed", completed, today, showDate = true, onToggle = store::toggle)

        if (overdue.isEmpty() && dueToday.isEmpty()) {
            item(key = "all-clear") {
                Text(
                    text = "Nothing left for today. Tap + to add a task.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 28.dp, start = 4.dp),
                )
            }
        }
    }
}

private fun summary(today: Int, overdue: Int): String {
    val left = when (today) {
        0 -> "All done for today"
        1 -> "1 task left today"
        else -> "$today tasks left today"
    }
    return if (overdue > 0) "$left, $overdue overdue" else left
}

@Composable
private fun ExamCard(exam: Exam, today: LocalDate, modifier: Modifier = Modifier) {
    val days = ChronoUnit.DAYS.between(today, exam.date)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(exam.subject.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.School, contentDescription = null, tint = exam.subject.color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Next exam", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
            Text(exam.name, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            Text(exam.date.format(shortDate), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = days.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Palette.Lavender,
            )
            Text(
                text = if (days == 1L) "day left" else "days left",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
            )
        }
    }
}

@Composable
private fun SampleNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Lavender.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(18.dp))
        Text(
            text = "These are sample tasks so you can try things out. Saving your own tasks comes in the next update.",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
        )
    }
}
