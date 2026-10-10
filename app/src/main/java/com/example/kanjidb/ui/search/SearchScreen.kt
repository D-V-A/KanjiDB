package com.example.kanjidb.ui.search

import com.example.kanjidb.ui.DictionaryModeControl
import com.example.kanjidb.ui.AppIcons
import com.example.kanjidb.ui.IconLabel
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.kanjidb.R
import com.example.kanjidb.ui.DictionaryText
import com.example.kanjidb.ui.standaloneKanjiMeaning
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryWord
import com.example.kanjidb.data.dictionary.WordSearchResult
import com.example.kanjidb.data.dictionary.KanjiSummary
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(state: SearchState, onOpenDetails: (String) -> Unit, onOpenWord: (Long, String) -> Unit, onOpenAbout: () -> Unit, onOpenRecommended: (String) -> Unit, modifier: Modifier = Modifier) {
    val query = state.query
    val wordMode = state.wordMode
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()
    val exploring = query.isBlank()
    val searchInteractionSource = remember { MutableInteractionSource() }
    val searchFocused by searchInteractionSource.collectIsFocusedAsState()
    val helpPreferences = remember(context) { SearchHelpPreferences(context) }
    var autoHelp by remember { mutableStateOf(helpPreferences.autoShow) }
    var helpVisible by remember { mutableStateOf(false) }
    LaunchedEffect(searchFocused, query) {
        helpVisible = searchFocused && query.isEmpty() && autoHelp
    }

    LaunchedEffect(dictionary) { ExploreState.initialize(dictionary) }
    LaunchedEffect(dictionary, state, query, wordMode, state.limit, state.retry) {
        state.search(dictionary::searchKanji, dictionary::searchWords)
    }
    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.search_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOpenAbout) {
                Icon(AppIcons.InfoSquareRounded,
                    contentDescription = stringResource(R.string.about_open))
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val searchColors = OutlinedTextFieldDefaults.colors()
                DictionaryModeControl(wordMode = wordMode,
                    arrowColor = if (searchFocused) searchColors.focusedIndicatorColor
                        else searchColors.unfocusedIndicatorColor,
                    onSwitch = {
                        state.switchMode()
                        state.wordMode
                    })
                OutlinedTextField(
                    interactionSource = searchInteractionSource,
                    colors = searchColors,
                    value = query,
                    onValueChange = { helpVisible = false; state.changeQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(
                        if (wordMode) R.string.search_words_hint else R.string.search_kanji_hint)) },
                    trailingIcon = {
                        if (query.isEmpty()) {
                            IconButton(onClick = { helpVisible = !helpVisible }) {
                                Icon(AppIcons.TextQuestion, contentDescription = stringResource(R.string.search_help))
                            }
                        } else {
                            IconButton(onClick = { state.changeQuery("") }) {
                                Icon(AppIcons.Backspace, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    singleLine = true
                )
            }
            if (helpVisible) SearchHelpPopup(
                wordMode = wordMode,
                showDisableAutoShow = autoHelp,
                onDismiss = { helpVisible = false },
                onDisableAutoShow = {
                    helpPreferences.disableAutoShow()
                    autoHelp = false
                    helpVisible = false
                }
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
                rows = state.kanjiPage.kanji, wordRows = emptyList(), searchWordRows = state.wordPage.words,
                loading = state.loading, failed = state.failed,
                hasMore = if (wordMode) state.wordPage.hasMore else state.kanjiPage.hasMore,
                onRetry = state::retrySearch, onRefresh = {}, onShowMore = state::showMore,
                onOpenDetails = onOpenDetails, onOpenWord = onOpenWord,
                modifier = Modifier.weight(1f), listState = state.listState
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
    searchWordRows: List<WordSearchResult> = emptyList(),
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
    val groups = remember(searchWordRows) { groupWordSearchResults(searchWordRows) }
    val empty = if (showingWords) (if (exploring) wordRows.isEmpty() else searchWordRows.isEmpty()) else rows.isEmpty()
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (exploring) 12.dp else 4.dp)
    ) {
        if (showingWords && !exploring) {
            items(groups, key = { group -> group.minOf { "${it.word.entryId}:${it.word.written}" } }) { group ->
                if (group.size > 1) {
                    GroupedSearchWordRow(group) { word ->
                        keyboardController?.hide()
                        onOpenWord(word.entryId, word.written)
                    }
                } else {
                    val result = group.single()
                    SearchWordRow(result.word, Modifier.fillMaxWidth().clickable {
                        keyboardController?.hide()
                        onOpenWord(result.word.entryId, result.word.written)
                    }, meaning = result.displayMeaning.standaloneKanjiMeaning())
                }
            }
        } else if (showingWords) {
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
                        IconLabel(AppIcons.Repeat) {
                            Text(stringResource(if (showingWords) R.string.search_new_words else R.string.search_new_kanji))
                        }
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
private fun SearchWordRow(word: DictionaryWord, modifier: Modifier = Modifier, meaning: String = word.meanings.firstOrNull().orEmpty()) {
    Row(modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DictionaryText(word.written, style = MaterialTheme.typography.titleLarge)
            DictionaryText(word.reading, style = MaterialTheme.typography.bodySmall)
        }
        DictionaryText(meaning, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
    }
}

/** The outline links variants; only the individual filled spelling surfaces are actions. */
@Composable
private fun GroupedSearchWordRow(group: List<WordSearchResult>, onOpen: (DictionaryWord) -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant,
        MaterialTheme.shapes.medium).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.forEach { result ->
                Surface(onClick = { onOpen(result.word) }, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.Start) {
                        DictionaryText(result.word.written, modifier = Modifier.fillMaxWidth(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge)
                        DictionaryText(result.word.reading, modifier = Modifier.fillMaxWidth(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        DictionaryText(group.minWith(com.example.kanjidb.data.dictionary.wordSearchOrder)
            .displayMeaning.standaloneKanjiMeaning(), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
    }
}
