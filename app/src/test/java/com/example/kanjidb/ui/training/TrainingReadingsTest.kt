package com.example.kanjidb.ui.training

import org.junit.Assert.*
import org.junit.Test

class TrainingReadingsTest {
    @Test fun dakutenIsIgnoredForBothKanaScripts() {
        assertEquals(trainingReadingKey("カン"), trainingReadingKey("ガン"))
        assertEquals(trainingReadingKey("さ"), trainingReadingKey("ざ"))
    }

    @Test fun handakutenAndDakutenMatchBaseKana() {
        for (reading in listOf("ハ", "バ", "パ")) assertEquals("ハ", trainingReadingKey(reading))
        for (reading in listOf("は", "ば", "ぱ")) assertEquals("は", trainingReadingKey(reading))
    }

    @Test fun decomposedMarksAlsoMatch() {
        assertEquals(trainingReadingKey("ガ"), trainingReadingKey("カ\u3099"))
        assertEquals(trainingReadingKey("パ"), trainingReadingKey("ハ\u309A"))
    }

    @Test fun leadingAndTrailingHyphensAreComparisonOnly() {
        assertEquals("さ・る", trainingReadingKey("-さ・る-"))
        assertEquals(trainingReadingKey("さ・る"), trainingReadingKey("-さ・る"))
    }

    @Test fun longVowelMarkDoesNotCreateAnotherKey() {
        assertEquals(trainingReadingKey("コ"), trainingReadingKey("コー"))
        assertEquals(trainingReadingKey("ココ"), trainingReadingKey("コーコ"))
    }

    @Test fun middleDotIsRetainedInComparisonAndDisplay() {
        assertEquals("さ・る", trainingReadingKey("さ・る"))
        assertNotEquals(trainingReadingKey("さる"), trainingReadingKey("さ・る"))
    }

    @Test fun shortListsAreUnchangedEvenWhenEquivalent() {
        val source = listOf("ハ", "バ", "パ")
        for (count in 0..3) assertEquals(source.take(count), trainingReadings(source.take(count)))
        assertEquals(listOf("-さ・る", "さ・る", "さ・る-"),
            trainingReadings(listOf("-さ・る", "さ・る", "さ・る-")))
    }

    @Test fun longListsPreferDistinctReadingsInDictionaryOrder() {
        assertEquals(listOf("カン", "コン", "キン"),
            trainingReadings(listOf("カン", "ガン", "コン", "キン", "ケン")))
    }

    @Test fun fewerThanThreeKeysBackfillsFirstSkippedVariant() {
        assertEquals(listOf("ハ", "カ", "バ"),
            trainingReadings(listOf("ハ", "バ", "パ", "カ", "ガ")))
    }

    @Test fun oneKeyStillFillsThreeSlotsIncludingExactDuplicates() {
        assertEquals(listOf("ハ", "バ", "パ"), trainingReadings(listOf("ハ", "バ", "パ", "ハー")))
        assertEquals(List(3) { "ハ" }, trainingReadings(List(7) { "ハ" }))
    }

    @Test fun selectedReadingsKeepOriginalSpellingAndDoNotMutateSource() {
        val source = mutableListOf("-ざ・る-", "さ・る", "ゴー", "コ", "ハ")
        val original = source.toList()
        assertEquals(listOf("-ざ・る-", "ゴー", "ハ"), trainingReadings(source))
        assertEquals(original, source)
    }

    @Test fun outputCountIsAlwaysMinOfThreeAndOriginalCount() {
        val examples = listOf("ハ", "バ", "パ", "ハー", "-ハ", "カ", "ガ", "キ")
        for (count in 0..40) {
            val source = List(count) { examples[it % examples.size] }
            assertEquals(minOf(3, count), trainingReadings(source).size)
        }
    }

    @Test fun separateCallsDoNotShareKeysBetweenOnAndKun() {
        assertEquals(listOf("ハ", "カ", "キ"), trainingReadings(listOf("ハ", "バ", "カ", "キ")))
        assertEquals(listOf("ハ", "サ", "タ"), trainingReadings(listOf("ハ", "バ", "サ", "タ")))
    }
}