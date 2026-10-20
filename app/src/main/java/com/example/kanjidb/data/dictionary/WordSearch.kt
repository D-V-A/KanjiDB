package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class WordSearchPage(val words: List<WordSearchResult>, val hasMore: Boolean)

internal const val SEARCH_WORDS_SQL = """
    WITH forms AS ($VALID_WORD_FORMS_SQL), input(q,hira,kata) AS (VALUES (?,?,?)), hits AS (
        SELECT f.*, CASE WHEN f.written=q THEN 0 WHEN f.reading IN(hira,kata) AND hira<>'' THEN 1 ELSE 2 END AS form_rank,
            NULL AS matched_meaning, -1 AS gloss_id, -1 AS sense_id,
            CASE WHEN f.written=q THEN 0 WHEN hira<>'' AND f.reading IN(hira,kata) THEN 1
                 WHEN INSTR(LOWER(f.written),q)>0 THEN 0 ELSE 1 END AS match_kind
        FROM forms f CROSS JOIN input
        WHERE INSTR(LOWER(f.written),q)>0 OR (hira<>'' AND (INSTR(f.reading,hira)>0 OR INSTR(f.reading,kata)>0))
        UNION ALL
        SELECT f.*,9,m.meaning,m.id,sf.sense_id,2 FROM input CROSS JOIN word_meaning m
        CROSS JOIN word_sense_form sf CROSS JOIN forms f
        WHERE m.language='en' AND INSTR(LOWER(m.meaning),q)>0
          AND sf.sense_id=m.sense_id AND f.id=sf.form_id
    )
    SELECT h.entry_id,h.written,h.reading,
        (SELECT m.meaning FROM word_entry_written_reading f CROSS JOIN word_sense_form sf CROSS JOIN word_sense s CROSS JOIN word_meaning m
         WHERE f.entry_id=h.entry_id AND f.written_id=h.written_id AND m.language='en'
           AND sf.form_id=f.id AND s.id=sf.sense_id AND m.sense_id=s.id
         ORDER BY s.sense_index,m.gloss_order,m.id LIMIT 1) AS primary_meaning,
        h.form_rank,h.matched_meaning,h.gloss_id,h.common,h.reading_priority,h.sense_id,h.match_kind,h.written_order,h.reading_order,
        (SELECT p.reading FROM forms p WHERE p.entry_id=h.entry_id AND p.written_id=h.written_id ORDER BY p.reading_order,p.id LIMIT 1)
    FROM hits h ORDER BY h.entry_id,h.written_order,h.reading_order,h.gloss_id
"""

internal const val EXPLORE_WORDS_SQL = """
    WITH forms AS ($VALID_WORD_FORMS_SQL), chosen AS (
        SELECT entry_id FROM forms WHERE common=1 GROUP BY entry_id ORDER BY RANDOM() LIMIT 5
    )
    SELECT f.entry_id,f.written,f.reading,
        (SELECT m.meaning FROM word_sense_form sf CROSS JOIN word_sense s CROSS JOIN word_meaning m
         WHERE sf.form_id=f.id AND s.id=sf.sense_id AND m.sense_id=s.id AND m.language='en'
         ORDER BY s.sense_index,m.gloss_order,m.id LIMIT 1)
    FROM chosen c JOIN forms f ON f.id=(
        SELECT p.id FROM forms p WHERE p.entry_id=c.entry_id AND p.written_id=(
            SELECT w.written_id FROM forms w WHERE w.entry_id=c.entry_id AND w.common=1
            ORDER BY w.written_order,w.reading_order,w.written LIMIT 1)
        ORDER BY p.reading_order,p.id LIMIT 1)
"""

/** Entry selection and exact displayed-written collapse occur before callers paginate. */
internal fun selectWordSearchCards(results: List<WordSearchResult>): List<WordSearchResult> =
    results.sortedWith(wordSearchOrder).distinctBy { it.word.entryId }.distinctBy { it.word.written }

internal suspend fun searchWordResults(db: SQLiteDatabase, text: String, reading: String): List<WordSearchResult> {
    val entries = linkedMapOf<Long,WordSearchResult>()
    db.rawQuery(SEARCH_WORDS_SQL,arrayOf(text,reading,toKatakana(reading))).use { c ->
        while(c.moveToNext()) {
            coroutineContext.ensureActive()
            val kind=WordMatchKind.entries[c.getInt(10)]
            val primary=c.getString(3)
            val meaning=c.getString(5)
            val displayedReading=if(kind==WordMatchKind.READING) c.getString(2) else c.getString(13)
            val word=DictionaryWord(c.getLong(0),c.getString(1),displayedReading,
                primary?.let { listOf(it) }.orEmpty())
            val result=WordSearchResult(word,primary,meaning,
                meaning?.let { englishMeaningMatch(it,text,it==primary) },c.getInt(4),c.getInt(7),c.getInt(8),
                kind,c.getLong(6),c.getLong(9),c.getString(2),c.getInt(11),c.getInt(12))
            val previous=entries[word.entryId]
            if(previous==null || wordSearchOrder.compare(result,previous)<0) entries[word.entryId]=result
        }
    }
    return selectWordSearchCards(entries.values.toList())
}
