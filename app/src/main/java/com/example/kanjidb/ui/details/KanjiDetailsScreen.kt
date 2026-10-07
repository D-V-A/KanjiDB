package com.example.kanjidb.ui.details

import com.example.kanjidb.ui.AppIcons
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjidb.R
import com.example.kanjidb.ui.DictionaryText
import com.example.kanjidb.ui.standaloneKanjiMeaning
import com.example.kanjidb.ui.primaryMeaning
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryKanji
import com.example.kanjidb.data.dictionary.DictionaryWord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.example.kanjidb.data.user.UserKanjiStateDao
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun KanjiDetailsScreen(
    kanjiId: String,
    userDao: UserKanjiStateDao,
    onOpenWord: (Long, String) -> Unit,
    onReturnToOrigin: () -> Unit,
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
    val kanji = loadedKanji
    if (loading || failed || kanji == null) {
        Box(modifier.fillMaxSize()) {
            Text(stringResource(when {
                loading -> R.string.details_loading
                failed -> R.string.details_load_error
                else -> R.string.details_not_found
            }), Modifier.padding(16.dp))
            DetailActionPanel(onReturnToOrigin,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                listOf(R.string.details_known, R.string.details_learning, R.string.custom_lists).forEach { label ->
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(stringResource(label))
                    }
                }
            }
        }
        return
    }

    val visibleWords = if (commonWordsExpanded) kanji.words else kanji.words.take(6)

    val savedState by remember(userDao, kanjiId) { userDao.observeState(kanjiId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val learningState = savedState?.state ?: LearningState.NONE
    val scope = rememberCoroutineScope()
    var saving by remember(kanjiId) { mutableStateOf(false) }
    var writeFailed by remember(kanjiId) { mutableStateOf(false) }
    fun toggleState(target: LearningState) {
        if (saving) return
        saving = true
        writeFailed = false
        scope.launch {
            try {
                userDao.toggle(kanjiId, target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("KanjiDetails", "Cannot save kanji state", error)
                writeFailed = true
            } finally {
                saving = false
            }
        }
    }
    val customListsDao = remember(context) { com.example.kanjidb.data.user.UserDatabase.getInstance(context).customLists() }
    var listsOpen by rememberSaveable(kanjiId) { mutableStateOf(false) }
    if (listsOpen) com.example.kanjidb.ui.lists.CustomListsDialog(customListsDao, listOf(kanji.character)) { listsOpen = false }
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
                    DictionaryText(
                        kanji.primaryMeaning ?: kanji.character,
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
                    kanji.jlpt?.let { level ->
                        Badge {
                            Text(stringResource(R.string.groups_jlpt_level, level),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            if (writeFailed) {
                item { Text(stringResource(R.string.search_error)) }
            }
            item {
                KanjiOverview(kanji, showStrokes, onShowStrokes = { showStrokes = it })
            }
            item {
                Text(
                    stringResource(R.string.details_words),
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

        DetailActionPanel(
            onReturnToOrigin = onReturnToOrigin,
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { panelHeight = it.height }
                .padding(12.dp)
        ) {
            OutlinedButton(
                enabled = !saving, onClick = { toggleState(LearningState.KNOWN) },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (learningState == LearningState.KNOWN) MaterialTheme.colorScheme.secondaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.details_known)) }
            OutlinedButton(
                enabled = !saving, onClick = { toggleState(LearningState.LEARNING) },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (learningState == LearningState.LEARNING) MaterialTheme.colorScheme.secondaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.details_learning)) }
            OutlinedButton(
                onClick = { listsOpen = true },
                enabled = !saving,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.custom_lists))
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
            val glyphLabel = stringResource(R.string.details_glyph)
            val strokesLabel = stringResource(R.string.details_strokes)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val iconSpacing = ((maxWidth - 132.dp) / 2).coerceIn(0.dp, 8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = showStrokes, role = Role.Switch, onValueChange = onShowStrokes)
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (showStrokes) strokesLabel else glyphLabel
                    },
                horizontalArrangement = Arrangement.spacedBy(iconSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Eye,
                    contentDescription = glyphLabel,
                    modifier = Modifier.size(40.dp),
                    tint = if (!showStrokes) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Switch(
                    checked = showStrokes,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics { }
                )
                Icon(
                    imageVector = AppIcons.Brush,
                    contentDescription = strokesLabel,
                    modifier = Modifier.size(40.dp),
                    tint = if (showStrokes) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
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
                        DictionaryText(kanji.character, fontSize = 88.sp, lineHeight = 104.sp)
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
            kanji.meanings.forEach { DictionaryText(it.standaloneKanjiMeaning()) }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.details_on),
                        style = MaterialTheme.typography.titleSmall)
                    DictionaryText(kanji.onReadings.joinToString("\n"))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.details_kun),
                        style = MaterialTheme.typography.titleSmall)
                    DictionaryText(kanji.kunReadings.joinToString("\n"))
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
                DictionaryText(
                    word.written,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DictionaryText(
                    word.reading,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val visibleMeanings = word.meanings.take(3)
            Column(Modifier.weight(0.62f)) {
                visibleMeanings.forEachIndexed { index, meaning ->
                    DictionaryText(
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