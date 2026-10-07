package com.example.kanjidb.ui.openalpha

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R

private const val GOOGLE_FORMS_URL = "https://docs.google.com/forms/d/e/1FAIpQLScgjo8oD7oEJKHDA6KGZRYXYDD2YlRfHew_6Gsmsb2BCILP9Q/viewform?usp=header"
private const val GITHUB_URL = "https://github.com/D-V-A/KanjiDB"

@Composable
internal fun FeedbackScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No application available to open this link.", Toast.LENGTH_SHORT).show()
        }
    }
    // Match About's header, scrolling, typography and spacing without changing About.
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.about_back))
            }
            Text("Feedback", style = MaterialTheme.typography.headlineMedium)
        }
        Text("Thank you for taking part in testing KanjiDB.\n\nTo provide feedback, you can use Google Forms or visit the project's GitHub page.\n\nAny feedback is greatly appreciated.")
        TextButton(onClick = { openLink(GOOGLE_FORMS_URL) }) { Text("Google Forms") }
        TextButton(onClick = { openLink(GITHUB_URL) }) { Text("GitHub project") }
    }
}
