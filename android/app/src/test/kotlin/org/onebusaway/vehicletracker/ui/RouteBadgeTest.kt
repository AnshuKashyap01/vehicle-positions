package org.onebusaway.vehicletracker.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.vehicletracker.ui.routes.RouteBadgeModel
import org.onebusaway.vehicletracker.ui.routes.routeColorArgb

class RouteBadgeTest {
    @Test fun `a route colour is read as opaque ARGB`() {
        assertEquals(0xFF0077C0L, routeColorArgb("0077C0"))
        // GTFS omits the '#', but a feed that includes one still means the same colour.
        assertEquals(0xFF0077C0L, routeColorArgb("#0077C0"))
        assertEquals(0xFF000000L, routeColorArgb("000000"))
    }

    @Test fun `a missing or unreadable colour leaves the badge to the theme`() {
        // route_color is optional in GTFS, and the server passes "" through as "".
        assertNull(routeColorArgb(""))
        assertNull(routeColorArgb("0077C"))
        assertNull(routeColorArgb("ZZZZZZ"))
    }

    @Test fun `a badge model reads both of the feed's colours once`() {
        val model = RouteBadgeModel.of("E Line", "9C182F", "FFFFFF")
        assertEquals(RouteBadgeModel("E Line", Color(0xFF9C182F), Color(0xFFFFFFFF)), model)
        assertEquals(RouteBadgeModel("7", null, null), RouteBadgeModel.of("7", "", "nope"))
    }
}
