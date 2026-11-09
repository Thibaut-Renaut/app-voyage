package corsica.universita.journalvoyage.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import corsica.universita.journalvoyage.ui.theme.Cream50
import corsica.universita.journalvoyage.ui.theme.Danger
import corsica.universita.journalvoyage.ui.theme.FormLabel
import corsica.universita.journalvoyage.ui.theme.Fraunces
import corsica.universita.journalvoyage.ui.theme.Gray200
import corsica.universita.journalvoyage.ui.theme.Gray400
import corsica.universita.journalvoyage.ui.theme.Gray500
import corsica.universita.journalvoyage.ui.theme.Gray700
import corsica.universita.journalvoyage.ui.theme.Green100
import corsica.universita.journalvoyage.ui.theme.Green200
import corsica.universita.journalvoyage.ui.theme.Green300
import corsica.universita.journalvoyage.ui.theme.Green50
import corsica.universita.journalvoyage.ui.theme.Green500
import corsica.universita.journalvoyage.ui.theme.Green600
import corsica.universita.journalvoyage.ui.theme.Green700
import corsica.universita.journalvoyage.ui.theme.Green800
import corsica.universita.journalvoyage.ui.theme.Green900
import corsica.universita.journalvoyage.ui.theme.PlusJakartaSans
import corsica.universita.journalvoyage.ui.theme.RadiusLg
import corsica.universita.journalvoyage.ui.theme.RadiusMd
import corsica.universita.journalvoyage.ui.theme.RadiusSm
import corsica.universita.journalvoyage.ui.theme.SectionLabel

/*
 * Les composants de la PWA, un par classe CSS : même rendu sur le téléphone que dans le navigateur.
 */

/** .section__label + .section__title (avec son icône). */
@Composable
fun SectionHeader(label: String, title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = SectionLabel, color = Green600)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Icon(icon, contentDescription = null, tint = Green900, modifier = Modifier.size(22.dp))
            Text(title, fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Green900, lineHeight = 30.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** .btn--primary : vert plein, toute la largeur. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, shape = RadiusMd,
        colors = ButtonDefaults.buttonColors(containerColor = Green600, contentColor = Color.White, disabledContainerColor = Green300, disabledContentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

/** .btn--secondary (vert pâle) ou .btn--outline (contour), en taille .btn--sm. */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    outline: Boolean = false,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(15.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
    }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    when {
        filled -> Button(
            onClick = onClick, enabled = enabled, shape = RadiusSm, contentPadding = padding, modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Green600, contentColor = Color.White),
            content = content,
        )
        outline -> OutlinedButton(
            onClick = onClick, enabled = enabled, shape = RadiusSm, contentPadding = padding, modifier = modifier,
            border = BorderStroke(1.dp, if (enabled) Green300 else Gray200),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Green700, disabledContentColor = Gray500),
            content = content,
        )
        else -> OutlinedButton(
            onClick = onClick, enabled = enabled, shape = RadiusSm, contentPadding = padding, modifier = modifier,
            border = BorderStroke(1.dp, Green200),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Green100, contentColor = Green800),
            content = content,
        )
    }
}

/** .photo-upload-btn et .btn--geo : blanc ou vert très pâle, bordure verte, icône + texte centrés. */
@Composable
fun SoftButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = Color.White) {
    OutlinedButton(
        onClick = onClick, shape = RadiusSm, border = BorderStroke(1.dp, Green200),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = background, contentColor = Green700),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** .form__group : le libellé en capitales, puis le champ (.form__input / .form__textarea). */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    readOnly: Boolean = false,
    showLabel: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLabel) Text(label.uppercase(), style = FormLabel, color = Gray700)
        OutlinedTextField(
            value = value, onValueChange = onValueChange, readOnly = readOnly,
            placeholder = { Text(placeholder, color = Gray400, fontSize = 15.sp) },
            singleLine = minLines == 1, minLines = minLines,
            shape = RadiusSm,
            textStyle = TextStyle(fontFamily = PlusJakartaSans, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = if (readOnly) Color(0xFFF0F0ED) else Cream50,
                focusedContainerColor = Color.White,
                unfocusedBorderColor = Gray200,
                focusedBorderColor = Green500,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** .form__label seul. */
@Composable
fun FieldLabel(text: String) = Text(text.uppercase(), style = FormLabel, color = Gray700)

/** .photo-upload-zone : la zone en pointillés vert pâle. */
@Composable
fun DashedZone(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RadiusMd)
            .background(Green50)
            .drawBehind {
                drawRoundRect(
                    color = Green300,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
            .padding(18.dp),
    ) { content() }
}

/** .modal-card : la fenêtre de la PWA (pastille, titre Fraunces, sous-titre, croix de fermeture). */
@Composable
fun ModalCard(
    badge: String,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RadiusLg)
                    .background(Color.White)
                    .border(1.dp, Green200, RadiusLg)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(
                        badge.uppercase(), style = SectionLabel.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                        color = if (danger) Danger else Green700,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (danger) Color(0xFFFEE2E2) else Green100)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                    Text(title, fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Green900, modifier = Modifier.padding(top = 8.dp))
                    Text(subtitle, fontSize = 14.sp, color = Gray500, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                }
                content()
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Gray500)
            }
        }
    }
}

/** .btn--danger : rouge plein. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, shape = RadiusSm, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

/** .journal-entry__status : la pastille « Synchronisé » / « En attente de sync ». */
@Composable
fun Pill(text: String, icon: ImageVector, background: Color, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(background).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    }
}

/** .card : fond blanc, bordure grise, rayon md. */
fun Modifier.pwaCard(radius: Shape = RadiusMd, borderColor: Color = Gray200, borderWidth: Float = 1f) =
    this.clip(radius).background(Color.White).border(borderWidth.dp, borderColor, radius)
