package com.example.kanjidb.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class CommonWordsTest {
    private fun word(
        written: String,
        meanings: List<String>,
        entryId: Long = 1L,
        reading: String = "ひとやま"
    ) = DictionaryWord(entryId, written, reading, meanings)

    @Test
    fun normalizesMeaningsAndKeepsFirstForm() {
        val first = word("ひと山", listOf(" A mountain ", "MOUNTAIN", "mountain"))
        val alternative = word("一山", listOf("a mountain", "Mountain"))
        assertEquals(listOf(first), listOf(first, alternative).deduplicateCommonWords())
    }

    @Test
    fun includesThresholdButUsesLargerSetAsDenominator() {
        val first = word("first", listOf("a", "b", "c"))
        val atThreshold = word("second", listOf("a", "b", "c", "d"))
        val belowThreshold = word("third", listOf("a", "b", "c", "d", "e"))
        assertEquals(
            listOf(first, belowThreshold),
            listOf(first, atThreshold, belowThreshold).deduplicateCommonWords()
        )
    }

    @Test
    fun preservesDifferentEntriesReadingsAndMeanings() {
        val words = listOf(
            word("first", listOf("a")),
            word("second", listOf("a"), entryId = 2L),
            word("third", listOf("a"), reading = "いちざん"),
            word("fourth", listOf("b"))
        )
        assertEquals(words, words.deduplicateCommonWords())
    }

    @Test
    fun doesNotMergeThroughDiscardedForms() {
        val first = word("first", listOf("a", "b", "c", "d"))
        val middle = word("middle", listOf("a", "b", "c", "e"))
        val last = word("last", listOf("a", "b", "e", "f"))
        assertEquals(listOf(first, last), listOf(first, middle, last).deduplicateCommonWords())
    }

    @Test
    fun preservesFormsWithoutMeanings() {
        val words = listOf(word("first", emptyList()), word("second", emptyList()))
        assertEquals(words, words.deduplicateCommonWords())
    }
    @Test
    fun groupsRetainHiddenFormsWithoutTransitiveMerging() {
        val first = word("first", listOf("a", "b", "c", "d"))
        val middle = word("middle", listOf("a", "b", "c", "e"))
        val last = word("last", listOf("a", "b", "e", "f"))
        assertEquals(
            listOf(listOf(first, middle), listOf(last)),
            listOf(first, middle, last).groupCommonWords()
        )
    }
    @Test
    fun combinedWordsKeepCommonFirstAndDoNotRepeatWrittenForms() {
        for (commonCount in listOf(0, 3, 6, 8)) {
            val common = (1..commonCount).map { word("common$it", listOf("meaning$it"), entryId = it.toLong()) }
            val ordinary = (20..29).map { word("other$it", listOf("meaning$it"), entryId = it.toLong()) }
            val all = ordinary + common.map { it.copy(reading = "another reading") }
            val merged = mergeKanjiWords(common, all).deduplicateCommonWords()
            assertEquals(common + ordinary, merged)
            assertEquals((common + ordinary).take(6), merged.take(6))
        }
    }

    @Test
    fun equivalenceIsAppliedAfterMergingBothSources() {
        val common = word("first", listOf("a", "b", "c"))
        val alternative = word("alternative", listOf("a", "b", "c", "d"))
        val remaining = (2..6).map { word("other$it", listOf("meaning$it"), entryId = it.toLong()) }
        val merged = mergeKanjiWords(listOf(common), listOf(alternative) + remaining)
            .deduplicateCommonWords()
        assertEquals(listOf(common) + remaining, merged)
        assertEquals(6, merged.size)
    }
}
