package com.example.kanjidb.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.ui.FloatingActionPanel
import kotlinx.coroutines.flow.first

internal data class KanjiCardItem(val character: String, val reading: String?)
internal data class KanjiSubgroup(val key: String, val title: String, val cards: List<KanjiCardItem>)
internal data class KanjiSection(
    val key: String, val title: String, val cards: List<KanjiCardItem>,
    val subgroups: List<KanjiSubgroup> = emptyList()
)

/** A null handler is a disabled action, including the future Training entry point.
 * Every handler receives exactly the current visible selection, never the whole group. */
internal data class KanjiSelectionAction(
    val label: Int,
    val onSelected: ((List<String>) -> Unit)? = null,
    val weight: Float = 1f
)

@Composable
internal fun KanjiCollectionGrid(
    sections: List<KanjiSection>,
    state: KanjiCollectionState,
    actions: List<KanjiSelectionAction>,
    onOpenDetails: (String) -> Unit,
    grid: LazyGridState,
    contentAvailable: Boolean,
    modifier: Modifier = Modifier,
    activePage: Boolean = true,
    isolateSection: Boolean = false,
    ready: Boolean = true,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    busy: Boolean = false,
    emptyMessage: String? = null,
    tag: String = "kanji_groups"
) {
    val selecting = state.selecting
    val shown = remember(sections, isolateSection, state.section) {
        if (isolateSection && selecting) sections.filter { it.key == state.section } else sections
    }
    val eligible = remember(shown) { shown.flatMap { it.cards }.mapTo(mutableSetOf()) { it.character } }
    LaunchedEffect(eligible, ready) { if (ready) state.retain(eligible) }
    val selected = state.selected.intersect(eligible)
    val interactionEnabled = ready && !busy
    BackHandler(enabled = activePage && selecting) { if (!busy) state.cancel() }
    var panelHeight by remember { mutableIntStateOf(0) }
    var panelTop by remember { mutableStateOf<Float?>(null) }
    var gridTop by remember { mutableStateOf(0f) }
    val padding = if (selecting) with(LocalDensity.current) { panelHeight.toDp() } + 16.dp else 16.dp
    val expanded = shown.filter { section ->
        section.key in state.expandedKeys || (selecting &&
            (section.key == state.section || section.cards.any { it.character in selected }))
    }.mapTo(mutableSetOf()) { it.key }
    val footer = loading || error != null || (ready && shown.isEmpty() && emptyMessage != null)
    val itemKeys = buildList {
        shown.forEach { section ->
            add("header:${section.key}")
            if (section.key in expanded) {
                if (section.subgroups.isEmpty()) addAll(section.cards.map { it.character })
                else section.subgroups.forEach { subgroup ->
                    add("subheader:${subgroup.key}")
                    if (subgroup.key !in state.collapsedSubgroups) addAll(subgroup.cards.map { it.character })
                }
            }
        }
        if (footer) add("status")
    }
    val currentKeys by rememberUpdatedState(itemKeys)
    val anchor = state.revealCharacter
    // Shared for both pages: measure the panel and reveal only the latest selected card as needed.
    LaunchedEffect(anchor, selecting, activePage, ready) {
        if (!selecting) {
            panelHeight = 0
            panelTop = null
            return@LaunchedEffect
        }
        if (!activePage || !ready || anchor == null) return@LaunchedEffect
        snapshotFlow {
            panelTop != null && panelHeight > 0 && grid.layoutInfo.afterContentPadding >= panelHeight &&
                grid.layoutInfo.totalItemsCount == currentKeys.size
        }.first { it }
        // A header gesture can cancel a pending reveal while layout/panel measurement is awaited.
        if (state.revealCharacter != anchor) return@LaunchedEffect
        val index = currentKeys.indexOf(anchor)
        if (index < 0) return@LaunchedEffect
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == anchor }) grid.scrollToItem(index)
        val card = snapshotFlow { grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchor } }
            .first { it != null } ?: return@LaunchedEffect
        val layout = grid.layoutInfo
        val header = layout.visibleItemsInfo.firstOrNull {
            (it.key as? String)?.startsWith("header:") == true && it.index <= card.index
        }
        val top = maxOf(layout.viewportStartOffset, header?.let { it.offset.y + it.size.height }
            ?: layout.viewportStartOffset).toFloat()
        val bottom = minOf(layout.viewportEndOffset.toFloat(), (panelTop ?: return@LaunchedEffect) - gridTop)
        val overflow = when {
            card.offset.y + card.size.height > bottom -> card.offset.y + card.size.height - bottom
            card.offset.y < top -> card.offset.y - top
            else -> 0f
        }
        if (overflow != 0f && state.revealCharacter == anchor) grid.animateScrollBy(overflow)
    }

    Box(modifier.testTag("${tag}_content").pointerInput(selecting, busy) {
        if (selecting && !busy) detectTapGestures(onLongPress = { state.cancel() })
    }) {
        // Never measure a restored grid against temporary loading rows: a nonempty placeholder
        // layout would clamp its saved index/offset before the real dictionary/Room data arrives.
        if (!contentAvailable) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(error ?: stringResource(R.string.search_loading))
                if (error != null && onRetry != null) TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.search_retry))
                }
            }
            return@Box
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp), state = grid,
            modifier = Modifier.fillMaxSize().testTag("${tag}_grid")
                .onGloballyPositioned { gridTop = it.positionInRoot().y },
            contentPadding = PaddingValues(bottom = padding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shown.forEach { section ->
                stickyHeader(key = "header:${section.key}") {
                    KanjiSectionHeader(
                        title = section.title, count = section.cards.size,
                        expanded = section.key in expanded, enabled = interactionEnabled,
                        onClick = { state.toggleExpanded(section.key) },
                        onLongClick = { state.selectAll(section.key, section.cards.map { it.character }) }
                    )
                }
                if (section.key in expanded) {
                    fun LazyGridScope.kanjiCards(cards: List<KanjiCardItem>) {
                        items(cards, key = { it.character }, contentType = { "kanji" }) { card ->
                            KanjiCard(
                                character = card.character,
                                reading = card.reading ?: stringResource(R.string.my_kanji_no_reading),
                                selecting = selecting, selected = card.character in selected,
                                enabled = interactionEnabled,
                                onClick = {
                                    if (selecting) state.toggle(card.character) else onOpenDetails(card.character)
                                },
                                onLongClick = { state.begin(section.key, listOf(card.character)) }
                            )
                        }
                    }
                    if (section.subgroups.isEmpty()) kanjiCards(section.cards)
                    else section.subgroups.forEach { subgroup ->
                        val subgroupExpanded = subgroup.key !in state.collapsedSubgroups
                        item(key = "subheader:${subgroup.key}", span = { GridItemSpan(maxLineSpan) }) {
                            KanjiSubgroupHeader(subgroup.title, subgroup.cards.size, subgroupExpanded,
                                enabled = interactionEnabled && !selecting,
                                onClick = { state.toggleSubgroup(subgroup.key) })
                        }
                        if (subgroupExpanded) kanjiCards(subgroup.cards)
                    }
                }
            }
            if (footer) item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(error ?: if (loading) stringResource(R.string.search_loading) else emptyMessage.orEmpty())
                    if (error != null && onRetry != null) TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.search_retry))
                    }
                }
            }
        }
        if (selecting) {
            KanjiSelectionPanel(
                actions = actions, selected = selected.toList(), enabled = interactionEnabled,
                onCancel = { state.cancel() }, cancelEnabled = !busy,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { panelHeight = it.height }.padding(12.dp)
                    .testTag("${tag}_panel").onGloballyPositioned { panelTop = it.positionInRoot().y }
            )
        }
    }
}

