package com.roshan.instatube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the foreground notification for the in-app YouTube/media queue.
 * Downloads remains the single source of truth; this service only renders its current state.
 */
class DownloadProgressService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var sawActiveTask = false
    private var latestStartId = 0
    private var lastKnownCompleteCount = 0
    private var lastKnownFailedCount = 0

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, progressNotification(emptyList()))
        if (monitorJob?.isActive != true) {
            lastKnownCompleteCount = Downloads.tasks.count { it.status == DownloadStatus.Complete }
            lastKnownFailedCount = Downloads.tasks.count { it.status == DownloadStatus.Failed }
            monitorJob = scope.launch { monitorQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun monitorQueue() {
        while (scope.isActive) {
            val active = Downloads.tasks.filter {
                it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued
            }

            if (active.isEmpty()) {
                if (sawActiveTask) {
                    val completeNow = Downloads.tasks.count { it.status == DownloadStatus.Complete }
                    val failedNow = Downloads.tasks.count { it.status == DownloadStatus.Failed }
                    val completed = (completeNow - lastKnownCompleteCount).coerceAtLeast(0)
                    val failed = (failedNow - lastKnownFailedCount).coerceAtLeast(0)
                    if (completed > 0 || failed > 0) postCompletionNotification(completed, failed)
                }
                if (stopSelfResult(latestStartId)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    return
                }
                // A newer start request arrived while the queue was going idle; keep observing.
                delay(250L)
                continue
            }

            sawActiveTask = true
            runCatching {
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    progressNotification(active)
                )
            }
            delay(850L)
        }
    }

    private fun progressNotification(active: List<DownloadTask>): Notification {
        val snapshot = DownloadProgressMath.calculate(active.map { it.downloaded to it.total })
        val totalBytes = snapshot.totalBytes
        val doneBytes = snapshot.downloadedBytes
        val determinate = snapshot.isDeterminate
        val percent = snapshot.percent
        val title = if (active.size == 1) {
            getString(R.string.download_progress_title)
        } else {
            getString(R.string.download_progress_multiple_format, active.size)
        }
        val currentTask = active.firstOrNull { it.status == DownloadStatus.Downloading } ?: active.firstOrNull()
        val detail = when {
            determinate -> getString(R.string.download_progress_percent_format, percent, formatBytes(doneBytes), formatBytes(totalBytes))
            currentTask != null -> currentTask.title.take(72)
            else -> getString(R.string.download_progress_preparing)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openDownloadsPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent, !determinate)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private fun postCompletionNotification(completed: Int, failed: Int) {
        val text = when {
            failed > 0 && completed > 0 -> getString(R.string.download_progress_finished_mixed_format, completed, failed)
            failed > 0 -> getString(R.string.download_progress_finished_failed_format, failed)
            completed > 0 -> getString(R.string.download_progress_finished_format, completed)
            else -> getString(R.string.download_progress_finished_empty)
        }
        val notification = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.download_progress_finished_title))
            .setContentText(text)
            .setContentIntent(openDownloadsPendingIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(RESULT_NOTIFICATION_ID, notification) }
    }

    private fun openDownloadsPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
        return PendingIntent.getActivity(
            this,
            31,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.download_progress_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.download_progress_channel_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL_ID, getString(R.string.download_progress_result_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = getString(R.string.download_progress_result_channel_description) }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Android 15+ enforces a shared six-hour budget for data-sync foreground services. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        monitorJob?.cancel()
        // Do not allow the queue's IO coroutines to continue without their foreground service.
        runCatching { Downloads.pauseAllActive() }
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                RESULT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_download)
                    .setContentTitle(getString(R.string.download_progress_paused_title))
                    .setContentText(getString(R.string.download_progress_paused_text))
                    .setContentIntent(openDownloadsPendingIntent())
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    companion object {
        private const val ACTION_STOP = "com.roshan.instatube.action.STOP_DOWNLOAD_PROGRESS"
        private const val CHANNEL_ID = "download_progress"
        private const val RESULT_CHANNEL_ID = "download_progress_results"
        private const val NOTIFICATION_ID = 3201
        private const val RESULT_NOTIFICATION_ID = 3202

        fun start(context: Context) {
            val intent = Intent(context, DownloadProgressService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
