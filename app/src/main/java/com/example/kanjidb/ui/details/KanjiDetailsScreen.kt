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
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjidb.R
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.MockKanji
import com.example.kanjidb.ui.MockWord
import com.example.kanjidb.ui.mockKanji

@Composable
fun KanjiDetailsScreen(kanjiId: String, modifier: Modifier = Modifier) {
    val kanji = mockKanji.find { it.id == kanjiId }
    if (kanji == null) {
        Text(stringResource(R.string.details_not_found), modifier.padding(16.dp))
        return
    }

    var learningState by rememberSaveable(kanjiId) { mutableStateOf(LearningState.NONE) }
    var showStrokes by rememberSaveable(kanjiId) { mutableStateOf(false) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { panelHeight.toDp() } + 16.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
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
                        stringResource(kanji.title),
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
                    stringResource(R.string.details_recommended),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(kanji.recommendedWords) { word -> WordRow(word, recommended = true) }
            item {
                Text(
                    stringResource(R.string.details_other),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(kanji.otherWords) { word -> WordRow(word, recommended = false) }
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
    kanji: MockKanji,
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
                        Text(stringResource(kanji.character), fontSize = 88.sp, lineHeight = 104.sp)
                    }
                }
            }
            Text(
                pluralStringResource(
                    R.plurals.details_stroke_count, kanji.strokeCount, kanji.strokeCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Column(
            modifier = Modifier.weight(0.56f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.details_meanings),
                style = MaterialTheme.typography.titleSmall)
            kanji.meanings.forEach { Text(stringResource(it)) }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.details_on),
                        style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(kanji.onyomi))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.details_kun),
                        style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(kanji.kunyomi))
                }
            }
        }
    }
}

@Composable
private fun WordRow(word: MockWord, recommended: Boolean) {
    Card(
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
            Column(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(word.writing), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(word.reading), style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                word.meanings.forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}