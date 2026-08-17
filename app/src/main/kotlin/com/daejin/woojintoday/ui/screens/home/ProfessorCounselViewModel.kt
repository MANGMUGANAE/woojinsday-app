package com.daejin.woojintoday.ui.screens.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.network.AdvisorClient
import com.daejin.woojintoday.data.network.AdvisorCounselStatus
import com.daejin.woojintoday.data.network.AdvisorCounselStatusResult
import com.daejin.woojintoday.data.network.AdvisorResult
import com.daejin.woojintoday.data.network.CounselApplyResult
import com.daejin.woojintoday.data.network.CounselDetail
import com.daejin.woojintoday.data.network.CounselDetailResult
import com.daejin.woojintoday.data.network.CounselHistoryEntry
import com.daejin.woojintoday.data.network.CounselHistoryResult
import com.daejin.woojintoday.data.network.MyFutureClient
import com.daejin.woojintoday.data.network.ProfessorSearchListResult
import com.daejin.woojintoday.data.network.ProfessorSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "지도교수 상담" 카드 — 지도교수 성함(dreams2, [AdvisorClient])과 상담 인정 현황·상담교수 검색·
 *  상담 신청(T-WIN together.daejin.ac.kr, [MyFutureClient])을 다룬다. 앞의 둘은 서로 다른
 *  로그인/인증 방식을 쓰는 별개 시스템이라 하나가 실패해도 다른 하나는 그대로 보여준다. */
