package org.onebusaway.vehicletracker.ui.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.onebusaway.vehicletracker.R
import org.onebusaway.vehicletracker.ui.map.RouteMap
import org.onebusaway.vehicletracker.ui.map.glEsVersion
import org.onebusaway.vehicletracker.ui.map.supportsMap

@Composable
fun TrackingScreen(
    onTripEnded: () -> Unit,
    onReauthRequired: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenVisible(true)
        onStopOrDispose { viewModel.onScreenVisible(false) }
    }
    val context = LocalContext.current
    val mapSupported = remember { supportsMap(context.glEsVersion()) }
    TrackingScreenContent(
        state = state,
        onEndTripClick = { viewModel.onEndTrip(onTripEnded) },
        onEndTripLocallyClick = { viewModel.onEndTripLocally(onTripEnded) },
        onDismissError = viewModel::dismissEndTripError,
        onReauthClick = onReauthRequired,
        map = { modifier ->
            // With no geometry there is no route to draw, and the status card already says so.
            val geometry = state.tracking.geometry
            if (mapSupported && geometry != null) {
                RouteMap(geometry, state.tracking.adherence, state.mapFile, modifier)
            } else {
                Spacer(modifier)
            }
        },
    )
}

@Composable
fun TrackingScreenContent(
    state: TrackingUiState,
    onEndTripClick: () -> Unit,
    onEndTripLocallyClick: () -> Unit,
    onDismissError: () -> Unit,
    onReauthClick: () -> Unit,
    // The map is handed in so that this screen can be shown, in tests and previews, with no
    // OpenGL behind it. It is given the space between the status card and the footer.
    map: @Composable (Modifier) -> Unit = { modifier -> Spacer(modifier) },
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    // The order of iOS's TrackingView: status, the map taking what height is left, footer.
    Column(modifier = Modifier.fillMaxSize()) {
        StatusArea(
            model = statusCardModel(state.tracking, state.activeTrip?.vehicleId),
            problem = state.tracking.problem,
            onSignIn = onReauthClick,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        state.activeTrip?.let { trip ->
            Text(
                text = stringResource(R.string.tracking_route_label, trip.routeId),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        map(Modifier.weight(1f).fillMaxWidth())
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TripDuration(startedAtEpochSec = state.tracking.tripStartedAtEpochSec ?: state.activeTrip?.startedAtEpochSec)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tracking_fixes_sent_label, state.tracking.fixesSent),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showConfirmDialog = true },
                enabled = !state.ending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text(stringResource(R.string.tracking_end_trip_button))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.tracking_end_trip_dialog_title)) },
            text = { Text(stringResource(R.string.tracking_end_trip_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onEndTripClick()
                }) {
                    Text(stringResource(R.string.tracking_end_trip_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.tracking_end_trip_dialog_cancel))
                }
            },
        )
    }

    if (state.endTripError) {
        EndTripErrorDialog(onRetry = onEndTripClick, onEndLocally = onEndTripLocallyClick, onDismiss = onDismissError)
    }
}

/**
 * How long the trip has run, ticking once a second. The tick lives here rather than in the screen, so each
 * second recomposes this text alone and not the status card or the map.
 */
@Composable
private fun TripDuration(startedAtEpochSec: Long?) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtEpochSec) {
        if (startedAtEpochSec != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() / 1000) - startedAtEpochSec
                delay(1000)
            }
        }
    }
    Text(
        text = stringResource(R.string.tracking_duration_label, formatDuration(elapsedSeconds)),
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** The server did not confirm the end. Shared with the resume prompt, which ends trips through [TripEnder] too. */
@Composable
internal fun EndTripErrorDialog(onRetry: () -> Unit, onEndLocally: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracking_end_trip_error_title)) },
        text = { Text(stringResource(R.string.tracking_end_trip_error_message)) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.tracking_end_trip_error_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onEndLocally) {
                Text(stringResource(R.string.tracking_end_trip_error_end_locally))
            }
        },
    )
}
