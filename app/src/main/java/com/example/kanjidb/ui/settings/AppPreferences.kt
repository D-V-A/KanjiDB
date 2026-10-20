package com.example.kanjidb.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class ThemePreference(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

internal enum class StartupScreen(val label: String) {
    AUTOMATIC("Automatic"), SEARCH("Search"), MY_KANJI("My Kanji"), TRAINING("Training")
}

internal enum class MyKanjiTab(val page: Int) {
    MY_KANJI(0), MY_LISTS(1), KANJI_GROUPS(2)
}

internal data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val startupScreen: StartupScreen = StartupScreen.AUTOMATIC,
    val lastMyKanjiTab: MyKanjiTab = MyKanjiTab.KANJI_GROUPS
)

/** Local configuration, independent of both databases and the external .kdb format. */
internal class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(read())
    val state = current.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> current.value = read() }

    init { preferences.registerOnSharedPreferenceChangeListener(listener) }

    private fun read() = AppSettings(
        enumValue(preferences.getString("theme", null), ThemePreference.SYSTEM),
        enumValue(preferences.getString("startupScreen", null), StartupScreen.AUTOMATIC),
        enumValue(preferences.getString("lastMyKanjiTab", null), MyKanjiTab.KANJI_GROUPS)
    )

    fun setTheme(theme: ThemePreference) = put("theme", theme.name)
    fun setStartupScreen(screen: StartupScreen) = put("startupScreen", screen.name)
    fun setLastMyKanjiTab(tab: MyKanjiTab) = put("lastMyKanjiTab", tab.name)
    private fun put(key: String, value: String) {
        if (preferences.getString(key, null) == value) return
        preferences.edit().putString(key, value).apply()
        current.value = read()
    }
    internal fun close() = preferences.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        @Volatile private var instance: AppPreferences? = null
        fun getInstance(context: Context): AppPreferences = instance ?: synchronized(this) {
            instance ?: AppPreferences(context).also { instance = it }
        }
        private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == value } ?: fallback
    }
}
