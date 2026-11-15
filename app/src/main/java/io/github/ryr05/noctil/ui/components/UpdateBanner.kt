package io.github.ryr05.noctil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.update.Updates

/**
 * "Noctil 0.1.48 is ready" on Today when a new version is out: Update downloads it, Later hides
 * the banner for that version (the dot on the profile button stays until it's installed).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val release by Updates.available.collectAsState()
    val dismissed by Updates.dismissed.collectAsState()
    val r = release ?: return
    if (r.version == dismissed) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.Lavender.copy(alpha = 0.35f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Noctil ${r.version} is ready", style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
                if (r.whatsNew.isNotBlank()) {
                    Text(
                        "New: ${r.whatsNew}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Open the download to install. Your tasks stay.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextTertiary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 34.dp)) {
            Pill("Update", selected = true) { Updates.download(context) }
            Pill("Later", selected = false) { Updates.dismissBanner(context) }
        }
    }
}
