package corsica.universita.journalvoyage.ui.home

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.journalvoyage.BuildConfig
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Destinations
import corsica.universita.journalvoyage.data.onlineFlow
import corsica.universita.journalvoyage.ui.common.ConsentBanner
import corsica.universita.journalvoyage.ui.common.NotificationsBanner
import corsica.universita.journalvoyage.ui.common.SectionHeader
import corsica.universita.journalvoyage.ui.common.copyToken
import corsica.universita.journalvoyage.ui.common.openPwa
import corsica.universita.journalvoyage.ui.etape.CameraCapture
import corsica.universita.journalvoyage.ui.permissions.rememberPermissionFlow
import corsica.universita.journalvoyage.ui.theme.AmberBg
import corsica.universita.journalvoyage.ui.theme.AmberBorder
import corsica.universita.journalvoyage.ui.theme.AmberText
import corsica.universita.journalvoyage.ui.theme.Cream50
import corsica.universita.journalvoyage.ui.theme.Fraunces
import corsica.universita.journalvoyage.ui.theme.Gray200
import corsica.universita.journalvoyage.ui.theme.Gray500
import corsica.universita.journalvoyage.ui.theme.Green100
import corsica.universita.journalvoyage.ui.theme.Green200
import corsica.universita.journalvoyage.ui.theme.Green300
import corsica.universita.journalvoyage.ui.theme.Green50
import corsica.universita.journalvoyage.ui.theme.Green500
import corsica.universita.journalvoyage.ui.theme.Green600
import corsica.universita.journalvoyage.ui.theme.Green700
import corsica.universita.journalvoyage.ui.theme.Green900
import corsica.universita.journalvoyage.ui.theme.SectionLabel
import corsica.universita.journalvoyage.ui.trajet.TrajetsSection
import java.io.File
import kotlinx.coroutines.delay

private const val TAG = "Journal"

@Composable
fun HomeScreen(onOpenEtape: (Long) -> Unit, onOpenTrajet: (Long) -> Unit) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.etapeForm.collectAsStateWithLifecycle()
    val geo by viewModel.geo.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val online by remember { context.onlineFlow() }.collectAsStateWithLifecycle(initialValue = true)

    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberPermissionFlow(Manifest.permission.CAMERA)
    val location = rememberPermissionFlow(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, requireAll = false)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { viewModel.importPhotos(it) }

    cameraTarget?.let { target ->
        CameraCapture(
            file = File(target),
            onCaptured = { file ->
                Log.d(TAG, "Photo enregistrée : ${file.absolutePath} (${file.length() / 1024} Ko)")
                viewModel.addPhoto(file.absolutePath)
                cameraTarget = null
            },
            onCancel = { cameraTarget = null },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Cream50)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Header(consent = consent, viewModel = viewModel) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotificationsBanner()
                    if (consent == null) ConsentBanner(onAnswer = viewModel::setConsent)
                }
            }
            item {
                EtapeFormSection(
                    activeTitle = state.active?.titre,
                    form = form,
                    geo = geo,
                    cameraFlow = camera,
                    locationFlow = location,
                    onEditActive = { state.active?.let { viewModel.openEdit(it.id) } },
                    onSwitch = viewModel::openSwitch,
                    onNewVoyage = viewModel::openCreate,
                    onTitre = viewModel::setEtapeTitre,
                    onDescription = viewModel::setEtapeDescription,
                    onCamera = { cameraTarget = viewModel.newPhotoFile().absolutePath },
                    onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemovePhoto = viewModel::removePhoto,
                    onLocate = viewModel::locate,
                    onSave = { viewModel.saveEtape(online) },
                )
            }
            item {
                SectionHeader(
                    stringResource(R.string.journal_label), stringResource(R.string.journal_title), Icons.Outlined.Schedule,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 20.dp),
                )
            }
            if (state.etapes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.journal_empty), color = Gray500, fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            itemsIndexed(state.etapes, key = { _, e -> "etape-${e.id}" }) { index, etape ->
                TimelineEntry(
                    number = index + 1, etape = etape, last = index == state.etapes.lastIndex,
                    onClick = { onOpenEtape(etape.id) }, onDelete = { viewModel.deleteEtape(etape.id) },
                )
            }
            state.active?.let { active ->
                item {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader(stringResource(R.string.trajets_label), stringResource(R.string.trajets), Icons.Outlined.Route)
                        TrajetsSection(voyageId = active.id, onOpen = onOpenTrajet)
                    }
                }
            }
            item {
                VoyagesHeader(onCreate = viewModel::openCreate)
            }
            itemsIndexed(state.voyages, key = { _, v -> "voyage-${v.id}" }) { _, v ->
                VoyageCard(
                    voyage = v, active = v.id == state.active?.id,
                    onOpen = { viewModel.openVoyage(v.id) }, onEdit = { viewModel.openEdit(v.id) }, onDelete = { viewModel.askDelete(v.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            item {
                SectionHeader(
                    stringResource(R.string.destinations_label), stringResource(R.string.destinations_title), Icons.Outlined.Explore,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 10.dp),
                )
            }
            Destinations.all.forEach { d ->
                item(key = "dest-${d.nom}") { DestinationCard(d, Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
            }
            item { Footer() }
        }

        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Cream50.copy(alpha = 0.94f)))
        NetworkPill(online, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 64.dp))
        ToastHost(viewModel, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp))
    }

    VoyageDialogs(viewModel, dialog)
}

