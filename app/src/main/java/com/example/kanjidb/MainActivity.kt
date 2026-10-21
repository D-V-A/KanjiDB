package com.example.kanjidb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.kanjidb.ui.KanjiDbApp
import com.example.kanjidb.ui.theme.KanjiDBTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.ui.settings.AppPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = AppPreferences.getInstance(applicationContext)
        setContent {
            val settings by preferences.state.collectAsStateWithLifecycle()
            KanjiDBTheme(darkTheme = settings.theme.isDark(isSystemInDarkTheme())) {
                KanjiDbApp()
            }
        }
    }
}
