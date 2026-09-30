package com.drishtinav.app.nav

/** One turn-by-turn step of a walking route. */
data class RouteStep(
    /** Spoken instruction, e.g. "Turn left onto MG Road". */
    val instruction: String,
    /** Length of this step in metres. */
    val distanceMeters: Double,
    /** Where the maneuver happens. */
    val maneuverLat: Double,
    val maneuverLng: Double
)

data class Route(
    val steps: List<RouteStep>,
    val totalDistanceMeters: Double,
    val totalDurationSec: Double
)
