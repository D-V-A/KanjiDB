package com.example.kanjidb.ui.training

import com.example.kanjidb.data.dictionary.*

internal enum class WordPrompt(val title: String) { TRANSLATION("Translation"), READING("Reading"), BOTH("Both") }
internal enum class WordLength(val title: String) {
    SHORT("Shorter than 4"), ANY("Any"), LONGER_TWO("Longer than 2"), LONGER_THREE("Longer than 3");

    fun accepts(count: Int): Boolean = when (this) {
        SHORT -> count in 1..3
        ANY -> count >= 1
        LONGER_TWO -> count >= 3
        LONGER_THREE -> count >= 4
    }
    val shortPriorityMaximum get() = when (this) { SHORT -> 3; ANY -> 4; else -> 0 }
}

internal data class WordTrainingSettings(
    val targetCoverage: Int = 1,
    val prompt: WordPrompt = WordPrompt.BOTH,
    val length: WordLength = WordLength.SHORT
) { init { require(targetCoverage in 1..5) } }

internal fun normalizedCoverage(input: String): Int =
    (input.toLongOrNull() ?: if (input.isNotEmpty() && input.all(Char::isDigit)) Long.MAX_VALUE else 1)
        .coerceIn(1, 5).toInt()

internal data class WordTrainingPlan(
    val pool: List<String>,
    val known: Set<String>,
    val settings: WordTrainingSettings,
    val words: List<WordTrainingCandidate>,
    val coverage: Map<String, Int>
) {
    val uncovered get() = pool.filter { coverage.getValue(it) == 0 }
    val belowTarget get() = pool.count { coverage.getValue(it) < settings.targetCoverage }
}

/** Pedagogical selection is separate from static Related Words ranking. No random tie-break. */
internal object WordTrainingSelection {
    // Reject very low usefulness rather than dragging encyclopedic phrases in solely for coverage.
    private const val MINIMUM_USEFULNESS = -20.0

    internal fun tier(word: WordTrainingCandidate, pool: Set<String>, known: Set<String>, length: WordLength): Int {
        if (word.kanjiOccurrences.size > length.shortPriorityMaximum) return 4
        val targets = word.kanji.intersect(pool)
        return when {
            word.kanji.all { it in pool } -> 1
            targets.size >= 2 && word.kanji.all { it in pool || it in known } -> 2
            targets.size >= 2 -> 3
            else -> 4
        }
    }

    fun build(
        candidates: List<WordTrainingCandidate>, selectedPool: List<String>, known: Set<String>,
        settings: WordTrainingSettings
    ): WordTrainingPlan {
        val pool = selectedPool.distinct()
        require(pool.size in 1..50)
        val poolSet = pool.toSet()
        val scores = candidates.associateWith { WordQuality.score(it.features, poolSet) }
        val eligible = candidates.filter { candidate ->
            candidate.kanji.any { it in poolSet } && settings.length.accepts(candidate.kanjiOccurrences.size) &&
                CounterConstructions.numeralKanji(candidate.word.written).all { it in poolSet } &&
                candidate.word.reading.isNotBlank() &&
                (settings.prompt == WordPrompt.READING || !candidate.primaryMeaning.isNullOrBlank()) &&
                scores.getValue(candidate) >= MINIMUM_USEFULNESS
        }
        val coverage = pool.associateWith { 0 }.toMutableMap()
        fun pedagogicalOrder(): Comparator<WordTrainingCandidate> {
            fun needed(word: WordTrainingCandidate) = word.kanji.intersect(poolSet)
                .filter { coverage.getValue(it) < settings.targetCoverage }
            fun minimum(word: WordTrainingCandidate) = needed(word).minOfOrNull { coverage.getValue(it) } ?: Int.MAX_VALUE
            return compareBy<WordTrainingCandidate> { tier(it, poolSet, known, settings.length) }
                // Unintroduced targets outrank targets already encountered, even at coverage >1.
                .thenBy { minimum(it) }
                .thenByDescending { word -> needed(word).count { coverage.getValue(it) == minimum(word) } }
                .thenByDescending { needed(it).size }
                .thenBy { it.kanji.count { char -> char !in poolSet && char !in known } }
                .thenByDescending { scores.getValue(it) }
                .thenComparator { a, b -> WordQuality.tieBreak.compare(a.features, b.features) }
        }
        val initiallySorted = eligible.sortedWith(pedagogicalOrder())
        val byWord = initiallySorted.associateBy { it.word }
        val equivalent = initiallySorted.map { it.word }.distinctBy { it.entryId to it.written }
            .deduplicateCommonWords().map { byWord.getValue(it) }
        val families = mutableSetOf<String>()
        val remaining = equivalent.distinctBy { it.word.written }.filter {
            val family = CounterConstructions.family(it.word.written)
            family == null || families.add(family)
        }.toMutableList()
        val chosen = mutableListOf<WordTrainingCandidate>()
        while (true) {
            val useful = remaining.filter { word -> word.kanji.any {
                it in poolSet && coverage.getValue(it) < settings.targetCoverage
            } }
            val best = useful.minWithOrNull(pedagogicalOrder()) ?: break
            chosen += best
            remaining.remove(best)
            best.kanji.intersect(poolSet).forEach { coverage[it] = coverage.getValue(it) + 1 }
        }
        return WordTrainingPlan(pool, known.toSet(), settings, chosen, coverage.toMap())
    }
}
