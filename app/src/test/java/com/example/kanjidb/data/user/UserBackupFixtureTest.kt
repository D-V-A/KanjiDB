package com.example.kanjidb.data.user

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.kanjidb.ui.training.availableTrainingPool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UserBackupFixtureTest {
    private fun projectFile(path: String) = listOf(File(path), File("../$path")).first { it.isFile }

    @Test fun actualFixturePassesProductionParserDictionaryClassificationAndRoomRestore() = runBlocking {
        val backup = UserBackupFormat.decode(projectFile("docs/test-data/KanjiDB_Test_Import_v1.kdb").readText(Charsets.UTF_8))
        val expectedMissing = (0x20000..0x20002).map { String(Character.toChars(it)) }
        val available = hashSetOf<String>()
        SQLiteDatabase.openDatabase(projectFile("app/src/main/assets/dictionary.db").absolutePath,
            null, SQLiteDatabase.OPEN_READONLY).use { dictionary ->
            backup.data.characters.forEach { character ->
                dictionary.rawQuery("SELECT COUNT(*) FROM kanji WHERE character = ?", arrayOf(character)).use {
                    assertTrue(it.moveToFirst())
                    val count = it.getInt(0)
                    assertEquals("Dictionary availability for U+${character.codePointAt(0).toString(16)}",
                        if (character in expectedMissing) 0 else 1, count)
                    if (count > 0) available += character
                }
            }
        }
        assertEquals(20, available.size)
        assertEquals(expectedMissing.toSet(), backup.data.missingCharacters(available).toSet())
        assertEquals(23, backup.data.characters.size)
        assertEquals(4, backup.data.lists.size)
        assertEquals(7, backup.data.states.count { it.state == "LEARNING" })
        assertEquals(7, backup.data.states.count { it.state == "KNOWN" })
        assertEquals(20, availableTrainingPool(backup.data.characters, available).size)
        assertTrue(availableTrainingPool(expectedMissing, available).isEmpty())
        assertTrue(expectedMissing.all { missing -> backup.data.lists.any { list -> list.members.any { it.character == missing } } })
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UserDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            db.backup().replace(backup.data)
            val restored = db.backup().snapshot()
            assertEquals(backup.data.states.toSet(), restored.states.toSet())
            assertEquals(backup.data.lists, restored.lists)
            assertEquals(backup.data.characters.toSet(), restored.characters.toSet())
            assertEquals(restored, UserBackupFormat.decode(UserBackupFormat.encode(backup.copy(data = restored))).data)
        } finally { db.close() }
    }
}
