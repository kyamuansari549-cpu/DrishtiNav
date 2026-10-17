package com.drishtinav.app.nav

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Destination search, India-first.
 *
 * Primary: Nominatim (OpenStreetMap) biased to India (`countrycodes=in`) and,
 * when a GPS fix is available, to a viewbox around the user (`bounded=1`).
 * Fallback: Photon (photon.komoot.io, keyless), filtered to IN results.
 * Results are relevance re-ranked: query-token matches first, then distance.
 *
 * Online only — when there is no network, the dialog falls back to raw
 * "lat,lng" input. All network I/O runs on Dispatchers.IO.
 *
 * Wiring the GPS fix (one line in MainActivity, next to the tracker setup):
 *   Geocoder.locationProvider = {
 *       locationTracker.lastLocation?.let { it.latitude to it.longitude }
 *   }
 * Without it, search still works with the India bias, just no viewbox.
 */
object Geocoder {

    /** Unchanged — NavigateDialog depends on these three fields. */
    data class Place(val name: String, val lat: Double, val lng: Double)

    /**
     * Optional hook supplying the user's current fix as (lat, lng).
     * Read fresh on every search; null = no location bias.
     */
    @Volatile
    var locationProvider: (() -> Pair<Double, Double>?)? = null

    private const val USER_AGENT = "DrishtiNav/1.0 (assistive navigation aid)"
    private const val NOMINATIM = "https://nominatim.openstreetmap.org/search"
    private const val PHOTON = "https://photon.komoot.io/api/"
    private const val LIMIT = 10
    private const val RESULT_COUNT = 8
    /** Half-width of the location viewbox in degrees (~55 km). */
    private const val VIEWBOX_DEG = 0.5

    // Nominatim usage policy: at most 1 request/second. Shared across both
    // APIs so the fallback can never burst either.
    private val rateMutex = Mutex()
    private var lastRequestMs = 0L

    private val TOKEN_RE = Regex("[\\p{L}\\p{N}]+")
    private val DEVANAGARI_RE = Regex("[\\u0900-\\u097F]")

    private data class RawPlace(val name: String, val lat: Double, val lng: Double)

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()

        val userLoc = try {
            locationProvider?.invoke()
        } catch (_: Exception) {
            null
        }

        // Primary: Nominatim. Any failure (network or empty) -> Photon.
        var nominatimOk = false
        val nominatimHits = try {
            nominatimSearch(q, userLoc).also { nominatimOk = true }
        } catch (_: Exception) {
            emptyList()
        }
        if (nominatimHits.isNotEmpty()) return@withContext rerank(q, nominatimHits, userLoc)

        var photonOk = false
        val photonHits = try {
            photonSearch(q, userLoc).also { photonOk = true }
        } catch (_: Exception) {
            emptyList()
        }
        if (photonHits.isNotEmpty()) return@withContext rerank(q, photonHits, userLoc)

