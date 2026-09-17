package com.example.kanjidb.data.dictionary

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

internal enum class RecommendationStrategy { JLPT, GRADE, FREQUENCY, RARE }
internal data class RecommendationKanji(
    val summary: KanjiSummary, val level: Int, val frequency: Int?, val eligible: Boolean
) { val character get() = summary.character }

internal val jlptOrder = listOf(5, 4, 3, 2, 1)

internal fun jlptProgress(rows: List<RecommendationKanji>, learned: Set<String>): Map<Int, Double> =
    jlptOrder.associateWith { level ->
        val members = rows.filter { it.level == level }
        if (members.isEmpty()) 0.0 else members.count { it.character in learned }.toDouble() / members.size
    }

internal fun openLevels(progress: Map<Int, Double>): List<Int> = buildList {
    for (level in jlptOrder) {
        if (isNotEmpty() && progress.getValue(last()) < 0.25) break
        add(level)
    }
}

internal fun effectiveWeights(progress: Map<Int, Double>): Map<Int, Double> = buildMap {
    var previous: Double? = null
    for (level in openLevels(progress)) {
        val value = progress.getValue(level)
        val weight = previous?.let { if (value <= it) value else it * 0.8 } ?: value
        put(level, weight)
        previous = weight
    }
}

/** Capped largest-remainder allocation; ties favor the easier level. Reserve tails first. */
internal fun recommendationQuotas(weights: Map<Int, Double>, capacity: Map<Int, Int>, target: Int): Map<Int, Int> {
    val levels = jlptOrder.filter { it in weights }
    val result = levels.associateWith { (capacity[it] ?: 0).let { n -> if (n <= 10) n else 0 } }.toMutableMap()
    var remaining = (target - result.values.sum()).coerceAtLeast(0)
    while (remaining > 0) {
        val available = levels.filter { result.getValue(it) < (capacity[it] ?: 0) }
        if (available.isEmpty()) break
        val sum = available.sumOf { weights.getValue(it) }
        // Explicit bootstrap (also handles exhausted positive-weight levels).
        if (sum == 0.0) {
            val level = available.first()
            val count = minOf(remaining, capacity.getValue(level) - result.getValue(level))
            result[level] = result.getValue(level) + count
            remaining -= count
            continue
        }
        val shares = available.associateWith { remaining * weights.getValue(it) / sum }
        for (level in available) {
            val count = minOf(shares.getValue(level).toInt(), capacity.getValue(level) - result.getValue(level))
            result[level] = result.getValue(level) + count
            remaining -= count
        }
        for (level in available.sortedByDescending { shares.getValue(it) % 1.0 }) {
            if (remaining > 0 && result.getValue(level) < capacity.getValue(level)) {
                result[level] = result.getValue(level) + 1
                remaining--
            }
        }
    }
    return result
}

internal fun normalizedFrequency(rank: Int?, best: Int?, worst: Int?): Double = when {
    rank == null -> 0.05
    best == null || worst == null || best == worst -> 1.0
    else -> (1.0 - 0.95 * (rank.toDouble() - best) / (worst.toDouble() - best)).coerceIn(0.05, 1.0)
}
internal fun frequencyWeight(rank: Int?, best: Int?, worst: Int?) = sqrt(normalizedFrequency(rank, best, worst))

/** Exponential race gives weighted sampling without replacement, with nonzero weight for every row. */
internal fun frequencySample(rows: List<RecommendationKanji>, count: Int, random: Random,
    frequencyRows: List<RecommendationKanji> = rows
): List<RecommendationKanji> {
    val ranks = frequencyRows.mapNotNull { it.frequency }
    val best = ranks.minOrNull()
    val worst = ranks.maxOrNull()
    return rows.distinctBy { it.character }.map {
        it to (-ln(1.0 - random.nextDouble()) / frequencyWeight(it.frequency, best, worst))
    }.sortedBy { it.second }.take(count).map { it.first }
}

