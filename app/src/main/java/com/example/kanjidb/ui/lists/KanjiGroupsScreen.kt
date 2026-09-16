package com.example.kanjidb.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.UserKanjiStateEntity
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GroupResult(
    val source: List<KanjiGroupEntry>, val states: List<UserKanjiStateEntity>,
    val options: KanjiGroupOptions, val groups: List<KanjiGroup>
)

@Composable
internal fun KanjiGroupsPage(
    dictionary: DictionaryDatabase, rows: List<UserKanjiStateEntity>?,
    state: KanjiCollectionState, writer: KanjiStateWriter, grid: LazyGridState,
    activePage: Boolean, onOpenDetails: (String) -> Unit
) {
    var options by rememberSaveable(stateSaver = KanjiGroupOptions.Saver) { mutableStateOf(KanjiGroupOptions()) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    var entries by remember(dictionary) { mutableStateOf<List<KanjiGroupEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<GroupResult?>(null) }
    LaunchedEffect(dictionary, retry) {
        failed = false
        try {
            entries = dictionary.getKanjiGroups()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("KanjiGroups", "Cannot load dictionary groups", error)
            failed = true
        }
    }
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
        val title = when (groupBy) {
            KanjiGroupBy.JLPT -> if (group.level == null) stringResource(R.string.groups_no_jlpt)
                else stringResource(R.string.groups_jlpt_level, group.level)
            KanjiGroupBy.GRADE -> if (group.level == null) stringResource(R.string.groups_no_grade)
                else stringResource(R.string.groups_grade_level, group.level)
        }
        val key = "${groupBy.name}:${group.level}"
        KanjiSection(key, title, group.kanji.map { KanjiCardItem(it.character, it.reading) },
            group.frequencyGroups.map { subgroup ->
                KanjiSubgroup("$key:${subgroup.kind.name}",
                    stringResource(if (subgroup.kind == FrequencyGroup.RANKED) R.string.groups_ranked else R.string.groups_unranked),
                    subgroup.kanji.map { KanjiCardItem(it.character, it.reading) })
            })
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                ChoiceMenu(
                    label = stringResource(R.string.groups_group_by), value = options.groupBy,
                    choices = KanjiGroupBy.entries, enabled = !writer.saving,
                    title = { stringResource(if (it == KanjiGroupBy.JLPT) R.string.groups_jlpt else R.string.groups_grade) },
                    onSelect = { options = options.copy(groupBy = it) }
                )
                TextButton(onClick = { options = options.copy(reverseGroups = !options.reverseGroups) },
                    enabled = !writer.saving) {
                    Text(stringResource(if (options.reverseGroups) R.string.groups_harder_first else R.string.groups_easier_first))
                }
            }
            Column(Modifier.weight(1f)) {
                ChoiceMenu(
                    label = stringResource(R.string.groups_sort_by), value = options.sortBy,
                    choices = KanjiSortBy.entries, enabled = !writer.saving,
                    title = { stringResource(if (it == KanjiSortBy.FREQUENCY) R.string.groups_frequency else R.string.groups_strokes) },
                    onSelect = { options = options.copy(sortBy = it) }
                )
                TextButton(onClick = { options = options.copy(descending = !options.descending) },
                    enabled = !writer.saving) {
                    Text(stringResource(options.sortDirectionLabel))
                }
            }
        }
        val context = LocalContext.current
        val summary = options.rulesSummary { context.getString(it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { rulesOpen = true }, enabled = !writer.saving) {
                Text(stringResource(R.string.groups_rules))
            }
            if (summary.isNotEmpty()) Text(summary, Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        KanjiCollectionGrid(
            sections = sections, state = state, onOpenDetails = onOpenDetails,
            grid = grid, contentAvailable = result != null,
            actions = listOf(
                KanjiSelectionAction(R.string.groups_add_learning, { writer.assign(it, LearningState.LEARNING) }, 1.4f),
                KanjiSelectionAction(R.string.groups_add_known, { writer.assign(it, LearningState.KNOWN) }, 1.3f)
            ),
            modifier = Modifier.weight(1f).fillMaxWidth(), activePage = activePage && !rulesOpen,
            ready = ready, loading = !ready && !failed, busy = writer.saving,
            error = when {
                failed -> stringResource(R.string.groups_load_error)
                writer.failed -> stringResource(R.string.kanji_state_save_error)
                else -> null
            },
            onRetry = if (failed) ({ retry++ }) else null,
            emptyMessage = stringResource(R.string.groups_empty)
        )
    }
    if (rulesOpen) {
        AlertDialog(onDismissRequest = { rulesOpen = false },
            title = { Text(stringResource(R.string.groups_rules)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresenceMenu(stringResource(R.string.groups_jlpt), options.jlpt) { options = options.copy(jlpt = it) }
                    PresenceMenu(stringResource(R.string.groups_grade), options.grade) { options = options.copy(grade = it) }
                    PresenceMenu(stringResource(R.string.details_joyo), options.joyo) { options = options.copy(joyo = it) }
                    ChoiceMenu(stringResource(R.string.groups_status), options.status, KanjiStatusRule.entries,
                        title = { stringResource(when (it) {
                            KanjiStatusRule.ANY -> R.string.groups_any
                            KanjiStatusRule.KNOWN -> R.string.details_known
                            KanjiStatusRule.LEARNING -> R.string.details_learning
                            KanjiStatusRule.EITHER -> R.string.groups_either
                            KanjiStatusRule.NEITHER -> R.string.groups_neither
                        }) }, onSelect = { options = options.copy(status = it) })
                }
            },
            dismissButton = {
                TextButton(onClick = { options = options.resetRules() }, enabled = options.activeRules > 0) {
                    Text(stringResource(R.string.groups_reset_rules))
                }
            },
            confirmButton = { TextButton(onClick = { rulesOpen = false }) { Text(stringResource(R.string.groups_done)) } }
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
