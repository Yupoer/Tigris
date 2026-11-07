package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class VpnBackgroundPolicyTest {
    private val surfshark = "com.surfshark.vpnclient.android"
    private val existing = listOf(
        "com.google.android.apps.mediashell", "com.android.bluetooth", "com.android.providers.media.module",
        "com.tcl.tvinput", "com.android.providers.tv", "com.tcl.autopair", "com.google.android.katniss",
        "com.tcl.ttvs", "com.tcl.versionUpdateApp", "com.tcl.usagestats", "com.tcl.MultiScreenInteraction_TV",
    )

    private fun dump(kill: List<String> = existing, oneKey: List<String> = listOf("com.tcl.bi")): String =
        (listOf("SERVICE com.tcl.guard/.memorymanager.TGuardService cafdc pid=1234",
            "=================== StrategyConfigInfo Begin ===================", "KillWhiteList") +
            kill.map { "KillWhiteList:          $it" } + "OneKeyCleanWhiteList" +
            oneKey.map { "OneKeyCleanWhiteList:          $it" } +
            "=================== StrategyConfigInfo End   ===================").joinToString("\n")

    @Test fun addsOnlyOptedPackageAndPreservesElevenExistingPackages() {
        val changes = VpnBackgroundPolicy.additions(dump(), setOf(surfshark))!!
        assertEquals(mapOf(
            VpnBackgroundPolicy.WhiteList.KILL to listOf(surfshark),
            VpnBackgroundPolicy.WhiteList.ONE_KEY to listOf(surfshark),
        ), changes)
        val after = dump(existing + changes.getValue(VpnBackgroundPolicy.WhiteList.KILL),
            listOf("com.tcl.bi") + changes.getValue(VpnBackgroundPolicy.WhiteList.ONE_KEY))
        assertEquals(emptyMap<VpnBackgroundPolicy.WhiteList, List<String>>(),
            VpnBackgroundPolicy.additions(after, setOf(surfshark)))
    }

    @Test fun retriesOnlyTheMissingListAfterPartialSuccess() {
        assertEquals(mapOf(VpnBackgroundPolicy.WhiteList.ONE_KEY to listOf(surfshark)),
            VpnBackgroundPolicy.additions(dump(existing + surfshark), setOf(surfshark)))
    }

    @Test fun requiresExactMemberNamesAndBothListHeadings() {
        val similar = dump(existing + "$surfshark.other", listOf("com.tcl.bi", "$surfshark.other"))
        assertEquals(setOf(VpnBackgroundPolicy.WhiteList.KILL, VpnBackgroundPolicy.WhiteList.ONE_KEY),
            VpnBackgroundPolicy.additions(similar, setOf(surfshark))?.keys)
        assertNull(VpnBackgroundPolicy.additions(dump().replace("OneKeyCleanWhiteList", "OneKeyCleanWhiteListExtra"), setOf(surfshark)))
        assertNull(VpnBackgroundPolicy.additions("KillWhiteList: $surfshark", setOf(surfshark)))
        assertNull(VpnBackgroundPolicy.additions(dump().substringBefore("StrategyConfigInfo End"), setOf(surfshark)))
    }

    @Test fun emptyListsCanBeReconciledButInvalidPackagesCannotReachShell() {
        assertEquals(2, VpnBackgroundPolicy.additions(dump(emptyList(), emptyList()), setOf(surfshark))?.size)
        assertNull(VpnBackgroundPolicy.additions(dump(), setOf("$surfshark; reboot")))
        assertNull(VpnBackgroundPolicy.additions(dump(existing + "tv.bad; reboot"), setOf(surfshark)))
    }

    @Test fun bootAndTwoMinuteRetriesAreAllowedWithoutConcurrentRepeatedWork() {
        assertTrue(VpnBackgroundPolicy.due(null, 5))
        assertFalse(VpnBackgroundPolicy.due(5, 5))
        assertFalse(VpnBackgroundPolicy.due(5, 120_004))
        assertTrue(VpnBackgroundPolicy.due(5, 120_005))
        assertTrue(VpnBackgroundPolicy.due(120_005, 5))
    }
}
