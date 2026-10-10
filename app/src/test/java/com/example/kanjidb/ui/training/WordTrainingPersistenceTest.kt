package com.example.kanjidb.ui.training

import androidx.room.Room
import com.example.kanjidb.data.dictionary.*
import com.example.kanjidb.data.user.UserDatabase
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WordTrainingPersistenceTest {
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UserDatabase::class.java)
        .allowMainThreadQueries().build()

    private fun startCompleted() {
        val a = WordTrainingCandidate(RelatedWordCandidate(DictionaryWord(1, "今日", "きょう", listOf("today")),
            true, 1000, true, 0, emptyList()), "today")
        val b = WordTrainingCandidate(RelatedWordCandidate(DictionaryWord(2, "日語", "にちご", listOf("Japanese")),
            true, 1000, true, 0, emptyList()), "Japanese")
        TrainingState.startWords(WordTrainingPlan(listOf("今", "日"), setOf("語"), WordTrainingSettings(), listOf(a, b),
            mapOf("今" to 1, "日" to 2)), emptyMap())
        TrainingState.updateWords { initial ->
            var current = initial
            while (!current.complete) current = current.reveal().answer(
                if (current.currentWord!!.word.entryId == 1L) TrainingResult.INCORRECT else TrainingResult.CORRECT)
            current
        }
    }

    private fun awaitSave() {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (TrainingState.saving && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        ShadowLooper.idleMainLooper()
        assertFalse("Save should complete", TrainingState.saving)
    }

    @Test fun stagedAndRepeatedResultsDoNotWriteUntilFinishAndEarlyEndDiscardsEverything() {
        val db = database()
        TrainingState.cancel()
        try {
            startCompleted()
            TrainingState.updateWords { it.withBulkActions(LearningState.LEARNING).withBulkActions(LearningState.KNOWN) }
            assertTrue(TrainingState.active)
            assertFalse(TrainingState.saving)
            assertTrue(runBlocking { db.kanjiStates().getAll() }.isEmpty())
            TrainingState.updateWords { it.toggleAction("日", LearningState.KNOWN) }
            assertTrue(runBlocking { db.kanjiStates().getAll() }.isEmpty())
            TrainingState.updateWords { it.repeatMistakes() }
            TrainingState.finishWords(db.kanjiStates()) // Incomplete repeat cannot save earlier pending actions.
            assertTrue(runBlocking { db.kanjiStates().getAll() }.isEmpty())
            TrainingState.cancel()
            assertFalse(TrainingState.active)
            assertTrue(runBlocking { db.kanjiStates().getAll() }.isEmpty())
        } finally { TrainingState.cancel(); db.close() }
    }

    @Test fun presentationAndIndependentScrollSurviveSwitchAndRepeatWithoutRoomWrites() {
        val db = database()
        TrainingState.cancel()
        try {
            startCompleted()
            val kanjiScroll = TrainingState.wordResultsKanjiScroll
            val wordsScroll = TrainingState.wordResultsWordsScroll
            kanjiScroll.requestScrollToItem(3, 24)
            wordsScroll.requestScrollToItem(1, 12)
            TrainingState.updateWords { it.withBulkActions(LearningState.LEARNING) }
            val original = TrainingState.wordSession!!
            assertTrue(TrainingState.switchWordResults())
            assertSame(original, TrainingState.wordSession)
            assertSame(wordsScroll, TrainingState.wordResultsWordsScroll)
            assertFalse(TrainingState.switchWordResults())
            assertSame(kanjiScroll, TrainingState.wordResultsKanjiScroll)
            assertTrue(TrainingState.switchWordResults())
            TrainingState.updateWords { it.repeatMistakes() }
            TrainingState.updateWords { it.reveal().answer(TrainingResult.CORRECT) }
            assertTrue(TrainingState.wordResultsWords)
            assertEquals(original.pending, TrainingState.wordSession!!.pending)
            assertEquals(3, kanjiScroll.firstVisibleItemIndex)
            assertEquals(24, kanjiScroll.firstVisibleItemScrollOffset)
            assertEquals(1, wordsScroll.firstVisibleItemIndex)
            assertEquals(12, wordsScroll.firstVisibleItemScrollOffset)
            assertTrue(runBlocking { db.kanjiStates().getAll() }.isEmpty())
            TrainingState.finishWords(db.kanjiStates())
            awaitSave()
            assertFalse(TrainingState.wordResultsWords)
            assertNotSame(kanjiScroll, TrainingState.wordResultsKanjiScroll)
        } finally { TrainingState.cancel(); db.close() }
    }

    @Test fun finishPersistsOnlyExplicitChoicesAndBulkSuccessExcludesPartialAndFailure() {
        val db = database()
        TrainingState.cancel()
        try {
            startCompleted()
            TrainingState.updateWords { it.toggleAction("日", LearningState.LEARNING) }
            TrainingState.finishWords(db.kanjiStates(), LearningState.KNOWN)
            awaitSave()
            assertFalse(TrainingState.active)
            assertTrue(TrainingState.wordCards.isEmpty())
            val saved = runBlocking { db.kanjiStates().getAll() }.associate { it.character to it.state }
            assertEquals(mapOf("日" to LearningState.LEARNING, "語" to LearningState.KNOWN), saved)
            assertFalse(saved.containsKey("今"))
        } finally { TrainingState.cancel(); db.close() }
    }

    @Test fun failedBulkCommitKeepsSessionChoicesAndRetryUsesSameRoomTransaction() {
        val db = database()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_training BEFORE INSERT ON kanji_state BEGIN SELECT RAISE(ABORT, 'test save failure'); END")
        TrainingState.cancel()
        try {
            startCompleted()
            TrainingState.finishWords(db.kanjiStates(), LearningState.LEARNING)
            awaitSave()
            assertTrue(TrainingState.saveFailed)
            assertTrue(TrainingState.active)
            assertEquals(mapOf("今" to LearningState.LEARNING), TrainingState.wordSession!!.pending)
            assertTrue(runBlocking { db.kanjiStates().getAll() }.isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_training")
            TrainingState.finishWords(db.kanjiStates())
            awaitSave()
            assertFalse(TrainingState.active)
            assertEquals(mapOf("今" to LearningState.LEARNING),
                runBlocking { db.kanjiStates().getAll() }.associate { it.character to it.state })
        } finally { TrainingState.cancel(); db.close() }
    }
}
