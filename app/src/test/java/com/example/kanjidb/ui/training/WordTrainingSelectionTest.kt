package com.example.kanjidb.ui.training

import com.example.kanjidb.data.dictionary.*
import org.junit.Assert.*
import org.junit.Test

class WordTrainingSelectionTest {
    private fun word(written: String, id: Long = 1, common: Boolean = true, priority: Int = 1000,
        reading: String = "reading", meanings: List<String> = listOf("meaning"), primary: String? = "meaning") =
        WordTrainingCandidate(RelatedWordCandidate(DictionaryWord(id, written, reading, meanings),
            common, priority, common, 0, writtenKanji(written).distinct().map { RelatedWordKanji(it, 1, 5) }), primary)

    @Test fun defaultsCoverageNormalizationAndSizeAreShared() {
        assertEquals(WordPrompt.BOTH, WordTrainingSettings().prompt)
        assertEquals(WordLength.SHORT, WordTrainingSettings().length)
        assertEquals(1, WordTrainingSettings().targetCoverage)
        assertEquals(1, normalizedCoverage(""))
        assertEquals(1, normalizedCoverage("-5"))
        assertEquals(3, normalizedCoverage("3"))
        assertEquals(5, normalizedCoverage("999999999999999999999"))
        assertEquals(50, sessionSizes(100).last())
        assertEquals(listOf(3), sessionSizes(3))
    }

    @Test fun lengthCountsKanjiOccurrencesNotKanaOrSurrogatePairs() {
        assertEquals(listOf("食", "物"), writtenKanji("食べ物"))
        assertEquals(listOf("申", "込"), writtenKanji("申し込み"))
        assertEquals(listOf("日", "日"), writtenKanji("日々日"))
        assertEquals(listOf("𠮷", "野"), writtenKanji("𠮷野かな！"))
        assertTrue(WordLength.SHORT.accepts(3)); assertFalse(WordLength.SHORT.accepts(4))
        assertTrue(WordLength.ANY.accepts(20))
        assertFalse(WordLength.LONGER_TWO.accepts(2)); assertTrue(WordLength.LONGER_TWO.accepts(3))
        assertFalse(WordLength.LONGER_THREE.accepts(3)); assertTrue(WordLength.LONGER_THREE.accepts(4))
    }

    @Test fun knownAloneDoesNotMakeCandidateAndUnknownContextIsAllowed() {
        val plan = WordTrainingSelection.build(listOf(word("車"), word("自転車", id = 2)), listOf("転"),
            setOf("車"), WordTrainingSettings())
        assertEquals(listOf("自転車"), plan.words.map { it.word.written })
        assertEquals(1, plan.coverage["転"])
    }

    @Test fun numeralKnownIsInsufficientAndEveryNumeralKanjiMustBeInPool() {
        val counters = listOf(word("一本"), word("二本", id = 2), word("二十三本", id = 3))
        assertTrue(WordTrainingSelection.build(counters, listOf("本"), setOf("一", "二", "三", "十"),
            WordTrainingSettings(length = WordLength.ANY)).words.isEmpty())
        val accepted = WordTrainingSelection.build(counters, listOf("本", "二"), setOf("一", "三", "十"),
            WordTrainingSettings(length = WordLength.ANY))
        assertEquals(listOf("二本"), accepted.words.map { it.word.written })
        val lexical = WordTrainingSelection.build(listOf(word("一部"), word("一番", id = 2)),
            listOf("部", "番"), setOf("一"), WordTrainingSettings())
        assertEquals(2, lexical.words.size)
    }

    @Test fun counterDedupRunsAfterEligibilityAndKeepsBestPedagogicalRepresentative() {
        val plan = WordTrainingSelection.build(listOf(word("一本", priority = 3520), word("二本", id = 2, priority = 500),
            word("三本", id = 3)), listOf("本", "二"), emptySet(), WordTrainingSettings())
        assertEquals(listOf("二本"), plan.words.map { it.word.written })
        assertEquals(1, plan.coverage["二"])
    }

    @Test fun tiersDominateButLongerFiltersSkipThem() {
        val first = word("今日", priority = 0)
        val second = word("日本語", id = 2, priority = 3520)
        val third = word("日本鳥", id = 3, priority = 3520)
        val last = word("日語", id = 4, priority = 3520)
        val pool = setOf("今", "日", "本")
        assertEquals(1, WordTrainingSelection.tier(first, pool, setOf("語"), WordLength.SHORT))
        assertEquals(2, WordTrainingSelection.tier(second, pool, setOf("語"), WordLength.SHORT))
        assertEquals(3, WordTrainingSelection.tier(third, pool, setOf("語"), WordLength.SHORT))
        assertEquals(4, WordTrainingSelection.tier(last, pool, setOf("語"), WordLength.SHORT))
        assertEquals("今日", WordTrainingSelection.build(listOf(last, third, second, first), pool.toList(),
            setOf("語"), WordTrainingSettings()).words.first().word.written)
        assertEquals(4, WordTrainingSelection.tier(second, pool, setOf("語"), WordLength.LONGER_TWO))
        assertEquals(4, WordTrainingSelection.tier(second, pool, setOf("語"), WordLength.LONGER_THREE))
        assertEquals(listOf("日本語"), WordTrainingSelection.build(listOf(second, third), pool.toList(), setOf("語"),
            WordTrainingSettings(length = WordLength.LONGER_TWO)).words.map { it.word.written })
    }

