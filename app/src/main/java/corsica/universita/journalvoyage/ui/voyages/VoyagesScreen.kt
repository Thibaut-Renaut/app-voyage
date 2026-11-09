package corsica.universita.journalvoyage.ui.voyages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Voyage
import corsica.universita.journalvoyage.ui.common.ConsentBanner
import corsica.universita.journalvoyage.ui.common.NotificationsBanner
import corsica.universita.journalvoyage.ui.common.PhotoImage
import corsica.universita.journalvoyage.ui.common.copyToken
import corsica.universita.journalvoyage.ui.common.openPwa

/** L'accueil : les carnets de voyage (la section « Mes voyages » de la PWA). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyagesScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val viewModel: VoyagesViewModel = viewModel(factory = VoyagesViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { context.openPwa() }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_pwa))
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_token)) },
                            onClick = { menu = false; context.copyToken(state.fcmToken) },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (state.consent == true) R.string.consent_menu_on else R.string.consent_menu_off))
                            },
                            onClick = { menu = false; viewModel.setConsent(state.consent != true) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_voyage)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NotificationsBanner() }
            if (state.consent == null) item { ConsentBanner(onAnswer = viewModel::setConsent) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        pluralStringResource(R.plurals.voyages_count, state.voyages.size, state.voyages.size),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    // L'attente rendue visible : les étapes sont sur le disque ; elles partiront seules.
                    if (state.pending > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text(pluralStringResource(R.plurals.pending_count, state.pending, state.pending)) },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                        )
                    }
                }
            }
            if (state.voyages.isEmpty()) {
                item { Text(stringResource(R.string.voyages_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.voyages, key = { it.id }) { voyage ->
                VoyageCard(voyage, active = voyage.id == state.activeId, onClick = { onOpen(voyage.id) })
            }
        }
    }
}

@Composable
private fun VoyageCard(voyage: Voyage, active: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        PhotoImage(voyage.coverPath, maxPx = 800, contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(voyage.titre, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (active) SuggestionChip(onClick = onClick, label = { Text(stringResource(R.string.active_voyage)) })
            }
            if (voyage.description.isNotBlank()) {
                Text(voyage.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                pluralStringResource(R.plurals.etapes_count, voyage.etapesCount, voyage.etapesCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
