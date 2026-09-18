package com.example.kanjidb.data.user

import androidx.room.*
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UserKanjiStateDao {
    @Query("SELECT * FROM kanji_state WHERE character = :character")
    abstract fun observeState(character: String): Flow<UserKanjiStateEntity?>

    @Query("SELECT * FROM kanji_state ORDER BY manualPosition, character")
    abstract fun observeAll(): Flow<List<UserKanjiStateEntity>>

    @Query("SELECT character FROM kanji_state WHERE state = :state ORDER BY manualPosition, character")
    abstract fun observeCharacters(state: LearningState): Flow<List<String>>

    @Query("SELECT state FROM kanji_state WHERE character = :character")
    abstract suspend fun getState(character: String): LearningState?

    @Query("SELECT * FROM kanji_state")
    abstract suspend fun getAll(): List<UserKanjiStateEntity>

    @Upsert
    protected abstract suspend fun upsert(rows: List<UserKanjiStateEntity>)

    @Query("DELETE FROM kanji_state WHERE character IN (:characters)")
    protected abstract suspend fun deleteCharacters(characters: List<String>)

    // Read and write together so rapid taps always toggle the latest committed state.
    @Transaction
    open suspend fun toggle(character: String, target: LearningState) {
        require(target != LearningState.NONE)
        setState(listOf(character), (getState(character) ?: LearningState.NONE).toggle(target))
    }

    @Transaction
    open suspend fun setState(characters: List<String>, state: LearningState) {
        if (state == LearningState.NONE) {
            // Stay below SQLite's bind limit on API 26, within one transaction.
            characters.distinct().chunked(900).forEach { deleteCharacters(it) }
        } else {
            assignedKanjiRows(getAll(), characters, state).chunked(900).forEach { upsert(it) }
        }
    }

    @Transaction
    open suspend fun reorder(state: LearningState, before: List<String>, after: List<String>): Boolean {
        val existing = getAll()
        val reordered = reorderedKanjiRows(existing, state, before, after) ?: return false
        val byCharacter = existing.associateBy { it.character }
        reordered.filter { it != byCharacter[it.character] }.chunked(900).forEach { upsert(it) }
        return true
    }

    /** Mixed assignments commit together, preserving existing append/idempotency semantics. */
    @Transaction
    open suspend fun applyStates(assignments: Map<String, LearningState>) {
        assignments.entries.groupBy({ it.value }, { it.key }).forEach { (state, characters) ->
            setState(characters, state)
        }
    }

    suspend fun remove(characters: List<String>) = setState(characters, LearningState.NONE)
}
