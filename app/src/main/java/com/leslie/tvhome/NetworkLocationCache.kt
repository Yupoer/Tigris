package com.leslie.tvhome

internal class NetworkLocationCache<T>(private val freshMs: Long, private val retryMs: Long) {
    private data class Entry<T>(val fingerprint: String, val value: T, val at: Long)
    private var route: String? = null
    private var revision = 0L
    private var entry: Entry<T>? = null
    private var attemptedAt: Long? = null

    @Synchronized fun fingerprint(currentRoute: String): String {
        if (route != currentRoute) {
            route = currentRoute
            invalidate()
        }
        return token()
    }

    @Synchronized fun invalidate() {
        revision++
        entry = null
        attemptedAt = null
    }

    @Synchronized fun cached(fingerprint: String, now: Long): T? = entry?.takeIf {
        fingerprint == token() && it.fingerprint == fingerprint &&
            now >= it.at && now - it.at < freshMs
    }?.value

    @Synchronized fun begin(fingerprint: String, now: Long, force: Boolean): Boolean {
        if (fingerprint != token()) return false
        val attempted = attemptedAt
        if (!force && attempted != null && now >= attempted && now - attempted < retryMs) return false
        entry = null
        attemptedAt = now
        return true
    }

    @Synchronized fun finish(fingerprint: String, value: T?, now: Long): T? {
        if (fingerprint != token()) return null
        entry = value?.let { Entry(fingerprint, it, now) }
        return value
    }

    private fun token() = "$revision/$route"
}
