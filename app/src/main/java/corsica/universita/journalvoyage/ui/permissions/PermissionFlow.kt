package corsica.universita.journalvoyage.ui.permissions

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import corsica.universita.journalvoyage.R

/**
 * une permission n'est pas un booléen.
 *
 *   NotAsked ──demande──▶ Granted ◀──────────────┐
 *      │                                         │ redemande, après explication
 *      └──refus──▶ DeniedOnce ───────────────────┘
 *                     └──2ᵉ refus──▶ DeniedForever : l'OS n'affiche PLUS le dialogue,
 *                                    seule issue : les réglages de l'app.
 * Et l'état change aussi sans l'app (réglages, révocation, « une fois ») : il se relit à ON_RESUME.
 */
enum class PermissionState { NotAsked, Granted, DeniedOnce, DeniedForever }

class PermissionFlow internal constructor(
    private val activity: Activity,
    val permissions: List<String>,
    /** false : une seule suffit (position : COARSE seule = approximative, ce n'est pas un refus). */
    private val requireAll: Boolean,
    private val askedBefore: () -> Boolean,
    private val markAsked: () -> Unit,
) {
    var state by mutableStateOf(PermissionState.NotAsked)
        private set

    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var onGranted: (() -> Unit)? = null
    private var onDenied: (() -> Unit)? = null

    private fun isGranted(p: String) =
        ContextCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED

    private fun granted() = if (requireAll) permissions.all(::isGranted) else permissions.any(::isGranted)

    /** true après UN refus : l'OS accepterait de redemander, à condition d'expliquer d'abord. */
    private fun rationale() = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    /**
     * L'OS ne répond qu'à deux questions (accordée ? expliquer ?). « Jamais demandée » et « refusée
     * définitivement » y répondent pareil (DENIED, false) : seul le drapeau `askedBefore`, gardé par l'app, les sépare.
     */
    private fun compute(afterResult: Boolean) = when {
        granted() -> PermissionState.Granted
        rationale() -> PermissionState.DeniedOnce
        askedBefore() || afterResult -> PermissionState.DeniedForever
        else -> PermissionState.NotAsked
    }

    fun refresh() {
        state = compute(afterResult = false)
    }

    /** Affiche le dialogue de l'OS — qui ne s'affichera pas en DeniedForever (réponse false immédiate). */
    fun request(onGranted: () -> Unit = {}, onDenied: () -> Unit = {}) {
        this.onGranted = onGranted
        this.onDenied = onDenied
        markAsked()
        launcher?.launch(permissions.toTypedArray())
    }

    internal fun onResult(result: Map<String, Boolean>) {
        state = compute(afterResult = true)
        Log.d("Journal", "Permission $result → $state")
        if (state == PermissionState.Granted) onGranted?.invoke() else onDenied?.invoke()
        onGranted = null
        onDenied = null
    }

    fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    internal fun attach(launcher: ActivityResultLauncher<Array<String>>) {
        this.launcher = launcher
    }
}

@Composable
fun rememberPermissionFlow(vararg permissions: String, requireAll: Boolean = true): PermissionFlow {
    val activity = LocalActivity.current!!
    var asked by rememberSaveable(*permissions) { mutableStateOf(false) } // « déjà demandée ? »
    val flow = remember(*permissions) {
        PermissionFlow(activity, permissions.toList(), requireAll, { asked }, { asked = true })
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        flow.onResult(it)
    }
    flow.attach(launcher)
    // Au retour des réglages (ou après une révocation), l'état réel est relu.
    LifecycleResumeEffect(flow) {
        flow.refresh()
        onPauseOrDispose { }
    }
    return flow
}

/**
 * La réparation de l'échec du le bouton ne demande pas aveuglément, il fait ce que l'état permet.
 * NotAsked → le dialogue · DeniedOnce → d'abord l'explication · DeniedForever → les réglages · Granted → l'action.
 */
@Composable
fun PermissionAction(
    flow: PermissionFlow,
    label: String,
    icon: ImageVector,
    rationale: String,
    onGranted: () -> Unit,
    onDenied: () -> Unit = {},
) {
    var showRationale by rememberSaveable { mutableStateOf(false) }
    when (flow.state) {
        PermissionState.NotAsked -> OutlinedButton(onClick = { flow.request(onGranted, onDenied) }) { ButtonContent(icon, label) }
        PermissionState.DeniedOnce -> OutlinedButton(onClick = { showRationale = true }) { ButtonContent(icon, label) }
        PermissionState.DeniedForever -> Column {
            OutlinedButton(onClick = flow::openSettings) {
                ButtonContent(Icons.Default.Settings, stringResource(R.string.permission_open_settings))
            }
            Text(
                stringResource(R.string.permission_denied_forever, label),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PermissionState.Granted -> Button(onClick = onGranted) { ButtonContent(icon, label) }
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(label) },
            text = { Text(rationale) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    flow.request(onGranted, onDenied)
                }) { Text(stringResource(R.string.permission_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.permission_not_now)) }
            },
        )
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null)
    Text(label, Modifier.padding(start = 8.dp))
}
