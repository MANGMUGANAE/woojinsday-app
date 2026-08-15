package com.daejin.woojintoday.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.model.Notice
import com.daejin.woojintoday.data.network.NoticeClient
import com.daejin.woojintoday.data.network.NoticeListResult
import kotlinx.coroutines.launch

class NoticeViewModel(
    private val client: NoticeClient = NoticeClient()
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
            when (val result = client.fetchNotices()) {
                is NoticeListResult.Success -> {
                    notices = result.notices
                    errorMessage = if (result.notices.isEmpty()) "공지사항이 없습니다." else null
                }
                is NoticeListResult.NetworkError -> {
                    errorMessage = result.message
                }
            }
            isLoading = false
        }
    }
}
