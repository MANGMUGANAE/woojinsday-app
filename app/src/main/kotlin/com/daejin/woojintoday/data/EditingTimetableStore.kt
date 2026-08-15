package com.daejin.woojintoday.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local, on-device cache of which courses are checked in the timetable grid, per (year, semester). */
class EditingTimetableStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(year: Int, semester: Int): Set<String> {
        val raw = prefs.getString(keyFor(year, semester), null) ?: return emptySet()
        return runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
    }

    fun save(year: Int, semester: Int, courseKeys: Set<String>) {
        prefs.edit().putString(keyFor(year, semester), json.encodeToString(courseKeys)).apply()
    }

    private fun keyFor(year: Int, semester: Int) = "period_${year}_$semester"

    companion object {
        private const val PREFS_NAME = "sugang_editing_timetable"
    }
}
