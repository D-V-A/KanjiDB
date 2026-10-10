package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WordSearchRelevanceTest {
    @Test fun meaningQualityUsesBoundariesParenthesesAndEveryOccurrence() {
        assertEquals(0, englishMeaningMatch(" Sunday ", "SUNDAY", true)?.quality)
        assertEquals(1, englishMeaningMatch("one's Sunday best", "sunday", true)?.quality)
        assertEquals(2, englishMeaningMatch("day (previous year, Sunday, etc.)", "sunday", true)?.quality)
        assertEquals(2, englishMeaningMatch("day [Sunday]", "sunday", true)?.quality)
        assertEquals(3, englishMeaningMatch("Whitsunday", "sunday", true)?.quality)
        assertEquals(1, englishMeaningMatch("(Sunday) or Sunday clothes", "sunday", true)?.quality)
        assertEquals(3, englishMeaningMatch("Sundayish", "sunday", true)?.quality)
        assertEquals(1, englishMeaningMatch("fresh water (liquid)", "fresh water", true)?.quality)
        assertEquals(2, englishMeaningMatch("day (a (Sunday) example)", "sunday", true)?.quality)
        assertNull(englishMeaningMatch("day", "sunday", true))
        assertNull(englishMeaningMatch("day", "", true))
    }

    @Test fun sqlAndRankingKeepBestGlossReadingRestrictionsAndQualityBeforePriority() = runBlocking {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE word_form (id INTEGER PRIMARY KEY, entry_id INTEGER, written TEXT, reading TEXT, common INTEGER, reading_priority INTEGER, reading_order INTEGER)")
            db.execSQL("CREATE TABLE word_meaning (id INTEGER PRIMARY KEY, word_form_id INTEGER, language TEXT, meaning TEXT, sense_index INTEGER)")
            fun word(id: Int, written: String, meanings: List<String>, priority: Int = 0) {
                db.execSQL("INSERT INTO word_form VALUES (?, ?, ?, 'にちようび', ?, ?, 0)",
                    arrayOf<Any>(id, id, written, if (priority > 0) 1 else 0, priority))
                meanings.forEachIndexed { index, meaning ->
                    db.execSQL("INSERT INTO word_meaning VALUES (?, ?, 'en', ?, ?)",
                        arrayOf<Any>(id * 10 + index, id, meaning, index))
                }
            }
            word(1, "日曜日", listOf("Sunday"))
            word(2, "日曜", listOf("holiday", "Sunday"), 4000)
            word(3, "晴着", listOf("one's Sunday best"), 4000)
            word(4, "例", listOf("day (previous year, Sunday, etc.)"), 4000)
            word(5, "五旬節", listOf("Shavuot", "Whitsunday"), 4000)
            word(6, "別", listOf("Whitsunday", "one's Sunday best", "Sunday"))
            // A later reading has its own valid gloss; primary reading must remain unchanged.
            db.execSQL("INSERT INTO word_form VALUES (100, 7, '限定', 'はやい', 0, 0, 0), (101, 7, '限定', 'おそい', 1, 5000, 1)")
            db.execSQL("INSERT INTO word_meaning VALUES (1000, 100, 'en', 'restricted', 0), (1001, 101, 'en', 'Sunday', 1)")
            val results = searchWordResults(db, "sunday", "")
            assertEquals(listOf(1L, 7L, 2L, 6L, 3L, 4L, 5L), results.map { it.word.entryId })
            assertEquals("Sunday", results.first { it.word.entryId == 6L }.displayMeaning)
            val secondary = results.first { it.word.entryId == 5L }
            assertEquals("Whitsunday", secondary.displayMeaning)
            assertEquals("Shavuot", secondary.primaryMeaning)
            assertEquals(listOf("Shavuot"), secondary.word.meanings)
            assertEquals("はやい", results.first { it.word.entryId == 7L }.word.reading)
            val written = searchWordResults(db, "五旬節", "")
            assertEquals("Shavuot", written.single().displayMeaning)
            assertEquals(0, written.single().rank)
            assertEquals(1, searchWordResults(db, "nitiyoubi", normalizeReading("nitiyoubi").orEmpty()).first().rank)
        }
    }
}
