package com.example.kanjidb.ui.search

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.kanjidb.R
import com.example.kanjidb.ui.DictionaryText

/** One local UI preference, independent of both dictionary and study data. */
internal class SearchHelpPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("search", Context.MODE_PRIVATE)
    val autoShow get() = !preferences.getBoolean("helpAutoShowDisabled", false)
    fun disableAutoShow() { preferences.edit().putBoolean("helpAutoShowDisabled", true).apply() }
}

private object HelpPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
        anchorBounds.right.minus(popupContentSize.width).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
        anchorBounds.bottom.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height))
    )
}

@Composable
internal fun SearchHelpPopup(wordMode: Boolean, showDisableAutoShow: Boolean, onDismiss: () -> Unit, onDisableAutoShow: () -> Unit) {
    Popup(popupPositionProvider = HelpPosition, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Column(Modifier.widthIn(max = 320.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.search_help_examples), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (wordMode) "nitiyoubi" else "niti")
                        Text(if (wordMode) "sunday" else "day")
                        DictionaryText(if (wordMode) "日曜日" else "日")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DictionaryText(if (wordMode) "にちようび" else "にち")
                        DictionaryText(if (wordMode) "ニチヨウビ" else "ニチ")
                    }
                }
                if (showDisableAutoShow) {
                    TextButton(onClick = onDisableAutoShow) { Text(stringResource(R.string.search_help_dont_show)) }
                }
            }
        }
    }
}
