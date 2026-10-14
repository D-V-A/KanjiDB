package com.example.kanjidb.data.dictionary

/** Dictionary-only features for Related Words; no user state or recommendation strategy. */
internal data class RelatedWordCandidate(
    val word: DictionaryWord,
    val common: Boolean,
    val maxReadingPriority: Int,
    val primaryReadingCommon: Boolean,
    val primaryReadingOrder: Int,
    val kanji: List<RelatedWordKanji>,
    val writtenOrder: Int = 0,
    val writtenCommon: Boolean = common
)

internal data class RelatedWordKanji(val character: String, val frequency: Int?, val jlpt: Int?)

internal object RelatedWordsRanker {
    internal fun counterFamily(written: String): String? = CounterConstructions.family(written)

    internal fun score(candidate: RelatedWordCandidate, currentKanji: String): Double =
        WordQuality.score(candidate, setOf(currentKanji))

    fun rank(candidates: List<RelatedWordCandidate>, currentKanji: String): List<DictionaryWord> {
        val scores = candidates.associateWith { score(it, currentKanji) }
        val sorted = candidates.sortedWith(compareByDescending<RelatedWordCandidate> { scores.getValue(it) }
            .then(WordQuality.tieBreak)).map { it.word }
        // One direct source representative; different entries never merge by gloss/reading similarity.
        val words = sorted.distinctBy { it.entryId }
        val families = mutableSetOf<String>()
        return words.filter { word ->
            val family = counterFamily(word.written)
            family == null || families.add(family)
        }
    }
}
