package corsica.universita.journalvoyage.ui.trajet

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Trajet
import corsica.universita.journalvoyage.data.toDateTime
import corsica.universita.journalvoyage.tracking.TrackMath
import corsica.universita.journalvoyage.tracking.TrackPoint
import corsica.universita.journalvoyage.tracking.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

class TrajetViewModel(private val trajetId: Long) : ViewModel() {
    private val repository = JournalContainer.repository

    /** null = pas encore lu ; Loaded(null) = supprimé. */
    val trajet: StateFlow<Loaded?> = repository.observeTrajet(trajetId).map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Les points, observés dans Room : écrits par le service, ils apparaissent ici en direct. */
    val points: StateFlow<List<TrackPoint>> = repository.trackPoints(trajetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Loaded(val trajet: Trajet?)

    fun shareGpx(context: Context) {
        viewModelScope.launch {
            val t = trajet.value?.trajet ?: return@launch
            val file = withContext(Dispatchers.IO) {
                val gpx = TrackMath.toGpx(context.getString(R.string.trajet_title, t.startedAt.toDateTime()), repository.trackPointsOnce(trajetId))
                File(context.cacheDir, "exports").apply { mkdirs() }.resolve("trajet_$trajetId.gpx").apply { writeText(gpx) }
            }
            val uri = JournalContainer.photoStore.uriFor(file.absolutePath)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.trajet_share_title)))
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTrajet(trajetId)
            onDone()
        }
    }
}

/** Un trajet : le tracé dessiné en direct pendant l'enregistrement, ses statistiques, l'export GPX. */
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

    // Pendant l'enregistrement, la durée avance chaque seconde, même sans nouveau point.
    val now by produceState(System.currentTimeMillis(), live) {
        while (live) { value = System.currentTimeMillis(); delay(1_000) }
    }

    val trajet = loaded?.trajet
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trajet)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (trajet != null && !live) {
                        IconButton(onClick = { viewModel.shareGpx(context) }, enabled = points.isNotEmpty()) {
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
                    onClick = { TrackingService.stop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text(stringResource(R.string.tracking_stop), Modifier.padding(start = 8.dp))
                }
            }
        }
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

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Le tracé, dessiné à la main sur un Canvas : pas de SDK de cartes (ni clé d'API, ni tuiles à télécharger
 * hors ligne). Projection équirectangulaire : la longitude est multipliée par cos(latitude), sinon le
 * tracé serait étiré d'est en ouest (en Corse, un degré de longitude ≈ 0,74 degré de latitude).
 */
@Composable
private fun TrackCanvas(points: List<TrackPoint>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val start = Color(0xFF2E7D32)
    val end = MaterialTheme.colorScheme.error
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (points.isEmpty()) return@Box
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
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
                (offY + (maxY - ys[i]) * scale).toFloat(), // le nord en haut
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
