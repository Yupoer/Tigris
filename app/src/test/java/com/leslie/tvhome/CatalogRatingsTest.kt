package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRatingsTest {
    @Test fun liveTaiwanCataloguePreservesSourceScoresAndArtwork() {
        val body = checkNotNull(javaClass.getResourceAsStream("/justwatch-tw-ratings.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val items = Shelf.localized(JustWatchCatalog.parseTitles(body), true)
        val eden = items.first { it.title == "伊甸之東" }
        assertEquals(8.0, checkNotNull(eden.imdbScore), 0.0001)
        assertEquals(88, eden.rottenTomatoes)
        assertEquals("2026", eden.meta)
        assertTrue(eden.image.startsWith("https://images.justwatch.com/backdrop/"))
        assertTrue("/s1920/" in eden.image)
        val doctorX = items.first { it.title == "Doctor X：白色黑手黨時代" }
        assertNull(doctorX.imdbScore)
        assertNull(doctorX.rottenTomatoes)
        val marriage = items.first { it.title == "嫁金釵" }
        assertEquals(7.7, checkNotNull(marriage.imdbScore), 0.0001)
        assertNull(marriage.rottenTomatoes)
    }

    @Test fun invalidOrAbsentScoresDoNotInventBadgesOrDiscardTitles() {
        for (scoring in listOf(
            "null", "{}", "{\"imdbScore\":null,\"tomatoMeter\":null}",
            "{\"imdbScore\":0,\"tomatoMeter\":-1}",
            "{\"imdbScore\":-4,\"tomatoMeter\":101}",
            "{\"imdbScore\":10.1,\"tomatoMeter\":80.5}",
            "{\"imdbScore\":\"8.1\",\"tomatoMeter\":\"90\"}",
            "{\"imdbScore\":true,\"tomatoMeter\":false}",
            "{\"imdbScore\":1e9999,\"tomatoMeter\":1e9999}",
        )) {
            val item = JustWatchCatalog.parseTitles(response(scoring)).single()
            assertEquals("Title", item.title)
            assertNull(scoring, item.imdbScore)
            assertNull(scoring, item.rottenTomatoes)
        }
    }

    @Test fun genuineZeroPercentRottenTomatoesScoreIsRetained() {
        val item = JustWatchCatalog.parseTitles(response("{\"imdbScore\":1.0,\"tomatoMeter\":0}")).single()
        assertEquals(1.0, checkNotNull(item.imdbScore), 0.0001)
        assertEquals(0, item.rottenTomatoes)
        val maximum = JustWatchCatalog.parseTitles(response("{\"imdbScore\":10,\"tomatoMeter\":100}")).single()
        assertEquals(10.0, checkNotNull(maximum.imdbScore), 0.0001)
        assertEquals(100, maximum.rottenTomatoes)
    }

    @Test fun sourceWithoutTitlesReturnsEmptyCatalogue() {
        assertTrue(JustWatchCatalog.parseTitles("{\"errors\":[{\"message\":\"Unavailable\"}]}").isEmpty())
    }

    private fun response(scoring: String) = """
        {"data":{"popularTitles":{"edges":[{"node":{"id":"tm123","content":{
          "title":"Title","originalReleaseYear":2026,"scoring":$scoring,
          "backdrops":[{"backdropUrl":"/backdrop/123/s1920/title.jpg"}]
        }}}]}}}
    """
}
