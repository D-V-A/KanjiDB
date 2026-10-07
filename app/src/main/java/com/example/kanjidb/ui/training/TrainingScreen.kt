package com.example.kanjidb.ui.training

import com.example.kanjidb.ui.AppIcons
import com.example.kanjidb.ui.IconLabel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryKanji
import com.example.kanjidb.data.user.UserKanjiStateDao
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.primaryMeaning
import com.example.kanjidb.ui.search.RecommendedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class TrainingKind(val title: String) { KANJI("Kanji Training"), WORD("Word Training") }
private data class PreparedWordTraining(
    val plan: WordTrainingPlan, val summaries: Map<String, com.example.kanjidb.data.dictionary.KanjiSummary>
)

@Composable
internal fun TrainingScreen(userDao: UserKanjiStateDao, onRequestExit: () -> Unit, modifier: Modifier = Modifier) {
    var setup by rememberSaveable { mutableStateOf<TrainingKind?>(null) }
    val fixedPool = TrainingState.fixedWordPool
    val session = TrainingState.session
    val wordSession = TrainingState.wordSession
    val complete = session?.complete == true || wordSession?.complete == true
    BackHandler(enabled = TrainingState.active || setup != null || fixedPool != null) {
        if (TrainingState.active) {
            if (!TrainingState.saving) onRequestExit()
        } else { setup = null; TrainingState.clearFixedPool() }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when {
                complete -> "Results"
                wordSession != null || fixedPool != null -> "Word Training"
                session != null -> "Kanji Training"
                else -> setup?.title ?: "Training"
            },
                Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            if (!complete && (TrainingState.active || setup != null || fixedPool != null)) {
                TextButton(enabled = !TrainingState.saving, onClick = {
                    if (TrainingState.active) onRequestExit() else { setup = null; TrainingState.clearFixedPool() }
                }) { IconLabel(if (TrainingState.active) AppIcons.CircleX else AppIcons.CircleChevronLeft) {
                    Text(if (TrainingState.active) "End training" else "Back")
                } }
            }
        }
        when {
            wordSession != null && wordSession.complete -> WordTrainingResults(wordSession, userDao)
            wordSession != null -> key(wordSession.currentWord?.key) { WordTrainingQuestion(wordSession) }
            session != null && session.complete -> TrainingResults(session, userDao)
            session != null -> key(session.currentCharacter) { TrainingQuestion(session) }
            fixedPool != null -> TrainingSetup(userDao, wordMode = true, fixedPool = fixedPool, onStarted = { setup = null })
            setup != null -> TrainingSetup(userDao, wordMode = setup == TrainingKind.WORD, onStarted = { setup = null })
            else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(onClick = { setup = TrainingKind.KANJI }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kanji Training", style = MaterialTheme.typography.titleLarge)
                        Text("Recall kanji from their meaning and readings.")
                    }
                }
                Card(onClick = { setup = TrainingKind.WORD }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Word Training", style = MaterialTheme.typography.titleLarge)
                        Text("Practice kanji in words with translation and reading prompts.")
                    }
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text("More coming soon", Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrainingSetup(dao: UserKanjiStateDao, wordMode: Boolean, onStarted: () -> Unit, fixedPool: List<String>? = null) {
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val rows by remember(dao) { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val listDao = remember(context) { com.example.kanjidb.data.user.UserDatabase.getInstance(context).customLists() }
    val lists by remember(listDao) { listDao.observeLists() }.collectAsStateWithLifecycle(initialValue = null)
    val availableLists = com.example.kanjidb.data.user.nonemptyCustomLists(lists.orEmpty())
    var selectedList by rememberSaveable { mutableStateOf<Long?>(null) }
    val effectiveList = availableLists.firstOrNull { it.list.id == selectedList } ?: availableLists.firstOrNull()
    LaunchedEffect(effectiveList?.list?.id) { selectedList = effectiveList?.list?.id }
    var dictionaryCharacters by remember(dictionary) { mutableStateOf<Set<String>?>(null) }
    var mode by rememberSaveable { mutableStateOf(TrainingMode.REVIEW) }
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var startFailed by remember { mutableStateOf(false) }
    var noWords by remember { mutableStateOf(false) }
    var coverage by rememberSaveable { mutableIntStateOf(1) }
    var prompt by rememberSaveable { mutableStateOf(WordPrompt.BOTH) }
    var length by rememberSaveable { mutableStateOf(WordLength.SHORT) }
    val wordSettings = WordTrainingSettings(coverage, prompt, length)
    var prepared by remember { mutableStateOf<PreparedWordTraining?>(null) }
    val busy = starting || prepared != null
    val scope = rememberCoroutineScope()
    LaunchedEffect(dictionary, retry, fixedPool) {
        if (fixedPool != null) return@LaunchedEffect
        failed = false
        try {
            dictionaryCharacters = dictionary.getKanjiGroups().map { it.character }.toSet()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            android.util.Log.e("Training", "Cannot load training sources", error)
            failed = true
        }
    }
    val source = fixedPool ?: when (mode) {
        TrainingMode.NEW -> RecommendedState.candidateCharacters
        TrainingMode.MY_LISTS -> com.example.kanjidb.data.user.customListTrainingPool(lists.orEmpty(), selectedList)
            .filter { it in dictionaryCharacters.orEmpty() }
        else -> rows.orEmpty().filter {
            it.state == if (mode == TrainingMode.REVIEW) LearningState.KNOWN else LearningState.LEARNING
        }.map { it.character }.filter { it in dictionaryCharacters.orEmpty() }
    }
    val loading = if (fixedPool != null) rows == null else (if (mode == TrainingMode.NEW) RecommendedState.loading else rows == null || dictionaryCharacters == null || (mode == TrainingMode.MY_LISTS && lists == null)) ||
        (wordMode && (rows == null || dictionaryCharacters == null))
    val sourceFailed = fixedPool == null && ((if (mode == TrainingMode.NEW) RecommendedState.failed else failed) || (wordMode && failed))
    val available = source.size
    var selectedSize by rememberSaveable { mutableIntStateOf(10) }
    var input by rememberSaveable { mutableStateOf("10") }
    // Loading is not an empty source: do not destroy the selection during initial load/recreation.
    val count = if (loading || sourceFailed) selectedSize else sessionSizeForSource(selectedSize, available)
    val values = sessionSizes(available)
    LaunchedEffect(mode, available, loading, sourceFailed) {
        if (!loading && !sourceFailed) {
            selectedSize = count
            input = count.toString()
        }
    }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (fixedPool == null) {
            Text("Training type", style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainingMode.entries.forEach { option ->
                    FilterChip(selected = mode == option, enabled = !busy && (option != TrainingMode.MY_LISTS || availableLists.isNotEmpty()), onClick = {
                        focus.clearFocus()
                        selectedSize = count
                        input = count.toString()
                        mode = option
                        startFailed = false
                        noWords = false
                    }, label = { Text(option.title) })
                }
            }
            Text(when (mode) {
                TrainingMode.REVIEW -> "Practice your Known kanji."
                TrainingMode.LEARNING -> "Practice kanji you are Learning."
                TrainingMode.NEW -> "Practice new kanji from Recommended."
                TrainingMode.MY_LISTS -> "Practice kanji from a Custom List."
            })
            if (mode == TrainingMode.MY_LISTS) {
                var dropdown by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(enabled = !busy && availableLists.isNotEmpty(), onClick = { dropdown = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(effectiveList?.list?.name ?: "No nonempty lists")
                    }
                    DropdownMenu(expanded = dropdown, onDismissRequest = { dropdown = false }) {
                        availableLists.forEach { list ->
                            DropdownMenuItem(text = { Text(list.list.name) }, onClick = {
                                focus.clearFocus(); selectedSize = count; selectedList = list.list.id; dropdown = false; noWords = false
                            })
                        }
                    }
                }
            }
        }
        when {
            sourceFailed -> {
                Text("Could not load kanji.")
                TextButton(onClick = {
                    if (mode == TrainingMode.NEW) RecommendedState.initialize(dictionary, dao)
                    retry++
                }) { Text("Retry") }
            }
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            available == 0 -> Text("No kanji available for this mode.")
            fixedPool != null -> Text("Selected kanji: ${fixedPool.size}", style = MaterialTheme.typography.titleMedium)
            else -> {
                Text("$available kanji available", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = input, onValueChange = {
                        input = it.filter(Char::isDigit)
                        selectedSize = normalizedSessionSize(input, available)
                    },
                    enabled = available >= 5 && !busy, singleLine = true,
                    label = { Text(if (wordMode) "Kanji pool size" else "Session size") },
                    supportingText = { Text("This session: $count kanji") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { input = count.toString(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) input = count.toString() }
                )
                Slider(
                    value = values.indexOf(count).toFloat(),
                    onValueChange = {
                        selectedSize = values[it.roundToInt().coerceIn(values.indices)]
                        input = selectedSize.toString()
                    },
                    valueRange = 0f..(values.size - 1).coerceAtLeast(1).toFloat(),
                    steps = (values.size - 2).coerceAtLeast(0),
                    enabled = values.size > 1 && !busy,
                    modifier = Modifier.semantics { contentDescription = if (wordMode) "Kanji pool size: $count kanji" else "Session size: $count kanji" }
                )
                if (available < 5) Text("All $available available kanji will be used.")
            }
        }
        if (wordMode) {
            WordTrainingOptions(wordSettings, enabled = !busy) { settings ->
                coverage = settings.targetCoverage; prompt = settings.prompt; length = settings.length; noWords = false
            }
        } else {
            Text("How it works", style = MaterialTheme.typography.titleLarge)
            Text("You’ll be shown the meaning and readings of a kanji. Try to reproduce the kanji — for example, write it on paper or draw it from memory.")
            Text("Then tap Show answer, compare your answer with the kanji shown, and mark it as Correct or Incorrect.")
            Text("At the end, you can review your results and practice the kanji again if you want.")
        }
        if (noWords) Text("No suitable words for this kanji pool and filters. Try another source or a different word length.",
            color = MaterialTheme.colorScheme.error)
        if (startFailed) Text("Could not start training. Please try again.", color = MaterialTheme.colorScheme.error)
        Button(
            enabled = !loading && !sourceFailed && !busy && available > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                focus.clearFocus()
                input = count.toString()
                val chosen = fixedPool ?: selectTrainingPool(source, count)
                val chosenMode = mode
                val chosenSettings = wordSettings
                val known = rows.orEmpty().filter { it.state == LearningState.KNOWN }.map { it.character }.toSet()
                starting = true
                startFailed = false
                noWords = false
                scope.launch {
                    try {
                        if (wordMode) {
                            val candidates = dictionary.getWordTrainingCandidates(chosen)
                            val plan = withContext(Dispatchers.Default) {
                                WordTrainingSelection.build(candidates, chosen, known, chosenSettings)
                            }
                            if (plan.words.isEmpty()) noWords = true else {
                                val resultCharacters = (plan.pool + plan.words.flatMap { it.kanjiOccurrences }
                                    .filter { it in known }).distinct()
                                val summaries = dictionary.getKanjiSummaries(resultCharacters)
                                if (plan.words.size > 30) prepared = PreparedWordTraining(plan, summaries)
                                else { TrainingState.startWords(plan, summaries); onStarted() }
                            }
                        } else {
                            val cards = chosen.map { character ->
                                requireNotNull(dictionary.getKanji(character, includeWords = false))
                            }
                            TrainingState.start(chosenMode, cards)
                            onStarted()
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        android.util.Log.e("Training", "Cannot start training", error)
                        startFailed = true
                    } finally { starting = false }
                }
            }
        ) { Text(if (starting) "Starting…" else "Start") }
    }
    prepared?.let { ready ->
        TrainingConfirmationDialog(
            title = "Large session (${ready.plan.words.size} words)",
            message = "Your session contains a large number of words.\nIf you end the session early, the results will not be recorded.\nContinue?",
            onDismiss = { prepared = null },
            onConfirm = {
                prepared = null
                TrainingState.startWords(ready.plan, ready.summaries)
                onStarted()
            })
    }
}

private enum class AnswerDisplay { GLYPH, STROKES }

@Composable
private fun TrainingQuestion(session: TrainingSession) {
    val kanji = TrainingState.cards.getValue(requireNotNull(session.currentCharacter))
    // Future Glyph/Strokes switch stays hidden; no stroke data or renderer in this version.
    val answerDisplay by rememberSaveable { mutableStateOf(AnswerDisplay.GLYPH) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(kanji.primaryMeaning ?: "No English meaning", Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge)
                Text((session.questionIndex + 1).toString() + " / " + session.questionOrder.size,
                    Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            TrainingAnswerArea(kanji, session.revealed, answerDisplay)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrainingReadingsColumn("On", kanji.onReadings, Modifier.weight(1f))
                TrainingReadingsColumn("Kun", kanji.kunReadings, Modifier.weight(1f))
            }
        }
        TrainingQuestionControls(session.revealed,
            onReveal = { TrainingState.update { it.reveal() } },
            onAnswer = { result -> TrainingState.update { it.answer(result) } })
    }
}

/** Stable On/Kun columns: each selected original reading always gets its own row. */
@Composable
private fun TrainingReadingsColumn(title: String, source: List<String>, modifier: Modifier) {
    val readings = remember(source) { trainingReadings(source) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (readings.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            readings.forEach { Text(it, style = MaterialTheme.typography.titleLarge) }
        }
    }
}
/** Dedicated central slot for future hint content; no hint UI or component data in v1. */
@Composable
private fun TrainingAnswerArea(kanji: DictionaryKanji, revealed: Boolean, display: AnswerDisplay) {
    TrainingAnswerSurface {
        if (revealed) {
            when (display) {
                AnswerDisplay.GLYPH -> Text(kanji.character, fontSize = 112.sp, lineHeight = 128.sp)
                AnswerDisplay.STROKES -> Unit // Reserved; switch and rendering are intentionally absent.
            }
        }
    }
}

@Composable
private fun TrainingResults(session: TrainingSession, dao: UserKanjiStateDao) {
    var choosePractice by rememberSaveable { mutableStateOf(false) }
    val options = session.practiceOptions()
    val saving = TrainingState.saving
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.mode == TrainingMode.NEW) {
                listOf(LearningState.LEARNING, LearningState.KNOWN).forEach { target ->
                    item(key = "new_bulk_$target") {
                        OutlinedButton(enabled = !saving, modifier = Modifier.fillMaxWidth(),
                            onClick = { TrainingState.update { it.withNewBulkActions(target) } }) {
                            Text(if (target == LearningState.KNOWN) "Add all correct to Known" else "Add all mistakes to Learning")
                        }
                    }
                }
            } else if (session.bulkTargets().isNotEmpty()) {
                item(key = "bulk_finish") {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        onClick = { TrainingState.finish(dao, bulk = true) }
                    ) {
                        Text(if (session.mode == TrainingMode.REVIEW)
                            "Move all current mistakes to Learning and Finish"
                        else "Add all correct to Known and finish")
                    }
                }
            }
            item(key = "pending_explanation") {
                Text("Individual changes are saved only when you finish training.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            items(session.pool, key = { it }) { character ->
                val kanji = TrainingState.cards.getValue(character)
                val correct = session.lastResults[character] == TrainingResult.CORRECT
                val participated = session.participated(character)
                TrainingResultCard(character, kanji.primaryMeaning ?: "No English meaning",
                    status = if (correct) TrainingResultStatus.SUCCESS else TrainingResultStatus.FAILURE,
                    participated = participated, actions = session.allowedActions(character),
                    pending = session.pending[character], saving = saving,
                    reviewing = session.mode == TrainingMode.REVIEW,
                    onAction = { target -> TrainingState.update { it.toggleAction(character, target) } })
            }
        }
        TrainingResultsFooter("Practice again", onPractice = {
            if (options.size == 1) TrainingState.update { it.practice(options.single().kind) }
            else choosePractice = true
        }, onFinish = { TrainingState.finish(dao) })
    }
    if (choosePractice) {
        TrainingRepeatMenu(options.map { it.kind to it.characters.size }, onDismiss = { choosePractice = false }) { kind ->
            choosePractice = false
            TrainingState.update { it.practice(kind) }
        }
    }
}
