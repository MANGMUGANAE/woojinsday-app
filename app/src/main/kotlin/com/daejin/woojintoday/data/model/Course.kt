package com.daejin.woojintoday.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Weekday(val label: Char) {
    MON('월'), TUE('화'), WED('수'), THU('목'), FRI('금'), SAT('토'), SUN('일');

    companion object {
        fun fromLabel(label: Char): Weekday? = entries.firstOrNull { it.label == label }
    }
}

/** One weekly occurrence of a course, e.g. 월09:30-11:30 → (MON, 570, 690). */
@Serializable
data class ClassSession(
    val day: Weekday,
    val startMinutes: Int,
    val endMinutes: Int
)

@Serializable
data class Course(
    val seq: Int,
    val code: String,
    val section: String,
    val name: String,
    val category: String,
    val credit: Double,
    val grade: String,
    val professor: String,
    val timeText: String,
    val sessions: List<ClassSession>,
    val room: String,
    val isCancelled: Boolean,
    val capacity: Int,
    val waitlistApplicants: Int,
    val ratio: String,
    val note: String
) {
    val courseKey: String get() = "$code-$section"
}

private val SESSION_REGEX = Regex("([월화수목금토일])(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})")

/** A course's 강의시간 cell can list several day/time ranges back to back (e.g. multi-day lectures). */
fun parseClassSessions(timeText: String): List<ClassSession> =
    SESSION_REGEX.findAll(timeText).mapNotNull { match ->
        val day = Weekday.fromLabel(match.groupValues[1][0]) ?: return@mapNotNull null
        val startMinutes = match.groupValues[2].toInt() * 60 + match.groupValues[3].toInt()
        val endMinutes = match.groupValues[4].toInt() * 60 + match.groupValues[5].toInt()
        ClassSession(day, startMinutes, endMinutes)
    }.toList()
