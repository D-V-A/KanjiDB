package com.example.kanjidb.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjidb.R
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryKanji
import com.example.kanjidb.data.dictionary.DictionaryWord
import kotlinx.coroutines.CancellationException

@Composable
fun KanjiDetailsScreen(
    kanjiId: String,
    onOpenWord: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    var loadedKanji by remember(kanjiId) { mutableStateOf<DictionaryKanji?>(null) }
    var loading by remember(kanjiId) { mutableStateOf(true) }
    var failed by remember(kanjiId) { mutableStateOf(false) }
    var commonWordsExpanded by remember(kanjiId) { mutableStateOf(false) }
    LaunchedEffect(dictionary, kanjiId) {
        try {
            loadedKanji = dictionary.getKanji(kanjiId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("KanjiDetails", "Cannot load dictionary", error)
            failed = true
        } finally {
            loading = false
        }
    }
    if (loading || failed) {
        Text(
            stringResource(if (loading) R.string.details_loading else R.string.details_load_error),
            modifier.padding(16.dp)
        )
        return
    }
    val kanji = loadedKanji
    if (kanji == null) {
        Text(stringResource(R.string.details_not_found), modifier.padding(16.dp))
        return
    }

    val visibleWords = if (commonWordsExpanded) kanji.words else kanji.words.take(6)

    var learningState by rememberSaveable(kanjiId) { mutableStateOf(LearningState.NONE) }
    var showStrokes by rememberSaveable(kanjiId) { mutableStateOf(false) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { panelHeight.toDp() } + 16.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        kanji.meanings.firstOrNull() ?: kanji.character,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    if (kanji.isJoyo) {
                        Badge {
                            Text(
                                stringResource(R.string.details_joyo),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            item {
                KanjiOverview(kanji, showStrokes, onShowStrokes = { showStrokes = it })
            }
            item {
                Text(
                    stringResource(if (kanji.hasCommonWords) R.string.details_recommended else R.string.details_words),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(visibleWords, key = { "${it.entryId}:${it.written}" }) { word ->
                WordRow(word, recommended = true, onClick = { onOpenWord(word.entryId, word.written) })
            }
            if (kanji.words.size > 6) {
                item(key = "common_words_toggle") {
                    TextButton(onClick = { commonWordsExpanded = !commonWordsExpanded }) {
                        Text(stringResource(
                            if (commonWordsExpanded) R.string.details_show_less
                            else R.string.details_show_more
                        ))
                    }
                }
            }
            if (kanji.words.isEmpty()) {
                item { Text(stringResource(R.string.details_no_words)) }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { panelHeight = it.height }
                .padding(12.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = learningState == LearningState.LEARNING,
                    onClick = {
                        learningState = if (learningState == LearningState.LEARNING) {
                            LearningState.NONE
                        } else {
                            LearningState.LEARNING
                        }
                    },
                    label = { Text(stringResource(R.string.details_learning)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = learningState == LearningState.KNOWN,
                    onClick = {
                        learningState = if (learningState == LearningState.KNOWN) {
                            LearningState.NONE
                        } else {
                            LearningState.KNOWN
                        }
                    },
                    label = { Text(stringResource(R.string.details_known)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.details_add_to_list))
                }
            }
        }
    }
}

@Composable
private fun KanjiOverview(
    kanji: DictionaryKanji,
    showStrokes: Boolean,
    onShowStrokes: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier.weight(0.44f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = !showStrokes,
                    onClick = { onShowStrokes(false) },
                    label = {
                        Text(stringResource(R.string.details_glyph),
                            style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showStrokes,
                    onClick = { onShowStrokes(true) },
                    label = {
                        Text(stringResource(R.string.details_strokes),
                            style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(144.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (showStrokes) {
                        Text(
                            stringResource(R.string.details_stroke_placeholder),
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(kanji.character, fontSize = 88.sp, lineHeight = 104.sp)
                    }
                }
            }
            kanji.strokeCount?.let { count ->
                Text(
                    pluralStringResource(R.plurals.details_stroke_count, count, count),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            kanji.grade?.let {
                Text(stringResource(R.string.details_grade, it), style = MaterialTheme.typography.bodySmall)
            }
            kanji.frequency?.let {
                Text(stringResource(R.string.details_frequency, it), style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(
            modifier = Modifier.weight(0.56f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.details_meanings),
                style = MaterialTheme.typography.titleSmall)
            kanji.meanings.forEach { Text(it) }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.details_on),
                        style = MaterialTheme.typography.titleSmall)
                    Text(kanji.onReadings.joinToString("\n"))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.details_kun),
                        style = MaterialTheme.typography.titleSmall)
                    Text(kanji.kunReadings.joinToString("\n"))
                }
            }
        }
    }
}

@Composable
private fun WordRow(word: DictionaryWord, recommended: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (recommended) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    word.written,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    word.reading,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val visibleMeanings = word.meanings.take(3)
            Column(Modifier.weight(0.62f)) {
                visibleMeanings.forEachIndexed { index, meaning ->
                    Text(
                        meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        // Share a three-line budget without hiding later meanings.
                        maxLines = if (index == 0) 4 - visibleMeanings.size else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}