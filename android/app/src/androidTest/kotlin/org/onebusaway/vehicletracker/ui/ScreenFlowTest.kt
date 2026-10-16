package org.onebusaway.vehicletracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.vehicletracker.R
import org.onebusaway.vehicletracker.data.ActiveTrip
import org.onebusaway.vehicletracker.data.TrackingProblem
import org.onebusaway.vehicletracker.data.TrackingState
import org.onebusaway.vehicletracker.engine.AdherenceEvaluator
import org.onebusaway.vehicletracker.engine.AdherenceThresholds
import org.onebusaway.vehicletracker.engine.GeoPoint
import org.onebusaway.vehicletracker.engine.TripGeometry
import org.onebusaway.vehicletracker.engine.TripStop
import org.onebusaway.vehicletracker.service.LocationFix
import org.onebusaway.vehicletracker.ui.login.LoginScreenContent
import org.onebusaway.vehicletracker.ui.login.LoginUiState
import org.onebusaway.vehicletracker.ui.resume.ResumeShiftScreenContent
import org.onebusaway.vehicletracker.ui.resume.ResumeShiftUiState
import org.onebusaway.vehicletracker.ui.tracking.StatusArea
import org.onebusaway.vehicletracker.ui.tracking.TrackingScreenContent
import org.onebusaway.vehicletracker.ui.tracking.TrackingUiState
import org.onebusaway.vehicletracker.ui.tracking.statusCardModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Instrumented UI tests that render the stateless screen `Content` composables directly with
 * fake state and no-op callbacks — no Hilt/ViewModel wiring needed. Run with
 * `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun getString(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    @Test
    fun loginScreen_disablesButtonWhileLoading() {
        compose.setContent {
            LoginScreenContent(
                state = LoginUiState(loading = true),
                onServerUrlChange = {},
                onEmailChange = {},
                onPasswordChange = {},
                onLoginClick = {},
            )
        }

        compose.onNodeWithText(getString(R.string.login_button)).assertIsNotEnabled()
    }

    @Test
    fun trackingScreen_showsNoProblemLineWhileAllIsWell() {
        showTracking(TrackingState(active = true, problem = TrackingProblem.NONE, geometry = t1))

        for (title in listOf(R.string.tracking_problem_no_network, R.string.tracking_problem_no_gps, R.string.tracking_problem_clock_skew)) {
            compose.onNodeWithText(getString(title)).assertDoesNotExist()
        }
        compose.onNodeWithText(getString(R.string.tracking_signed_out_title)).assertDoesNotExist()
    }

    @Test
    fun trackingScreen_endTripShowsConfirmation() {
        compose.setContent {
            TrackingScreenContent(
                state = TrackingUiState(
                    tracking = TrackingState(active = true, problem = TrackingProblem.NONE),
                ),
                onEndTripClick = {},
                onEndTripLocallyClick = {},
                onDismissError = {},
                onReauthClick = {},
            )
        }

        compose.onNodeWithText(getString(R.string.tracking_end_trip_button)).performClick()

        compose.onNodeWithText(getString(R.string.tracking_end_trip_dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.tracking_end_trip_dialog_message)).assertIsDisplayed()
    }

    @Test
    fun resumeScreen_namesTheShiftAndOffersBothActions() {
        var resumed = false
        var ended = false
        compose.setContent {
            ResumeShiftScreenContent(
                state = ResumeShiftUiState(
                    trip = ActiveTrip(7L, "T1", "bus-1", "R1", "20260902", 100L),
                    startedAgoSec = 2 * 3600L + 5 * 60,
                ),
                onResumeClick = { resumed = true },
                onEndShiftClick = { ended = true },
                onEndShiftLocallyClick = {},
                onDismissError = {},
            )
        }

        compose.onNodeWithText(resources.getString(R.string.resume_message, "bus-1")).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.resume_route, "R1")).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.resume_started_hours_ago, 2, 5)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.resume_button)).performClick()
        compose.onNodeWithText(getString(R.string.resume_end_shift_button)).performClick()
        assertTrue(resumed)
        assertTrue(ended)
    }

    @Test
    fun resumeScreen_disablesBothActionsUntilTheTripLoads() {
        compose.setContent {
            ResumeShiftScreenContent(
                state = ResumeShiftUiState(trip = null),
                onResumeClick = {},
                onEndShiftClick = {},
                onEndShiftLocallyClick = {},
                onDismissError = {},
            )
        }

        compose.onNodeWithText(getString(R.string.resume_button)).assertIsNotEnabled()
        compose.onNodeWithText(getString(R.string.resume_end_shift_button)).assertIsNotEnabled()
    }

    @Test
    fun resumeScreen_scrollsToEndShiftAtALargeFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                // A short screen, so End Shift starts below the fold whatever the test device.
                Box(Modifier.height(360.dp)) {
                    ResumeShiftScreenContent(
                        state = ResumeShiftUiState(trip = ActiveTrip(7L, "T1", "bus-1", "R1", "20260902", 100L)),
                        onResumeClick = {},
                        onEndShiftClick = {},
                        onEndShiftLocallyClick = {},
                        onDismissError = {},
                    )
                }
            }
        }

        val endShift = compose.onNodeWithText(getString(R.string.resume_end_shift_button))
        endShift.assertIsNotDisplayed()
        endShift.performScrollTo().assertIsDisplayed()
    }

    // --- The status card, against the server fixture's trip T1 ---

    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val pacific = ZoneId.of("America/Los_Angeles")

    private fun at(hour: Int, minute: Int): Instant = ZonedDateTime.of(2026, 9, 2, hour, minute, 0, 0, pacific).toInstant()

    /** A straight 1 km run north with stops at 0, 500 and 1001 m, scheduled 08:00, 08:05, 08:10. */
    private val t1 = TripGeometry(
        tripId = "T1",
        serviceDate = "20260902",
        timezone = pacific,
        shapePoints = listOf(GeoPoint(47.6000, -122.3300), GeoPoint(47.6045, -122.3300), GeoPoint(47.6090, -122.3300)),
        stops = listOf(
            TripStop("ST1", "Stop ST1", 47.6000, -122.3300, 0.0, at(8, 0), at(8, 0)),
            TripStop("ST2", "Stop ST2", 47.6045, -122.3300, 500.0, at(8, 5), at(8, 5)),
            TripStop("ST3", "Stop ST3", 47.6090, -122.3300, 1001.0, at(8, 10), at(8, 10)),
        ),
        thresholds = AdherenceThresholds(maxShapeDistanceM = 60.0, scheduleEarlyS = 900, scheduleLateS = 5400),
    )

    private fun judged(lat: Double, lon: Double, time: Instant) = requireNotNull(AdherenceEvaluator.of(t1)).evaluate(
        LocationFix(lat, lon, bearing = 0.0, speed = 8.0, accuracy = 5.0, timeEpochSec = time.epochSecond),
        previous = null,
    )

    private fun showTracking(tracking: TrackingState, activeTrip: ActiveTrip? = null, onReauthClick: () -> Unit = {}) {
        compose.setContent {
            TrackingScreenContent(
                state = TrackingUiState(tracking = tracking, activeTrip = activeTrip),
                onEndTripClick = {},
                onEndTripLocallyClick = {},
                onDismissError = {},
                onReauthClick = onReauthClick,
            )
        }
    }

    private val announcedPolitely = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun trackingScreen_showsAdherenceAndTheNextStop() {
        showTracking(TrackingState(active = true, geometry = t1, adherence = judged(47.6045, -122.3300, at(8, 5))))

        compose.onNodeWithText(getString(R.string.tracking_adherence_on_time)).assertIsDisplayed().assert(announcedPolitely)
        compose.onNodeWithText(getString(R.string.tracking_adherence_next_stop)).assertIsDisplayed()
        compose.onNodeWithText("Stop ST3").assertIsDisplayed()
        // ~501 m to ST3, due at 08:10 on the agency's clock whatever the device's zone.
        compose.onNodeWithText(resources.getString(R.string.tracking_adherence_distance_m, 501), substring = true).assertIsDisplayed()
        compose.onNodeWithText("8:10", substring = true).assertIsDisplayed()
    }

    @Test
    fun trackingScreen_countsTheTripTimeFromItsStart() {
        val startedAt = System.currentTimeMillis() / 1000 - 65
        showTracking(TrackingState(active = true, tripStartedAtEpochSec = startedAt, geometry = t1))

        // 01:05, or a second or two more on a slow device.
        compose.onNodeWithText(resources.getString(R.string.tracking_duration_label, "01:0"), substring = true).assertIsDisplayed()
    }

    @Test
    fun trackingScreen_showsTheNextStopInKilometresPastOneKilometre() {
        // Without ST2, the next stop from ST1 is ST3, 1001 m on.
        val twoStops = t1.copy(stops = listOf(t1.stops[0], t1.stops[2]))
        val adherence = requireNotNull(AdherenceEvaluator.of(twoStops)).evaluate(
            LocationFix(47.6000, -122.3300, bearing = 0.0, speed = 8.0, accuracy = 5.0, timeEpochSec = at(8, 0).epochSecond),
            previous = null,
        )
        showTracking(TrackingState(active = true, geometry = twoStops, adherence = adherence))

        compose.onNodeWithText(resources.getString(R.string.tracking_adherence_distance_km, 1.0), substring = true).assertIsDisplayed()
    }

    @Test
    fun trackingScreen_showsWholeMinutesLate() {
        // 400 s behind ST2's schedule rounds to 7 minutes.
        showTracking(TrackingState(active = true, geometry = t1, adherence = judged(47.6045, -122.3300, at(8, 5).plusSeconds(400))))

        compose.onNodeWithText(resources.getQuantityString(R.plurals.tracking_adherence_minutes_late, 7, 7)).assertIsDisplayed()
    }

    @Test
    fun trackingScreen_showsOffScheduleWithTheDeviation() {
        showTracking(TrackingState(active = true, geometry = t1, adherence = judged(47.6000, -122.3300, at(8, 0).plusSeconds(6000))))

        compose.onNodeWithText(resources.getQuantityString(R.plurals.tracking_adherence_minutes_late, 100, 100)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.tracking_adherence_off_schedule)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.tracking_adherence_right_run)).assertIsDisplayed()
        compose.onNodeWithText("Stop ST2", substring = true).assertIsDisplayed()
    }

    @Test
    fun trackingScreen_showsOffRouteButAnnouncesItWithoutTheDistance() {
        // 0.017° of longitude east of the line is ~1.28 km.
        showTracking(TrackingState(active = true, geometry = t1, adherence = judged(47.6045, -122.3130, at(8, 5))))

        compose.onNodeWithText(getString(R.string.tracking_adherence_off_route_short)).assertIsDisplayed().assert(announcedPolitely)
        // Drawn on the card, but changing with every fix, so TalkBack is never handed it to read out.
        val distance = resources.getString(R.string.tracking_adherence_distance_km, 1.3)
        compose.onNodeWithText(resources.getString(R.string.tracking_adherence_off_route, distance)).assertDoesNotExist()
        compose.onNodeWithText("Stop ST3").assertIsDisplayed()
    }

    @Test
    fun trackingScreen_waitsForTheFirstFixFromTheFirstStop() {
        showTracking(TrackingState(active = true, geometry = t1), activeTrip = ActiveTrip(7L, "T1", "4317", "R1", "20260902", 100L))

        compose.onNodeWithText(getString(R.string.tracking_adherence_waiting_for_gps)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.tracking_adherence_from)).assertIsDisplayed()
        compose.onNodeWithText("Stop ST1").assertIsDisplayed()
        // "8:00 AM run · 4317", with the time in whatever style the device's locale writes it.
        compose.onNodeWithText(resources.getString(R.string.tracking_adherence_run_vehicle, "", "4317"), substring = true).assertIsDisplayed()
    }

    @Test
    fun trackingScreen_saysWhenThereIsNoScheduleToJudgeAgainst() {
        showTracking(TrackingState(active = true, geometry = null))

        compose.onNodeWithText(getString(R.string.tracking_adherence_no_schedule)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.tracking_adherence_no_schedule_riders)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.tracking_adherence_no_schedule_detail)).assertIsDisplayed()
    }

    @Test
    fun statusCard_keepsOneHeightInEveryState() {
        val states = listOf(
            TrackingState(active = true, geometry = t1, adherence = judged(47.6045, -122.3300, at(8, 5))),
            // The longest answer, which the card shrinks to fit on one line.
            TrackingState(active = true, geometry = t1, adherence = judged(47.6045, -122.3130, at(8, 5))),
            TrackingState(active = true, geometry = t1, adherence = judged(47.6000, -122.3300, at(8, 0).plusSeconds(6000))),
            TrackingState(active = true, geometry = t1),
            TrackingState(active = false, geometry = null),
            TrackingState(active = true, geometry = null),
        )
        var tracking by mutableStateOf(states.first())
        val heights = mutableSetOf<Int>()
        compose.setContent {
            Box(Modifier.onSizeChanged { heights += it.height }) {
                StatusArea(statusCardModel(tracking, vehicleId = "4317"), TrackingProblem.NONE, onSignIn = {})
            }
        }

        for (state in states) {
            tracking = state
            compose.waitForIdle()
        }

        // The map sits under the card, so a card that changed height would move the map with every status.
        assertEquals(1, heights.size)
    }

    // --- The problem line under the status card, and the Signed out card in its place ---

    private fun assertProblemLine(problem: TrackingProblem, title: Int, detail: Int) {
        showTracking(TrackingState(active = true, problem = problem, geometry = t1))

        compose.onNodeWithText(getString(title)).assertIsDisplayed().assert(announcedPolitely)
        compose.onNodeWithText(getString(detail)).assertIsDisplayed()
        // The status card stays above the line.
        compose.onNodeWithText(getString(R.string.tracking_adherence_waiting_for_gps)).assertIsDisplayed()
    }

    @Test
    fun trackingScreen_saysWhenThereIsNoConnection() =
        assertProblemLine(TrackingProblem.NO_NETWORK, R.string.tracking_problem_no_network, R.string.tracking_problem_no_network_detail)

    @Test
    fun trackingScreen_saysWhenThereIsNoGps() =
        assertProblemLine(TrackingProblem.NO_GPS, R.string.tracking_problem_no_gps, R.string.tracking_problem_no_gps_detail)

    @Test
    fun trackingScreen_saysWhenThePhoneClockIsWrong() =
        assertProblemLine(TrackingProblem.CLOCK_SKEW, R.string.tracking_problem_clock_skew, R.string.tracking_problem_clock_skew_detail)

    @Test
    fun trackingScreen_signedOutCardTakesTheStatusCardsPlace() {
        var signInClicked = false
        showTracking(TrackingState(active = true, problem = TrackingProblem.AUTH_EXPIRED, geometry = t1), onReauthClick = { signInClicked = true })

        compose.onNodeWithText(getString(R.string.tracking_signed_out_title))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(getString(R.string.tracking_signed_out_message)).assertIsDisplayed()
        compose.onNodeWithText(getString(R.string.tracking_adherence_waiting_for_gps)).assertDoesNotExist()
        compose.onNodeWithText(getString(R.string.tracking_reauth_button)).performClick()
        assertTrue(signInClicked)
    }
}
