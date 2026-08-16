package com.daejin.woojintoday.ui.screens.timetable

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.EditingCoursesStore
import com.daejin.woojintoday.data.EditingTimetableStore
import com.daejin.woojintoday.data.LectureNotificationStore
import com.daejin.woojintoday.data.SavedTimetableStore
import com.daejin.woojintoday.data.SessionStore
import com.daejin.woojintoday.data.SyllabusIndexStore
import com.daejin.woojintoday.data.model.AcademicPeriod
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.CourseFilters
import com.daejin.woojintoday.data.model.Department
import com.daejin.woojintoday.data.model.SavedTimetable
import com.daejin.woojintoday.data.model.TimeRangeFilter
import com.daejin.woojintoday.data.model.TimeRegion
import com.daejin.woojintoday.data.model.Weekday
import com.daejin.woojintoday.data.model.mergeRegionsIfPossible
import com.daejin.woojintoday.data.network.CourseCatalogClient
import com.daejin.woojintoday.data.network.CourseCatalogResult
import com.daejin.woojintoday.data.network.DaejinSession
import com.daejin.woojintoday.data.network.MyTimetableClient
import com.daejin.woojintoday.data.network.MyTimetableResult
import com.daejin.woojintoday.data.network.SyllabusClient
import com.daejin.woojintoday.schedule.LectureAlarmScheduler
import kotlinx.coroutines.launch

