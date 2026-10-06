package com.example.kanjidb.ui.training

import com.example.kanjidb.data.dictionary.WordTrainingCandidate
import com.example.kanjidb.ui.LearningState
import kotlin.random.Random

internal enum class WordKanjiStatus { SUCCESS, PARTIAL, FAILURE }
internal data class WordKanjiTally(val shownCount: Int = 0, val score: Int = 0) {
    val status get() = when {
        shownCount == 0 -> null // An uncovered pool kanji must not be reported as successful.
        score == shownCount -> WordKanjiStatus.SUCCESS
        score >= 0 -> WordKanjiStatus.PARTIAL
        else -> WordKanjiStatus.FAILURE
    }
    fun answer(result: TrainingResult) = copy(shownCount = shownCount + 1,
        score = score + if (result == TrainingResult.CORRECT) 1 else -1)
}

internal fun maskedWord(word: WordTrainingCandidate, pool: Set<String>, known: Set<String>): String {
    val hidden = word.kanji.intersect(pool + known)
    return buildString {
        var offset = 0
        while (offset < word.word.written.length) {
            val point = word.word.written.codePointAt(offset)
            val character = String(Character.toChars(point))
            append(if (character in hidden) "＊" else character)
            offset += Character.charCount(point)
        }
    }
}

@ConsistentCopyVisibility
internal data class WordTrainingSession private constructor(
    val plan: WordTrainingPlan,
    val questionOrder: List<WordTrainingCandidate>,
    val questionIndex: Int = 0,
    val revealed: Boolean = false,
    val tallies: Map<String, WordKanjiTally> = emptyMap(),
    // Explicit word answers, independent of per-kanji status; Correct removes a resolved mistake.
    val incorrectWords: Set<Pair<Long, String>> = emptySet(),
    val pending: Map<String, LearningState> = emptyMap()
) {
    val complete get() = questionIndex == questionOrder.size
    val currentWord get() = questionOrder.getOrNull(questionIndex)
    val resultCharacters get() = (plan.pool + plan.words.flatMap { it.kanjiOccurrences }
        .filter { it in plan.known }).distinct()
    val repeatWords get() = plan.words.filter { it.key in incorrectWords }

    fun hidden(word: WordTrainingCandidate): Set<String> = word.kanji.intersect(plan.pool.toSet() + plan.known)
    fun participated(character: String): Boolean = questionOrder.any { character in hidden(it) }
    fun reveal(): WordTrainingSession = if (complete) this else copy(revealed = true)

    fun answer(result: TrainingResult): WordTrainingSession {
        if (complete || !revealed) return this
        val word = requireNotNull(currentWord)
        val updated = hidden(word).associateWith { (tallies[it] ?: WordKanjiTally()).answer(result) }
        return copy(questionIndex = questionIndex + 1, revealed = false,
            tallies = tallies + updated,
            incorrectWords = if (result == TrainingResult.INCORRECT) incorrectWords + word.key else incorrectWords - word.key,
            pending = pending - updated.keys)
    }

    fun repeatMistakes(random: Random = Random.Default): WordTrainingSession {
        if (!complete || repeatWords.isEmpty()) return this
        return copy(questionOrder = repeatWords.shuffled(random), questionIndex = 0, revealed = false)
    }

    fun allowedActions(character: String): List<LearningState> = when (tallies[character]?.status) {
        WordKanjiStatus.FAILURE -> listOf(LearningState.LEARNING)
        WordKanjiStatus.SUCCESS, WordKanjiStatus.PARTIAL -> listOf(LearningState.LEARNING, LearningState.KNOWN)
        null -> emptyList()
    }

    fun toggleAction(character: String, target: LearningState): WordTrainingSession {
        if (!complete || target !in allowedActions(character)) return this
        return copy(pending = if (pending[character] == target) pending - character else pending + (character to target))
    }

    fun bulkTargets(target: LearningState): List<String> {
        if (!complete) return emptyList()
        val status = when (target) {
            LearningState.LEARNING -> WordKanjiStatus.FAILURE
            LearningState.KNOWN -> WordKanjiStatus.SUCCESS
            else -> return emptyList()
        }
        return resultCharacters.filter { tallies[it]?.status == status }
    }

    fun withBulkActions(target: LearningState): WordTrainingSession {
        check(complete)
        return copy(pending = pending + bulkTargets(target).associateWith { target })
    }

    fun finishAssignments(): Map<String, LearningState> {
        check(complete)
        return resultCharacters.mapNotNull { character -> pending[character]
            ?.takeIf { it in allowedActions(character) }?.let { character to it } }.toMap()
    }

    companion object {
        fun start(plan: WordTrainingPlan, random: Random = Random.Default): WordTrainingSession {
            require(plan.words.isNotEmpty() && plan.words.map { it.word.written }.distinct().size == plan.words.size)
            return WordTrainingSession(plan, plan.words.shuffled(random))
        }
    }
}
