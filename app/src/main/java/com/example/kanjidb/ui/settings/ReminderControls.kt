package com.example.kanjidb.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.ui.AppIcons

/** Shared permission flow for first-Finish offer and Settings, requested only after opt-in. */
@Composable
internal fun rememberEnableReminder(preferences: AppPreferences): () -> Unit {
    val context = LocalContext.current
    var blocked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && PracticeReminder.allowed(context)) {
            preferences.setReminderEnabled(true)
            PracticeReminder.reconcile(context)
        } else blocked = true
    }
    if (blocked) AlertDialog(onDismissRequest = { blocked = false },
        title = { Text("Notifications are disabled") },
        text = { Text("Allow KanjiDB notifications in Android settings, then enable the daily practice reminder here.") },
        dismissButton = { TextButton(onClick = { blocked = false }) { Text("Cancel") } },
        confirmButton = { Button(onClick = {
            blocked = false
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("Open settings") } })
    return {
        PracticeReminder.channel(context)
        when {
            PracticeReminder.allowed(context) -> {
                preferences.setReminderEnabled(true)
                PracticeReminder.reconcile(context)
            }
            Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED ->
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> blocked = true
        }
    }
}

@Composable
internal fun ReminderSettings(preferences: AppPreferences) {
    val settings by preferences.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val enable = rememberEnableReminder(preferences)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Training", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(AppIcons.BellRinging2, null, Modifier.size(24.dp))
        Text("Daily practice reminder", Modifier.weight(1f))
        Switch(checked = settings.reminderEnabled, onCheckedChange = {
            if (it) enable() else {
                preferences.setReminderEnabled(false)
                PracticeReminder.reconcile(context)
            }
        })
    }
    SettingsRow(AppIcons.Clock, "Reminder time", value = String.format(java.util.Locale.getDefault(), "%02d:%02d", settings.reminderMinute / 60, settings.reminderMinute % 60), enabled = settings.reminderEnabled, onClick = {
        TimePickerDialog(context, { _, hour, minute ->
            preferences.setReminderMinute(hour * 60 + minute)
            PracticeReminder.reconcile(context)
        }, settings.reminderMinute / 60, settings.reminderMinute % 60, DateFormat.is24HourFormat(context)).apply {
            setButton(TimePickerDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ -> dialog.dismiss() }
            show()
        }
    })
}

@Composable
internal fun ReminderOffer(preferences: AppPreferences) {
    val enable = rememberEnableReminder(preferences)
    var show by rememberSaveable { mutableStateOf(false) }
    val finished = com.example.kanjidb.ui.training.TrainingState.finishedCount
    LaunchedEffect(finished) {
        if (finished > 0 && !preferences.state.value.reminderOffered) {
            preferences.markReminderOffered()
            if (!preferences.state.value.reminderEnabled) show = true
        }
    }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("Daily practice reminder?") },
        text = { Text("KanjiDB can remind you to practise once a day. You can change the time or turn reminders off in Settings.") },
        dismissButton = { TextButton(onClick = { show = false }) { Text("Not now") } },
        confirmButton = { Button(onClick = { show = false; enable() }) { Text("Enable") } })
}
