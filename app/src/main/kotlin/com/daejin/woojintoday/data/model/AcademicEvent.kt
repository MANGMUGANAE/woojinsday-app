package com.daejin.woojintoday.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class AcademicEvent(
    val beginEpochDay: Long,
    val endEpochDay: Long,
    val description: String
) {
    val id: String get() = "${beginEpochDay}_${description.hashCode()}"
    val beginDate: LocalDate get() = LocalDate.ofEpochDay(beginEpochDay)
    val endDate: LocalDate get() = LocalDate.ofEpochDay(endEpochDay)

    companion object {
        fun of(beginDate: LocalDate, endDate: LocalDate, description: String) =
            AcademicEvent(beginDate.toEpochDay(), endDate.toEpochDay(), description)
    }
}
