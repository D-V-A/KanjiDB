package com.example.kanjidb.ui.training

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kanjidb.data.dictionary.DictionaryKanji
import com.example.kanjidb.data.dictionary.KanjiSummary
import com.example.kanjidb.data.user.UserKanjiStateDao
import kotlinx.coroutines.*

/** Process lifetime only, like Explore/Recommended. Holds no Activity, navigation or UI callbacks. */
internal object TrainingState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var session by mutableStateOf<TrainingSession?>(null)
        private set
    var wordSession by mutableStateOf<WordTrainingSession?>(null)
        private set
    var kanjiResultsScroll = androidx.compose.foundation.lazy.LazyListState()
        private set
    var wordResultsWords by mutableStateOf(false)
        private set
    var wordResultsKanjiScroll = androidx.compose.foundation.lazy.LazyListState()
        private set
    var wordResultsWordsScroll = androidx.compose.foundation.lazy.LazyListState()
        private set

    fun switchWordResults(): Boolean {
        if (!saving) wordResultsWords = !wordResultsWords
        return wordResultsWords
    }

    private fun resetWordResultsPresentation() {
        wordResultsWords = false
        wordResultsKanjiScroll = androidx.compose.foundation.lazy.LazyListState()
        wordResultsWordsScroll = androidx.compose.foundation.lazy.LazyListState()
    }

    val active get() = session != null || wordSession != null
    var wordCards: Map<String, KanjiSummary> = emptyMap()
        private set
    var cards: Map<String, DictionaryKanji> = emptyMap()
        private set
    var saving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set

    var fixedWordPool by mutableStateOf<List<String>?>(null)
        private set

    fun prepareFixedWords(pool: List<String>) {
        check(!active && !saving && pool.isNotEmpty())
        fixedWordPool = pool.distinct().toList()
    }

    fun clearFixedPool() { fixedWordPool = null }

    fun start(mode: TrainingMode, kanji: List<DictionaryKanji>) {
        check(!active && !saving)
        kanjiResultsScroll = androidx.compose.foundation.lazy.LazyListState()
        cards = kanji.associateBy { it.character }
        session = TrainingSession.start(mode, kanji.map { it.character })
        saveFailed = false
    }

    fun update(transform: (TrainingSession) -> TrainingSession) {
        if (!saving) session = session?.let(transform)
    }

    fun startWords(plan: WordTrainingPlan, summaries: Map<String, KanjiSummary>) {
        check(!active && !saving)
        val started = WordTrainingSession.start(plan)
        wordCards = summaries.toMap()
        resetWordResultsPresentation()
        wordSession = started
        fixedWordPool = null
        saveFailed = false
    }

    fun updateWords(transform: (WordTrainingSession) -> WordTrainingSession) {
        if (!saving) wordSession = wordSession?.let(transform)
    }

    /** Cancel has no DAO dependency and cannot apply actions. */
    fun cancel() {
        if (saving) return
        fixedWordPool = null
        session = null
        wordSession = null
        resetWordResultsPresentation()
        kanjiResultsScroll = androidx.compose.foundation.lazy.LazyListState()
        cards = emptyMap()
        wordCards = emptyMap()
        saveFailed = false
    }

    fun finish(dao: UserKanjiStateDao, bulk: Boolean = false) {
        var current = session ?: return
        if (saving || !current.complete) return
        if (bulk) {
            current = current.withBulkActions()
            // Keep the explicit bulk choices if saving fails, so normal Finish retries them.
            session = current
        }
        commit(dao, current.finishAssignments())
    }

    fun finishWords(dao: UserKanjiStateDao, bulk: com.example.kanjidb.ui.LearningState? = null) {
        var current = wordSession ?: return
        if (saving || !current.complete) return
        if (bulk != null) {
            current = current.withBulkActions(bulk)
            wordSession = current
        }
        commit(dao, current.finishAssignments())
    }

    private fun commit(dao: UserKanjiStateDao, assignments: Map<String, com.example.kanjidb.ui.LearningState>) {
        saving = true
        saveFailed = false
        // Finishing survives Activity recreation; navigation cannot discard a committing session.
        scope.launch {
            try {
                dao.applyStates(assignments)
                session = null
                wordSession = null
                resetWordResultsPresentation()
                kanjiResultsScroll = androidx.compose.foundation.lazy.LazyListState()
                cards = emptyMap()
                wordCards = emptyMap()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.e("Training", "Cannot finish training", error)
                saveFailed = true
            } finally { saving = false }
        }
    }
}
