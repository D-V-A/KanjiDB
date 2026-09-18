package com.example.kanjidb.ui.training

import com.example.kanjidb.ui.LearningState
import kotlin.math.abs
import kotlin.random.Random

internal enum class TrainingMode(val title: String) { REVIEW("Review"), LEARNING("Learning"), NEW("New") }
internal enum class TrainingResult { CORRECT, INCORRECT }
internal enum class PracticeKind(val title: String) { ALL("All"), CURRENT("Current iteration"), MISTAKES("Mistakes") }
internal data class PracticeOption(val kind: PracticeKind, val characters: List<String>)

internal fun sessionSizes(available: Int): List<Int> {
    val maximum = available.coerceIn(0, 50)
    if (maximum < 5) return listOf(maximum)
    return ((5..maximum step 5).toList() + maximum).distinct()
}

/** Nearest allowed value; ties resolve to the smaller size. Handles empty/overflowing input. */
internal fun normalizedSessionSize(input: String, available: Int): Int {
    val number = input.toLongOrNull() ?: if (input.isNotEmpty() && input.all(Char::isDigit)) Long.MAX_VALUE else 0
    val bounded = number.coerceIn(0, 50).toInt()
    return sessionSizes(available).minBy { abs(it - bounded) }
}

internal fun selectTrainingPool(source: List<String>, count: Int, random: Random = Random.Default): List<String> =
    source.distinct().shuffled(random).take(count.coerceIn(0, 50))

/**
 * Pure immutable session snapshots. Pool order never changes; attempt membership and shuffled
 * question order are separate from last results and explicit, uncommitted dictionary actions.
 */
@ConsistentCopyVisibility
internal data class TrainingSession private constructor(
    val mode: TrainingMode,
    val pool: List<String>,
    val attempt: List<String>,
    val questionOrder: List<String>,
    val questionIndex: Int = 0,
    val revealed: Boolean = false,
    val lastResults: Map<String, TrainingResult> = emptyMap(),
    val pending: Map<String, LearningState> = emptyMap()
) {
    val complete get() = questionIndex == questionOrder.size
    val currentCharacter get() = questionOrder.getOrNull(questionIndex)
    fun participated(character: String) = character in attempt

    fun reveal(): TrainingSession = if (complete) this else copy(revealed = true)

    fun answer(result: TrainingResult): TrainingSession {
        if (complete || !revealed) return this
        val character = requireNotNull(currentCharacter)
        return copy(
            questionIndex = questionIndex + 1, revealed = false,
            lastResults = lastResults + (character to result),
            pending = if (lastResults[character] != result) pending - character else pending
        )
    }

    fun allowedActions(character: String): List<LearningState> = when (lastResults[character]) {
        TrainingResult.CORRECT -> when (mode) {
            TrainingMode.REVIEW -> emptyList()
            TrainingMode.LEARNING -> listOf(LearningState.KNOWN)
            TrainingMode.NEW -> listOf(LearningState.LEARNING, LearningState.KNOWN)
        }
        TrainingResult.INCORRECT -> when (mode) {
            TrainingMode.LEARNING -> emptyList()
            else -> listOf(LearningState.LEARNING)
        }
        null -> emptyList()
    }

    fun toggleAction(character: String, target: LearningState): TrainingSession {
        if (!complete || target !in allowedActions(character)) return this
        return copy(pending = if (pending[character] == target) pending - character else pending + (character to target))
    }

    fun practiceOptions(): List<PracticeOption> {
        if (!complete) return emptyList()
        val options = mutableListOf(PracticeOption(PracticeKind.ALL, pool))
        if (attempt.toSet() != pool.toSet()) options += PracticeOption(PracticeKind.CURRENT, attempt)
        val mistakes = attempt.filter { lastResults[it] == TrainingResult.INCORRECT }
        // When every participant was incorrect, Mistakes duplicates All or Current iteration.
        if (mistakes.isNotEmpty() && options.none { it.characters.toSet() == mistakes.toSet() }) {
            options += PracticeOption(PracticeKind.MISTAKES, mistakes)
        }
        return options
    }

    fun practice(kind: PracticeKind, random: Random = Random.Default): TrainingSession {
        val subset = practiceOptions().first { it.kind == kind }.characters
        return copy(attempt = subset.toList(), questionOrder = subset.shuffled(random), questionIndex = 0, revealed = false)
    }

    fun finishAssignments(): Map<String, LearningState> {
        check(complete)
        return pool.mapNotNull { character ->
            pending[character]?.takeIf { it in allowedActions(character) }?.let { character to it }
        }.toMap()
    }

    companion object {
        fun start(mode: TrainingMode, pool: List<String>, random: Random = Random.Default): TrainingSession {
            require(pool.isNotEmpty() && pool.distinct().size == pool.size && pool.size <= 50)
            val fixed = pool.toList()
            return TrainingSession(mode, fixed, fixed, fixed.shuffled(random))
        }
    }
}