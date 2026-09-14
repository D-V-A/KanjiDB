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
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.dictionary.DictionaryWordDetails
import kotlinx.coroutines.CancellationException

@Composable
fun WordDetailsScreen(entryId: Long, written: String, sourceKanji: String, modifier: Modifier = Modifier) {
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
    val details = word
    if (loading || failed || details == null) {
        Text(
            stringResource(when {
                loading -> R.string.details_loading
                failed -> R.string.details_load_error
                else -> R.string.word_not_found
            }),
            modifier.padding(16.dp)
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(details.written, style = MaterialTheme.typography.displaySmall)
                if (details.alternativeWrittenForms.isNotEmpty()) {
                    Text(
                        details.alternativeWrittenForms.joinToString(" \u00B7 "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.word_readings), style = MaterialTheme.typography.titleLarge)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.word_preferred_reading),
                            style = MaterialTheme.typography.labelSmall)
                        Text(details.preferredReading, style = MaterialTheme.typography.titleLarge)
                    }
                    val otherReadings = details.readings.filter { it != details.preferredReading }
                    if (otherReadings.isNotEmpty()) {
                        Text(
                            otherReadings.joinToString(" \u00B7 "),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.details_meanings), style = MaterialTheme.typography.titleLarge)
        }
        if (details.meaningGroups.isEmpty()) {
            item { Text(stringResource(R.string.word_no_meanings)) }
        }
        details.meaningGroups.forEach { (language, meanings) ->
            if (language != "en") {
                item { Text(language, style = MaterialTheme.typography.titleSmall) }
            }
            items(meanings) { meaning ->
                Text(meaning, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
