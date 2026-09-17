package com.example.kanjidb.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var collapsedSubgroups by mutableStateOf(emptySet<String>())
        private set
    var selectionEntryId by mutableIntStateOf(0)
        private set
    val selecting: Boolean get() = section != null

    fun toggleExpanded(key: String) {
        if (selecting) return
        expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
    }

    fun toggleSubgroup(key: String) {
        if (selecting) return
        collapsedSubgroups = if (key in collapsedSubgroups) collapsedSubgroups - key else collapsedSubgroups + key
    }

    fun begin(key: String, characters: Collection<String>) {
        if (selecting || characters.isEmpty()) return
        section = key
        selectionEntryId++
        selected = characters.toSet()
        revealCharacter = characters.first()
    }

    /** Header selection preserves the viewport, including when extending a card selection. */
    fun selectAll(key: String, characters: Collection<String>) {
        // Do not enter through begin(): headers never create a card-reveal request.
        revealCharacter = null
        if (!selecting) {
            if (characters.isEmpty()) return
            section = key
            selectionEntryId++
        }
        selected = selected + characters
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

    fun clearReveal() { revealCharacter = null }

    fun finishReorder(character: String) {
        selected = selected - character
        revealCharacter = null
    }

    fun cancel() {
        selected = emptySet()
        section = null
        revealCharacter = null
    }

    // Reveal requests are transient gestures, not state to replay after Back/recreation.
    fun save(): List<String> = listOf("v2", section.orEmpty(), expandedKeys.size.toString(),
        collapsedSubgroups.size.toString()) + expandedKeys + collapsedSubgroups + selected

    companion object {
        fun restore(saved: List<String>): KanjiCollectionState = KanjiCollectionState().apply {
            if (saved[0] == "v2") {
                section = saved[1].ifEmpty { null }
                val expandedEnd = 4 + saved[2].toInt()
                val collapsedEnd = expandedEnd + saved[3].toInt()
                expandedKeys = saved.subList(4, expandedEnd).toSet()
                collapsedSubgroups = saved.subList(expandedEnd, collapsedEnd).toSet()
                selected = saved.drop(collapsedEnd).toSet()
            } else {
                // Saved state from v0.3.0: ignore its obsolete pending reveal character.
                section = saved[0].ifEmpty { null }
                val expandedEnd = 3 + saved[2].toInt()
                expandedKeys = saved.subList(3, expandedEnd).toSet()
                selected = saved.drop(expandedEnd).toSet()
            }
        }
        val Saver = listSaver<KanjiCollectionState, String>(save = { it.save() }, restore = { restore(it) })
    }
}
