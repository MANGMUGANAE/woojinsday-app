package com.daejin.woojintoday.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedTimetable(
    val id: String,
    val name: String,
    val courses: List<Course>,
    val savedAt: Long,
    val year: Int,
    val semester: Int
)
