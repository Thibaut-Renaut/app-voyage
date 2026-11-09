package corsica.universita.journalvoyage.ui.trajet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import corsica.universita.journalvoyage.data.Trajet
import corsica.universita.journalvoyage.data.toDateTime
import corsica.universita.journalvoyage.tracking.TrackMath
import corsica.universita.journalvoyage.tracking.TrackPoint
import corsica.universita.journalvoyage.tracking.TrackingService
import corsica.universita.journalvoyage.ui.common.FieldLabel
import corsica.universita.journalvoyage.ui.common.FormField
import corsica.universita.journalvoyage.ui.common.ModalCard
import corsica.universita.journalvoyage.ui.common.PrimaryButton
import corsica.universita.journalvoyage.ui.common.SmallButton
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GpxEvent {
    data object Saved : GpxEvent
    data class Share(val uri: Uri) : GpxEvent
}

class TrajetViewModel(private val trajetId: Long) : ViewModel() {
    private val repository = JournalContainer.repository
    private val exporter = JournalContainer.gpxExporter

    private val _events = Channel<GpxEvent>(Channel.BUFFERED)
    val events: Flow<GpxEvent> = _events.receiveAsFlow()

    val trajet: StateFlow<Loaded?> = repository.observeTrajet(trajetId).map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val points: StateFlow<List<TrackPoint>> = repository.trackPoints(trajetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Loaded(val trajet: Trajet?)

    @OptIn(ExperimentalCoroutinesApi::class)
    val etapes: StateFlow<List<Etape>> = repository.observeTrajet(trajetId)
        .flatMapLatest { t -> if (t == null) flowOf(emptyList()) else repository.etapes(t.voyageId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(nom: String) {
        viewModelScope.launch { repository.renameTrajet(trajetId, nom) }
    }

    private suspend fun gpx(fallbackName: String): String? {
        val t = trajet.value?.trajet ?: return null
        return TrackMath.toGpx(t.nom ?: fallbackName, repository.trackPointsOnce(trajetId))
    }

    fun saveGpx(uri: Uri, fallbackName: String) {
        viewModelScope.launch {
            val text = gpx(fallbackName) ?: return@launch
            exporter.save(uri, text)
            _events.send(GpxEvent.Saved)
        }
    }

    fun shareGpx(fallbackName: String) {
        viewModelScope.launch {
            val text = gpx(fallbackName) ?: return@launch
            _events.send(GpxEvent.Share(exporter.forSharing("trajet_$trajetId.gpx", text)))
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTrajet(trajetId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrajetScreen(trajetId: Long, onBack: () -> Unit) {
    val viewModel: TrajetViewModel = viewModel(factory = viewModelFactory { initializer { TrajetViewModel(trajetId) } })
    val loaded by viewModel.trajet.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val active by TrackingService.activeTrajetId.collectAsStateWithLifecycle()
    val live = active == trajetId
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    val etapes by viewModel.etapes.collectAsStateWithLifecycle()
    val fallbackName = loaded?.trajet?.let { stringResource(R.string.trajet_title, it.startedAt.toDateTime()) }.orEmpty()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        uri?.let { viewModel.saveGpx(it, fallbackName) }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                GpxEvent.Saved -> Toast.makeText(context, R.string.trajet_gpx_saved, Toast.LENGTH_LONG).show()
                is GpxEvent.Share -> context.shareGpx(event.uri)
            }
        }
    }

    val now by produceState(System.currentTimeMillis(), live) {
        while (live) { value = System.currentTimeMillis(); delay(1_000) }
    }

    val trajet = loaded?.trajet
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trajet?.nom ?: stringResource(R.string.trajet), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (trajet != null) {
                        IconButton(onClick = { naming = true }) { Icon(Icons.Default.Edit, stringResource(R.string.trajet_rename)) }
                    }
                    if (trajet != null && !live) {
                        IconButton(onClick = { saveLauncher.launch(gpxFileName(trajet)) }, enabled = points.isNotEmpty()) {
                            Icon(Icons.Default.Download, stringResource(R.string.trajet_save_gpx))
                        }
                        IconButton(onClick = { viewModel.shareGpx(fallbackName) }, enabled = points.isNotEmpty()) {
                            Icon(Icons.Default.Share, stringResource(R.string.trajet_share_gpx))
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.trajet_delete)) }
                    }
                },
            )
        },
    ) { padding ->
        if (trajet == null) {
            if (loaded != null) Text(stringResource(R.string.trajet_missing), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val stats = TrackMath.stats(points)
        val duration = (if (live) now else trajet.endedAt ?: now) - trajet.startedAt
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) Icon(Icons.Default.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 6.dp))
                Text(
                    stringResource(
                        when {
                            live -> R.string.trajet_live
                            trajet.endedAt == null -> R.string.trajet_interrupted
                            else -> R.string.trajet_finished
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(trajet.startedAt.toDateTime(), style = MaterialTheme.typography.bodyMedium)

            TrackCanvas(points, Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)))
            if (points.isEmpty() && live) Text(stringResource(R.string.trajet_waiting_fix), style = MaterialTheme.typography.bodySmall)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row {
                        Stat(stringResource(R.string.stat_distance), TrackMath.formatDistance(stats.distanceM), Modifier.weight(1f))
                        Stat(stringResource(R.string.stat_duration), TrackMath.formatDuration(duration), Modifier.weight(1f))
                    }
                    Row {
                        val hours = duration / 3_600_000.0
                        val speed = if (hours > 0) stats.distanceM / 1000 / hours else 0.0
                        Stat(stringResource(R.string.stat_speed), String.format(Locale.FRANCE, "%.1f km/h", speed), Modifier.weight(1f))
                        Stat(stringResource(R.string.stat_elevation), "${stats.elevationGainM.roundToInt()} m", Modifier.weight(1f))
                    }
                    Row {
                        Stat(stringResource(R.string.stat_points), points.size.toString(), Modifier.weight(1f))
                        Stat(stringResource(R.string.stat_accuracy), points.lastOrNull()?.let { "± ${it.accuracy.roundToInt()} m" } ?: "—", Modifier.weight(1f))
                    }
                }
            }

            if (live) {
                Button(
                    onClick = {
                        TrackingService.stop(context)
                        if (trajet.nom == null) naming = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text(stringResource(R.string.tracking_stop), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (naming && trajet != null) {
        NameTrajetDialog(
            initial = trajet.nom.orEmpty(),
            etapes = etapes,
            onDismiss = { naming = false },
            onSave = { viewModel.rename(it); naming = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.trajet_delete)) },
            text = { Text(stringResource(R.string.trajet_delete_confirm, points.size)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun gpxFileName(t: Trajet): String {
    val base = java.text.Normalizer.normalize(t.nom ?: "trajet_${t.id}", java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return "${base.ifEmpty { "trajet" }}.gpx"
}

@Composable
private fun NameTrajetDialog(initial: String, etapes: List<Etape>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var nom by rememberSaveable { mutableStateOf(initial) }
    var from by rememberSaveable { mutableStateOf<String?>(null) }
    var to by rememberSaveable { mutableStateOf<String?>(null) }
    val titres = etapes.sortedBy { it.date }.map { it.titre }
    val res = LocalContext.current.resources
    fun compose() {
        val f = from
        val t = to
        nom = when {
            f != null && t != null -> res.getString(R.string.trajet_name_from_to, f, t)
            f != null -> res.getString(R.string.trajet_name_from, f)
            t != null -> res.getString(R.string.trajet_name_to, t)
            else -> nom
        }
    }
    ModalCard(
        badge = stringResource(R.string.trajet),
        title = stringResource(R.string.trajet_rename),
        subtitle = stringResource(R.string.trajet_rename_subtitle),
        onDismiss = onDismiss,
    ) {
        if (titres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EtapePicker(stringResource(R.string.trajet_from), from, titres, Modifier.weight(1f)) { from = it; compose() }
                EtapePicker(stringResource(R.string.trajet_to), to, titres, Modifier.weight(1f)) { to = it; compose() }
            }
        }
        FormField(stringResource(R.string.trajet_name), nom, { nom = it }, stringResource(R.string.trajet_name_placeholder))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallButton(stringResource(R.string.cancel), onDismiss, outline = true)
            PrimaryButton(stringResource(R.string.save), { onSave(nom) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EtapePicker(label: String, selected: String?, titres: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        Box(Modifier.padding(top = 6.dp)) {
            SmallButton(selected ?: stringResource(R.string.trajet_pick_etape), { open = true }, Modifier.fillMaxWidth(), outline = true)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                titres.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; onPick(t) }) }
            }
        }
    }
}

private fun Context.shareGpx(uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, getString(R.string.trajet_share_title)))
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrackCanvas(points: List<TrackPoint>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val start = Color(0xFF2E7D32)
    val end = MaterialTheme.colorScheme.error
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (points.isEmpty()) return@Box
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            // Projection équirectangulaire : la longitude est corrigée par cos(latitude).
            val k = cos(Math.toRadians(points.map { it.lat }.average()))
            val xs = points.map { it.lng * k }
            val ys = points.map { it.lat }
            val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
            val span = maxOf(maxX - minX, maxY - minY).takeIf { it > 0 } ?: 1e-6
            val scale = minOf(size.width, size.height) / span
            val offX = (size.width - (maxX - minX) * scale) / 2
            val offY = (size.height - (maxY - minY) * scale) / 2
            fun at(i: Int) = Offset(
                (offX + (xs[i] - minX) * scale).toFloat(),
                (offY + (maxY - ys[i]) * scale).toFloat(),
            )
            val path = Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
            }
            drawPath(path, line, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(start, radius = 7.dp.toPx(), center = at(0))
            drawCircle(end, radius = 7.dp.toPx(), center = at(points.lastIndex))
        }
    }
}
