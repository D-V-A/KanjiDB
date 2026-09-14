package com.example.kanjidb.data.dictionary

data class WordSearchPage(val words: List<DictionaryWord>, val hasMore: Boolean)

// Limit written-form matches before fetching preferred readings and display glosses.
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
        SELECT f.entry_id, f.written,
            CASE WHEN TRIM(m.meaning) = q COLLATE NOCASE THEN 1 ELSE 2 END
        FROM word_meaning m JOIN word_form f ON f.id = m.word_form_id CROSS JOIN input
        WHERE m.language = 'en' AND INSTR(LOWER(m.meaning), q) > 0
    ), page AS (
        SELECT entry_id, written, MIN(rank) AS rank FROM hits
        GROUP BY entry_id, written ORDER BY rank, written, entry_id LIMIT ?
    )
    SELECT f.entry_id, f.written, f.reading,
        (SELECT m.meaning FROM word_meaning m WHERE m.word_form_id = f.id AND m.language = 'en'
         ORDER BY m.sense_index, m.id LIMIT 1)
    FROM page p JOIN word_form f ON f.id = (
        SELECT preferred.id FROM word_form preferred
        WHERE preferred.entry_id = p.entry_id AND preferred.written = p.written
        ORDER BY preferred.reading_priority DESC, preferred.reading_order, preferred.id LIMIT 1
    )
    ORDER BY p.rank, p.written, p.entry_id
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
        WHERE preferred.entry_id = c.entry_id AND preferred.common = 1
        ORDER BY preferred.reading_priority DESC, preferred.reading_order, preferred.id LIMIT 1
    )
"""