class TimetableViewModel(
    private val sessionStore: SessionStore,
    private val credentialStore: CredentialStore,
    private val savedTimetableStore: SavedTimetableStore,
    private val editingTimetableStore: EditingTimetableStore,
    private val editingCoursesStore: EditingCoursesStore,
    private val syllabusIndexStore: SyllabusIndexStore,
    private val lectureNotificationStore: LectureNotificationStore,
    private val lectureAlarmScheduler: LectureAlarmScheduler,
    private val catalogClient: CourseCatalogClient = CourseCatalogClient(),
    private val syllabusClient: SyllabusClient = SyllabusClient(),
    private val myTimetableClient: MyTimetableClient = MyTimetableClient()
) : ViewModel() {

    private val currentPeriod = AcademicPeriod.current()

    var year by mutableStateOf(currentPeriod.first)
        private set
    var semester by mutableStateOf(currentPeriod.second)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMyTimetable by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var courses by mutableStateOf<List<Course>>(emptyList())
        private set
    var selectedCourseKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var conflictMessage by mutableStateOf<String?>(null)
        private set
    var filters by mutableStateOf(CourseFilters())
        private set
    /** 과목 리스트에서 탭해 "미리보기" 중인 과목 — 리스트 행에 뜨는 "시간표에 추가" 버튼을 눌러야
     *  실제로 담긴다. 같은 과목을 다시 탭하면 미리보기가 취소된다. */
    var previewCourseKey by mutableStateOf<String?>(null)
        private set
    /** 과목코드-분반 → 교선 영역 라벨(예: "1영역"). 과목을 담기 전에도 리스트/필터에서 바로 쓸 수
     *  있도록, 강의계획서 화면을 열지 않아도 [load] 시점에 미리(백그라운드로) 채운다. */
    var genEdAreaByCourse by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** "강의시간 알림" 토글 — 켜져있으면 [selectedCourses]가 바뀔 때마다 알람도 같이 다시 예약된다. */
    var notificationsEnabled by mutableStateOf(lectureNotificationStore.isEnabled())
        private set
    var notificationMinutesBefore by mutableStateOf(lectureNotificationStore.minutesBefore())
        private set
    /** "강의시간 알림"을 켠 직후 토스트로 보여줄 "다음 알람이 언제 울리는지" 문구 — 실제로 예약이
     *  됐는지 그 자리에서 확인할 수 있게 한다. 예약할 세션이 하나도 없으면(빈 시간표) null. */
    var firstAlarmTimeText by mutableStateOf<String?>(null)
        private set

    // 학기별로 화면에 표시되는 "선택된 과목" 상태를 분리 보관 (저장된 시간표 목록과는 무관한, 편집 중인 그리드 상태).
    private val selectedCourseKeysByPeriod = mutableMapOf<Pair<Int, Int>, Set<String>>()

    // 전공 필터를 바꾸면 courses가 그 전공 목록으로 통째로 교체된다 — 시간표에 이미 담아둔 다른 전공
    // 과목이 courses에서 사라지면서 같이 사라지지 않도록, 한 번이라도 본 과목은 courseKey로 계속 기억해둔다.
    private val courseCache = mutableMapOf<String, Course>()

    private fun currentPeriodKey() = year to semester

    private fun updateSelectedKeys(newKeys: Set<String>) {
        selectedCourseKeys = newKeys
        selectedCourseKeysByPeriod[currentPeriodKey()] = newKeys
        editingTimetableStore.save(year, semester, newKeys)
        // courseKey만 저장하면, 화면을 나갔다 다시 들어왔을 때(뷰모델이 새로 생기고 courseCache가
        // 비어있는 상태) 네트워크로 과목 목록을 다시 받아올 때까지 그리드가 잠깐 비어 보이거나(깜빡임),
        // 그 시점 필터에 안 걸리는 과목은 아예 안 보이게 된다 — 과목 데이터 자체를 로컬에 같이 저장해서
        // 다시 들어오자마자 바로 고정된 상태로 보이게 한다.
        editingCoursesStore.save(year, semester, newKeys.mapNotNull { key -> courseCache[key] })
        if (notificationsEnabled) {
            lectureAlarmScheduler.scheduleAll(selectedCourses, notificationMinutesBefore)
        }
    }

    /** "강의시간 알림" 모달에서 확인을 눌렀을 때 — 토글을 켜고 그 시점의 시간표 기준으로 알람을 건다. */
    fun applyNotificationSettings(minutesBefore: Int) {
        lectureNotificationStore.setEnabled(true)
        lectureNotificationStore.setMinutesBefore(minutesBefore)
        notificationsEnabled = true
        notificationMinutesBefore = minutesBefore
        lectureAlarmScheduler.scheduleAll(selectedCourses, minutesBefore)
        firstAlarmTimeText = LectureAlarmScheduler.nearestUpcomingAlarm(selectedCourses, minutesBefore)?.let { upcoming ->
            val dayLabel = "${upcoming.day.label}요일"
            val alarmTime = formatMinutesOfDay(upcoming.classStartMinutes - minutesBefore)
            "$dayLabel $alarmTime 에 첫 알림이 울려요!"
        }
    }

    fun disableNotifications() {
        lectureNotificationStore.setEnabled(false)
        notificationsEnabled = false
        lectureAlarmScheduler.cancelAll()
    }

    val selectedCourses: List<Course>
        get() = selectedCourseKeys.mapNotNull { key -> courseCache[key] }

    val totalCredits: Double
        get() = selectedCourses.sumOf { it.credit }

    val previewCourse: Course?
        get() = previewCourseKey?.let { key -> courses.firstOrNull { it.courseKey == key } }

    val filteredCourses: List<Course>
        get() {
            val query = searchQuery.trim()
            return courses
                .filter { course ->
                    query.isEmpty() ||
                        course.name.contains(query, ignoreCase = true) ||
                        course.professor.contains(query, ignoreCase = true) ||
                        course.code.contains(query, ignoreCase = true)
                }
                .filter(filters::matches)
                .filter { course ->
                    filters.genEdAreas.isEmpty() || genEdAreaByCourse[course.courseKey] in filters.genEdAreas
                }
        }

    /** 네트워크 응답을 기다리지 않고도 이전에 담아뒀던 과목이 바로 보이도록, 로컬에 저장해둔 과목
     *  데이터를 courseCache에 먼저 채워 넣는다. */
    private fun preloadCourseCache(year: Int, semester: Int) {
        editingCoursesStore.get(year, semester).forEach { courseCache[it.courseKey] = it }
    }

    init {
        selectedCourseKeys = editingTimetableStore.get(year, semester)
        selectedCourseKeysByPeriod[currentPeriodKey()] = selectedCourseKeys
        preloadCourseCache(year, semester)
        // "강의시간 알림"이 기본으로 켜져있는데(LectureNotificationStore.isEnabled 기본값 true),
        // 알람은 시간표를 편집할 때만 다시 걸린다 — 이미 담아둔 시간표가 있는 채로 화면을 열었을
        // 때도 확실히 걸리도록 여기서도 한 번 걸어준다(이미 걸려있어도 재예약은 덮어쓸 뿐이라 안전).
        if (notificationsEnabled) {
            lectureAlarmScheduler.scheduleAll(selectedCourses, notificationMinutesBefore)
        }
        load()
        ensureGenEdAreaIndex()
    }

    /** 교선 영역 인덱스를 캐시에서 먼저 채우고, 없으면 백그라운드로 구축해둔다(19번 요청, 강의계획서
     *  화면을 열지 않아도 리스트/필터에서 바로 쓸 수 있도록). [load]와 달리 실패해도 화면엔 영향 없이
     *  그냥 "교선(영역)" 표시/필터가 아직 비어있는 채로 남는다. */
    private fun ensureGenEdAreaIndex() {
        val requestedPeriod = currentPeriodKey()
        val cached = syllabusIndexStore.getGenEdAreas(requestedPeriod.first, requestedPeriod.second)
        genEdAreaByCourse = cached ?: emptyMap()
        if (cached != null) return
        val session = DaejinSession(wmonid = sessionStore.wmonid(), jsessionId = sessionStore.jsessionId())
        if (session.jsessionId == null) return
        viewModelScope.launch {
            val result = runCatching {
                syllabusClient.buildGenEdAreaIndex(requestedPeriod.first, requestedPeriod.second, session) { _, _ -> }
            }.getOrNull() ?: return@launch
            syllabusIndexStore.saveGenEdAreas(requestedPeriod.first, requestedPeriod.second, result)
            if (currentPeriodKey() == requestedPeriod) genEdAreaByCourse = result
        }
    }

    fun onSearchQueryChange(value: String) {
        searchQuery = value
    }

    fun setPeriod(newYear: Int, newSemester: Int) {
        if (year == newYear && semester == newSemester) return
        year = newYear
        semester = newSemester
        selectedCourseKeys = selectedCourseKeysByPeriod[currentPeriodKey()]
            ?: editingTimetableStore.get(year, semester).also { selectedCourseKeysByPeriod[currentPeriodKey()] = it }
        preloadCourseCache(year, semester)
        previewCourseKey = null
        load()
        ensureGenEdAreaIndex()
    }

    /** Loads a saved timetable's semester + courses into the live editing grid, replacing whatever
     *  was there for that semester — used by "시간표로 보기" in the saved-timetable list. */
    fun viewSavedTimetable(timetable: SavedTimetable) {
        year = timetable.year
        semester = timetable.semester
        timetable.courses.forEach { courseCache[it.courseKey] = it }
        val keys = timetable.courses.map { it.courseKey }.toSet()
        updateSelectedKeys(keys)
        previewCourseKey = null
        load()
        ensureGenEdAreaIndex()
    }

    /** 과목 리스트 행 탭 처리 — 이미 담긴 과목은 바로 빼고, 새 과목은 탭할 때마다 "미리보기"를
     *  켜고 끈다(이때 리스트 행에 "시간표에 추가"/"상세보기" 버튼이 뜬다). 실제로 담는 건
     *  [addPreviewedCourse]에서, 그 버튼을 눌렀을 때만 한다. */
    fun onCourseRowTap(course: Course) {
        if (course.courseKey in selectedCourseKeys) {
            updateSelectedKeys(selectedCourseKeys - course.courseKey)
            if (previewCourseKey == course.courseKey) previewCourseKey = null
            return
        }
        previewCourseKey = if (previewCourseKey == course.courseKey) null else course.courseKey
    }

    /** 미리보기 중인 과목 리스트 행의 "시간표에 추가" 버튼 — 이 시점에만 시간 겹침을 검사한다. */
    fun addPreviewedCourse(course: Course) {
        val conflict = findConflict(course)
        if (conflict != null) {
            conflictMessage = "${course.name}과(와) ${conflict.name}의 시간이 겹쳐 추가할 수 없습니다."
            previewCourseKey = null
            return
        }
        updateSelectedKeys(selectedCourseKeys + course.courseKey)
        previewCourseKey = null
    }

    fun removeCourse(course: Course) {
        updateSelectedKeys(selectedCourseKeys - course.courseKey)
    }

    /** 시간표 그리드 쪽을 탭하면 미리보기 중이던 과목을 취소한다. */
    fun cancelPreview() {
        previewCourseKey = null
    }

    fun clearConflictMessage() {
        conflictMessage = null
    }

    fun setDepartmentFilter(department: Department?) {
        if (filters.department == department) return
        filters = filters.copy(department = department)
        load()
    }

    /** 필터 패널의 요일/시간 컨트롤은 항상 regions[0]을 편집한다 — 요일만 고르면 시간은 종일로,
     *  시간만 고르면 요일은 전체(와일드카드, 빈 집합)로 취급된다. */
    private fun region0OrDefault(): TimeRegion = filters.regions.getOrNull(0) ?: TimeRegion(emptySet(), TimeRangeFilter(0, 1440))

    private fun setRegion0(region: TimeRegion) {
        val isEffectivelyEmpty = region.days.isEmpty() && region.timeRange == TimeRangeFilter(0, 1440)
        val updated = filters.regions.toMutableList()
        when {
            isEffectivelyEmpty -> if (updated.isNotEmpty()) updated.removeAt(0)
            updated.isEmpty() -> updated.add(region)
            else -> updated[0] = region
        }
        filters = filters.copy(regions = updated)
    }

    fun toggleDayFilter(day: Weekday) {
        val current = region0OrDefault()
        val newDays = if (day in current.days) current.days - day else current.days + day
        setRegion0(current.copy(days = newDays))
    }

    fun setTimeRangeFilter(range: TimeRangeFilter?) {
        val current = region0OrDefault()
        setRegion0(current.copy(timeRange = range ?: TimeRangeFilter(0, 1440)))
    }

    /** 시간표 그리드 드래그로 새 사각형을 만들거나(끝 index 초과) 기존 사각형을 실시간으로 갱신한다.
     *  드래그 도중에는 병합하지 않고, 손을 뗄 때 [finalizeRegions]로 한 번에 정리한다. */
    fun setRegionAt(index: Int, region: TimeRegion) {
        val updated = filters.regions.toMutableList()
        if (index < updated.size) updated[index] = region else updated.add(region)
        filters = filters.copy(regions = updated)
    }

    fun appendRegion(region: TimeRegion): Int {
        val updated = filters.regions + region
        filters = filters.copy(regions = updated)
        return updated.lastIndex
    }

    /** 요일이 같고 시간이 붙어있거나, 시간이 같은 사각형끼리 하나로 합친다. */
    fun finalizeRegions() {
        var list = filters.regions
        var mergedAny = true
        while (mergedAny) {
            mergedAny = false
            outer@ for (i in list.indices) {
                for (j in list.indices) {
                    if (i == j) continue
                    val merged = mergeRegionsIfPossible(list[i], list[j])
                    if (merged != null) {
                        list = list.filterIndexed { idx, _ -> idx != i && idx != j } + merged
                        mergedAny = true
                        break@outer
                    }
                }
            }
        }
        filters = filters.copy(regions = list)
    }

    fun removeRegionAt(index: Int) {
        val updated = filters.regions.toMutableList()
        if (index in updated.indices) updated.removeAt(index)
        filters = filters.copy(regions = updated)
    }

    fun toggleGradeFilter(grade: String) {
        filters = filters.copy(
            grades = if (grade in filters.grades) filters.grades - grade else filters.grades + grade
        )
    }

    fun toggleCategoryFilter(category: String) {
        val newCategories = if (category in filters.categories) filters.categories - category else filters.categories + category
        filters = filters.copy(
            categories = newCategories,
            // 교선을 이수구분에서 빼면 영역 필터도 같이 비운다 — 안 보이는 영역 필터가 그대로 남아
            // "교선 아님 + 특정 영역"처럼 앞뒤가 안 맞는 조합으로 남는 걸 막는다.
            genEdAreas = if ("교선" !in newCategories) emptySet() else filters.genEdAreas
        )
    }

    fun toggleGenEdAreaFilter(area: String) {
        filters = filters.copy(
            genEdAreas = if (area in filters.genEdAreas) filters.genEdAreas - area else filters.genEdAreas + area
        )
    }

    fun toggleCreditFilter(credit: String) {
        filters = filters.copy(
            credits = if (credit in filters.credits) filters.credits - credit else filters.credits + credit
        )
    }

    fun logout() {
        sessionStore.clear()
        credentialStore.clear()
    }

    fun saveCurrentTimetable(name: String) {
        if (selectedCourses.isEmpty()) return
        savedTimetableStore.save(name, selectedCourses, year, semester)
    }

    fun savedTimetables(): List<SavedTimetable> = savedTimetableStore.getAll()

    fun deleteSavedTimetable(id: String) {
        savedTimetableStore.delete(id)
    }

    fun removeSavedCourse(timetableId: String, courseKey: String) {
        savedTimetableStore.removeCourse(timetableId, courseKey)
    }

    private fun findConflict(course: Course): Course? =
        selectedCourses.firstOrNull { existing ->
            existing.sessions.any { existingSession ->
                course.sessions.any { newSession ->
                    newSession.day == existingSession.day &&
                        newSession.startMinutes < existingSession.endMinutes &&
                        existingSession.startMinutes < newSession.endMinutes
                }
            }
        }

    fun load() {
        val session = DaejinSession(wmonid = sessionStore.wmonid(), jsessionId = sessionStore.jsessionId())
        if (session.jsessionId == null) {
            errorMessage = "로그인 정보가 없습니다. 다시 로그인해주세요."
            return
        }
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            when (val result = catalogClient.fetchCourses(year, semester, session, filters.department?.code)) {
                is CourseCatalogResult.Success -> {
                    courses = result.courses
                    result.courses.forEach { courseCache[it.courseKey] = it }
                    errorMessage = if (result.courses.isEmpty()) "조회된 과목이 없습니다." else null
                }
                is CourseCatalogResult.NetworkError -> {
                    errorMessage = result.message
                }
            }
            isLoading = false
        }
    }

    /** "내 시간표 불러오기" 버튼 — 학교 시스템에 등록된 실제 시간표(BlsnTotalTimeTableLst.jsp)를
     *  받아와 교과번호+분반을 courseKey 형식으로 매칭해서 지금 로드된 [courses] 중 일치하는 것만
     *  바로 담는다. 이미 학교 시스템에서 검증된 실제 시간표라 시간 겹침 검사는 하지 않는다. */
    fun loadMyRegisteredTimetable() {
        val session = DaejinSession(wmonid = sessionStore.wmonid(), jsessionId = sessionStore.jsessionId())
        val userId2 = credentialStore.userId2()
        if (session.jsessionId == null || userId2 == null) {
            conflictMessage = "로그인 정보가 없습니다. 다시 로그인해주세요."
            return
        }
        isLoadingMyTimetable = true
        viewModelScope.launch {
            // 전공 필터가 걸려있으면 지금 로드된 courses는 그 학과 것만이라 매칭 대상이 부족하다 —
            // 이때는 필터와 무관하게 전체 과목으로 따로 조회해서 매칭한다.
            val matchCandidates: List<Course>? = if (filters.department == null) {
                courses
            } else {
                when (val catalogResult = catalogClient.fetchCourses(year, semester, session, null)) {
                    is CourseCatalogResult.Success -> {
                        catalogResult.courses.forEach { courseCache[it.courseKey] = it }
                        catalogResult.courses
                    }
                    is CourseCatalogResult.NetworkError -> {
                        conflictMessage = catalogResult.message
                        null
                    }
                }
            }
            if (matchCandidates == null) {
                isLoadingMyTimetable = false
                return@launch
            }
            when (val result = myTimetableClient.fetchMyTimetableCourseKeys(year, semester, session, userId2)) {
                is MyTimetableResult.Success -> {
                    val matchedKeys = matchCandidates.filter { it.courseKey in result.courseKeys }.map { it.courseKey }.toSet()
                    if (matchedKeys.isEmpty()) {
                        conflictMessage = "불러올 시간표가 없습니다."
                    } else {
                        updateSelectedKeys(matchedKeys)
                        conflictMessage = "시간표를 불러왔습니다!"
                    }
                }
                is MyTimetableResult.NetworkError -> {
                    conflictMessage = result.message
                }
            }
            isLoadingMyTimetable = false
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimetableViewModel(
                sessionStore = SessionStore(context),
                credentialStore = CredentialStore(context),
                savedTimetableStore = SavedTimetableStore(context),
                editingTimetableStore = EditingTimetableStore(context),
                editingCoursesStore = EditingCoursesStore(context),
                syllabusIndexStore = SyllabusIndexStore(context),
                lectureNotificationStore = LectureNotificationStore(context),
                lectureAlarmScheduler = LectureAlarmScheduler(context)
            ) as T
        }
    }
}

private fun formatMinutesOfDay(minutes: Int): String = "%d시 %d분".format(minutes / 60, minutes % 60)
