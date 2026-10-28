package dev.xboyrex.instatube

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings

class QuickDownloadActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (url.isNotEmpty()) {
                if (Settings.canDrawOverlays(this)) {
                    // Pop up the interactive Floating Overlay Card right over Instagram/YouTube!
                    OverlayBubbleService.start(this, initialUrl = url)
                } else {
                    val serviceIntent = Intent(this, QuickDownloadService::class.java)
                        .putExtra(QuickDownloadService.EXTRA_URL, url)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
