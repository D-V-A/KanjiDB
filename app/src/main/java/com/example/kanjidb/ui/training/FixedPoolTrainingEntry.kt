package com.example.kanjidb.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Shared by selected kanji and entire lists. Cancelling never mutates the origin. */
@Composable
internal fun FixedPoolTrainingEntry(pool: List<String>, onDismiss: () -> Unit, onAccepted: () -> Unit) {
    var confirmed by rememberSaveable(pool) { mutableStateOf(pool.size <= 50) }
    var starting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dictionary = remember(context) { DictionaryDatabase(context) }
    val scope = rememberCoroutineScope()
    var availablePool by remember(pool) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(dictionary, pool) {
        try {
            availablePool = availableTrainingPool(pool, dictionary.getKanjiGroups().mapTo(hashSetOf()) { it.character })
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    if (!confirmed) {
        TrainingConfirmationDialog("Large kanji pool (${pool.size} kanji)",
            "You selected a large number of kanji. This may result in a long training session. Continue?",
            confirmLabel = "Continue", onDismiss = onDismiss, onConfirm = { confirmed = true })
    } else {
        com.example.kanjidb.ui.ActionMenuDialog("Training", onDismiss, dismissEnabled = !starting) {
            availablePool?.let { filtered ->
                missingTrainingWarning(pool, filtered)?.let { Text(it) }
            }
            OutlinedButton(enabled = !starting && !availablePool.isNullOrEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                starting = true
                failed = false
                scope.launch {
                    try {
                        val cards = availablePool.orEmpty().mapNotNull { character ->
                            dictionary.getKanji(character, includeWords = false)
                        }
                        if (cards.isEmpty()) { availablePool = emptyList(); return@launch }
                        // Explicit pools use existing My Lists result actions, independent of ownership.
                        TrainingState.start(TrainingMode.MY_LISTS, cards)
                        onAccepted()
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        android.util.Log.e("Training", "Cannot start fixed-pool training", error)
                        failed = true
                    } finally { starting = false }
                }
            }) { Text("Kanji Training") }
            OutlinedButton(enabled = !starting && !availablePool.isNullOrEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                TrainingState.prepareFixedWords(availablePool.orEmpty())
                onAccepted()
            }) { Text("Word Training") }
            if (starting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (availablePool == null && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (availablePool?.isEmpty() == true) Text("No kanji available for training in the current dictionary.")
            if (failed) Text("Could not start training. Please try again.", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Both independent large-pool and generated-word warnings use the same Cancel-left layout. */
@Composable
internal fun TrainingConfirmationDialog(
    title: String, message: String, confirmLabel: String = "Confirm",
    onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } })
}
