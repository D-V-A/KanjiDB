package com.example.kanjidb.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryWord
import com.example.kanjidb.data.dictionary.KanjiSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-only discovery state. Navigation and Activity recreation do not reload it. */
internal object ExploreState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var kanji by mutableStateOf<List<KanjiSummary>>(emptyList())
        private set
    var words by mutableStateOf<List<DictionaryWord>>(emptyList())
        private set
    var kanjiLoading by mutableStateOf(false)
        private set
    var wordsLoading by mutableStateOf(false)
        private set
    var kanjiFailed by mutableStateOf(false)
        private set
    var wordsFailed by mutableStateOf(false)
        private set
    private var initialized = false

    fun initialize(dictionary: DictionaryDatabase) {
        if (initialized) return
        initialized = true
        newKanji(dictionary)
        newWords(dictionary)
    }

    fun newKanji(dictionary: DictionaryDatabase) {
        if (kanjiLoading) return
        kanjiLoading = true
        kanjiFailed = false
        scope.launch {
            try { kanji = dictionary.getExploreKanji() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.e("Explore", "Cannot load kanji", error)
                kanjiFailed = true
            } finally { kanjiLoading = false }
        }
    }

    fun newWords(dictionary: DictionaryDatabase) {
        if (wordsLoading) return
        wordsLoading = true
        wordsFailed = false
        scope.launch {
            try { words = dictionary.getExploreWords() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.e("Explore", "Cannot load words", error)
                wordsFailed = true
            } finally { wordsLoading = false }
        }
    }
}
