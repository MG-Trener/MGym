package om.mgtrener.mgym.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = 0.sp
)
val Typography = Typography(
    displaySmall = type(28,32,FontWeight.Bold),
    headlineLarge = type(28,32,FontWeight.Bold),
    headlineMedium = type(23,28,FontWeight.Bold),
    headlineSmall = type(20,25,FontWeight.SemiBold),
    titleLarge = type(18,23,FontWeight.SemiBold),
    titleMedium = type(15,20,FontWeight.SemiBold),
    titleSmall = type(13,18,FontWeight.SemiBold),
    bodyLarge = type(14,20),
    bodyMedium = type(13,18),
    bodySmall = type(11,16),
    labelLarge = type(13,18,FontWeight.SemiBold),
    labelMedium = type(11,16,FontWeight.Medium),
    labelSmall = type(10,14,FontWeight.Medium)
)
