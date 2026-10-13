package org.onebusaway.vehicletracker.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.vehicletracker.ui.theme.DarkColors
import org.onebusaway.vehicletracker.ui.theme.DarkProblemColors
import org.onebusaway.vehicletracker.ui.theme.DarkStatusColors
import org.onebusaway.vehicletracker.ui.theme.LightColors
import org.onebusaway.vehicletracker.ui.theme.LightProblemColors
import org.onebusaway.vehicletracker.ui.theme.LightStatusColors
import org.onebusaway.vehicletracker.ui.theme.ProblemColors
import org.onebusaway.vehicletracker.ui.theme.StatusColors
import org.onebusaway.vehicletracker.ui.theme.StatusTone

/**
 * WCAG contrast for the pairs the theme promises, in both themes: 4.5:1 for text, and 7:1 for white
 * on a status fill, which the driver reads at a glance in sun.
 */
class ThemeContrastTest {
    private class Theme(val name: String, val scheme: ColorScheme, val status: StatusColors, val problems: ProblemColors)

    private val themes = listOf(
        Theme("light", LightColors, LightStatusColors, LightProblemColors),
        Theme("dark", DarkColors, DarkStatusColors, DarkProblemColors),
    )

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertContrast(minimum: Float, pair: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("$pair is ${"%.2f".format(ratio)}:1, below $minimum:1", ratio >= minimum)
    }

    @Test fun `ink reads on the surface`() {
        for (theme in themes) {
            val s = theme.scheme
            assertContrast(4.5f, "${theme.name} onSurface", s.onSurface, s.surface)
            assertContrast(4.5f, "${theme.name} onSurfaceVariant", s.onSurfaceVariant, s.surface)
        }
    }

    @Test fun `every on colour reads on its colour and its container`() {
        for (theme in themes) {
            val s = theme.scheme
            assertContrast(4.5f, "${theme.name} onPrimary", s.onPrimary, s.primary)
            assertContrast(4.5f, "${theme.name} onSecondary", s.onSecondary, s.secondary)
            assertContrast(4.5f, "${theme.name} onTertiary", s.onTertiary, s.tertiary)
            assertContrast(4.5f, "${theme.name} onError", s.onError, s.error)
            assertContrast(4.5f, "${theme.name} onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer)
            assertContrast(4.5f, "${theme.name} onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
            assertContrast(4.5f, "${theme.name} onTertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
            assertContrast(4.5f, "${theme.name} onErrorContainer", s.onErrorContainer, s.errorContainer)
        }
    }

    @Test fun `status text reads on the surface and on a container`() {
        for (theme in themes) {
            for (tone in StatusTone.entries) {
                val text = theme.status[tone].text
                assertContrast(4.5f, "${theme.name} $tone text on surface", text, theme.scheme.surface)
                assertContrast(4.5f, "${theme.name} $tone text on surfaceContainer", text, theme.scheme.surfaceContainer)
            }
        }
    }

    @Test fun `white on a status fill reads at a glance`() {
        for (theme in themes) {
            for (tone in StatusTone.entries) {
                val status = theme.status[tone]
                assertContrast(7f, "${theme.name} $tone onFill", status.onFill, status.fill)
            }
        }
    }

    @Test fun `a problem reads on its container`() {
        for (theme in themes) {
            assertContrast(4.5f, "${theme.name} problem content", theme.problems.content, theme.problems.container)
        }
    }
}
