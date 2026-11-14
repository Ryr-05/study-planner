package io.github.ryr05.noctil.ui.onboarding

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import io.github.ryr05.noctil.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.ui.components.AuroraBackground
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Noctil is for college students who are 18 or older (also required by the Gemini API terms
 * used for AI planning). The app asks once, neutrally, for the birth month and year: it never
 * hints at the age needed, and an under-18 answer is kept so it can't simply be retried.
 */
object AgeCheck {
    private const val PREFS = "profile"
    private const val KEY_BORN = "born"
    const val MIN_AGE = 18

    enum class Status { Unknown, Adult, Under }

    fun status(context: Context): Status {
        val born = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BORN, null) ?: return Status.Unknown
        val ym = runCatching { YearMonth.parse(born) }.getOrNull() ?: return Status.Unknown
        return if (ageOn(ym, YearMonth.now()) >= MIN_AGE) Status.Adult else Status.Under
    }

    fun save(context: Context, born: YearMonth) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BORN, born.toString()).apply()
    }

    /** Whole years old in [now]. Only the month is known, so the birthday month counts as reached. */
    fun ageOn(born: YearMonth, now: YearMonth): Int =
        now.year - born.year - if (now.monthValue < born.monthValue) 1 else 0
}

@Composable
fun AgeGate(onAdult: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(AgeCheck.status(context)) }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var yearText by rememberSaveable { mutableStateOf("") }
    var showTerms by rememberSaveable { mutableStateOf(false) }
    val thisYear = YearMonth.now().year

    Box(Modifier.fillMaxSize().background(Palette.Night)) {
        AuroraBackground(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MoonMark()
            Spacer(Modifier.height(28.dp))
            if (status == AgeCheck.Status.Under) {
                Text(
                    "Noctil is for college students 18 and older",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Thanks for your interest. Noctil can't be used before you turn 18.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }
            Text("Welcome to Noctil", style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "A calm planner for college students. When were you born?",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Picker(
                    label = month?.let { Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) } ?: "Month",
                    options = (1..12).map { it to Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                    onPick = { month = it },
                    modifier = Modifier.weight(1f),
                )
                YearField(
                    value = yearText,
                    onValue = { text ->
                        yearText = text.filter { it.isDigit() }.take(4)
                        year = yearText.toIntOrNull()?.takeIf { yearText.length == 4 && it in (thisYear - 100)..thisYear }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            val ready = month != null && year != null
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(CircleShape)
                    .background(
                        if (ready) Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender))
                        else Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.SurfaceHigh)),
                    )
                    .clickable(enabled = ready, role = Role.Button) {
                        val born = YearMonth.of(year!!, month!!)
                        AgeCheck.save(context, born)
                        status = AgeCheck.status(context)
                        if (status == AgeCheck.Status.Adult) onAdult()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, color = if (ready) Palette.OnLavender else Palette.TextTertiary)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                buildAnnotatedString {
                    append("By continuing you agree to the ")
                    withStyle(SpanStyle(color = Palette.Lavender)) { append("Terms and privacy") }
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { showTerms = true }
                    .padding(8.dp),
            )
        }
    }
    if (showTerms) TermsDialog(onDismiss = { showTerms = false })
}

/** The app icon: the launcher's own gradient and crater moon. */
@Composable
private fun MoonMark() {
    Box(
        Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF241A42), Color(0xFF120B22)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<Int, String>>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Surface)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.TextSecondary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Palette.SurfaceHigh,
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text, color = Palette.TextPrimary) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

/** Four digits, typed on the number pad: faster than scrolling a long list of years. */
@Composable
private fun YearField(value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text("Year", style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
            cursorBrush = SolidColor(Palette.Lavender),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Birth year" },
        )
    }
}

/** The short, plain-language terms. Also shown from Me > Terms and privacy. */
@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Terms and privacy", color = Palette.TextPrimary) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                TERMS.forEachIndexed { i, (heading, body) ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Text(heading, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

val TERMS: List<Pair<String, String>> = listOf(
    "Who Noctil is for" to
        "Noctil is a planner for college students who are 18 or older. By using it, you confirm that you are 18 or older.",
    "Your data stays yours" to
        "Your tasks, classes, habits and focus sessions are saved on this phone. If you sign in with Google, they are also " +
        "synced to your own account so your other devices can see them. You can back up, export or delete everything from Me.",
    "AI planning is optional" to
        "When you use AI, Noctil sends Google's Gemini service what you type to it, plus the titles, due dates, times, start " +
        "times, priority and length of the tasks you pick and of your open tasks for the next two weeks. Notes, checklists " +
        "and places are never sent. The AI can add tasks and move start times when you ask; it changes a deadline only when " +
        "you ask for that, and only after you confirm. It can't delete tasks. Gemini is used through Google's free service, " +
        "so Google may use what is sent to improve its products, and trained reviewers may read it. Please don't put private " +
        "details in tasks or text you send to AI. AI stays off until you turn it on, and you can turn it off anytime. " +
        "It isn't offered in the European Economic Area, Switzerland or the United Kingdom.",
    "Plans are suggestions" to
        "Plans, reminders and AI answers can be wrong. Always check due dates with your class. Noctil is provided as is, " +
        "without any warranty.",
    "Free, no ads" to
        "Noctil is free, shows no ads and never sells your data.",
)
