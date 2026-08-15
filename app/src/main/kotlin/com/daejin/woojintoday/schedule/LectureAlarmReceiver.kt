package com.daejin.woojintoday.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daejin.woojintoday.data.model.Weekday
import com.daejin.woojintoday.notification.LectureNotifier

/** Fired by [LectureAlarmScheduler] a configurable number of minutes before a lecture starts —
 *  shows the reminder, then re-arms itself for the same weekday/time one week later (alarms are
 *  one-shot, so this self-chaining is what makes it repeat weekly). */
class LectureAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(LectureAlarmScheduler.EXTRA_REQUEST_CODE, -1)
        if (requestCode == -1) return
        val courseName = intent.getStringExtra(LectureAlarmScheduler.EXTRA_COURSE_NAME) ?: return
        val room = intent.getStringExtra(LectureAlarmScheduler.EXTRA_ROOM).orEmpty()
        val dayOrdinal = intent.getIntExtra(LectureAlarmScheduler.EXTRA_DAY_ORDINAL, -1)
        val startMinutes = intent.getIntExtra(LectureAlarmScheduler.EXTRA_START_MINUTES, -1)
        val minutesBefore = intent.getIntExtra(LectureAlarmScheduler.EXTRA_MINUTES_BEFORE, 0)
        if (dayOrdinal !in Weekday.entries.indices || startMinutes < 0) return
        val day = Weekday.entries[dayOrdinal]

        val startTimeText = "%02d:%02d".format(startMinutes / 60, startMinutes % 60)
        LectureNotifier.notify(context, requestCode, courseName, room, startTimeText, minutesBefore)

        LectureAlarmScheduler.arm(context, requestCode, courseName, room, day, startMinutes, minutesBefore)
    }
}
