package ph.edu.asiatech.goananas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LeafGreen = Color(0xFF1E5631)
val FreshGreen = Color(0xFF4C9A2A)
val PineGold = Color(0xFFF4B400)
val SoftGold = Color(0xFFFFF4D6)
val AlertRed = Color(0xFFC62828)
val SoftRed = Color(0xFFFDECEA)
val SoftGreen = Color(0xFFE6F2E0)
val Paper = Color(0xFFF6F8F2)

private val Colors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Color.White,
    primaryContainer = SoftGreen,
    onPrimaryContainer = LeafGreen,
    secondary = PineGold,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = SoftGold,
    error = AlertRed,
    errorContainer = SoftRed,
    background = Paper,
    surface = Color.White,
)

@Composable
fun GoAnanasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
