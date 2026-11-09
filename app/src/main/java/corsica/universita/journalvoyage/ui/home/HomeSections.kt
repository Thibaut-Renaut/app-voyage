package corsica.universita.journalvoyage.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Destination
import corsica.universita.journalvoyage.data.Etape
import corsica.universita.journalvoyage.data.Voyage
import corsica.universita.journalvoyage.ui.common.AssetImage
import corsica.universita.journalvoyage.ui.common.DashedZone
import corsica.universita.journalvoyage.ui.common.FieldLabel
import corsica.universita.journalvoyage.ui.common.FormField
import corsica.universita.journalvoyage.ui.common.PhotoImage
import corsica.universita.journalvoyage.ui.common.Pill
import corsica.universita.journalvoyage.ui.common.PrimaryButton
import corsica.universita.journalvoyage.ui.common.SectionHeader
import corsica.universita.journalvoyage.ui.common.SmallButton
import corsica.universita.journalvoyage.ui.common.SoftButton
import corsica.universita.journalvoyage.ui.common.pwaCard
import corsica.universita.journalvoyage.ui.permissions.PermissionFlow
import corsica.universita.journalvoyage.ui.permissions.rememberPermissionGate
import corsica.universita.journalvoyage.ui.theme.AmberBg
import corsica.universita.journalvoyage.ui.theme.AmberText
import corsica.universita.journalvoyage.ui.theme.Cream50
import corsica.universita.journalvoyage.ui.theme.Danger
import corsica.universita.journalvoyage.ui.theme.Fraunces
import corsica.universita.journalvoyage.ui.theme.Gray200
import corsica.universita.journalvoyage.ui.theme.Gray400
import corsica.universita.journalvoyage.ui.theme.Gray500
import corsica.universita.journalvoyage.ui.theme.Gray700
import corsica.universita.journalvoyage.ui.theme.Green100
import corsica.universita.journalvoyage.ui.theme.Green200
import corsica.universita.journalvoyage.ui.theme.Green300
import corsica.universita.journalvoyage.ui.theme.Green50
import corsica.universita.journalvoyage.ui.theme.Green600
import corsica.universita.journalvoyage.ui.theme.Green700
import corsica.universita.journalvoyage.ui.theme.Green900
import corsica.universita.journalvoyage.ui.theme.RadiusLg
import corsica.universita.journalvoyage.ui.theme.RadiusSm
import corsica.universita.journalvoyage.ui.theme.SectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================== 1. « Nouvelle étape de … » (section--form) ========================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EtapeFormSection(
    activeTitle: String?,
    form: EtapeForm,
    geo: GeoStatus,
    cameraFlow: PermissionFlow,
    locationFlow: PermissionFlow,
    onEditActive: () -> Unit,
    onSwitch: () -> Unit,
    onNewVoyage: () -> Unit,
    onTitre: (String) -> Unit,
    onDescription: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onLocate: () -> Unit,
    onSave: () -> Unit,
) {
    // Les permissions, demandées au geste : le bouton garde le style de la PWA, la machine à états fait le reste.
    val cameraGate = rememberPermissionGate(cameraFlow, stringResource(R.string.take_photo), stringResource(R.string.camera_rationale))
    val locationGate = rememberPermissionGate(locationFlow, stringResource(R.string.add_location), stringResource(R.string.location_rationale))

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(stringResource(R.string.form_label).uppercase(), style = SectionLabel, color = Green600)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Outlined.EditNote, null, tint = Green900, modifier = Modifier.padding(top = 4.dp).size(22.dp))
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.form_title_prefix)); append(" ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Green600, fontWeight = FontWeight.SemiBold)) { append(activeTitle.orEmpty()) }
                    },
                    fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = Green900,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        // .voyage-selector-actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(stringResource(R.string.edit_active_voyage), onEditActive, Modifier.weight(1f), icon = Icons.Outlined.Edit)
            SmallButton(stringResource(R.string.switch_voyage), onSwitch, Modifier.weight(1f), icon = Icons.Outlined.SwapHoriz)
        }
        SmallButton(stringResource(R.string.new_carnet), onNewVoyage, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add, outline = true)

        // .form : la carte blanche
        Column(
            Modifier.fillMaxWidth().pwaCard(RadiusLg).padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FormField(stringResource(R.string.etape_title_label), form.titre, onTitre, stringResource(R.string.etape_title_placeholder))
            FormField(stringResource(R.string.etape_description_label), form.description, onDescription, stringResource(R.string.etape_description_placeholder), minLines = 3)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(R.string.etape_photos_label))
                DashedZone {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton(stringResource(R.string.take_photo), Icons.Outlined.PhotoCamera, onClick = { cameraGate(onCamera) })
                        SoftButton(stringResource(R.string.from_gallery), Icons.Outlined.Image, onClick = onGallery)
                        Text(stringResource(R.string.photos_hint), fontSize = 12.sp, color = Gray500, textAlign = TextAlign.Center)
                    }
                }
                if (form.photos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        items(form.photos, key = { it }) { path ->
                            Box(Modifier.size(84.dp)) {
                                PhotoImage(path, maxPx = 300, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RadiusSm).border(1.dp, Gray200, RadiusSm))
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color(0xCC1B2618))
                                        .clickable { onRemovePhoto(path) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Default.Close, stringResource(R.string.remove), tint = Color.White, modifier = Modifier.size(13.dp)) }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Gray200)

            // .form__group--geo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(R.string.gps_label))
                FormField("", form.lat?.let { String.format(Locale.ROOT, "%.6f", it) } ?: "", {}, stringResource(R.string.latitude), readOnly = true, showLabel = false)
                FormField("", form.lng?.let { String.format(Locale.ROOT, "%.6f", it) } ?: "", {}, stringResource(R.string.longitude), readOnly = true, showLabel = false)
                SoftButton(stringResource(R.string.add_location), Icons.Outlined.NearMe, onClick = { locationGate(onLocate) }, background = Green50)
                when (geo) {
                    GeoStatus.Idle -> Unit
                    GeoStatus.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Green600)
                        Text(stringResource(R.string.geo_searching), fontSize = 13.sp, color = Gray500, modifier = Modifier.padding(start = 8.dp))
                    }
                    is GeoStatus.Found -> Text(
                        stringResource(
                            R.string.geo_found, String.format(Locale.ROOT, "%.4f, %.4f", geo.lat, geo.lng), geo.accuracy,
                            stringResource(if (geo.precise) R.string.location_precise else R.string.location_approximate),
                        ),
                        fontSize = 13.sp, color = Green700, fontWeight = FontWeight.Medium,
                    )
                    GeoStatus.Unavailable -> Text(stringResource(R.string.geo_unavailable), fontSize = 13.sp, color = Danger)
                }
            }

            PrimaryButton(stringResource(R.string.save_etape_arrow), onSave, enabled = form.titre.isNotBlank() && form.description.isNotBlank())
        }
    }
}

