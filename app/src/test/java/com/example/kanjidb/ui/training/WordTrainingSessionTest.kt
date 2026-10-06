package com.example.kanjidb.ui.training

import com.example.kanjidb.data.dictionary.*
import com.example.kanjidb.ui.LearningState
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WordTrainingSessionTest {
    @Test fun repeatDedupComparesMembershipAndKeepsPriorityRatherThanEqualCounts() {
        val options = distinctPracticeSets(listOf(
            PracticeKind.ALL to listOf(1, 2, 3),
            PracticeKind.MISTAKES to listOf(1, 2),
            PracticeKind.CURRENT to listOf(2, 1)
        )) { it }
        assertEquals(listOf(PracticeKind.ALL, PracticeKind.MISTAKES), options.map { it.first })
        assertEquals(2, distinctPracticeSets(listOf(
            PracticeKind.ALL to listOf(1, 2), PracticeKind.CURRENT to listOf(2, 3),
            PracticeKind.MISTAKES to emptyList()
        )) { it }.size)
        assertEquals(1, distinctPracticeSets(listOf(
            PracticeKind.ALL to listOf(1, 2), PracticeKind.MISTAKES to listOf(2, 1),
            PracticeKind.CURRENT to listOf(1, 2)
        )) { it }.size)
    }
    private fun word(written: String, id: Long) = WordTrainingCandidate(
        RelatedWordCandidate(DictionaryWord(id, written, "reading", listOf("meaning")), true, 1000, true, 0,
            emptyList()), "meaning")
    private val first = word("今日", 1)
    private val second = word("日語", 2)
    private fun start(words: List<WordTrainingCandidate> = listOf(first, second)): WordTrainingSession {
        val plan = WordTrainingPlan(listOf("今", "日", "月"), setOf("語"), WordTrainingSettings(), words,
            mapOf("今" to 1, "日" to 2, "月" to 0))
        return WordTrainingSession.start(plan, Random(1))
    }
    private fun finish(session: WordTrainingSession, mistakes: Set<Long> = setOf(1)): WordTrainingSession {
        var current = session
        while (!current.complete) {
            current = current.reveal().answer(if (current.currentWord!!.word.entryId in mistakes)
                TrainingResult.INCORRECT else TrainingResult.CORRECT)
        }
        return current
    }

    @Test fun masksPoolKnownAndKeepsUnknownKanaPunctuationAndSupplementaryCharacters() {
        val mixed = word("自転車、かな！", 1)
        assertEquals("自＊＊、かな！", maskedWord(mixed, setOf("転"), setOf("車")))
        assertEquals("＊野かな", maskedWord(word("𠮷野かな", 2), setOf("𠮷"), emptySet()))
        assertEquals("＊＊＊", maskedWord(word("日日日", 3), setOf("日"), emptySet()))
    }

    @Test fun revealIsRequiredAndVisibleUnknownKanjiAreNotScored() {
        val word = word("自転車", 1)
        val plan = WordTrainingPlan(listOf("転"), setOf("車"), WordTrainingSettings(), listOf(word), mapOf("転" to 1))
        val initial = WordTrainingSession.start(plan, Random(1))
        assertEquals(initial, initial.answer(TrainingResult.CORRECT))
        val complete = initial.reveal().answer(TrainingResult.CORRECT)
        assertEquals(setOf("転", "車"), complete.tallies.keys)
        assertEquals(WordKanjiTally(1, 1), complete.tallies["転"])
        assertFalse(complete.tallies.containsKey("自"))
        assertEquals(complete, complete.answer(TrainingResult.INCORRECT))
    }

    @Test fun repeatedKanjiCountsOncePerWordResponse() {
        val complete = finish(start(listOf(word("日日", 1))))
        assertEquals(WordKanjiTally(1, -1), complete.tallies["日"])
    }

    @Test fun correctIncorrectAndUncoveredProduceThreeStatusesPlusNotTested() {
        val complete = finish(start())
        assertEquals(WordKanjiStatus.FAILURE, complete.tallies["今"]?.status)
        assertEquals(WordKanjiStatus.PARTIAL, complete.tallies["日"]?.status)
        assertEquals(WordKanjiStatus.SUCCESS, complete.tallies["語"]?.status)
        assertEquals(listOf("今", "日", "月", "語"), complete.resultCharacters)
        assertNull(WordKanjiTally().status)
        assertEquals(listOf(LearningState.LEARNING, LearningState.KNOWN), complete.allowedActions("月"))
        assertFalse(complete.bulkTargets(LearningState.KNOWN).contains("月"))
    }

    @Test fun explicitWordMistakesAreIndependentOfKanjiPartialOrFailure() {
        val complete = finish(start())
        assertEquals(setOf(first.key), complete.incorrectWords)
        assertEquals(listOf(first), complete.repeatWords)
        assertEquals(WordKanjiStatus.PARTIAL, complete.tallies["日"]?.status)
        assertFalse(complete.repeatWords.contains(second)) // Its shared kanji is partial but the word was Correct.
        val failure = finish(start(), setOf(1, 2))
        assertEquals(listOf(first, second), failure.repeatWords)
    }

    @Test fun repeatUsesSameSnapshotAndAccumulatesStatsWithoutExpandingWordList() {
        val completed = finish(start())
        val repeating = completed.repeatMistakes(Random(2))
        assertEquals(listOf(first), repeating.questionOrder)
        assertEquals(completed.plan, repeating.plan)
        assertEquals(completed.tallies, repeating.tallies)
        val corrected = finish(repeating, emptySet())
        assertEquals(WordKanjiTally(2, 0), corrected.tallies["今"])
        assertEquals(WordKanjiTally(3, 1), corrected.tallies["日"])
        assertEquals(WordKanjiTally(1, 1), corrected.tallies["語"])
        assertTrue(corrected.repeatWords.isEmpty())
        assertEquals(corrected, corrected.repeatMistakes())
    }

    @Test fun noMistakesDisablesRepeatAndEarlyRepeatDoesNothing() {
        val initial = start()
        assertEquals(initial, initial.repeatMistakes())
        val correct = finish(initial, emptySet())
        assertTrue(correct.incorrectWords.isEmpty())
        assertEquals(correct, correct.repeatMistakes())
    }

    @Test fun bulkActionsExcludePartialPreserveManualPartialDecisionAndDoNotWriteAnything() {
        val completed = finish(start()).toggleAction("日", LearningState.KNOWN)
        assertEquals(listOf("今"), completed.bulkTargets(LearningState.LEARNING))
        assertEquals(listOf("語"), completed.bulkTargets(LearningState.KNOWN))
        assertEquals(mapOf("今" to LearningState.LEARNING, "日" to LearningState.KNOWN),
            completed.withBulkActions(LearningState.LEARNING).finishAssignments())
        assertEquals(mapOf("日" to LearningState.KNOWN, "語" to LearningState.KNOWN),
            completed.withBulkActions(LearningState.KNOWN).finishAssignments())
        assertEquals(mapOf("日" to LearningState.KNOWN), completed.pending)
    }

    @Test fun finishOnlyEmitsExplicitActionsAndPendingChoicesAreExclusive() {
        val completed = finish(start())
        assertTrue(completed.finishAssignments().isEmpty())
        val pending = completed.toggleAction("日", LearningState.LEARNING).toggleAction("日", LearningState.KNOWN)
        assertEquals(mapOf("日" to LearningState.KNOWN), pending.finishAssignments())
        assertTrue(pending.toggleAction("日", LearningState.KNOWN).pending.isEmpty())
        assertEquals(LearningState.KNOWN, completed.toggleAction("今", LearningState.KNOWN).pending["今"])
        assertEquals(LearningState.KNOWN, completed.toggleAction("月", LearningState.KNOWN).pending["月"])
        assertTrue(start().toggleAction("日", LearningState.LEARNING).pending.isEmpty())
    }

    @Test fun newAnswersClearOnlyDecisionsForAffectedKanji() {
        val completed = finish(start()).toggleAction("日", LearningState.KNOWN).toggleAction("語", LearningState.KNOWN)
        val repeated = finish(completed.repeatMistakes(Random(2)), emptySet())
        assertFalse(repeated.pending.containsKey("日"))
        assertEquals(LearningState.KNOWN, repeated.pending["語"])
    }

    @Test fun repeatMenuUsesCurrentSubsetOriginalSessionAndExplicitWordMistakes() {
        val original = finish(start())
        val subset = finish(original.practice(PracticeKind.MISTAKES, Random(2)), emptySet())
        assertEquals(listOf(first), subset.practice(PracticeKind.CURRENT).questionOrder)
        assertEquals(original.plan.words.toSet(), subset.practice(PracticeKind.ALL).questionOrder.toSet())
        assertEquals(subset, subset.practice(PracticeKind.MISTAKES))
        assertEquals(original.plan.settings, subset.practice(PracticeKind.ALL).plan.settings)
        assertEquals(original.plan.known, subset.plan.known)
    }

    @Test fun newKanjiBulkActionsOnlyStageMatchingLastResultsAndPreserveOtherManualChoices() {
        var session = TrainingSession.start(TrainingMode.NEW, listOf("今", "日"), Random(1))
        while (!session.complete) session = session.reveal().answer(
            if (session.currentCharacter == "今") TrainingResult.INCORRECT else TrainingResult.CORRECT)
        val staged = session.withNewBulkActions(LearningState.LEARNING).withNewBulkActions(LearningState.KNOWN)
        assertTrue(staged.complete)
        assertEquals(mapOf("今" to LearningState.LEARNING, "日" to LearningState.KNOWN), staged.pending)
        assertTrue(session.pending.isEmpty())
        assertEquals(staged.pending, staged.finishAssignments())
    }

    @Test(expected = IllegalStateException::class) fun incompleteFinishCannotApplyResults() { start().finishAssignments() }
    @Test(expected = IllegalArgumentException::class) fun emptySessionCannotStart() {
        WordTrainingSession.start(start().plan.copy(words = emptyList()))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateWrittenWordCannotStart() {
        WordTrainingSession.start(start().plan.copy(words = listOf(first, first)))
    }

    @Test fun cancelDiscardsWordSessionPendingAndMetadataWithoutDao() {
        TrainingState.cancel()
        try {
            TrainingState.startWords(start().plan, emptyMap())
            assertTrue(TrainingState.active)
            assertNull(TrainingState.session)
            TrainingState.updateWords { finish(it).toggleAction("日", LearningState.KNOWN) }
            assertFalse(TrainingState.wordSession!!.pending.isEmpty())
            TrainingState.cancel()
            assertFalse(TrainingState.active)
            assertNull(TrainingState.wordSession)
            assertTrue(TrainingState.wordCards.isEmpty())
        } finally { TrainingState.cancel() }
    }
}
