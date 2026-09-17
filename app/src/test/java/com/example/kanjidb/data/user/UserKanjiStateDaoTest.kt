package com.example.kanjidb.data.user

import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercises production DAO operations; SQL/transactions/persistence have separate device tests. */
class UserKanjiStateDaoTest {
    @Test fun toggleReadsLatestStateAndNeverKeepsBothStates() = runBlocking {
        val dao = MemoryDao()
        for (target in listOf(LearningState.LEARNING, LearningState.KNOWN)) {
            dao.toggle("character", target)
            assertEquals(target, dao.getState("character"))
            assertEquals(1, dao.observeAll().first().size)
            dao.toggle("character", target)
            assertNull(dao.observeState("character").first())
        }
        dao.toggle("character", LearningState.LEARNING)
        dao.toggle("character", LearningState.KNOWN)
        assertEquals(LearningState.KNOWN, dao.getState("character"))
        assertTrue(dao.observeCharacters(LearningState.LEARNING).first().isEmpty())
        dao.toggle("character", LearningState.LEARNING)
        assertEquals(LearningState.LEARNING, dao.getState("character"))
        assertTrue(dao.observeCharacters(LearningState.KNOWN).first().isEmpty())
    }

    @Test fun bulkMovesReplaceRatherThanToggleAndRemoveMeansNone() = runBlocking {
        val dao = MemoryDao()
        val selected = listOf("a", "b", "a")
        dao.setState(selected, LearningState.LEARNING)
        dao.setState(listOf("untouched"), LearningState.KNOWN)
        dao.setState(selected, LearningState.KNOWN)
        dao.setState(selected, LearningState.KNOWN)
        assertEquals(setOf("a", "b", "untouched"), dao.observeCharacters(LearningState.KNOWN).first().toSet())
        assertTrue(dao.observeCharacters(LearningState.LEARNING).first().isEmpty())
        dao.setState(selected, LearningState.LEARNING)
        assertEquals(setOf("a", "b"), dao.observeCharacters(LearningState.LEARNING).first().toSet())
        dao.remove(selected)
        assertNull(dao.getState("a"))
        assertNull(dao.getState("b"))
        assertEquals(LearningState.KNOWN, dao.getState("untouched"))
    }

    @Test fun largeOperationsStayBelowBindLimitAndEmptyOperationsAreSafe() = runBlocking {
        val dao = MemoryDao()
        val characters = (1..2000).map { "character-$it" }
        dao.setState(characters, LearningState.LEARNING)
        assertEquals(2000, dao.observeAll().first().size)
        dao.remove(characters)
        dao.setState(emptyList(), LearningState.KNOWN)
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test fun bulkOverwriteIgnoresEveryPreviousStateAndIsIdempotent() = runBlocking {
        for (target in listOf(LearningState.LEARNING, LearningState.KNOWN)) {
            val dao = MemoryDao()
            dao.setState(listOf("learning"), LearningState.LEARNING)
            dao.setState(listOf("known", "untouched"), LearningState.KNOWN)
            val selected = listOf("none", "learning", "known")
            repeat(2) { dao.setState(selected, target) }
            selected.forEach { assertEquals(target, dao.getState(it)) }
            assertEquals(LearningState.KNOWN, dao.getState("untouched"))
            assertEquals(4, dao.observeAll().first().size)
        }
    }

    @Test fun reorderPersistsPositionsAndMovingAppendsWithoutDisturbingOtherState() = runBlocking {
        val dao = MemoryDao()
        dao.setState(listOf("a", "b", "c"), LearningState.LEARNING)
        dao.setState(listOf("x", "y"), LearningState.KNOWN)
        assertTrue(dao.reorder(LearningState.LEARNING, listOf("a", "b", "c"), listOf("c", "a", "b")))
        assertEquals(listOf("c", "a", "b"), dao.observeCharacters(LearningState.LEARNING).first())
        assertEquals(listOf(0L, 1L, 2L), manualOrder(dao.getAll(), LearningState.LEARNING).map { it.manualPosition })
        dao.setState(listOf("c", "a", "b"), LearningState.LEARNING)
        assertEquals(listOf("c", "a", "b"), dao.observeCharacters(LearningState.LEARNING).first())
        dao.setState(listOf("a", "x", "b"), LearningState.KNOWN)
        assertEquals(listOf("x", "y", "a", "b"), dao.observeCharacters(LearningState.KNOWN).first())
        assertEquals(listOf("c"), dao.observeCharacters(LearningState.LEARNING).first())
        dao.setState(listOf("a"), LearningState.LEARNING)
        assertEquals(listOf("c", "a"), dao.observeCharacters(LearningState.LEARNING).first())
        assertEquals(listOf("x", "y", "b"), dao.observeCharacters(LearningState.KNOWN).first())
    }

    @Test fun staleIncompleteDuplicateAndCrossStateReordersWriteNothing() = runBlocking {
        val dao = MemoryDao()
        dao.setState(listOf("a", "b", "c"), LearningState.LEARNING)
        dao.setState(listOf("x"), LearningState.KNOWN)
        val snapshot = dao.getAll()
        val before = listOf("a", "b", "c")
        for (after in listOf(listOf("a", "b"), listOf("a", "a", "c"), listOf("a", "b", "x"))) {
            assertFalse(dao.reorder(LearningState.LEARNING, before, after))
            assertEquals(snapshot, dao.getAll())
        }
        assertFalse(dao.reorder(LearningState.LEARNING, listOf("b", "a", "c"), before))
        assertFalse(dao.reorder(LearningState.NONE, before, before))
        dao.remove(listOf("b"))
        assertFalse(dao.reorder(LearningState.LEARNING, before, before.reversed()))
        assertEquals(listOf("a", "c"), dao.observeCharacters(LearningState.LEARNING).first())
        assertEquals(listOf("x"), dao.observeCharacters(LearningState.KNOWN).first())
    }

    private class MemoryDao : UserKanjiStateDao() {
        private val rows = MutableStateFlow<Map<String, UserKanjiStateEntity>>(emptyMap())
        override fun observeState(character: String) = rows.map { it[character] }
        override fun observeAll() = rows.map { it.values.toList() }
        override fun observeCharacters(state: LearningState) = rows.map { data ->
            manualOrder(data.values.toList(), state).map { it.character }
        }
        override suspend fun getAll() = rows.value.values.toList()
        override suspend fun getState(character: String) = rows.value[character]?.state
        override suspend fun upsert(rows: List<UserKanjiStateEntity>) {
            this.rows.value = this.rows.value + rows.associateBy { it.character }
        }
        override suspend fun deleteCharacters(characters: List<String>) {
            check(characters.size <= 900)
            rows.value = rows.value - characters.toSet()
        }
    }
}
