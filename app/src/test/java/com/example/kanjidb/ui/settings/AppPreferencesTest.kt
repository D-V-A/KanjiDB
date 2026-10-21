package com.example.kanjidb.ui.settings

import android.content.Context
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AppPreferencesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val instances = mutableListOf<AppPreferences>()
    private fun preferences() = AppPreferences(context).also { instances += it }
    @Before fun clear() { context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun close() { instances.forEach { it.close() } }

    @Test fun defaultsAreSystemAutomaticAndGroups() {
        assertEquals(AppSettings(ThemePreference.SYSTEM, StartupScreen.AUTOMATIC, MyKanjiTab.KANJI_GROUPS), preferences().state.value)
    }
    @Test fun eachThemePersistsAndUpdatesExistingState() {
        val current = preferences()
        for (theme in listOf(ThemePreference.LIGHT, ThemePreference.DARK, ThemePreference.SYSTEM)) {
            current.setTheme(theme)
            assertEquals(theme, current.state.value.theme)
            assertEquals(theme, preferences().state.value.theme)
        }
    }
    @Test fun everyStartupChoicePersistsWithoutChangingOtherPreferences() {
        val current = preferences()
        current.setTheme(ThemePreference.DARK)
        current.setLastMyKanjiTab(MyKanjiTab.MY_LISTS)
        for (screen in listOf(StartupScreen.SEARCH, StartupScreen.MY_KANJI, StartupScreen.TRAINING, StartupScreen.AUTOMATIC)) {
            current.setStartupScreen(screen)
            assertEquals(screen, preferences().state.value.startupScreen)
            assertEquals(ThemePreference.DARK, current.state.value.theme)
            assertEquals(MyKanjiTab.MY_LISTS, current.state.value.lastMyKanjiTab)
        }
    }
    @Test fun lastCollectionTabPersistsForAllTabs() {
        for (tab in MyKanjiTab.entries) {
            preferences().setLastMyKanjiTab(tab)
            assertEquals(tab, preferences().state.value.lastMyKanjiTab)
        }
    }
    @Test fun externalPreferenceChangesAreObservedAndUnknownValuesUseDefaults() {
        val current = preferences()
        val stored = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        stored.edit().putString("theme", "LIGHT").commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ThemePreference.LIGHT, current.state.value.theme)
        stored.edit().putString("theme", "unknown").putString("startupScreen", "unknown")
            .putString("lastMyKanjiTab", "unknown").commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AppSettings(), current.state.value)
    }
    @Test fun themeResolutionFollowsSystemOnlyForSystemChoice() {
        assertFalse(ThemePreference.SYSTEM.isDark(false))
        assertTrue(ThemePreference.SYSTEM.isDark(true))
        assertFalse(ThemePreference.LIGHT.isDark(true))
        assertTrue(ThemePreference.DARK.isDark(false))
    }
    @Test fun configurationDoesNotCreateDatabasesOrModifySearchPreferences() {
        val databases = context.databaseList().toSet()
        val search = context.getSharedPreferences("search", Context.MODE_PRIVATE)
        search.edit().putBoolean("helpAutoShowDisabled", true).commit()
        preferences().setTheme(ThemePreference.DARK)
        preferences().setStartupScreen(StartupScreen.TRAINING)
        assertTrue(search.getBoolean("helpAutoShowDisabled", false))
        assertEquals(databases, context.databaseList().toSet())
    }
}
