package com.example.kanjidb.ui.training

import com.example.kanjidb.data.dictionary.DictionaryKanji
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.primaryMeaning
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TrainingSessionTest {
    private val pool = listOf("a", "b", "c", "d", "e")
    private fun start(mode: TrainingMode = TrainingMode.NEW) = TrainingSession.start(mode, pool, Random(1))
    private fun finish(session: TrainingSession, mistakes: Set<String> = emptySet()): TrainingSession {
        var current = session
        while (!current.complete) {
            val result = if (current.currentCharacter in mistakes) TrainingResult.INCORRECT else TrainingResult.CORRECT
            current = current.reveal().answer(result)
        }
        return current
    }
    private fun mixed() = finish(start(), setOf("b", "d"))
    private fun subset() = mixed().practice(PracticeKind.MISTAKES, Random(2))

    @Test fun randomSelectionHasRequestedSizeWithoutDuplicates() {
        repeat(30) { seed ->
            val result = selectTrainingPool(pool + pool, 3, Random(seed))
            assertEquals(3, result.size)
            assertEquals(3, result.toSet().size)
            assertTrue(pool.containsAll(result))
        }
    }
    @Test fun selectionLimitsAvailableAndMaximum() {
        assertEquals(5, selectTrainingPool(pool, 50).size)
        assertEquals(50, selectTrainingPool((1..100).map(Int::toString), 100).size)
        assertTrue(selectTrainingPool(emptyList(), 5).isEmpty())
        assertTrue(selectTrainingPool(pool, -1).isEmpty())
    }
    @Test fun regularSessionSizes() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 35, 40), sessionSizes(40))
        assertEquals((5..50 step 5).toList(), sessionSizes(200))
    }
    @Test fun irregularSessionMaximumIsSelectable() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 35, 37), sessionSizes(37))
    }
    @Test fun smallAndEmptySources() {
        assertEquals(listOf(3), sessionSizes(3))
        assertEquals(3, normalizedSessionSize("50", 3))
        assertEquals(listOf(0), sessionSizes(0))
        assertEquals(0, normalizedSessionSize("10", 0))
        assertEquals(1, TrainingSession.start(TrainingMode.NEW, listOf("a")).attempt.size)
    }
    @Test fun typedSizeNormalizesSafely() {
        assertEquals(35, normalizedSessionSize("36", 37))
        assertEquals(37, normalizedSessionSize("37", 37))
        assertEquals(37, normalizedSessionSize("999999999999999999999999", 37))
        assertEquals(50, normalizedSessionSize("99", 90))
        assertEquals(5, normalizedSessionSize("-999", 37))
        assertEquals(5, normalizedSessionSize("", 37))
        assertEquals(5, normalizedSessionSize("invalid", 37))
        assertEquals(15, normalizedSessionSize("14", 37))
    }
    @Test fun firstAttemptUsesEntirePool() {
        val s = start()
        assertEquals(pool, s.pool)
        assertEquals(pool.toSet(), s.attempt.toSet())
        assertEquals(pool.toSet(), s.questionOrder.toSet())
        assertEquals(0, s.questionIndex)
        assertFalse(s.revealed)
        assertTrue(s.lastResults.isEmpty())
    }
    @Test fun answerRequiresRevealAndClearsItForNextQuestion() {
        val s = start()
        assertEquals(s, s.answer(TrainingResult.CORRECT))
        val next = s.reveal().answer(TrainingResult.CORRECT)
        assertEquals(1, next.questionIndex)
        assertFalse(next.revealed)
        assertEquals(TrainingResult.CORRECT, next.lastResults[s.currentCharacter])
    }
    @Test fun completedAttemptCannotBeAnsweredAgain() {
        val s = mixed()
        assertEquals(s, s.reveal().answer(TrainingResult.INCORRECT))
        assertNull(s.currentCharacter)
    }
    @Test fun mistakesUsesOnlyLatestAttemptIncorrect() {
        val s = finish(subset(), setOf("d"))
        assertEquals(listOf("d"), s.practiceOptions().first { it.kind == PracticeKind.MISTAKES }.characters)
        val next = s.practice(PracticeKind.MISTAKES)
        assertEquals(listOf("d"), next.attempt)
        assertEquals(listOf("d"), next.questionOrder)
        assertEquals(0, next.questionIndex)
    }
    @Test fun mistakesExcludesStaleIncorrectOutsideLatestAttempt() {
        val s = finish(subset(), setOf("d"))
        val smaller = s.practice(PracticeKind.MISTAKES)
        assertFalse(smaller.participated("b"))
        val completed = finish(smaller)
        assertTrue(completed.practiceOptions().none { it.kind == PracticeKind.MISTAKES })
    }
    @Test fun currentIterationKeepsWholePreviousSubset() {
        val s = finish(subset(), setOf("d")).practice(PracticeKind.CURRENT)
        assertEquals(setOf("b", "d"), s.attempt.toSet())
        assertEquals(s.attempt.toSet(), s.questionOrder.toSet())
    }
    @Test fun allReturnsToOriginalPool() {
        val s = finish(subset()).practice(PracticeKind.ALL)
        assertEquals(pool, s.attempt)
        assertEquals(pool.toSet(), s.questionOrder.toSet())
    }
    @Test fun resultsOrderNeverFollowsQuestionShuffle() {
        var s = mixed()
        repeat(20) { seed ->
            s = finish(s.practice(PracticeKind.ALL, Random(seed)))
            assertEquals(pool, s.pool)
        }
    }
    @Test fun poolIsCopiedFromCaller() {
        val input = pool.toMutableList()
        val s = TrainingSession.start(TrainingMode.NEW, input)
        input.clear()
        assertEquals(pool, s.pool)
    }
    @Test fun nonParticipantsKeepLastResult() {
        val before = mixed()
        val after = finish(before.practice(PracticeKind.MISTAKES))
        assertEquals(before.lastResults["a"], after.lastResults["a"])
        assertEquals(before.lastResults["c"], after.lastResults["c"])
    }
    @Test fun participatingKanjiUpdatesLastResult() {
        val s = finish(subset())
        assertEquals(TrainingResult.CORRECT, s.lastResults["b"])
        assertEquals(TrainingResult.CORRECT, s.lastResults["d"])
    }
    @Test fun participationReflectsLatestAttemptForGrayOut() {
        val s = finish(subset())
        pool.forEach { assertEquals(it in setOf("b", "d"), s.participated(it)) }
        assertEquals(pool.size, s.pool.size)
    }
    @Test fun pendingActionTogglesOff() {
        val s = mixed().toggleAction("b", LearningState.LEARNING)
        assertEquals(LearningState.LEARNING, s.pending["b"])
        assertFalse(s.toggleAction("b", LearningState.LEARNING).pending.containsKey("b"))
    }
    @Test fun newModeActionsAreMutuallyExclusive() {
        val s = mixed().toggleAction("a", LearningState.LEARNING).toggleAction("a", LearningState.KNOWN)
        assertEquals(mapOf("a" to LearningState.KNOWN), s.pending)
        assertEquals(LearningState.LEARNING, s.toggleAction("a", LearningState.LEARNING).pending["a"])
    }
    @Test fun correctToIncorrectResetsPending() {
        val s = mixed().toggleAction("a", LearningState.KNOWN).practice(PracticeKind.ALL)
        assertFalse(finish(s, setOf("a")).pending.containsKey("a"))
    }
    @Test fun incorrectToCorrectResetsEvenStillAllowedPending() {
        val s = mixed().toggleAction("b", LearningState.LEARNING).practice(PracticeKind.MISTAKES)
        assertFalse(finish(s).pending.containsKey("b"))
    }
    @Test fun sameCorrectPreservesPending() {
        val s = mixed().toggleAction("a", LearningState.KNOWN).practice(PracticeKind.ALL)
        assertEquals(LearningState.KNOWN, finish(s).pending["a"])
    }
    @Test fun sameIncorrectPreservesPending() {
        val s = mixed().toggleAction("b", LearningState.LEARNING).practice(PracticeKind.MISTAKES)
        assertEquals(LearningState.LEARNING, finish(s, setOf("b")).pending["b"])
    }
    @Test fun outsideAttemptPreservesPendingAndRemainsInteractive() {
        val s = finish(mixed().toggleAction("a", LearningState.KNOWN).practice(PracticeKind.MISTAKES))
        assertFalse(s.participated("a"))
        assertEquals(LearningState.KNOWN, s.pending["a"])
        assertEquals(LearningState.LEARNING, s.toggleAction("a", LearningState.LEARNING).pending["a"])
    }
    @Test fun fullAttemptOffersAllAndMistakesWithoutCurrentDuplicate() {
        assertEquals(listOf(PracticeKind.ALL, PracticeKind.MISTAKES), mixed().practiceOptions().map { it.kind })
    }
    @Test fun subsetWithMistakesOffersThreeChoices() {
        assertEquals(listOf(PracticeKind.ALL, PracticeKind.CURRENT, PracticeKind.MISTAKES),
            finish(subset(), setOf("d")).practiceOptions().map { it.kind })
    }
    @Test fun perfectFullAttemptResolvesDirectlyToAll() {
        assertEquals(PracticeKind.ALL, finish(start()).practiceOptions().single().kind)
    }
    @Test fun perfectSubsetStillRequiresChoice() {
        assertEquals(listOf(PracticeKind.ALL, PracticeKind.CURRENT),
            finish(subset()).practiceOptions().map { it.kind })
    }
    @Test fun identicalMistakesOptionIsNotDuplicated() {
        assertEquals(listOf(PracticeKind.ALL), finish(start(), pool.toSet()).practiceOptions().map { it.kind })
        assertEquals(listOf(PracticeKind.ALL, PracticeKind.CURRENT),
            finish(subset(), setOf("b", "d")).practiceOptions().map { it.kind })
    }
    @Test fun modeAndResultControlAvailableActions() {
        val review = finish(start(TrainingMode.REVIEW), setOf("b"))
        assertTrue(review.allowedActions("a").isEmpty())
        assertEquals(listOf(LearningState.LEARNING), review.allowedActions("b"))
        val learning = finish(start(TrainingMode.LEARNING), setOf("b"))
        assertEquals(listOf(LearningState.KNOWN), learning.allowedActions("a"))
        assertTrue(learning.allowedActions("b").isEmpty())
        assertEquals(listOf(LearningState.LEARNING, LearningState.KNOWN), mixed().allowedActions("a"))
        assertEquals(listOf(LearningState.LEARNING), mixed().allowedActions("b"))
    }
    @Test fun invalidOrPrematureActionsAreIgnored() {
        assertTrue(start().toggleAction("a", LearningState.KNOWN).pending.isEmpty())
        assertTrue(mixed().toggleAction("b", LearningState.KNOWN).pending.isEmpty())
        assertTrue(mixed().toggleAction("unknown", LearningState.LEARNING).pending.isEmpty())
        assertTrue(mixed().toggleAction("a", LearningState.NONE).pending.isEmpty())
    }
    @Test fun finishTranslatesOnlyExplicitActionsInPoolOrder() {
        val s = mixed().toggleAction("d", LearningState.LEARNING).toggleAction("a", LearningState.KNOWN)
        assertEquals(mapOf("a" to LearningState.KNOWN, "d" to LearningState.LEARNING), s.finishAssignments())
        assertEquals(listOf("a", "d"), s.finishAssignments().keys.toList())
        assertTrue(mixed().finishAssignments().isEmpty())
        assertEquals(mapOf("b" to LearningState.LEARNING),
            finish(start(TrainingMode.REVIEW), setOf("b")).toggleAction("b", LearningState.LEARNING).finishAssignments())
        assertEquals(mapOf("a" to LearningState.KNOWN),
            finish(start(TrainingMode.LEARNING)).toggleAction("a", LearningState.KNOWN).finishAssignments())
    }
    @Test(expected = IllegalStateException::class)
    fun cannotFinishAnIncompleteAttempt() { start().finishAssignments() }

    @Test fun cancelDiscardsPendingWithoutAnyDao() {
        TrainingState.cancel()
        try {
            TrainingState.start(TrainingMode.NEW, pool.map { card(it) })
            TrainingState.update { finish(it).toggleAction("a", LearningState.KNOWN) }
            assertFalse(requireNotNull(TrainingState.session).pending.isEmpty())
            TrainingState.cancel()
            assertNull(TrainingState.session)
            assertTrue(TrainingState.cards.isEmpty())
            TrainingState.start(TrainingMode.NEW, listOf(card("x")))
            assertTrue(requireNotNull(TrainingState.session).pending.isEmpty())
        } finally { TrainingState.cancel() }
    }
    @Test fun primaryMeaningMatchesDetailsWithoutChangingSourceOrder() {
        val kanji = card("a").copy(meanings = listOf("water", "liquid"))
        assertEquals("Water", kanji.primaryMeaning)
        assertNull(card("b").primaryMeaning)
    }
    private fun card(character: String) = DictionaryKanji(
        character, null, null, null, false, emptyList(), emptyList(), emptyList(), emptyList()
    )
}