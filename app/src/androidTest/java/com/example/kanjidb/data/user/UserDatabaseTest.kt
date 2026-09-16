package com.example.kanjidb.data.user

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UserDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun emptyToggleMoveRemoveAndBulkAreExclusive() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build()
        try {
            val dao = db.kanjiStates()
            assertTrue(dao.observeAll().first().isEmpty())
            for (target in listOf(LearningState.LEARNING, LearningState.KNOWN)) {
                dao.toggle("\u5c71", target)
                assertEquals(target, dao.observeState("\u5c71").first()?.state)
                dao.toggle("\u5c71", target)
                assertNull(dao.getState("\u5c71"))
            }
            val characters = listOf("\u5c71", "\u6c34", "\uD842\uDFB7", "missing-character")
            dao.setState(characters, LearningState.LEARNING)
            dao.setState(characters, LearningState.KNOWN)
            assertTrue(dao.observeCharacters(LearningState.LEARNING).first().isEmpty())
            assertEquals(characters.toSet(), dao.observeCharacters(LearningState.KNOWN).first().toSet())
            dao.setState(characters, LearningState.LEARNING)
            assertTrue(dao.observeCharacters(LearningState.KNOWN).first().isEmpty())
            assertEquals(characters.size, dao.observeAll().first().size)
            dao.remove(characters)
            assertTrue(dao.observeAll().first().isEmpty())
            val many = (1..1001).map { "character-$it" }
            dao.setState(many, LearningState.KNOWN)
            assertEquals(1001, dao.observeAll().first().size)
            dao.remove(many)
            assertTrue(dao.observeAll().first().isEmpty())
        } finally { db.close() }
    }

    @Test fun stateSurvivesClosingAndReopeningDatabase() = runBlocking {
        val name = "user-state-test.db"
        context.deleteDatabase(name)
        try {
            Room.databaseBuilder(context, UserDatabase::class.java, name).build().let { db ->
                try { db.kanjiStates().setState(listOf("\u5c71"), LearningState.KNOWN) }
                finally { db.close() }
            }
            Room.databaseBuilder(context, UserDatabase::class.java, name).build().let { db ->
                try { assertEquals(LearningState.KNOWN, db.kanjiStates().getState("\u5c71")) }
                finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
