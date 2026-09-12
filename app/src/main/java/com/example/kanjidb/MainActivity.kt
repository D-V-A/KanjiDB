package com.example.kanjidb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.kanjidb.ui.KanjiDbApp
import com.example.kanjidb.ui.theme.KanjiDBTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KanjiDBTheme {
                KanjiDbApp()
            }
        }
    }
}