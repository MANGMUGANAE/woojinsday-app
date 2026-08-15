package com.daejin.woojintoday.data

import android.content.Context
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.SavedTimetable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Local (on-device only, not synced anywhere) storage for named timetable snapshots. */
class SavedTimetableStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAll(): List<SavedTimetable> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val list = runCatching { json.decodeFromString<List<SavedTimetable>>(raw) }.getOrDefault(emptyList())
        return list.sortedByDescending { it.savedAt }
    }

    fun get(id: String): SavedTimetable? = getAll().firstOrNull { it.id == id }

    fun save(name: String, courses: List<Course>, year: Int, semester: Int) {
        val updated = listOf(
            SavedTimetable(
                id = UUID.randomUUID().toString(),
                name = name,
                courses = courses,
                savedAt = System.currentTimeMillis(),
                year = year,
                semester = semester
            )
        ) + getAll()
        persist(updated)
    }

    fun delete(id: String) {
        persist(getAll().filterNot { it.id == id })
    }

    fun removeCourse(timetableId: String, courseKey: String) {
        val updated = getAll().map { timetable ->
            if (timetable.id == timetableId) {
                timetable.copy(courses = timetable.courses.filterNot { it.courseKey == courseKey })
            } else {
                timetable
            }
        }
        persist(updated)
    }

    private fun persist(list: List<SavedTimetable>) {
        prefs.edit().putString(KEY_LIST, json.encodeToString(list)).apply()
    }

    companion object {
        private const val PREFS_NAME = "sugang_saved_timetables"
        private const val KEY_LIST = "timetables"
    }
}
