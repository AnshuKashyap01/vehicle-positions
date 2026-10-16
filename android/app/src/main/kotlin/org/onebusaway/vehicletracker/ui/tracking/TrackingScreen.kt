package org.onebusaway.vehicletracker.ui.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

/** The gap between the screen's edges and the status card floating over the map. */
private val CardMargin = 12.dp

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
        map = { modifier, contentPadding ->
            // With no geometry there is no route to draw, and the status card already says so.
            val geometry = state.tracking.geometry
            if (mapSupported && geometry != null) {
                RouteMap(geometry, state.tracking.adherence, state.mapFile, contentPadding, modifier)
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
    // OpenGL behind it. It fills the screen, and its padding says how much of it the status card
    // and the footer cover.
    map: @Composable (Modifier, PaddingValues) -> Unit = { modifier, _ -> Spacer(modifier) },
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var cardHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val statusInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val mapPadding = with(density) {
        PaddingValues(top = statusInsets.getTop(density).toDp() + CardMargin + cardHeight.toDp(), bottom = footerHeight.toDp())
    }

    // Unlike the other screens, Tracking draws under the system bars: the map fills the whole
    // screen, and the status card and the footer float over it, each clear of its bar.
    Box(modifier = Modifier.fillMaxSize()) {
        map(Modifier.fillMaxSize(), mapPadding)
        StatusArea(
            model = statusCardModel(state.tracking, state.activeTrip?.vehicleId),
            problem = state.tracking.problem,
            onSignIn = onReauthClick,
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(statusInsets).padding(CardMargin),
            onCardHeightChanged = { cardHeight = it },
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { footerHeight = it.height },
        ) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                state.activeTrip?.let { trip ->
                    Text(
                        text = stringResource(R.string.tracking_route_label, trip.routeId),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                }
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
