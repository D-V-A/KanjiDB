package com.example.kanjidb.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One shared line-count decision for the two-word labels in an equal-width tab row. */
internal fun adaptiveTabLabels(
    labels: List<String>, tabWidthPx: Int, horizontalPaddingPx: Int,
    leadingContentWidthPx: Int, measurer: TextMeasurer, style: TextStyle
): List<String> {
    val textWidth = tabWidthPx - horizontalPaddingPx - leadingContentWidthPx
    val twoLines = labels.any {
        measurer.measure(AnnotatedString(it), style, softWrap = false, maxLines = 1).size.width > textWidth
    }
    return if (twoLines) labels.map { it.replaceFirst(" ", "\n") } else labels
}

/** Retains Material tabs/actions; only coordinates their text wrapping. */
@Composable
internal fun AdaptiveLabelTabs(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    icons: List<ImageVector>? = null, enabled: Boolean = true
) {
    val measurer = rememberTextMeasurer()
    // Material3 Tab's existing label font and 16 dp padding on each side.
    val style = MaterialTheme.typography.titleSmall
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val paddingPx = with(density) { 16.dp.roundToPx() * 2 }
        val leadingPx = if (icons == null) 0 else with(density) {
            AppActionIconSize.roundToPx() + 6.dp.roundToPx()
        }
        val displayedLabels = adaptiveTabLabels(labels, constraints.maxWidth / labels.size,
            paddingPx, leadingPx, measurer, style)
        val lines = if (displayedLabels.any { '\n' in it }) 2 else 1
        TabRow(selectedTabIndex = selectedIndex) {
            displayedLabels.forEachIndexed { index, label ->
                Tab(selected = selectedIndex == index, enabled = enabled,
                    onClick = { onSelect(index) }, text = {
                        val text: @Composable () -> Unit = {
                            Text(label, style = style, textAlign = TextAlign.Center,
                                softWrap = false, minLines = lines, maxLines = lines,
                                overflow = TextOverflow.Visible)
                        }
                        if (icons == null) text() else IconLabel(icons[index], content = text)
                    })
            }
        }
    }
}
