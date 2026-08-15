package com.daejin.woojintoday.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** A personal reminder the student added themselves — unlike [AcademicEvent], the user owns it and can delete it. */
@Serializable
data class UserCalendarEvent(
    val id: String,
    val epochDay: Long,
    val title: String,
    val notifyEnabled: Boolean = true,
    val notifyHour: Int = 8,
    val notifyMinute: Int = 0
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}
