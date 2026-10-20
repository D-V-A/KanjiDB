package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase

data class WordGloss(val senseId: Long, val glossId: Long, val language: String, val text: String, val readings: List<String>)
data class ReadingMeaningGroup(val readings: List<String>, val glosses: List<WordGloss>)

internal fun loadWordDetails(db: SQLiteDatabase, entryId: Long, written: String): DictionaryWordDetails? {
    val args=arrayOf(entryId.toString(),written)
    val readings=db.rawQuery(WORD_READINGS_SQL,args).use { c ->
        buildList { while(c.moveToNext()) add(c.getString(0)) }.distinct()
    }
    if(readings.isEmpty()) return null
    val glossRows=linkedMapOf<Long,WordGloss>()
    db.rawQuery(WORD_MEANINGS_SQL,args).use { c ->
        while(c.moveToNext()) {
            val id=c.getLong(4)
            val old=glossRows[id]
            glossRows[id]=if(old==null) WordGloss(c.getLong(2),id,c.getString(0),c.getString(1),listOf(c.getString(6)))
                else old.copy(readings=(old.readings+c.getString(6)).distinct())
        }
    }
    val glosses=glossRows.values.toList()
    val meaningGroups=linkedMapOf<String,List<String>>()
    glosses.groupBy { it.language }.forEach { (lang,gs) -> meaningGroups[lang]=gs.map { it.text }.distinct() }
    // Exact applicability groups: no overlap/similarity heuristic and no loss of source sense IDs.
    val grouped=readings.groupBy { r -> glosses.filter { r in it.readings }.map { it.glossId } }.map { (ids,rs) ->
        ReadingMeaningGroup(rs,ids.map { glossRows.getValue(it) })
    }
    val alternatives=db.rawQuery("""
        SELECT w.text FROM word_entry_written ew JOIN word_written w ON w.id=ew.written_id
        WHERE ew.entry_id=? AND w.text<>? AND EXISTS (
            SELECT 1 FROM word_entry_written_reading f WHERE f.entry_id=ew.entry_id AND f.written_id=ew.written_id)
        ORDER BY ew.written_order,w.text
    """.trimIndent(),args).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    val others=db.rawQuery("""
        WITH forms AS ($VALID_WORD_FORMS_SQL)
        SELECT f.entry_id,f.written,f.reading,
            (SELECT m.meaning FROM word_entry_written_reading v CROSS JOIN word_sense_form sf CROSS JOIN word_sense s CROSS JOIN word_meaning m
             WHERE v.entry_id=f.entry_id AND v.written_id=f.written_id AND m.language='en'
               AND sf.form_id=v.id AND s.id=sf.sense_id AND m.sense_id=s.id
             ORDER BY s.sense_index,m.gloss_order,m.id LIMIT 1)
        FROM forms f WHERE f.written=? AND f.entry_id<>?
            AND f.id=(SELECT p.id FROM forms p WHERE p.entry_id=f.entry_id AND p.written_id=f.written_id
                ORDER BY p.reading_order,p.id LIMIT 1)
        ORDER BY f.entry_id
    """.trimIndent(),arrayOf(written,entryId.toString())).use { c ->
        buildList { while(c.moveToNext()) add(DictionaryWord(c.getLong(0),c.getString(1),c.getString(2),
            c.getString(3)?.let { listOf(it) }.orEmpty())) }
    }
    val kanji=db.rawQuery(WORD_KANJI_SQL,arrayOf(written,entryId.toString())).use { c ->
        buildList { while(c.moveToNext()) add(KanjiSummary(c.getString(0),c.getString(1).orEmpty())) }
    }
    return DictionaryWordDetails(entryId,written,readings,meaningGroups,alternatives,readings.first(),kanji,glosses,grouped,others)
}

/** Search highlight is scoped to its source entry and an applicable source gloss. */
internal fun DictionaryWordDetails.highlightedMeaning(originalEntry: Long, matchedGloss: Long, matchedText: String? = null): String? =
    if(entryId!=originalEntry) null else glosses.firstOrNull {
        it.glossId==matchedGloss && (matchedText==null || it.text==matchedText.trim())
    }?.text
