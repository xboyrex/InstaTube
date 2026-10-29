package com.roshan.instatube

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Dedicated Share-menu action for users who want to start a quick download immediately.
 * Request notification permission before starting the foreground service, so the user
 * can see its ongoing progress notification from the beginning.
 */
class QuickDownloadActivity : Activity() {
    private var pendingUrl: String = ""
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        pendingUrl = InstagramDownloader.extractCleanUrl(raw)
        if (!isSupportedLink(pendingUrl)) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AppSettings(this).notificationPermissionAsked = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        startQuickDownload()
    }

    private fun isSupportedLink(url: String): Boolean =
        InstagramDownloader.isInstagramUrl(url) || InstagramDownloader.isYouTubeUrl(url)

    private fun startQuickDownload() {
        if (launched || pendingUrl.isBlank()) return
        launched = true
        val serviceIntent = Intent(this, QuickDownloadService::class.java).apply {
            action = QuickDownloadService.ACTION_DOWNLOAD
            putExtra(QuickDownloadService.EXTRA_URL, pendingUrl)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        }.onFailure {
            openMainActivity()
            return
        }
        finish()
    }

    private fun openMainActivity() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHARED_URL, pendingUrl)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    @Deprecated("Deprecated in Android, retained for API 24 compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startQuickDownload()
        } else {
            // Do not start a silent download after the user declined visible notifications.
            openMainActivity()
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 410
    }
}
