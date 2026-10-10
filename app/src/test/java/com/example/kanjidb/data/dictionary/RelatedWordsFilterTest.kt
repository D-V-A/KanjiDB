package com.example.kanjidb.data.dictionary

import org.junit.Assert.*
import org.junit.Test

class RelatedWordsFilterTest {
    private val sunday = DictionaryWord(1, "\u65e5\u66dc\u65e5", "\u306b\u3061\u3088\u3046\u3073", listOf("Sunday", "day of rest"))

    @Test fun usesExistingWordSearchLanguage() {
        listOf("\u65e5\u66dc\u65e5", "\u306b\u3061\u3088\u3046\u3073", "\u30cb\u30c1\u30e8\u30a6\u30d3", "nitiyoubi", " SUNDAY ", "day of rest",
            "\uff86\uff81\uff96\uff73\uff8b\uff9e").forEach { query ->
            assertEquals(query, listOf(sunday), filterRelatedWords(listOf(sunday), query))
        }
    }

    @Test fun filtersFullSetIncludingHiddenWordsWithoutReranking() {
        val words = (1L..8L).map { id ->
            DictionaryWord(id, "word$id", "", listOf(if (id == 2L || id == 7L || id == 8L) "match" else "other"))
        }
        assertEquals(listOf(words[1], words[6], words[7]), filterRelatedWords(words, "match"))
        assertSame(words, filterRelatedWords(words, ""))
        assertSame(words, filterRelatedWords(words, "   "))
        assertTrue(filterRelatedWords(words, "absent").isEmpty())
    }

    @Test fun queryPreparationKeepsGlobalBehavior() {
        listOf(" NITIYOUBI ", "\u30cb\u30c1\u30e8\u30a6\u30d3", " Sunday ", "\u65e5\u66dc\u65e5", "nichiyoubi", "%", "").forEach { raw ->
            val query = prepareWordSearchQuery(raw)
            assertEquals(raw.trim().lowercase(java.util.Locale.ROOT), query.text)
            assertEquals(normalizeReading(query.text).orEmpty(), query.reading)
            assertEquals(toKatakana(query.reading), query.katakana)
        }
    }
}