    @Test fun uncoveredTargetsBeatAlreadyIntroducedTargetsForNextWord() {
        val today = word("今日", priority = 4000)
        val sample = word("見本", id = 2, priority = 0)
        val japanese = word("日本語", id = 3, priority = 3900)
        val plan = WordTrainingSelection.build(listOf(japanese, sample, today), listOf("今", "日", "見", "本"),
            setOf("語"), WordTrainingSettings(targetCoverage = 2))
        // Both first words are tier 1; sample introduces 見/本 before reusing 日 in the next tier.
        assertEquals(listOf("今日", "見本", "日本語"), plan.words.map { it.word.written })
        assertEquals(mapOf("今" to 1, "日" to 2, "見" to 1, "本" to 2), plan.coverage)
    }

    @Test fun dynamicPriorityWorksWithinOneTierWithoutAnAutomaticLengthBonus() {
        val a = word("今日", priority = 4000)
        val b = word("日本語", id = 2, priority = 3900)
        val c = word("見本", id = 3, priority = 0)
        val plan = WordTrainingSelection.build(listOf(b, c, a), listOf("今", "日", "見", "本", "語"), emptySet(),
            WordTrainingSettings(targetCoverage = 2))
        // The three-target word introduces the most targets initially; afterward 今日 and 見本 each introduce one.
        assertEquals("日本語", plan.words.first().word.written)
        assertEquals("今日", plan.words[1].word.written)
        val short = WordTrainingSelection.build(listOf(word("食べ物", priority = 3000), word("食", id = 2)),
            listOf("食"), setOf("物"), WordTrainingSettings())
        // Tier 1 for a single-target word still precedes a word with extra Known context, regardless of quality.
        assertEquals("食", short.words.first().word.written)
    }

    @Test fun balancingTargetsMinimizesSessionSizeAndDoesNotRepeatWordsForCoverage() {
        val plan = WordTrainingSelection.build(listOf(word("今日"), word("今", id = 2), word("日", id = 3)),
            listOf("今", "日"), emptySet(), WordTrainingSettings())
        assertEquals(1, plan.words.size)
        assertEquals(mapOf("今" to 1, "日" to 1), plan.coverage)
        val lacking = WordTrainingSelection.build(listOf(word("今日")), listOf("今", "日", "月"), emptySet(),
            WordTrainingSettings(targetCoverage = 5))
        assertEquals(1, lacking.words.size)
        assertEquals(listOf("月"), lacking.uncovered)
        assertEquals(3, lacking.belowTarget)
    }

    @Test fun dedupReusesSameEntryReadingGlossThresholdAndSessionWrittenUniqueness() {
        val words = listOf(word("食べ物", meanings = listOf("a", "b", "c")),
            word("食物", meanings = listOf("a", "b", "c", "d"), priority = 2000),
            word("食物", id = 99, reading = "other", meanings = listOf("different")))
        val plan = WordTrainingSelection.build(words, listOf("食", "物"), emptySet(), WordTrainingSettings(targetCoverage = 5))
        assertEquals(listOf("食物"), plan.words.map { it.word.written })
    }

    @Test fun emptyMissingMeaningLowQualityAndRestrictiveFiltersStaySafe() {
        val pool = listOf("日")
        assertTrue(WordTrainingSelection.build(emptyList(), pool, emptySet(), WordTrainingSettings()).words.isEmpty())
        val noMeaning = word("日", primary = null)
        assertTrue(WordTrainingSelection.build(listOf(noMeaning), pool, emptySet(), WordTrainingSettings()).words.isEmpty())
        assertEquals(1, WordTrainingSelection.build(listOf(noMeaning), pool, emptySet(),
            WordTrainingSettings(prompt = WordPrompt.READING)).words.size)
        assertTrue(WordTrainingSelection.build(listOf(word("日".repeat(27), common = false, priority = 0)), pool,
            emptySet(), WordTrainingSettings(length = WordLength.ANY)).words.isEmpty())
        assertTrue(WordTrainingSelection.build(listOf(word("日")), pool, emptySet(),
            WordTrainingSettings(length = WordLength.LONGER_THREE)).words.isEmpty())
    }

    @Test fun sameSnapshotIsDeterministicAndLargeSessionsAreNotTruncated() {
        val pool = (0..39).map { String(Character.toChars(0x4E00 + it)) }
        val words = pool.mapIndexed { index, character -> word(character, id = index.toLong()) }
        val a = WordTrainingSelection.build(words, pool, emptySet(), WordTrainingSettings())
        val b = WordTrainingSelection.build(words.reversed(), pool, emptySet(), WordTrainingSettings())
        assertEquals(a, b)
        assertEquals(40, a.words.size)
        assertTrue(a.coverage.values.all { it == 1 })
    }
}
