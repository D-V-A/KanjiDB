package com.example.kanjidb.data.user

import androidx.room.*
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UserKanjiStateDao {
    @Query("SELECT * FROM kanji_state WHERE character = :character")
    abstract fun observeState(character: String): Flow<UserKanjiStateEntity?>

    @Query("SELECT * FROM kanji_state ORDER BY rowid")
    abstract fun observeAll(): Flow<List<UserKanjiStateEntity>>

    @Query("SELECT character FROM kanji_state WHERE state = :state ORDER BY rowid")
    abstract fun observeCharacters(state: LearningState): Flow<List<String>>

    @Query("SELECT state FROM kanji_state WHERE character = :character")
    abstract suspend fun getState(character: String): LearningState?

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
        // Stay below SQLite's bind parameter limit on API 26, within one transaction.
        characters.distinct().chunked(900).forEach { chunk ->
            if (state == LearningState.NONE) deleteCharacters(chunk)
            else upsert(chunk.map { UserKanjiStateEntity(it, state) })
        }
    }

    suspend fun remove(characters: List<String>) = setState(characters, LearningState.NONE)
}
