package om.mgtrener.mgym.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Lime = Color(0xFFD2FA69)
val Steel = Color(0xFF93A9BE)
private val GymColors = darkColorScheme(
    primary = Lime, onPrimary = Color(0xFF182008),
    secondary = Steel, background = Color(0xFF0C0F12),
    secondaryContainer = Color(0xFF334125), onSecondaryContainer = Lime,
    surfaceContainer = Color(0xFF151A20), surfaceContainerLow = Color(0xFF151A20),
    surfaceContainerHigh = Color(0xFF222A33), surfaceContainerHighest = Color(0xFF29333D),
    surface = Color(0xFF151A20), surfaceVariant = Color(0xFF222A33),
    onSurface = Color(0xFFF1F4F7), onBackground = Color(0xFFF1F4F7),
    onSurfaceVariant = Color(0xFFA4AFBC), outline = Color(0xFF39434F)
)
@Composable
fun MGymTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GymColors, typography = Typography, shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp)), content = content)
}
