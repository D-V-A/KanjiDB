package com.example.kanjidb.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryWord
import com.example.kanjidb.data.dictionary.WordSearchPage
import com.example.kanjidb.data.dictionary.KanjiSearchPage
import com.example.kanjidb.data.dictionary.KanjiSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(onOpenDetails: (String) -> Unit, onOpenWord: (Long, String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var wordMode by rememberSaveable { mutableStateOf(false) }
    var discoveryTab by rememberSaveable { mutableIntStateOf(0) }
    var limit by remember(query, wordMode) { mutableIntStateOf(10) }
    var page by remember(query, wordMode) { mutableStateOf(KanjiSearchPage(emptyList(), false)) }
    var wordPage by remember(query, wordMode) { mutableStateOf(WordSearchPage(emptyList(), false)) }
    var searchLoading by remember(query, wordMode) { mutableStateOf(true) }
    var searchFailed by remember(query, wordMode) { mutableStateOf(false) }
    var searchRetry by remember(query, wordMode) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val exploring = query.isBlank()
    val showingWords = if (exploring) discoveryTab == 2 else wordMode

    LaunchedEffect(dictionary) { ExploreState.initialize(dictionary) }
    LaunchedEffect(query, wordMode, discoveryTab) { listState.scrollToItem(0) }
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
    val loading = if (!exploring) searchLoading
        else if (showingWords) ExploreState.wordsLoading else ExploreState.kanjiLoading
    val failed = if (!exploring) searchFailed
        else if (showingWords) ExploreState.wordsFailed else ExploreState.kanjiFailed
    val rows = if (exploring) ExploreState.kanji else page.kanji
    val wordRows = if (exploring) ExploreState.words else wordPage.words
    val empty = if (showingWords) wordRows.isEmpty() else rows.isEmpty()
    val hasMore = if (showingWords) wordPage.hasMore else page.hasMore

    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineMedium)
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
            TabRow(selectedTabIndex = discoveryTab) {
                listOf(R.string.search_explore, R.string.search_recommended_kanji,
                    R.string.search_explore_words).forEachIndexed { index, title ->
                    Tab(selected = discoveryTab == index, onClick = { discoveryTab = index },
                        enabled = index != 1, text = { Text(stringResource(title)) })
                }
            }
        } else {
            Text(stringResource(R.string.search_results), style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
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
                            if (!exploring) searchRetry++
                            else if (showingWords) ExploreState.newWords(dictionary)
                            else ExploreState.newKanji(dictionary)
                        }) { Text(stringResource(R.string.search_retry)) }
                    }
                }
                empty -> item { Text(stringResource(R.string.search_dictionary_empty)) }
                !exploring && hasMore -> item {
                    TextButton(onClick = { limit += 10 }) {
                        Text(stringResource(R.string.search_show_more_results))
                    }
                }
            }
            if (exploring && !failed) {
                item {
                    TextButton(enabled = !loading, onClick = {
                        if (showingWords) ExploreState.newWords(dictionary)
                        else ExploreState.newKanji(dictionary)
                    }) { Text(stringResource(if (showingWords) R.string.search_new_words else R.string.search_new_kanji)) }
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
        Text(
            kanji.character,
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall
        )
        Text(
            kanji.primaryMeaning, modifier = Modifier.weight(1f),
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun SearchWordRow(word: DictionaryWord, modifier: Modifier = Modifier) {
    Row(modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(word.written, style = MaterialTheme.typography.titleLarge)
            Text(word.reading, style = MaterialTheme.typography.bodySmall)
        }
        Text(word.meanings.firstOrNull().orEmpty(), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
    }
}
