package com.example.kanjidb.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.KanjiSummary
import com.example.kanjidb.data.dictionary.RecommendedKanjiSession
import com.example.kanjidb.data.user.UserKanjiStateDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Like ExploreState, owned by the process, with one Room subscription across all screens. */
internal object RecommendedState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var observer: Job? = null
    private var session: RecommendedKanjiSession? = null
    var kanji by mutableStateOf<List<KanjiSummary>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    fun initialize(dictionary: DictionaryDatabase, dao: UserKanjiStateDao) {
        if (observer?.isActive == true) return
        loading = true
        failed = false
        observer = scope.launch {
            try {
                val rows = if (session == null) dictionary.getRecommendationKanji() else null
                dao.observeAll().map { records -> records.map { it.character }.toSet() }
                    .distinctUntilChanged().collect { learned ->
                        mutex.withLock {
                            withContext(Dispatchers.Default) {
                                val current = session ?: RecommendedKanjiSession(requireNotNull(rows)).also { session = it }
                                current.update(learned)
                            }
                            publish()
                        }
                        loading = false
                    }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.e("Recommended", "Cannot load recommendations", error)
                failed = true
            } finally { loading = false }
        }
    }

    private fun publish() { kanji = session?.visible?.map { it.summary }.orEmpty() }
    fun refresh() = change { refresh() }
    fun returnedFromDetails(character: String) = change { returnedFromDetails(character) }
    private fun change(action: RecommendedKanjiSession.() -> Unit) {
        scope.launch {
            mutex.withLock {
                withContext(Dispatchers.Default) { session?.action() }
                publish()
            }
        }
    }
}
