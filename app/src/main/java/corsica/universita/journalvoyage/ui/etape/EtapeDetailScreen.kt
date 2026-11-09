package corsica.universita.journalvoyage.ui.etape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Etape
import corsica.universita.journalvoyage.data.toDateTime
import corsica.universita.journalvoyage.ui.common.PhotoImage
import corsica.universita.journalvoyage.ui.common.openMap
import corsica.universita.journalvoyage.ui.common.shareEtape
import corsica.universita.journalvoyage.ui.home.SyncPill
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EtapeDetailViewModel(private val etapeId: Long) : ViewModel() {
    private val repository = JournalContainer.repository

    val etape: StateFlow<Loaded?> = repository.observeEtape(etapeId).map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class Loaded(val etape: Etape?)

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEtape(etapeId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtapeDetailScreen(etapeId: Long, onBack: () -> Unit) {
    val viewModel: EtapeDetailViewModel = viewModel(factory = viewModelFactory { initializer { EtapeDetailViewModel(etapeId) } })
    val loaded by viewModel.etape.collectAsStateWithLifecycle()
    val etape = loaded?.etape
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.etape)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    etape?.let { e ->
                        IconButton(onClick = { context.shareEtape(e) }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                    }
                },
            )
        },
    ) { padding ->
        val e = etape ?: run {
            if (loaded != null) Text(stringResource(R.string.etape_missing), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (e.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(e.photos, key = { it }) { path ->
                        PhotoImage(
                            path, maxPx = 1080, contentDescription = stringResource(R.string.etape_photo),
                            modifier = Modifier.fillParentMaxWidth(if (e.photos.size == 1) 1f else 0.85f).height(260.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                }
            }
            Text(e.titre, style = MaterialTheme.typography.headlineSmall)
            Text(e.date.toDateTime(), style = MaterialTheme.typography.bodyMedium)
            SyncPill(e.synced)
            Text(e.description, style = MaterialTheme.typography.bodyLarge)
            if (e.lat != null && e.lng != null) {
                Text(stringResource(R.string.location_value, e.lat, e.lng), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { context.openMap(e.lat, e.lng, e.titre) }) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Text(stringResource(R.string.open_map), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_etape)) },
            text = { Text(stringResource(R.string.delete_etape_confirm, etape?.titre.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
