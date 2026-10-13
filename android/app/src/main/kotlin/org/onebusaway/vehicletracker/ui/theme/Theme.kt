package org.onebusaway.vehicletracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

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
