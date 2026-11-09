package io.github.ryr05.noctil.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.ui.components.TaskRow
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.glass.LocalHazeState
import io.github.ryr05.noctil.ui.tasks.SmallIconButton
import io.github.ryr05.noctil.ui.tasks.TaskActions
import io.github.ryr05.noctil.ui.tasks.searchTasks
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate

/** Searches titles, notes, checklist items, tags and list names, including finished tasks. */
@Composable
fun SearchOverlay(
    visible: Boolean,
    snapshot: Snapshot,
    today: LocalDate,
    actions: TaskActions,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 12 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 12 },
    ) {
        var query by rememberSaveable { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val results = remember(query, snapshot) { searchTasks(snapshot, query) }
        // The search bar blurs the results scrolling under it, not the screen behind search.
        val haze = rememberHazeState()

        Box(Modifier.fillMaxSize().background(Palette.Night)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(haze).imePadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 112.dp, bottom = bottomContentPadding()),
            ) {
                when {
                    query.isBlank() -> item(key = "hint") {
                        Hint("Search your tasks", "Titles, notes, checklist items, tags and lists are all searched.")
                    }
                    results.isEmpty() -> item(key = "none") {
                        Hint("No matches", "Nothing matches \"${query.trim()}\".")
                    }
                    else -> itemsIndexed(results, key = { _, t -> "s-${t.id}" }) { index, task ->
                        TaskRow(
                            task = task,
                            index = index,
                            count = results.size,
                            today = today,
                            showDate = true,
                            onToggle = actions.onToggle,
                            onOpen = actions.onOpen,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalHazeState provides haze) {
            GlassSurface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(60.dp),
                shape = CircleShape,
            ) {
                Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                        cursorBrush = SolidColor(Palette.Lavender),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Search tasks" },
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
                                inner()
                            }
                        },
                    )
                    SmallIconButton(Icons.Rounded.Close, "Close search", onClick = onDismiss)
                }
            }
            }
        }
    }
}

@Composable
private fun Hint(title: String, body: String) {
    Column(Modifier.padding(top = 24.dp, start = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    }
}
