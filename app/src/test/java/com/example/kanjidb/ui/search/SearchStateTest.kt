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
import kotlinx.coroutines.CoroutineStart
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
        assertEquals("sunday", returned.query)
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

    @Test fun kanjiReturnIsCachedAndModeSwitchClearsItsSession() = runBlocking {
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
        assertEquals("sun", state.query)
        assertEquals(10, state.limit)
        assertTrue(state.kanjiPage.kanji.isEmpty())
        state.search(kanji, emptyWords)
        assertEquals(2, calls)
        state.switchMode()
        assertEquals("sun", state.query)
        state.search(kanji, emptyWords)
        assertEquals(3, calls)
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

    @Test fun modeSwitchPreservesSharedQueryAndSearchesEachNewMode() = runBlocking {
        val state = SearchState(SavedStateHandle())
        state.switchMode()
        state.changeQuery("Sunday")
        state.showMore()
        var wordCalls=0
        var kanjiCalls=0
        val words: suspend (String,Int)->WordSearchPage={ q,_ ->
            assertEquals("Sunday",q);wordCalls++
            WordSearchPage(listOf(WordSearchResult(DictionaryWord(1,"word","reading",listOf("Sunday")),"Sunday",null,null)),true)
        }
        val kanji: suspend (String,Int)->KanjiSearchPage={ q,_ ->
            assertEquals("Sunday",q);kanjiCalls++
            KanjiSearchPage(listOf(KanjiSummary("day","Sunday")),false)
        }
        state.search(kanji,words)
        state.listState.requestScrollToItem(0,23)
        repeat(2) {
            state.switchMode()
            assertEquals("Sunday",state.query)
            assertEquals(10,state.limit)
            assertTrue(state.kanjiPage.kanji.isEmpty())
            assertTrue(state.wordPage.words.isEmpty())
            assertTrue(state.loading)
            assertFalse(state.failed)
            assertEquals(0,state.listState.firstVisibleItemScrollOffset)
            state.search(kanji,words)
        }
        assertEquals(1,kanjiCalls)
        assertEquals(2,wordCalls)
    }

    @Test fun oldCompletionCannotRepopulateAfterModeRoundTripEvenWithSameQuery() = runBlocking {
        val state=SearchState(SavedStateHandle())
        state.changeQuery("day")
        val entered=CompletableDeferred<Unit>()
        val finish=CompletableDeferred<Unit>()
        val pending=async {
            state.search({ _, _ ->
                entered.complete(Unit);finish.await()
                KanjiSearchPage(listOf(KanjiSummary("old","old")),true)
            },emptyWords)
        }
        entered.await()
        state.switchMode()
        state.switchMode()
        state.changeQuery("day")
        state.search({ _, _ -> KanjiSearchPage(listOf(KanjiSummary("new","new")),false) },emptyWords)
        finish.complete(Unit);pending.await()
        assertEquals("new",state.kanjiPage.kanji.single().character)
        assertFalse(state.loading)
    }

    @Test fun modeSwitchStartsSearchWithoutTypingDebounce() = runBlocking {
        val state=SearchState(SavedStateHandle())
        state.changeQuery("day")
        state.switchMode()
        var called=false
        val job=async(start=CoroutineStart.UNDISPATCHED) {
            state.search(emptyKanji, { q,_ ->
                assertEquals("day",q)
                called=true
                WordSearchPage(emptyList(),false)
            })
        }
        assertTrue(called)
        assertTrue(job.isCompleted)
        job.await()
    }

    @Test fun restorationKeepsOneActiveQueryAndSwitchNeverResurrectsLegacyScopes() {
        val saved=SavedStateHandle(mapOf("wordMode" to true,"wordsQuery" to "Sunday",
            "wordsLimit" to 20,"kanjiQuery" to "day","kanjiLimit" to 30))
        val first=SearchState(saved)
        assertEquals("Sunday",first.query)
        assertEquals(20,first.limit)
        assertNull(saved.get<String>("wordsQuery"))
        assertNull(saved.get<String>("kanjiQuery"))
        val recreated=SearchState(saved)
        assertEquals("Sunday",recreated.query)
        assertTrue(recreated.wordMode)
        recreated.switchMode()
        val restored=SearchState(saved)
        assertEquals("Sunday",restored.query)
        assertFalse(restored.wordMode)
        restored.switchMode()
        assertEquals("Sunday",restored.query)
        restored.changeQuery("new")
        assertEquals("new",SearchState(saved).query)
    }
}
