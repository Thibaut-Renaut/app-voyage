package corsica.universita.journalvoyage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green600,
    onPrimary = Cream50,
    primaryContainer = Green200,
    onPrimaryContainer = Green900,
    secondary = Green500,
    onSecondary = Cream50,
    secondaryContainer = Green50,
    onSecondaryContainer = Green900,
    background = Cream50,
    onBackground = Gray900,
    surface = Cream50,
    onSurface = Gray900,
    surfaceVariant = Cream100,
    onSurfaceVariant = Gray500,
    outlineVariant = Gray200,
    // Les cartes Material 3 prennent surfaceContainerHighest : blanches, comme les cartes de la PWA.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Cream100,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Green300,
    onPrimary = Green900,
    primaryContainer = Green800,
    onPrimaryContainer = Green50,
    secondary = Green200,
    onSecondary = Green900,
    secondaryContainer = Green800,
    onSecondaryContainer = Green50,
    background = Green900,
    onBackground = Cream50,
    surface = Green900,
    onSurface = Cream50,
    surfaceContainerHighest = Green800,
    surfaceContainerHigh = Green800,
    surfaceContainer = Green800,
)

/** Thème Material 3 aux couleurs de la PWA : mode sombre suivi automatiquement. */
@Composable
fun JournalTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = JournalTypography, content = content)
}
