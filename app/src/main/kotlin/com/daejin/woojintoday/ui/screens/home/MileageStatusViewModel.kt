package com.daejin.woojintoday.ui.screens.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.StudentProfileStore
import com.daejin.woojintoday.data.model.Departments
import com.daejin.woojintoday.data.network.MileageHistoryEntry
import com.daejin.woojintoday.data.network.MileageHistoryResult
import com.daejin.woojintoday.data.network.MileageSummary
import com.daejin.woojintoday.data.network.MileageSummaryResult
import com.daejin.woojintoday.data.network.MyFutureClient
import kotlinx.coroutines.launch

private val GRADE_DIGIT_REGEX = Regex("""\d+""")

/** T-WIN(together.daejin.ac.kr)의 "마일리지 현황" — RankViewModel과 같은 이유로 별도 로그인이
 *  필요하고(포털/dreams2와 세션이 다름), sustCd/shyrCd도 같은 방식으로 저장된 학생 프로필에서
 *  derive한다(부서명→코드 매칭, 학년 텍스트에서 숫자만 추출). */
class MileageStatusViewModel(
    private val credentialStore: CredentialStore,
    private val studentProfileStore: StudentProfileStore,
    private val client: MyFutureClient = MyFutureClient()
) : ViewModel() {

    var summary by mutableStateOf<MileageSummary?>(null)
        private set
    var history by mutableStateOf<List<MileageHistoryEntry>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        val studentNo = credentialStore.studentNo()
        val password = credentialStore.password()
        if (studentNo == null || password == null) {
            errorMessage = "저장된 로그인 정보가 없습니다."
            return
        }
        val profile = studentProfileStore.get()
        val sustCd = profile?.department?.trim()?.let { name -> Departments.ALL.find { it.name == name } }?.code
        val shyrCd = profile?.grade?.let { GRADE_DIGIT_REGEX.find(it)?.value }
        if (sustCd == null || shyrCd == null) {
            errorMessage = "학과/학년 정보를 확인할 수 없습니다."
            return
        }

        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            if (!client.login(studentNo, password)) {
                errorMessage = "마일리지 시스템 로그인에 실패했습니다."
                isLoading = false
                return@launch
            }
            when (val summaryResult = client.fetchMileageSummary()) {
                is MileageSummaryResult.Success -> summary = summaryResult.summary
                is MileageSummaryResult.NetworkError -> errorMessage = summaryResult.message
            }
            when (val historyResult = client.fetchMileageHistory(studentNo, sustCd, shyrCd)) {
                is MileageHistoryResult.Success -> history = historyResult.entries
                is MileageHistoryResult.NetworkError -> {
                    if (errorMessage == null) errorMessage = historyResult.message
                }
            }
            isLoading = false
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MileageStatusViewModel(
                credentialStore = CredentialStore(context),
                studentProfileStore = StudentProfileStore(context)
            ) as T
        }
    }
}
