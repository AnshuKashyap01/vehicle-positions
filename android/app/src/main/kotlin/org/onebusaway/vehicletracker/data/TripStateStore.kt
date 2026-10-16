package org.onebusaway.vehicletracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

data class ActiveTrip(
    val tripDbId: Long,
    /** GTFS trip_id the driver entered, or "" when they only know the route. Never a route id. */
    val gtfsTripId: String,
    val vehicleId: String,
    val routeId: String,
    /** Service date, YYYYMMDD, fixed when the trip started. */
    val startDate: String,
    val startedAtEpochSec: Long,
    /** The driver who started the trip ([Session.driverId]); null for a trip saved before drivers were recorded. */
    val driverId: String? = null,
)

/**
 * A trip ended on this phone only, whose end the server has not confirmed. [driverId] is the
 * driver who ended it ([Session.driverId]); null for one stored before drivers were recorded.
 */
data class UnconfirmedEnd(val tripDbId: Long, val driverId: String?) {
    /**
     * Only the driver who ended it can send its end: for anyone else the server answers 404, and
     * a 404 would clear it for good while it is still open. An end stored without a driver goes
     * out under whoever is signed in, as it did before drivers were recorded.
     */
    fun belongsTo(signedInDriverId: String?): Boolean = driverId == null || driverId == signedInDriverId
}

interface TripStateStore {
    val activeTrip: Flow<ActiveTrip?>
    val unconfirmedEnd: Flow<UnconfirmedEnd?>
    val recentRoutes: Flow<List<String>>
    suspend fun saveActiveTrip(trip: ActiveTrip)
    suspend fun clearActiveTrip()

    /**
     * Clears the active trip but keeps its server id and [driverId] as [unconfirmedEnd], in one
     * write so a crash between the two cannot lose it: the server keeps the trip open until it
     * hears the end, and refuses every start until then.
     */
    suspend fun endActiveTripLocally(driverId: String?)
    suspend fun clearUnconfirmedEnd()
    suspend fun addRecentRoute(routeId: String)
}

/** Internal, not private, only because [org.onebusaway.vehicletracker.di.AppModule] provides the store. */
internal val Context.tripStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "trip_state")

/**
 * Recovers the GTFS trip id from a trip saved by the build before gtfsTripId was split out. That
 * build stored `gtfsTripId.ifBlank { routeId }` under `trip_location_id`, so a value equal to the
 * stored route id means the driver entered no trip id; anything else is the id they typed.
 */
internal fun legacyGtfsTripId(legacy: String?, routeId: String): String =
    legacy?.takeIf { it != routeId }?.trim().orEmpty()

/**
 * Takes a `DataStore<Preferences>` rather than a `Context` so the upgrade path from the previous
 * build can be unit-tested without an emulator, the same trade [DataStoreVehiclePrefsStore] makes.
 */
