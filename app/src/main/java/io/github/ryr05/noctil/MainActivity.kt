package io.github.ryr05.noctil

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.ryr05.noctil.reminders.EXTRA_OPEN_TASK
import io.github.ryr05.noctil.ui.NoctilApp
import io.github.ryr05.noctil.ui.theme.StudyTheme

class MainActivity : ComponentActivity() {
    /** Set when a reminder notification asks to open a specific task. */
    private val openTask = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readOpenTask(intent)
        setContent {
            StudyTheme {
                NoctilApp(
                    openTaskRequest = openTask.value,
                    onOpenTaskHandled = { openTask.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpenTask(intent)
    }

    private fun readOpenTask(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_TASK, -1L) ?: -1L
        if (id > 0) openTask.value = id
    }
}