@Composable
internal fun KanjiSectionHeader(
    title: String, count: Int, expanded: Boolean, enabled: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val expansion = stringResource(if (expanded) R.string.my_kanji_expanded else R.string.my_kanji_collapsed)
    Card(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .combinedClickable(enabled = enabled, role = Role.Button,
            onClick = onClick, onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.kanji_select_section))
        .semantics { stateDescription = expansion }) {
        Text(stringResource(R.string.my_kanji_section_count, title, count),
            Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun KanjiSubgroupHeader(title: String, count: Int, expanded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val expansion = stringResource(if (expanded) R.string.my_kanji_expanded else R.string.my_kanji_collapsed)
    Card(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp).semantics { stateDescription = expansion }) {
        Text(stringResource(R.string.my_kanji_section_count, title, count), Modifier.padding(12.dp),
            style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun KanjiSelectionPanel(
    actions: List<KanjiSelectionAction>, selected: List<String>, enabled: Boolean,
    onCancel: () -> Unit, cancelEnabled: Boolean, modifier: Modifier
) {
    FloatingActionPanel(modifier, contentPadding = PaddingValues(8.dp), spacing = 4.dp) {
        // Training is represented by the same action model, but has no handler yet.
        (actions + KanjiSelectionAction(R.string.training_title)).forEach { action ->
            OutlinedButton(
                onClick = { action.onSelected?.invoke(selected) },
                enabled = enabled && selected.isNotEmpty() && action.onSelected != null,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.weight(action.weight)
            ) { Text(stringResource(action.label), style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center) }
        }
        OutlinedButton(onClick = onCancel, enabled = cancelEnabled,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            modifier = Modifier.weight(0.8f)) {
            Text(stringResource(R.string.my_kanji_cancel), style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun KanjiCard(
    character: String,
    reading: String,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.my_kanji_select),
                role = if (selecting) Role.Checkbox else Role.Button
            )
            .semantics { this.selected = selected },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Text selection would conflict with the card's long-press multi-selection gesture.
            Text(character, style = MaterialTheme.typography.headlineLarge)
            Text(
                reading,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

