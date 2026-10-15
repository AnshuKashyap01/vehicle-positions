package org.onebusaway.vehicletracker.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.vehicletracker.ui.theme.DarkColors
import org.onebusaway.vehicletracker.ui.theme.LightColors
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The window background as committed, read from `src/main/res`: the window shows it before Compose
 * draws the first frame, so any colour but the theme's surface flashes on launch.
 */
class WindowColorsTest {
    private fun windowBackground(valuesDir: String): Color {
        val colors = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$valuesDir/colors.xml"))
            .getElementsByTagName("color")
        val hex = (0 until colors.length).map { colors.item(it) as Element }
            .single { it.getAttribute("name") == "window_background" }
            .textContent.trim()
        return Color(0xFF000000 or hex.removePrefix("#").toLong(radix = 16))
    }

    @Test fun `the window is the theme's surface by day and by night`() {
        assertEquals(LightColors.surface, windowBackground("values"))
        assertEquals(DarkColors.surface, windowBackground("values-night"))
    }
}
