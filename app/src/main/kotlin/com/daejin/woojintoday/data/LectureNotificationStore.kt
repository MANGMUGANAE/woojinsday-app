package com.daejin.woojintoday.data

import android.content.Context

/** Local storage for the timetable screen's "강의시간 알림" toggle — on/off + how many minutes
 *  before class start to notify, plus the request codes currently scheduled so they can be
 *  cancelled cleanly when the toggle turns off or the timetable changes. */
class LectureNotificationStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun minutesBefore(): Int = prefs.getInt(KEY_MINUTES_BEFORE, DEFAULT_MINUTES_BEFORE)

    fun setMinutesBefore(minutes: Int) {
        prefs.edit().putInt(KEY_MINUTES_BEFORE, minutes).apply()
    }

    fun scheduledRequestCodes(): Set<Int> =
        (prefs.getStringSet(KEY_SCHEDULED_CODES, emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()

    fun saveScheduledRequestCodes(codes: Set<Int>) {
        prefs.edit().putStringSet(KEY_SCHEDULED_CODES, codes.map { it.toString() }.toSet()).apply()
    }

    companion object {
        private const val PREFS_NAME = "sugang_lecture_notifications"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MINUTES_BEFORE = "minutes_before"
        private const val KEY_SCHEDULED_CODES = "scheduled_codes"
        const val DEFAULT_MINUTES_BEFORE = 10
    }
}
