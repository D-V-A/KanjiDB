package com.example.kanjidb.data.user

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CustomListDao {
    @Transaction
    @Query("SELECT * FROM custom_list ORDER BY manualIndex DESC, id")
    abstract fun observeLists(): Flow<List<CustomListWithKanji>>

    @Transaction
    @Query("SELECT * FROM custom_list ORDER BY manualIndex DESC, id")
    abstract suspend fun getLists(): List<CustomListWithKanji>

    @Query("SELECT * FROM custom_list ORDER BY manualIndex DESC, id")
    protected abstract suspend fun getHeaders(): List<CustomListEntity>

    @Insert
    protected abstract suspend fun insertList(row: CustomListEntity): Long

    @Query("UPDATE custom_list SET name = :name WHERE id = :id")
    protected abstract suspend fun updateName(id: Long, name: String)

    @Query("DELETE FROM custom_list WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE custom_list SET manualIndex = :position WHERE id = :id")
    protected abstract suspend fun updateListPosition(id: Long, position: Long)

    @Query("SELECT * FROM custom_list_kanji WHERE listId = :id ORDER BY manualIndex, character")
    abstract suspend fun getMemberships(id: Long): List<CustomListKanjiEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMemberships(rows: List<CustomListKanjiEntity>)

    @Update
    protected abstract suspend fun updateMemberships(rows: List<CustomListKanjiEntity>)

    @Query("DELETE FROM custom_list_kanji WHERE listId = :id AND character IN (:characters)")
    protected abstract suspend fun removeMemberships(id: Long, characters: List<String>)

    @Transaction
    open suspend fun create(name: String): Long {
        val headers = getHeaders()
        require(customListNameError(name, headers) == null) { customListNameError(name, headers).orEmpty() }
        return insertList(CustomListEntity(name = name, manualIndex = (headers.maxOfOrNull { it.manualIndex } ?: -1) + 1))
    }

    @Transaction
    open suspend fun rename(id: Long, name: String) {
        val headers = getHeaders()
        require(headers.any { it.id == id }) { "This list no longer exists." }
        require(customListNameError(name, headers, id) == null) { customListNameError(name, headers, id).orEmpty() }
        updateName(id, name)
    }

    @Transaction
    open suspend fun setMembership(id: Long, characters: List<String>, included: Boolean) {
        require(getHeaders().any { it.id == id }) { "This list no longer exists." }
        if (!included) characters.distinct().chunked(900).forEach { removeMemberships(id, it) }
        else {
            val existing = getMemberships(id)
            val keys = existing.map { it.character }.toSet()
            val start = (existing.maxOfOrNull { it.manualIndex } ?: -1) + 1
            insertMemberships(characters.distinct().filter { it !in keys }.mapIndexed { index, character ->
                require(character.isNotEmpty())
                CustomListKanjiEntity(id, character, start + index)
            })
        }
    }

    @Transaction
    open suspend fun applyChanges(characters: List<String>, targets: List<CustomListTarget>) {
        // New targets are displayed newest first. Create oldest first so max+1 preserves that order.
        targets.filter { it.id == null }.asReversed().forEach {
            val id = create(it.name)
            if (it.target == true) setMembership(id, characters, true)
        }
        targets.filter { it.id != null && it.target != null }.forEach {
            setMembership(requireNotNull(it.id), characters, requireNotNull(it.target))
        }
    }

    @Transaction
    open suspend fun reorderLists(before: List<Long>, after: List<Long>): Boolean {
        val current = getHeaders().map { it.id }
        if (current != before || after.distinct().size != after.size || after.toSet() != current.toSet()) return false
        after.forEachIndexed { index, id -> updateListPosition(id, (after.size - 1 - index).toLong()) }
        return true
    }

    @Transaction
    open suspend fun reorderKanji(id: Long, before: List<String>, after: List<String>): Boolean {
        val current = getMemberships(id)
        if (current.map { it.character } != before || after.distinct().size != after.size ||
            after.toSet() != before.toSet()) return false
        val byCharacter = current.associateBy { it.character }
        updateMemberships(after.mapIndexed { index, character ->
            byCharacter.getValue(character).copy(manualIndex = index.toLong())
        })
        return true
    }
}
