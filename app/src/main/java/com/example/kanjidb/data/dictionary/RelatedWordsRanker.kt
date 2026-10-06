package com.example.kanjidb.data.dictionary

/** Dictionary-only features for Related Words; no user state or recommendation strategy. */
internal data class RelatedWordCandidate(
    val word: DictionaryWord,
    val common: Boolean,
    val maxReadingPriority: Int,
    val primaryReadingCommon: Boolean,
    val primaryReadingOrder: Int,
    val kanji: List<RelatedWordKanji>
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
        // Existing equivalence semantics retain distinct readings/senses, choosing the best form first.
        val words = sorted.distinctBy { it.entryId to it.written }.deduplicateCommonWords()
        val families = mutableSetOf<String>()
        return words.filter { word ->
            val family = counterFamily(word.written)
            family == null || families.add(family)
        }
    }
}
