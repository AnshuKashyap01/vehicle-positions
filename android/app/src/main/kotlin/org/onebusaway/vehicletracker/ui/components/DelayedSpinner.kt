package org.onebusaway.vehicletracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal const val SPINNER_DELAY_MS = 200L

/**
 * A setup screen's loading spinner, shown only once a load has taken [SPINNER_DELAY_MS]: a fast load then goes
 * straight to its content instead of flashing a spinner on the way.
 */
@Composable
internal fun DelayedSpinner(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MS)
        shown = true
    }
    Box(modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
        if (shown) CircularProgressIndicator()
    }
}
