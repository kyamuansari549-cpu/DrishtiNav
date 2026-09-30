package com.drishtinav.app.nav

import android.location.Location
import android.os.SystemClock
import com.drishtinav.app.output.AppStrings
import com.drishtinav.app.output.SpeechEngine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turn-by-turn guidance over a [Route], driven by GPS fixes.
 *
 * Behaviour:
 * - Announces "In X meters, <instruction>" at 100/50/20 m thresholds
 *   (only thresholds shorter than the step itself).
 * - Advances the step when the maneuver point is reached (< 12 m).
 * - Announces arrival on the final step (< 20 m).
 * - Detects off-route (moving away from the maneuver, or > 80 m out) and
 *   asks the host to replan — at most once per 15 s.
 *
 * Obstacle alerts keep working underneath: urgent ones interrupt navigation
 * speech because AlertPolicy uses QUEUE_FLUSH, while navigation uses QUEUE_ADD.
 */
class NavigationEngine(
    private val speech: SpeechEngine,
    private val listener: Listener
) {

    interface Listener {
        fun onInstruction(text: String)
        fun onArrival()
        fun onOffRoute()
    }

    private var route: Route? = null
    private var destination: Pair<Double, Double>? = null
    private var stepIndex = 0
    private var announced = mutableSetOf<String>()
    private var lastPromptAt = 0L
    private var lastOffRouteAt = 0L
    private var prevDistToManeuver = Double.MAX_VALUE
    private var arrived = false

    val isNavigating: Boolean get() = route != null && !arrived
    val destinationCoords: Pair<Double, Double>? get() = destination

    fun start(route: Route, destLat: Double, destLng: Double) {
        this.route = route
        this.destination = destLat to destLng
        stepIndex = 0
        announced = mutableSetOf()
        arrived = false
        prevDistToManeuver = Double.MAX_VALUE
        lastPromptAt = 0L
        lastOffRouteAt = 0L
        val first = route.steps.firstOrNull()
        val text = AppStrings.routeFound(route.totalDistanceMeters, first?.instruction)
        announce(text, force = true)
    }

    fun stop() {
        route = null
        destination = null
        arrived = false
    }

    fun onLocation(location: Location) {
        val route = route ?: return
        if (arrived || stepIndex >= route.steps.size) return

        val step = route.steps[stepIndex]
        val distToManeuver =
            haversineMeters(location.latitude, location.longitude, step.maneuverLat, step.maneuverLng)

        // Arrival on the final step.
        if (stepIndex == route.steps.size - 1 && distToManeuver < ARRIVE_M) {
            arrived = true
            val text = AppStrings.arrived()
            speech.speakUrgent(text)
            listener.onInstruction(text)
            listener.onArrival()
            return
        }

        // Reached the maneuver → advance to the next step.
        if (distToManeuver < STEP_ADVANCE_M) {
            stepIndex++
            announced = mutableSetOf()
            prevDistToManeuver = Double.MAX_VALUE
            if (stepIndex < route.steps.size) {
                announce(AppStrings.maneuver(route.steps[stepIndex].instruction), force = true)
            }
            return
        }

        // Off-route: clearly moving away from the maneuver, or far away.
        val now = SystemClock.elapsedRealtime()
        if ((distToManeuver > prevDistToManeuver + OFF_ROUTE_MARGIN_M ||
                distToManeuver > OFF_ROUTE_MAX_M) &&
            now - lastOffRouteAt > OFF_ROUTE_COOLDOWN_MS
        ) {
            lastOffRouteAt = now
            listener.onOffRoute()
            prevDistToManeuver = distToManeuver
            return
        }
        prevDistToManeuver = distToManeuver

        // Threshold prompts, nearest first.
        for (threshold in PROMPT_THRESHOLDS) {
            if (threshold >= step.distanceMeters) continue
            val key = "$stepIndex:$threshold"
            if (distToManeuver < threshold && key !in announced) {
                announced.add(key)
                announce(AppStrings.approaching(distToManeuver, step.instruction))
                break
            }
        }
    }

    private fun announce(text: String, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPromptAt < MIN_PROMPT_GAP_MS) return
        lastPromptAt = now
        speech.speakQueued(text)
        listener.onInstruction(text)
    }

    companion object {
        private val PROMPT_THRESHOLDS = doubleArrayOf(100.0, 50.0, 20.0)
        private const val STEP_ADVANCE_M = 12.0
        private const val ARRIVE_M = 20.0
        private const val OFF_ROUTE_MARGIN_M = 30.0
        private const val OFF_ROUTE_MAX_M = 80.0
        private const val OFF_ROUTE_COOLDOWN_MS = 15_000L
        private const val MIN_PROMPT_GAP_MS = 4_000L

        fun haversineMeters(
            lat1: Double, lng1: Double, lat2: Double, lng2: Double
        ): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
