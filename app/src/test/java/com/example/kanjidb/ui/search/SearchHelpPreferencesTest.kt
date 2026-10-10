package com.example.kanjidb.ui.search

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SearchHelpPreferencesTest {
    @Test fun disabledAutoShowSurvivesNewPreferenceInstance() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("search", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = SearchHelpPreferences(context)
        assertTrue(settings.autoShow)
        settings.disableAutoShow()
        assertFalse(SearchHelpPreferences(context).autoShow)
    }
}
