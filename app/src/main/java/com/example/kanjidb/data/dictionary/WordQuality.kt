package com.example.kanjidb.data.dictionary

/** Shared static features only; each caller retains its own selection algorithm. */
internal object WordQuality {
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

    fun score(candidate: RelatedWordCandidate, excludedKanji: Set<String>): Double {
        val length = candidate.word.written.codePointCount(0, candidate.word.written.length)
        val otherKanji = candidate.kanji.distinctBy { it.character }.filter { it.character !in excludedKanji }
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
            (if (CounterConstructions.family(candidate.word.written) != null) COUNTER_PENALTY else 0.0)
    }


    val tieBreak: Comparator<RelatedWordCandidate> = compareByDescending<RelatedWordCandidate> { it.common }
        .thenByDescending { it.maxReadingPriority }
        .thenBy { it.primaryReadingOrder }
        .thenBy { it.word.entryId }
        .thenBy { it.word.written }
        .thenBy { it.word.reading }
}
