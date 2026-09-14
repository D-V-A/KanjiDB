package com.example.kanjidb.data.dictionary

import java.text.Normalizer
import java.util.Locale

data class KanjiSummary(val character: String, val primaryMeaning: String)
data class KanjiSearchPage(val kanji: List<KanjiSummary>, val hasMore: Boolean)

/** NFKC also accepts half-width katakana; dictionary punctuation is not pronounced. */
internal fun normalizeReading(value: String): String? {
    val input = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(".", "").replace("-", "")
        .map { if (it in '\u30A1'..'\u30F6') (it.code - 0x60).toChar() else it }
        .joinToString("")
    if (input.isEmpty()) return null
    if (input.all { it in '\u3041'..'\u3096' || it == '\u30FC' }) return input
    if (input.any { it !in 'a'..'z' && it != '\'' }) return null
    val result = StringBuilder()
    var index = 0
    while (index < input.length) {
        val current = input[index]
        val next = input.getOrNull(index + 1)
        if (current == 'n' && (next == null || next == '\'' ||
                (next !in "aeiouy" && next != 'n'))) {
            result.append('\u3093')
            index += if (next == '\'') 2 else 1
            continue
        }
        if (current == 'n' && next == 'n') {
            result.append('\u3093')
            index += if (index + 2 == input.length) 2 else 1
            continue
        }
        if (current !in "aeioun" && (current == next ||
                (current == 't' && input.startsWith("ch", index + 1)))) {
            result.append('\u3063')
            index++
            continue
        }
        val syllable = (3 downTo 1).firstNotNullOfOrNull { length ->
            input.substring(index, minOf(index + length, input.length))
                .takeIf { it.length == length && it in romajiKana }
        } ?: return null
        result.append(romajiKana.getValue(syllable))
        index += syllable.length
    }
    return result.toString()
}

internal fun toKatakana(hiragana: String): String = hiragana.map {
    if (it in '\u3041'..'\u3096') (it.code + 0x60).toChar() else it
}.joinToString("")

private val romajiKana: Map<String, String> = buildMap {
    fun row(prefix: String, kana: String) {
        "aiueo".zip(kana).forEach { (vowel, character) ->
            put("$prefix$vowel", character.toString())
        }
    }
    row("", "\u3042\u3044\u3046\u3048\u304a")
    row("k", "\u304b\u304d\u304f\u3051\u3053")
    row("g", "\u304c\u304e\u3050\u3052\u3054")
    row("s", "\u3055\u3057\u3059\u305b\u305d")
    row("z", "\u3056\u3058\u305a\u305c\u305e")
    row("t", "\u305f\u3061\u3064\u3066\u3068")
    row("d", "\u3060\u3062\u3065\u3067\u3069")
    row("n", "\u306a\u306b\u306c\u306d\u306e")
    row("h", "\u306f\u3072\u3075\u3078\u307b")
    row("b", "\u3070\u3073\u3076\u3079\u307c")
    row("p", "\u3071\u3074\u3077\u307a\u307d")
    row("m", "\u307e\u307f\u3080\u3081\u3082")
    row("r", "\u3089\u308a\u308b\u308c\u308d")
    put("ya", "\u3084"); put("yu", "\u3086"); put("yo", "\u3088")
    put("wa", "\u308f"); put("wo", "\u3092")
    put("shi", "\u3057"); put("chi", "\u3061"); put("tsu", "\u3064")
    put("fu", "\u3075"); put("ji", "\u3058")
    val palatal = mapOf(
        "ky" to "\u304d", "gy" to "\u304e", "sh" to "\u3057", "sy" to "\u3057",
        "ch" to "\u3061", "ty" to "\u3061", "cy" to "\u3061", "j" to "\u3058",
        "jy" to "\u3058", "zy" to "\u3058", "dy" to "\u3062", "ny" to "\u306b",
        "hy" to "\u3072", "by" to "\u3073", "py" to "\u3074", "my" to "\u307f", "ry" to "\u308a"
    )
    palatal.forEach { (prefix, kana) ->
        "auo".zip("\u3083\u3085\u3087").forEach { (vowel, small) ->
            put("$prefix$vowel", "$kana$small")
        }
    }
}

internal const val EXPLORE_KANJI_SQL = """
    SELECT k.character,
        COALESCE((SELECT meaning FROM kanji_meaning
                  WHERE kanji_id = k.id AND language = 'en' ORDER BY id LIMIT 1), '')
    FROM kanji k
    WHERE EXISTS (SELECT 1 FROM kanji_meaning m WHERE m.kanji_id = k.id AND m.language = 'en')
      AND EXISTS (SELECT 1 FROM kanji_reading r WHERE r.kanji_id = k.id AND r.type IN ('on', 'kun'))
      AND (k.joyo = 1 OR k.frequency IS NOT NULL OR EXISTS (
          SELECT 1 FROM word_kanji wk
          JOIN word_form f ON f.id = wk.word_form_id
          WHERE wk.kanji_id = k.id
      ))
    ORDER BY RANDOM() LIMIT 5
"""

// EXISTS avoids duplicates from multiple matching readings/meanings.
// Both scripts are compared because the asset stores on/kun in different scripts.
internal const val SEARCH_KANJI_SQL = """
    WITH input(q, hira, kata) AS (VALUES (?, ?, ?)),
    matches AS (
        SELECT k.id, k.character, k.frequency,
            CASE WHEN k.character = q THEN 0
                 WHEN EXISTS (
                     SELECT 1 FROM kanji_reading r WHERE r.kanji_id = k.id AND hira <> ''
                     AND REPLACE(REPLACE(r.reading, '.', ''), '-', '') IN (hira, kata)
                 ) OR EXISTS (
                     SELECT 1 FROM kanji_meaning m WHERE m.kanji_id = k.id AND m.language = 'en'
                     AND TRIM(m.meaning) = q COLLATE NOCASE
                 ) THEN 1 ELSE 2 END AS match_rank
        FROM kanji k CROSS JOIN input
        WHERE k.character = q OR EXISTS (
            SELECT 1 FROM kanji_reading r WHERE r.kanji_id = k.id AND hira <> ''
            AND (INSTR(REPLACE(REPLACE(r.reading, '.', ''), '-', ''), hira) > 0
                 OR INSTR(REPLACE(REPLACE(r.reading, '.', ''), '-', ''), kata) > 0)
        ) OR EXISTS (
            SELECT 1 FROM kanji_meaning m WHERE m.kanji_id = k.id AND m.language = 'en'
            AND INSTR(LOWER(m.meaning), q) > 0
        )
    )
    SELECT k.character,
        COALESCE((SELECT meaning FROM kanji_meaning
                  WHERE kanji_id = k.id AND language = 'en' ORDER BY id LIMIT 1), '')
    FROM matches k
    ORDER BY k.match_rank, k.frequency IS NULL, k.frequency, k.id
    LIMIT ?
"""
