package com.daejin.woojintoday.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daejin.woojintoday.data.LectureNotificationStore
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.Weekday
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

private const val TAG = "LectureAlarmScheduler"

/** [classStartMinutes]는 그 세션의 원래 수업 시작 시각(자정 기준 분), [alarmTriggerMillis]는 실제로
 *  울리도록 예약된 절대 시각. */
data class UpcomingLectureAlarm(val day: Weekday, val classStartMinutes: Int, val alarmTriggerMillis: Long)

/**
 * Schedules/cancels the "강의시간 알림" reminders for the student's currently selected timetable —
 * one alarm per (course, session), firing [minutesBefore] minutes before that session's start.
 * Android alarms are one-shot, so weekly recurrence works by [LectureAlarmReceiver] re-arming
 * itself for +1주 every time it fires, rather than this class tracking repetition.
 */
class LectureAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val store = LectureNotificationStore(appContext)

    fun scheduleAll(courses: List<Course>, minutesBefore: Int) {
        cancelAll()
        val newCodes = mutableSetOf<Int>()
        courses.forEach { course ->
            course.sessions.forEachIndexed { index, session ->
                val requestCode = requestCodeFor(course.courseKey, index)
                val armed = arm(appContext, requestCode, course.name, course.room, session.day, session.startMinutes, minutesBefore)
                if (armed) newCodes += requestCode
            }
        }
        store.saveScheduledRequestCodes(newCodes)
    }

    fun cancelAll() {
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        store.scheduledRequestCodes().forEach { code ->
            manager.cancel(buildPendingIntent(appContext, code, "", "", 0, 0, 0))
        }
        store.saveScheduledRequestCodes(emptySet())
    }

    companion object {
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_ROOM = "room"
        const val EXTRA_DAY_ORDINAL = "day_ordinal"
        const val EXTRA_START_MINUTES = "start_minutes"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"

        // Bit-29 tag keeps this feature's request codes namespaced away from
        // CalendarNotificationScheduler's (bit-30).
        fun requestCodeFor(courseKey: String, sessionIndex: Int): Int =
            ("$courseKey#$sessionIndex".hashCode() and 0xFFFFFFF) or (1 shl 29)

        /** [day] 요일의 (startMinutes - minutesBefore) 중 지금 이후로 가장 가까운 시각 — 오늘이면
         *  오늘, 이미 지났으면 다음 주 같은 요일. 자정 이전으로 당겨지는 경우는 지원하지 않는다. */
        fun nextTriggerMillis(day: Weekday, startMinutes: Int, minutesBefore: Int, now: LocalDateTime = LocalDateTime.now()): Long? {
            val triggerMinuteOfDay = startMinutes - minutesBefore
            if (triggerMinuteOfDay < 0) return null
            val targetDow = DayOfWeek.of(day.ordinal + 1)
            val daysUntil = (targetDow.value - now.dayOfWeek.value + 7) % 7
            var candidate = now.toLocalDate().plusDays(daysUntil.toLong())
                .atTime(triggerMinuteOfDay / 60, triggerMinuteOfDay % 60)
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(7)
            }
            return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        /** [courses]에 걸릴 알람들 중 지금 이후로 가장 먼저 울릴 것 하나 — 어느 요일의 몇 시 수업이라
         *  이렇게 예약됐는지 토글을 켠 그 자리에서 토스트로 보여주기 위한 것(자정을 넘기는 경우는
         *  [nextTriggerMillis]가 애초에 걸러내므로 수업 시각과 알람 시각은 항상 같은 날). */
        fun nearestUpcomingAlarm(courses: List<Course>, minutesBefore: Int, now: LocalDateTime = LocalDateTime.now()): UpcomingLectureAlarm? =
            courses.flatMap { it.sessions }
                .mapNotNull { session ->
                    nextTriggerMillis(session.day, session.startMinutes, minutesBefore, now)
                        ?.let { millis -> UpcomingLectureAlarm(session.day, session.startMinutes, millis) }
                }
                .minByOrNull { it.alarmTriggerMillis }

        /** 알람 하나를 실제로 걸어준다 — 최초 스케줄과, [LectureAlarmReceiver]의 매주 재예약 둘 다 여기를 탄다. */
        fun arm(context: Context, requestCode: Int, courseName: String, room: String, day: Weekday, startMinutes: Int, minutesBefore: Int): Boolean {
            val triggerMillis = nextTriggerMillis(day, startMinutes, minutesBefore) ?: return false
            val manager = context.getSystemService(AlarmManager::class.java) ?: return false
            val pendingIntent = buildPendingIntent(context, requestCode, courseName, room, day.ordinal, startMinutes, minutesBefore)
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } catch (e: SecurityException) {
                Log.w(TAG, "정확한 알람 권한 없음, 대략적인 알람으로 대체", e)
                manager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            return true
        }

        private fun buildPendingIntent(
            context: Context,
            requestCode: Int,
            courseName: String,
            room: String,
            dayOrdinal: Int,
            startMinutes: Int,
            minutesBefore: Int
        ): PendingIntent {
            val intent = Intent(context, LectureAlarmReceiver::class.java).apply {
                putExtra(EXTRA_REQUEST_CODE, requestCode)
                putExtra(EXTRA_COURSE_NAME, courseName)
                putExtra(EXTRA_ROOM, room)
                putExtra(EXTRA_DAY_ORDINAL, dayOrdinal)
                putExtra(EXTRA_START_MINUTES, startMinutes)
                putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
