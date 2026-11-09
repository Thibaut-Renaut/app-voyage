package corsica.universita.journalvoyage.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.ui.common.DangerButton
import corsica.universita.journalvoyage.ui.common.DashedZone
import corsica.universita.journalvoyage.ui.common.FieldLabel
import corsica.universita.journalvoyage.ui.common.FormField
import corsica.universita.journalvoyage.ui.common.ModalCard
import corsica.universita.journalvoyage.ui.common.PhotoImage
import corsica.universita.journalvoyage.ui.common.PrimaryButton
import corsica.universita.journalvoyage.ui.common.SmallButton
import corsica.universita.journalvoyage.ui.common.SoftButton
import corsica.universita.journalvoyage.ui.theme.Cream50
import corsica.universita.journalvoyage.ui.theme.Danger
import corsica.universita.journalvoyage.ui.theme.Gray200
import corsica.universita.journalvoyage.ui.theme.Gray500
import corsica.universita.journalvoyage.ui.theme.Green100
import corsica.universita.journalvoyage.ui.theme.Green500
import corsica.universita.journalvoyage.ui.theme.Green900
import corsica.universita.journalvoyage.ui.theme.RadiusMd
import corsica.universita.journalvoyage.ui.theme.RadiusSm

/** Les modales de la PWA : #modal-create-voyage, #modal-edit-voyage, #modal-delete-voyage, #modal-switch-voyage. */
@Composable
fun VoyageDialogs(viewModel: HomeViewModel, dialog: VoyageDialog) {
    when (dialog) {
        VoyageDialog.None -> Unit
        VoyageDialog.Create -> VoyageFormDialog(viewModel, editing = false)
        VoyageDialog.Edit -> VoyageFormDialog(viewModel, editing = true)
        VoyageDialog.Delete -> DeleteDialog(viewModel)
        VoyageDialog.Switch -> SwitchDialog(viewModel)
    }
}

@Composable
private fun VoyageFormDialog(viewModel: HomeViewModel, editing: Boolean) {
    val form by viewModel.voyageForm.collectAsStateWithLifecycle()
    val editingId by viewModel.dialogVoyageId.collectAsStateWithLifecycle()
    // Le sélecteur de photos du système : aucune permission.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setCover) }

    ModalCard(
        badge = stringResource(if (editing) R.string.modal_edit_badge else R.string.modal_create_badge),
        title = stringResource(if (editing) R.string.modal_edit_title else R.string.modal_create_title),
        subtitle = stringResource(if (editing) R.string.modal_edit_subtitle else R.string.modal_create_subtitle),
        onDismiss = viewModel::closeDialog,
    ) {
        FormField(stringResource(R.string.voyage_title), form.titre, viewModel::setVoyageTitre, stringResource(if (editing) R.string.voyage_title_placeholder_edit else R.string.voyage_title_placeholder))
        FormField(stringResource(R.string.voyage_description), form.description, viewModel::setVoyageDescription, stringResource(if (editing) R.string.voyage_description_placeholder_edit else R.string.voyage_description_placeholder), minLines = 2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.cover))
            DashedZone {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(
                        stringResource(if (editing) R.string.cover_change else R.string.cover_choose), Icons.Outlined.Image,
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                    Text(stringResource(if (editing) R.string.cover_hint_edit else R.string.cover_hint), fontSize = 12.sp, color = Gray500, textAlign = TextAlign.Center)
                }
            }
            form.coverPath?.let { path ->
                PhotoImage(path, maxPx = 800, contentDescription = stringResource(R.string.cover), modifier = Modifier.fillMaxWidth().height(150.dp).clip(RadiusMd))
                if (editing) {
                    Text(
                        stringResource(R.string.cover_remove), color = Gray500, fontSize = 13.sp,
                        modifier = Modifier.clip(RadiusSm).clickable(onClick = viewModel::removeCover).padding(vertical = 6.dp),
                    )
                }
            }
        }
        if (editing) {
            Row(
                Modifier.clip(RadiusSm).clickable { editingId?.let(viewModel::askDelete) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = Danger, modifier = Modifier.size(15.dp))
                Text(stringResource(R.string.delete_this_carnet), color = Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallButton(stringResource(R.string.cancel), viewModel::closeDialog, outline = true)
            PrimaryButton(
                stringResource(if (editing) R.string.modal_edit_submit else R.string.modal_create_submit),
                if (editing) viewModel::submitEdit else viewModel::submitCreate,
                Modifier.weight(1f),
                enabled = form.titre.isNotBlank() && form.description.isNotBlank(),
            )
        }
    }
}

@Composable
private fun DeleteDialog(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val id by viewModel.dialogVoyageId.collectAsStateWithLifecycle()
    val titre = state.voyages.firstOrNull { it.id == id }?.titre.orEmpty()
    ModalCard(
        badge = stringResource(R.string.modal_delete_badge),
        title = stringResource(R.string.modal_delete_title),
        subtitle = stringResource(R.string.modal_delete_text, titre),
        onDismiss = viewModel::closeDialog,
        danger = true,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            SmallButton(stringResource(R.string.cancel), viewModel::closeDialog, outline = true)
            DangerButton(stringResource(R.string.modal_delete_confirm), viewModel::confirmDelete)
        }
    }
}

@Composable
private fun SwitchDialog(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalCard(
        badge = stringResource(R.string.modal_switch_badge),
        title = stringResource(R.string.modal_switch_title),
        subtitle = stringResource(R.string.modal_switch_subtitle),
        onDismiss = viewModel::closeDialog,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.voyages.forEach { v ->
                val current = v.id == state.active?.id
                // .switch-voyage-item (.is-selected pour le carnet actif)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RadiusMd)
                        .background(if (current) Green100 else Cream50)
                        .border(if (current) 1.5.dp else 1.dp, if (current) Green500 else Gray200, RadiusMd)
                        .clickable { viewModel.openVoyage(v.id); viewModel.closeDialog() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhotoImage(v.coverPath, maxPx = 200, contentDescription = null, modifier = Modifier.size(48.dp).clip(RadiusSm))
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(v.titre, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Green900)
                        Text(
                            pluralStringResource(R.plurals.etapes_count, v.etapesCount, v.etapesCount) + if (current) " · " + stringResource(R.string.active_voyage) else "",
                            fontSize = 12.5.sp, color = Gray500,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SmallButton(stringResource(R.string.switch_create_new), viewModel::openCreate, icon = Icons.Outlined.Add)
        }
    }
}
