package com.example.kanjidb.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.ui.AppIcons
import com.example.kanjidb.ui.lists.UserDataControl
import com.example.kanjidb.ui.lists.UserDataMessage

/** Stable section identities allow a future optional target without adding navigation levels. */
internal enum class SettingsSection { GENERAL, DATABASE }

@Composable
internal fun SettingsScreen(preferences: AppPreferences, onBack: () -> Unit, onAbout: () -> Unit) {
    val settings by preferences.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(AppIcons.CircleChevronLeft, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                key(SettingsSection.GENERAL) {
                    SectionHeader("General")
                    var themeMenu by rememberSaveable { mutableStateOf(false) }
                    Box {
                        SettingsRow(AppIcons.Paint, "Theme", settings.theme.label, onClick = { themeMenu = true })
                        DropdownMenu(themeMenu, onDismissRequest = { themeMenu = false }) {
                            ThemePreference.entries.forEach { theme ->
                                DropdownMenuItem(text = { Text(theme.label) },
                                    leadingIcon = { Icon(when (theme) {
                                        ThemePreference.SYSTEM -> AppIcons.BrightnessAuto
                                        ThemePreference.LIGHT -> AppIcons.Brightness2
                                        ThemePreference.DARK -> AppIcons.Moon2
                                    }, null) },
                                    trailingIcon = { if (settings.theme == theme) Icon(AppIcons.Check, "Selected") },
                                    onClick = { preferences.setTheme(theme); themeMenu = false })
                            }
                        }
                    }
                    var startupMenu by rememberSaveable { mutableStateOf(false) }
                    Box {
                        SettingsRow(AppIcons.DeviceTabletCog, "Startup screen", settings.startupScreen.label,
                            onClick = { startupMenu = true })
                        DropdownMenu(startupMenu, onDismissRequest = { startupMenu = false }) {
                            StartupScreen.entries.forEach { screen ->
                                DropdownMenuItem(text = { Text(screen.label) },
                                    leadingIcon = { Icon(when (screen) {
                                        StartupScreen.AUTOMATIC -> AppIcons.ZeroConfig
                                        StartupScreen.SEARCH -> AppIcons.Search
                                        StartupScreen.MY_KANJI -> AppIcons.Vocabulary
                                        StartupScreen.TRAINING -> AppIcons.PencilCheck
                                    }, null) },
                                    trailingIcon = { if (settings.startupScreen == screen) Icon(AppIcons.Check, "Selected") },
                                    onClick = { preferences.setStartupScreen(screen); startupMenu = false })
                            }
                        }
                    }
                }
                key(SettingsSection.DATABASE) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    SectionHeader("Database")
                    UserDataControl(snackbar) { exportEnabled, importEnabled, onExport, onImport ->
                        SettingsRow(AppIcons.DatabaseShare, "Export", enabled = exportEnabled, onClick = onExport)
                        SettingsRow(AppIcons.DatabasePlus, "Import", enabled = importEnabled, onClick = onImport)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SettingsRow(AppIcons.InfoSquareRounded, "About", onClick = onAbout)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data ->
            Snackbar { UserDataMessage(data.visuals.message) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String? = null,
    enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}
