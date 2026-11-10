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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.ui.components.RoundIconButton
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.ui.theme.rememberReduceMotion

/** Me isn't in the bar; it opens from the profile button at the top of each screen. */
enum class Tab(val label: String, val icon: ImageVector) {
    Tasks("Tasks", Icons.Rounded.TaskAlt),
    Calendar("Calendar", Icons.Rounded.CalendarMonth),
    Habits("Habits", Icons.Rounded.LocalFireDepartment),
    Focus("Focus", Icons.Rounded.Timer),
    Me("Me", Icons.Rounded.Person),
}

/** Bar slots, left to right. Slot 2 is the + button. */
private val slots = listOf(Tab.Tasks, Tab.Calendar, null, Tab.Habits, Tab.Focus)

/**
 * Keeps small chrome (tab labels, chips) readable at big text sizes without breaking the layout:
 * text still grows, up to [max] times its normal size.
 */
@Composable
fun CappedText(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(max)), content = content)
}

/**
 * Floating glass tab bar with a raised + in the middle. The highlight moves like a drop of liquid:
 * its leading edge springs ahead and the trailing edge catches up.
 */
@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    CappedText {
        Box(modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
            GlassSurface(modifier = Modifier.fillMaxWidth().height(68.dp), shape = CircleShape) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
                    val slotWidth = maxWidth / slots.size
                    val index = slots.indexOf(selected).takeIf { it >= 0 }
                    Indicator(index, slotWidth)
                    Row(Modifier.fillMaxSize()) {
                        slots.forEach { tab ->
                            if (tab == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                TabItem(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
            AddButton(onAdd, Modifier.offset(y = (-18).dp))
        }
    }
}

@Composable
private fun Indicator(index: Int?, slotWidth: Dp) {
    val reduceMotion = rememberReduceMotion()
    val target = index ?: 0
    val lastIndex = remember { intArrayOf(target) }
    val movingRight = remember(target) {
        val right = target >= lastIndex[0]
        lastIndex[0] = target
        right
    }
    val fast: AnimationSpec<Dp> = if (reduceMotion) snap() else spring(dampingRatio = 0.75f, stiffness = 600f)
    val slow: AnimationSpec<Dp> = if (reduceMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 210f)
    val left by animateDpAsState(slotWidth * target, if (movingRight) slow else fast, label = "left")
    val right by animateDpAsState(slotWidth * (target + 1), if (movingRight) fast else slow, label = "right")
    val alpha by animateFloatAsState(if (index == null) 0f else 1f, label = "indicatorAlpha")
    Box(
        modifier = Modifier
            .offset(x = left)
            .width((right - left).coerceAtLeast(0.dp))
            .fillMaxHeight()
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Palette.Lavender.copy(alpha = 0.16f))
            .border(1.dp, Palette.Lavender.copy(alpha = 0.28f), CircleShape),
    )
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val iconColor by animateColorAsState(if (selected) Palette.Lavender else Palette.TextSecondary, label = "tabIcon")
    val labelColor by animateColorAsState(if (selected) Palette.TextPrimary else Palette.TextSecondary, label = "tabLabel")
    Column(
        modifier = modifier
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "addScale",
    )
    Box(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(14.dp, CircleShape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .clip(CircleShape)
            .background(Palette.Night)
            .border(4.dp, Palette.Night, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender)))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Add" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(30.dp))
    }
}

/** The round button at the top right of each screen that opens Me (settings, account, stats). */
@Composable
fun ProfileButton(onClick: () -> Unit) {
    RoundIconButton(Icons.Rounded.Person, "Me and settings", onClick = onClick)
}

/** The bottom bar as it sits on screen, for screenshot tests. */
@Composable
fun ScreenshotChrome() {
    BottomBar(Tab.Tasks, {}, {}, Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
}
