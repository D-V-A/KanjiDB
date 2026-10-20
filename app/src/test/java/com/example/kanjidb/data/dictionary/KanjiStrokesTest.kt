package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],manifest=Config.NONE)
class KanjiStrokesTest {
    @Test fun strokesAreOrderedAndScopedToExistingKanji() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE kanji_stroke(kanji_id INTEGER,stroke_index INTEGER,path_data TEXT)")
            db.execSQL("INSERT INTO kanji_stroke VALUES (1,2,'M10 0L10 10'),(2,1,'M0 0L1 1'),(1,1,'M0 0L10 0')")
            assertEquals(listOf(KanjiStroke(1,"M0 0L10 0"),KanjiStroke(2,"M10 0L10 10")),loadKanjiStrokes(db,"1"))
            assertTrue(loadKanjiStrokes(db,"3").isEmpty())
        }
    }
    @Test fun missingOptionalTableReturnsNoStrokes() {
        SQLiteDatabase.create(null).use { db -> assertTrue(loadKanjiStrokes(db,"1").isEmpty()) }
    }
}
