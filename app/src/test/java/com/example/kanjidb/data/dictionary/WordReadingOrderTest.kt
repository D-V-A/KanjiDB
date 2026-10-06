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
            val relatedArgs = arrayOf("1", "0")
            db.rawQuery(DictionaryDatabase.RELATED_WORD_FEATURES_SQL, relatedArgs).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("\u304d\u3087\u3046", cursor.getString(3))
                assertEquals(1, cursor.getInt(4)) // A later valid reading is common.
                assertEquals(2380, cursor.getInt(5)) // Its priority informs ranking, not reading selection.
                assertEquals(0, cursor.getInt(6))
                assertEquals(0, cursor.getInt(7))
            }
            db.rawQuery(DictionaryDatabase.RELATED_WORD_MEANINGS_SQL, relatedArgs).use { cursor ->
                val meanings = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(listOf("today"), meanings) // Later-reading and other-spelling glosses stay excluded.
            }
            db.execSQL("ALTER TABLE kanji ADD COLUMN frequency INTEGER")
            db.execSQL("UPDATE kanji SET frequency = 1 WHERE id = 1")
            db.rawQuery(DictionaryDatabase.relatedWordKanjiSql(false), relatedArgs).use { cursor ->
                assertEquals(2, cursor.count) // Repeated occurrences become one feature per kanji.
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(2))
                assertEquals(true, cursor.isNull(3)) // Older dictionaries without JLPT still work.
            }
            db.execSQL("CREATE TABLE jlpt_kanji (kanji_id INTEGER PRIMARY KEY, level INTEGER)")
            db.execSQL("INSERT INTO jlpt_kanji VALUES (1, 5)")
            db.rawQuery(DictionaryDatabase.relatedWordKanjiSql(true), relatedArgs).use { cursor ->
                cursor.moveToFirst()
                assertEquals(5, cursor.getInt(3))
            }
            db.execSQL("INSERT INTO word_form VALUES (6, 2, '日語', 'にちご', 0, 0, 0)")
            db.execSQL("INSERT INTO kanji VALUES (3, '語', 5)")
            db.execSQL("INSERT INTO word_kanji VALUES (6, 2, 0), (6, 3, 1)")
            val onePoolSql = DictionaryDatabase.wordTrainingFormsSql(1)
            db.rawQuery(onePoolSql, arrayOf("今")).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("きょう", cursor.getString(3))
            }
            // A second pool target includes its word, but never duplicates a word covering both targets.
            db.rawQuery(DictionaryDatabase.wordTrainingFormsSql(2), arrayOf("今", "日")).use { cursor ->
                assertEquals(2, cursor.count)
            }
            // The primary translation is the same first applicable gloss as Word Details,
            // including senses restricted to a later valid reading; priority still cannot choose the reading.
            db.execSQL("INSERT INTO word_meaning VALUES (5, 2, 'en', 'present day', 0)")
            val detailsMeanings = db.rawQuery(DictionaryDatabase.WORD_MEANINGS_SQL, arrayOf("1", "今日")).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
            }
            db.rawQuery(DictionaryDatabase.wordTrainingMeaningsSql(onePoolSql), arrayOf("今")).use { cursor ->
                val trainingMeanings = buildList { while (cursor.moveToNext()) add(cursor.getString(2) to cursor.getString(3)) }
                assertEquals(detailsMeanings, trainingMeanings)
                assertEquals("present day", orderedWordMeanings(trainingMeanings.groupBy({ it.first }, { it.second })).first())
            }
        }
    }
}
