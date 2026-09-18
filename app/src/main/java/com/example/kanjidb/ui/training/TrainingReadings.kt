package com.example.kanjidb.ui.training

import java.text.Normalizer

/** Comparison only: original dictionary strings are always used for display. */
internal fun trainingReadingKey(reading: String): String =
    Normalizer.normalize(reading, Normalizer.Form.NFD).filterNot {
        it == '\u3099' || it == '\u309A' || it == '-' || it == '\u30FC'
    }

/** Prefer diversity only for long lists; fill remaining slots with skipped entries in source order. */
internal fun trainingReadings(readings: List<String>): List<String> {
    if (readings.size <= 3) return readings.toList()
    val keys = mutableSetOf<String>()
    val selected = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    for (reading in readings) {
        if (keys.add(trainingReadingKey(reading))) selected += reading else skipped += reading
        if (selected.size == 3) return selected
    }
    return selected + skipped.take(3 - selected.size)
}