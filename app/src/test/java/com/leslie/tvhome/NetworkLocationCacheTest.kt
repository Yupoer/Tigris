package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class NetworkLocationCacheTest {
    private val cache = NetworkLocationCache<String>(freshMs = 300_000, retryMs = 30_000)

    @Test fun changingNetworkImmediatelyHidesThePreviousRegion() {
        val wifi = cache.fingerprint("wifi")
        cache.begin(wifi, 100, false)
        cache.finish(wifi, "台北", 100)
        assertEquals("台北", cache.cached(wifi, 200))
        val vpn = cache.fingerprint("vpn")
        assertNull(cache.cached(vpn, 200))
        assertNull(cache.cached(cache.fingerprint("wifi"), 200))
    }

    @Test fun oldNetworkResponseCannotReplaceCurrentVpnRegion() {
        val wifi = cache.fingerprint("wifi")
        cache.begin(wifi, 100, false)
        val vpn = cache.fingerprint("vpn")
        cache.begin(vpn, 200, false)
        cache.finish(vpn, "東京", 300)
        assertNull(cache.finish(wifi, "台北", 400))
        assertEquals("東京", cache.cached(vpn, 500))
    }

    @Test fun explicitInvalidationRejectsAnInFlightResponse() {
        val key = cache.fingerprint("vpn")
        cache.begin(key, 100, false)
        cache.invalidate()
        assertNull(cache.finish(key, "東京", 200))
        assertNull(cache.cached(cache.fingerprint("vpn"), 200))
    }

    @Test fun failedForcedRefreshDoesNotKeepShowingTheOldExitRegion() {
        val key = cache.fingerprint("vpn")
        cache.begin(key, 100, false)
        cache.finish(key, "東京", 100)
        assertTrue(cache.begin(key, 200, true))
        assertNull(cache.cached(key, 200))
        assertNull(cache.finish(key, null, 300))
        assertNull(cache.cached(key, 400))
    }

    @Test fun expiredAndFutureDatedEntriesAreUnavailable() {
        val key = cache.fingerprint("wifi")
        cache.begin(key, 100, false)
        cache.finish(key, "台北", 100)
        assertNull(cache.cached(key, 99))
        assertEquals("台北", cache.cached(key, 300_099))
        assertNull(cache.cached(key, 300_100))
    }

    @Test fun failedRequestsBackOffButANewNetworkCanRetryImmediately() {
        val key = cache.fingerprint("wifi")
        assertTrue(cache.begin(key, 100, false))
        cache.finish(key, null, 100)
        assertFalse(cache.begin(key, 30_099, false))
        assertTrue(cache.begin(key, 30_100, false))
        assertTrue(cache.begin(cache.fingerprint("vpn"), 30_101, false))
    }

    @Test fun disablingThenEnablingLocationDoesNotRestoreThePreviousRegion() {
        val enabled = cache.fingerprint("true/wifi")
        cache.begin(enabled, 100, false)
        cache.finish(enabled, "台北", 100)
        assertNull(cache.cached(cache.fingerprint("false/wifi"), 200))
        assertNull(cache.cached(cache.fingerprint("true/wifi"), 300))
    }
}
