package com.example.kanjidb.data.dictionary

/** Dictionary-only projection for collection cards; never persisted in user.db. */
data class KanjiGroupEntry(
    val character: String,
    val reading: String?,
    val grade: Int?,
    val frequency: Int?,
    val strokeCount: Int?,
    val isJoyo: Boolean,
    val jlpt: Int?
)
