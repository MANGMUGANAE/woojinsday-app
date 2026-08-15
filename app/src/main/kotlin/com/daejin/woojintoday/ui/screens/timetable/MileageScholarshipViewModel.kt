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
import com.daejin.woojintoday.data.network.MileageAttachment
import com.daejin.woojintoday.data.network.MileageEligibility
import com.daejin.woojintoday.data.network.MileageScholarshipClient
import com.daejin.woojintoday.data.network.NoticeListResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 마일리지 장학 공지 목록의 한 항목 — [attachment]가 null이 아니면 그 글에 xlsx 대상자 명단이
 *  첨부돼 있다는 뜻이라 "대상자 확인" 버튼을 보여줄 수 있다. */
data class MileageNoticeItem(val notice: Notice, val attachment: MileageAttachment?)

class MileageScholarshipViewModel(
    private val credentialStore: CredentialStore,
    private val client: MileageScholarshipClient = MileageScholarshipClient()
) : ViewModel() {

    var items by mutableStateOf<List<MileageNoticeItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // 대상자 확인 모달 상태 — checkingNoticeTitle이 null이 아니면 모달이 떠 있는 것이고,
    // 그 상태에서 eligibilityResult가 아직 null이면 로딩 중이라는 뜻.
    var checkingNoticeTitle by mutableStateOf<String?>(null)
        private set
    var eligibilityResult by mutableStateOf<MileageEligibility?>(null)
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
                    val notices = result.notices.take(5)
                    errorMessage = if (notices.isEmpty()) "마일리지 장학 공지가 없습니다." else null
                    // 목록부터 먼저 보여주고, 첨부파일(xlsx) 유무는 각 글 상세페이지를 병렬로
                    // 조회해야 알 수 있어 뒤이어 채운다.
                    items = notices.map { MileageNoticeItem(it, attachment = null) }
                    items = coroutineScope {
                        notices.map { notice ->
                            async { MileageNoticeItem(notice, client.findXlsxAttachment(notice.detailPath)) }
                        }.awaitAll()
                    }
                }
                is NoticeListResult.NetworkError -> {
                    errorMessage = result.message
                }
            }
            isLoading = false
        }
    }

    fun checkEligibility(item: MileageNoticeItem) {
        val attachment = item.attachment ?: return
        val studentNo = credentialStore.studentNo() ?: return
        checkingNoticeTitle = item.notice.title
        eligibilityResult = null
        viewModelScope.launch {
            eligibilityResult = client.checkEligibility(attachment.downloadUrl, studentNo)
        }
    }

    fun dismissEligibilityResult() {
        checkingNoticeTitle = null
        eligibilityResult = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MileageScholarshipViewModel(credentialStore = CredentialStore(context.applicationContext)) as T
        }
    }
}
