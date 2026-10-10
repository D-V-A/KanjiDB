package com.example.kanjidb.ui.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.kanjidb.data.dictionary.KanjiSearchPage
import com.example.kanjidb.data.dictionary.WordSearchPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Both mode sessions belong to the existing Search back-stack entry. */
class SearchState(private val savedState: SavedStateHandle) : ViewModel() {
    var wordMode by mutableStateOf(savedState.get<Boolean>("wordMode") ?: false)
        private set

    private data class Request(val query: String, val limit: Int, val retry: Int)
    private class ModeState<T>(query: String, limit: Int, private val emptyPage: T) {
        var query by mutableStateOf(query)
        var limit by mutableIntStateOf(limit)
        var retry by mutableIntStateOf(0)
        var page by mutableStateOf(emptyPage)
        var loading by mutableStateOf(false)
        var failed by mutableStateOf(false)
        var listState by mutableStateOf(LazyListState())
        var completed: Request? = null
        fun request() = Request(query, limit, retry)
        fun resetResults() {
            limit = 10
            retry = 0
            completed = null
            page = emptyPage
            listState = LazyListState()
            loading = query.isNotBlank()
            failed = false
        }
    }

    // Restore the previous single-session metadata only into its original active mode.
    private fun restoredQuery(words: Boolean): String = savedState.get<String>(queryKey(words))
        ?: if (words == wordMode) savedState.get<String>("query").orEmpty() else ""
    private fun restoredLimit(words: Boolean): Int = savedState.get<Int>(limitKey(words))
        ?: if (words == wordMode) savedState.get<Int>("limit") ?: 10 else 10
    private fun queryKey(words: Boolean) = if (words) "wordsQuery" else "kanjiQuery"
    private fun limitKey(words: Boolean) = if (words) "wordsLimit" else "kanjiLimit"
    private val kanjiState = ModeState(restoredQuery(false), restoredLimit(false), KanjiSearchPage(emptyList(), false))
    private val wordsState = ModeState(restoredQuery(true), restoredLimit(true), WordSearchPage(emptyList(), false))
    private val active: ModeState<*> get() = if (wordMode) wordsState else kanjiState

    init {
        savedState[queryKey(false)] = kanjiState.query
        savedState[queryKey(true)] = wordsState.query
        savedState[limitKey(false)] = kanjiState.limit
        savedState[limitKey(true)] = wordsState.limit
        savedState.remove<String>("query")
        savedState.remove<Int>("limit")
    }

    val query get() = active.query
    val limit get() = active.limit
    val retry get() = active.retry
    val kanjiPage get() = kanjiState.page
    val wordPage get() = wordsState.page
    val loading get() = active.loading
    val failed get() = active.failed
    val listState get() = active.listState

    fun changeQuery(value: String) {
        val session = active
        if (session.query == value) return
        session.query = value
        savedState[queryKey(wordMode)] = value
        session.resetResults()
        savedState[limitKey(wordMode)] = session.limit
    }

    fun switchMode() {
        wordMode = !wordMode
        savedState["wordMode"] = wordMode
    }

    fun showMore() {
        active.limit += 10
        savedState[limitKey(wordMode)] = active.limit
    }

    fun retrySearch() { active.retry++ }

    internal suspend fun search(
        kanji: suspend (String, Int) -> KanjiSearchPage,
        words: suspend (String, Int) -> WordSearchPage
    ) {
        if (wordMode) searchSession(wordsState, words) else searchSession(kanjiState, kanji)
    }

    private suspend fun <T> searchSession(session: ModeState<T>, search: suspend (String, Int) -> T) {
        val requested = session.request()
        if (requested.query.isBlank() || requested == session.completed) return
        session.loading = true
        session.failed = false
        try {
            delay(250)
            val page = search(requested.query, requested.limit)
            // Completion always belongs to the captured session, even if the visible mode changed.
            if (requested == session.request()) {
                session.page = page
                session.completed = requested
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (requested == session.request()) {
                android.util.Log.e("Search", "Cannot search dictionary", error)
                session.failed = true
                // Navigation/mode round trips retain errors; Retry explicitly requests another SQL run.
                session.completed = requested
            }
        } finally {
            if (requested == session.request()) session.loading = false
        }
    }
}
