package com.roshan.instatube

import android.app.AppOpsManager
import android.content.ClipboardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * User-enabled floating shortcut. It appears only after a recent clipboard-change event while
 * Instagram is foreground. Clipboard contents are read only after the user taps the shortcut
 * and InstaTube itself is brought to the foreground.
 */
class LinkAssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var overlaySubtitleView: TextView? = null
    private var lastForegroundPackage: String? = null
    private var lastForegroundClass: String? = null
    private var lastProcessedEventTime = 0L
    private var firstScan = true
    @Volatile private var lastClipboardChangeElapsed = 0L
    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Observe only that the clipboard changed. Never inspect its contents in this background service.
        lastClipboardChangeElapsed = SystemClock.elapsedRealtime()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        runCatching { clipboardManager?.addPrimaryClipChangedListener(clipboardListener) }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppSettings(this).overlayAssistantEnabled = false
            removeOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val settings = AppSettings(this)
        if (!settings.overlayAssistantEnabled ||
            !AutomationAccess.canDrawOverlays(this) ||
            !AutomationAccess.hasUsageAccess(this)
        ) {
            settings.overlayAssistantEnabled = false
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startAsForeground()
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch { monitorInstagramForeground() }
        }
        return START_STICKY
    }

    private suspend fun monitorInstagramForeground() {
        while (scope.isActive) {
            if (!AutomationAccess.canDrawOverlays(this) || !AutomationAccess.hasUsageAccess(this)) {
                AppSettings(this).overlayAssistantEnabled = false
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            // Usage event queries can cover a large first-run window; never perform them on Main.
            withContext(Dispatchers.IO) { updateForegroundPackage() }
            val clipboardChangeIsRecent = lastClipboardChangeElapsed > 0L &&
                SystemClock.elapsedRealtime() - lastClipboardChangeElapsed <= CLIPBOARD_EVENT_WINDOW_MS
            if (LinkAssistantPolicy.shouldShowShortcut(
                    AppSettings(this).overlayAssistantEnabled,
                    lastForegroundPackage
                )
            ) {
                showOverlay()
                overlaySubtitleView?.text = LinkAssistantPolicy.subtitleForClipboardState(clipboardChangeIsRecent)
            } else {
                removeOverlay()
            }
            delay(900L)
        }
    }

    private fun updateForegroundPackage() {
        val now = System.currentTimeMillis()
        val from = if (firstScan) now - 24L * 60L * 60L * 1000L else now - 12_000L
        firstScan = false
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val events = runCatching { manager.queryEvents(from, now) }.getOrNull() ?: return
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp < lastProcessedEventTime) continue
            val resumed = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            val paused = event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED)

            if (resumed) {
                lastForegroundPackage = event.packageName
                lastForegroundClass = event.className
            } else if (paused &&
                event.packageName == lastForegroundPackage &&
                (lastForegroundClass == null || event.className == lastForegroundClass)
            ) {
                lastForegroundPackage = null
                lastForegroundClass = null
            }
            lastProcessedEventTime = maxOf(lastProcessedEventTime, event.timeStamp)
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(0xF01A101C.toInt())
                setStroke(dp(1), 0xCCFF2D75.toInt())
            }
            elevation = dp(12).toFloat()
            contentDescription = getString(R.string.link_assistant_overlay_description)
            isClickable = true
            isFocusable = true
        }

        val badge = TextView(this).apply {
            text = "↓"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF2D75.toInt())
            }
        }
        root.addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)))

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }
        labels.addView(TextView(this).apply {
            text = getString(R.string.link_assistant_overlay_title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        val subtitleView = TextView(this).apply {
            text = LinkAssistantPolicy.subtitleForClipboardState(
                lastClipboardChangeElapsed > 0L &&
                    SystemClock.elapsedRealtime() - lastClipboardChangeElapsed <= CLIPBOARD_EVENT_WINDOW_MS
            )
            setTextColor(0xFFD8CBD9.toInt())
            textSize = 10.5f
            maxLines = 1
        }
        overlaySubtitleView = subtitleView
        labels.addView(subtitleView)
        root.addView(labels, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(14)
            y = dp(172)
        }

        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        root.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        val maxX = (resources.displayMetrics.widthPixels - view.width - dp(8)).coerceAtLeast(dp(8))
                        val maxY = (resources.displayMetrics.heightPixels - view.height - dp(8)).coerceAtLeast(dp(8))
                        params.x = (initialX - dx.roundToInt()).coerceIn(dp(8), maxX)
                        params.y = (initialY - dy.roundToInt()).coerceIn(dp(8), maxY)
                        runCatching { windowManager?.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        root.setOnClickListener { openInstaTubeForClipboard() }
        root.alpha = 0f
        root.translationX = dp(12).toFloat()
        runCatching {
            windowManager?.addView(root, params)
            overlayView = root
            root.animate().alpha(1f).translationX(0f).setDuration(180L).start()
        }.onFailure {
            AppSettings(this).overlayAssistantEnabled = false
            stopSelf()
        }
    }

    private fun openInstaTubeForClipboard() {
        // Consume this clipboard-change trigger so returning to Instagram does not resurrect the pill.
        lastClipboardChangeElapsed = 0L
        runCatching {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_READ_CLIPBOARD, true)
            startActivity(intent)
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        overlaySubtitleView = null
        runCatching { view.animate().cancel() }
        runCatching { windowManager?.removeView(view) }
    }

    private fun startAsForeground() {
        val notification = assistantNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun assistantNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            41,
            Intent(this, LinkAssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val openIntent = PendingIntent.getActivity(
            this,
            42,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.link_assistant_notification_title))
            .setContentText(getString(R.string.link_assistant_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.link_assistant_disable_action), stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.link_assistant_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.link_assistant_channel_description)
                }
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        removeOverlay()
        runCatching { clipboardManager?.removePrimaryClipChangedListener(clipboardListener) }
        clipboardManager = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "link_assistant"
        private const val NOTIFICATION_ID = 3301
        private const val ACTION_STOP = "com.roshan.instatube.action.STOP_LINK_ASSISTANT"
        private const val CLIPBOARD_EVENT_WINDOW_MS = 45_000L

        fun start(context: Context) {
            val intent = Intent(context, LinkAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkAssistantService::class.java))
        }
    }
}
