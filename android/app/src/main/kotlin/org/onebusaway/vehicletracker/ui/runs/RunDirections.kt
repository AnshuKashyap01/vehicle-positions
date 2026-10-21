package org.onebusaway.vehicletracker.ui.runs

/**
 * One direction of a route's runs as the board's switch offers it. [headsign] is the one most of its runs show, for
 * the switch's "To" label, and [firstStop] and [lastStop] are the ends most of them run between.
 */
data class RunDirection(val id: Int, val headsign: String, val firstStop: String, val lastStop: String)

/** The two directions the board switches between, in `direction_id` order, and the index of the one it opens on. */
data class RunDirections(val directions: List<RunDirection>, val start: Int)

/**
 * The board's two directions, or null when the runs do not split into exactly two: a feed without `direction_id`, or
 * one that gives only some runs one, keeps a single list as before. The board opens on the direction of
 * [highlightedId], the run under way or the next to start, and on the first once the day's runs are over.
 */
fun directionsOf(runs: List<TripRun>, highlightedId: String?): RunDirections? {
    val ids = runs.map { it.directionId ?: return null }.distinct().sorted()
    if (ids.size != 2) return null
    val directions = ids.map { id ->
        val inDirection = runs.filter { it.directionId == id }
        val (firstStop, lastStop) = inDirection.mostCommon { it.firstStop to it.lastStop }
        RunDirection(id, inDirection.mostCommon { it.headsign }, firstStop, lastStop)
    }
    val highlighted = runs.firstOrNull { it.id == highlightedId }
    return RunDirections(directions, start = if (highlighted == null) 0 else ids.indexOf(highlighted.directionId))
}

/** The value most of these give, a tie going to the one that comes first. */
private fun <T, K> List<T>.mostCommon(key: (T) -> K): K = groupingBy(key).eachCount().maxBy { it.value }.key
