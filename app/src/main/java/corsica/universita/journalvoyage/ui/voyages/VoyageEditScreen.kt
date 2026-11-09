package corsica.universita.journalvoyage.ui.voyages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.ui.common.PhotoImage

/** Créer ou modifier un carnet : titre, description, photo vitrine (la modale « Nouveau voyage » de la PWA). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyageEditScreen(voyageId: Long, onDone: () -> Unit) {
    val viewModel: VoyageEditViewModel = viewModel(factory = VoyageEditViewModel.factory(voyageId))
    val form by viewModel.form.collectAsStateWithLifecycle()

    // Le sélecteur de photos du système : AUCUNE permission (l'utilisateur choisit, l'app ne voit que ce choix).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::setCover)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (voyageId == 0L) R.string.new_voyage else R.string.edit_voyage)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.titre, onValueChange = viewModel::setTitre,
                label = { Text(stringResource(R.string.voyage_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description, onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.voyage_description)) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.cover), style = MaterialTheme.typography.labelLarge)
            PhotoImage(form.coverPath, maxPx = 800, contentDescription = stringResource(R.string.cover), modifier = Modifier.fillMaxWidth().height(160.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text(stringResource(R.string.choose_cover), Modifier.padding(start = 8.dp))
                }
                if (form.coverPath != null) TextButton(onClick = viewModel::clearCover) { Text(stringResource(R.string.remove_cover)) }
            }
            Button(
                enabled = form.titre.isNotBlank(),
                onClick = { viewModel.save(onDone) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
