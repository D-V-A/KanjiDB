package com.example.kanjidb.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Capitalize only the first letter, preserving leading punctuation and the rest of the gloss. */
internal fun String.standaloneKanjiMeaning(): String {
    val index = indexOfFirst { it.isLetter() }
    if (index < 0) return this
    return replaceRange(index, index + 1, this[index].uppercaseChar().toString())
}

/** Keep selection local to dictionary text, leaving surrounding card clicks and controls intact. */
@Composable
internal fun DictionaryText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current
) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            overflow = overflow,
            maxLines = maxLines,
            style = style
        )
    }
}
