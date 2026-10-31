package com.example.kanjidb.ui.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import com.example.kanjidb.ui.training.trainingShowsStrokes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PracticeReminderTest {
    @Test fun nextAlarmUsesLocalCalendarAndNeverSchedulesInPast() {
        val now = ZonedDateTime.parse("2026-10-31T18:00:00+01:00[Europe/Belgrade]")
        assertEquals(19, PracticeReminder.nextTime(now, 1140).hour)
        assertEquals(now.toLocalDate(), PracticeReminder.nextTime(now, 1140).toLocalDate())
        assertEquals(now.toLocalDate().plusDays(1), PracticeReminder.nextTime(now, 1080).toLocalDate())
        val beforeDst = ZonedDateTime.parse("2026-10-24T20:00:00+02:00[Europe/Belgrade]")
        val next = PracticeReminder.nextTime(beforeDst, 19 * 60)
        assertEquals(19, next.hour)
        assertEquals(1 * 3600, next.offset.totalSeconds)
        assertEquals(24L, java.time.Duration.between(beforeDst, next).toHours())
    }
    @Test fun enablingReschedulingDisablingKeepOnlyOneAlarm() {
        val context = RuntimeEnvironment.getApplication()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        PracticeReminder.reconcile(context, AppSettings(reminderEnabled = true, reminderMinute = 600))
        assertEquals(1, alarms.scheduledAlarms.size)
        val first = alarms.scheduledAlarms.single().triggerAtTime
        PracticeReminder.reconcile(context, AppSettings(reminderEnabled = true, reminderMinute = 720))
        assertEquals(1, alarms.scheduledAlarms.size)
        assertNotEquals(first, alarms.scheduledAlarms.single().triggerAtTime)
        PracticeReminder.reconcile(context, AppSettings(reminderEnabled = false))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
    @Test fun receiverRestoresEnabledScheduleAndDeliversAtMostOncePerDate() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = AppPreferences.getInstance(context)
        preferences.setReminderEnabled(true)
        preferences.setReminderMinute(1020)
        PracticeReminderReceiver().onReceive(context, android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        PracticeReminder.deliver(context)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, notifications.size())
        context.getSystemService(NotificationManager::class.java).cancelAll()
        PracticeReminder.deliver(context)
        assertEquals(0, notifications.size())
        preferences.setReminderEnabled(false)
        PracticeReminder.reconcile(context)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }
    @Test fun missingStrokesFallsBackWithoutChangingPreference() {
        assertTrue(trainingShowsStrokes(true, true))
        assertFalse(trainingShowsStrokes(true, false))
        assertFalse(trainingShowsStrokes(false, true))
        val context = RuntimeEnvironment.getApplication()
        val preferences = AppPreferences(context)
        try {
            preferences.setTrainingStrokes(true)
            assertFalse(trainingShowsStrokes(preferences.state.value.trainingStrokes, false))
            assertTrue(preferences.state.value.trainingStrokes)
            assertTrue(trainingShowsStrokes(preferences.state.value.trainingStrokes, true))
        } finally { preferences.close() }
    }
}
