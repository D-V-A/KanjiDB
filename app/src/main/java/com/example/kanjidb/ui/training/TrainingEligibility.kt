package com.example.kanjidb.ui.training

/** Character identity is Unicode text; dictionary availability never changes stored user data. */
internal fun availableTrainingPool(characters: List<String>, available: Set<String>): List<String> =
    characters.distinct().filter { it in available }

internal fun missingTrainingWarning(source: List<String>, filteredPool: List<String>): String? {
    val count = (source.toSet() - filteredPool.toSet()).size
    return if (count == 0) null else
        "$count kanji ${if (count == 1) "is" else "are"} not available in the current dictionary and will be excluded from training."
}
