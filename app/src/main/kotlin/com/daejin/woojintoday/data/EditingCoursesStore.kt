package com.daejin.woojintoday.data

import android.content.Context
import com.daejin.woojintoday.data.model.Course
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local, on-device cache of the *full course data* (not just keys) for whatever is currently
 *  checked in the timetable grid, per (year, semester). [EditingTimetableStore] only remembers
 *  *which* courseKeys are selected; without this, re-opening the screen has to wait for a fresh
 *  network fetch before it can show those courses again, so the grid would flash empty (or drop
 *  courses entirely if they're not in whatever department filter happens to be active) until that
 *  fetch completes. */
class EditingCoursesStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(year: Int, semester: Int): List<Course> {
        val raw = prefs.getString(keyFor(year, semester), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Course>>(raw) }.getOrDefault(emptyList())
    }

    fun save(year: Int, semester: Int, courses: List<Course>) {
        prefs.edit().putString(keyFor(year, semester), json.encodeToString(courses)).apply()
    }

    private fun keyFor(year: Int, semester: Int) = "period_${year}_$semester"

    companion object {
        private const val PREFS_NAME = "sugang_editing_courses"
    }
}