        // Both APIs failed -> likely no connectivity. The dialog shows this
        // message, so keep it short and in the user's language mix.
        if (!nominatimOk && !photonOk) {
            throw Exception("Internet me dikkat lag rahi hai — connection check karke dobara try karo")
        }
        // Both answered but found nothing.
        emptyList()
    }

    // ------------------------------------------------------------------
    // Nominatim (primary)
    // ------------------------------------------------------------------

    private suspend fun nominatimSearch(
        query: String,
        userLoc: Pair<Double, Double>?
    ): List<RawPlace> {
        val sb = StringBuilder(NOMINATIM)
            .append("?q=").append(enc(query))
            .append("&format=jsonv2")
            .append("&limit=").append(LIMIT)
            .append("&addressdetails=0")
            .append("&namedetails=1")
            .append("&countrycodes=in")
            .append("&accept-language=hi,en")
        if (userLoc != null) {
            val (lat, lng) = userLoc
            val left = lng - VIEWBOX_DEG
            val right = lng + VIEWBOX_DEG
            val top = (lat + VIEWBOX_DEG).coerceAtMost(90.0)
            val bottom = (lat - VIEWBOX_DEG).coerceAtLeast(-90.0)
            // viewbox order: left,top,right,bottom  (minLng,maxLat,maxLng,minLat)
            sb.append("&viewbox=").append(left).append(',')
                .append(top).append(',').append(right).append(',').append(bottom)
                .append("&bounded=1")
        }
        val results = JSONArray(get(sb.toString()))
        val out = ArrayList<RawPlace>(results.length())
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lng = item.optString("lon").toDoubleOrNull() ?: continue
            val displayName = item.optString("display_name")
            val name = cleanName(query, displayName, item.optJSONObject("namedetails"))
            if (name.isBlank()) continue
            out.add(RawPlace(name, lat, lng))
        }
        return out
    }

    /**
     * Speakable short name. Prefers the Hindi name when the query itself is
     * in Devanagari, then the canonical `name`, then the old 2-part
     * shortening of display_name.
     */
    private fun cleanName(
        query: String,
        displayName: String,
        namedetails: JSONObject?
    ): String {
        if (namedetails != null) {
            if (DEVANAGARI_RE.containsMatchIn(query)) {
                val hi = namedetails.optString("name:hi").trim()
                if (hi.isNotEmpty()) return hi
            }
            val canonical = namedetails.optString("name").trim()
            if (canonical.isNotEmpty() && canonical.length <= 80) return canonical
        }
        val short = displayName.split(",").take(2).joinToString(",").trim()
        return short.ifBlank { displayName.ifBlank { "Unknown place" } }
    }

    // ------------------------------------------------------------------
    // Photon (fallback, keyless)
    // ------------------------------------------------------------------

    private suspend fun photonSearch(
        query: String,
        userLoc: Pair<Double, Double>?
    ): List<RawPlace> {
        val sb = StringBuilder(PHOTON)
            .append("?q=").append(enc(query))
            .append("&limit=").append(LIMIT)
            .append("&lang=en")
        if (userLoc != null) {
            // Photon biases (not strictly bounds) results near lat/lon.
            sb.append("&lat=").append(userLoc.first).append("&lon=").append(userLoc.second)
        }
        val root = JSONObject(get(sb.toString()))
        val features = root.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<RawPlace>(features.length())
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: continue
            val lng = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            val props = feature.optJSONObject("properties") ?: continue
            // India-only, mirroring the Nominatim countrycodes=in bias.
            if (!props.optString("countrycode").equals("IN", ignoreCase = true)) continue
            val name = props.optString("name").trim()
            val locality = props.optString("city").trim()
                .ifEmpty { props.optString("state").trim() }
            val label = listOf(name, locality).filter { it.isNotEmpty() }
                .joinToString(", ")
            if (label.isEmpty()) continue
            out.add(RawPlace(label, lat, lng))
        }
        return out
    }

    // ------------------------------------------------------------------
    // HTTP + rate limiting
    // ------------------------------------------------------------------

    /** Single GET with the shared 1 req/sec throttle. Throws on HTTP errors. */
    private suspend fun get(urlStr: String): String {
        rateMutex.withLock {
            val waitMs = 1100L - (SystemClock.elapsedRealtime() - lastRequestMs)
            if (waitMs > 0) delay(waitMs)
            lastRequestMs = SystemClock.elapsedRealtime()
        }
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------
    // Relevance re-ranking
    // ------------------------------------------------------------------

    private fun rerank(
        query: String,
        places: List<RawPlace>,
        userLoc: Pair<Double, Double>?
    ): List<Place> {
        val tokens = TOKEN_RE.findAll(query.lowercase())
            .map { it.value }
            .filter { it.length >= 2 }
            .toList()
        val seen = HashSet<String>()
        return places.mapNotNull { p ->
            // Drop near-duplicate pins (~1 m grid).
            val key = "${(p.lat * 1e4).roundToInt()},${(p.lng * 1e4).roundToInt()}"
            if (!seen.add(key)) return@mapNotNull null
            p to score(tokens, p.name, userLoc, p.lat, p.lng)
        }
            .sortedByDescending { (_, s) -> s }
            .take(RESULT_COUNT)
            .map { (p, _) -> Place(p.name, p.lat, p.lng) }
    }

    private fun score(
        tokens: List<String>,
        name: String,
        userLoc: Pair<Double, Double>?,
        lat: Double,
        lng: Double
    ): Double {
        val lower = name.lowercase()
        var s = 0.0
        for (t in tokens) if (lower.contains(t)) s += 2.0
        if (tokens.isNotEmpty() && lower.startsWith(tokens[0])) s += 1.5
        if (userLoc != null) {
            val km = haversineKm(userLoc.first, userLoc.second, lat, lng)
            // Up to +2 for pins within ~60 km, fading with distance.
            s += 2.0 * (1.0 - (km / 60.0).coerceIn(0.0, 1.0))
        }
        return s
    }

    private fun haversineKm(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        return 2 * r * asin(sqrt(a))
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
