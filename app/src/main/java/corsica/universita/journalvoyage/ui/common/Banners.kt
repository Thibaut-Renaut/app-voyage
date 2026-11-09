package corsica.universita.journalvoyage.ui.common

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.notif.Notifications
import corsica.universita.journalvoyage.ui.permissions.PermissionState
import corsica.universita.journalvoyage.ui.permissions.rememberPermissionFlow

/**
 * POST_NOTIFICATIONS, demandée après un geste de l'utilisateur (ce bouton), jamais au lancement.
 * Affiché tant que areNotificationsEnabled() est faux : permission refusée ou notifications coupées dans les réglages.
 * Après un refus, le bouton mène aux réglages de notification de l'app : l'app marche sans.
 */
@Composable
fun NotificationsBanner() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Notifications.enabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = Notifications.enabled(context)
        onPauseOrDispose { }
    }
    if (enabled) return

    val askable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val flow = if (askable) rememberPermissionFlow(Manifest.permission.POST_NOTIFICATIONS) else null
    InfoCard(
        text = stringResource(R.string.notifications_off),
        actions = listOf(
            stringResource(R.string.notifications_enable) to {
                if (flow != null && flow.state != PermissionState.DeniedForever) {
                    flow.request { enabled = Notifications.enabled(context) }
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        ),
    )
}

/** RGPD : la question posée une fois, avant toute collecte. Le choix se change ensuite dans le menu. */
@Composable
fun ConsentBanner(onAnswer: (Boolean) -> Unit) {
    InfoCard(
        text = stringResource(R.string.consent_text),
        actions = listOf(
            stringResource(R.string.consent_refuse) to { onAnswer(false) },
            stringResource(R.string.consent_accept) to { onAnswer(true) },
        ),
    )
}

@Composable
fun InfoCard(text: String, actions: List<Pair<String, () -> Unit>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
            }
        }
    }
}
