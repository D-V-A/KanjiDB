package com.example.kanjidb.data.dictionary

import org.junit.Assert.*
import org.junit.Test

class RelatedWordsRankerTest {
    private fun candidate(
        written: String, id: Long = 1, common: Boolean = false, priority: Int = 0,
        reading: String = "reading", meanings: List<String> = listOf("meaning"),
        kanji: List<RelatedWordKanji> = emptyList(), primaryCommon: Boolean = common
    ) = RelatedWordCandidate(DictionaryWord(id, written, reading, meanings), common, priority,
        primaryCommon, 0, kanji)

    @Test
    fun commonPriorityAndLengthPreferUsefulShortWordsWithoutFilteringLongOnes() {
        val ordinary = candidate("日本", common = true, priority = 2400)
        val obscure = candidate("本物", id = 2)
        val long = candidate("本".repeat(27), id = 3, common = true, priority = 2400)
        assertEquals(listOf(ordinary.word, obscure.word, long.word),
            RelatedWordsRanker.rank(listOf(long, obscure, ordinary), "本"))
        assertTrue(RelatedWordsRanker.score(ordinary, "本") > RelatedWordsRanker.score(ordinary.copy(maxReadingPriority = 0), "本"))
        assertEquals(RelatedWordsRanker.score(ordinary.copy(maxReadingPriority = 4000), "本"),
            RelatedWordsRanker.score(ordinary.copy(maxReadingPriority = Int.MAX_VALUE), "本"), 0.0)
    }

    @Test
    fun counterFamiliesKeepBestVariantAcrossEntriesAndDecimalSpellings() {
        val words = listOf(candidate("二本", id = 2), candidate("３本", id = 3),
            candidate("一本", id = 1, common = true, priority = 1370),
            candidate("四本", id = 4), candidate("1本", id = 5),
            candidate("二人", id = 6), candidate("一人", id = 7, common = true, priority = 3520))
        val result = RelatedWordsRanker.rank(words, "本")
        assertEquals(setOf("一本", "一人"), result.map { it.written }.toSet())
        assertEquals(2, result.size)
        assertEquals("#本", RelatedWordsRanker.counterFamily("二十三本"))
        assertEquals("#本", RelatedWordsRanker.counterFamily("１２３本"))
        assertEquals("#人", RelatedWordsRanker.counterFamily("10人"))
    }

    @Test
    fun whitelistAndCanonicalNumeralsPreserveLexicalWordsAndLongerExpressions() {
        listOf("一番", "一部", "万人", "千六本", "一本気", "三人寄れば文殊の知恵",
            "一時", "一分", "万歳", "一日", "一月", "一年", "一十本", "本").forEach {
            assertNull(it, RelatedWordsRanker.counterFamily(it))
        }
        val lexical = listOf("一番", "一部", "万人", "一本気").mapIndexed { index, word ->
            candidate(word, id = index.toLong())
        }
        assertEquals(lexical.map { it.word }.toSet(), RelatedWordsRanker.rank(lexical, "一").toSet())
        listOf("本", "人", "個", "枚", "匹", "台", "冊", "回", "階").forEach {
            assertEquals("#$it", RelatedWordsRanker.counterFamily("二$it"))
        }
    }

    @Test
    fun currentKanjiIsNotPenalizedAndOtherKanjiSignalsAreSoft() {
        val rare = RelatedWordKanji("本", null, null)
        val word = candidate("本", kanji = listOf(rare))
        assertEquals(RelatedWordsRanker.score(word.copy(kanji = emptyList()), "本"),
            RelatedWordsRanker.score(word, "本"), 0.0)
        val easyOther = word.copy(kanji = listOf(rare, RelatedWordKanji("日", 1, 5)))
        val hardOther = word.copy(kanji = listOf(rare, RelatedWordKanji("日", null, null)))
        assertTrue(RelatedWordsRanker.score(easyOther, "本") > RelatedWordsRanker.score(hardOther, "本"))
        assertEquals(listOf(hardOther.word), RelatedWordsRanker.rank(listOf(hardOther), "本"))
        assertEquals(RelatedWordsRanker.score(easyOther, "本"),
            RelatedWordsRanker.score(easyOther.copy(kanji = easyOther.kanji + easyOther.kanji), "本"), 0.0)
    }

    @Test
    fun existingEquivalenceSelectsBetterSpellingButKeepsDifferentReadingsAndSenses() {
        val low = candidate("ひと山", meanings = listOf("a", "b", "c"))
        val best = candidate("一山", common = true, meanings = listOf("a", "b", "c", "d"))
        val differentReading = candidate("壱山", reading = "different", meanings = listOf("a", "b", "c"))
        val differentSense = candidate("一やま", meanings = listOf("other"))
        val result = RelatedWordsRanker.rank(listOf(low, differentSense, best, differentReading), "山")
        assertEquals(listOf(best.word, differentSense.word, differentReading.word), result)
    }

    @Test
    fun deterministicTiesDoNotDependOnCandidateInputOrder() {
        val words = listOf(candidate("本語", id = 2), candidate("語本", id = 1),
            candidate("本本", id = 1, reading = "different"))
        val expected = listOf(words[2].word, words[1].word, words[0].word)
        assertEquals(expected, RelatedWordsRanker.rank(words, "本"))
        assertEquals(expected, RelatedWordsRanker.rank(words.reversed(), "本"))
    }

    @Test
    fun rankSeesAllCandidatesBeforeTheUiTakesSix() {
        val ordinary = (1L..20L).map { candidate("本語$it", id = it) }
        val useful = candidate("本気", id = 99, common = true, priority = 2300)
        val result = RelatedWordsRanker.rank(ordinary + useful, "本")
        assertEquals(21, result.size)
        assertEquals(useful.word, result.take(6).first())
    }

    @Test
    fun lengthPenaltyCountsUnicodeCodePointsAndNumericPenaltyIsSoft() {
        val bmp = candidate("本".repeat(5))
        val supplementary = candidate("𠮷".repeat(5))
        assertEquals(RelatedWordsRanker.score(bmp, "本"), RelatedWordsRanker.score(supplementary, "本"), 0.0)
        assertEquals(RelatedWordsRanker.score(candidate("一部"), "一") - 12.0,
            RelatedWordsRanker.score(candidate("一本"), "一"), 0.0)
        val counter = candidate("一本")
        assertEquals(listOf(counter.word), RelatedWordsRanker.rank(listOf(counter), "本"))
    }
}
