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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.KanjiSearchPage
import com.example.kanjidb.data.dictionary.KanjiSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(onOpenDetails: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var explore by remember(dictionary) { mutableStateOf<List<KanjiSummary>>(emptyList()) }
    var exploreLoading by remember(dictionary) { mutableStateOf(true) }
    var exploreFailed by remember(dictionary) { mutableStateOf(false) }
    var exploreRetry by remember { mutableIntStateOf(0) }
    var limit by remember(query) { mutableIntStateOf(10) }
    var page by remember(query) { mutableStateOf(KanjiSearchPage(emptyList(), false)) }
    var searchLoading by remember(query) { mutableStateOf(true) }
    var searchFailed by remember(query) { mutableStateOf(false) }
    var searchRetry by remember(query) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val exploring = query.isBlank()

    LaunchedEffect(dictionary, exploreRetry) {
        exploreLoading = true
        exploreFailed = false
        try {
            explore = dictionary.getExploreKanji()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("Search", "Cannot load explore kanji", error)
            exploreFailed = true
        } finally {
            exploreLoading = false
        }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    LaunchedEffect(dictionary, query, limit, searchRetry) {
        if (exploring) return@LaunchedEffect
        searchLoading = true
        searchFailed = false
        try {
            delay(250)
            page = dictionary.searchKanji(query, limit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("Search", "Cannot search kanji", error)
            searchFailed = true
        } finally {
            searchLoading = false
        }
    }
    val loading = if (exploring) exploreLoading else searchLoading
    val failed = if (exploring) exploreFailed else searchFailed
    val rows = if (exploring) explore else page.kanji

    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true
        )
        Text(
            stringResource(if (exploring) R.string.search_explore else R.string.search_results),
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (exploring) 12.dp else 4.dp)
        ) {
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
            when {
                loading -> item { Text(stringResource(R.string.search_loading)) }
                failed -> item {
                    Column {
                        Text(stringResource(R.string.search_error))
                        TextButton(onClick = {
                            if (exploring) exploreRetry++ else searchRetry++
                        }) { Text(stringResource(R.string.search_retry)) }
                    }
                }
                rows.isEmpty() -> item { Text(stringResource(R.string.search_no_results)) }
                !exploring && page.hasMore -> item {
                    TextButton(onClick = { limit += 10 }) {
                        Text(stringResource(R.string.search_show_more_results))
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
