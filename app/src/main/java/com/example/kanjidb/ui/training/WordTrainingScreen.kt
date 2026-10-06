package com.example.kanjidb.ui.training

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.standaloneKanjiMeaning
import kotlin.math.roundToInt

@Composable
internal fun WordTrainingOptions(settings: WordTrainingSettings, enabled: Boolean, onChange: (WordTrainingSettings) -> Unit) {
    var input by rememberSaveable { mutableStateOf(settings.targetCoverage.toString()) }
    val focus = LocalFocusManager.current
    OutlinedTextField(value = input, onValueChange = {
        input = it.filter(Char::isDigit)
        onChange(settings.copy(targetCoverage = normalizedCoverage(input)))
    }, enabled = enabled, singleLine = true, label = { Text("Target coverage") },
        supportingText = { Text("Goal: ${settings.targetCoverage} different words per pool kanji") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { input = settings.targetCoverage.toString(); focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) input = settings.targetCoverage.toString() })
    Slider(value = settings.targetCoverage.toFloat(), onValueChange = {
        val value = it.roundToInt().coerceIn(1, 5)
        input = value.toString()
        onChange(settings.copy(targetCoverage = value))
    }, valueRange = 1f..5f, steps = 3, enabled = enabled)
    Text("Coverage is a goal, not a guarantee. The dictionary, word length filters and limited distinct words may leave some kanji below the target.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Prompt", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WordPrompt.entries.forEach { prompt ->
            FilterChip(selected = settings.prompt == prompt, enabled = enabled,
                onClick = { onChange(settings.copy(prompt = prompt)) }, label = { Text(prompt.title) })
        }
    }
    Text("Word length", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WordLength.entries.forEach { length ->
            FilterChip(selected = settings.length == length, enabled = enabled,
                onClick = { onChange(settings.copy(length = length)) }, label = { Text(length.title) })
        }
    }
    Text("Length counts kanji, including repeated kanji. Kana and other characters do not count.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (settings.length.shortPriorityMaximum == 0) {
        Text("This length restriction may make your target coverage unreachable.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "How it works ▴" else "How it works ▾") }
            if (expanded) {
                listOf(
                    "Words are selected from the kanji pool you choose.",
                    "Pool kanji and Known kanji are hidden. Unknown kanji outside the pool stay visible as context.",
                    "Kana and other characters stay visible.",
                    "Choose the translation, reading, or both. Try to reproduce the hidden kanji on paper or from memory.",
                    "Reveal the answer, then mark the whole word Correct or Incorrect.",
                    "Results are estimated per hidden kanji across the whole session, including repeats.",
                    "Target coverage is a goal: some kanji may appear fewer times because of the dictionary and filters."
                ).forEach { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
internal fun WordTrainingQuestion(session: WordTrainingSession) {
    val word = requireNotNull(session.currentWord)
    val prompt = session.plan.settings.prompt
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prompt != WordPrompt.READING) {
                        Text("Translation", style = MaterialTheme.typography.titleMedium)
                        Text(word.primaryMeaning?.standaloneKanjiMeaning() ?: "No meaning",
                            style = MaterialTheme.typography.headlineLarge)
                    }
                    if (prompt != WordPrompt.TRANSLATION) {
                        Text("Reading", style = MaterialTheme.typography.titleMedium)
                        Text(word.word.reading, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text("${session.questionIndex + 1} / ${session.questionOrder.size}",
                    Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            TrainingAnswerSurface {
                Text(if (session.revealed) word.word.written else maskedWord(word, session.plan.pool.toSet(), session.plan.known),
                    modifier = Modifier.fillMaxWidth(), fontSize = 40.sp, lineHeight = 48.sp, textAlign = TextAlign.Center)
            }
        }
        TrainingQuestionControls(session.revealed,
            onReveal = { TrainingState.updateWords { it.reveal() } },
            onAnswer = { result -> TrainingState.updateWords { it.answer(result) } })
    }
}

@Composable
internal fun WordTrainingResults(session: WordTrainingSession, dao: com.example.kanjidb.data.user.UserKanjiStateDao) {
    var choosePractice by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LearningState.LEARNING, LearningState.KNOWN).forEach { target ->
                if (session.bulkTargets(target).isNotEmpty()) {
                    item(key = "bulk_$target") {
                        OutlinedButton(enabled = !TrainingState.saving, modifier = Modifier.fillMaxWidth(),
                            onClick = { TrainingState.updateWords { it.withBulkActions(target) } }) {
                            Text(if (target == LearningState.LEARNING) "Add all mistakes to Learning"
                                else "Add all successful to Known")
                        }
                    }
                }
            }
            item(key = "explanation") {
                Text("Results show which kanji appeared in words you made mistakes on. They are intended as a guide, not a definitive assessment. Changes are saved only on Finish.",
                    style = MaterialTheme.typography.bodyMedium)
                if (session.plan.belowTarget > 0) Text(
                    "${session.plan.belowTarget} pool kanji fell below the target coverage (${session.plan.uncovered.size} not tested).",
                    style = MaterialTheme.typography.bodySmall)
            }
            items(session.resultCharacters, key = { it }) { character ->
                val tally = session.tallies[character] ?: WordKanjiTally()
                val status = when (tally.status) {
                    WordKanjiStatus.SUCCESS -> TrainingResultStatus.SUCCESS
                    WordKanjiStatus.PARTIAL -> TrainingResultStatus.PARTIAL
                    WordKanjiStatus.FAILURE -> TrainingResultStatus.FAILURE
                    null -> TrainingResultStatus.NOT_TESTED
                }
                val meaning = TrainingState.wordCards[character]?.primaryMeaning?.takeIf { it.isNotBlank() }
                    ?.standaloneKanjiMeaning() ?: "No English meaning"
                TrainingResultCard(character, meaning, status, session.participated(character),
                    detail = "Shown: ${tally.shownCount}",
                    actions = session.allowedActions(character), pending = session.pending[character],
                    saving = TrainingState.saving, wordColors = true,
                    onAction = { target -> TrainingState.updateWords { it.toggleAction(character, target) } })
            }
        }
        TrainingResultsFooter("Repeat",
            onPractice = { choosePractice = true },
            onFinish = { TrainingState.finishWords(dao) })
    }
    if (choosePractice) {
        TrainingRepeatMenu(session.practiceOptions().map { it.first to it.second.size }, onDismiss = { choosePractice = false }) { kind ->
            choosePractice = false
            TrainingState.updateWords { it.practice(kind) }
        }
    }
}