// ======================== 2. La timeline (section--journal) ========================

private val dateFr = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)

/** .journal-entry : la pastille numérotée sur le trait vertical, puis la carte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineEntry(number: Int, etape: Etape, last: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        // .journal-list::before (le trait) + .journal-entry__dot (la pastille)
        Box(Modifier.width(36.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = 15.dp.toPx()
                drawLine(Green200, Offset(x, if (number == 1) 24.dp.toPx() else 0f), Offset(x, if (last) 28.dp.toPx() else size.height), strokeWidth = 2.dp.toPx())
            }
            Box(
                Modifier.padding(top = 14.dp).size(30.dp).clip(CircleShape).background(Green300).padding(1.dp)
                    .clip(CircleShape).background(Cream50).padding(3.dp).clip(CircleShape).background(Green600),
                contentAlignment = Alignment.Center,
            ) { Text("$number", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier.weight(1f).padding(bottom = if (last) 0.dp else 24.dp)
                .pwaCard().clickable(onClick = onClick).padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(etape.titre, fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp, color = Green900, modifier = Modifier.weight(1f))
                Text(dateFr.format(Date(etape.date)), fontSize = 12.sp, color = Gray400, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
            }
            Text(etape.description, fontSize = 14.5.sp, lineHeight = 23.sp, color = Gray700, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            if (etape.photos.isNotEmpty()) {
                // .journal-entry__photos : des vignettes de 90 px de haut
                etape.photos.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { p -> PhotoImage(p, maxPx = 400, contentDescription = null, modifier = Modifier.weight(1f).height(90.dp).clip(RadiusSm).border(1.dp, Gray200, RadiusSm)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider(color = Gray200)
            // .journal-entry__footer : GPS · statut · Supprimer
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (etape.lat != null && etape.lng != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, tint = Green700, modifier = Modifier.size(13.dp))
                        Text(String.format(Locale.ROOT, "%.4f, %.4f", etape.lat, etape.lng), fontSize = 12.5.sp, color = Green700, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                SyncPill(etape.synced)
                Row(
                    Modifier.clip(RadiusSm).clickable(onClick = onDelete).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = Gray400, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.delete), fontSize = 12.5.sp, color = Gray400, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** .journal-entry__status--synced / --pending */
