package com.daejin.woojintoday.ui.screens.timetable

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.model.Notice
import com.daejin.woojintoday.data.network.LectureTimetableNoticeClient
import com.daejin.woojintoday.data.network.NoticeListResult
import kotlinx.coroutines.launch

class LectureTimetableNoticeViewModel(
    private val credentialStore: CredentialStore,
    private val client: LectureTimetableNoticeClient = LectureTimetableNoticeClient()
) : ViewModel() {

    var notices by mutableStateOf<List<Notice>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            when (val result = client.fetchNotices(credentialStore.studentNo(), credentialStore.password())) {
                is NoticeListResult.Success -> {
                    // 게시판 응답이 이미 최신순(작성일 내림차순)으로 내려오므로 앞에서 3개만 자르면 된다.
                    notices = result.notices.take(3)
                    errorMessage = if (result.notices.isEmpty()) "게시물이 없습니다." else null
                }
                is NoticeListResult.NetworkError -> {
                    errorMessage = result.message
                }
            }
            isLoading = false
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LectureTimetableNoticeViewModel(credentialStore = CredentialStore(context)) as T
        }
    }
}
