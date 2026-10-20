package com.example.kanjidb.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.ui.AppIcons
import com.example.kanjidb.ui.MultiStateSelector
import com.example.kanjidb.ui.SelectorOption
import com.example.kanjidb.ui.selectorWidth
import com.example.kanjidb.ui.lists.UserDataControl
import com.example.kanjidb.ui.lists.UserDataMessage

/** Stable section identities allow a future optional target without adding navigation levels. */
internal enum class SettingsSection { GENERAL, DATABASE }

internal enum class SelectorRowLayout { FULL_LABEL, SHORT_LABEL, STACKED }

internal fun selectorRowLayout(availableWidth: Int, fullLeadingWidth: Int, shortLeadingWidth: Int?,
    controlWidth: Int, gap: Int): SelectorRowLayout = when {
    fullLeadingWidth + gap + controlWidth <= availableWidth -> SelectorRowLayout.FULL_LABEL
    shortLeadingWidth != null && shortLeadingWidth + gap + controlWidth <= availableWidth -> SelectorRowLayout.SHORT_LABEL
    else -> SelectorRowLayout.STACKED
}

internal val themeSelectorOptions get() = ThemePreference.entries.map {
    SelectorOption(when (it) {
        ThemePreference.SYSTEM -> AppIcons.BrightnessAuto
        ThemePreference.LIGHT -> AppIcons.Brightness2
        ThemePreference.DARK -> AppIcons.Moon2
    }, it.label)
}

internal val startupSelectorOptions get() = StartupScreen.entries.map {
    SelectorOption(when (it) {
        StartupScreen.AUTOMATIC -> AppIcons.ZeroConfig
        StartupScreen.SEARCH -> AppIcons.Search
        StartupScreen.MY_KANJI -> AppIcons.Vocabulary
        StartupScreen.TRAINING -> AppIcons.PencilCheck
    }, it.label)
}

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
                    SelectorSettingsRow(AppIcons.Paint, "Theme", themeSelectorOptions, settings.theme.ordinal) {
                        preferences.setTheme(ThemePreference.entries[it])
                    }
                    SelectorSettingsRow(AppIcons.DeviceTabletCog, "Startup screen", startupSelectorOptions,
                        settings.startupScreen.ordinal, shortLabel = "Startup") { preferences.setStartupScreen(StartupScreen.entries[it]) }
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
private fun SelectorSettingsRow(icon: ImageVector, label: String, options: List<SelectorOption>,
    selectedIndex: Int, shortLabel: String? = null, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    val controlWidth = selectorWidth(options.size).dp
    // Match the 56 dp single-line Database rows: keep the 52 dp control intact.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        val iconAndGap = with(density) { 36.dp.roundToPx() }
        fun leadingWidth(text: String) = iconAndGap +
            measurer.measure(AnnotatedString(text), style, softWrap = false).size.width
        val layout = selectorRowLayout(constraints.maxWidth, leadingWidth(label), shortLabel?.let(::leadingWidth),
            with(density) { controlWidth.roundToPx() }, with(density) { 12.dp.roundToPx() })
        val heading: @Composable () -> Unit = {
            Row(Modifier.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(24.dp))
                Text(if (layout == SelectorRowLayout.SHORT_LABEL) requireNotNull(shortLabel) else label, style = style)
            }
        }
        val selector: @Composable () -> Unit = {
            MultiStateSelector(options, selectedIndex, onSelect, label, Modifier.width(controlWidth))
        }
        if (layout == SelectorRowLayout.STACKED) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                heading()
                Box(Modifier.align(Alignment.End)) { selector() }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            heading()
            selector()
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
