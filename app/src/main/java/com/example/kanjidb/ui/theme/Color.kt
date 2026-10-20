package com.example.kanjidb.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/** Material error red and an explicit accessible orange pair for missing user-owned kanji. */
@androidx.compose.runtime.Composable
internal fun missingKanjiColors(selected: Boolean): Pair<Color, Color> {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    if (!selected) return scheme.errorContainer to scheme.onErrorContainer
    return if (scheme.surface.luminance() < 0.5f)
        Color(0xFF733500) to Color(0xFFFFDCC2)
    else Color(0xFFFFDCC2) to Color(0xFF311300)
}
