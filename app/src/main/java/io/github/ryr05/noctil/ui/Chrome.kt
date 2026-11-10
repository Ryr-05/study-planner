package io.github.ryr05.noctil.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.ui.theme.rememberReduceMotion

enum class Tab(val label: String, val icon: ImageVector) {
    Tasks("Tasks", Icons.Rounded.TaskAlt),
    Calendar("Calendar", Icons.Rounded.CalendarMonth),
    Habits("Habits", Icons.Rounded.LocalFireDepartment),
    Focus("Focus", Icons.Rounded.Timer),
    Me("Me", Icons.Rounded.Person),
}

/**
 * Floating glass tab bar. The highlight moves like a drop of liquid: its leading edge
 * springs ahead and the trailing edge catches up, so it stretches and settles.
 */
@Composable
fun GlassTabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
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
fun AddButton(onClick: () -> Unit) {
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
