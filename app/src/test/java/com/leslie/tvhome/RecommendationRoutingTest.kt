package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecommendationRoutingTest {
    private val providers = listOf(
        JustWatchCatalog.Provider("nfx", "Netflix", "netflix"),
        JustWatchCatalog.Provider("dnp", "Disney Plus", "disneyplus"),
        JustWatchCatalog.Provider("mxx", "HBO Max", "max"),
        JustWatchCatalog.Provider("ytr", "YouTube Premium", "youtubered"),
    )

    @Test fun knownPackageWinsOverAppLabel() {
        assertEquals("nfx", JustWatchCatalog.select("com.netflix.ninja", "Video", providers)?.code)
    }

    @Test fun installedAppLabelMatchesTaiwanProviderWithoutHandWrittenMapping() {
        assertEquals("dnp", JustWatchCatalog.select("new.disney.tv", "Disney+", providers)?.code)
        assertEquals("mxx", JustWatchCatalog.select("new.max.tv", "Max", providers)?.code)
    }

    @Test fun unrelatedAppDoesNotReceiveSomeOtherServiceCatalogue() {
        assertNull(JustWatchCatalog.select("com.example.files", "File Manager", providers))
        assertNull(JustWatchCatalog.select(YouTubeChannels.PACKAGE, "YouTube", providers))
    }

    @Test fun youtubeUsesItsPublishedRecommendationsBeforeTrending() {
        assertEquals(0, YouTubeChannels.channelPriority("為你推薦"))
        assertEquals(1, YouTubeChannels.channelPriority("Trending"))
        assertEquals(-1, YouTubeChannels.channelPriority("Subscriptions"))
    }
}
