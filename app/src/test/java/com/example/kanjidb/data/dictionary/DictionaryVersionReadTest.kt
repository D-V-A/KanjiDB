package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DictionaryVersionReadTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun readsActualLocalVersionIncludingLegacyAndNewer() {
        val file = folder.newFile("local.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE kanji (id INTEGER, character TEXT)")
            db.execSQL("INSERT INTO kanji VALUES (1, 'a')")
            for (table in listOf("kanji_meaning", "kanji_reading", "word_form", "word_meaning", "word_kanji", "jlpt_kanji")) {
                db.execSQL("CREATE TABLE $table (id INTEGER)")
            }
        }
        for (version in listOf(0, 1, 2)) {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = version }
            assertEquals(version, DictionaryInstallation.readVersion(file))
        }
    }

    @Test fun unreadableFileFailsWithoutDeletingOriginal() {
        val file = folder.newFile("invalid.db").apply { writeText("invalid SQLite") }
        assertTrue(runCatching { DictionaryInstallation.readVersion(file) }.isFailure)
        assertEquals("invalid SQLite", file.readText())
    }
}
