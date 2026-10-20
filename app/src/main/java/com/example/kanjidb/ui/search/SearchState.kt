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

/** One search session belongs to the existing Search back-stack entry. */
class SearchState(private val savedState: SavedStateHandle) : ViewModel() {
    var wordMode by mutableStateOf(savedState.get<Boolean>("wordMode") ?: false)
        private set
    // Read only the active legacy scope once; never retain separate mode sessions.
    var query by mutableStateOf(savedState.get<String>("query")
        ?: savedState.get<String>(if (wordMode) "wordsQuery" else "kanjiQuery").orEmpty())
        private set
    var limit by mutableIntStateOf(savedState.get<Int>("limit")
        ?: savedState.get<Int>(if (wordMode) "wordsLimit" else "kanjiLimit") ?: 10)
        private set
    var retry by mutableIntStateOf(0)
        private set
    var kanjiPage by mutableStateOf(KanjiSearchPage(emptyList(), false))
        private set
    var wordPage by mutableStateOf(WordSearchPage(emptyList(), false))
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var listState by mutableStateOf(LazyListState())
        private set

    private data class Request(val words: Boolean, val query: String, val limit: Int,
        val retry: Int, val generation: Long)
    private var generation = 0L
    private var completed: Request? = null
    private var modeSearchPending = false
    private fun request() = Request(wordMode, query, limit, retry, generation)

    init {
        savedState["query"] = query
        savedState["limit"] = limit
        listOf("wordsQuery", "kanjiQuery", "wordsLimit", "kanjiLimit").forEach {
            savedState.remove<Any>(it)
        }
    }

    private fun resetResults() {
        modeSearchPending = false
        generation++
        limit = 10
        savedState["limit"] = limit
        retry = 0
        completed = null
        kanjiPage = KanjiSearchPage(emptyList(), false)
        wordPage = WordSearchPage(emptyList(), false)
        listState = LazyListState()
        loading = query.isNotBlank()
        failed = false
    }

    fun changeQuery(value: String) {
        if (query == value) return
        query = value
        savedState["query"] = value
        resetResults()
    }

    /** Preserve the shared query and search it immediately in the newly selected mode. */
    fun switchMode() {
        wordMode = !wordMode
        savedState["wordMode"] = wordMode
        resetResults()
        modeSearchPending = query.isNotBlank()
    }

    fun showMore() {
        limit += 10
        savedState["limit"] = limit
    }

    fun retrySearch() { retry++ }

    internal suspend fun search(
        kanji: suspend (String, Int) -> KanjiSearchPage,
        words: suspend (String, Int) -> WordSearchPage
    ) {
        val requested = request()
        if (requested.query.isBlank() || requested == completed) return
        loading = true
        failed = false
        try {
            if (!modeSearchPending) delay(250)
            if (requested != request()) return
            if (requested.words) {
                val page = words(requested.query, requested.limit)
                if (requested == request()) wordPage = page
            } else {
                val page = kanji(requested.query, requested.limit)
                if (requested == request()) kanjiPage = page
            }
            if (requested == request()) completed = requested
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (requested == request()) {
                android.util.Log.e("Search", "Cannot search dictionary", error)
                failed = true
                completed = requested
            }
        } finally {
            if (requested == request()) {
                loading = false
                modeSearchPending = false
            }
        }
    }
}
