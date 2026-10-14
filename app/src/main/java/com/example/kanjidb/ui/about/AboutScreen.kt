package com.example.kanjidb.ui.about

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val databaseVersion by produceState<Int?>(null, context) {
        value = try {
            DictionaryDatabase(context).getDatabaseVersion()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.about_back))
            }
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.about_version, versionName))
        Text(databaseVersion?.let { stringResource(R.string.about_database_version, it) }
            ?: stringResource(R.string.about_database_version_unknown))
        Text(stringResource(R.string.about_developer))
        Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.about_kanjidic))
        Text(stringResource(R.string.about_jmdict))
        Text(stringResource(R.string.about_kanjivg))
        Text(stringResource(R.string.about_tabler_icons))
    }
}