class DataStoreTripStateStore(
    private val dataStore: DataStore<Preferences>,
    private val zone: ZoneId,
) : TripStateStore {
    private object Keys {
        val TRIP_DB_ID = longPreferencesKey("trip_db_id")
        val TRIP_GTFS_TRIP_ID = stringPreferencesKey("trip_gtfs_trip_id")
        val TRIP_VEHICLE_ID = stringPreferencesKey("trip_vehicle_id")
        val TRIP_ROUTE_ID = stringPreferencesKey("trip_route_id")
        val TRIP_START_DATE = stringPreferencesKey("trip_start_date")
        val TRIP_STARTED_AT = longPreferencesKey("trip_started_at")
        val TRIP_DRIVER_ID = stringPreferencesKey("trip_driver_id")
        val UNCONFIRMED_END_TRIP_DB_ID = longPreferencesKey("unconfirmed_end_trip_db_id")
        val UNCONFIRMED_END_DRIVER_ID = stringPreferencesKey("unconfirmed_end_driver_id")
        val RECENT_ROUTES = stringPreferencesKey("recent_routes")

        /** Written only by the build before the split; read once to restore an in-progress trip. */
        val LEGACY_LOCATION_TRIP_ID = stringPreferencesKey("trip_location_id")
    }

    override val activeTrip: Flow<ActiveTrip?> = dataStore.data.map { prefs ->
        val tripDbId = prefs[Keys.TRIP_DB_ID]
        val vehicleId = prefs[Keys.TRIP_VEHICLE_ID]
        val routeId = prefs[Keys.TRIP_ROUTE_ID]
        val startedAt = prefs[Keys.TRIP_STARTED_AT]
        if (tripDbId != null && vehicleId != null && routeId != null && startedAt != null) {
            ActiveTrip(
                tripDbId = tripDbId,
                // A trip saved by the build before the split has no gtfs key; recover the id the
                // driver entered from the legacy key instead of silently dropping it.
                gtfsTripId = prefs[Keys.TRIP_GTFS_TRIP_ID]
                    ?: legacyGtfsTripId(prefs[Keys.LEGACY_LOCATION_TRIP_ID], routeId),
                vehicleId = vehicleId,
                // New saves are already trimmed (TripRepository.start); the old build's were not.
                routeId = routeId.trim(),
                startDate = prefs[Keys.TRIP_START_DATE] ?: serviceDate(startedAt, zone),
                startedAtEpochSec = startedAt,
                driverId = prefs[Keys.TRIP_DRIVER_ID],
            )
        } else {
            null
        }
    }

    override val unconfirmedEnd: Flow<UnconfirmedEnd?> = dataStore.data.map { prefs ->
        prefs[Keys.UNCONFIRMED_END_TRIP_DB_ID]?.let { UnconfirmedEnd(it, prefs[Keys.UNCONFIRMED_END_DRIVER_ID]) }
    }

    override val recentRoutes: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[Keys.RECENT_ROUTES]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    override suspend fun saveActiveTrip(trip: ActiveTrip) {
        dataStore.edit { prefs ->
            prefs[Keys.TRIP_DB_ID] = trip.tripDbId
            prefs[Keys.TRIP_GTFS_TRIP_ID] = trip.gtfsTripId
            prefs[Keys.TRIP_VEHICLE_ID] = trip.vehicleId
            prefs[Keys.TRIP_ROUTE_ID] = trip.routeId
            prefs[Keys.TRIP_START_DATE] = trip.startDate
            prefs[Keys.TRIP_STARTED_AT] = trip.startedAtEpochSec
            if (trip.driverId != null) {
                prefs[Keys.TRIP_DRIVER_ID] = trip.driverId
            } else {
                prefs.remove(Keys.TRIP_DRIVER_ID)
            }
        }
    }

    override suspend fun clearActiveTrip() {
        dataStore.edit { it.removeActiveTrip() }
    }

    override suspend fun endActiveTripLocally(driverId: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.TRIP_DB_ID]?.let { tripDbId ->
                prefs[Keys.UNCONFIRMED_END_TRIP_DB_ID] = tripDbId
                if (driverId != null) {
                    prefs[Keys.UNCONFIRMED_END_DRIVER_ID] = driverId
                } else {
                    prefs.remove(Keys.UNCONFIRMED_END_DRIVER_ID)
                }
            }
            prefs.removeActiveTrip()
        }
    }

    override suspend fun clearUnconfirmedEnd() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.UNCONFIRMED_END_TRIP_DB_ID)
            prefs.remove(Keys.UNCONFIRMED_END_DRIVER_ID)
        }
    }

    private fun MutablePreferences.removeActiveTrip() {
        remove(Keys.TRIP_DB_ID)
        remove(Keys.TRIP_GTFS_TRIP_ID)
        remove(Keys.TRIP_VEHICLE_ID)
        remove(Keys.TRIP_ROUTE_ID)
        remove(Keys.TRIP_START_DATE)
        remove(Keys.TRIP_STARTED_AT)
        remove(Keys.TRIP_DRIVER_ID)
        remove(Keys.LEGACY_LOCATION_TRIP_ID)
    }

    override suspend fun addRecentRoute(routeId: String) {
        val cleaned = routeId.replace("|", "")
        dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_ROUTES]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
            val updated = (listOf(cleaned) + current.filter { it != cleaned }).take(5)
            prefs[Keys.RECENT_ROUTES] = updated.joinToString("|")
        }
    }
}
