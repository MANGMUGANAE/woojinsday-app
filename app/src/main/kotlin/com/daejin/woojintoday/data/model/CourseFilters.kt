package com.daejin.woojintoday.data.model

/** 이수구분/학년/학점 범위는 학교 전체에서 항상 고정된 값이라 현재 로드된 과목 목록에서 동적으로
 *  뽑지 않는다 — 전공을 뭘 고르든 항상 똑같이 보인다. 선택한 값이 지금 목록에 없으면 그냥 결과가
 *  0개일 뿐, "알아서" 옵션을 숨기거나 범위를 좁히지 않는다. */
val FixedCourseCategories = listOf("교필", "교선", "일선", "전필", "전선", "전기", "교직")
val FixedCourseGrades = listOf("1학년", "2학년", "3학년", "4학년")
val FixedCourseCredits = listOf("1학점", "2학점", "3학점")
const val MIN_REGION_GAP_MINUTES = 30

data class TimeRangeFilter(val startMinutes: Int, val endMinutes: Int)

/** 시간표 그리드의 사각형 하나 = "이 요일들 중 하나이면서(비어있으면 요일 무관), 이 시간 안에
 *  완전히 들어있는" 세션. 여러 개를 두면 OR로 묶인다 (예: 월 9~11시 사각형 하나 + 목 14~16시
 *  사각형 하나 = 둘 중 하나만 맞아도 매칭). */
data class TimeRegion(val days: Set<Weekday>, val timeRange: TimeRangeFilter)

/** [a]와 [b]가 요일이 완전히 같거나 시간이 완전히 같으면서(+ 다른 쪽 축이 겹치거나 붙어있으면)
 *  하나로 합칠 수 있는지. 합쳐지면 그 결과를, 아니면 null을 반환한다. */
fun mergeRegionsIfPossible(a: TimeRegion, b: TimeRegion): TimeRegion? {
    if (a.days == b.days) {
        val touching = a.timeRange.startMinutes <= b.timeRange.endMinutes && b.timeRange.startMinutes <= a.timeRange.endMinutes
        if (touching) {
            return TimeRegion(
                a.days,
                TimeRangeFilter(
                    minOf(a.timeRange.startMinutes, b.timeRange.startMinutes),
                    maxOf(a.timeRange.endMinutes, b.timeRange.endMinutes)
                )
            )
        }
    }
    if (a.timeRange == b.timeRange) {
        return TimeRegion(a.days + b.days, a.timeRange)
    }
    return null
}

/** course.grade가 "3" 또는 "3학년"처럼 표기가 다를 수 있어 숫자만 뽑아 비교한다. */
private fun gradeDigits(text: String): String = text.filter { it.isDigit() }

/** "1학점"에서 숫자만 뽑아 course.credit(Double)과 정확히 비교한다. */
private fun creditDigits(text: String): Double = text.filter { it.isDigit() }.toDouble()

/** All active course-list filters. [department] triggers a server-side refetch (different API
 *  query); everything else is applied client-side over whatever list is currently loaded.
 *  [regions]는 요일+시간 사각형 목록(OR로 묶임) — 사각형 하나 안에서는 요일도 맞고 시간도 그 안에
 *  완전히 들어있는 세션만 매칭된다("겹치기만" 하는 게 아니라 "안에 딱 들어있는" 것만). 교수명/과목명/
 *  코드 검색은 검색창(TimetableViewModel.filteredCourses)에서 이미 처리하므로 여기엔 없다. */
data class CourseFilters(
    val department: Department? = null,
    val regions: List<TimeRegion> = emptyList(),
    val grades: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    /** 교선 영역(예: "1영역") 선택값 — 이수구분에 "교선"이 없으면 무시된다. courseKey→영역 매핑이
     *  Course 자체엔 없어(별도로 구축한 인덱스에만 있음) matches()가 아니라 호출부(뷰모델)에서 적용한다. */
    val genEdAreas: Set<String> = emptySet(),
    val credits: Set<String> = emptySet()
) {
    val isEmpty: Boolean
        get() = department == null && regions.isEmpty() && grades.isEmpty() &&
            categories.isEmpty() && genEdAreas.isEmpty() && credits.isEmpty()

    fun matches(course: Course): Boolean {
        if (regions.isNotEmpty()) {
            val fits = course.sessions.any { session ->
                regions.any { region ->
                    (region.days.isEmpty() || session.day in region.days) &&
                        session.startMinutes >= region.timeRange.startMinutes &&
                        session.endMinutes <= region.timeRange.endMinutes
                }
            }
            if (!fits) return false
        }
        if (grades.isNotEmpty()) {
            val gradeSet = grades.map(::gradeDigits)
            if (gradeDigits(course.grade) !in gradeSet) return false
        }
        if (categories.isNotEmpty() && course.category !in categories) return false
        if (credits.isNotEmpty() && course.credit !in credits.map(::creditDigits)) return false
        return true
    }
}
