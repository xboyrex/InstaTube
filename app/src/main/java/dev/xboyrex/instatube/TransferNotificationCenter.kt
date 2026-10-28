package dev.xboyrex.instatube

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
import kotlin.math.roundToInt

object TransferNotificationCenter {
    private const val CHANNEL_PROGRESS = "instatube_live_transfers"
    private const val CHANNEL_COMPLETE = "instatube_completed_transfers"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "Active Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress for Instagram and YouTube media"
                setShowBadge(false)
            }
            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when an Instagram or YouTube download finishes"
                enableVibration(true)
            }
            nm.createNotificationChannel(progressChannel)
            nm.createNotificationChannel(completeChannel)
        }
    }

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showProgress(
        context: Context,
        notificationId: Int,
        platform: String,
        title: String,
        progressPercent: Int,
        indeterminate: Boolean = false,
        detailText: String? = null,
    ) {
        val settings = AppSettings(context)
        if (!settings.notificationsEnabled || !hasPermission(context)) return
        init(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pct = progressPercent.coerceIn(0, 100)
        val sub = detailText ?: if (indeterminate) "Resolving stream…" else "$pct%"

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("[$platform] $title")
            .setContentText(sub)
            .setSubText("InstaTube")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
            .setContentIntent(pendingOpen)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun showComplete(
        context: Context,
        notificationId: Int,
        platform: String,
        title: String,
        fileName: String,
    ) {
        val settings = AppSettings(context)
        if (!settings.notificationsEnabled || !hasPermission(context)) return
        init(context)

        val nm = NotificationManagerCompat.from(context)
        runCatching { nm.cancel(notificationId) }

        val openVaultIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
        }
        val pendingVault = PendingIntent.getActivity(
            context,
            notificationId + 10000,
            openVaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete · $platform")
            .setContentText(title.ifBlank { fileName })
            .setSubText("Saved to Vault")
            .setAutoCancel(true)
            .setContentIntent(pendingVault)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open Vault",
                pendingVault
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        runCatching {
            nm.notify(notificationId + 10000, builder.build())
        }
    }

    fun showFailed(
        context: Context,
        notificationId: Int,
        platform: String,
        title: String,
        reason: String?,
    ) {
        val settings = AppSettings(context)
        if (!settings.notificationsEnabled || !hasPermission(context)) return
        init(context)

        val nm = NotificationManagerCompat.from(context)
        runCatching { nm.cancel(notificationId) }

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed · $platform")
            .setContentText(reason ?: title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        runCatching {
            nm.notify(notificationId + 20000, builder.build())
        }
    }
}
