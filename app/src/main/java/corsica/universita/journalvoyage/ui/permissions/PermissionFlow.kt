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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import corsica.universita.journalvoyage.R

/** NotAsked → Granted, ou DeniedOnce (explication avant de redemander) → DeniedForever (seuls les réglages permettent de l'accorder). */
enum class PermissionState { NotAsked, Granted, DeniedOnce, DeniedForever }

class PermissionFlow internal constructor(
    private val activity: Activity,
    val permissions: List<String>,
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

    private fun rationale() = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    // Android répond de la même façon pour « jamais demandée » et « refusée définitivement » : askedBefore les distingue.
    private fun compute(afterResult: Boolean) = when {
        granted() -> PermissionState.Granted
        rationale() -> PermissionState.DeniedOnce
        askedBefore() || afterResult -> PermissionState.DeniedForever
        else -> PermissionState.NotAsked
    }

    fun refresh() {
        state = compute(afterResult = false)
    }

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
    var asked by rememberSaveable(*permissions) { mutableStateOf(false) }
    val flow = remember(*permissions) {
        PermissionFlow(activity, permissions.toList(), requireAll, { asked }, { asked = true })
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        flow.onResult(it)
    }
    flow.attach(launcher)
    LifecycleResumeEffect(flow) {
        flow.refresh()
        onPauseOrDispose { }
    }
    return flow
}

class PermissionGate internal constructor(private val run: (onGranted: () -> Unit) -> Unit) {
    operator fun invoke(onGranted: () -> Unit) = run(onGranted)
}

@Composable
fun rememberPermissionGate(flow: PermissionFlow, label: String, rationale: String, onDenied: () -> Unit = {}): PermissionGate {
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(label) },
            text = { Text(rationale) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    flow.request(pending ?: {}, onDenied)
                }) { Text(stringResource(R.string.permission_continue)) }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.permission_not_now)) } },
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(label) },
            text = { Text(stringResource(R.string.permission_denied_forever, label)) },
            confirmButton = {
                TextButton(onClick = { showSettings = false; flow.openSettings() }) { Text(stringResource(R.string.permission_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    return remember(flow) {
        PermissionGate { onGranted ->
            when (flow.state) {
                PermissionState.Granted -> onGranted()
                PermissionState.NotAsked -> flow.request(onGranted, onDenied)
                PermissionState.DeniedOnce -> { pending = onGranted; showRationale = true }
                PermissionState.DeniedForever -> showSettings = true
            }
        }
    }
}
