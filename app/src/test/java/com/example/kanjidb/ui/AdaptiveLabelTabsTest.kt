package com.example.kanjidb.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveLabelTabsTest {
    private val search = listOf("Explore kanji", "Learn Next", "Explore words")
    private val collections = listOf("My Kanji", "My Lists", "Kanji Groups")
    private val style = TextStyle(fontSize = 14.sp)

    private fun measurer(fontScale: Float = 1f) = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(RuntimeEnvironment.getApplication()),
        defaultDensity = Density(1f, fontScale), defaultLayoutDirection = LayoutDirection.Ltr
    )

    private fun widest(labels: List<String>, m: TextMeasurer, textStyle: TextStyle = style) =
        labels.maxOf { m.measure(AnnotatedString(it), textStyle, softWrap = false).size.width }

    @Test fun bothRowsKeepSingleLinesWhenAllFitAtNormalAnd110Percent() {
        for (scale in listOf(1f, 1.1f)) {
            val m = measurer(scale)
            assertEquals(search, adaptiveTabLabels(search, widest(search, m) + 32, 32, 0, m, style))
            assertEquals(collections, adaptiveTabLabels(collections, widest(collections, m) + 58, 32, 26, m, style))
        }
    }

    @Test fun oneLabelThatDoesNotFitSwitchesEveryLabel() {
        val labels = listOf("My Kanji", "My Lists", "Exceptionallylong word")
        val m = measurer()
        val available = widest(labels.take(2), m)
        val displayed = adaptiveTabLabels(labels, available + 32, 32, 0, m, style)
        assertEquals(listOf("My\nKanji", "My\nLists", "Exceptionallylong\nword"), displayed)
    }

    @Test fun myKanjiIconAndTabPaddingAreSubtractedFromAvailableWidth() {
        val m = measurer()
        val width = widest(collections, m)
        assertEquals(collections, adaptiveTabLabels(collections, width + 58, 32, 26, m, style))
        assertTrue(adaptiveTabLabels(collections, width + 57, 32, 26, m, style).all { '\n' in it })
        assertTrue(adaptiveTabLabels(collections, width + 58, 33, 26, m, style).all { '\n' in it })
    }

    @Test fun actualFontScaleChangesTheSharedDecisionWithoutAFixedThreshold() {
        val m = measurer()
        val width = widest(search, m) + 32
        assertEquals(search, adaptiveTabLabels(search, width, 32, 0, m, style))
        assertTrue(adaptiveTabLabels(search, width, 32, 0, measurer(1.1f), style).all { '\n' in it })
    }

    @Test fun actualTypographyAndWidthChangesAreMeasured() {
        val m = measurer()
        val width = widest(search, m) + 32
        assertTrue(adaptiveTabLabels(search, width, 32, 0, m, style.copy(fontSize = 20.sp)).all { '\n' in it })
        assertTrue(adaptiveTabLabels(search, width - 1, 32, 0, m, style).all { '\n' in it })
    }

    @Test fun evenVeryNarrowSlotsOnlyInsertOneBreakBetweenWholeWords() {
        val displayed = adaptiveTabLabels(search, 1, 32, 0, measurer(), style)
        assertEquals(listOf("Explore\nkanji", "Learn\nNext", "Explore\nwords"), displayed)
        displayed.zip(search).forEach { (after, before) ->
            assertEquals(before, after.replace('\n', ' '))
            assertEquals(1, after.count { it == '\n' })
        }
    }
}
