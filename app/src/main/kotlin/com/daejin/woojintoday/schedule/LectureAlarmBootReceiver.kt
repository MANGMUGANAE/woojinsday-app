package com.daejin.woojintoday.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daejin.woojintoday.data.EditingCoursesStore
import com.daejin.woojintoday.data.LectureNotificationStore
import com.daejin.woojintoday.data.model.AcademicPeriod

/** Exact alarms don't survive reboot — if "강의시간 알림" is toggled on, re-arm every reminder for
 *  the current semester's timetable on startup. */
class LectureAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = LectureNotificationStore(context)
        if (!store.isEnabled()) return
        val (year, semester) = AcademicPeriod.current()
        val courses = EditingCoursesStore(context).get(year, semester)
        LectureAlarmScheduler(context).scheduleAll(courses, store.minutesBefore())
    }
}
