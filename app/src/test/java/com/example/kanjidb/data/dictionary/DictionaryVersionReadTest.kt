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

    @Test fun newSchemaValidatesButLegacyMetadataDoesNotRequireNewWordTables() {
        val file=folder.newFile("v2.db")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            val fixture=WordDatabaseFixture(db)
            fixture.form(1,1,"日","ひ")
            fixture.meaning(1,0,"day")
            fixture.kanji("日","日",0)
            db.execSQL("CREATE TABLE kanji_reading(id INTEGER)")
            db.execSQL("CREATE TABLE jlpt_kanji(kanji_id INTEGER,level INTEGER)")
            db.version=2
        }
        assertEquals(2,DictionaryInstallation.readVersion(file,validateWords=true))
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            db.execSQL("DROP TABLE word_sense_form")
            db.version=1
        }
        assertEquals(1,DictionaryInstallation.readVersion(file))
        assertTrue(shouldInstallDictionary(DictionaryInstallation.readVersion(file),2))
        assertTrue(runCatching { DictionaryInstallation.readVersion(file,validateWords=true) }.isFailure)
        assertTrue(file.exists())
    }
}
