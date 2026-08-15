package com.daejin.woojintoday.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

private const val TAG = "NoticeWatchScheduler"

// 3섹션을 한꺼번에 검사하는 알람 하나뿐이라 고정 요청코드 하나로 충분하다 — 다른 기능들의
// hashCode 기반 비트태그(29/30번)와 겹치지 않는 별도 상수.
private const val REQUEST_CODE = 0x4E4F5443
private const val CHECK_HOUR = 18

/** "글 올라오면 알려드려요" 새 공지 검사를 매일 저녁 6시에 돌리는 자체 재예약 알람 —
 *  [NoticeWatchAlarmReceiver]가 검사를 마칠 때마다 다음 날 같은 시각으로 스스로 다시 건다. */
object NoticeWatchScheduler {
    fun scheduleNext(context: Context, from: LocalDateTime = LocalDateTime.now()) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        var candidate = from.toLocalDate().atTime(CHECK_HOUR, 0)
        if (!candidate.isAfter(from)) {
            candidate = candidate.plusDays(1)
        }
        val triggerMillis = candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = buildPendingIntent(context)
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } catch (e: SecurityException) {
            Log.w(TAG, "정확한 알람 권한 없음, 대략적인 알람으로 대체", e)
            manager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NoticeWatchAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
