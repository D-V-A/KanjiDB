package com.example.kanjidb.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.*
import com.example.kanjidb.ui.FloatingActionPanel
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun MyListsPage(
    dao: CustomListDao, userDao: UserKanjiStateDao, entries: List<KanjiGroupEntry>?,
    rows: List<UserKanjiStateEntity>?, failed: Boolean, onRetry: () -> Unit,
    activePage: Boolean, currentPage: Int, onOpenDetails: (String) -> Unit, onOpenTraining: () -> Unit
) {
    val lists by remember(dao) { dao.observeLists() }.collectAsStateWithLifecycle(initialValue = null)
    val collection = rememberSaveable(saver = KanjiCollectionState.Saver) { KanjiCollectionState() }
    val writer = rememberKanjiStateWriter(userDao, collection)
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var trainingPool by rememberSaveable { mutableStateOf<List<String>?>(null) }
    trainingPool?.let { pool ->
        com.example.kanjidb.ui.training.FixedPoolTrainingEntry(pool,
            onDismiss = { trainingPool = null },
            onAccepted = { trainingPool = null; onOpenTraining() })
    }
    var menu by rememberSaveable { mutableStateOf<Long?>(null) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var before by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var after by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var options by rememberSaveable(stateSaver = KanjiGroupOptions.Saver) {
        mutableStateOf(KanjiGroupOptions(groupBy = KanjiGroupBy.NONE, sortBy = KanjiSortBy.MANUAL))
    }
    var selectEntireList by rememberSaveable { mutableStateOf(false) }
    val displayOptions = if (selectEntireList && collection.selecting) options.resetRules() else options
    LaunchedEffect(collection.selecting) { if (!collection.selecting) selectEntireList = false }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    data class Result(val lists: List<CustomListWithKanji>, val entries: List<KanjiGroupEntry>,
        val rows: List<UserKanjiStateEntity>, val settings: KanjiGroupOptions, val sections: List<KanjiSection>)
    var result by remember { mutableStateOf<Result?>(null) }
    val sections = result?.sections
    val ready = result?.let { it.lists == lists && it.entries === entries &&
        it.rows == rows && it.settings == displayOptions } == true
    LaunchedEffect(lists, entries, rows, displayOptions) {
        val current = lists ?: return@LaunchedEffect
        val dictionary = entries ?: return@LaunchedEffect
        val states = rows ?: return@LaunchedEffect
        val savedSettings = displayOptions
        val organizedSections = withContext(Dispatchers.Default) {
            val metadata = dictionary.associateBy { it.character }
            val statuses = states.associate { it.character to it.state }
            customListSections(current, metadata, statuses, savedSettings)
        }
        result = Result(current, dictionary, states, savedSettings, organizedSections)
    }
    LaunchedEffect(lists, collection.section) {
        if (lists != null && collection.section != null) {
            val active = lists.orEmpty().firstOrNull { it.list.id.toString() == collection.section }
            if (active == null) collection.cancel()
            else collection.retain(active.characters.toSet())
        }
    }
    fun write(operation: suspend () -> Unit) {
        if (saving) return
        saving = true
        saveError = null
        scope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { saveError = e.message ?: "Could not save changes. Please try again." }
            finally { saving = false }
        }
    }
    val currentLists = lists.orEmpty()
    CollectionFiltersHeader(currentPage = currentPage, activePage = activePage,
        locked = collection.selecting || reordering, enabled = !saving && !writer.saving && !rulesOpen,
        modifier = Modifier.fillMaxSize(),
        controls = { KanjiCollectionControls(options, { options = it }, rulesOpen,
            { rulesOpen = it }, saving || writer.saving, customList = true) }) {
        if (reordering) {
            CustomListReorder(currentLists, after, { after = it },
                enabled = !saving, activePage = activePage,
                onCancel = { if (!saving) reordering = false },
                onApply = { write {
                    check(dao.reorderLists(before, after)) { "Lists changed. Cancel and try again." }
                    reordering = false
                } }, error = saveError)
        } else {
            val activeList = currentLists.firstOrNull { it.list.id.toString() == collection.section }
            // Room membership is authoritative even while sort/filter recomputation is pending.
            val displaySections = retainCustomListMembership(sections.orEmpty(), currentLists)
            val canReorder = displayOptions.manualReorderAvailable &&
                displaySections.firstOrNull { it.key == collection.section }?.cards?.map { it.character }?.toSet() ==
                activeList?.characters?.toSet()
            KanjiCollectionGrid(
                sections = displaySections, state = collection, actions = listOf(
                    KanjiSelectionAction(R.string.groups_add_learning, { writer.assign(it, LearningState.LEARNING) }),
                    KanjiSelectionAction(R.string.groups_add_known, { writer.assign(it, LearningState.KNOWN) })
                ),
                onOpenDetails = onOpenDetails, onOpenTraining = onOpenTraining, grid = grid, contentAvailable = sections != null,
                modifier = Modifier.fillMaxSize(), activePage = activePage && !rulesOpen,
                isolateSection = true, busy = saving || writer.saving, ready = ready,
                loading = !ready && !failed,
                error = saveError ?: if (writer.failed) "Could not save kanji state." else if (failed) "Could not load kanji." else null,
                onRetry = if (failed) onRetry else null, customListsDao = dao, namespaceCards = true,
                leadingContent = if (!collection.selecting) ({
                    OutlinedCard(onClick = { creating = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text("Add new list", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }) else null,
                onHeaderLongClick = { section ->
                    if (collection.selecting) collection.toggleAll(section.key, section.cards.map { it.character })
                    else menu = section.key.toLong()
                },
                sectionCount = { section -> currentLists.firstOrNull { it.list.id.toString() == section.key }?.memberships?.size ?: 0 },
                sectionControls = { section ->
                    if (section.cards.isEmpty()) Text("No kanji matching filters or this list is empty.", Modifier.padding(8.dp))
                },
                onReorder = if (canReorder) ({ drop ->
                    if (saving) false else {
                        saving = true
                        saveError = null
                        try {
                            val success = dao.reorderKanji(drop.section.toLong(), drop.before, drop.after)
                            if (success) collection.finishReorder()
                            else saveError = "List changed. Please try again."
                            success
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { saveError = "Could not save order."; false }
                        finally { saving = false }
                    }
                }) else null,
                tag = "my_lists"
            )
        }
    }
    currentLists.firstOrNull { it.list.id == menu }?.let { list ->
        com.example.kanjidb.ui.ActionMenuDialog(list.list.name, onDismiss = { menu = null }) {
            OutlinedButton(onClick = { editId = list.list.id; menu = null }, modifier = Modifier.fillMaxWidth()) { Text("Rename") }
            OutlinedButton(onClick = { deleteId = list.list.id; menu = null }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            OutlinedButton(onClick = {
                before = currentLists.map { it.list.id }; after = before; reordering = true; menu = null
            }, modifier = Modifier.fillMaxWidth()) { Text("Change order") }
            OutlinedButton(onClick = { trainingPool = list.characters.toList(); menu = null },
                enabled = list.characters.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Practice list's kanji") }
            OutlinedButton(onClick = {
                // Reveal all list members through the existing shared filters/selection mechanism.
                selectEntireList = true
                collection.selectAll(list.list.id.toString(), list.characters)
                menu = null
            }, enabled = list.characters.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Select list's kanji") }
        }
    }
    if (creating) CustomListNameDialog("Create new list", onDismiss = { creating = false }, onApply = { name ->
        val error = customListNameError(name, dao.getLists().map { it.list })
        if (error == null) dao.create(name)
        error
    })
    currentLists.firstOrNull { it.list.id == editId }?.let { list ->
        CustomListNameDialog("Rename list", list.list.name, { editId = null }, { name ->
            val error = customListNameError(name, dao.getLists().map { it.list }, list.list.id)
            if (error == null) dao.rename(list.list.id, name)
            error
        })
    }
    currentLists.firstOrNull { it.list.id == deleteId }?.let { list ->
        AlertDialog(onDismissRequest = { if (!saving) deleteId = null },
            title = { Text("Delete \"" + list.list.name + "\"?") },
            text = { Text("The kanji in this list won't be deleted from your collection or other lists.") },
            dismissButton = { TextButton(enabled = !saving, onClick = { deleteId = null }) { Text("Cancel") } },
            confirmButton = { TextButton(enabled = !saving, onClick = { write { dao.delete(list.list.id); deleteId = null } }) { Text("Delete") } })
    }
}

/** Headers only; preview and saved expansion belong to different states. Only handles start a drag. */
@Composable
private fun CustomListReorder(
    lists: List<CustomListWithKanji>, order: List<Long>, onOrder: (List<Long>) -> Unit,
    enabled: Boolean, activePage: Boolean, onCancel: () -> Unit, onApply: () -> Unit, error: String?
) {
    val grid = rememberLazyListState()
    var dragged by remember { mutableStateOf<Long?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var listTop by remember { mutableFloatStateOf(0f) }
    val currentOrder by rememberUpdatedState(order)
    val currentOnOrder by rememberUpdatedState(onOrder)
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    BackHandler(enabled = activePage) { if (enabled) onCancel() }
    LaunchedEffect(activePage) { if (!activePage) dragged = null }
    LaunchedEffect(dragged) {
        if (dragged == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        var previousLayout: androidx.compose.foundation.lazy.LazyListLayoutInfo? = null
        while (dragged != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.032f)
            previous = now
            val bottom = grid.layoutInfo.viewportEndOffset.toFloat()
            val y = pointer.y - listTop
            val speed = when {
                y < edge -> -((edge - y) / edge).coerceIn(0f, 1f)
                y > bottom - edge -> ((y - bottom + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) grid.dispatchRawDelta(speed * 800 * density.density * seconds)
            val target = grid.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y <= it.offset + it.size }
            val id = dragged
            if (target != null && id != null && target.key != id && previousLayout !== grid.layoutInfo) {
                val next = currentOrder.toMutableList()
                val from = next.indexOf(id)
                val to = next.indexOf(target.key as Long)
                if (from >= 0 && to >= 0) { next.add(to, next.removeAt(from)); currentOnOrder(next); previousLayout = grid.layoutInfo }
            }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.weight(1f).onGloballyPositioned { listTop = it.positionInRoot().y },
            state = grid, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(order, key = { it }) { id ->
                val list = lists.firstOrNull { it.list.id == id }
                if (list != null) Card(Modifier.fillMaxWidth().animateItem(),
                    colors = CardDefaults.cardColors(containerColor = if (dragged == id)
                        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(list.list.name + " (" + list.memberships.size + ")", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        var handleTop by remember { mutableStateOf(Offset.Zero) }
                        Text("≡", Modifier.size(48.dp)
                            .semantics { contentDescription = "Drag to reorder " + list.list.name }
                            .onGloballyPositioned { handleTop = it.positionInRoot() }
                            .pointerInput(id, enabled, activePage) {
                                if (enabled && activePage) awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    dragged = id
                                    pointer = handleTop + down.position
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed || event.changes.count { it.pressed } > 1) break
                                            pointer += change.position - change.previousPosition
                                            change.consume()
                                        }
                                    } finally { dragged = null }
                                }
                            }.wrapContentSize(), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FloatingActionPanel(Modifier.fillMaxWidth()) {
            OutlinedButton(enabled = enabled && dragged == null, modifier = Modifier.weight(1f), onClick = onCancel) { Text("Cancel") }
            OutlinedButton(enabled = enabled && dragged == null, modifier = Modifier.weight(1f), onClick = onApply) { Text("Apply") }
        }
    }
}
