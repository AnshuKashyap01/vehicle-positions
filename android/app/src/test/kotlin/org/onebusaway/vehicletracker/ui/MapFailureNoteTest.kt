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

    @Test fun `a URL the phone refuses asks for https`() {
        assertEquals(R.string.tracking_map_needs_https, mapFailureNote(MapFailure.CLEARTEXT_BLOCKED))
    }

    // An unexpected failure is not retried on a timer, only at the next refresh, and it happens on
    // the phone, so the note neither promises a retry soon nor sends the driver to the agency.
    @Test fun `an unexpected failure says it is tried again when the app is next opened`() {
        assertEquals(R.string.tracking_map_retry_on_open, mapFailureNote(MapFailure.UNEXPECTED))
    }

    @Test fun `only a failure retried on a timer says it will retry`() {
        val retrying = MapFailure.entries.filter { mapFailureNote(it) == R.string.tracking_map_retrying }

        assertEquals(setOf(MapFailure.NETWORK), retrying.toSet())
    }
}
