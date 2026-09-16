package com.example.kanjidb.ui.lists

import androidx.compose.foundation.layout.*
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
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.user.UserKanjiStateDao
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.CancellationException
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
    var collectionLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(rows) {
        rows?.let { state.updateCollections(it); collectionLoaded = true }
    }
    val groupsState = rememberSaveable(saver = KanjiCollectionState.Saver) { KanjiCollectionState() }
    val myWriter = rememberKanjiStateWriter(userDao, state.collection)
    val groupsWriter = rememberKanjiStateWriter(userDao, groupsState)
    val saving = myWriter.saving || groupsWriter.saving
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
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
        HorizontalPager(state = pager, userScrollEnabled = !saving, beyondViewportPageCount = 2,
            modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            when (index) {
                0 -> MyKanjiPage(state, dictionary, myWriter, collectionLoaded,
                    activePage = pager.currentPage == 0, onOpenDetails = onOpenDetails)
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.my_lists_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                2 -> KanjiGroupsPage(dictionary, rows, groupsState, groupsWriter,
                    activePage = pager.currentPage == 2, onOpenDetails = onOpenDetails)
            }
        }
    }
}

@Composable
private fun MyKanjiPage(
    state: MyKanjiState, dictionary: DictionaryDatabase, writer: KanjiStateWriter,
    collectionLoaded: Boolean, activePage: Boolean, onOpenDetails: (String) -> Unit
) {
    val readings = remember { mutableStateMapOf<String, String?>() }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(dictionary, retry, state.learning, state.known) {
        loading = true
        failed = false
        try {
            val missing = (state.learning + state.known).filterNot { it in readings }
            readings.putAll(dictionary.getCardReadings(missing))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("MyKanji", "Cannot load dictionary readings", error)
            failed = true
        } finally {
            loading = false
        }
    }
    val pending = stringResource(R.string.my_kanji_reading_pending)
    val sections = listOf(LearningState.LEARNING, LearningState.KNOWN).map { section ->
        KanjiSection(section.name, stringResource(if (section == LearningState.LEARNING)
            R.string.details_learning else R.string.details_known),
            state.kanji(section).map { KanjiCardItem(it, if (it in readings) readings[it] else pending) })
    }
    val learning = state.selectionSection == LearningState.LEARNING
    KanjiCollectionGrid(
        sections = sections, state = state.collection, onOpenDetails = onOpenDetails,
        actions = listOf(
            KanjiSelectionAction(if (learning) R.string.my_kanji_remove_learning else R.string.my_kanji_remove_known,
                { writer.assign(it, LearningState.NONE) }, 1.4f),
            KanjiSelectionAction(if (learning) R.string.my_kanji_move_known else R.string.my_kanji_move_learning,
                { writer.assign(it, if (learning) LearningState.KNOWN else LearningState.LEARNING) }, 1.3f)
        ),
        modifier = Modifier.fillMaxSize(), activePage = activePage, isolateSection = true,
        ready = collectionLoaded, loading = loading || !collectionLoaded, busy = writer.saving,
        error = when {
            writer.failed -> stringResource(R.string.kanji_state_save_error)
            failed -> stringResource(R.string.search_error)
            else -> null
        },
        onRetry = if (failed) ({ retry++ }) else null,
        tag = "my_kanji"
    )
}
