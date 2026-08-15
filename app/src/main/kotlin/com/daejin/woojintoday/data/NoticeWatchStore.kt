package com.daejin.woojintoday.data

import android.content.Context

enum class NoticeSection(val displayName: String) {
    ACADEMIC("학사공지"),
    LECTURE_TIMETABLE("종합강의시간표"),
    MILEAGE("마일리지")
}

/** 공지사항 다이얼로그의 3섹션(학사공지/종합강의시간표/마일리지) 각각의 "글 올라오면 알려드려요"
 *  토글 상태와, 다음 검사 때 무엇이 "새 글"인지 가려낼 기준이 되는 마지막 확인 시점의 글 목록
 *  (detailPath 집합)을 저장한다. */
class NoticeWatchStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(section: NoticeSection): Boolean = prefs.getBoolean(enabledKey(section), false)

    fun setEnabled(section: NoticeSection, enabled: Boolean) {
        prefs.edit().putBoolean(enabledKey(section), enabled).apply()
    }

    fun anyEnabled(): Boolean = NoticeSection.entries.any { isEnabled(it) }

    fun lastSeenDetailPaths(section: NoticeSection): Set<String> =
        prefs.getStringSet(seenKey(section), emptySet()) ?: emptySet()

    fun saveLastSeenDetailPaths(section: NoticeSection, paths: Set<String>) {
        prefs.edit().putStringSet(seenKey(section), paths).apply()
    }

    private fun enabledKey(section: NoticeSection) = "enabled_${section.name}"
    private fun seenKey(section: NoticeSection) = "seen_${section.name}"

    companion object {
        private const val PREFS_NAME = "sugang_notice_watch"
    }
}
