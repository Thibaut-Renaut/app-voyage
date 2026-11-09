package corsica.universita.journalvoyage.ui.trajet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Trajet
import corsica.universita.journalvoyage.data.toDateTime
import corsica.universita.journalvoyage.notif.Notifications
import corsica.universita.journalvoyage.tracking.TrackMath
import corsica.universita.journalvoyage.tracking.TrackingService
import corsica.universita.journalvoyage.ui.common.InfoCard
import corsica.universita.journalvoyage.ui.common.SoftButton
import corsica.universita.journalvoyage.ui.permissions.PermissionState
import corsica.universita.journalvoyage.ui.permissions.rememberPermissionFlow
import corsica.universita.journalvoyage.ui.permissions.rememberPermissionGate
import corsica.universita.journalvoyage.ui.theme.Green50
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TrajetsViewModel(voyageId: Long) : ViewModel() {
    val trajets: StateFlow<List<Trajet>> = JournalContainer.repository.trajets(voyageId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun TrajetsSection(voyageId: Long, onOpen: (Long) -> Unit) {
    val viewModel: TrajetsViewModel = viewModel(key = "trajets-$voyageId", factory = viewModelFactory { initializer { TrajetsViewModel(voyageId) } })
    val trajets by viewModel.trajets.collectAsStateWithLifecycle()
    val active by TrackingService.activeTrajetId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val location = rememberPermissionFlow(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, requireAll = false,
    )
    val locationGate = rememberPermissionGate(
        location, stringResource(R.string.trajet_start), stringResource(R.string.trajet_location_rationale),
        onDenied = { JournalContainer.analytics.trajetFailed("permission_denied") },
    )
    var locationOn by remember { mutableStateOf(true) }
    var notificationsOn by remember { mutableStateOf(true) }
    var precise by remember { mutableStateOf(true) }
    LifecycleResumeEffect(location.state) {
        locationOn = context.locationEnabled()
        notificationsOn = Notifications.enabled(context)
        precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    var openWhenStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active, openWhenStarted) {
        val id = active
        if (openWhenStarted && id != null) {
            openWhenStarted = false
            onOpen(id)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.trajets_intro), style = MaterialTheme.typography.bodySmall)

        val activeId = active
        when {
            activeId != null -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(activeId) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(if (trajets.any { it.id == activeId }) R.string.trajet_in_progress else R.string.trajet_in_progress_other),
                        Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(stringResource(R.string.trajet_see), color = MaterialTheme.colorScheme.primary)
                }
            }
            !locationOn -> InfoCard(
                text = stringResource(R.string.trajet_location_off),
                actions = listOf(
                    stringResource(R.string.trajet_location_settings) to {
                        JournalContainer.analytics.trajetFailed("location_off")
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                ),
            )
            else -> SoftButton(stringResource(R.string.trajet_start), Icons.AutoMirrored.Filled.DirectionsWalk, background = Green50, onClick = {
                locationGate {
                    openWhenStarted = true
                    TrackingService.start(context, voyageId)
                }
            })
        }
        if (active == null && locationOn && !precise && location.state == PermissionState.Granted) {
            Text(stringResource(R.string.trajet_approximate_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (!notificationsOn) {
            Text(stringResource(R.string.trajet_no_notifications), style = MaterialTheme.typography.bodySmall)
        }
        trajets.forEach { t -> TrajetRow(t, live = t.id == active, onClick = { onOpen(t.id) }) }
    }
}

@Composable
private fun TrajetRow(t: Trajet, live: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (live) Icons.Default.FiberManualRecord else Icons.Default.Route, contentDescription = null,
                tint = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(t.nom ?: stringResource(R.string.trajet_title, t.startedAt.toDateTime()), style = MaterialTheme.typography.titleSmall)
                if (t.nom != null) Text(t.startedAt.toDateTime(), style = MaterialTheme.typography.bodySmall)
                val duration = (t.endedAt ?: System.currentTimeMillis()) - t.startedAt
                Text(
                    "${TrackMath.formatDistance(t.distanceM)} · ${TrackMath.formatDuration(duration)} · ${t.pointsCount} pts",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (live) TextButton(onClick = onClick) { Text(stringResource(R.string.trajet_see)) }
        }
    }
}

private fun Context.locationEnabled(): Boolean =
    LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java))
