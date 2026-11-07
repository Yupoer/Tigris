package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class CleanupPolicyTest {
    @Test fun automaticCleanupNeverForceStopsAndUnknownForegroundFailsClosed() {
        assertEquals(CleanupPolicy.Mode.SKIP, CleanupPolicy.mode(true, null))
        assertEquals(CleanupPolicy.Mode.CACHE_ONLY, CleanupPolicy.mode(true, "tv.player"))
        assertEquals(CleanupPolicy.Mode.CACHE_ONLY, CleanupPolicy.mode(false, null))
        assertEquals(CleanupPolicy.Mode.DEEP, CleanupPolicy.mode(false, "tv.home"))
    }
    @Test fun readsAndroid9AndNewerForegroundFormats() {
        assertEquals("tv.player", CleanupPolicy.foreground("mResumedActivity: ActivityRecord{a u0 tv.player/.Main t4}"))
        assertEquals("tv.player", CleanupPolicy.foreground("topResumedActivity=ActivityRecord{a u10 tv.player/.Main t4}"))
        assertNull(CleanupPolicy.foreground("connection failed"))
    }

    @Test fun targetsExcludeProtectedAppsAndInvalidShellInput() {
        assertEquals(listOf("tv.unused"), CleanupPolicy.targets(
            listOf("tv.player", "tv.unused", "tv.unused", "tv.bad; reboot", "Failure"), setOf("tv.player")))
    }

    @Test fun outputIsNotProofOfSuccessfulStop() {
        assertFalse(CleanupPolicy.succeeded("SecurityException", "OK"))
        assertFalse(CleanupPolicy.succeeded(null, "OK"))
        assertTrue(CleanupPolicy.succeeded("OK\r\n", "OK"))
    }

    @Test fun discoversVpnProvidersByIntentOrServicePermission() {
        val vpn = CleanupPolicy.vpnPackages(listOf("vpn.intent"), listOf(
            CleanupPolicy.Service("vpn.permission", "android.permission.BIND_VPN_SERVICE"),
            CleanupPolicy.Service("vpn.intent", "android.permission.BIND_VPN_SERVICE"),
            CleanupPolicy.Service("tv.player", "android.permission.BIND_JOB_SERVICE"),
            CleanupPolicy.Service("tv.unused", null),
        ))
        assertEquals(setOf("vpn.intent", "vpn.permission"), vpn)
    }

    @Test fun vpnProvidersSurviveManualAndCachedCleanupWithoutRunningProcesses() {
        val vpn = CleanupPolicy.vpnPackages(emptyList(), listOf(
            CleanupPolicy.Service("com.surfshark.vpnclient.android", "android.permission.BIND_VPN_SERVICE"),
            CleanupPolicy.Service("vpn.other", "android.permission.BIND_VPN_SERVICE"),
        ))
        val packages = listOf("com.surfshark.vpnclient.android", "vpn.other", "tv.unused")
        assertEquals(listOf("tv.unused"), CleanupPolicy.targets(packages, vpn))
        assertEquals(listOf("tv.unused"), CleanupPolicy.cachedTargets(packages, emptySet(), vpn))
    }

    @Test fun cachedCleanupRequiresEvidenceAndProtectsPackagesWithAnyActiveProcess() {
        assertEquals(emptyList<String>(), CleanupPolicy.cachedTargets(emptyList(), emptySet(), emptySet()))
        assertEquals(listOf("tv.unused"), CleanupPolicy.cachedTargets(
            listOf("tv.player", "tv.unused", "tv.kept"), setOf("tv.player"), setOf("tv.kept")))
    }
}
