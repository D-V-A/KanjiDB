package com.example.kanjidb.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.ui.DictionaryText
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryWordDetails
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.kanjidb.ui.lists.KanjiCard
import com.example.kanjidb.ui.standaloneKanjiMeaning

@Composable
fun WordDetailsScreen(entryId: Long, written: String, sourceKanji: String,
    onOpenKanji: (String) -> Unit, onReturnToOrigin: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    var word by remember(entryId, written, sourceKanji) { mutableStateOf<DictionaryWordDetails?>(null) }
    var loading by remember(entryId, written, sourceKanji) { mutableStateOf(true) }
    var failed by remember(entryId, written, sourceKanji) { mutableStateOf(false) }
    LaunchedEffect(dictionary, entryId, written, sourceKanji) {
        try {
            word = dictionary.getWord(entryId, written, sourceKanji)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("WordDetails", "Cannot load word", error)
            failed = true
        } finally {
            loading = false
        }
    }
    var panelHeight by remember { mutableIntStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { panelHeight.toDp() } + 16.dp
    Box(modifier.fillMaxSize()) {
        val details = word
        if (loading || failed || details == null) {
            Text(stringResource(when {
                loading -> R.string.details_loading
                failed -> R.string.details_load_error
                else -> R.string.word_not_found
            }), Modifier.padding(16.dp))
        } else {
            val compact = isCompactWord(details.written)
            val meanings = details.orderedMeanings
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DictionaryText(meanings.firstOrNull()?.standaloneKanjiMeaning() ?: stringResource(R.string.word_no_meanings),
                        modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge)
                }
                item {
                    if (compact) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WrittenBlock(details.written, Modifier.weight(1f), compact = true)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.word_readings), style = MaterialTheme.typography.titleSmall)
                                WordTextList(details.readings, columns = true, primaryBold = true)
                            }
                        }
                    } else WrittenBlock(details.written, Modifier.fillMaxWidth())
                }
                if (details.alternativeWrittenForms.isNotEmpty()) {
                    item {
                        // Keep existing alternative spellings separate from the displayed form's readings.
                        DictionaryText(details.alternativeWrittenForms.joinToString(" ? "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!compact) {
                    item { Text(stringResource(R.string.word_readings), style = MaterialTheme.typography.titleLarge) }
                    items(details.readings.size) { index ->
                        DictionaryText(details.readings[index], style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal))
                    }
                }
                if (meanings.size > 1) {
                    item { Text(stringResource(R.string.details_meanings), style = MaterialTheme.typography.titleLarge) }
                    if (compact) {
                        item { WordTextList(meanings.drop(1), columns = true) }
                    } else items(meanings.drop(1)) { DictionaryText(it) }
                }
                item {
                    Text(stringResource(R.string.word_kanji), modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                }
                items(details.constituentKanji.chunked(4)) { row ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardWidth = (maxWidth - 8.dp * 3) / 4
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            row.forEach { kanji ->
                                KanjiCard(character = kanji.character, reading = kanji.primaryMeaning.standaloneKanjiMeaning(),
                                    selected = false, selecting = false, onClick = { onOpenKanji(kanji.character) },
                                    onLongClick = {}, allowLongClick = false, modifier = Modifier.width(cardWidth))
                            }
                        }
                    }
                }
            }
        }
        DetailActionPanel(onReturnToOrigin,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { panelHeight = it.height }.padding(12.dp)) {
            listOf(R.string.details_learning, R.string.details_known, R.string.custom_lists).forEach { label ->
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun WrittenBlock(written: String, modifier: Modifier, compact: Boolean = false) {
    Surface(modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.heightIn(min = 144.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Text(written, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = if (compact) 28.sp else 32.sp, lineHeight = 40.sp)
        }
    }
}

@Composable
private fun WordTextList(texts: List<String>, columns: Boolean, primaryBold: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        texts.chunked(if (columns) 2 else 1).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { columnIndex, text ->
                    DictionaryText(text, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (primaryBold && rowIndex == 0 && columnIndex == 0)
                                FontWeight.Bold else FontWeight.Normal))
                }
                if (columns && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
