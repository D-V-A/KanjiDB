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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.kanjidb.ui.LocalDismissUnfocusedTextMenus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.kanjidb.data.dictionary.highlightedMeaning
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.kanjidb.ui.lists.KanjiCard
import com.example.kanjidb.ui.standaloneKanjiMeaning

@Composable
fun WordDetailsScreen(entryId: Long, written: String, sourceKanji: String,
    onOpenKanji: (String) -> Unit, onReturnToOrigin: () -> Unit, modifier: Modifier = Modifier,
    matchedGlossId: Long = -1, matchedMeaning: String = "") {
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val snackbar = remember { SnackbarHostState() }
    val copyText = rememberDetailsCopyAction(snackbar)
    var activeEntry by rememberSaveable(entryId,written) { mutableStateOf(entryId) }
    var selectedWritten by rememberSaveable(entryId,written) { mutableStateOf(written) }
    var alternativesExpanded by rememberSaveable(entryId,written) { mutableStateOf(false) }
    var entriesExpanded by rememberSaveable(entryId,written) { mutableStateOf(false) }
    var word by remember { mutableStateOf<DictionaryWordDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(dictionary, activeEntry, selectedWritten) {
        loading = true
        failed = false
        word = null
        try {
            word = dictionary.getWord(activeEntry, selectedWritten)
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
    CompositionLocalProvider(LocalDismissUnfocusedTextMenus provides true) {
    Box(modifier.fillMaxSize().clearSelectionOnBackgroundTap()) {
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
            val highlighted = details.highlightedMeaning(entryId,matchedGlossId,matchedMeaning)?.takeIf { it != meanings.firstOrNull() }
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
                            WrittenBlock(details.written, Modifier.weight(1f), copyText, compact = true)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.word_readings), style = MaterialTheme.typography.titleSmall)
                                WordTextList(details.readings, primaryBold = true)
                            }
                        }
                    } else WrittenBlock(details.written, Modifier.fillMaxWidth(), copyText)
                }
                if (details.alternativeWrittenForms.isNotEmpty() || details.otherEntries.isNotEmpty()) {
                    item {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val controlWidth = if (compact) (maxWidth - 16.dp) / 2 else maxWidth
                            Column(Modifier.width(controlWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (details.alternativeWrittenForms.isNotEmpty()) {
                                    WordChoiceMenu(stringResource(R.string.word_alternative_writings),
                                        alternativesExpanded, { alternativesExpanded = false }, {
                                            entriesExpanded = false
                                            alternativesExpanded = !alternativesExpanded
                                        }) {
                                        details.alternativeWrittenForms.forEach { alternative ->
                                            DropdownMenuItem(text = { Text(alternative) }, onClick = {
                                                if (activeEntry == details.entryId && selectedWritten == details.written) {
                                                    selectedWritten = alternative
                                                    alternativesExpanded = false
                                                    entriesExpanded = false
                                                }
                                            })
                                        }
                                    }
                                }
                                if (details.otherEntries.isNotEmpty()) {
                                    WordChoiceMenu(stringResource(R.string.word_other_entries),
                                        entriesExpanded, { entriesExpanded = false }, {
                                            alternativesExpanded = false
                                            entriesExpanded = !entriesExpanded
                                        }) {
                                        details.otherEntries.forEach { other ->
                                            DropdownMenuItem(text = {
                                                Text("${other.reading} \u2014 ${other.meanings.firstOrNull().orEmpty()}")
                                            }, onClick = {
                                                if (activeEntry == details.entryId && selectedWritten == details.written) {
                                                    activeEntry = other.entryId
                                                    entriesExpanded = false
                                                    alternativesExpanded = false
                                                }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!compact) {
                    item { Text(stringResource(R.string.word_readings), style = MaterialTheme.typography.titleLarge) }
                    item { WordTextList(details.readings, primaryBold = true) }
                }
                if (details.readingMeaningGroups.size > 1 && details.readingMeaningGroups.any { it.otherMeaningTexts(meanings.firstOrNull()).isNotEmpty() }) {
                    item { Text(stringResource(R.string.word_other_meanings), style = MaterialTheme.typography.titleLarge) }
                    items(details.readingMeaningGroups.filter { it.otherMeaningTexts(meanings.firstOrNull()).isNotEmpty() }) { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(group.readings.joinToString(" / "), style = MaterialTheme.typography.titleSmall)
                            WordTextList(group.otherMeaningTexts(meanings.firstOrNull()),highlighted = highlighted,
                                measurementTexts=details.readingMeaningGroups.flatMap { it.otherMeaningTexts(meanings.firstOrNull()) })
                        }
                    }
                } else if (meanings.size > 1) {
                    item { Text(stringResource(R.string.word_other_meanings), style = MaterialTheme.typography.titleLarge) }
                    item { WordTextList(meanings.drop(1), highlighted = highlighted) }
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
            listOf(R.string.details_known, R.string.details_learning, R.string.details_lists).forEach { label ->
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(stringResource(label))
                }
            }
        }
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter)
            .padding(bottom=with(LocalDensity.current) { panelHeight.toDp() }))
    }
    }
}

@Composable
private fun WrittenBlock(written: String, modifier: Modifier, onCopy: (String) -> Unit, compact: Boolean = false) {
    Surface(modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.heightIn(min = 144.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            DictionaryText(written, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(textAlign=TextAlign.Center,
                    fontSize = if (compact) 28.sp else 32.sp, lineHeight = 40.sp))
            DetailsCopyButton(written,stringResource(R.string.details_copy_word),onCopy,
                Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun WordTextList(texts: List<String>, primaryBold: Boolean = false, highlighted: String? = null,
    measurementTexts: List<String> = texts) {
    val measurer=rememberTextMeasurer()
    val bodyStyle=MaterialTheme.typography.bodyLarge
    val styleFor: (Int,String) -> androidx.compose.ui.text.TextStyle = { index,text ->
        bodyStyle.copy(fontWeight=if(text==highlighted || (primaryBold && index==0)) FontWeight.Bold else FontWeight.Normal)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gapPx=with(LocalDensity.current) { 12.dp.roundToPx() }
        val columns=wordTextColumnCount(measurementTexts,constraints.maxWidth,gapPx,measurer,styleFor)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            texts.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { columnIndex, text ->
                        DictionaryText(text, modifier = Modifier.weight(1f),
                            style = styleFor(rowIndex*columns+columnIndex,text))
                    }
                    if (columns==2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Menu lives in a popup; expanding it never contributes height to the details list. */
@Composable
private fun WordChoiceMenu(label: String, expanded: Boolean, onDismiss: () -> Unit,
    onToggle: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) { content() }
    }
}
