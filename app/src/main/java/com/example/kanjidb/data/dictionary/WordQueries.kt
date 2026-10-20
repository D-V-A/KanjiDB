package com.example.kanjidb.data.dictionary

/** Explicit source realizations; sharing written_id never changes source entry identity. */
internal const val VALID_WORD_FORMS_SQL = """
    SELECT f.id, f.entry_id, f.written_id, w.text AS written, r.text AS reading,
        MAX(ew.common,r.common) AS common, r.reading_priority, r.reading_order, ew.written_order
    FROM word_entry_written_reading f
    JOIN word_written w ON w.id=f.written_id
    JOIN word_reading r ON r.id=f.reading_id AND r.entry_id=f.entry_id
    JOIN word_entry_written ew ON ew.entry_id=f.entry_id AND ew.written_id=f.written_id
"""

internal const val WORD_READINGS_SQL = """
    SELECT r.text FROM word_entry_written_reading f
    JOIN word_reading r ON r.id=f.reading_id JOIN word_written w ON w.id=f.written_id
    WHERE f.entry_id=? AND w.text=? ORDER BY r.reading_order,r.id
"""

internal const val WORD_MEANINGS_SQL = """
    SELECT m.language, TRIM(m.meaning), s.id, s.sense_index, m.id, m.gloss_order, r.text
    FROM word_entry_written_reading f JOIN word_written w ON w.id=f.written_id
    JOIN word_reading r ON r.id=f.reading_id
    JOIN word_sense_form sf ON sf.form_id=f.id AND sf.entry_id=f.entry_id
    JOIN word_sense s ON s.id=sf.sense_id AND s.entry_id=f.entry_id
    JOIN word_meaning m ON m.sense_id=s.id
    WHERE f.entry_id=? AND w.text=?
    ORDER BY s.sense_index,m.gloss_order,m.id,r.reading_order
"""

internal const val WORD_KANJI_SQL = """
    SELECT k.character, (SELECT meaning FROM kanji_meaning WHERE kanji_id=k.id AND language='en' ORDER BY id LIMIT 1)
    FROM word_written w JOIN word_written_kanji wk ON wk.written_id=w.id JOIN kanji k ON k.id=wk.kanji_id
    WHERE w.text=? AND EXISTS (SELECT 1 FROM word_entry_written ew WHERE ew.entry_id=? AND ew.written_id=w.id)
    GROUP BY k.id ORDER BY MIN(wk.position)
"""

internal const val WORDS_SQL = """
    WITH forms AS ($VALID_WORD_FORMS_SQL), writings AS (
        SELECT DISTINCT written_id FROM word_written_kanji WHERE kanji_id=?
    )
    SELECT f.* FROM writings wk CROSS JOIN forms f
    WHERE f.written_id=wk.written_id
      AND (?='0' OR EXISTS (SELECT 1 FROM forms c WHERE c.entry_id=f.entry_id AND c.written_id=f.written_id AND c.common=1))
      AND f.id=(SELECT p.id FROM forms p WHERE p.entry_id=f.entry_id AND p.written_id=f.written_id
          ORDER BY p.reading_order,p.id LIMIT 1)
"""

internal fun wordTrainingFormsSql(poolSize: Int): String {
    require(poolSize>0)
    return """
        WITH forms AS ($VALID_WORD_FORMS_SQL), writings AS (
            SELECT DISTINCT wk.written_id FROM kanji k JOIN word_written_kanji wk ON wk.kanji_id=k.id
            WHERE k.character IN (${List(poolSize){"?"}.joinToString(",")}))
        SELECT f.* FROM writings wk CROSS JOIN forms f WHERE f.written_id=wk.written_id
    """
}

internal fun candidateFeaturesSql(selected: String) = """
    WITH selected AS ($selected), forms AS ($VALID_WORD_FORMS_SQL)
    SELECT s.id,s.entry_id,s.written,s.reading,MAX(v.common),MAX(v.reading_priority),s.common,s.reading_order,s.written_order,ew.common
    FROM selected s JOIN forms v ON v.entry_id=s.entry_id AND v.written_id=s.written_id
    JOIN word_entry_written ew ON ew.entry_id=s.entry_id AND ew.written_id=s.written_id
    GROUP BY s.id ORDER BY s.entry_id,s.written_order,s.written
"""

internal fun candidateMeaningsSql(selected: String, primaryReadingOnly: Boolean) = """
    WITH selected AS ($selected)
    SELECT s.id,m.meaning FROM selected s CROSS JOIN word_entry_written_reading f
    CROSS JOIN word_sense_form sf CROSS JOIN word_sense sense CROSS JOIN word_meaning m
    WHERE f.entry_id=s.entry_id AND f.written_id=s.written_id ${if(primaryReadingOnly) "AND f.id=s.id" else ""}
      AND sf.form_id=f.id AND sf.entry_id=s.entry_id AND sense.id=sf.sense_id AND m.sense_id=sense.id AND m.language='en'
    GROUP BY s.id,m.id ORDER BY s.id,sense.sense_index,m.gloss_order,m.id
"""

internal fun candidateKanjiSql(selected: String, hasJlpt: Boolean) = """
    WITH selected AS ($selected)
    SELECT s.id,k.character,k.frequency,${if(hasJlpt) "j.level" else "NULL"}
    FROM selected s JOIN word_written_kanji wk ON wk.written_id=s.written_id JOIN kanji k ON k.id=wk.kanji_id
    ${if(hasJlpt) "LEFT JOIN jlpt_kanji j ON j.kanji_id=k.id" else ""}
    GROUP BY s.id,k.id ORDER BY s.id,MIN(wk.position)
"""
