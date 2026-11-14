package io.github.ryr05.noctil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.focus.FocusEngine
import io.github.ryr05.noctil.focus.FocusKind
import io.github.ryr05.noctil.plan.TodayPlan
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate

/**
 * Today's plan, wherever you are: in Focus and on the Noctil AI tab. When a planned focus block
 * ends it asks "Done, or more time?" right here, and the rest of the plan shifts to match.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayPlanCard(modifier: Modifier = Modifier, onStarted: () -> Unit = {}) {
    val context = LocalContext.current
    val plan by TodayPlan.state.collectAsState()
    val s = plan?.takeIf { it.day == LocalDate.now() } ?: return
    val next = s.next
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Lavender.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's plan", style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                "Clear",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { TodayPlan.clear(context) }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        val checkIn = s.checkInTaskId?.let { id -> s.blocks.firstOrNull { it.taskId == id } }
        if (checkIn != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Time's up for ${checkIn.title.substringBefore(" (")}. Done, or do you need more time?",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Done", selected = true) { FocusEngine.checkIn(context, TodayPlan.Answer.Done) }
                Pill("+10 min", selected = false) { FocusEngine.checkIn(context, TodayPlan.Answer.More(10)); onStarted() }
                Pill("+25 min", selected = false) { FocusEngine.checkIn(context, TodayPlan.Answer.More(25)); onStarted() }
                Pill("Not today", selected = false) { FocusEngine.checkIn(context, TodayPlan.Answer.NotToday) }
            }
        }
        if (s.movedLater.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Moved off today's plan: " + s.movedLater.joinToString(", ") + ". It's still on your list.", style = MaterialTheme.typography.labelMedium, color = Palette.PriorityMedium)
        }
        Spacer(Modifier.height(8.dp))
        s.blocks.forEach { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.start.format(timeFormat), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, modifier = Modifier.width(72.dp))
                Text(
                    b.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (b.done) Palette.TextTertiary else Palette.TextPrimary,
                    textDecoration = if (b.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                when {
                    b.done -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = Palette.Lavender, modifier = Modifier.size(18.dp))
                    b == next && checkIn == null -> Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Palette.Lavender.copy(alpha = 0.18f))
                            .clickable(role = Role.Button, onClickLabel = "Start focus on ${b.title}") {
                                FocusEngine.select(context, FocusKind.Focus)
                                FocusEngine.setMinutes(context, b.minutes.coerceIn(5, 180))
                                FocusEngine.setTask(context, b.taskId)
                                FocusEngine.start(context)
                                onStarted()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start", style = MaterialTheme.typography.labelMedium, color = Palette.Lavender)
                    }
                }
            }
        }
        if (next == null) Text("All done for today. Nice work.", style = MaterialTheme.typography.labelLarge, color = Palette.Lavender, modifier = Modifier.padding(top = 6.dp))
    }
}
