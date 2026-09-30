package com.drishtinav.app.nav

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Named destinations persisted as JSON in the app's private files dir.
 * Home, office, the bus stop — places a blind user navigates to often.
 */
class SavedPlaces(context: Context) {

    data class SavedPlace(val name: String, val lat: Double, val lng: Double)

    private val file = File(context.filesDir, "saved_places.json")

    fun all(): List<SavedPlace> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            ArrayList<SavedPlace>(array.length()).also { list ->
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SavedPlace(
                            name = obj.getString("name"),
                            lat = obj.getDouble("lat"),
                            lng = obj.getDouble("lng")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(place: SavedPlace) {
        val current = all().toMutableList()
        current.removeAll { it.name.equals(place.name, ignoreCase = true) }
        current.add(0, place)
        write(current)
    }

    fun remove(name: String) {
        write(all().filterNot { it.name.equals(name, ignoreCase = true) })
    }

    fun isSaved(name: String): Boolean =
        all().any { it.name.equals(name, ignoreCase = true) }

    private fun write(places: List<SavedPlace>) {
        val array = JSONArray()
        for (place in places) {
            array.put(
                JSONObject()
                    .put("name", place.name)
                    .put("lat", place.lat)
                    .put("lng", place.lng)
            )
        }
        file.writeText(array.toString())
    }
}
