package com.example.kanjidb.ui.training

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
import kotlin.math.roundToInt

@Composable
internal fun TrainingScreen(userDao: UserKanjiStateDao, onRequestExit: () -> Unit, modifier: Modifier = Modifier) {
    var setup by rememberSaveable { mutableStateOf(false) }
    val session = TrainingState.session
    BackHandler(enabled = session != null || setup) {
        if (session != null) {
            if (!TrainingState.saving) onRequestExit()
        } else setup = false
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (session?.complete == true) "Results" else if (setup) "Kanji Training" else "Training",
                Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            if (session != null || setup) {
                TextButton(enabled = !TrainingState.saving, onClick = {
                    if (session != null) onRequestExit() else setup = false
                }) { Text(if (session != null) "End training" else "Back") }
            }
        }
        when {
            session != null && session.complete -> TrainingResults(session, userDao)
            session != null -> key(session.currentCharacter) { TrainingQuestion(session) }
            setup -> TrainingSetup(userDao, onStarted = { setup = false })
            else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(onClick = { setup = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kanji Training", style = MaterialTheme.typography.titleLarge)
                        Text("Recall kanji from their meaning and readings.")
                    }
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Word Training", style = MaterialTheme.typography.titleLarge)
                        Text("Coming later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingSetup(dao: UserKanjiStateDao, onStarted: () -> Unit) {
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val rows by remember(dao) { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    var dictionaryCharacters by remember(dictionary) { mutableStateOf<Set<String>?>(null) }
    var mode by rememberSaveable { mutableStateOf(TrainingMode.REVIEW) }
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var startFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(dictionary, retry) {
        failed = false
        try {
            dictionaryCharacters = dictionary.getKanjiGroups().map { it.character }.toSet()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            android.util.Log.e("Training", "Cannot load training sources", error)
            failed = true
        }
    }
    val source = when (mode) {
        TrainingMode.NEW -> RecommendedState.candidateCharacters
        else -> rows.orEmpty().filter {
            it.state == if (mode == TrainingMode.REVIEW) LearningState.KNOWN else LearningState.LEARNING
        }.map { it.character }.filter { it in dictionaryCharacters.orEmpty() }
    }
    val loading = if (mode == TrainingMode.NEW) RecommendedState.loading else rows == null || dictionaryCharacters == null
    val sourceFailed = if (mode == TrainingMode.NEW) RecommendedState.failed else failed
    val available = source.size
    val values = sessionSizes(available)
    var input by rememberSaveable(mode, available) { mutableStateOf(normalizedSessionSize("10", available).toString()) }
    val count = normalizedSessionSize(input, available)
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Training type", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainingMode.entries.forEach { option ->
                FilterChip(selected = mode == option, enabled = !starting, onClick = {
                    mode = option
                    startFailed = false
                }, label = { Text(option.title) })
            }
        }
        Text(when (mode) {
            TrainingMode.REVIEW -> "Practice your Known kanji."
            TrainingMode.LEARNING -> "Practice kanji you are Learning."
            TrainingMode.NEW -> "Practice new kanji from Recommended."
        })
        when {
            sourceFailed -> {
                Text("Could not load kanji.")
                TextButton(onClick = {
                    if (mode == TrainingMode.NEW) RecommendedState.initialize(dictionary, dao) else retry++
                }) { Text("Retry") }
            }
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            available == 0 -> Text("No kanji available for this mode.")
            else -> {
                Text("$available kanji available", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = input, onValueChange = { input = it.filter(Char::isDigit) },
                    enabled = available >= 5 && !starting, singleLine = true,
                    label = { Text("Session size") },
                    supportingText = { Text("This session: $count kanji") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { input = count.toString(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) input = count.toString() }
                )
                Slider(
                    value = values.indexOf(count).toFloat(),
                    onValueChange = { input = values[it.roundToInt().coerceIn(values.indices)].toString() },
                    valueRange = 0f..(values.size - 1).coerceAtLeast(1).toFloat(),
                    steps = (values.size - 2).coerceAtLeast(0),
                    enabled = values.size > 1 && !starting,
                    modifier = Modifier.semantics { contentDescription = "Session size: $count kanji" }
                )
                if (available < 5) Text("All $available available kanji will be used.")
            }
        }
        Text("How it works", style = MaterialTheme.typography.titleLarge)
        Text("You’ll be shown the meaning and readings of a kanji. Try to reproduce the kanji — for example, write it on paper or draw it from memory.")
        Text("Then tap Show answer, compare your answer with the kanji shown, and mark it as Correct or Incorrect.")
        Text("At the end, you can review your results and practice the kanji again if you want.")
        if (startFailed) Text("Could not start training. Please try again.", color = MaterialTheme.colorScheme.error)
        Button(
            enabled = !loading && !sourceFailed && !starting && available > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                focus.clearFocus()
                input = count.toString()
                val chosen = selectTrainingPool(source, count)
                val chosenMode = mode
                starting = true
                startFailed = false
                scope.launch {
                    try {
                        val cards = chosen.map { character ->
                            requireNotNull(dictionary.getKanji(character, includeWords = false))
                        }
                        TrainingState.start(chosenMode, cards)
                        onStarted()
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        android.util.Log.e("Training", "Cannot start training", error)
                        startFailed = true
                    } finally { starting = false }
                }
            }
        ) { Text(if (starting) "Starting…" else "Start") }
    }
}

@Composable
private fun TrainingQuestion(session: TrainingSession) {
    val kanji = TrainingState.cards.getValue(requireNotNull(session.currentCharacter))
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(kanji.primaryMeaning ?: "No English meaning", Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge)
                Text((session.questionIndex + 1).toString() + " / " + session.questionOrder.size,
                    Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            TrainingAnswerArea(kanji, session.revealed)
            if (kanji.onReadings.isNotEmpty()) {
                Text("On", style = MaterialTheme.typography.titleMedium)
                Text(kanji.onReadings.take(3).joinToString("  ·  "), style = MaterialTheme.typography.titleLarge)
            }
            if (kanji.kunReadings.isNotEmpty()) {
                Text("Kun", style = MaterialTheme.typography.titleMedium)
                Text(kanji.kunReadings.take(3).joinToString("  ·  "), style = MaterialTheme.typography.titleLarge)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!session.revealed) {
                Button(onClick = { TrainingState.update { it.reveal() } }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Show answer")
                }
            } else {
                OutlinedButton(onClick = { TrainingState.update { it.answer(TrainingResult.INCORRECT) } },
                    Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Incorrect") }
                Button(onClick = { TrainingState.update { it.answer(TrainingResult.CORRECT) } },
                    Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Correct") }
            }
        }
    }
}

/** Dedicated central slot for future hint content; no hint UI or component data in v1. */
@Composable
private fun TrainingAnswerArea(kanji: DictionaryKanji, revealed: Boolean) {
    Surface(Modifier.fillMaxWidth().heightIn(min = 200.dp), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            if (revealed) Text(kanji.character, fontSize = 112.sp, lineHeight = 128.sp)
            else Text("Write the kanji from memory", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrainingResults(session: TrainingSession, dao: UserKanjiStateDao) {
    var choosePractice by rememberSaveable { mutableStateOf(false) }
    val options = session.practiceOptions()
    val saving = TrainingState.saving
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Select any Learning / Known changes below. They are saved only when you tap Finish.",
            style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(session.pool, key = { it }) { character ->
                val kanji = TrainingState.cards.getValue(character)
                val correct = session.lastResults[character] == TrainingResult.CORRECT
                val participated = session.participated(character)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.alpha(if (participated) 1f else 0.45f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (correct) "✓" else "✗", style = MaterialTheme.typography.titleLarge,
                                color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { contentDescription = if (correct) "Correct" else "Incorrect" })
                            Text(character, fontSize = 36.sp)
                            Column(Modifier.weight(1f)) {
                                Text(kanji.primaryMeaning ?: "No English meaning")
                                if (!participated) Text("Previous iteration", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        // De-emphasis is limited to result content; actions remain fully interactive.
                        session.allowedActions(character).forEach { target ->
                            FilterChip(
                                selected = session.pending[character] == target,
                                enabled = !saving,
                                onClick = { TrainingState.update { it.toggleAction(character, target) } },
                                label = { Text(when {
                                    target == LearningState.KNOWN -> "Add to Known"
                                    session.mode == TrainingMode.REVIEW -> "Move to Learning"
                                    else -> "Add to Learning"
                                }) }
                            )
                        }
                    }
                }
            }
        }
        if (TrainingState.saveFailed) {
            Text("Could not save changes. Your session is kept; tap Finish to retry.",
                color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !saving, modifier = Modifier.weight(1f), onClick = {
                if (options.size == 1) TrainingState.update { it.practice(options.single().kind) }
                else choosePractice = true
            }) { Text("Practice again") }
            Button(enabled = !saving, modifier = Modifier.weight(1f),
                onClick = { TrainingState.finish(dao) }) { Text(if (saving) "Saving…" else "Finish") }
        }
    }
    if (choosePractice) {
        AlertDialog(
            onDismissRequest = { choosePractice = false },
            title = { Text("Practice again") },
            text = {
                Column {
                    options.forEach { option ->
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                            choosePractice = false
                            TrainingState.update { it.practice(option.kind) }
                        }) { Text(option.kind.title + " (" + option.characters.size + ")") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosePractice = false }) { Text("Cancel") } }
        )
    }
}