package com.daejin.woojintoday.ui.screens.timetable

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.SessionStore
import com.daejin.woojintoday.data.SyllabusIndexStore
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.Syllabus
import com.daejin.woojintoday.data.network.DaejinSession
import com.daejin.woojintoday.data.network.SyllabusClient
import com.daejin.woojintoday.data.network.SyllabusDetailResult
import kotlinx.coroutines.launch

class SyllabusViewModel(
    private val sessionStore: SessionStore,
    private val indexStore: SyllabusIndexStore,
    private val client: SyllabusClient = SyllabusClient()
) : ViewModel() {

    /** null이면 로딩 중 아님, 아니면 (완료 개수, 전체 개수) — 학기 첫 조회 때만 뜬다(그 뒤엔 로컬 인덱스 재사용). */
    var indexBuildProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var isLoadingDetail by mutableStateOf(false)
        private set
    var syllabus by mutableStateOf<Syllabus?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load(year: Int, semester: Int, course: Course) {
        val session = DaejinSession(wmonid = sessionStore.wmonid(), jsessionId = sessionStore.jsessionId())
        if (session.jsessionId == null) {
            errorMessage = "로그인 정보가 없습니다. 다시 로그인해주세요."
            return
        }
        errorMessage = null
        syllabus = null
        viewModelScope.launch {
            val links = indexStore.getLinks(year, semester) ?: run {
                indexBuildProgress = 0 to client.totalQueryCount()
                val built = client.buildIndex(year, semester, session) { done, total ->
                    indexBuildProgress = done to total
                }
                indexStore.save(year, semester, built.links, built.departments)
                indexBuildProgress = null
                built.links
            }

            val detailUrl = links[course.courseKey]
            if (detailUrl == null) {
                errorMessage = "강의계획서를 찾을 수 없어요."
                return@launch
            }

            isLoadingDetail = true
            when (val result = client.fetchDetail(session, detailUrl)) {
                is SyllabusDetailResult.Success -> syllabus = result.syllabus
                is SyllabusDetailResult.NetworkError -> errorMessage = result.message
            }
            isLoadingDetail = false
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SyllabusViewModel(
                sessionStore = SessionStore(context),
                indexStore = SyllabusIndexStore(context)
            ) as T
        }
    }
}
