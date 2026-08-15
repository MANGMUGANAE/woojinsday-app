package com.daejin.woojintoday.data

import android.content.Context
import com.daejin.woojintoday.data.network.StudentProfile

/** Local cache of the student's basic info (name/department/grade), refreshed on every login. */
class StudentProfileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(profile: StudentProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.name)
            .putString(KEY_DEPARTMENT, profile.department)
            .putString(KEY_GRADE, profile.grade)
            .apply()
    }

    fun get(): StudentProfile? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val department = prefs.getString(KEY_DEPARTMENT, null) ?: return null
        val grade = prefs.getString(KEY_GRADE, null) ?: return null
        return StudentProfile(name, department, grade)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "sugang_student_profile"
        private const val KEY_NAME = "name"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_GRADE = "grade"
    }
}
