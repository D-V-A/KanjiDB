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
    private const val COMMON_BONUS = 40.0
    private const val PRIORITY_BONUS = 30.0
    private const val PRIORITY_CAP = 4000
    private const val PRIMARY_COMMON_BONUS = 4.0
    private const val FREQUENCY_BONUS = 6.0
    private const val FREQUENCY_SCALE = 2500.0
    private const val RARE_FREQUENCY_THRESHOLD = 2000
    private const val RARE_KANJI_PENALTY = 2.0
    private const val UNKNOWN_FREQUENCY_PENALTY = 4.0
    private const val JLPT_LEVEL_BONUS = 0.6
    private const val SHORT_WORD_LENGTH = 4
    private const val LONG_WORD_LENGTH = 8
    private const val EXTRA_CHARACTER_PENALTY = 4.0
    private const val LONG_CHARACTER_PENALTY = 4.0
    private const val COUNTER_PENALTY = 12.0

    // Exact suffixes only. Calendar/time/age counters have too many lexical ambiguities for v1.
    private val counters = setOf("本", "人", "個", "枚", "匹", "台", "冊", "回", "階")
    private val digits = "一二三四五六七八九"
    // Deliberately recognize only canonical kanji numerals 1..99, not arbitrary digit strings.
    // In particular 万人 (everybody) and 千六本 (a lexicalized term) must not collapse.
    private val kanjiNumerals = buildSet {
        digits.forEach { add(it.toString()) }
        for (tens in 1..9) {
            val prefix = (if (tens == 1) "" else digits[tens - 1].toString()) + "十"
            add(prefix)
            digits.forEach { add(prefix + it) }
        }
    }

    internal fun counterFamily(written: String): String? {
        val counter = counters.firstOrNull { written.endsWith(it) } ?: return null
        val number = written.dropLast(counter.length)
        val decimal = number.isNotEmpty() && number.all { it in '0'..'9' || it in '０'..'９' }
        return if (decimal || number in kanjiNumerals) "#$counter" else null
    }

    internal fun score(candidate: RelatedWordCandidate, currentKanji: String): Double {
        val length = candidate.word.written.codePointCount(0, candidate.word.written.length)
        val otherKanji = candidate.kanji.distinctBy { it.character }.filter { it.character != currentKanji }
        val kanjiUsefulness = otherKanji.map { kanji ->
            val frequency = kanji.frequency
            val frequencyScore = if (frequency == null) -UNKNOWN_FREQUENCY_PENALTY else {
                FREQUENCY_BONUS * (1.0 - (frequency - 1) / FREQUENCY_SCALE).coerceIn(0.0, 1.0) -
                    if (frequency > RARE_FREQUENCY_THRESHOLD) RARE_KANJI_PENALTY else 0.0
            }
            frequencyScore + (kanji.jlpt?.takeIf { it in 1..5 } ?: 0) * JLPT_LEVEL_BONUS
        }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        // Priority is the importer's sum of reading tags, not a corpus frequency or reading selector.
        return (if (candidate.common) COMMON_BONUS else 0.0) +
            PRIORITY_BONUS * candidate.maxReadingPriority.coerceIn(0, PRIORITY_CAP) / PRIORITY_CAP +
            (if (candidate.primaryReadingCommon) PRIMARY_COMMON_BONUS else 0.0) + kanjiUsefulness -
            (length - SHORT_WORD_LENGTH).coerceAtLeast(0) * EXTRA_CHARACTER_PENALTY -
            (length - LONG_WORD_LENGTH).coerceAtLeast(0) * LONG_CHARACTER_PENALTY -
            (if (counterFamily(candidate.word.written) != null) COUNTER_PENALTY else 0.0)
    }

    fun rank(candidates: List<RelatedWordCandidate>, currentKanji: String): List<DictionaryWord> {
        val sorted = candidates.map { it to score(it, currentKanji) }.sortedWith(
            compareByDescending<Pair<RelatedWordCandidate, Double>> { it.second }
                .thenByDescending { it.first.common }
                .thenByDescending { it.first.maxReadingPriority }
                .thenBy { it.first.primaryReadingOrder }
                .thenBy { it.first.word.entryId }
                .thenBy { it.first.word.written }
                .thenBy { it.first.word.reading }
        ).map { it.first.word }
        // Existing equivalence semantics retain distinct readings/senses, choosing the best form first.
        val words = sorted.distinctBy { it.entryId to it.written }.deduplicateCommonWords()
        val families = mutableSetOf<String>()
        return words.filter { word ->
            val family = counterFamily(word.written)
            family == null || families.add(family)
        }
    }
}
