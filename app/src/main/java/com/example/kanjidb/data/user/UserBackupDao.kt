package com.example.kanjidb.data.user

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Consistent export snapshot and atomic replacement of all user-owned tables. */
@Dao
abstract class UserBackupDao {
    @Query("SELECT EXISTS(SELECT 1 FROM kanji_state) OR EXISTS(SELECT 1 FROM custom_list) OR EXISTS(SELECT 1 FROM custom_list_kanji)")
    abstract fun observeHasData(): Flow<Boolean>

    @Query("SELECT * FROM kanji_state ORDER BY manualPosition, character")
    protected abstract suspend fun states(): List<UserKanjiStateEntity>
    @Query("SELECT * FROM custom_list ORDER BY manualIndex DESC, id")
    protected abstract suspend fun lists(): List<CustomListEntity>
    @Query("SELECT * FROM custom_list_kanji ORDER BY listId, manualIndex, character")
    protected abstract suspend fun members(): List<CustomListKanjiEntity>
    @Query("DELETE FROM custom_list_kanji")
    protected abstract suspend fun clearMembers()
    @Query("DELETE FROM custom_list")
    protected abstract suspend fun clearLists()
    @Query("DELETE FROM kanji_state")
    protected abstract suspend fun clearStates()
    @Insert protected abstract suspend fun insertStates(rows: List<UserKanjiStateEntity>)
    @Insert protected abstract suspend fun insertLists(rows: List<CustomListEntity>)
    @Insert protected abstract suspend fun insertMembers(rows: List<CustomListKanjiEntity>)

    @Transaction
    open suspend fun snapshot(): FullBackupData {
        val states = states()
        val lists = lists()
        val members = members().groupBy { it.listId }
        return FullBackupData(states.map { BackupState(it.character, it.state.name, it.manualPosition) },
            lists.map { list -> BackupList(list.id, list.name, list.manualIndex,
                members[list.id].orEmpty().map { BackupMember(it.character, it.manualIndex) }) })
    }

    @Transaction
    open suspend fun replace(data: FullBackupData) {
        clearMembers()
        clearLists()
        clearStates()
        insertStates(data.stateEntities())
        insertLists(data.lists.map { CustomListEntity(it.id, it.name, it.manualIndex) })
        insertMembers(data.lists.flatMap { list -> list.members.map {
            CustomListKanjiEntity(list.id, it.character, it.manualIndex)
        } })
    }
}
