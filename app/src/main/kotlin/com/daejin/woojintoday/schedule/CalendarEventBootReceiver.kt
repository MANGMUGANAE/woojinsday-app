package com.daejin.woojintoday.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daejin.woojintoday.data.AcademicEventNotificationStore
import com.daejin.woojintoday.data.UserCalendarEventStore

/** Exact alarms don't survive reboot — re-arm every still-future toggled-on reminder on startup. */
class CalendarEventBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        AcademicEventNotificationStore(context).getAll().forEach { event ->
            CalendarNotificationScheduler.schedule(context, event.id, event.description, event.beginDate)
        }
        UserCalendarEventStore(context).getAll().filter { it.notifyEnabled }.forEach { event ->
            CalendarNotificationScheduler.schedule(context, event.id, event.title, event.date, event.notifyHour, event.notifyMinute)
        }
    }
}
