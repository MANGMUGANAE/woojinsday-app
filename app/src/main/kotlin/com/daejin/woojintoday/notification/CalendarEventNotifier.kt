package com.daejin.woojintoday.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daejin.woojintoday.DeepLink
import com.daejin.woojintoday.MainActivity
import com.daejin.woojintoday.R

/** Posts a reminder when a toggled-on calendar event's date arrives (학사일정 or a personal reminder). */
object CalendarEventNotifier {
    private const val CHANNEL_ID = "calendar_events"
    private const val CHANNEL_NAME = "우진이의 캘린더"

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notify(context: Context, notificationId: Int, title: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DeepLink.EXTRA_TARGET, DeepLink.TARGET_CALENDAR)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6A1A.toInt())
            .setContentTitle("오늘의 일정")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
