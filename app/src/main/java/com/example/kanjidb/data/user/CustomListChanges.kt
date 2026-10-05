package com.example.kanjidb.data.user

/** Display names are never normalized; normalization is exclusively for duplicate comparison. */
fun customListNameError(name: String, existing: List<CustomListEntity>, exceptId: Long? = null): String? {
    val length = name.codePointCount(0, name.length)
    return when {
        name.isBlank() -> "Enter a name that isn't only whitespace."
        length < 2 -> "Use at least 2 characters."
        length > 32 -> "Use no more than 32 characters."
        existing.any { it.id != exceptId && it.name.trim().equals(name.trim(), ignoreCase = true) } ->
            "A list with this name already exists."
        else -> null
    }
}

enum class ListMembershipState {
    UNCHECKED, PARTIAL, CHECKED;
    fun tapped() = if (this == CHECKED) UNCHECKED else CHECKED
}

fun membershipState(members: Collection<String>, selected: Collection<String>): ListMembershipState {
    val count = selected.distinct().count { it in members }
    return when {
        count == 0 -> ListMembershipState.UNCHECKED
        count == selected.distinct().size -> ListMembershipState.CHECKED
        else -> ListMembershipState.PARTIAL
    }
}

/** Null target means preserve the original partial/all/none state, including concurrent changes. */
data class CustomListTarget(val id: Long?, val name: String, val target: Boolean? = null, val kanjiCount: Int = 0)
class CustomListDraft(lists: List<CustomListWithKanji>, val selected: List<String>) {
    var targets: List<CustomListTarget> = lists.map { CustomListTarget(it.list.id, it.list.name, kanjiCount = it.memberships.size) }
        private set
    private var initial = lists.associate { it.list.id to membershipState(it.characters, selected) }
    fun state(index: Int): ListMembershipState {
        val target = targets[index]
        return target.target?.let { if (it) ListMembershipState.CHECKED else ListMembershipState.UNCHECKED }
            ?: initial.getValue(requireNotNull(target.id))
    }
    fun tap(index: Int) {
        val checked = state(index).tapped() == ListMembershipState.CHECKED
        targets = targets.mapIndexed { i, target ->
            if (i == index) target.copy(target = checked,
                kanjiCount = if (target.id == null) { if (checked) selected.distinct().size else 0 } else target.kanjiCount)
            else target
        }
    }
    fun save(): List<String> = listOf("v2", selected.size.toString()) + selected +
        listOf(targets.size.toString()) + targets.flatMap {
            listOf(it.id?.toString().orEmpty(), it.name, it.target?.toString().orEmpty(),
                (it.id?.let { id -> initial[id] } ?: ListMembershipState.UNCHECKED).name, it.kanjiCount.toString())
        }

    companion object {
        fun restore(saved: List<String>): CustomListDraft {
            val counted = saved.first() == "v2"
            val values = if (counted) saved.drop(1) else saved
            val selectedCount = values[0].toInt()
            val selected = values.subList(1, 1 + selectedCount)
            val targetCount = values[1 + selectedCount].toInt()
            val rows = values.drop(2 + selectedCount).chunked(if (counted) 5 else 4).take(targetCount)
            return CustomListDraft(emptyList(), selected).apply {
                targets = rows.map { CustomListTarget(it[0].toLongOrNull(), it[1],
                    it[2].takeIf(String::isNotEmpty)?.toBooleanStrict(), if (counted) it[4].toInt() else 0) }
                initial = rows.filter { it[0].isNotEmpty() }.associate {
                    it[0].toLong() to ListMembershipState.valueOf(it[3])
                }
            }
        }
    }

    fun create(name: String): String? {
        val error = customListNameError(name, targets.mapIndexed { i, t -> CustomListEntity(i.toLong(), t.name, 0) })
        if (error == null) targets = listOf(CustomListTarget(null, name, true, selected.distinct().size)) + targets
        return error
    }
}

/** Training reads full memberships, independently of collection presentation options. */
fun nonemptyCustomLists(lists: List<CustomListWithKanji>): List<CustomListWithKanji> =
    lists.filter { it.memberships.isNotEmpty() }.sortedWith(
        compareByDescending<CustomListWithKanji> { it.list.manualIndex }.thenBy { it.list.id }
    )

fun customListTrainingPool(lists: List<CustomListWithKanji>, selectedId: Long?): List<String> {
    val available = nonemptyCustomLists(lists)
    return (available.firstOrNull { it.list.id == selectedId } ?: available.firstOrNull())?.characters.orEmpty()
}
