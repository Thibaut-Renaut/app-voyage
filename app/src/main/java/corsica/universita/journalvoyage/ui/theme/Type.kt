package corsica.universita.journalvoyage.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import corsica.universita.journalvoyage.R

/*
 * Les deux polices de la PWA (Google Fonts), embarquées dans res/font (licence OFL, docs/licences/) :
 * pas de téléchargement au lancement, elles marchent hors ligne. Ce sont des polices « variables » :
 * un seul fichier, la graisse est choisie par FontVariation.
 */
private fun fraunces(weight: Int, italic: Boolean = false) = Font(
    if (italic) R.font.fraunces_italic else R.font.fraunces,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** --font-display : les titres. */
val Fraunces = FontFamily(fraunces(600), fraunces(700), fraunces(800), fraunces(600, italic = true), fraunces(700, italic = true))

/** --font-body : tout le reste. */
val PlusJakartaSans = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val base = Typography()

val JournalTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlusJakartaSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlusJakartaSans),
    bodySmall = base.bodySmall.copy(fontFamily = PlusJakartaSans),
    labelLarge = base.labelLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
)

/** Les petits libellés en capitales de la PWA (.section__label, .form__label). */
val SectionLabel = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 1.sp)
val FormLabel = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 0.7.sp)
