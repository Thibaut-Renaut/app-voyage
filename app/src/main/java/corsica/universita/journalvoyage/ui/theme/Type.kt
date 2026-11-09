package corsica.universita.journalvoyage.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// La PWA titre en Fraunces (une serif) : ici la serif du système, sans télécharger de police.
private val base = Typography()
val JournalTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
)
