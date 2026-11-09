package io.github.ryr05.noctil.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.ryr05.noctil.data.TaskStore
import io.github.ryr05.noctil.ui.calendar.CalendarScreen
import io.github.ryr05.noctil.ui.components.AuroraBackground
import io.github.ryr05.noctil.ui.focus.FocusScreen
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.glass.LocalHazeState
import io.github.ryr05.noctil.ui.me.MeScreen
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.ui.theme.rememberReduceMotion
import io.github.ryr05.noctil.ui.today.TodayScreen
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Rounded.TaskAlt),
    Calendar("Calendar", Icons.Rounded.CalendarMonth),
    Focus("Focus", Icons.Rounded.Timer),
    Me("Me", Icons.Rounded.Person),
}

@Composable
fun NoctilApp() {
    val today = remember { LocalDate.now() }
    val store = remember { TaskStore(today) }
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()

    BackHandler(enabled = quickAddOpen || tab != Tab.Today) {
        if (quickAddOpen) quickAddOpen = false else tab = Tab.Today
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().background(Palette.Night)) {
            // Everything inside this box is what the glass controls blur.
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                AuroraBackground()
                Crossfade(targetState = tab, animationSpec = tween(200), label = "tabs") { current ->
                    when (current) {
                        Tab.Today -> TodayScreen(store, today)
                        Tab.Calendar -> CalendarScreen(store, today)
                        Tab.Focus -> FocusScreen()
                        Tab.Me -> MeScreen()
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassTabBar(selected = tab, onSelect = { tab = it }, modifier = Modifier.weight(1f))
                AddButton(onClick = { quickAddOpen = true })
            }

            QuickAddOverlay(
                visible = quickAddOpen,
                onDismiss = { quickAddOpen = false },
                onAdd = { title ->
                    store.add(title, today)
                    quickAddOpen = false
                    tab = Tab.Today
                },
            )
        }
    }
}

/**
 * Floating glass tab bar. The highlight moves like a drop of liquid: its leading edge
 * springs ahead and the trailing edge catches up, so it stretches and settles.
 */
@Composable
private fun GlassTabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    GlassSurface(modifier = modifier.height(64.dp), shape = CircleShape) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val itemWidth = maxWidth / Tab.entries.size
            val index = selected.ordinal
            val lastIndex = remember { intArrayOf(index) }
            val movingRight = remember(index) {
                val right = index >= lastIndex[0]
                lastIndex[0] = index
                right
            }
            val fast: AnimationSpec<Dp> =
                if (reduceMotion) snap() else spring(dampingRatio = 0.75f, stiffness = 600f)
            val slow: AnimationSpec<Dp> =
                if (reduceMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 210f)
            val left by animateDpAsState(itemWidth * index, if (movingRight) slow else fast, label = "left")
            val right by animateDpAsState(itemWidth * (index + 1), if (movingRight) fast else slow, label = "right")

            Box(
                modifier = Modifier
                    .offset(x = left)
                    .width((right - left).coerceAtLeast(0.dp))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Palette.Lavender.copy(alpha = 0.16f))
                    .border(1.dp, Palette.Lavender.copy(alpha = 0.28f), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                Tab.entries.forEach { item ->
                    TabItem(
                        tab = item,
                        selected = item == selected,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val iconColor by animateColorAsState(
        if (selected) Palette.Lavender else Palette.TextSecondary,
        label = "tabIcon",
    )
    val labelColor by animateColorAsState(
        if (selected) Palette.TextPrimary else Palette.TextSecondary,
        label = "tabLabel",
    )
    Column(
        modifier = modifier
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "addScale",
    )
    GlassSurface(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender)))
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { contentDescription = "Add task" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun QuickAddOverlay(visible: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Shadow.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            QuickAddPanel(onAdd = onAdd)
        }
    }
}

@Composable
private fun QuickAddPanel(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val canAdd = text.isNotBlank()
    val submit = { if (canAdd) onAdd(text) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("New task", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                    cursorBrush = SolidColor(Palette.Lavender),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .semantics { contentDescription = "Task title" },
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    "Read chapter 5",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Palette.TextSecondary,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canAdd) Palette.Lavender else Palette.SurfaceHigh)
                        .clickable(enabled = canAdd, role = Role.Button, onClick = { submit() })
                        .semantics { contentDescription = "Add" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = if (canAdd) Palette.OnLavender else Palette.TextTertiary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Adds to today. Typing dates like \"fri 5pm\" comes in the next update.",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
            )
        }
    }
}