class ProfessorCounselViewModel(
    private val credentialStore: CredentialStore,
    private val advisorClient: AdvisorClient = AdvisorClient(),
    private val futureClient: MyFutureClient = MyFutureClient()
) : ViewModel() {

    var advisorName by mutableStateOf<String?>(null)
        private set
    var isLoadingAdvisor by mutableStateOf(false)
        private set
    var advisorErrorMessage by mutableStateOf<String?>(null)
        private set

    var counselStatus by mutableStateOf<AdvisorCounselStatus?>(null)
        private set
    var isLoadingCounsel by mutableStateOf(false)
        private set
    var counselErrorMessage by mutableStateOf<String?>(null)
        private set

    var counselHistory by mutableStateOf<List<CounselHistoryEntry>>(emptyList())
        private set
    var isLoadingHistory by mutableStateOf(false)
        private set
    var historyErrorMessage by mutableStateOf<String?>(null)
        private set

    var selectedHistoryDetail by mutableStateOf<CounselDetail?>(null)
        private set
    var isLoadingHistoryDetail by mutableStateOf(false)
        private set
    var historyDetailErrorMessage by mutableStateOf<String?>(null)
        private set
    var showHistoryDetail by mutableStateOf(false)
        private set

    /** [counselStatus] 응답에 실려온 내 지도교수 staffNo — 상담교수 기본 선택값. */
    val advisorStaffNo: String?
        get() = counselStatus?.advisorStaffNo

    /** 사용자가 상담교수 검색 목록에서 직접 고르지 않았다면, 내 지도교수가 기본으로 선택돼 있다. */
    val selectedProfessor: ProfessorSearchResult?
        get() = manuallySelectedProfessor ?: advisorStaffNo?.let { staffNo ->
            ProfessorSearchResult(staffNo = staffNo, name = advisorName ?: "지도교수", department = "")
        }
    private var manuallySelectedProfessor by mutableStateOf<ProfessorSearchResult?>(null)

    var professorSearchQuery by mutableStateOf("")
        private set
    var professorSearchResults by mutableStateOf<List<ProfessorSearchResult>>(emptyList())
        private set
    var isSearchingProfessors by mutableStateOf(false)
        private set
    var professorSearchError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    var applyContent by mutableStateOf("")
        private set
    var applyPhonePart1 by mutableStateOf("")
        private set
    var applyPhonePart2 by mutableStateOf("")
        private set
    var applyPhonePart3 by mutableStateOf("")
        private set
    var applyEmail by mutableStateOf("")
        private set
    var isSubmittingApply by mutableStateOf(false)
        private set
    var applyResultMessage by mutableStateOf<String?>(null)
        private set
    var applySucceeded by mutableStateOf(false)
        private set
    var showSubmitConfirm by mutableStateOf(false)
        private set

    /** 전화번호 카드에서 "다음"이 눌리려면 세 칸이 전부 채워져야 한다. */
    val isPhoneStepValid: Boolean
        get() = applyPhonePart1.length == 3 && applyPhonePart2.length == 4 && applyPhonePart3.length == 4

    /** 이메일 카드에서 "다음"이 눌리려면 형식이 맞아야 한다. */
    val isEmailStepValid: Boolean
        get() = applyEmail.contains("@") && applyEmail.substringAfter("@", "").contains(".")

    /** 상담내용/전화번호/이메일이 전부 채워져야만 신청 버튼이 눌리게 하는 조건. */
    val canSubmit: Boolean
        get() = selectedProfessor != null &&
            applyContent.trim().length >= 10 &&
            isPhoneStepValid &&
            isEmailStepValid

    private val applyPhone: String
        get() = "$applyPhonePart1-$applyPhonePart2-$applyPhonePart3"

    init {
        load()
    }

    fun load() {
        val userId2 = credentialStore.userId2()
        if (userId2 == null) {
            advisorErrorMessage = "저장된 로그인 정보가 없습니다."
        } else {
            isLoadingAdvisor = true
            advisorErrorMessage = null
            viewModelScope.launch {
                when (val result = advisorClient.fetchAdvisorName(userId2)) {
                    is AdvisorResult.Success -> advisorName = result.name
                    is AdvisorResult.NetworkError -> advisorErrorMessage = result.message
                }
                isLoadingAdvisor = false
            }
        }

        val studentNo = credentialStore.studentNo()
        val password = credentialStore.password()
        if (studentNo == null || password == null) {
            counselErrorMessage = "저장된 로그인 정보가 없습니다."
            return
        }
        isLoadingCounsel = true
        counselErrorMessage = null
        viewModelScope.launch {
            if (!futureClient.login(studentNo, password)) {
                counselErrorMessage = "상담 현황 시스템 로그인에 실패했습니다."
                isLoadingCounsel = false
                return@launch
            }
            when (val result = futureClient.fetchAdvisorCounselStatus()) {
                is AdvisorCounselStatusResult.Success -> counselStatus = result.status
                is AdvisorCounselStatusResult.NetworkError -> counselErrorMessage = result.message
            }
            isLoadingCounsel = false

            refreshHistory()
        }
    }

    fun openHistoryDetail(cnsKeyId: String) {
        showHistoryDetail = true
        isLoadingHistoryDetail = true
        historyDetailErrorMessage = null
        selectedHistoryDetail = null
        viewModelScope.launch {
            when (val result = futureClient.fetchCounselDetail(cnsKeyId)) {
                is CounselDetailResult.Success -> selectedHistoryDetail = result.detail
                is CounselDetailResult.NetworkError -> historyDetailErrorMessage = result.message
            }
            isLoadingHistoryDetail = false
        }
    }

    fun dismissHistoryDetail() {
        showHistoryDetail = false
    }

    /** 검색어를 바꿀 때마다 잠깐 기다렸다가(디바운스) 이전 검색은 취소하고 새로 요청한다. */
    fun updateSearchQuery(query: String) {
        professorSearchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch(query)
        }
    }

    private suspend fun runSearch(query: String) {
        isSearchingProfessors = true
        professorSearchError = null
        when (val result = futureClient.searchProfessors(query)) {
            is ProfessorSearchListResult.Success -> professorSearchResults = result.professors
            is ProfessorSearchListResult.NetworkError -> professorSearchError = result.message
        }
        isSearchingProfessors = false
    }

    fun selectProfessor(professor: ProfessorSearchResult) {
        manuallySelectedProfessor = professor
    }

    fun updateApplyContent(value: String) {
        applyContent = value
    }

    fun updateApplyPhonePart1(value: String) {
        applyPhonePart1 = value.filter { it.isDigit() }.take(3)
    }

    fun updateApplyPhonePart2(value: String) {
        applyPhonePart2 = value.filter { it.isDigit() }.take(4)
    }

    fun updateApplyPhonePart3(value: String) {
        applyPhonePart3 = value.filter { it.isDigit() }.take(4)
    }

    fun updateApplyEmail(value: String) {
        applyEmail = value
    }

    /** "상담 신청하기" 버튼을 누르면 바로 쏘지 않고, 원본 사이트처럼 한 번 더 확인부터 받는다. */
    fun requestSubmit() {
        if (!canSubmit) return
        showSubmitConfirm = true
    }

    fun cancelSubmitConfirm() {
        showSubmitConfirm = false
    }

    fun confirmSubmit() {
        val professor = selectedProfessor ?: return
        showSubmitConfirm = false
        isSubmittingApply = true
        applySucceeded = false
        applyResultMessage = null
        viewModelScope.launch {
            when (
                val result = futureClient.submitOnlineCounselApply(
                    professor.staffNo,
                    applyContent.trim(),
                    applyPhone,
                    applyEmail.trim()
                )
            ) {
                is CounselApplyResult.Success -> {
                    applyResultMessage = "상담 신청이 완료됐어요!"
                    applySucceeded = true
                    refreshHistory()
                }
                is CounselApplyResult.NetworkError -> applyResultMessage = result.message
            }
            isSubmittingApply = false
        }
    }

    /** 방금 신청한 게 이력에 바로 반영되도록, 신청 성공 직후 상담 이력을 다시 불러온다. */
    private suspend fun refreshHistory() {
        val studentNo = credentialStore.studentNo() ?: return
        isLoadingHistory = true
        historyErrorMessage = null
        when (val result = futureClient.fetchCounselHistory(studentNo)) {
            is CounselHistoryResult.Success -> counselHistory = result.entries
            is CounselHistoryResult.NetworkError -> historyErrorMessage = result.message
        }
        isLoadingHistory = false
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfessorCounselViewModel(credentialStore = CredentialStore(context)) as T
        }
    }
}
