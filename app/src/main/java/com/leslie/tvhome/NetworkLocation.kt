package com.leslie.tvhome

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** External-IP region for this app's default network. No GPS permission or stored IP address. */
object NetworkLocation {
    const val FRESH_MS = 5L * 60_000
    private const val RETRY_MS = 30_000L
    private const val ENDPOINT = "https://ipwho.is/?fields=success,city,region,country,country_code,latitude,longitude"
    private val cache = NetworkLocationCache<Place>(FRESH_MS, RETRY_MS)
    private val requestLock = Any()

    data class Place(val city: String, val region: String, val country: String,
                     val countryCode: String, val latitude: Double, val longitude: Double) {
        val locality: String get() = city.ifBlank { region.ifBlank { country } }
        val label: String get() = listOf(country, locality).filter { it.isNotBlank() }
            .distinct().joinToString(" · ")
    }

    private data class Route(val signature: String, val connected: Boolean, val vpn: Boolean)

    private fun route(ctx: Context): Route = runCatching {
        val manager = ctx.getSystemService(ConnectivityManager::class.java)
        val active = manager?.activeNetwork ?: return Route("offline", false, false)
        val capabilities = manager.getNetworkCapabilities(active)
            ?: return Route("unavailable", false, false)
        val vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val transports = (NetworkCapabilities.TRANSPORT_CELLULAR..NetworkCapabilities.TRANSPORT_LOWPAN)
            .filter { capabilities.hasTransport(it) }
        val links = manager.getLinkProperties(active)?.hashCode() ?: 0
        Route("${active.networkHandle}/$transports/$links/$internet/$vpn",
            internet, vpn)
    }.getOrElse { Route("unavailable", false, false) }

    /** Opaque local-only token. Compare before applying an asynchronous result. */
    fun fingerprint(ctx: Context): String = cache.fingerprint(
        "${Prefs(ctx).networkLocationEnabled}/${route(ctx).signature}")

    fun vpnActive(ctx: Context): Boolean = route(ctx).vpn

    /** Call from the default-network callback, including VPN and link-property changes. */
    fun invalidate() = cache.invalidate()

    /** Memory-only and nonblocking. Never returns a region from a previous network. */
    fun cached(ctx: Context): Place? {
        val key = fingerprint(ctx)
        if (!Prefs(ctx).networkLocationEnabled) return null
        return cache.cached(key, SystemClock.elapsedRealtime())
    }

    /** Blocking. Call off the main thread after the user enables network-region detection. */
    fun current(ctx: Context, force: Boolean = false): Place? = synchronized(requestLock) {
        val key = fingerprint(ctx)
        if (!Prefs(ctx).networkLocationEnabled || !route(ctx).connected) return@synchronized null
        val now = SystemClock.elapsedRealtime()
        if (!force) cache.cached(key, now)?.let { return@synchronized it }
        if (!cache.begin(key, now, force)) return@synchronized null
        val place = runCatching { lookup() }
            .onFailure { android.util.Log.w("NetworkLocation", "Could not resolve current network region", it) }.getOrNull()
        fingerprint(ctx)
        cache.finish(key, place, SystemClock.elapsedRealtime())
    }

    private fun lookup(): Place? {
        val chinese = I18n.isChinese()
        val connection = URL(ENDPOINT + if (chinese) "&lang=zh-CN" else "&lang=en")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 7_000
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        val json = try {
            if (connection.responseCode !in 200..299) return null
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
        if (!json.optBoolean("success", false)) return null
        val latitude = json.optDouble("latitude", Double.NaN)
        val longitude = json.optDouble("longitude", Double.NaN)
        val code = json.optString("country_code").uppercase(Locale.ROOT)
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0 || !code.matches(Regex("[A-Z]{2}"))) return null
        fun name(field: String): String = json.optString(field).trim().let {
            if (chinese) TaiwanText.convert(it) else it
        }
        val locale = if (chinese) Locale.forLanguageTag("zh-TW") else Locale.ENGLISH
        val country = Locale.Builder().setRegion(code).build().getDisplayCountry(locale).ifBlank { name("country") }
        return Place(name("city"), name("region"), country, code, latitude, longitude)
    }
}
