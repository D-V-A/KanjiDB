package com.example.kanjidb.data.dictionary

import org.junit.Assert.*
import org.junit.Test

class CommonWordsTest {
    @Test fun relatedRepresentativesUseSourceEntryNotReadingOrGlossOverlap() {
        fun candidate(id: Long,w: String,r: String,meanings: List<String>,common: Boolean=false) =
            RelatedWordCandidate(DictionaryWord(id,w,r,meanings),common,0,common,0,emptyList())
        val first=candidate(1,"飛切り","とびきり",listOf("best"),true)
        val sameEntry=candidate(1,"飛び切り","different",listOf("different"))
        val other=candidate(2,"とびっ切り","とびっきり",listOf("best"))
        val result=RelatedWordsRanker.rank(listOf(sameEntry,other,first),"切")
        assertEquals(listOf(first.word,other.word),result)
    }
}
