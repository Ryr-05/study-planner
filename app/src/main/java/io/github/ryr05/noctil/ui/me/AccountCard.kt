package io.github.ryr05.noctil.ui.me

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import io.github.ryr05.noctil.cloud.CloudSync
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.tasks.ConfirmDialog
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.launch

/** Guest or signed-in account, with sign-in, sync status and sign-out. Signing in is optional. */
@Composable
fun AccountCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by CloudSync.status.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val user = status.user

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = Palette.Lavender)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user?.name ?: user?.email ?: "Guest",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    when {
                        user == null && status.available -> "Saved on this phone. Sign in to sync with your other devices."
                        user == null -> "Your tasks are saved on this phone and work offline."
                        else -> user.email ?: "Signed in"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                )
            }
        }
        if (status.available) {
            Spacer(Modifier.height(14.dp))
            if (user == null) {
                Pill(if (busy) "Opening Google..." else "Sign in with Google", selected = true, leading = Icons.Rounded.Login) {
                    if (busy) return@Pill
                    busy = true
                    scope.launch {
                        val result = CloudSync.signIn(context)
                        busy = false
                        result.exceptionOrNull()?.let { e ->
                            if (e !is GetCredentialCancellationException) {
                                message = "Couldn't sign in. ${e.message ?: "Check your internet connection and try again."}"
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (status.error != null) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = if (status.error != null) Palette.PriorityMedium else Palette.Lavender,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            status.syncing -> "Syncing..."
                            status.error != null -> "Will sync when you're back online"
                            status.lastSynced != null -> "Synced " + DateUtils.getRelativeTimeSpanString(
                                status.lastSynced!!, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                            ).toString().replaceFirstChar { it.lowercase() }
                            else -> "Ready to sync"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Sync now", selected = false, leading = Icons.Rounded.Sync) {
                        scope.launch { CloudSync.syncNow(context) }
                    }
                    Pill("Sign out", selected = false, leading = Icons.Rounded.Logout) { confirmSignOut = true }
                }
            }
        }
    }

    message?.let { text ->
        ConfirmDialog(
            title = "Sign-in problem",
            body = text,
            confirm = "OK",
            onConfirm = { message = null },
            onDismiss = { message = null },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Your tasks stay on this phone. They just stop syncing until you sign in again.",
            confirm = "Sign out",
            onConfirm = {
                confirmSignOut = false
                scope.launch { CloudSync.signOut(context) }
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}
