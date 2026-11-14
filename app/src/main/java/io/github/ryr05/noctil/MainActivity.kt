package io.github.ryr05.noctil

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.ryr05.noctil.reminders.EXTRA_OPEN_TASK
import io.github.ryr05.noctil.ui.LaunchRequest
import io.github.ryr05.noctil.ui.NoctilApp
import io.github.ryr05.noctil.ui.onboarding.AgeCheck
import io.github.ryr05.noctil.ui.onboarding.AgeGate
import io.github.ryr05.noctil.ui.theme.StudyTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** Where to open the app when it was launched from a widget, notification, tile or shortcut. */
    private val request = mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) read(intent)
        setContent {
            StudyTheme {
                // College students 18+ only: asked once, before anything else.
                var adult by remember { mutableStateOf(AgeCheck.status(this) == AgeCheck.Status.Adult) }
                if (adult) {
                    NoctilApp(
                        launchRequest = request.value,
                        onLaunchHandled = { request.value = null },
                    )
                } else {
                    AgeGate(onAdult = { adult = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent?) {
        intent ?: return
        val taskId = intent.getLongExtra(EXTRA_OPEN_TASK, -1L)
        request.value = when {
            taskId > 0 -> LaunchRequest.OpenTask(taskId)
            intent.action == Launch.ACTION_QUICK_ADD -> LaunchRequest.QuickAdd
            intent.action == Launch.ACTION_FOCUS -> LaunchRequest.Focus(
                startNext = intent.getBooleanExtra(Launch.EXTRA_START_FOCUS, false),
                taskId = intent.getLongExtra(Launch.EXTRA_FOCUS_TASK, -1L).takeIf { it > 0 },
            )
            intent.action == Launch.ACTION_TODAY -> LaunchRequest.Today
            intent.action == Launch.ACTION_HABITS -> LaunchRequest.Habits
            intent.action == Launch.ACTION_OPEN_DATE && intent.hasExtra(Launch.EXTRA_OPEN_DATE) ->
                LaunchRequest.OpenDate(LocalDate.ofEpochDay(intent.getLongExtra(Launch.EXTRA_OPEN_DATE, 0L)))
            else -> request.value
        }
    }
}
