package org.onebusaway.vehicletracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The banner paints these behind white text and the adherence panel draws them as text on the
// light and the dark surface alike, so each is a mid-tone that reads in both themes.
val StatusGreen = Color(0xFF1B873B)
val StatusRed = Color(0xFFC62828)
val StatusBlue = Color(0xFF1565C0)
val StatusGrey = Color(0xFF757575)

// The design's text fields have 8 dp corners; no other component here uses extraSmall.
private val AppShapes = Shapes(extraSmall = RoundedCornerShape(8.dp))

private val LocalStatusColors = staticCompositionLocalOf<StatusColors> { error("StatusColors read outside AppTheme") }
private val LocalProblemColors = staticCompositionLocalOf<ProblemColors> { error("ProblemColors read outside AppTheme") }

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalProblemColors provides if (darkTheme) DarkProblemColors else LightProblemColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** The theme's colours that [MaterialTheme] has no role for. */
object AppTheme {
    val statusColors: StatusColors
        @Composable @ReadOnlyComposable
        get() = LocalStatusColors.current

    val problemColors: ProblemColors
        @Composable @ReadOnlyComposable
        get() = LocalProblemColors.current
}
