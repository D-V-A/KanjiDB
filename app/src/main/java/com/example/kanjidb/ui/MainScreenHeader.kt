package com.example.kanjidb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared geometry keeps Settings at the same top-right position on all main screens. */
@Composable
internal fun MainScreenHeader(
    title: String, onOpenSettings: () -> Unit, enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        IconButton(enabled = enabled, onClick = onOpenSettings) { Icon(AppIcons.Settings2, "Settings") }
    }
}
