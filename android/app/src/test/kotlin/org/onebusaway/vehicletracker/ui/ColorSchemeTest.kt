package org.onebusaway.vehicletracker.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.vehicletracker.ui.theme.DarkColors
import org.onebusaway.vehicletracker.ui.theme.LightColors

/**
 * Every role of both schemes is set: a role left to its default is Material's purple baseline, and
 * shows only on whichever component happens to use it.
 */
class ColorSchemeTest {
    // White and black are the same in any palette, so these roles match the baseline on purpose.
    private val sameAsBaseline = mapOf(
        "light" to setOf("onPrimary", "onSecondary", "onTertiary", "onError", "surfaceContainerLowest", "scrim"),
        "dark" to setOf("scrim"),
    )

    /**
     * Every colour role, read off the scheme's getters so a role a later material3 adds is checked too.
     * `Color` is a value class, so each getter returns the packed `Long` under a mangled name (`getPrimary-0d7_KjU`).
     */
    private fun roles(scheme: ColorScheme): Map<String, Long> =
        ColorScheme::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
            .associate { it.name.removePrefix("get").substringBefore('-').replaceFirstChar(Char::lowercaseChar) to it.invoke(scheme) as Long }

    @Test fun `reflection finds all 48 colour roles of material3 1_4`() {
        val roles = roles(LightColors)
        assertEquals(roles.keys.toString(), 48, roles.size)
        assertTrue(roles.keys.containsAll(listOf("primary", "surfaceContainerHighest", "onTertiaryFixedVariant")))
    }

    @Test fun `no role is left on Material's baseline`() {
        for ((name, ours, baseline) in listOf(
            Triple("light", LightColors, lightColorScheme()),
            Triple("dark", DarkColors, darkColorScheme()),
        )) {
            val baselineRoles = roles(baseline)
            val leaked = roles(ours).filter { (role, value) -> value == baselineRoles[role] }.keys - sameAsBaseline.getValue(name)
            assertTrue("$name roles still on the baseline: $leaked", leaked.isEmpty())
        }
    }
}
