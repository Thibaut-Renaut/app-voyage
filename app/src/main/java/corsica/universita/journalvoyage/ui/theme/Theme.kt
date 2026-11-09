package corsica.universita.journalvoyage.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val RadiusSm = RoundedCornerShape(8.dp)
val RadiusMd = RoundedCornerShape(14.dp)
val RadiusLg = RoundedCornerShape(20.dp)

private val Colors = lightColorScheme(
    primary = Green600,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green800,
    secondary = Green500,
    onSecondary = Color.White,
    secondaryContainer = Green50,
    onSecondaryContainer = Green900,
    background = Cream50,
    onBackground = Gray900,
    surface = Cream50,
    onSurface = Gray900,
    surfaceVariant = Cream100,
    onSurfaceVariant = Gray500,
    outline = Gray300,
    outlineVariant = Gray200,
    error = Danger,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Cream100,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

@Composable
fun JournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = JournalTypography,
        shapes = Shapes(small = RadiusSm, medium = RadiusMd, large = RadiusLg),
        content = content,
    )
}
