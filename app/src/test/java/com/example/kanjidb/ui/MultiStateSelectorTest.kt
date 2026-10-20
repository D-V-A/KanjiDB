package com.example.kanjidb.ui

import com.example.kanjidb.ui.settings.*
import org.junit.Assert.*
import org.junit.Test

class MultiStateSelectorTest {
    @Test fun directTapSlotsMatchNearestStateForThreeAndFourOptions() {
        for (count in listOf(3, 4)) {
            val positions = selectorSnapPositions(count)
            val slotWidth = selectorWidth(count) / count
            // Equal native selectable slots must coincide with nearest-center hit
            // areas, including taps away from the icon itself. Exact ties allow either.
            for (x in 0 until selectorWidth(count).toInt()) {
                val coordinate = x + 0.5f
                val slot = (coordinate / slotWidth).toInt()
                assertEquals(slot, nearestSelectorIndex(coordinate, positions))
            }
            assertEquals(0, nearestSelectorIndex(0f, positions))
            assertEquals(count - 1, nearestSelectorIndex(selectorWidth(count), positions))
        }
    }

    @Test fun positionsAreEvenAndLeaveRoomForTheLockedBall() {
        for (count in listOf(2, 3, 4)) {
            val positions = selectorSnapPositions(count)
            assertEquals(count, positions.size)
            assertEquals(22f, positions.first(), 0.001f)
            assertEquals(selectorWidth(count) - 22f, positions.last(), 0.001f)
            val spacing = positions[1] - positions[0]
            assertEquals(44f, spacing, 0.001f)
            assertTrue(spacing > 43f) // Settled ball; enlarged icons are smaller still.
            val previousSpacing = (8f + 48f * count - 44f) / (count - 1)
            assertTrue(spacing < previousSpacing)
            positions.zipWithNext().forEach { (left, right) -> assertEquals(spacing, right - left, 0.001f) }
        }
    }

    @Test fun nearestSnapClampsEdgesAndResolvesIntermediatePositions() {
        for (count in listOf(2, 3, 4)) {
            val positions = selectorSnapPositions(count)
            assertEquals(0, nearestSelectorIndex(-100f, positions))
            assertEquals(count - 1, nearestSelectorIndex(selectorWidth(count) + 100f, positions))
            positions.forEachIndexed { index, center -> assertEquals(index, nearestSelectorIndex(center, positions)) }
            positions.zipWithNext().forEachIndexed { index, (left, right) ->
                assertEquals(index, nearestSelectorIndex(left + (right - left) * 0.49f, positions))
                assertEquals(index + 1, nearestSelectorIndex(left + (right - left) * 0.51f, positions))
            }
        }
    }

    @Test fun widthGrowsByOneSlotPerOption() {
        assertEquals(88f, selectorWidth(2), 0f)
        assertEquals(132f, selectorWidth(3), 0f)
        assertEquals(176f, selectorWidth(4), 0f)
        assertEquals(44f, selectorWidth(5) - selectorWidth(4), 0f)
    }

    @Test fun rowPrefersFullLabelThenShortLabelThenStacked() {
        assertEquals(SelectorRowLayout.FULL_LABEL, selectorRowLayout(308, 120, 75, 176, 12))
        assertEquals(SelectorRowLayout.SHORT_LABEL, selectorRowLayout(307, 120, 75, 176, 12))
        assertEquals(SelectorRowLayout.SHORT_LABEL, selectorRowLayout(263, 120, 75, 176, 12))
        assertEquals(SelectorRowLayout.STACKED, selectorRowLayout(262, 120, 75, 176, 12))
    }

    @Test fun themeNeverUsesAShortenedLabel() {
        assertEquals(SelectorRowLayout.FULL_LABEL, selectorRowLayout(224, 80, null, 132, 12))
        assertEquals(SelectorRowLayout.STACKED, selectorRowLayout(223, 80, null, 132, 12))
    }

    @Test fun themeOptionsKeepPreferenceOrderAndSharedIcons() {
        assertEquals(listOf("System", "Light", "Dark"), themeSelectorOptions.map { it.label })
        assertEquals(listOf(AppIcons.BrightnessAuto, AppIcons.Brightness2, AppIcons.Moon2), themeSelectorOptions.map { it.icon })
        ThemePreference.entries.forEach { assertEquals(it.label, themeSelectorOptions[it.ordinal].label) }
    }

    @Test fun startupOptionsKeepPreferenceOrderAndSharedIcons() {
        assertEquals(listOf("Automatic", "Search", "My Kanji", "Training"), startupSelectorOptions.map { it.label })
        assertEquals(listOf(AppIcons.ZeroConfig, AppIcons.Search, AppIcons.Vocabulary, AppIcons.PencilCheck),
            startupSelectorOptions.map { it.icon })
        StartupScreen.entries.forEach { assertEquals(it.label, startupSelectorOptions[it.ordinal].label) }
    }
}
