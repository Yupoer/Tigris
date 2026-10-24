package com.leslie.tvhome

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** No location permissions and no network requests unless the user explicitly opts in. */
internal object WeatherPolicy {
    const val UPDATE_MS = 45L * 60_000
    fun shouldShow(enabled: Boolean, city: String, clockStyle: String): Boolean =
        enabled && city.isNotBlank() && clockStyle != Prefs.CLOCK_OFF

    fun shouldRefresh(now: Long, attemptedAt: Long): Boolean =
        attemptedAt == Long.MIN_VALUE || now - attemptedAt >= UPDATE_MS

    fun shortText(degrees: Int, code: Int): String {
        val condition = when (code) {
            0 -> "晴"
            1, 2 -> "多云"
            3 -> "阴"
            45, 48 -> "雾"
            in 51..67, in 80..82 -> "雨"
            in 71..77, 85, 86 -> "雪"
            in 95..99 -> "雷雨"
            else -> ""
        }
        return "$degrees°" + if (condition.isNotEmpty()) " · $condition" else ""
    }
}

internal object Weather {
    /**
     * Returns a compact label, or null when unavailable. Disk cache is private to the TV and
     * city-specific. Offline or stale results do not leave a permanent weather badge on screen.
     * Call off the UI thread only.
     */
    fun read(ctx: Context, city: String): String? {
        if (city.isBlank()) return null
        val cache = File(ctx.filesDir, "weather-cache.json")
        val now = System.currentTimeMillis()
        val saved = runCatching { JSONObject(cache.readText()) }.getOrNull()
        if (saved != null && saved.optString("city") == city &&
            now - saved.optLong("updated", 0) in 0 until WeatherPolicy.UPDATE_MS) {
            return saved.optString("text").takeIf { it.isNotEmpty() }
        }

        val encoded = URLEncoder.encode(city, "UTF-8")
        val places = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=zh&format=json")
            .optJSONArray("results") ?: return null
        if (places.length() == 0) return null
        val loc = places.getJSONObject(0)
        val lat = loc.getDouble("latitude")
        val lon = loc.getDouble("longitude")
        val current = getJson("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&timezone=auto").getJSONObject("current")
        val degrees = current.getDouble("temperature_2m").roundToInt()
        val code = current.getInt("weather_code")
        val text = WeatherPolicy.shortText(degrees, code)
        runCatching {
            cache.writeText(JSONObject().put("city", city).put("updated", now).put("text", text).toString())
        }
        return text
    }

    private fun getJson(address: String): JSONObject {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "Tigris AndroidTV weather/1.0")
        try {
            if (conn.responseCode !in 200..299) error("Weather HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { conn.disconnect() }
    }
}
