package com.openremotv.sweep

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Where the TV is, for its weather, Panchang and sun times: set once on the phone, from the phone's own location or a
 * typed city, and written into the theme. Without one, the TV finds its city from its internet address.
 */
data class Place(val city: String, val latitude: Double, val longitude: Double) {

    companion object {
        private const val PREFS = "place"

        fun saved(context: Context): Place? {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val city = p.getString("city", null) ?: return null
            return Place(city, p.getFloat("lat", 0f).toDouble(), p.getFloat("lon", 0f).toDouble())
        }

        fun save(context: Context, place: Place?) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (place == null) p.clear() else p.putString("city", place.city)
                .putFloat("lat", place.latitude.toFloat()).putFloat("lon", place.longitude.toFloat())
            p.apply()
        }

        /** The phone's last known position (no GPS fix is waited for), named by the phone's geocoder when it can. */
        @SuppressLint("MissingPermission")
        suspend fun fromPhone(context: Context): Place? = withContext(Dispatchers.IO) {
            val lm = context.getSystemService(LocationManager::class.java) ?: return@withContext null
            val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time } ?: return@withContext null
            val city = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(best.latitude, best.longitude, 1)
                    ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()
            Place(city ?: "%.2f, %.2f".format(Locale.ROOT, best.latitude, best.longitude), best.latitude, best.longitude)
        }

        /** A typed city, looked up with Open-Meteo's free geocoder; null when nothing matches. */
        suspend fun search(name: String): Place? = withContext(Dispatchers.IO) {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name.trim(), "UTF-8")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            try {
                val r = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("results")?.optJSONObject(0)
                    ?: return@withContext null
                Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
            } finally {
                c.disconnect()
            }
        }

        /** The theme's settings with this place written in, or "auto" (the TV finds it) when there is none. */
        fun applyTo(config: String, place: Place?): String {
            val o = JSONObject(config)
            if (place == null) {
                o.put("weather_city", "auto")
                o.remove("weather_latitude")
                o.remove("weather_longitude")
            } else {
                o.put("weather_city", place.city).put("weather_latitude", place.latitude).put("weather_longitude", place.longitude)
            }
            return o.toString(2)
        }
    }
}
