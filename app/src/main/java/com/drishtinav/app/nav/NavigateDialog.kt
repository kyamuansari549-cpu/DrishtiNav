package com.drishtinav.app.nav

import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drishtinav.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Destination picker: search (Nominatim, online), raw "lat,lng" input
 * (offline), and saved places. Tap to navigate, long-press to save/remove.
 *
 * Results show a distance hint when [locationProvider] supplies the current
 * fix; every row carries a TalkBack label (name + distance/saved state).
 */
class NavigateDialog(
    private val activity: AppCompatActivity,
    private val savedPlaces: SavedPlaces,
    private val onPick: (lat: Double, lng: Double, name: String) -> Unit,
    private val locationProvider: (() -> Location?)? = null
) {

    private data class Item(
        val label: String,
        val lat: Double,
        val lng: Double,
        val saved: Boolean
    )

    /** Two-line rows: place name + distance/saved hint. */
    private inner class ResultAdapter(
        private val distanceOf: (Item) -> String?
    ) : BaseAdapter() {
        var rows: List<Item> = emptyList()

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Item = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: activity.layoutInflater.inflate(R.layout.dialog_destination_row, parent, false)
            val item = rows[position]
            val title = view.findViewById<TextView>(R.id.row_title)
            val subtitle = view.findViewById<TextView>(R.id.row_subtitle)

            title.text = (if (item.saved) "★ " else "") + item.label
            val dist = distanceOf(item)
            val sub = buildString {
                if (item.saved) append("Saved place")
                if (dist != null) {
                    if (isNotEmpty()) append(" · ")
                    append(dist)
                }
            }
            subtitle.text = sub
            subtitle.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
            // TalkBack: one spoken label per row.
            view.contentDescription =
                if (sub.isEmpty()) title.text.toString() else "${title.text}, $sub"
            return view
        }
    }

    fun show() {
        val view = activity.layoutInflater.inflate(R.layout.dialog_destination, null)
        val input = view.findViewById<EditText>(R.id.dest_input)
        val searchButton = view.findViewById<Button>(R.id.search_button)
        val list = view.findViewById<ListView>(R.id.results_list)

        val distanceOf: (Item) -> String? = { item ->
            val origin = locationProvider?.invoke()
            if (origin == null) {
                null
            } else {
                val res = FloatArray(1)
                Location.distanceBetween(
                    origin.latitude, origin.longitude,
                    item.lat, item.lng, res
                )
                formatDistance(res[0])
            }
        }

        val adapter = ResultAdapter(distanceOf)
        list.adapter = adapter

        fun refreshSaved() {
            adapter.rows = savedPlaces.all().map {
                Item(it.name, it.lat, it.lng, saved = true)
            }
            adapter.notifyDataSetChanged()
        }
        refreshSaved()

        fun toast(text: String) =
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

        val dialog = MaterialAlertDialogBuilder(activity)
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
                    adapter.rows = results.map {
                        Item(it.name, it.lat, it.lng, savedPlaces.isSaved(it.name))
                    }
                    adapter.notifyDataSetChanged()
                    if (results.isEmpty()) toast("No results found")
                } catch (e: Exception) {
                    toast("Search failed: ${e.message}")
                } finally {
                    searchButton.isEnabled = true
                }
            }
        }

        list.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            dialog.dismiss()
            onPick(item.lat, item.lng, item.label)
        }

        list.setOnItemLongClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
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

        private fun formatDistance(meters: Float): String =
            if (meters < 1000f) "${meters.toInt()} m away"
            else "%.1f km away".format(meters / 1000f)
    }
}
