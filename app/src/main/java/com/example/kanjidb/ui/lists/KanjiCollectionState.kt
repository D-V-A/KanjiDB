package com.example.kanjidb.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/** Shared transient UI state. An active section is independent of selectedCount. */
internal class KanjiCollectionState {
    var section by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf(emptySet<String>())
        private set
    var revealCharacter by mutableStateOf<String?>(null)
        private set
    var expandedKeys by mutableStateOf(emptySet<String>())
        private set
    val selecting: Boolean get() = section != null

    fun toggleExpanded(key: String) {
        if (selecting) return
        expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
    }

    fun begin(key: String, characters: Collection<String>) {
        if (selecting || characters.isEmpty()) return
        section = key
        selected = characters.toSet()
        revealCharacter = characters.first()
    }

    /** Header selection preserves the viewport, including when extending a card selection. */
    fun selectAll(key: String, characters: Collection<String>) {
        if (!selecting) begin(key, characters)
        else selected = selected + characters
        // No individual card needs revealing; also cancel any pending card autoscroll.
        revealCharacter = null
    }

    fun toggle(character: String) {
        if (!selecting) return
        if (character in selected) selected = selected - character
        else {
            selected = selected + character
            revealCharacter = character
        }
    }

    fun retain(visibleCharacters: Set<String>) {
        selected = selected.intersect(visibleCharacters)
        if (revealCharacter !in visibleCharacters) revealCharacter = null
    }

    fun cancel() {
        selected = emptySet()
        section = null
        revealCharacter = null
    }

    // Only UI flags/character keys, never dictionary metadata or Room snapshots.
    fun save(): List<String> = listOf(section.orEmpty(), revealCharacter.orEmpty(), expandedKeys.size.toString()) +
        expandedKeys + selected

    companion object {
        fun restore(saved: List<String>): KanjiCollectionState = KanjiCollectionState().apply {
            section = saved[0].ifEmpty { null }
            revealCharacter = saved[1].ifEmpty { null }
            val expandedEnd = 3 + saved[2].toInt()
            expandedKeys = saved.subList(3, expandedEnd).toSet()
            selected = saved.drop(expandedEnd).toSet()
        }
        val Saver = listSaver<KanjiCollectionState, String>(save = { it.save() }, restore = { restore(it) })
    }
}
