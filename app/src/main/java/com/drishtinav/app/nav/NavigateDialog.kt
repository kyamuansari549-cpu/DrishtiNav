package com.drishtinav.app.nav

import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drishtinav.app.R
import kotlinx.coroutines.launch

/**
 * Destination picker: search (Nominatim, online), raw "lat,lng" input
 * (offline), and saved places. Tap to navigate, long-press to save/remove.
 */
class NavigateDialog(
    private val activity: AppCompatActivity,
    private val savedPlaces: SavedPlaces,
    private val onPick: (lat: Double, lng: Double, name: String) -> Unit
) {

    private data class Item(
        val label: String,
        val lat: Double,
        val lng: Double,
        val saved: Boolean
    )

    fun show() {
        val view = activity.layoutInflater.inflate(R.layout.dialog_destination, null)
        val input = view.findViewById<EditText>(R.id.dest_input)
        val searchButton = view.findViewById<Button>(R.id.search_button)
        val list = view.findViewById<ListView>(R.id.results_list)

        var items: List<Item> = emptyList()
        val adapter =
            ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, mutableListOf())
        list.adapter = adapter

        fun refreshSaved() {
            items = savedPlaces.all().map {
                Item(it.name, it.lat, it.lng, saved = true)
            }
            adapter.clear()
            adapter.addAll(items.map { "★ ${it.label}" })
        }
        refreshSaved()

        fun toast(text: String) =
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Navigate to")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()

        searchButton.setOnClickListener {
            val query = input.text.toString().trim()
            if (query.isEmpty()) return@setOnClickListener

            parseLatLng(query)?.let { (lat, lng) ->
                dialog.dismiss()
                onPick(lat, lng, query)
                return@setOnClickListener
            }

            searchButton.isEnabled = false
            activity.lifecycleScope.launch {
                try {
                    val results = Geocoder.search(query)
                    items = results.map {
                        Item(it.name, it.lat, it.lng, savedPlaces.isSaved(it.name))
                    }
                    adapter.clear()
                    adapter.addAll(items.map { if (it.saved) "★ ${it.label}" else it.label })
                    if (results.isEmpty()) toast("No results found")
                } catch (e: Exception) {
                    toast("Search failed: ${e.message}")
                } finally {
                    searchButton.isEnabled = true
                }
            }
        }

        list.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            dialog.dismiss()
            onPick(item.lat, item.lng, item.label)
        }

        list.setOnItemLongClickListener { _, _, position, _ ->
            val item = items[position]
            if (item.saved) {
                savedPlaces.remove(item.label)
                toast("Removed ${item.label}")
            } else {
                savedPlaces.save(SavedPlaces.SavedPlace(item.label, item.lat, item.lng))
                toast("Saved ${item.label}")
            }
            refreshSaved()
            true
        }

        dialog.show()
    }

    companion object {
        private val LAT_LNG = Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")

        fun parseLatLng(text: String): Pair<Double, Double>? {
            val match = LAT_LNG.matchEntire(text.trim()) ?: return null
            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lng = match.groupValues[2].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            return lat to lng
        }
    }
}
