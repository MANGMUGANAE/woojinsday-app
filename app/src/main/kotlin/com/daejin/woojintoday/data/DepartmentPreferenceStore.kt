package com.daejin.woojintoday.data

import android.content.Context
import com.daejin.woojintoday.data.model.Department
import com.daejin.woojintoday.data.model.Departments

/** Local storage for the student's own department, used by the "학과에서 몇등일까요?" feature. */
class DepartmentPreferenceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(department: Department) {
        prefs.edit().putString(KEY_CODE, department.code).apply()
    }

    fun get(): Department? {
        val code = prefs.getString(KEY_CODE, null) ?: return null
        return Departments.ALL.firstOrNull { it.code == code }
    }

    companion object {
        private const val PREFS_NAME = "sugang_department"
        private const val KEY_CODE = "department_code"
    }
}
