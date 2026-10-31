package com.example.kanjidb.ui.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kanjidb.data.dictionary.DictionaryKanji
import com.example.kanjidb.data.user.UserDatabase
import com.example.kanjidb.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun KanjiMeanings(kanji: DictionaryKanji) {
    val row = LocalUserMeanings.current[kanji.character]
    val dao = UserDatabase.getInstance(LocalContext.current).meanings()
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(kanji.character) { mutableStateOf(false) }
    var text by rememberSaveable(kanji.character) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun save(custom: Boolean, value: String?) {
        if (saving) return
        saving = true
        failed = false
        scope.launch {
            try {
                if (custom) dao.setCustom(kanji.character, value) else dao.setPreferred(kanji.character, value)
                editing = false
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failed = true }
            finally { saving = false }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Meanings", style = MaterialTheme.typography.titleSmall)
        Icon(AppIcons.HeartCog, if (row?.custom == null) "Add personal meaning" else "Edit personal meaning",
            Modifier.size(18.dp).clickable(enabled = !saving, role = Role.Button) {
                text = row?.custom.orEmpty(); failed = false; editing = true
            })
    }
    row?.custom?.let {
        DictionaryText(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text("Personal meaning", style = MaterialTheme.typography.labelSmall)
    }
    kanji.meanings.forEach { meaning ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (row?.preferred == null || row.preferred == meaning) {
                val selected = row?.preferred == meaning
                Icon(if (selected) AppIcons.Heart else AppIcons.HeartPlus,
                    if (selected) "Clear preferred meaning" else "Use as preferred meaning",
                    Modifier.size(18.dp).clickable(enabled = !saving, role = Role.Button) {
                        save(false, if (selected) null else meaning)
                    }, tint = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 1f else 0.45f))
            }
            DictionaryText(meaning.standaloneKanjiMeaning(), Modifier.weight(1f))
        }
    }
    if (failed && !editing) Text("Could not save meaning. Please try again.", color = MaterialTheme.colorScheme.error)
    if (editing) AlertDialog(
        onDismissRequest = { if (!saving) editing = false },
        title = { Text("Personal meaning") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your personal meaning does not change the built-in dictionary. While set, it is the primary meaning for this kanji throughout the app.")
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Meaning") }, singleLine = true, enabled = !saving)
                if (row?.custom != null) OutlinedButton(enabled = !saving, onClick = { save(true, null) }) { Text("Delete personal meaning") }
                if (failed) Text("Could not save meaning. Please try again.", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { editing = false }) { Text("Cancel") } },
        confirmButton = { Button(enabled = !saving && text.isNotBlank(), onClick = { save(true, text.trim()) }) { Text("Save") } }
    )
}
