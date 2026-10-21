package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],manifest=Config.NONE)
class WordReadingOrderTest {
    @Test fun restrictionsAndReadingOrderSurviveWithoutCrossEntryOrAlternativeContamination() = runBlocking {
        SQLiteDatabase.create(null).use { db ->
            val f=WordDatabaseFixture(db)
            f.form(1,1,"今日","きょう",priority=1000)
            f.form(2,1,"今日","こんにち",order=1,common=true,priority=2380)
            f.form(3,1,"別","べつ",order=2,writtenOrder=1)
            f.form(4,2,"今日","ひ",common=true)
            f.meaning(1,1,"today"); f.meaning(2,1,"today")
            val secondary=f.meaning(2,2,"these days")
            f.meaning(3,3,"other spelling only"); f.meaning(4,0,"different source")
            val kid=f.kanji("今日","今",0); f.kanji("今日","日",1)
            val details=loadWordDetails(db,1,"今日")!!
            assertEquals(listOf("きょう","こんにち"),details.readings)
            assertEquals(listOf("today","these days"),details.meaningGroups["en"])
            assertEquals(listOf("別"),details.alternativeWrittenForms)
            assertEquals(listOf(2L),details.otherEntries.map { it.entryId })
            assertEquals(2,details.readingMeaningGroups.size)
            assertEquals(listOf("こんにち"),details.glosses.first { it.glossId==secondary }.readings)
            assertEquals("these days",details.highlightedMeaning(1,secondary))
            assertEquals("these days",details.highlightedMeaning(1,secondary,"these days"))
            assertNull(details.highlightedMeaning(1,secondary,"unrelated restored gloss"))
            assertNull(details.highlightedMeaning(2,secondary))
            assertNull(loadWordDetails(db,1,"別")!!.highlightedMeaning(1,secondary))
            db.rawQuery(candidateFeaturesSql(WORDS_SQL),arrayOf(kid.toString(),"0")).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("きょう",c.getString(3))
                assertEquals(2380,c.getInt(5)); assertEquals(0,c.getInt(6))
            }
            val direct=searchWordResults(db,"今日","")
            assertEquals(1,direct.size) // exact written collapse, without physically merging sources
            val meaning=searchWordResults(db,"these days","").single()
            assertEquals(1L,meaning.word.entryId); assertEquals(secondary,meaning.matchedGlossId)
            val reading=searchWordResults(db,"こんにち","こんにち").single()
            assertEquals("こんにち",reading.word.reading)
            assertEquals(WordMatchKind.READING,reading.matchKind)
        }
    }

    @Test fun alternativesStayInsideActiveEntryEvenWhenWrittenLinksAnotherEntry() {
        SQLiteDatabase.create(null).use { db ->
            val f=WordDatabaseFixture(db)
            f.form(1,1,"男","お"); f.form(2,1,"夫","お",writtenOrder=1)
            f.form(3,2,"夫","つま"); f.form(4,2,"妻","つま",writtenOrder=1)
            f.form(5,3,"妻","め"); f.form(6,3,"女","め",writtenOrder=1)
            (1..6).forEach { f.meaning(it,0,"source") }
            assertEquals(listOf("夫"),loadWordDetails(db,1,"男")!!.alternativeWrittenForms)
            val husband=loadWordDetails(db,1,"夫")!!
            assertEquals(listOf("男"),husband.alternativeWrittenForms)
            assertEquals(listOf(2L),husband.otherEntries.map { it.entryId })
            assertEquals(listOf("妻"),loadWordDetails(db,2,"夫")!!.alternativeWrittenForms)
            assertFalse(loadWordDetails(db,1,"男")!!.alternativeWrittenForms.contains("女"))
        }
    }

    @Test fun poolQueriesUseActualWrittenKanjiAndExploreRetainsEntryIdentity() {
        SQLiteDatabase.create(null).use { db ->
            val f=WordDatabaseFixture(db)
            f.form(1,1,"今時","いまどき",common=true)
            f.form(2,1,"今どき","いまどき",common=true,writtenOrder=1)
            f.form(3,2,"日","ひ",common=true)
            f.meaning(1,0,"nowadays"); f.meaning(2,0,"nowadays"); f.meaning(3,0,"day")
            f.kanji("今時","今",0); f.kanji("今時","時",1); f.kanji("今どき","今",0); f.kanji("日","日",0)
            db.rawQuery(wordTrainingFormsSql(1),arrayOf("時")).use { c ->
                assertEquals(1,c.count); c.moveToFirst(); assertEquals("今時",c.getString(3))
            }
            db.rawQuery(EXPLORE_WORDS_SQL,null).use { c -> assertEquals(2,c.count) }
        }
    }

    @Test fun schemaRejectsCrossEntryReadingAndSenseApplicability() {
        SQLiteDatabase.create(null).use { db ->
            val f=WordDatabaseFixture(db)
            f.form(1,1,"男","お"); f.form(2,2,"女","め")
            f.meaning(1,0,"male"); f.meaning(2,0,"female")
            val wid=db.rawQuery("SELECT written_id FROM word_entry_written_reading WHERE id=1",null).use { it.moveToFirst();it.getLong(0) }
            val rid=db.rawQuery("SELECT reading_id FROM word_entry_written_reading WHERE id=2",null).use { it.moveToFirst();it.getLong(0) }
            assertTrue(runCatching { db.execSQL("INSERT INTO word_entry_written_reading VALUES (99,1,?,?)",arrayOf<Any>(wid,rid)) }.isFailure)
            val sid=db.rawQuery("SELECT id FROM word_sense WHERE entry_id=2",null).use { it.moveToFirst();it.getLong(0) }
            assertTrue(runCatching { db.execSQL("INSERT INTO word_sense_form VALUES (1,?,1)",arrayOf(sid)) }.isFailure)
        }
    }
}
