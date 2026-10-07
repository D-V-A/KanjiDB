package com.example.kanjidb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Existing Custom List action-menu layout, shared with the training-mode menu. */
@Composable
internal fun ActionMenuDialog(
    title: String, onDismiss: () -> Unit, dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(onDismissRequest = { if (dismissEnabled) onDismiss() },
        title = { Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }, confirmButton = { TextButton(enabled = dismissEnabled, onClick = onDismiss) { Text("Cancel") } })
}