@Composable
fun SyncPill(synced: Boolean) {
    if (synced) Pill(stringResource(R.string.synced), Icons.Outlined.Check, Green100, Green700)
    else Pill(stringResource(R.string.pending_sync), Icons.Outlined.Schedule, AmberBg, AmberText)
}

// ======================== 3. Mes carnets de voyage (section--voyages) ========================

@Composable
fun VoyagesHeader(onCreate: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(stringResource(R.string.voyages_label), stringResource(R.string.voyages_title), Icons.AutoMirrored.Outlined.MenuBook)
        SmallButton(stringResource(R.string.create_carnet), onCreate, icon = Icons.Outlined.Add, outline = true)
    }
}

/** .voyage-card : la photo vitrine (ou la boussole), le badge « Carnet actif », les actions. */
@Composable
fun VoyageCard(voyage: Voyage, active: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().pwaCard(borderColor = if (active) Green600 else Gray200, borderWidth = if (active) 1.5f else 1f)) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            if (voyage.coverPath != null) {
                PhotoImage(voyage.coverPath, maxPx = 1080, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                // .destination-card__media--placeholder
                Column(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Green100, Green200))),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.Explore, null, tint = Green700, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.carnet_placeholder), color = Green700, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (active) {
                Row(
                    Modifier.padding(10.dp).clip(CircleShape).background(Green700).padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.carnet_active).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(voyage.titre, fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Green900)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = Green700, modifier = Modifier.size(14.dp))
                Text(
                    pluralStringResource(R.plurals.etapes_count, voyage.etapesCount, voyage.etapesCount),
                    fontSize = 12.5.sp, color = Green700, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp),
                )
            }
            Text(voyage.description, fontSize = 13.5.sp, lineHeight = 20.sp, color = Gray500)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (active) SmallButton(stringResource(R.string.carnet_open_current), {}, outline = true, enabled = false)
                else SmallButton(stringResource(R.string.carnet_open), onOpen, filled = true)
                SmallButton(stringResource(R.string.edit), onEdit, icon = Icons.Outlined.Edit, outline = true)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_voyage), tint = Gray400) }
            }
        }
    }
}

// ======================== 4. Destinations inspirantes (section--destinations) ========================

@Composable
fun DestinationCard(d: Destination, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().pwaCard()) {
        AssetImage(d.image, contentDescription = d.nom, modifier = Modifier.fillMaxWidth().height(170.dp))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.nom, fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Green900)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, tint = Green600, modifier = Modifier.size(13.dp))
                Text(d.lieu, fontSize = 12.5.sp, color = Green600, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
            Text(d.description, fontSize = 14.sp, lineHeight = 21.sp, color = Gray500, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
