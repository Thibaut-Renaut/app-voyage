package corsica.universita.journalvoyage.ui.voyage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Etape
import corsica.universita.journalvoyage.data.toDate
import corsica.universita.journalvoyage.ui.common.PhotoImage
import corsica.universita.journalvoyage.ui.theme.Amber

/** Un voyage : sa vitrine, puis l'itinéraire (la timeline « Mon itinéraire » de la PWA). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyageScreen(
    voyageId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddEtape: () -> Unit,
    onOpenEtape: (Long) -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    val viewModel: VoyageViewModel = viewModel(factory = VoyageViewModel.factory(voyageId))
    val voyage by viewModel.voyage.collectAsStateWithLifecycle()
    val etapes by viewModel.etapes.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(voyage?.titre.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit_voyage)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_voyage)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEtape,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_etape)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PhotoImage(
                    voyage?.coverPath, maxPx = 1080, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
            voyage?.description?.takeIf { it.isNotBlank() }?.let { item { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            item { extraContent() }
            item {
                Text(
                    pluralStringResource(R.plurals.etapes_count, etapes.size, etapes.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (etapes.isEmpty()) item { Text(stringResource(R.string.etapes_empty), style = MaterialTheme.typography.bodyMedium) }
            items(etapes, key = { it.id }) { etape -> EtapeRow(etape, onClick = { onOpenEtape(etape.id) }) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_voyage)) },
            text = { Text(stringResource(R.string.delete_voyage_confirm, voyage?.titre.orEmpty(), etapes.size)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EtapeRow(etape: Etape, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoImage(
                etape.photos.firstOrNull(), maxPx = 300, contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(etape.titre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(etape.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(etape.date.toDate(), style = MaterialTheme.typography.labelSmall)
                    if (etape.lat != null) Icon(Icons.Default.Place, stringResource(R.string.has_location), Modifier.size(14.dp))
                    SyncBadge(etape.synced)
                }
            }
        }
    }
}

/** Le badge de la PWA : coche verte « Synchronisé », horloge ambre « En attente ». */
@Composable
fun SyncBadge(synced: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (synced) Icons.Default.CloudDone else Icons.Default.Schedule,
            contentDescription = null,
            tint = if (synced) MaterialTheme.colorScheme.primary else Amber,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(if (synced) R.string.synced else R.string.pending),
            style = MaterialTheme.typography.labelSmall,
            color = if (synced) MaterialTheme.colorScheme.primary else Amber,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
