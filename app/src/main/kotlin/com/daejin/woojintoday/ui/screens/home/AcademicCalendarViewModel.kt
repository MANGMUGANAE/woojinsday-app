package com.daejin.woojintoday.ui.screens.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.AcademicEventNotificationStore
import com.daejin.woojintoday.data.UserCalendarEventStore
import com.daejin.woojintoday.data.model.AcademicEvent
import com.daejin.woojintoday.data.model.UserCalendarEvent
import com.daejin.woojintoday.data.network.AcademicScheduleClient
import com.daejin.woojintoday.data.network.AcademicScheduleResult
import com.daejin.woojintoday.schedule.CalendarNotificationScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class AcademicCalendarViewModel(
    private val academicNotificationStore: AcademicEventNotificationStore,
    private val userEventStore: UserCalendarEventStore,
    private val client: AcademicScheduleClient = AcademicScheduleClient()
) : ViewModel() {

    var yearMonth by mutableStateOf(YearMonth.now())
        private set
    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var academicEvents by mutableStateOf<List<AcademicEvent>>(emptyList())
        private set
    var userEvents by mutableStateOf<List<UserCalendarEvent>>(emptyList())
        private set
    var enabledAcademicIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadUserEvents()
        loadAcademicMonth()
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
    }

    fun goToDate(date: LocalDate) {
        val targetMonth = YearMonth.from(date)
        if (targetMonth != yearMonth) {
            yearMonth = targetMonth
            loadAcademicMonth()
        }
        selectedDate = date
    }

    fun goToPreviousMonth() {
        yearMonth = yearMonth.minusMonths(1)
        loadAcademicMonth()
    }

    fun goToNextMonth() {
        yearMonth = yearMonth.plusMonths(1)
        loadAcademicMonth()
    }

    fun loadAcademicMonth() {
        isLoading = true
        errorMessage = null
        enabledAcademicIds = academicNotificationStore.enabledIds()
        val targetYearMonth = yearMonth
        viewModelScope.launch {
            when (val result = client.fetchMonth(targetYearMonth.year, targetYearMonth.monthValue)) {
                is AcademicScheduleResult.Success -> academicEvents = result.events
                is AcademicScheduleResult.NetworkError -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    private fun loadUserEvents() {
        userEvents = userEventStore.getAll()
    }

    fun eventsOn(date: LocalDate): Pair<List<AcademicEvent>, List<UserCalendarEvent>> {
        val academic = academicEvents.filter { !date.isBefore(it.beginDate) && !date.isAfter(it.endDate) }
        val personal = userEvents.filter { it.date == date }
        return academic to personal
    }

    fun isAcademicEnabled(event: AcademicEvent): Boolean = enabledAcademicIds.contains(event.id)

    fun academicTimeFor(event: AcademicEvent): Pair<Int, Int> = academicNotificationStore.timeFor(event.id)

    fun toggleAcademic(context: Context, event: AcademicEvent) {
        val enable = !isAcademicEnabled(event)
        academicNotificationStore.setEnabled(event, enable)
        enabledAcademicIds = academicNotificationStore.enabledIds()
        if (enable) {
            val (hour, minute) = academicNotificationStore.timeFor(event.id)
            CalendarNotificationScheduler.schedule(context, event.id, event.description, event.beginDate, hour, minute)
        } else {
            CalendarNotificationScheduler.cancel(context, event.id)
        }
    }

    fun setAcademicEventTime(context: Context, event: AcademicEvent, hour: Int, minute: Int) {
        academicNotificationStore.setTime(event.id, hour, minute)
        academicNotificationStore.setEnabled(event, true)
        enabledAcademicIds = academicNotificationStore.enabledIds()
        CalendarNotificationScheduler.schedule(context, event.id, event.description, event.beginDate, hour, minute)
    }

    fun setUserEventTime(context: Context, event: UserCalendarEvent, hour: Int, minute: Int) {
        val updated = event.copy(notifyEnabled = true, notifyHour = hour, notifyMinute = minute)
        userEventStore.update(updated)
        loadUserEvents()
        CalendarNotificationScheduler.schedule(context, updated.id, updated.title, updated.date, updated.notifyHour, updated.notifyMinute)
    }

    fun addUserEvent(context: Context, date: LocalDate, title: String, hour: Int = 8, minute: Int = 0) {
        if (title.isBlank()) return
        val event = UserCalendarEvent(
            id = UUID.randomUUID().toString(),
            epochDay = date.toEpochDay(),
            title = title.trim(),
            notifyEnabled = true,
            notifyHour = hour,
            notifyMinute = minute
        )
        userEventStore.add(event)
        loadUserEvents()
        CalendarNotificationScheduler.schedule(context, event.id, event.title, event.date, event.notifyHour, event.notifyMinute)
    }

    fun deleteUserEvent(context: Context, event: UserCalendarEvent) {
        userEventStore.remove(event.id)
        loadUserEvents()
        CalendarNotificationScheduler.cancel(context, event.id)
    }

    fun toggleUserEventNotification(context: Context, event: UserCalendarEvent) {
        val updated = event.copy(notifyEnabled = !event.notifyEnabled)
        userEventStore.update(updated)
        loadUserEvents()
        if (updated.notifyEnabled) {
            CalendarNotificationScheduler.schedule(context, updated.id, updated.title, updated.date, updated.notifyHour, updated.notifyMinute)
        } else {
            CalendarNotificationScheduler.cancel(context, updated.id)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AcademicCalendarViewModel(
                academicNotificationStore = AcademicEventNotificationStore(context),
                userEventStore = UserCalendarEventStore(context)
            ) as T
        }
    }
}
