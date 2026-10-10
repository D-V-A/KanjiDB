package com.example.kanjidb.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.kanjidb.data.dictionary.DictionaryWord
import com.example.kanjidb.data.dictionary.KanjiSearchPage
import com.example.kanjidb.data.dictionary.KanjiSummary
import com.example.kanjidb.data.dictionary.WordSearchPage
import com.example.kanjidb.data.dictionary.WordSearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SearchStateTest {
    private val emptyKanji: suspend (String, Int) -> KanjiSearchPage = { _, _ -> KanjiSearchPage(emptyList(), false) }
    private val emptyWords: suspend (String, Int) -> WordSearchPage = { _, _ -> WordSearchPage(emptyList(), false) }

    @Test fun navigationEntryRetainsWordPagePaginationAndScrollWithoutAnotherSearch() = runBlocking {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchState(SavedStateHandle()) as T
        }
        val state = ViewModelProvider(store, factory)[SearchState::class.java]
        state.switchMode()
        state.changeQuery("sunday")
        var calls = 0
        val search: suspend (String, Int) -> WordSearchPage = { query, limit ->
            calls++
            assertEquals("sunday", query)
            WordSearchPage((1..limit).map {
                WordSearchResult(DictionaryWord(it.toLong(), "word$it", "reading", listOf("Sunday")), "Sunday", "Sunday", null)
            }, true)
        }
        state.search(emptyKanji, search)
        state.showMore()
        state.search(emptyKanji, search)
        state.listState.requestScrollToItem(8, 23)
        val page = state.wordPage
        val scroll = state.listState
        // A new screen composition obtains its existing state from the same navigation entry store.
        val returned = ViewModelProvider(store, factory)[SearchState::class.java]
        returned.search(emptyKanji, search)
        assertSame(state, returned)
        assertSame(page, returned.wordPage)
        assertSame(scroll, returned.listState)
        assertEquals(8, returned.listState.firstVisibleItemIndex)
        assertEquals(23, returned.listState.firstVisibleItemScrollOffset)
        assertEquals(20, returned.limit)
        assertTrue(returned.wordMode)
        assertTrue(returned.wordPage.hasMore)
        assertEquals(2, calls)
        store.clear()
    }

    @Test fun kanjiReturnIsCachedAndModeSwitchKeepsItsSession() = runBlocking {
        val state = SearchState(SavedStateHandle())
        state.changeQuery("day")
        var calls = 0
        val kanji: suspend (String, Int) -> KanjiSearchPage = { _, _ ->
            calls++
            KanjiSearchPage(listOf(KanjiSummary("日", "day")), false)
        }
        state.search(kanji, emptyWords)
        state.listState.requestScrollToItem(0, 12)
        val page = state.kanjiPage
        state.changeQuery("day")
        state.search(kanji, emptyWords)
        assertSame(page, state.kanjiPage)
        assertEquals(12, state.listState.firstVisibleItemScrollOffset)
        assertEquals(1, calls)
        state.changeQuery("sun")
        assertTrue(state.kanjiPage.kanji.isEmpty())
        assertEquals(0, state.listState.firstVisibleItemScrollOffset)
        state.search(kanji, emptyWords)
        assertEquals(2, calls)
        state.switchMode()
        assertEquals("", state.query)
        assertEquals(10, state.limit)
        assertFalse(state.kanjiPage.kanji.isEmpty())
        state.search(kanji, emptyWords)
        assertEquals(2, calls)
        state.switchMode()
        assertEquals("sun", state.query)
        state.search(kanji, emptyWords)
        assertEquals(2, calls)
    }

    @Test fun oldResultCannotOverwriteChangedQuery() = runBlocking {
        val state = SearchState(SavedStateHandle())
        state.changeQuery("old")
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val old = async {
            state.search({ _, _ ->
                entered.complete(Unit)
                finish.await()
                KanjiSearchPage(listOf(KanjiSummary("旧", "old")), false)
            }, emptyWords)
        }
        entered.await()
        state.changeQuery("new")
        state.search({ _, _ -> KanjiSearchPage(listOf(KanjiSummary("新", "new")), false) }, emptyWords)
        finish.complete(Unit)
        old.await()
        assertEquals("新", state.kanjiPage.kanji.single().character)
        assertFalse(state.loading)
    }

    @Test fun retryIsExplicitAndBlankInputDoesNotQuery() = runBlocking {
        val state = SearchState(SavedStateHandle())
        var calls = 0
        val search: suspend (String, Int) -> KanjiSearchPage = { _, _ ->
            calls++
            throw IllegalStateException("test error")
        }
        state.search(search, emptyWords)
        assertEquals(0, calls)
        state.changeQuery("day")
        state.search(search, emptyWords)
        state.search(search, emptyWords)
        assertTrue(state.failed)
        assertEquals(1, calls)
        state.retrySearch()
        state.search(search, emptyWords)
        assertEquals(2, calls)
        state.changeQuery("")
        state.search(search, emptyWords)
        assertFalse(state.failed)
        assertEquals(10, state.limit)
    }

    @Test fun processRestoreKeepsMetadataAndFetchesTheRequestedPage() = runBlocking {
        val restored = SearchState(SavedStateHandle(mapOf("query" to "day", "wordMode" to true, "limit" to 30)))
        var limitUsed = 0
        restored.search(emptyKanji, { query, limit ->
            assertEquals("day", query)
            limitUsed = limit
            WordSearchPage(emptyList(), false)
        })
        assertEquals(30, limitUsed)
        assertTrue(restored.wordMode)
    }

    @Test fun bothSessionsRetainDifferentQueriesPaginationScrollAndPagesWithoutSqlOnSwitch() = runBlocking {
        val state = SearchState(SavedStateHandle())
        var kanjiCalls = 0
        var wordCalls = 0
        val kanji: suspend (String, Int) -> KanjiSearchPage = { query, limit ->
            assertEquals("day", query)
            kanjiCalls++
            KanjiSearchPage((1..limit).map { KanjiSummary("kanji$it", "day") }, true)
        }
        val words: suspend (String, Int) -> WordSearchPage = { query, limit ->
            assertEquals("Sunday", query)
            wordCalls++
            WordSearchPage((1..limit).map {
                WordSearchResult(DictionaryWord(it.toLong(), "word$it", "reading", listOf("Sunday")), "Sunday", "Sunday", null)
            }, true)
        }
        state.switchMode()
        state.changeQuery("Sunday")
        state.search(kanji, words)
        state.showMore()
        state.search(kanji, words)
        state.listState.requestScrollToItem(8, 23)
        val wordScroll = state.listState
        val wordPage = state.wordPage

        state.switchMode()
        assertEquals("", state.query)
        assertEquals(10, state.limit)
        assertTrue(state.kanjiPage.kanji.isEmpty())
        state.search(kanji, words)
        assertEquals(0, kanjiCalls)
        state.changeQuery("day")
        state.search(kanji, words)
        state.showMore()
        state.showMore()
        state.search(kanji, words)
        state.listState.requestScrollToItem(12, 17)
        val kanjiScroll = state.listState
        val kanjiPage = state.kanjiPage

        repeat(2) {
            state.switchMode()
            state.search(kanji, words)
            assertEquals("Sunday", state.query)
            assertEquals(20, state.limit)
            assertSame(wordPage, state.wordPage)
            assertSame(wordScroll, state.listState)
            assertEquals(8, state.listState.firstVisibleItemIndex)
            assertEquals(23, state.listState.firstVisibleItemScrollOffset)
            // Returning from Details in this mode also calls search without new SQL.
            state.search(kanji, words)

            state.switchMode()
            state.search(kanji, words)
            assertEquals("day", state.query)
            assertEquals(30, state.limit)
            assertSame(kanjiPage, state.kanjiPage)
            assertSame(kanjiScroll, state.listState)
            assertEquals(12, state.listState.firstVisibleItemIndex)
            assertEquals(17, state.listState.firstVisibleItemScrollOffset)
            state.search(kanji, words)
        }
        assertEquals(2, kanjiCalls)
        assertEquals(2, wordCalls)
        state.changeQuery("")
        state.switchMode()
        assertEquals("Sunday", state.query)
        assertSame(wordPage, state.wordPage)
        assertSame(wordScroll, state.listState)
        assertEquals(20, state.limit)
    }

    @Test fun completionAfterModeSwitchUpdatesOnlyItsOriginalSession() = runBlocking {
        val state = SearchState(SavedStateHandle())
        state.changeQuery("old")
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val pending = async {
            state.search({ _, _ ->
                entered.complete(Unit)
                finish.await()
                KanjiSearchPage(listOf(KanjiSummary("old-kanji", "old")), true)
            }, emptyWords)
        }
        entered.await()
        state.switchMode()
        state.changeQuery("new word")
        state.search(emptyKanji, emptyWords)
        val wordPage = state.wordPage
        finish.complete(Unit)
        pending.await()
        assertEquals("new word", state.query)
        assertSame(wordPage, state.wordPage)
        assertFalse(state.loading)
        state.switchMode()
        assertEquals("old", state.query)
        assertEquals("old-kanji", state.kanjiPage.kanji.single().character)
        state.search({ _, _ -> fail("Completed Kanji search must stay cached"); emptyKanji("", 10) }, emptyWords)
    }

    @Test fun bothModeMetadataRestoreIndependentlyAndLegacyMetadataDoesNotLeak() {
        val saved = SavedStateHandle(mapOf("query" to "Sunday", "wordMode" to true, "limit" to 20))
        val first = SearchState(saved)
        first.switchMode()
        assertEquals("", first.query)
        val legacyRestored = SearchState(saved)
        assertEquals("", legacyRestored.query)
        legacyRestored.switchMode()
        assertEquals("Sunday", legacyRestored.query)
        assertEquals(20, legacyRestored.limit)
        legacyRestored.switchMode()
        legacyRestored.changeQuery("day")
        legacyRestored.showMore()
        legacyRestored.showMore()
        val restored = SearchState(saved)
        assertEquals("day", restored.query)
        assertEquals(30, restored.limit)
        restored.switchMode()
        assertEquals("Sunday", restored.query)
        assertEquals(20, restored.limit)
    }
}
