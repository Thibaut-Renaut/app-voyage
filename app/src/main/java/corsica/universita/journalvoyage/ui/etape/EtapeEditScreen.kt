package corsica.universita.journalvoyage.ui.etape

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.ui.EtapeEdit
import corsica.universita.journalvoyage.ui.common.PhotoImage
import corsica.universita.journalvoyage.ui.permissions.PermissionAction
import corsica.universita.journalvoyage.ui.permissions.rememberPermissionFlow
import java.io.File
import kotlin.math.roundToInt

/**
 * « Ajouter une étape » : titre, récit, position, photos — le formulaire de la PWA.
 * Deux permissions runtime, demandées au geste (pas à l'ouverture), gérées jusqu'au refus définitif ;
 * et le sélecteur de photos du système, qui n'en demande aucune.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtapeEditScreen(key: EtapeEdit, onDone: () -> Unit) {
    val viewModel: EtapeEditViewModel = viewModel(factory = EtapeEditViewModel.factory(key))
    val form by viewModel.form.collectAsStateWithLifecycle()
    val fix by viewModel.fix.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()

    // Non null = la caméra est ouverte, sur ce fichier. Saveable : une rotation ne ferme pas la caméra.
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberPermissionFlow(Manifest.permission.CAMERA)
    // Les deux ensemble, et une seule suffit : « Approximative » (COARSE seule) n'est pas un refus.
    val location = rememberPermissionFlow(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, requireAll = false,
    )
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        viewModel.importPhotos(uris)
    }

    cameraTarget?.let { target ->
        CameraCapture(
            file = File(target),
            onCaptured = { file ->
                Log.d("Journal", "Photo enregistrée : ${file.absolutePath} (${file.length() / 1024} Ko)")
                viewModel.addPhoto(file.absolutePath)
                cameraTarget = null
            },
            onCancel = { cameraTarget = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_etape)) },
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
                label = { Text(stringResource(R.string.etape_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description, onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.etape_description)) }, minLines = 4, modifier = Modifier.fillMaxWidth(),
            )

            // Position
            Text(stringResource(R.string.location), style = MaterialTheme.typography.labelLarge)
            if (form.lat != null && form.lng != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.location_value, form.lat!!, form.lng!!), Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearLocation) { Text(stringResource(R.string.remove)) }
                }
            }
            fix?.let {
                Text(
                    stringResource(
                        R.string.location_accuracy, it.accuracy.roundToInt(),
                        stringResource(if (it.precise) R.string.location_precise else R.string.location_approximate),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionAction(
                    flow = location, label = stringResource(R.string.add_location), icon = Icons.Default.MyLocation,
                    rationale = stringResource(R.string.location_rationale), onGranted = viewModel::locate,
                )
                if (locating) CircularProgressIndicator(Modifier.size(24.dp))
            }

            // Photos
            Text(stringResource(R.string.photos), style = MaterialTheme.typography.labelLarge)
            if (form.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(form.photos, key = { it }) { path ->
                        Box {
                            PhotoImage(path, maxPx = 300, contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)))
                            FilledTonalIconButton(onClick = { viewModel.removePhoto(path) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.remove), Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionAction(
                    flow = camera, label = stringResource(R.string.take_photo), icon = Icons.Default.PhotoCamera,
                    rationale = stringResource(R.string.camera_rationale),
                    onGranted = { cameraTarget = viewModel.newPhotoFile().absolutePath },
                )
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.from_gallery), Modifier.padding(start = 8.dp))
                }
            }

            Button(
                enabled = form.titre.isNotBlank() && form.description.isNotBlank(),
                onClick = { viewModel.save(onDone) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.save_etape)) }
            Text(stringResource(R.string.save_offline_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
