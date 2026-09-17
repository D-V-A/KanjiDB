package com.example.kanjidb.ui.lists

import androidx.compose.runtime.*
import com.example.kanjidb.data.user.UserKanjiStateDao
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shared async write/error handling. Assignment always uses the existing transactional bulk API. */
internal class KanjiStateWriter(
    private val dao: UserKanjiStateDao,
    private val selection: KanjiCollectionState,
    private val scope: CoroutineScope
) {
    var saving by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    suspend fun reorder(state: LearningState, before: List<String>, after: List<String>, character: String): Boolean {
        if (saving) return false
        saving = true
        failed = false
        return try {
            val saved = dao.reorder(state, before, after)
            if (saved) selection.finishReorder(character) else failed = true
            saved
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("KanjiState", "Cannot save manual order", error)
            failed = true
            false
        } finally {
            saving = false
        }
    }

    fun assign(characters: List<String>, target: LearningState) {
        if (saving || characters.isEmpty()) return
        saving = true
        failed = false
        scope.launch {
            try {
                dao.setState(characters, target)
                selection.cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("KanjiState", "Cannot save kanji states", error)
                failed = true
            } finally {
                saving = false
            }
        }
    }
}

@Composable
internal fun rememberKanjiStateWriter(dao: UserKanjiStateDao, state: KanjiCollectionState): KanjiStateWriter {
    val scope = rememberCoroutineScope()
    return remember(dao, state, scope) { KanjiStateWriter(dao, state, scope) }
}