@Composable
private fun Header(consent: Boolean?, viewModel: HomeViewModel) {
    val context = LocalContext.current
    val token by viewModel.fcmToken.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Green100, Green50, Cream50)))
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 36.dp, bottom = 28.dp)) {
            Row(
                Modifier.clip(CircleShape).background(Color.White).border(1.dp, Green200, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Green500))
                Text(stringResource(R.string.header_badge).uppercase(), style = SectionLabel.copy(letterSpacing = 0.8.sp, fontSize = 12.sp), color = Green700, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.header_title_1)); append("\n")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Green600, fontWeight = FontWeight.SemiBold)) { append(stringResource(R.string.header_title_2)) }
                },
                fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 39.sp, color = Green900,
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.header_subtitle), fontSize = 16.5.sp, lineHeight = 26.sp, color = Gray500)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more), tint = Green700) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.open_pwa)) }, onClick = { menu = false; context.openPwa() })
                if (BuildConfig.DEBUG) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.copy_token)) }, onClick = { menu = false; context.copyToken(token) })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(if (consent == true) R.string.consent_menu_on else R.string.consent_menu_off)) },
                    onClick = { menu = false; viewModel.setConsent(consent != true) },
                )
            }
        }
    }
    HorizontalDivider(color = Green200)
}

@Composable
private fun NetworkPill(online: Boolean, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(online) {
        if (first) { first = false; if (online) return@LaunchedEffect }
        visible = true
        delay(4_000)
        visible = false
    }
    if (!visible) return
    Row(
        modifier
            .clip(CircleShape)
            .background(if (online) Green100 else AmberBg)
            .border(1.dp, if (online) Green300 else AmberBorder, CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (online) Green500 else AmberText))
        Text(
            stringResource(if (online) R.string.network_online else R.string.network_offline),
            color = if (online) Green700 else AmberText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ToastHost(viewModel: HomeViewModel, modifier: Modifier) {
    var current by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(Unit) {
        viewModel.toasts.collect { t ->
            current = t
            delay(3_500)
            if (current == t) current = null
        }
    }
    val t = current ?: return
    Text(
        stringResource(t.text, *t.args.toTypedArray()),
        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .clip(CircleShape)
            .background(Green900)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun Footer() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(color = Gray200)
        Text(stringResource(R.string.app_name), fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 17.5.sp, color = Green900, modifier = Modifier.padding(top = 28.dp))
        Text(stringResource(R.string.footer_meta), fontSize = 13.sp, color = Gray500, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
