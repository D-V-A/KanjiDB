package com.example.kanjidb.ui.search

import com.example.kanjidb.data.dictionary.*
import org.junit.Assert.*
import org.junit.Test

class WordSearchGroupingTest {
    @Test fun visualGroupRetainsEveryCardAndMatchContextLongestFirst() {
        val short=hit(1,"日").copy(matchedMeaning="secondary",matchedGlossId=99)
        val long=hit(2,"日曜日")
        val groups=groupWordSearchResults(listOf(short,long))
        assertEquals(listOf(listOf(long,short)),groups)
        assertEquals(99L,groups.single().last().matchedGlossId)
    }

    @Test fun shortWritingCannotBridgeIncompatibleLongWritings() {
        val groups=groupWordSearchResults(listOf(hit(1,"日曜日"),hit(2,"日"),hit(3,"休日")))
        assertEquals(listOf(2,1),groups.map { it.size })
        assertEquals(setOf(1L,2L,3L),groups.flatten().map { it.word.entryId }.toSet())
    }

    @Test fun groupingRequiresExactNormalizedPrimaryMeaningAndOrderedWriting() {
        val a=hit(1,"日").copy(primaryMeaning=" Day ")
        val b=hit(2,"日曜日").copy(primaryMeaning="day")
        val c=hit(3,"休日").copy(primaryMeaning="holiday")
        val d=hit(4,"月").copy(primaryMeaning="day")
        assertEquals(listOf(2,1,1),groupWordSearchResults(listOf(a,b,c,d)).map { it.size })
        assertEquals(2,groupWordSearchResults(listOf(a.copy(primaryMeaning=null),b)).size)
        assertFalse(orderedSubsequence("日月","月日"))
    }
    private fun hit(id: Long,w: String,rank: Int=9) = WordSearchResult(DictionaryWord(id,w,"r",listOf("same")),"same",null,null,rank)

    @Test fun oneCardPerEntryThenExactWritingBeforePagination() {
        val cards=selectWordSearchCards(listOf(hit(1,"今時",2),hit(1,"今どき",0),hit(2,"今どき",1),hit(3,"今",3)))
        assertEquals(listOf("今どき","今"),cards.map { it.word.written })
        assertEquals(listOf(1L,3L),cards.map { it.word.entryId })
    }
    @Test fun similarWritingAndSameMeaningsNeverMergeAcrossEntries() {
        val cards=selectWordSearchCards(listOf(hit(1,"飛びきり"),hit(2,"とびっ切り"),hit(3,"日"),hit(4,"日曜")))
        assertEquals(4,cards.size)
    }
}
