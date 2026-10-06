package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WordReadingOrderTest {
    @Test
    fun allWordQueriesUseEarliestValidReadingEvenWhenAnotherReadingIsCommon() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE word_form (id INTEGER PRIMARY KEY, entry_id INTEGER, written TEXT, reading TEXT, common INTEGER, reading_priority INTEGER, reading_order INTEGER)")
            db.execSQL("CREATE TABLE word_meaning (id INTEGER PRIMARY KEY, word_form_id INTEGER, language TEXT, meaning TEXT, sense_index INTEGER)")
            db.execSQL("CREATE TABLE word_kanji (word_form_id INTEGER, kanji_id INTEGER, position INTEGER)")
            db.execSQL("CREATE TABLE kanji (id INTEGER PRIMARY KEY, character TEXT)")
            db.execSQL("CREATE TABLE kanji_meaning (id INTEGER PRIMARY KEY, kanji_id INTEGER, language TEXT, meaning TEXT)")
            listOf("\u304d\u3087\u3046", "\u3053\u3093\u306b\u3061", "\u3053\u3093\u3061", "\u3053\u3093\u3058\u3064").forEachIndexed { index, reading ->
                db.execSQL("INSERT INTO word_form VALUES (?, 1, '\u4eca\u65e5', ?, ?, ?, ?)",
                    arrayOf<Any>(index + 1, reading, if (index == 1) 1 else 0,
                        listOf(1000, 2380, 0, 0)[index], index))
                db.execSQL("INSERT INTO word_kanji VALUES (?, 1, 0)", arrayOf(index + 1))
            }
            // An alternative spelling's earlier reading must not enter today's readings.
            db.execSQL("INSERT INTO word_form VALUES (5, 1, '\u5225', '\u4eca\u65e5', 0, 9999, -1)")
            db.rawQuery(DictionaryDatabase.WORD_READINGS_SQL, arrayOf("1", "\u4eca\u65e5")).use { cursor ->
                val readings = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertEquals(listOf("\u304d\u3087\u3046", "\u3053\u3093\u306b\u3061", "\u3053\u3093\u3061", "\u3053\u3093\u3058\u3064"), readings)
            }
            listOf(
                SEARCH_WORDS_SQL to arrayOf("\u4eca\u65e5", "", "", "10"),
                EXPLORE_WORDS_SQL to emptyArray(),
                DictionaryDatabase.WORDS_SQL to arrayOf("1", "1"),
                DictionaryDatabase.WORDS_SQL to arrayOf("1", "0")
            ).forEach { (sql, args) ->
                db.rawQuery(sql, args).use { cursor ->
                    cursor.moveToFirst()
                    val column = if (sql == DictionaryDatabase.WORDS_SQL) 3 else 2
                    assertEquals("\u304d\u3087\u3046", cursor.getString(column))
                }
            }
            db.execSQL("INSERT INTO kanji VALUES (1, '\u4eca'), (2, '\u65e5')")
            db.execSQL("INSERT INTO kanji_meaning VALUES (1, 1, 'en', 'now'), (2, 2, 'en', 'day')")
            db.execSQL("INSERT INTO word_kanji VALUES (1, 2, 1), (1, 1, 2)")
            db.rawQuery(DictionaryDatabase.WORD_KANJI_SQL, arrayOf("1", "\u4eca\u65e5")).use { cursor ->
                val cards = buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
                assertEquals(listOf("\u4eca" to "now", "\u65e5" to "day"), cards)
            }
            // Meanings remain scoped to the displayed spelling, including reading-specific glosses.
            db.execSQL("INSERT INTO word_meaning VALUES (1, 1, 'en', 'today', 1), (2, 2, 'en', 'today', 1), (3, 2, 'en', 'these days', 2), (4, 5, 'en', 'other', 1)")
            db.rawQuery(DictionaryDatabase.WORD_MEANINGS_SQL, arrayOf("1", "\u4eca\u65e5")).use { cursor ->
                val meanings = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(listOf("today", "these days"), meanings)
            }
        }
    }
}