/** Pure process session model. Only the small published visible list becomes Compose state. */
internal class RecommendedKanjiSession(rows: List<RecommendationKanji>, private val random: Random = Random.Default) {
    private val rows = rows.distinctBy { it.character }
    val strategy = RecommendationStrategy.JLPT
    var pool: List<RecommendationKanji> = emptyList()
        private set
    var visible: List<RecommendationKanji> = emptyList()
        private set
    var recent: List<String> = emptyList()
        private set
    var initialized = false
        private set

    fun update(learned: Set<String>) {
        if (!initialized) {
            pool = additions(learned, emptyList(), 50)
            initialized = true
            refresh()
            return
        }
        val retained = pool.filterNot { it.character in learned }
        val removed = pool.size - retained.size
        if (removed == 0) return // Move and Remove never reinsert or rebuild.
        pool = retained + additions(learned, retained, removed)
        val valid = pool.map { it.character }.toSet()
        replaceSlots(visible.filterNot { it.character in valid }.map { it.character }.toSet())
    }

    private fun additions(learned: Set<String>, retained: List<RecommendationKanji>, count: Int): List<RecommendationKanji> {
        val weights = effectiveWeights(jlptProgress(rows, learned))
        val eligible = rows.filter { it.eligible && it.character !in learned && it.level in weights }
        val groups = eligible.groupBy { it.level }
        val quotas = recommendationQuotas(weights, groups.mapValues { it.value.size }, 50)
        val existing = retained.map { it.character }.toSet()
        val available = groups.mapValues { (_, group) -> group.filterNot { it.character in existing }.toMutableList() }
        val counts = retained.groupingBy { it.level }.eachCount().toMutableMap()
        val result = mutableListOf<RecommendationKanji>()
        // Tails take priority; replacements preserve all retained members even if quotas changed.
        val tails = eligible.filter { groups.getValue(it.level).size <= 10 && it.character !in existing }
        for (row in tails.take(count)) {
            result.add(row)
            available.getValue(row.level).remove(row)
            counts[row.level] = (counts[row.level] ?: 0) + 1
        }
        while (result.size < count) {
            val levels = jlptOrder.filter { !available[it].isNullOrEmpty() }
            if (levels.isEmpty()) break
            val level = levels.maxBy { (quotas[it] ?: 0) - (counts[it] ?: 0) }
            // Normalize against the whole currently eligible level, not a shrinking sample.
            val group = groups.getValue(level)
            val next = frequencySample(available.getValue(level), 1, random, group).single()
            result.add(next)
            available.getValue(level).remove(next)
            counts[level] = (counts[level] ?: 0) + 1
        }
        return result
    }

    private fun select(count: Int, excluded: Set<String>): List<RecommendationKanji> {
        val available = pool.filterNot { it.character in excluded }
        val fresh = available.filterNot { it.character in recent }.shuffled(random).take(count)
        val selected = fresh.map { it.character }.toSet()
        return fresh + available.filterNot { it.character in selected }.shuffled(random).take(count - fresh.size)
    }
    private fun record(characters: List<String>) { recent = (recent + characters).takeLast(10) }

    fun refresh() {
        visible = select(5, emptySet())
        record(visible.map { it.character })
    }

    fun returnedFromDetails(character: String) { replaceSlots(setOf(character)) }

    private fun replaceSlots(characters: Set<String>) {
        val outgoing = visible.filter { it.character in characters }
        if (outgoing.isEmpty()) return // Room already replaced the opened slot; never replace it twice.
        record(outgoing.map { it.character })
        val replacements = select(outgoing.size, visible.map { it.character }.toSet()).iterator()
        val incoming = mutableListOf<String>()
        visible = visible.mapNotNull {
            if (it.character !in characters) it
            else if (replacements.hasNext()) replacements.next().also { row -> incoming.add(row.character) }
            else pool.firstOrNull { row -> row.character == it.character } // Tiny pool: keep eligible card.
        }
        record(incoming)
    }
}
