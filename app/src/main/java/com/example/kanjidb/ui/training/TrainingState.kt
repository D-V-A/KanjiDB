package com.example.kanjidb.ui.training

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kanjidb.data.dictionary.DictionaryKanji
import com.example.kanjidb.data.user.UserKanjiStateDao
import kotlinx.coroutines.*

/** Process lifetime only, like Explore/Recommended. Holds no Activity, navigation or UI callbacks. */
internal object TrainingState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var session by mutableStateOf<TrainingSession?>(null)
        private set
    var cards: Map<String, DictionaryKanji> = emptyMap()
        private set
    var saving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set

    fun start(mode: TrainingMode, kanji: List<DictionaryKanji>) {
        check(session == null && !saving)
        cards = kanji.associateBy { it.character }
        session = TrainingSession.start(mode, kanji.map { it.character })
        saveFailed = false
    }

    fun update(transform: (TrainingSession) -> TrainingSession) {
        if (!saving) session = session?.let(transform)
    }

    /** Cancel has no DAO dependency and cannot apply actions. */
    fun cancel() {
        if (saving) return
        session = null
        cards = emptyMap()
        saveFailed = false
    }

    fun finish(dao: UserKanjiStateDao) {
        val current = session ?: return
        if (saving || !current.complete) return
        saving = true
        saveFailed = false
        // Finishing survives Activity recreation; navigation cannot discard a committing session.
        scope.launch {
            try {
                dao.applyStates(current.finishAssignments())
                session = null
                cards = emptyMap()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.e("Training", "Cannot finish training", error)
                saveFailed = true
            } finally { saving = false }
        }
    }
}