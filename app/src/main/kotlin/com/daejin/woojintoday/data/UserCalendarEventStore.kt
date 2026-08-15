package com.daejin.woojintoday.data

import android.content.Context
import com.daejin.woojintoday.data.model.UserCalendarEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local storage for personal calendar reminders the student adds themselves. */
class UserCalendarEventStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAll(): List<UserCalendarEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<UserCalendarEvent>>(raw) }.getOrDefault(emptyList())
    }

    fun add(event: UserCalendarEvent) {
        save(getAll() + event)
    }

    fun remove(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    fun update(event: UserCalendarEvent) {
        save(getAll().map { if (it.id == event.id) event else it })
    }

    private fun save(events: List<UserCalendarEvent>) {
        prefs.edit().putString(KEY_EVENTS, json.encodeToString(events)).apply()
    }

    companion object {
        private const val PREFS_NAME = "sugang_user_calendar_events"
        private const val KEY_EVENTS = "events"
    }
}
