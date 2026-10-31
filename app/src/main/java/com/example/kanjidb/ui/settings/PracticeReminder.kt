package com.example.kanjidb.ui.settings

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kanjidb.MainActivity
import com.example.kanjidb.R
import java.time.ZonedDateTime

/** One local-calendar alarm at a time; no network, exact-alarm access or background service. */
internal object PracticeReminder {
    const val ACTION = "com.example.kanjidb.PRACTICE_REMINDER"
    const val CHANNEL = "daily_practice"
    private const val ID = 710
    fun nextTime(now: ZonedDateTime, minute: Int): ZonedDateTime {
        val today = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1)
            .atTime(minute / 60, minute % 60).atZone(now.zone)
    }
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily practice", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(context, ID,
        Intent(context, PracticeReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun reconcile(context: Context, settings: AppSettings = AppPreferences.getInstance(context).state.value) {
        channel(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pending(context)
        alarms.cancel(pending)
        if (!settings.reminderEnabled || !allowed(context)) {
            context.getSystemService(NotificationManager::class.java).cancel(ID)
            return
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            nextTime(ZonedDateTime.now(), settings.reminderMinute).toInstant().toEpochMilli(), pending)
    }
    fun deliver(context: Context) {
        val preferences = AppPreferences.getInstance(context)
        val settings = preferences.state.value
        val today = ZonedDateTime.now().toLocalDate().toString()
        if (settings.reminderEnabled && allowed(context) && settings.lastReminderDate != today) {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                context.getSystemService(NotificationManager::class.java).notify(ID,
                    NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_practice_reminder)
                        .setContentTitle("Daily practice").setContentText("Take a moment to practise your kanji.")
                        .setContentIntent(open).setAutoCancel(true).build())
                preferences.setLastReminderDate(today)
            } catch (_: SecurityException) { /* Permission may change between check and delivery. */ }
        }
        reconcile(context)
    }
}

class PracticeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PracticeReminder.ACTION) PracticeReminder.deliver(context)
        else if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) PracticeReminder.reconcile(context)
    }
}
