package com.example.kanjidb.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.kanjidb.R
import com.example.kanjidb.ui.DictionaryText
import com.example.kanjidb.ui.standaloneKanjiMeaning
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryWord
import com.example.kanjidb.data.dictionary.WordSearchPage
import com.example.kanjidb.data.dictionary.KanjiSearchPage
import com.example.kanjidb.data.dictionary.KanjiSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(onOpenDetails: (String) -> Unit, onOpenWord: (Long, String) -> Unit, onOpenAbout: () -> Unit, onOpenRecommended: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    var wordMode by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()
    var limit by remember(query, wordMode) { mutableIntStateOf(10) }
    var page by remember(query, wordMode) { mutableStateOf(KanjiSearchPage(emptyList(), false)) }
    var wordPage by remember(query, wordMode) { mutableStateOf(WordSearchPage(emptyList(), false)) }
    var searchLoading by remember(query, wordMode) { mutableStateOf(true) }
    var searchFailed by remember(query, wordMode) { mutableStateOf(false) }
    var searchRetry by remember(query, wordMode) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val exploring = query.isBlank()

    LaunchedEffect(dictionary) { ExploreState.initialize(dictionary) }
    LaunchedEffect(query, wordMode) { listState.scrollToItem(0) }
    LaunchedEffect(dictionary, query, wordMode, limit, searchRetry) {
        if (exploring) return@LaunchedEffect
        searchLoading = true
        searchFailed = false
        try {
            delay(250)
            if (wordMode) wordPage = dictionary.searchWords(query, limit)
            else page = dictionary.searchKanji(query, limit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("Search", "Cannot search dictionary", error)
            searchFailed = true
        } finally {
            searchLoading = false
        }
    }
    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.search_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOpenAbout) {
                Icon(painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.about_open))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modeDescription = stringResource(
                if (wordMode) R.string.search_mode_words else R.string.search_mode_kanji)
            TextButton(onClick = { wordMode = !wordMode },
                modifier = Modifier.semantics { contentDescription = modeDescription }) {
                Text(if (wordMode) "\u8A00" else "\u5B57", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(
                    if (wordMode) R.string.search_words_hint else R.string.search_kanji_hint)) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    }
                } else null,
                singleLine = true
            )
        }
        if (exploring) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                listOf(R.string.search_explore, R.string.search_recommended_kanji,
                    R.string.search_explore_words).forEachIndexed { index, title ->
                    Tab(selected = pagerState.currentPage == index,
                        onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(title)) })
                }
            }
        } else {
            Text(stringResource(R.string.search_results), style = MaterialTheme.typography.titleMedium)
        }
        if (exploring) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                if (index == 1) {
                    SearchList(
                        exploring = true, showingWords = false,
                        rows = RecommendedState.kanji, wordRows = emptyList(),
                        loading = RecommendedState.loading, failed = RecommendedState.failed,
                        hasMore = false,
                        onRetry = { RecommendedState.initialize(dictionary,
                            com.example.kanjidb.data.user.UserDatabase.getInstance(context).kanjiStates()) },
                        onRefresh = { RecommendedState.refresh() }, onShowMore = {},
                        onOpenDetails = onOpenRecommended, onOpenWord = onOpenWord,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val words = index == 2
                    val refresh = {
                        if (words) ExploreState.newWords(dictionary) else ExploreState.newKanji(dictionary)
                    }
                    SearchList(
                        exploring = true, showingWords = words,
                        rows = ExploreState.kanji, wordRows = ExploreState.words,
                        loading = if (words) ExploreState.wordsLoading else ExploreState.kanjiLoading,
                        failed = if (words) ExploreState.wordsFailed else ExploreState.kanjiFailed,
                        hasMore = false, onRetry = refresh, onRefresh = refresh, onShowMore = {},
                        onOpenDetails = onOpenDetails, onOpenWord = onOpenWord,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            SearchList(
                exploring = false, showingWords = wordMode,
                rows = page.kanji, wordRows = wordPage.words,
                loading = searchLoading, failed = searchFailed,
                hasMore = if (wordMode) wordPage.hasMore else page.hasMore,
                onRetry = { searchRetry++ }, onRefresh = {}, onShowMore = { limit += 10 },
                onOpenDetails = onOpenDetails, onOpenWord = onOpenWord,
                modifier = Modifier.weight(1f), listState = listState
            )
        }
    }
}

@Composable
private fun SearchList(
    exploring: Boolean,
    showingWords: Boolean,
    rows: List<KanjiSummary>,
    wordRows: List<DictionaryWord>,
    loading: Boolean,
    failed: Boolean,
    hasMore: Boolean,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onShowMore: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenWord: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val empty = if (showingWords) wordRows.isEmpty() else rows.isEmpty()
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (exploring) 12.dp else 4.dp)
    ) {
        if (showingWords) {
            items(wordRows, key = { "${it.entryId}:${it.written}" }) { word ->
                val open = {
                    keyboardController?.hide()
                    onOpenWord(word.entryId, word.written)
                }
                if (exploring) {
                    Card(onClick = open, modifier = Modifier.fillMaxWidth()) { SearchWordRow(word) }
                } else {
                    SearchWordRow(word, Modifier.fillMaxWidth().clickable(onClick = open))
                }
            }
        } else {
            items(rows, key = { it.character }) { kanji ->
                val open = {
                    keyboardController?.hide()
                    onOpenDetails(kanji.character)
                }
                if (exploring) {
                    Card(onClick = open, modifier = Modifier.fillMaxWidth()) {
                        KanjiRow(kanji, compact = false)
                    }
                } else {
                    KanjiRow(
                        kanji, compact = true,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = open)
                    )
                }
            }
        }
        when {
            loading -> item { Text(stringResource(R.string.search_dictionary_loading)) }
            failed -> item {
                Column {
                    Text(stringResource(R.string.search_dictionary_error))
                    TextButton(onClick = {
                        onRetry()
                    }) { Text(stringResource(R.string.search_retry)) }
                }
            }
            empty -> item { Text(stringResource(R.string.search_dictionary_empty)) }
            !exploring && hasMore -> item {
                TextButton(onClick = onShowMore) {
                    Text(stringResource(R.string.search_show_more_results))
                }
            }
        }
        if (exploring && !failed) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(enabled = !loading, onClick = onRefresh) {
                        Text(stringResource(if (showingWords) R.string.search_new_words else R.string.search_new_kanji))
                    }
                }
            }
        }
    }
}

@Composable
private fun KanjiRow(kanji: KanjiSummary, compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DictionaryText(
            kanji.character,
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall
        )
        DictionaryText(
            kanji.primaryMeaning.standaloneKanjiMeaning(), modifier = Modifier.weight(1f),
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun SearchWordRow(word: DictionaryWord, modifier: Modifier = Modifier) {
    Row(modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DictionaryText(word.written, style = MaterialTheme.typography.titleLarge)
            DictionaryText(word.reading, style = MaterialTheme.typography.bodySmall)
        }
        DictionaryText(word.meanings.firstOrNull().orEmpty(), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
    }
}
