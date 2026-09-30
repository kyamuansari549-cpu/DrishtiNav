package com.drishtinav.app.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Walking-route planner using the public OSRM demo server (no API key).
 * Network failures are the caller's problem to surface — see NavigateDialog.
 */
object RoutePlanner {

    suspend fun plan(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Route = withContext(Dispatchers.IO) {
        val url = URL(
            "https://router.project-osrm.org/route/v1/foot/" +
                "$fromLng,$fromLat;$toLng,$toLat" +
                "?overview=false&steps=true&geometries=geojson"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "DrishtiNav/1.0 (assistive navigation aid)")
        try {
            if (conn.responseCode != 200) {
                throw Exception("Route service returned HTTP ${conn.responseCode}")
            }
            parse(JSONObject(conn.inputStream.bufferedReader().readText()))
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(root: JSONObject): Route {
        if (root.optString("code") != "Ok") {
            throw Exception("No walking route found (${root.optString("code")})")
        }
        val routeJson = root.getJSONArray("routes").getJSONObject(0)
        val leg = routeJson.getJSONArray("legs").getJSONObject(0)
        val stepsJson = leg.getJSONArray("steps")

        val steps = ArrayList<RouteStep>(stepsJson.length())
        for (i in 0 until stepsJson.length()) {
            val stepJson = stepsJson.getJSONObject(i)
            val maneuver = stepJson.getJSONObject("maneuver")
            val location = maneuver.getJSONArray("location") // [lng, lat]
            steps.add(
                RouteStep(
                    instruction = instructionFor(
                        maneuver.optString("type"),
                        maneuver.optString("modifier"),
                        stepJson.optString("name")
                    ),
                    distanceMeters = stepJson.getDouble("distance"),
                    maneuverLat = location.getDouble(1),
                    maneuverLng = location.getDouble(0)
                )
            )
        }
        return Route(
            steps = steps,
            totalDistanceMeters = routeJson.getDouble("distance"),
            totalDurationSec = routeJson.getDouble("duration")
        )
    }

    private fun instructionFor(type: String, modifier: String, name: String): String {
        val mod = modifierText(modifier)
        val on = if (name.isNotBlank()) " onto $name" else ""
        return when (type) {
            "depart" -> if (name.isNotBlank()) "Head $mod on $name" else "Start walking"
            "arrive" -> "You have arrived at your destination"
            "roundabout", "rotary" -> "At the roundabout, take the exit$on"
            "turn" -> "Turn $mod$on"
            "new name" -> "Continue$on"
            "merge", "fork" -> "Merge $mod$on"
            "end of road" -> "At the end of the road, turn $mod$on"
            else -> "${type.replace('_', ' ')} $mod$on".trim().replaceFirstChar(Char::titlecase)
        }
    }

    private fun modifierText(modifier: String): String = when (modifier) {
        "left" -> "left"
        "slight left" -> "slightly left"
        "sharp left" -> "sharply left"
        "right" -> "right"
        "slight right" -> "slightly right"
        "sharp right" -> "sharply right"
        "straight" -> "straight"
        "uturn" -> "back (U-turn)"
        else -> ""
    }.trim()
}
