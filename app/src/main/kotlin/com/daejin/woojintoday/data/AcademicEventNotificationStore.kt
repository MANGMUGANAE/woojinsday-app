package com.daejin.woojintoday.data

import android.content.Context
import com.daejin.woojintoday.data.model.AcademicEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local storage for which academic-schedule events the student wants a push reminder for. */
class AcademicEventNotificationStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAll(): List<AcademicEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AcademicEvent>>(raw) }.getOrDefault(emptyList())
    }

    fun enabledIds(): Set<String> = getAll().map { it.id }.toSet()

    fun setEnabled(event: AcademicEvent, enabled: Boolean) {
        val updated = getAll().filterNot { it.id == event.id }.toMutableList()
        if (enabled) updated += event
        prefs.edit().putString(KEY_EVENTS, json.encodeToString(updated)).apply()
    }

    fun timeFor(id: String): Pair<Int, Int> {
        val raw = prefs.getString(KEY_TIME_PREFIX + id, null)
        val parts = raw?.split(":")
        val hour = parts?.getOrNull(0)?.toIntOrNull() ?: DEFAULT_HOUR
        val minute = parts?.getOrNull(1)?.toIntOrNull() ?: DEFAULT_MINUTE
        return hour to minute
    }

    fun setTime(id: String, hour: Int, minute: Int) {
        prefs.edit().putString(KEY_TIME_PREFIX + id, "$hour:$minute").apply()
    }

    companion object {
        private const val PREFS_NAME = "sugang_academic_events"
        private const val KEY_EVENTS = "enabled_events"
        private const val KEY_TIME_PREFIX = "notify_time_"
        private const val DEFAULT_HOUR = 9
        private const val DEFAULT_MINUTE = 0
    }
}
