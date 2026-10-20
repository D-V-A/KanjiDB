package com.example.kanjidb.ui.details

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/** One policy for an entire readings/meanings block, using its actual fonts and width. */
internal fun wordTextColumnCount(texts: List<String>, availableWidthPx: Int, gapPx: Int,
    measurer: TextMeasurer, styleFor: (Int,String) -> TextStyle): Int {
    val columnWidth=(availableWidthPx-gapPx)/2
    if(columnWidth<=0) return 1
    val wraps=texts.withIndex().any { (index,text) ->
        val layout=measurer.measure(AnnotatedString(text),styleFor(index,text),
            constraints=Constraints(maxWidth=columnWidth),maxLines=2)
        layout.lineCount>1 || layout.hasVisualOverflow
    }
    return if(wraps) 1 else 2
}
