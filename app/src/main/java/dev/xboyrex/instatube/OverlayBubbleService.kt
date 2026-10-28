package dev.xboyrex.instatube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Floating Overlay Assistant that runs over Instagram & YouTube.
 * - Displays a sleek draggable edge bubble.
 * - Automatically detects copied/shared Instagram & YouTube links (even on Android 10–17 by
 *   temporarily focusing the overlay window for 120ms when tapped or triggered) and pops up
 *   a floating action card right over Instagram/YouTube with Video, Audio, Cover, and Copy Caption!
 */
class OverlayBubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var currentDetectedUrl: String = ""
    private var statusTextView: TextView? = null
    private var urlPreviewTextView: TextView? = null
    private var platformBadgeView: TextView? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        inspectClipboardAndPopup(autoTriggered = true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(OVERLAY_NOTIFICATION_ID, buildForegroundNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        runCatching { cm?.addPrimaryClipChangedListener(clipListener) }
        if (Settings.canDrawOverlays(this)) {
            showFloatingBubble()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val action = intent?.action
        if (action == ACTION_STOP_OVERLAY) {
            AppSettings(this).overlayEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        val incomingUrl = intent?.getStringExtra(EXTRA_OVERLAY_URL).orEmpty()
        if (incomingUrl.isNotBlank()) {
            val clean = InstagramDownloader.extractCleanUrl(incomingUrl)
            if (InstagramDownloader.isInstagramUrl(clean) || InstagramDownloader.isYouTubeUrl(clean)) {
                currentDetectedUrl = clean
                showExpandedOverlayPanel(clean)
            }
        } else if (action == ACTION_SCAN_CLIPBOARD) {
            probeClipboardWithBriefFocus()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        runCatching { cm?.removePrimaryClipChangedListener(clipListener) }
        removeBubbleView()
        removePanelView()
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun showFloatingBubble() {
        if (bubbleView != null) return
        val wm = windowManager ?: return

        val container = FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#6A11CB"),
                    Color.parseColor("#E1306C"),
                    Color.parseColor("#FF0033")
                )
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(2), Color.parseColor("#55FFFFFF"))
            }
            elevation = dp(10).toFloat()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val iconText = TextView(this).apply {
            text = "▶⬇"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }
        pill.addView(iconText)
        container.addView(pill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(84)
            y = resources.displayMetrics.heightPixels / 3
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        probeClipboardWithBriefFocus()
                    } else {
                        // Snap to nearest horizontal screen edge
                        val screenW = resources.displayMetrics.widthPixels
                        params.x = if (params.x + container.width / 2 < screenW / 2) dp(8) else screenW - container.width - dp(8)
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    true
                }
                else -> false
            }
        }

        bubbleParams = params
        bubbleView = container
        runCatching { wm.addView(container, params) }
    }

    /**
     * Temporarily clears FLAG_NOT_FOCUSABLE for 120ms so Android 10–17 allows reading the clipboard
     * while the user is inside Instagram or YouTube, then immediately opens the floating download card!
     */
    private fun probeClipboardWithBriefFocus() {
        val wm = windowManager ?: return
        val bView = bubbleView ?: return
        val bParams = bubbleParams ?: return

        bParams.flags = bParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        runCatching { wm.updateViewLayout(bView, bParams) }

        mainHandler.postDelayed({
            inspectClipboardAndPopup(autoTriggered = false)
            bParams.flags = bParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { wm.updateViewLayout(bView, bParams) }
        }, 120L)
    }

    private fun inspectClipboardAndPopup(autoTriggered: Boolean) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val raw = cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val clean = InstagramDownloader.extractCleanUrl(raw)
        if (InstagramDownloader.isInstagramUrl(clean) || InstagramDownloader.isYouTubeUrl(clean)) {
            if (autoTriggered && clean == currentDetectedUrl && panelView != null) return
            currentDetectedUrl = clean
            showExpandedOverlayPanel(clean)
        } else if (!autoTriggered) {
            if (currentDetectedUrl.isNotBlank()) {
                showExpandedOverlayPanel(currentDetectedUrl)
            } else {
                Toast.makeText(
                    this,
                    "Copy any Instagram or YouTube link first, then tap the bubble!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showExpandedOverlayPanel(targetUrl: String) {
        val wm = windowManager ?: return
        removePanelView()

        val isYt = InstagramDownloader.isYouTubeUrl(targetUrl)
        val accentHex = if (isYt) "#FF2D55" else "#A855F7"

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            setOnClickListener { removePanelView() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121521"))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            elevation = dp(18).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = TextView(this).apply {
            text = if (isYt) "YOUTUBE DETECTED" else "INSTAGRAM DETECTED"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor(accentHex))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }
        platformBadgeView = badge

        val spacer = View(this)
        headerRow.addView(badge)
        headerRow.addView(spacer, LinearLayout.LayoutParams(0, dp(1), 1f))

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(6), dp(4))
            setOnClickListener { removePanelView() }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        val titleView = TextView(this).apply {
            text = "InstaTube Quick Overlay"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }
        card.addView(titleView)

        val urlText = TextView(this).apply {
            text = targetUrl
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(10))
        }
        urlPreviewTextView = urlText
        card.addView(urlText)

        val statusView = TextView(this).apply {
            text = "Select an action below to save directly in background:"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, 0, 0, dp(12))
        }
        statusTextView = statusView
        card.addView(statusView)

        // 2x2 Action Buttons Grid
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnVideo = buildOverlayButton("⬇ Save Video", accentHex, true) {
            triggerOverlayAction(targetUrl, OverlayAction.VIDEO)
        }
        val btnAudio = buildOverlayButton("🎵 Audio (M4A)", "#1E2436", false) {
            triggerOverlayAction(targetUrl, OverlayAction.AUDIO)
        }
        row1.addView(btnVideo, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) })
        row1.addView(btnAudio, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(6) })
        card.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val btnCover = buildOverlayButton("🖼 Save Cover", "#1E2436", false) {
            triggerOverlayAction(targetUrl, OverlayAction.COVER)
        }
        val btnCaption = buildOverlayButton("📋 Copy Caption", "#1E2436", false) {
            triggerOverlayAction(targetUrl, OverlayAction.CAPTION)
        }
        row2.addView(btnCover, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) })
        row2.addView(btnCaption, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(6) })
        card.addView(row2)

        // Open Full Studio button
        val openAppBtn = TextView(this).apply {
            text = "Open in InstaTube Studio ↗"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener {
                removePanelView()
                val launchIntent = Intent(this@OverlayBubbleService, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, targetUrl)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(launchIntent)
            }
        }
        card.addView(openAppBtn)

        val cardLp = FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(36)
        }
        root.addView(card, cardLp)

        val pParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        panelParams = pParams
        panelView = root
        runCatching { wm.addView(root, pParams) }
    }

    private fun buildOverlayButton(label: String, bgHex: String, primary: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = dp(13).toFloat()
                if (!primary) setStroke(dp(1), Color.parseColor("#2EFFFFFF"))
            }
            setOnClickListener { onClick() }
        }
    }

    private enum class OverlayAction { VIDEO, AUDIO, COVER, CAPTION }

    private fun triggerOverlayAction(url: String, action: OverlayAction) {
        val context = applicationContext
        val settings = AppSettings(context)
        val isYt = InstagramDownloader.isYouTubeUrl(url)
        val notifId = (url + action.name).hashCode()

        statusTextView?.text = when (action) {
            OverlayAction.VIDEO -> "Starting video download… (Check top notification)"
            OverlayAction.AUDIO -> "Extracting M4A audio… (Check top notification)"
            OverlayAction.COVER -> "Saving HD cover artwork…"
            OverlayAction.CAPTION -> "Fetching caption…"
        }

        scope.launch {
            if (isYt) {
                val videoId = InstagramDownloader.extractYouTubeId(url)
                if (videoId == null) {
                    statusTextView?.text = "Invalid YouTube link"
                    return@launch
                }
                TransferNotificationCenter.showProgress(
                    context = context,
                    notificationId = notifId,
                    platform = "YouTube",
                    title = "Resolving YouTube stream…",
                    progressPercent = 10,
                    indeterminate = true
                )
                val res = runCatching {
                    withContext(Dispatchers.IO) {
                        val details = YouTube.details(videoId)
                        val opts = runCatching { Downloads.options(videoId) }.getOrDefault(emptyList())
                        details to opts
                    }
                }
                if (res.isFailure) {
                    val err = res.exceptionOrNull()?.message ?: "Failed to resolve YouTube link"
                    statusTextView?.text = err
                    TransferNotificationCenter.showFailed(context, notifId, "YouTube", url, err)
                    return@launch
                }
                val (details, opts) = res.getOrThrow()
                when (action) {
                    OverlayAction.VIDEO -> {
                        val h = opts.firstOrNull { it.height == 1080 }?.height
                            ?: opts.firstOrNull { it.height == 720 }?.height
                            ?: opts.firstOrNull()?.height ?: 720
                        Downloads.enqueue(details.item, h)
                        statusTextView?.text = "✓ Downloading ${h}p in background notification!"
                        mainHandler.postDelayed({ removePanelView() }, 1200L)
                    }
                    OverlayAction.AUDIO -> {
                        Downloads.enqueueAudio(details.item)
                        statusTextView?.text = "✓ Downloading M4A audio in background notification!"
                        mainHandler.postDelayed({ removePanelView() }, 1200L)
                    }
                    OverlayAction.COVER -> {
                        val covRes = runCatching {
                            withContext(Dispatchers.IO) {
                                saveYouTubeCoverToDownloads(
                                    videoId = details.item.id,
                                    title = details.item.title,
                                    channel = details.item.channel,
                                    thumbnailUrl = details.item.thumbnail,
                                    context = context,
                                    settings = settings
                                )
                            }
                        }
                        if (covRes.isSuccess) {
                            TransferNotificationCenter.showComplete(
                                context, notifId, "YouTube Cover", details.item.title, "${details.item.id}_cover.jpg"
                            )
                            statusTextView?.text = "✓ Cover saved to Vault!"
                            mainHandler.postDelayed({ removePanelView() }, 1200L)
                        } else {
                            statusTextView?.text = "Cover download failed"
                        }
                    }
                    OverlayAction.CAPTION -> {
                        val fullText = listOfNotNull(details.item.title, details.description).joinToString("\n\n")
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Caption", fullText))
                        statusTextView?.text = "✓ Title & Description copied to clipboard!"
                        mainHandler.postDelayed({ removePanelView() }, 1000L)
                    }
                }
            } else {
                TransferNotificationCenter.showProgress(
                    context = context,
                    notificationId = notifId,
                    platform = "Instagram",
                    title = "Fetching Instagram media…",
                    progressPercent = 20,
                    indeterminate = true
                )
                val igRes = runCatching {
                    withContext(Dispatchers.IO) {
                        InstagramDownloader.getMediaItems(url, settings.targetWidth())
                    }
                }
                if (igRes.isFailure) {
                    val err = igRes.exceptionOrNull()?.message ?: "Failed to fetch Instagram post"
                    statusTextView?.text = err
                    TransferNotificationCenter.showFailed(context, notifId, "Instagram", url, err)
                    return@launch
                }
                val items = igRes.getOrThrow()
                when (action) {
                    OverlayAction.VIDEO -> {
                        val saveRes = runCatching {
                            withContext(Dispatchers.IO) {
                                items.forEachIndexed { idx, item ->
                                    val pct = (((idx + 1).toFloat() / items.size) * 100).toInt()
                                    TransferNotificationCenter.showProgress(
                                        context = context,
                                        notificationId = notifId,
                                        platform = "Instagram",
                                        title = item.meta?.username?.let { "@$it" } ?: "Saving Instagram media…",
                                        progressPercent = pct,
                                        indeterminate = false,
                                        detailText = "Saving item ${idx + 1} of ${items.size} ($pct%)"
                                    )
                                    saveToDownloads(item, context, settings)
                                }
                            }
                        }
                        if (saveRes.isSuccess) {
                            statusTextView?.text = "✓ Saved ${items.size} item(s) to Vault!"
                            mainHandler.postDelayed({ removePanelView() }, 1200L)
                        } else {
                            statusTextView?.text = saveRes.exceptionOrNull()?.message ?: "Save failed"
                        }
                    }
                    OverlayAction.AUDIO -> {
                        val videoItem = items.firstOrNull { it.isVideo }
                        if (videoItem == null) {
                            statusTextView?.text = "No video/audio track in this post"
                            return@launch
                        }
                        val audRes = runCatching {
                            withContext(Dispatchers.IO) {
                                saveAudioToDownloads(videoItem, context, settings)
                            }
                        }
                        if (audRes.isSuccess) {
                            TransferNotificationCenter.showComplete(
                                context, notifId, "Instagram Audio",
                                videoItem.meta?.username?.let { "@$it Audio" } ?: "Audio Track",
                                "${videoItem.baseName}.m4a"
                            )
                            statusTextView?.text = "✓ Audio (.m4a) saved to Vault!"
                            mainHandler.postDelayed({ removePanelView() }, 1200L)
                        } else {
                            statusTextView?.text = "Audio extraction failed"
                        }
                    }
                    OverlayAction.COVER -> {
                        val covRes = runCatching {
                            withContext(Dispatchers.IO) {
                                items.forEach { saveCoverToDownloads(it, context, settings) }
                            }
                        }
                        if (covRes.isSuccess) {
                            statusTextView?.text = "✓ Cover image saved to Vault!"
                            mainHandler.postDelayed({ removePanelView() }, 1200L)
                        } else {
                            statusTextView?.text = "Cover save failed"
                        }
                    }
                    OverlayAction.CAPTION -> {
                        val cap = items.firstOrNull()?.meta?.caption.orEmpty()
                        if (cap.isNotBlank()) {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Instagram Caption", cap))
                            statusTextView?.text = "✓ Caption copied to clipboard!"
                        } else {
                            statusTextView?.text = "No caption on this post"
                        }
                        mainHandler.postDelayed({ removePanelView() }, 1000L)
                    }
                }
            }
        }
    }

    private fun removeBubbleView() {
        val wm = windowManager ?: return
        bubbleView?.let { v ->
            runCatching { wm.removeView(v) }
            bubbleView = null
        }
    }

    private fun removePanelView() {
        val wm = windowManager ?: return
        panelView?.let { v ->
            runCatching { wm.removeView(v) }
            panelView = null
        }
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = "instatube_overlay_assistant"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                channelId,
                "Floating Overlay Assistant",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the InstaTube floating overlay ready over Instagram and YouTube"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        val scanIntent = Intent(this, OverlayBubbleService::class.java).apply {
            action = ACTION_SCAN_CLIPBOARD
        }
        val pendingScan = PendingIntent.getService(
            this, 401, scanIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayBubbleService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val pendingStop = PendingIntent.getService(
            this, 402, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("InstaTube Overlay Active")
            .setContentText("Tap bubble over Instagram/YouTube or tap here to scan copied link")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingScan)
            .addAction(android.R.drawable.ic_menu_search, "Scan Link", pendingScan)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", pendingStop)
            .build()
    }

    companion object {
        private const val OVERLAY_NOTIFICATION_ID = 4401
        const val ACTION_SCAN_CLIPBOARD = "dev.xboyrex.instatube.action.SCAN_CLIPBOARD"
        const val ACTION_STOP_OVERLAY = "dev.xboyrex.instatube.action.STOP_OVERLAY"
        const val EXTRA_OVERLAY_URL = "dev.xboyrex.instatube.extra.OVERLAY_URL"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, initialUrl: String? = null) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, OverlayBubbleService::class.java).apply {
                if (!initialUrl.isNullOrBlank()) {
                    putExtra(EXTRA_OVERLAY_URL, initialUrl)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayBubbleService::class.java))
        }
    }
}
