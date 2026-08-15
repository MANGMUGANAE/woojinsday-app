package com.daejin.woojintoday.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daejin.woojintoday.R

/** 공지사항 다이얼로그의 섹션별 "글 올라오면 알려드려요" 토글이 켜져 있을 때, 새 글이 올라오면
 *  "[섹션이름] 글 제목" 형태로 알려준다. */
object NoticeWatchNotifier {
    private const val CHANNEL_ID = "notice_watch"
    private const val CHANNEL_NAME = "새 공지 알림"

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notify(context: Context, notificationId: Int, sectionName: String, noticeTitle: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val title = "[$sectionName] $noticeTitle"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6A1A.toInt())
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
