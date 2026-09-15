package com.example.kanjidb.ui.lists

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.kanjidb.ui.LearningState
import com.example.kanjidb.ui.theme.KanjiDBTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MyKanjiScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = MyKanjiMockState()
    private val opened = mutableListOf<String>()

    private fun showScreen(modifier: Modifier = Modifier) {
        compose.setContent {
            KanjiDBTheme {
                MyKanjiScreen(onOpenDetails = { opened.add(it) }, state = state, modifier = modifier)
            }
        }
    }

    @Test
    fun cardTapSelectDeselectAndEmptySpaceCancel() {
        showScreen()
        compose.onNodeWithText("My Lists").assertIsNotEnabled()
        compose.onNodeWithText("Kanji Groups").assertIsNotEnabled()
        compose.onNodeWithText("Learning (30)").performClick()
        compose.onNodeWithText("山").performClick()
        compose.runOnIdle { assertEquals(listOf("山"), opened) }
        compose.onNodeWithText("山").performTouchInput { longClick() }
        compose.onNodeWithText("山").assertIsSelected().performClick()
        compose.onNodeWithText("山").assertIsNotSelected()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Move to Known").assertIsNotEnabled()
        compose.onNodeWithText("Training").assertIsNotEnabled()
        compose.onNodeWithText("Known (30)").assertDoesNotExist()
        compose.onNodeWithTag("my_kanji_content").performTouchInput {
            longClick(Offset(width - 1f, height - 1f))
        }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("山").performClick()
        compose.runOnIdle {
            assertEquals(listOf("山", "山"), opened)
            assertEquals(LearningState.NONE, state.selectionSection)
            assertTrue(state.learningExpanded)
            assertFalse(state.knownExpanded)
        }
        compose.onNodeWithText("山").performTouchInput { longClick() }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(LearningState.NONE, state.selectionSection) }
    }

    @Test
    fun backFromKnownSelectionRestoresBothExpandedSections() {
        showScreen()
        compose.onNodeWithText("Learning (30)").performClick()
        compose.onNodeWithTag("my_kanji_grid")
            .performScrollToNode(hasText("Known (30)"))
        compose.onNodeWithText("Known (30)").performClick()
        compose.onNodeWithTag("my_kanji_grid").performScrollToNode(hasText("一"))
        compose.onNodeWithText("一").performTouchInput { longClick() }
        compose.onNodeWithText("Move to Learning").assertIsDisplayed()
        compose.onNodeWithText("Learning (30)").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(state.learningExpanded)
            assertTrue(state.knownExpanded)
            assertEquals(LearningState.NONE, state.selectionSection)
        }
        compose.onNodeWithTag("my_kanji_grid").performScrollToNode(hasText("山"))
        compose.onNodeWithText("山").assertIsDisplayed()
    }

    @Test
    fun moveAndRemoveUpdateHeadersAndRealReadingIsLoaded() {
        showScreen()
        compose.onNodeWithText("Learning (30)").performClick()
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithText("やま").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("山").performTouchInput { longClick() }
        compose.onNodeWithText("川").performClick()
        compose.onNodeWithText("Move to Known").performClick()
        compose.onNodeWithText("Learning (28)").assertIsDisplayed()
        compose.onNodeWithTag("my_kanji_grid").performScrollToIndex(0)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("水").performTouchInput { longClick() }
        compose.onNodeWithText("Remove from Learning").performClick()
        compose.onNodeWithText("Learning (27)").assertIsDisplayed()
        compose.onNodeWithTag("my_kanji_grid")
            .performScrollToNode(hasText("Known (32)"))
        compose.onNodeWithText("Known (32)").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(state.learning.contains("水"))
            assertTrue(state.known.containsAll(listOf("山", "川")))
            assertTrue(state.selected.isEmpty())
            assertEquals(LearningState.NONE, state.selectionSection)
        }
    }

    @Test
    fun headersStayPinnedCanCollapseAndNextSectionTakesOver() {
        showScreen()
        compose.onNodeWithText("Learning (30)").performClick()
        val grid = compose.onNodeWithTag("my_kanji_grid")
        grid.performScrollToIndex(17)
        val viewport = grid.getUnclippedBoundsInRoot()
        val learning = compose.onNodeWithText("Learning (30)")
        assertEquals(viewport.top.value, learning.getUnclippedBoundsInRoot().top.value, 1f)
        learning.performClick()
        compose.runOnIdle { assertFalse(state.learningExpanded) }
        compose.onNodeWithText("Known (30)").assertIsDisplayed()
        learning.performClick()
        grid.performScrollToNode(hasText("Known (30)"))
        compose.onNodeWithText("Known (30)").performClick()
        grid.performScrollToIndex(40)
        assertEquals(
            viewport.top.value,
            compose.onNodeWithText("Known (30)").getUnclippedBoundsInRoot().top.value,
            1f
        )
        compose.onNodeWithText("Known (30)").performClick()
        compose.runOnIdle { assertFalse(state.knownExpanded) }
    }

    @Test
    fun panelOnlyScrollsWhenNeededAndKeepsBottomCardClear() {
        showScreen(Modifier.width(360.dp).height(600.dp))
        compose.onNodeWithText("Learning (30)").performClick()
        val grid = compose.onNodeWithTag("my_kanji_grid")
        val first = compose.onNodeWithText("山")
        val firstBefore = first.getUnclippedBoundsInRoot()
        first.performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(firstBefore.top.value, first.getUnclippedBoundsInRoot().top.value, 1f)
        val panel = compose.onNodeWithTag("my_kanji_panel").getUnclippedBoundsInRoot()
        val labels = listOf("Remove from Learning", "Move to Known", "Cancel", "Training")
        val buttons = labels.map { compose.onNodeWithText(it).getUnclippedBoundsInRoot() }
        buttons.forEach {
            assertTrue(it.left >= panel.left && it.right <= panel.right)
            assertEquals((buttons.first().top.value + buttons.first().bottom.value) / 2, (it.top.value + it.bottom.value) / 2, 1f)
        }
        compose.onNodeWithText("Cancel").performClick()

        grid.performScrollToIndex(0)
        val card = compose.onNodeWithText("上")
        val before = card.getUnclippedBoundsInRoot()
        assertTrue("Expected overlap: card=$before panel=$panel", before.bottom > panel.top)
        card.performTouchInput { longClick() }
        compose.waitForIdle()
        val after = card.getUnclippedBoundsInRoot()
        val panelAfter = compose.onNodeWithTag("my_kanji_panel").getUnclippedBoundsInRoot()
        val headerAfter = compose.onNodeWithText("Learning (30)").getUnclippedBoundsInRoot()
        assertTrue("Selected card must clear panel", after.bottom.value <= panelAfter.top.value + 1f)
        assertTrue("Selected card must clear sticky header", after.top.value >= headerAfter.bottom.value - 1f)
        assertEquals((after.right - after.left).value, (after.bottom - after.top).value, 1f)
        assertTrue(after.top < before.top)
    }}
