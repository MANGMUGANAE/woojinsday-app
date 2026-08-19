package com.daejin.woojintoday.ui.screens.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.model.FixedCourseCategories
import com.daejin.woojintoday.data.network.AcademicTranscriptClient
import com.daejin.woojintoday.data.network.TranscriptCourseRow
import com.daejin.woojintoday.data.network.TranscriptDetail
import com.daejin.woojintoday.data.network.TranscriptDetailResult
import kotlinx.coroutines.launch

/** "졸업학점" 카드 — dreams2 이수구분표(AI 시간표 생성 때 쓰는 것과 같은 리포트)를 과목 단위로,
 *  그리고 리포트 안에 같이 들어있는 졸업사정 기준표까지 통째로 받아온다. */
class GraduationCreditsViewModel(
    private val credentialStore: CredentialStore,
    private val transcriptClient: AcademicTranscriptClient = AcademicTranscriptClient()
) : ViewModel() {

    var detail by mutableStateOf<TranscriptDetail?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var showRawTable by mutableStateOf(false)
        private set

    /** 과목이 하나도 없는 이수구분도 빈 목록으로 항상 다 보여주려고, [FixedCourseCategories]
     *  전체를 기준으로 묶는다. */
    val rowsByCategory: List<Pair<String, List<TranscriptCourseRow>>>
        get() {
            val rows = detail?.rows.orEmpty()
            return FixedCourseCategories.map { category -> category to rows.filter { it.category == category } }
        }

    init {
        load()
    }

    fun load() {
        val userId2 = credentialStore.userId2()
        if (userId2 == null) {
            errorMessage = "저장된 로그인 정보가 없습니다."
            return
        }
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            when (val result = transcriptClient.fetchTranscriptDetail(userId2)) {
                is TranscriptDetailResult.Success -> detail = result.detail
                is TranscriptDetailResult.NetworkError -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    fun openRawTable() {
        showRawTable = true
    }

    fun dismissRawTable() {
        showRawTable = false
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GraduationCreditsViewModel(credentialStore = CredentialStore(context)) as T
        }
    }
}
