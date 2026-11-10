package io.github.ryr05.noctil.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ryr05.noctil.data.model.Habit
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Recurrence
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.Stage
import io.github.ryr05.noctil.data.model.Tag
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.model.TaskList
import io.github.ryr05.noctil.ui.ScreenshotChrome
import io.github.ryr05.noctil.ui.calendar.CalMode
import io.github.ryr05.noctil.ui.calendar.CalendarActions
import io.github.ryr05.noctil.ui.calendar.CalendarScreen
import io.github.ryr05.noctil.ui.components.AuroraBackground
import io.github.ryr05.noctil.ui.focus.FocusScreen
import io.github.ryr05.noctil.ui.habits.HabitActions
import io.github.ryr05.noctil.ui.habits.HabitsScreen
import io.github.ryr05.noctil.ui.me.MeScreen
import io.github.ryr05.noctil.ui.quickadd.QuickAddOverlay
import io.github.ryr05.noctil.ui.tasks.BoardActions
import io.github.ryr05.noctil.ui.tasks.TaskActions
import io.github.ryr05.noctil.ui.tasks.TaskLayout
import io.github.ryr05.noctil.ui.tasks.TaskView
import io.github.ryr05.noctil.ui.tasks.TasksScreen
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.ui.theme.StudyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Renders the main screens with sample data at normal text size and at 2x (the largest
 * Android text setting), so layout problems with big text can be seen and fixed.
 * Images land in app/build/outputs/roborazzi and are uploaded by CI.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class ScreenshotTest(private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "text{0}")
        fun scales() = listOf(arrayOf<Any>(1.0f), arrayOf<Any>(2.0f))
    }

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 11)
    private val noTask = TaskActions({}, {}, {})

    private fun shot(name: String, withChrome: Boolean = true, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                StudyTheme {
                    Box(Modifier.fillMaxSize().background(Palette.Night)) {
                        AuroraBackground()
                        content()
                        if (withChrome) Box(Modifier.align(Alignment.BottomCenter)) { ScreenshotChrome() }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_text${fontScale}.png")
    }

    @Test fun todayList() = shot("01_today") { TasksScreen(TaskView.Today, sample(), today, noTask, {}, {}) }

    @Test fun todayEmpty() = shot("02_today_empty") { TasksScreen(TaskView.Today, Snapshot.Empty.copy(loaded = true), today, noTask, {}, {}) }

    @Test fun board() = shot("03_board") {
        TasksScreen(TaskView.All, sample(), today, noTask, {}, {}, layout = TaskLayout.Board, boardActions = BoardActions({ _, _, _ -> }, {}, {}, {}))
    }

    @Test fun matrix() = shot("04_matrix") { TasksScreen(TaskView.All, sample(), today, noTask, {}, {}, layout = TaskLayout.Matrix) }

    @Test fun timeline() = shot("05_timeline") { TasksScreen(TaskView.All, sample(), today, noTask, {}, {}, layout = TaskLayout.Timeline) }

    @Test fun calendarMonth() = shot("06_calendar_month") { CalendarScreen(sample(), today, today, CalMode.Month, noTask, calendarActions()) }

    @Test fun calendarWeek() = shot("07_calendar_week") { CalendarScreen(sample(), today, today, CalMode.Week, noTask, calendarActions()) }

    @Test fun habits() = shot("08_habits") { HabitsScreen(sample(), today, HabitActions({ _, _ -> }, {}, {}, { _, _ -> })) }

    @Test fun focus() = shot("09_focus") { FocusScreen(sample(), today) }

    @Test fun me() = shot("10_me") { MeScreen(false, {}, {}) }

    @Test fun quickAdd() = shot("11_quick_add", withChrome = false) {
        QuickAddOverlay(true, today, null, sample(), today, {}, { _, _ -> })
    }

    private fun calendarActions() = CalendarActions({}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {})

    private fun sample(): Snapshot {
        val school = TaskList(1, "Chemistry", Color(0xFF7FD6B5), null)
        val calc = TaskList(2, "Calculus", Color(0xFF8DB8FF), null)
        val exam = Tag(1, "exam", Color(0xFFFF8FA3))
        fun t(
            id: Long, title: String, date: LocalDate?, time: LocalTime? = null, p: Priority = Priority.None,
            list: TaskList? = null, done: Boolean = false, tags: List<Tag> = emptyList(), start: LocalDate? = null,
            stage: Long? = null, rec: Recurrence? = null, sub: Int = 0, check: Int = 0,
        ) = TaskItem(
            id = id, parentId = null, title = title, notes = "", date = date, time = time, priority = p, list = list,
            tags = tags, recurrence = rec, reminder = 0, done = done, completedAt = if (done) 1L else null, sortOrder = id,
            createdAt = 0, subtaskTotal = sub, subtaskDone = sub / 2, checklistTotal = check, checklistDone = check / 2,
            stageId = stage, startDate = start,
        )
        val tasks = listOf(
            t(1, "Lab report: titration results and error analysis", today.minusDays(1), LocalTime.of(23, 59), Priority.High, school, tags = listOf(exam)),
            t(2, "Problem set 7, questions 1 to 12", today, LocalTime.of(17, 0), Priority.High, calc, sub = 4, stage = 10),
            t(3, "Read chapter 4 of The Great Gatsby", today, LocalTime.of(21, 0), Priority.Medium, check = 6),
            t(4, "Email Prof. Okafor about office hours", today, rec = Recurrence(Recurrence.Freq.Weekly, 1, setOf(DayOfWeek.MONDAY))),
            t(5, "Chemistry midterm", today.plusDays(6), LocalTime.of(9, 0), Priority.High, school, tags = listOf(exam)),
            t(6, "Group project slides", today.plusDays(9), p = Priority.Medium, list = calc, start = today.minusDays(2)),
            t(7, "Return library books", null),
            t(8, "Flashcards: Cold War timeline", today.plusDays(1), LocalTime.of(10, 0), done = true),
        )
        val habits = listOf(
            Habit(1, "Read 20 minutes", Color(0xFF7FD6B5), DayOfWeek.entries.toSet(), 1, null, today.minusDays(30),
                (1L..12L).associate { today.minusDays(it).toEpochDay() to 1 }),
            Habit(2, "Drink water", Color(0xFF8DB8FF), DayOfWeek.entries.toSet(), 8, LocalTime.of(20, 0), today.minusDays(10),
                mapOf(today.toEpochDay() to 5, today.minusDays(1).toEpochDay() to 8)),
        )
        val classes = listOf(
            SchoolClass(1, "Calculus II", "Hall B 204", Color(0xFF8DB8FF), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(9, 0), LocalTime.of(10, 15), 2),
        )
        return Snapshot(
            tasks = tasks, lists = listOf(school, calc), folders = emptyList(), tags = listOf(exam), checklist = emptyMap(),
            loaded = true, classes = classes, habits = habits, stages = listOf(Stage(10, "Doing", 0)),
        )
    }
}
