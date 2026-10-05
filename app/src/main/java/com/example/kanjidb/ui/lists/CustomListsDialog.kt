package com.example.kanjidb.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.example.kanjidb.data.user.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CustomListNameDialog(
    title: String, initialName: String = "", onDismiss: () -> Unit,
    onApply: suspend (String) -> String?
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                enabled = !saving, label = { Text("Name") }, isError = error != null,
                supportingText = { error?.let { Text(it) } })
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        error = onApply(name)
                        if (error == null) onDismiss()
                    } catch (e: CancellationException) { throw e }
                    catch (e: IllegalArgumentException) { error = e.message }
                    catch (e: Exception) { error = "Could not save the list. Please try again." }
                    finally { saving = false }
                }
            }) { Text("Apply") }
        }
    )
}

@Composable
internal fun CustomListsDialog(dao: CustomListDao, characters: List<String>, onDismiss: () -> Unit) {
    val draftSaver = remember {
        listSaver<CustomListDraft?, String>(
            save = { it?.save().orEmpty() }, restore = { if (it.isEmpty()) null else CustomListDraft.restore(it) })
    }
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf<CustomListDraft?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(dao, retry) {
        if (draft != null) return@LaunchedEffect
        try { draft = CustomListDraft(dao.getLists(), characters.distinct()); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Could not load lists. Please try again." }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Custom Lists", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = draft
                if (current == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (current != null) {
                    // revision makes the small mutable draft observable without duplicating its logic.
                    val targets = remember(current, revision) { current.targets }
                    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (current.targets.isEmpty()) item { Text("You don't have any lists yet.") }
                            itemsIndexed(targets) { index, target ->
                                val state = when (current.state(index)) {
                                    ListMembershipState.UNCHECKED -> ToggleableState.Off
                                    ListMembershipState.PARTIAL -> ToggleableState.Indeterminate
                                    ListMembershipState.CHECKED -> ToggleableState.On
                                }
                                CustomListMembershipButton(
                                    label = target.name + " (" + target.kanjiCount + ")",
                                    state = state, enabled = !saving,
                                    onClick = { current.tap(index); revision++ })
                            }
                    }
                    TextButton(enabled = !saving, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = { creating = true }) { Text("+ Create new list") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (current == null && error != null) TextButton(onClick = { retry++ }) { Text("Retry") }
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(enabled = draft != null && !saving, onClick = {
                val current = draft ?: return@TextButton
                saving = true
                error = null
                scope.launch {
                    try {
                        dao.applyChanges(current.selected, current.targets)
                        onDismiss()
                    } catch (e: CancellationException) { throw e }
                    catch (e: IllegalArgumentException) { error = e.message }
                    catch (e: Exception) { error = "Could not save lists. Please try again." }
                    finally { saving = false }
                }
            }) { Text("Apply") }
        }
    )
    if (creating && draft != null) CustomListNameDialog("Create new list", onDismiss = { creating = false },
        onApply = { name -> requireNotNull(draft).create(name).also { if (it == null) revision++ } })
}

/** Tri-state belongs to the entire container; stripes are decorative and never cover the text. */
@Composable
private fun CustomListMembershipButton(label: String, state: ToggleableState, enabled: Boolean, onClick: () -> Unit) {
    val selected = MaterialTheme.colorScheme.secondaryContainer
    val unselected = MaterialTheme.colorScheme.surfaceContainer
    val foreground = if (state == ToggleableState.Off) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .triStateToggleable(state, enabled = enabled, role = Role.Checkbox, onClick = onClick),
        shape = shape,
        color = if (state == ToggleableState.On) selected else unselected,
        contentColor = foreground,
        border = BorderStroke(1.dp, if (state == ToggleableState.Off) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.primary)
    ) {
        Box(Modifier.then(if (state == ToggleableState.Indeterminate) Modifier.drawWithCache {
            val width = 10.dp.toPx()
            onDrawBehind {
                // x+y is constant along each 45-degree line; wide alternating bands fill the clipped shape.
                var offset = -size.height
                while (offset < size.width) {
                    drawLine(selected, Offset(offset, size.height), Offset(offset + size.height, 0f), strokeWidth = width)
                    offset += width * 2f * kotlin.math.sqrt(2f)
                }
            }
        } else Modifier).padding(horizontal = 12.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
