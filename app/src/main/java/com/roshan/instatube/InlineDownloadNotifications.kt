package com.roshan.instatube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shows item-count progress for media saves initiated from an already-visible screen.
 * Long-running share-menu and YouTube queue transfers continue to use their foreground services.
 */
internal class InlineDownloadNotifications private constructor(
    context: Context,
    private val itemCount: Int,
    private val notificationId: Int,
    private val title: String,
) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private var finished = false

    fun update(completedItems: Int, detail: String) {
        if (finished) return
        val max = itemCount.coerceAtLeast(1)
        val completed = completedItems.coerceIn(0, max)
        notificationManager.notify(
            notificationId,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(title)
                .setContentText(detail.take(120))
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(openDownloadsPendingIntent())
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(max, completed, false)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        )
    }

    fun complete(message: String) {
        finish(message, failed = false)
    }

    fun fail(message: String) {
        finish(message, failed = true)
    }

    private fun finish(message: String, failed: Boolean) {
        if (finished) return
        finished = true
        notificationManager.cancel(notificationId)
        notificationManager.notify(
            notificationId + RESULT_ID_OFFSET,
            NotificationCompat.Builder(appContext, RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(appContext.getString(if (failed) R.string.inline_download_failed_title else R.string.inline_download_complete_title))
                .setContentText(message.take(160))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openDownloadsPendingIntent())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }

    private fun openDownloadsPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(appContext, notificationId, intent, flags)
    }

    companion object {
        private const val CHANNEL_ID = "inline_media_progress"
        private const val RESULT_CHANNEL_ID = "inline_media_results"
        private const val RESULT_ID_OFFSET = 100_000
        private val ids = AtomicInteger((System.currentTimeMillis() / 1_000L % 800_000_000L).toInt().coerceAtLeast(4_000))

        fun start(context: Context, itemCount: Int, title: String): InlineDownloadNotifications {
            val appContext = context.applicationContext
            createChannels(appContext)
            val session = InlineDownloadNotifications(appContext, itemCount.coerceAtLeast(1), ids.incrementAndGet(), title)
            session.update(0, appContext.getString(R.string.inline_download_preparing, itemCount.coerceAtLeast(1)))
            return session
        }

        private fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(
                listOf(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.inline_download_channel_name), NotificationManager.IMPORTANCE_LOW)
                        .apply { description = context.getString(R.string.inline_download_channel_description) },
                    NotificationChannel(RESULT_CHANNEL_ID, context.getString(R.string.inline_download_result_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = context.getString(R.string.inline_download_result_channel_description) },
                )
            )
        }
    }
}
