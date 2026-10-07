package com.example.kanjidb.ui.openalpha

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogProperties
import com.example.kanjidb.ui.AppIcons

// Public alpha infrastructure only; independent of dictionary and user.db.
private const val NOTICE_DISMISSED = "openAlphaNoticeDismissed"

@Composable
internal fun OpenAlphaNotice() {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences("open_alpha", Context.MODE_PRIVATE)
    }
    var dismissed by remember(preferences) {
        mutableStateOf(preferences.getBoolean(NOTICE_DISMISSED, false))
    }
    if (dismissed) return

    val message = remember {
        buildAnnotatedString {
            append("This application is under active development. Some features may be unavailable or may not work correctly. Please keep this in mind while using the application.\n\n")
            append("You can provide feedback about the application by tapping the ")
            appendInlineContent("feedback", "feedback icon")
            append(" on the main screen. Any feedback is greatly appreciated.\n\n")
            append("Thank you for taking part in testing the alpha version.\n\n")
            append("This message will not be shown again.")
        }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = {
            Text(
                text = message,
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                inlineContent = mapOf("feedback" to InlineTextContent(
                    Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)
                ) {
                    Icon(AppIcons.MessageReply, contentDescription = null, modifier = Modifier.fillMaxWidth())
                })
            )
        },
        confirmButton = {
            TextButton(onClick = {
                preferences.edit().putBoolean(NOTICE_DISMISSED, true).apply()
                dismissed = true
            }) { Text("Close") }
        }
    )
}
