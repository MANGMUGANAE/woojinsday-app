package com.daejin.woojintoday.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

private const val NOTIFY_HOUR = 9

/**
 * Schedules/cancels the exact alarm that reminds the student on a calendar event's date — shared
 * by both school-provided 학사일정 events and the student's own personal reminders, since the
 * AlarmManager mechanics are identical; only the id/title/date differ.
 */
object CalendarNotificationScheduler {
    const val EXTRA_TITLE = "title"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    private const val TAG = "CalendarScheduler"

    fun schedule(context: Context, id: String, title: String, date: LocalDate, hour: Int = NOTIFY_HOUR, minute: Int = 0) {
        val triggerMillis = triggerTimeMillis(date, hour, minute)
        if (triggerMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "이미 지난 일정이라 예약하지 않음: $title")
            return
        }
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(context, id, title)
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            Log.d(TAG, "캘린더 알림 등록: $title @ $triggerMillis")
        } catch (e: SecurityException) {
            Log.w(TAG, "정확한 알람 권한 없음, 대략적인 알람으로 대체", e)
            manager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, id: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(buildPendingIntent(context, id, ""))
    }

    private fun triggerTimeMillis(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun buildPendingIntent(context: Context, id: String, title: String): PendingIntent {
        val intent = Intent(context, CalendarEventAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_NOTIFICATION_ID, requestCodeFor(id))
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Bit-30 tag keeps this feature's PendingIntent request codes namespaced away from any
    // other feature that also derives request codes from a plain hashCode().
    fun requestCodeFor(id: String): Int = (id.hashCode() and 0x3FFFFFFF) or (1 shl 30)
}
