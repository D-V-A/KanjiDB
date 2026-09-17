package com.example.kanjidb.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.KanjiGroupEntry
import com.example.kanjidb.data.user.UserKanjiStateEntity
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.user.UserKanjiStateDao
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun MyKanjiScreen(
    onOpenDetails: (String) -> Unit,
    userDao: UserKanjiStateDao,
    state: MyKanjiState = rememberSaveable(saver = MyKanjiState.Saver) { MyKanjiState() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val rows by remember(userDao) { userDao.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(rows) {
        rows?.let { state.updateCollections(it) }
    }
    val groupsState = rememberSaveable(saver = KanjiCollectionState.Saver) { KanjiCollectionState() }
    val myWriter = rememberKanjiStateWriter(userDao, state.collection)
    val groupsWriter = rememberKanjiStateWriter(userDao, groupsState)
    val saving = myWriter.saving || groupsWriter.saving
    // Separate saveable list states owned by the destination, not ephemeral pager content.
    val myGrid = rememberLazyGridState()
    val groupsGrid = rememberLazyGridState()
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var options by rememberSaveable(stateSaver = KanjiGroupOptions.Saver) { mutableStateOf(KanjiGroupOptions()) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    var myOptions by rememberSaveable(stateSaver = KanjiGroupOptions.Saver) {
        mutableStateOf(KanjiGroupOptions(groupBy = KanjiGroupBy.NONE, sortBy = KanjiSortBy.NONE))
    }
    var myRulesOpen by rememberSaveable { mutableStateOf(false) }
    // Both pages share the same bulk metadata/readings load; user data stays in Room.
    var entries by remember(dictionary) { mutableStateOf<List<KanjiGroupEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(dictionary, retry) {
        failed = false
        try {
            entries = dictionary.getKanjiGroups()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("KanjiCollections", "Cannot load dictionary groups", error)
            failed = true
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.my_kanji_title), style = MaterialTheme.typography.headlineMedium)
        TabRow(selectedTabIndex = pager.currentPage) {
            listOf(R.string.my_kanji_title, R.string.my_lists_title, R.string.kanji_groups_title)
                .forEachIndexed { index, title ->
                    Tab(selected = pager.currentPage == index, enabled = !saving,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(stringResource(title)) })
                }
        }
        // Reset controls after a completed tab change, avoiding a jump on the outgoing page mid-swipe.
        HorizontalPager(state = pager, key = { it }, userScrollEnabled = !saving, beyondViewportPageCount = 2,
            modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            when (index) {
                0 -> CollapsingCollectionHeader(
                    currentPage = pager.settledPage,
                    scrollEnabled = pager.currentPage == 0 && !saving && !myRulesOpen,
                    modifier = Modifier.fillMaxSize(),
                    header = {
                        Box(Modifier.padding(bottom = 8.dp)) {
                            KanjiCollectionControls(myOptions, { myOptions = it }, myRulesOpen,
                                { myRulesOpen = it }, myWriter.saving, personal = true)
                        }
                    }
                ) {
                    MyKanjiPage(state, entries, rows, failed, { retry++ }, myWriter, myGrid,
                        activePage = pager.currentPage == 0 && !myRulesOpen,
                        onOpenDetails = onOpenDetails, options = myOptions)
                }
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.my_lists_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                2 -> CollapsingCollectionHeader(
                    currentPage = pager.settledPage,
                    scrollEnabled = pager.currentPage == 2 && !saving && !rulesOpen,
                    modifier = Modifier.fillMaxSize(),
                    header = {
                        Box(Modifier.padding(bottom = 8.dp)) {
                            KanjiCollectionControls(options, { options = it }, rulesOpen,
                                { rulesOpen = it }, groupsWriter.saving)
                        }
                    }
                ) {
                    KanjiGroupsPage(entries, rows, failed, { retry++ }, groupsState, groupsWriter, groupsGrid,
                        activePage = pager.currentPage == 2, onOpenDetails = onOpenDetails,
                        options = options, rulesOpen = rulesOpen)
                }
            }
        }
    }
}

private data class PersonalGroupResult(
    val source: List<KanjiGroupEntry>, val rows: List<UserKanjiStateEntity>,
    val options: KanjiGroupOptions, val sections: List<PersonalKanjiSection>
)

@Composable
private fun MyKanjiPage(
    state: MyKanjiState, entries: List<KanjiGroupEntry>?, rows: List<UserKanjiStateEntity>?,
    failed: Boolean, onRetry: () -> Unit, writer: KanjiStateWriter, grid: LazyGridState,
    activePage: Boolean, onOpenDetails: (String) -> Unit, options: KanjiGroupOptions
) {
    var result by remember { mutableStateOf<PersonalGroupResult?>(null) }
    LaunchedEffect(entries, rows, options) {
        val source = entries ?: return@LaunchedEffect
        val owned = rows ?: return@LaunchedEffect
        result = withContext(Dispatchers.Default) {
            PersonalGroupResult(source, owned, options, groupMyKanji(source, owned, options))
        }
    }
    val ready = result?.let { it.source === entries && it.rows == rows && it.options == options } == true
    val sections = personalKanjiSections(result?.sections.orEmpty(), result?.options?.groupBy ?: options.groupBy)
    val learning = state.selectionSection == LearningState.LEARNING
    KanjiCollectionGrid(
        sections = sections, state = state.collection, onOpenDetails = onOpenDetails,
        // Do not measure restored grid state until Room + metadata + organization are ready.
        grid = grid, contentAvailable = result != null,
        actions = listOf(
            KanjiSelectionAction(if (learning) R.string.my_kanji_remove_learning else R.string.my_kanji_remove_known,
                { writer.assign(it, LearningState.NONE) }, 1.4f),
            KanjiSelectionAction(if (learning) R.string.my_kanji_move_known else R.string.my_kanji_move_learning,
                { writer.assign(it, if (learning) LearningState.KNOWN else LearningState.LEARNING) }, 1.3f)
        ),
        modifier = Modifier.fillMaxSize(), activePage = activePage, isolateSection = true,
        ready = ready, loading = !ready && !failed, busy = writer.saving,
        error = when {
            writer.failed -> stringResource(R.string.kanji_state_save_error)
            failed -> stringResource(R.string.groups_load_error)
            else -> null
        },
        onRetry = if (failed) onRetry else null,
        tag = "my_kanji"
    )
}
