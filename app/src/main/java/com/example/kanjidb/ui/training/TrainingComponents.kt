package com.example.kanjidb.ui.training

import com.example.kanjidb.ui.AppIcons
import com.example.kanjidb.ui.IconLabel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjidb.ui.LearningState

@Composable
internal fun TrainingQuestionControls(revealed: Boolean, onReveal: () -> Unit, onAnswer: (TrainingResult) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!revealed) {
            Button(onClick = onReveal, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Show answer") }
        } else {
            OutlinedButton(onClick = { onAnswer(TrainingResult.INCORRECT) },
                Modifier.weight(1f).heightIn(min = 56.dp)) { IconLabel(AppIcons.Cross) { Text("Incorrect") } }
            Button(onClick = { onAnswer(TrainingResult.CORRECT) },
                Modifier.weight(1f).heightIn(min = 56.dp)) { IconLabel(AppIcons.Check) { Text("Correct") } }
        }
    }
}

@Composable
internal fun TrainingAnswerSurface(content: @Composable BoxScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().aspectRatio(1f), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center, content = content)
    }
}

internal enum class TrainingResultStatus { SUCCESS, PARTIAL, FAILURE, NOT_TESTED }

@Composable
internal fun TrainingResultCard(
    character: String, meaning: String, status: TrainingResultStatus, participated: Boolean,
    detail: String? = null, actions: List<LearningState>, pending: LearningState?,
    saving: Boolean, onAction: (LearningState) -> Unit, reviewing: Boolean = false, wordColors: Boolean = false
) {
    val actionAreaHeight = with(LocalDensity.current) { 52.sp.toDp().coerceAtLeast(56.dp) }
    val singleActionMaxHeight = with(LocalDensity.current) { 32.sp.toDp().coerceAtLeast(32.dp) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp).heightIn(min = actionAreaHeight), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).alpha(if (participated) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val label = when (status) {
                    TrainingResultStatus.SUCCESS -> "Correct"
                    TrainingResultStatus.PARTIAL -> "Partial"
                    TrainingResultStatus.FAILURE -> "Incorrect"
                    TrainingResultStatus.NOT_TESTED -> "Not tested"
                }
                val statusColor = when (status) {
                    TrainingResultStatus.SUCCESS -> if (wordColors) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    TrainingResultStatus.PARTIAL -> Color(0xFFFBC02D)
                    TrainingResultStatus.FAILURE -> MaterialTheme.colorScheme.error
                    TrainingResultStatus.NOT_TESTED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                if (status == TrainingResultStatus.NOT_TESTED) {
                    Text("\u2013", style = MaterialTheme.typography.titleLarge, color = statusColor,
                        modifier = Modifier.semantics { contentDescription = label })
                } else Icon(when (status) {
                    TrainingResultStatus.SUCCESS -> AppIcons.Check
                    TrainingResultStatus.PARTIAL -> AppIcons.AlertCircle
                    else -> AppIcons.Cross
                }, contentDescription = label, tint = statusColor, modifier = Modifier.size(24.dp))
                Text(character, fontSize = 36.sp)
                Column(Modifier.weight(1f)) {
                    Text(meaning)
                    detail?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    if (!participated && status != TrainingResultStatus.NOT_TESTED)
                        Text("Previous iteration", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.fillMaxWidth(0.42f).height(actionAreaHeight),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    actions.forEach { target ->
                        FilterChip(
                            modifier = Modifier.fillMaxWidth().heightIn(
                                max = if (actions.size == 2) (actionAreaHeight - 2.dp) / 2 else singleActionMaxHeight),
                            selected = pending == target, enabled = !saving,
                            onClick = { onAction(target) },
                            label = { Text(when {
                                target == LearningState.KNOWN -> "Add to Known"
                                reviewing -> "Move to Learning"
                                else -> "Add to Learning"
                            }, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrainingResultsFooter(
    practiceLabel: String, practiceEnabled: Boolean = true, onPractice: () -> Unit, onFinish: () -> Unit
) {
    if (TrainingState.saveFailed) {
        Text("Could not save changes. Your session is kept; tap Finish to retry.", color = MaterialTheme.colorScheme.error)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(enabled = !TrainingState.saving && practiceEnabled,
            modifier = Modifier.weight(1f), onClick = onPractice) { IconLabel(AppIcons.Rotate) { Text(practiceLabel) } }
        Button(enabled = !TrainingState.saving, modifier = Modifier.weight(1f), onClick = onFinish) {
            IconLabel(AppIcons.CircleCheck) { Text(if (TrainingState.saving) "Saving…" else "Finish") }
        }
    }
}

/** Shared repeat selection for both training modes; empty subsets remain visibly disabled. */
@Composable
internal fun TrainingRepeatMenu(
    options: List<Pair<PracticeKind, Int>>, onDismiss: () -> Unit, onSelect: (PracticeKind) -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Repeat") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (kind, count) ->
                OutlinedButton(enabled = count > 0, modifier = Modifier.fillMaxWidth(), onClick = { onSelect(kind) }) {
                    IconLabel(when (kind) {
                        PracticeKind.ALL -> AppIcons.Refresh
                        PracticeKind.CURRENT -> AppIcons.RefreshDot
                        PracticeKind.MISTAKES -> AppIcons.RefreshAlert
                    }) { Text(when (kind) {
                        PracticeKind.CURRENT -> "Current iteration"
                        PracticeKind.ALL -> "Entire session"
                        PracticeKind.MISTAKES -> "Mistakes only"
                    } + " ($count)") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
