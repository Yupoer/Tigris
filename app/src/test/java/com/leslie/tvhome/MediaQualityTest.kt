package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaQualityTest {
    private val avc = "https://sylvan.apple.com/Videos/SAMPLE_SDR_2K_AVC.mov"
    private val hevc = "https://sylvan.apple.com/Aerials/2x/Videos/SAMPLE_SDR_2K_HEVC.mov"

    @Test fun uhdHevcPreferredButRetainsTwoFallbacks() {
        assertEquals(listOf(
            "https://sylvan.apple.com/Aerials/2x/Videos/SAMPLE_SDR_4K_HEVC.mov",
            hevc, avc,
        ), MediaQuality.aerialCandidates(avc, hevc, true, true))
    }

    @Test fun incompatibleDecoderUsesAvcOnly() {
        assertEquals(listOf(avc), MediaQuality.aerialCandidates(avc, hevc, true, false))
    }

    @Test fun compatible2kUsesHevcThenAvc() {
        assertEquals(listOf(hevc, avc), MediaQuality.aerialCandidates(avc, hevc, false, true))
    }

    @Test fun no4kSubstitutionForNonstandardUrl() {
        val special = "https://example.org/not_a_2k_variant.mov"
        assertEquals(listOf(special, avc), MediaQuality.aerialCandidates(avc, special, true, true))
    }

    @Test fun missingHevcFallsBackToAvc() {
        assertEquals(listOf(avc), MediaQuality.aerialCandidates(avc, null, true, true))
    }
}
