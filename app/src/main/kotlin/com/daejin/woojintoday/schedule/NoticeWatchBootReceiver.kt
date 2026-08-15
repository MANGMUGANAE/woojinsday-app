package com.daejin.woojintoday.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daejin.woojintoday.data.NoticeWatchStore

/** 정확한 알람은 재부팅 시 사라지므로, "글 올라오면 알려드려요"가 섹션 중 하나라도 켜져 있으면
 *  다음 검사 시각(오늘/내일 18시)을 다시 걸어준다. */
class NoticeWatchBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!NoticeWatchStore(context).anyEnabled()) return
        NoticeWatchScheduler.scheduleNext(context)
    }
}
