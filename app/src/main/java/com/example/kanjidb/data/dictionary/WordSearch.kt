package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class WordSearchPage(val words: List<WordSearchResult>, val hasMore: Boolean)

/** All candidates, before pagination: gloss restrictions remain attached to their valid form. */
internal const val SEARCH_WORDS_SQL = """
    WITH input(q, hira, kata) AS (VALUES (?, ?, ?)),
    hits AS (
        SELECT f.entry_id, f.written,
            CASE WHEN f.written = q THEN 0
                 WHEN f.reading IN (hira, kata) THEN 1 ELSE 2 END AS rank
        FROM word_form f CROSS JOIN input
        WHERE INSTR(LOWER(f.written), q) > 0
           OR (hira <> '' AND (INSTR(f.reading, hira) > 0 OR INSTR(f.reading, kata) > 0))
        UNION ALL
        SELECT f.entry_id, f.written, 9
        FROM word_meaning m JOIN word_form f ON f.id = m.word_form_id CROSS JOIN input
        WHERE m.language = 'en' AND INSTR(LOWER(m.meaning), q) > 0
    ), candidates AS (
        SELECT entry_id, written, MIN(rank) AS rank FROM hits GROUP BY entry_id, written
    ), summaries AS (
        SELECT p.entry_id, p.written, p.rank,
            (SELECT preferred.reading FROM word_form preferred
             WHERE preferred.entry_id = p.entry_id AND preferred.written = p.written
             ORDER BY preferred.reading_order, preferred.id LIMIT 1) AS reading,
            (SELECT m.meaning FROM word_form v CROSS JOIN word_meaning m
             WHERE m.word_form_id = v.id AND v.entry_id = p.entry_id
               AND v.written = p.written AND m.language = 'en'
             ORDER BY m.sense_index, m.id LIMIT 1) AS primary_meaning,
            MAX(v.common) AS common, MAX(v.reading_priority) AS priority
        FROM candidates p JOIN word_form v ON v.entry_id = p.entry_id AND v.written = p.written
        GROUP BY p.entry_id, p.written
    )
    SELECT s.entry_id, s.written, s.reading, s.primary_meaning, s.rank,
        m.meaning, MIN(m.id), s.common, s.priority
    FROM summaries s
    LEFT JOIN word_form v ON v.entry_id = s.entry_id AND v.written = s.written
    LEFT JOIN word_meaning m ON m.word_form_id = v.id AND m.language = 'en'
        AND INSTR(LOWER(m.meaning), (SELECT q FROM input)) > 0
    GROUP BY s.entry_id, s.written, m.meaning
    ORDER BY s.rank, s.written, s.entry_id, MIN(m.sense_index), MIN(m.id)
"""

// One common representative per randomly selected entry; no duplicate entries in a set.
internal const val EXPLORE_WORDS_SQL = """
    WITH chosen AS (
        SELECT entry_id FROM word_form WHERE common = 1
        GROUP BY entry_id ORDER BY RANDOM() LIMIT 5
    )
    SELECT f.entry_id, f.written, f.reading,
        (SELECT m.meaning FROM word_meaning m WHERE m.word_form_id = f.id AND m.language = 'en'
         ORDER BY m.sense_index, m.id LIMIT 1)
    FROM chosen c JOIN word_form f ON f.id = (
        SELECT preferred.id FROM word_form preferred
        WHERE preferred.entry_id = c.entry_id AND preferred.written = (
            SELECT written FROM word_form WHERE entry_id = c.entry_id AND common = 1
            ORDER BY reading_order, id LIMIT 1
        )
        ORDER BY preferred.reading_order, preferred.id LIMIT 1
    )
"""

internal suspend fun searchWordResults(db: SQLiteDatabase, text: String, reading: String): List<WordSearchResult> {
    val candidates = linkedMapOf<Pair<Long, String>, WordSearchResult>()
    db.rawQuery(SEARCH_WORDS_SQL, arrayOf(text, reading, toKatakana(reading))).use { cursor ->
        while (cursor.moveToNext()) {
            coroutineContext.ensureActive()
            val word = DictionaryWord(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                if (cursor.isNull(3)) emptyList() else listOf(cursor.getString(3)))
            val primary = cursor.getString(3)
            val meaning = cursor.getString(5)
            val match = meaning?.let { englishMeaningMatch(it, text, it == primary) }
            val result = WordSearchResult(word, primary, meaning, match,
                cursor.getInt(4), cursor.getInt(7), cursor.getInt(8))
            val key = word.entryId to word.written
            val previous = candidates[key]
            if (previous == null || wordSearchOrder.compare(result, previous) < 0) {
                candidates[key] = result
            }
        }
    }
    return candidates.values.sortedWith(wordSearchOrder)
}
