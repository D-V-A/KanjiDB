package com.example.kanjidb.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.UserKanjiStateEntity
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GroupResult(
    val source: List<KanjiGroupEntry>, val states: List<UserKanjiStateEntity>,
    val options: KanjiGroupOptions, val groups: List<KanjiGroup>
)

@Composable
internal fun KanjiGroupsPage(
    entries: List<KanjiGroupEntry>?, rows: List<UserKanjiStateEntity>?,
    failed: Boolean, onRetry: () -> Unit,
    state: KanjiCollectionState, writer: KanjiStateWriter, grid: LazyGridState,
    activePage: Boolean, onOpenDetails: (String) -> Unit, options: KanjiGroupOptions,
    rulesOpen: Boolean
) {
    var result by remember { mutableStateOf<GroupResult?>(null) }
    LaunchedEffect(entries, rows, options) {
        val source = entries ?: return@LaunchedEffect
        val states = rows ?: return@LaunchedEffect
        val settings = options
        result = withContext(Dispatchers.Default) {
            GroupResult(source, states, settings,
                groupKanji(source, states.associate { it.character to it.state }, settings))
        }
    }
    val ready = result?.let { it.source === entries && it.states == rows && it.options == options } == true
    val groups = result?.groups.orEmpty()
    val groupBy = result?.options?.groupBy ?: options.groupBy
    val sections = groups.map { group ->
        val key = "${groupBy.name}:${group.level}"
        KanjiSection(key, kanjiGroupTitle(groupBy, group.level), group.kanji.cards(),
            frequencySubgroups(key, group))
    }
    KanjiCollectionGrid(
        sections = sections, state = state, onOpenDetails = onOpenDetails,
        grid = grid, contentAvailable = result != null,
        actions = listOf(
            KanjiSelectionAction(R.string.groups_add_learning, { writer.assign(it, LearningState.LEARNING) }, 1.4f),
            KanjiSelectionAction(R.string.groups_add_known, { writer.assign(it, LearningState.KNOWN) }, 1.3f)
        ),
        modifier = Modifier.fillMaxSize(), activePage = activePage && !rulesOpen,
        ready = ready, loading = !ready && !failed, busy = writer.saving,
        error = when {
            failed -> stringResource(R.string.groups_load_error)
            writer.failed -> stringResource(R.string.kanji_state_save_error)
            else -> null
        },
        onRetry = if (failed) onRetry else null,
        emptyMessage = stringResource(R.string.groups_empty)
    )
}

@Composable
internal fun KanjiCollectionControls(
    options: KanjiGroupOptions, onOptionsChange: (KanjiGroupOptions) -> Unit,
    rulesOpen: Boolean, onRulesOpenChange: (Boolean) -> Unit, saving: Boolean,
    personal: Boolean = false
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                ChoiceMenu(
                    label = stringResource(R.string.groups_group_by), value = options.groupBy,
                    choices = KanjiGroupBy.entries.filter { personal || it != KanjiGroupBy.NONE }, enabled = !saving,
                    title = { stringResource(when (it) {
                        KanjiGroupBy.NONE -> R.string.groups_rule_na
                        KanjiGroupBy.JLPT -> R.string.groups_jlpt
                        KanjiGroupBy.GRADE -> R.string.groups_grade
                    }) },
                    onSelect = { onOptionsChange(options.copy(groupBy = it)) }
                )
                TextButton(onClick = { onOptionsChange(options.copy(reverseGroups = !options.reverseGroups)) },
                    enabled = !saving && options.groupBy != KanjiGroupBy.NONE) {
                    Text(stringResource(if (options.reverseGroups) R.string.groups_harder_first else R.string.groups_easier_first))
                }
            }
            Column(Modifier.weight(1f)) {
                ChoiceMenu(
                    label = stringResource(R.string.groups_sort_by), value = options.sortBy,
                    choices = KanjiSortBy.entries.filter { personal || it != KanjiSortBy.MANUAL }, enabled = !saving,
                    title = { stringResource(when (it) {
                        KanjiSortBy.MANUAL -> R.string.my_kanji_sort_manually
                        KanjiSortBy.FREQUENCY -> R.string.groups_frequency
                        KanjiSortBy.STROKES -> R.string.groups_strokes
                    }) },
                    onSelect = { onOptionsChange(options.copy(sortBy = it)) }
                )
                TextButton(onClick = { onOptionsChange(options.copy(descending = !options.descending)) },
                    enabled = !saving && options.sortBy != KanjiSortBy.MANUAL) {
                    Text(stringResource(options.sortDirectionLabel))
                }
            }
        }
        val context = LocalContext.current
        val manualHint = personal && options.manualReorderAvailable
        val summary = if (manualHint) stringResource(R.string.my_kanji_order_hint)
            else options.rulesSummary { context.getString(it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRulesOpenChange(true) }, enabled = !saving) {
                Text(stringResource(R.string.groups_rules))
            }
            if (summary.isNotEmpty()) Text(summary, Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, maxLines = if (manualHint) 2 else 1,
                overflow = if (manualHint) TextOverflow.Clip else TextOverflow.Ellipsis)
        }
    }
    if (rulesOpen) {
        AlertDialog(onDismissRequest = { onRulesOpenChange(false) },
            title = { Text(stringResource(R.string.groups_rules)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresenceMenu(stringResource(R.string.groups_jlpt), options.jlpt) { onOptionsChange(options.copy(jlpt = it)) }
                    PresenceMenu(stringResource(R.string.groups_grade), options.grade) { onOptionsChange(options.copy(grade = it)) }
                    PresenceMenu(stringResource(R.string.details_joyo), options.joyo) { onOptionsChange(options.copy(joyo = it)) }
                    if (!personal) ChoiceMenu(stringResource(R.string.groups_status), options.status, KanjiStatusRule.entries,
                        title = { stringResource(when (it) {
                            KanjiStatusRule.ANY -> R.string.groups_any
                            KanjiStatusRule.KNOWN -> R.string.details_known
                            KanjiStatusRule.LEARNING -> R.string.details_learning
                            KanjiStatusRule.EITHER -> R.string.groups_either
                            KanjiStatusRule.NEITHER -> R.string.groups_neither
                        }) }, onSelect = { onOptionsChange(options.copy(status = it)) })
                }
            },
            dismissButton = {
                TextButton(onClick = { onOptionsChange(options.resetRules()) }, enabled = options.activeRules > 0) {
                    Text(stringResource(R.string.groups_reset_rules))
                }
            },
            confirmButton = { TextButton(onClick = { onRulesOpenChange(false) }) { Text(stringResource(R.string.groups_done)) } }
        )
    }
}

@Composable
private fun PresenceMenu(label: String, value: PresenceRule, onSelect: (PresenceRule) -> Unit) {
    ChoiceMenu(label, value, PresenceRule.entries, title = {
        stringResource(when (it) {
            PresenceRule.ANY -> R.string.groups_rule_na
            PresenceRule.ONLY -> R.string.groups_rule_only
            PresenceRule.NOT -> R.string.groups_rule_not
        })
    }, onSelect = onSelect)
}

@Composable
private fun <T> ChoiceMenu(
    label: String, value: T, choices: List<T>, enabled: Boolean = true,
    title: @Composable (T) -> String, onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.groups_control_value, label, title(value)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(title(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}
