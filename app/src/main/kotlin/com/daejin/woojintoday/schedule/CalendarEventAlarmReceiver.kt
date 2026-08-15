package com.daejin.woojintoday.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daejin.woojintoday.notification.CalendarEventNotifier

/** Fired by [CalendarNotificationScheduler] on a calendar event's date. */
class CalendarEventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(CalendarNotificationScheduler.EXTRA_TITLE) ?: return
        val notificationId = intent.getIntExtra(CalendarNotificationScheduler.EXTRA_NOTIFICATION_ID, 0)
        CalendarEventNotifier.notify(context, notificationId, title)
    }
}
