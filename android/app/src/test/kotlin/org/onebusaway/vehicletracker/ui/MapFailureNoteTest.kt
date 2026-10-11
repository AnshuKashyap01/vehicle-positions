package org.onebusaway.vehicletracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.vehicletracker.R
import org.onebusaway.vehicletracker.data.map.MapFailure
import org.onebusaway.vehicletracker.ui.map.mapFailureNote

class MapFailureNoteTest {
    // The repository leaves a file that is not a map alone for a day, so "will retry" would make
    // a wrong URL look like a dropped connection.
    @Test fun `a file that is not a map says it is not usable`() {
        assertEquals(R.string.tracking_map_unusable, mapFailureNote(MapFailure.INVALID_FILE))
    }

    @Test fun `only a failure that can pass by itself says it will retry`() {
        val retrying = MapFailure.entries.filter { mapFailureNote(it) == R.string.tracking_map_retrying }

        assertEquals(setOf(MapFailure.NETWORK, MapFailure.UNEXPECTED), retrying.toSet())
    }
}
