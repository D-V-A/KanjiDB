package com.example.kanjidb.data.user

import com.example.kanjidb.ui.LearningState

internal fun manualOrder(rows: List<UserKanjiStateEntity>, state: LearningState) =
    rows.filter { it.state == state }.sortedWith(compareBy({ it.manualPosition }, { it.character }))

/** Existing members keep their positions; incoming members append in request order. */
internal fun assignedKanjiRows(
    rows: List<UserKanjiStateEntity>, characters: List<String>, target: LearningState
): List<UserKanjiStateEntity> {
    require(target != LearningState.NONE)
    val existing = rows.associateBy { it.character }
    var next = (rows.filter { it.state == target }.maxOfOrNull { it.manualPosition } ?: -1L) + 1L
    return characters.distinct().map { character ->
        existing[character]?.takeIf { it.state == target }
            ?: UserKanjiStateEntity(character, target, next++)
    }
}

/** Reject stale/incomplete/cross-state drops. Positions are compacted once, never per pointer frame. */
internal fun reorderedKanjiRows(
    rows: List<UserKanjiStateEntity>, state: LearningState, before: List<String>, after: List<String>
): List<UserKanjiStateEntity>? {
    if (state == LearningState.NONE) return null
    val current = manualOrder(rows, state)
    if (current.map { it.character } != before || after.size != before.size ||
        after.toSet() != before.toSet()) return null
    val byCharacter = current.associateBy { it.character }
    return after.mapIndexed { index, character -> byCharacter.getValue(character).copy(manualPosition = index.toLong()) }
}

internal fun moveKanji(order: List<String>, character: String, target: String): List<String> {
    val from = order.indexOf(character)
    val to = order.indexOf(target)
    if (from < 0 || to < 0 || from == to) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
