package com.daejin.woojintoday.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.NoticeSection
import com.daejin.woojintoday.data.NoticeWatchStore
import com.daejin.woojintoday.data.model.Notice
import com.daejin.woojintoday.data.network.LectureTimetableNoticeClient
import com.daejin.woojintoday.data.network.MileageScholarshipClient
import com.daejin.woojintoday.data.network.NoticeClient
import com.daejin.woojintoday.data.network.NoticeListResult
import com.daejin.woojintoday.notification.NoticeWatchNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "NoticeWatchAlarmReceiver"

/** 매일 저녁 6시 [NoticeWatchScheduler]가 깨우면, "글 올라오면 알려드려요"가 켜진 섹션만 새로
 *  목록을 받아 지난번에 저장해둔 목록과 비교하고, 새로 생긴 글에 대해서만 알림을 준다. 네트워크
 *  호출이 필요해 onReceive를 바로 끝낼 수 없으므로 goAsync로 시스템에 처리 시간을 더 요청한다.
 *  알람은 한 번 쏘고 끝나므로(재부팅 시에도 안 살아남음) 검사를 마칠 때마다 다음 날로 스스로
 *  재예약한다. */
class NoticeWatchAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkAllSections(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "새 공지 확인 실패", e)
            } finally {
                NoticeWatchScheduler.scheduleNext(appContext)
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkAllSections(context: Context) {
        val store = NoticeWatchStore(context)
        val credentialStore = CredentialStore(context)

        NoticeSection.entries.forEach { section ->
            if (!store.isEnabled(section)) return@forEach
            val notices = fetchNotices(section, credentialStore) ?: return@forEach
            val previouslySeen = store.lastSeenDetailPaths(section)
            // 최초 실행(토글은 켰지만 기준 목록이 아직 없는 경우) 이번 목록 전체를 "새 글"로
            // 오해하지 않도록, 기준이 비어있을 땐 알림 없이 기준만 세운다.
            if (previouslySeen.isNotEmpty()) {
                notices.filter { it.detailPath !in previouslySeen }
                    .forEach { notice ->
                        NoticeWatchNotifier.notify(
                            context,
                            notificationId = (section.name + notice.detailPath).hashCode(),
                            section = section,
                            noticeTitle = notice.title
                        )
                    }
            }
            store.saveLastSeenDetailPaths(section, notices.map { it.detailPath }.toSet())
        }
    }

    private suspend fun fetchNotices(section: NoticeSection, credentialStore: CredentialStore): List<Notice>? {
        val result = when (section) {
            NoticeSection.ACADEMIC -> NoticeClient().fetchNotices()
            NoticeSection.LECTURE_TIMETABLE ->
                LectureTimetableNoticeClient().fetchNotices(credentialStore.studentNo(), credentialStore.password())
            NoticeSection.MILEAGE -> MileageScholarshipClient().fetchNotices()
        }
        return (result as? NoticeListResult.Success)?.notices
    }
}
