package com.example.kanjidb.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.FloatingActionPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Composable
internal fun MyKanjiScreen(
    onOpenDetails: (String) -> Unit,
    state: MyKanjiMockState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val readings = remember { mutableStateMapOf<String, String?>() }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(dictionary, retry) {
        loading = true
        failed = false
        try {
            for (character in (state.learning + state.known).distinct()) {
                if (character in readings) continue
                val kanji = dictionary.getKanji(character)
                readings[character] = kanji?.kunReadings?.firstOrNull()
                    ?: kanji?.onReadings?.firstOrNull()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("MyKanji", "Cannot load dictionary readings", error)
            failed = true
        } finally {
            loading = false
        }
    }

    val selecting = state.selectionSection != LearningState.NONE
    BackHandler(enabled = selecting) { state.cancelSelection() }
    val gridState = rememberLazyGridState()
    var panelHeight by remember { mutableIntStateOf(0) }
    var panelTop by remember { mutableStateOf<Float?>(null) }
    var gridTop by remember { mutableStateOf(0f) }
    val bottomPadding = if (selecting) {
        with(LocalDensity.current) { panelHeight.toDp() } + 16.dp
    } else 16.dp

    // Run only on entry, never on subsequent multi-selection taps.
    LaunchedEffect(state.selectionSection) {
        if (!selecting) {
            panelHeight = 0
            panelTop = null
            return@LaunchedEffect
        }
        val character = state.selected.firstOrNull() ?: return@LaunchedEffect
        snapshotFlow {
            val layout = gridState.layoutInfo
            panelTop != null && panelHeight > 0 &&
                layout.afterContentPadding >= panelHeight &&
                layout.totalItemsCount == state.kanji(state.selectionSection).size + 1 +
                    (if (loading || failed) 1 else 0)
        }.first { it }

        // Hiding the other section can change the item's index. Resolve by its stable key.
        if (gridState.layoutInfo.visibleItemsInfo.none { it.key == character }) {
            gridState.scrollToItem(state.kanji(state.selectionSection).indexOf(character) + 1)
        }
        val layout = gridState.layoutInfo
        val card = layout.visibleItemsInfo.firstOrNull { it.key == character }
            ?: return@LaunchedEffect
        val header = layout.visibleItemsInfo.firstOrNull { it.key == state.selectionSection.name }
        val visibleTop = maxOf(
            layout.viewportStartOffset,
            header?.let { it.offset.y + it.size.height } ?: layout.viewportStartOffset
        ).toFloat()
        val visibleBottom = minOf(
            layout.viewportEndOffset.toFloat(), (panelTop ?: return@LaunchedEffect) - gridTop
        )
        val overflow = when {
            card.offset.y + card.size.height > visibleBottom ->
                card.offset.y + card.size.height - visibleBottom
            card.offset.y < visibleTop -> card.offset.y - visibleTop
            else -> 0f
        }
        if (overflow != 0f) gridState.animateScrollBy(overflow)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.my_kanji_title), style = MaterialTheme.typography.headlineMedium)
        // Use the same Material tabs and defaults as Search; only one page is available.
        TabRow(selectedTabIndex = 0) {
            listOf(R.string.my_kanji_title, R.string.my_lists_title, R.string.kanji_groups_title)
                .forEachIndexed { index, title ->
                    Tab(
                        selected = index == 0,
                        onClick = {},
                        enabled = index == 0,
                        text = { Text(stringResource(title)) }
                    )
                }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("my_kanji_content")
                .pointerInput(selecting) {
                    if (selecting) {
                        // Child cards/buttons consume their gestures; only empty space cancels here.
                        detectTapGestures(onLongPress = { state.cancelSelection() })
                    }
                }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("my_kanji_grid")
                    .onGloballyPositioned { gridTop = it.positionInRoot().y },
                contentPadding = PaddingValues(bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (section in listOf(LearningState.LEARNING, LearningState.KNOWN)) {
                    if (selecting && state.selectionSection != section) continue
                    val expanded = selecting || state.expanded(section)
                    stickyHeader(key = section.name) {
                        SectionHeader(
                            section = section,
                            count = state.kanji(section).size,
                            expanded = expanded,
                            enabled = !selecting,
                            onClick = { state.toggleExpanded(section) }
                        )
                    }
                    if (expanded) {
                        items(state.kanji(section), key = { it }) { character ->
                            KanjiCard(
                                character = character,
                                reading = if (character in readings) {
                                    readings[character] ?: stringResource(R.string.my_kanji_no_reading)
                                } else {
                                    stringResource(R.string.my_kanji_reading_pending)
                                },
                                selected = character in state.selected,
                                selecting = selecting,
                                onClick = {
                                    if (selecting) state.toggleSelection(character)
                                    else onOpenDetails(character)
                                },
                                onLongClick = { state.beginSelection(section, character) }
                            )
                        }
                    }
                }
                if (loading || failed) {
                    item(key = "reading_status", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(stringResource(
                                if (failed) R.string.search_error else R.string.search_loading
                            ))
                            if (failed) {
                                TextButton(onClick = { retry++ }) {
                                    Text(stringResource(R.string.search_retry))
                                }
                            }
                        }
                    }
                }
            }

            if (selecting) {
                SelectionPanel(
                    section = state.selectionSection,
                    hasSelection = state.selected.isNotEmpty(),
                    onRemove = state::removeSelected,
                    onMove = state::moveSelected,
                    onCancel = state::cancelSelection,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().onSizeChanged { panelHeight = it.height }
                        .padding(12.dp)
                        .testTag("my_kanji_panel")
                        .onGloballyPositioned { panelTop = it.positionInRoot().y }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    section: LearningState,
    count: Int,
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(
        if (section == LearningState.LEARNING) R.string.details_learning else R.string.details_known
    )
    val expansion = stringResource(
        if (expanded) R.string.my_kanji_expanded else R.string.my_kanji_collapsed
    )
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = expansion }
    ) {
        Text(
            stringResource(R.string.my_kanji_section_count, label, count),
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KanjiCard(
    character: String,
    reading: String,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
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

@Composable
private fun SelectionPanel(
    section: LearningState,
    hasSelection: Boolean,
    onRemove: () -> Unit,
    onMove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionPanel(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        spacing = 4.dp
    ) {
        val labels = listOf(
            if (section == LearningState.LEARNING) R.string.my_kanji_remove_learning
                else R.string.my_kanji_remove_known,
            if (section == LearningState.LEARNING) R.string.my_kanji_move_known
                else R.string.my_kanji_move_learning,
            R.string.my_kanji_cancel,
            R.string.training_title
        )
        val actions = listOf(onRemove, onMove, onCancel, {})
        val weights = listOf(1.4f, 1.3f, 0.8f, 1f)
        labels.forEachIndexed { index, label ->
            OutlinedButton(
                onClick = actions[index],
                enabled = when (index) {
                    0, 1 -> hasSelection
                    2 -> true
                    else -> false
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.weight(weights[index])
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
