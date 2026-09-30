package com.drishtinav.app.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Destination search via Nominatim (OpenStreetMap). Online only — when there
 * is no network, the dialog falls back to raw "lat,lng" input.
 */
object Geocoder {

    data class Place(val name: String, val lat: Double, val lng: Double)

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://nominatim.openstreetmap.org/search" +
                "?q=$encoded&format=jsonv2&limit=5&addressdetails=0"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        // Required by the Nominatim usage policy.
        conn.setRequestProperty("User-Agent", "DrishtiNav/1.0 (assistive navigation aid)")
        try {
            if (conn.responseCode != 200) {
                throw Exception("Search returned HTTP ${conn.responseCode}")
            }
            val results = JSONArray(conn.inputStream.bufferedReader().readText())
            ArrayList<Place>(results.length()).also { places ->
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val fullName = item.getString("display_name")
                    // Keep the first two address parts for a speakable short name.
                    val shortName = fullName.split(",").take(2).joinToString(",").trim()
                    places.add(
                        Place(
                            name = shortName.ifBlank { fullName },
                            lat = item.getString("lat").toDouble(),
                            lng = item.getString("lon").toDouble()
                        )
                    )
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
