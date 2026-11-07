package com.leslie.tvhome

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class TaiwanTextTest {
    @Test fun sourceTitlesAndAmbiguousPhrasesMatchOpenCcTaiwanReference() {
        val cases = JSONArray(checkNotNull(javaClass.getResourceAsStream("/taiwan-text.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() })
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val source = case.getString("source")
            assertEquals(source, case.getString("taiwan"), TaiwanText.convert(source))
        }
    }

    @Test fun allShelfSourcesUseTaiwanTextOnlyForChineseUi() {
        val item = Shelf.Item("嫁金钗", "软件 · 2026", "https://example.test/image.jpg",
            listOf("https://example.test/backup.jpg"), 7.7, null)
        assertEquals(listOf(item), Shelf.localized(listOf(item), false))
        val translated = Shelf.localized(listOf(item), true).single()
        assertEquals("嫁金釵", translated.title)
        assertEquals("軟體 · 2026", translated.meta)
        assertEquals(item.copy(title = translated.title, meta = translated.meta), translated)
    }
}
