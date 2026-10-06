package com.example.kanjidb.data.dictionary

/** Same gloss order for Word Details headings and Word Training translation prompts. */
internal fun orderedWordMeanings(groups: Map<String, List<String>>): List<String> =
    (groups["en"].orEmpty() + groups.filterKeys { it != "en" }.values.flatten()).distinct()

/** Count actual kanji occurrences, while callers use a set for coverage and per-question scoring. */
internal fun writtenKanji(written: String): List<String> = buildList {
    var offset = 0
    while (offset < written.length) {
        val codePoint = written.codePointAt(offset)
        if (codePoint in 0x3400..0x4DBF || codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF || codePoint in 0x20000..0x2FA1F ||
            codePoint in 0x30000..0x323AF || codePoint == 0x3007) {
            add(String(Character.toChars(codePoint)))
        }
        offset += Character.charCount(codePoint)
    }
}

internal data class WordTrainingCandidate(
    val features: RelatedWordCandidate,
    val primaryMeaning: String?,
    val kanjiOccurrences: List<String> = writtenKanji(features.word.written)
) {
    val word get() = features.word
    val key = features.word.entryId to features.word.written
    val kanji = kanjiOccurrences.toSet()
}
