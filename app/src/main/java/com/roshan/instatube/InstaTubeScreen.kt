package com.roshan.instatube

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private val StudioBlue = Color(0xFF0A84FF)
private val StudioCrimson = Color(0xFFFF2D55)
private val StudioViolet = Color(0xFF8B5CF6)
private val StudioEmerald = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InstaTubeDownloaderScreen(
    initialUrl: String = "",
    initialUrlKey: Any = initialUrl,
    clipboardCandidateUrl: String = "",
    clipboardEventKey: Int = 0,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    settings: AppSettings,
    selectedFolderName: String,
    checkStoragePermission: () -> Boolean,
    requestStoragePermission: () -> Unit,
    onChooseFolder: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onFolderAccessLost: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDownloadsSaved: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onShowNotice: (String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var savingActionLabel by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var fullError by remember { mutableStateOf<String?>(null) }
    var media by remember { mutableStateOf<List<MediaResult>?>(null) }
    var ytDetails by remember { mutableStateOf<VideoDetails?>(null) }
    var ytOptions by remember { mutableStateOf<List<DownloadOption>?>(null) }
    var selectedYtHeight by remember { mutableStateOf<Int?>(null) }
    var deselectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var targetWidth by remember { mutableIntStateOf(settings.targetWidth()) }
    var profileToBrowse by remember { mutableStateOf<String?>(null) }
    var captionExpanded by remember { mutableStateOf(false) }
    var autoDetectEnabled by remember { mutableStateOf(settings.autoDetectClipboard) }
    var pendingAutoDownload by remember { mutableStateOf(false) }

    val dark = isDarkTheme()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val genericError = stringResource(R.string.error_generic)
    val emptyUrlError = stringResource(R.string.error_empty_url)
    val invalidUrlError = stringResource(R.string.error_invalid_url)
    val downloadFailedError = stringResource(R.string.error_download_failed)

    LaunchedEffect(initialUrlKey) {
        if (initialUrl.isNotBlank()) {
            url = initialUrl
            settings.lastHandledClipboardUrl = initialUrl
            media = null
            ytDetails = null
            ytOptions = null
            urlError = null
            fullError = null
            if (settings.autoDownloadOnPaste) pendingAutoDownload = true
        }
    }

    LaunchedEffect(clipboardEventKey, clipboardCandidateUrl) {
        autoDetectEnabled = settings.autoDetectClipboard
        if (clipboardCandidateUrl.isNotBlank() && settings.autoDetectClipboard) {
            val cleanClip = InstagramDownloader.extractCleanUrl(clipboardCandidateUrl)
            val alreadyHandled = settings.lastHandledClipboardUrl == cleanClip
            if (cleanClip.isNotBlank() && (!alreadyHandled || url.isBlank())) {
                settings.lastHandledClipboardUrl = cleanClip
                if (url != cleanClip) {
                    url = cleanClip
                    media = null
                    ytDetails = null
                    ytOptions = null
                    urlError = null
                    fullError = null
                    if (settings.autoDownloadOnPaste) pendingAutoDownload = true
                    onShowNotice("Copied link loaded from clipboard")
                }
            }
        }
    }

    val cleanedUrl = remember(url) { InstagramDownloader.extractCleanUrl(url) }
    val isStory = remember(cleanedUrl) { InstagramDownloader.isStoryUrl(cleanedUrl) }
    val isYouTube = remember(cleanedUrl) { InstagramDownloader.isYouTubeUrl(cleanedUrl) }
    val isInstagram = remember(cleanedUrl) { InstagramDownloader.isInstagramUrl(cleanedUrl) }
    val isProfile = remember(cleanedUrl) { isInstagram && InstagramDownloader.isProfileUrl(cleanedUrl) }

    val activeAccent by animateColorAsState(
        targetValue = when {
            isYouTube -> StudioCrimson
            isInstagram -> StudioViolet
            else -> c.primary
        },
        animationSpec = tween(280),
        label = "accent"
    )
    val activeAccentContent = readableContentColor(activeAccent)

    // Automatically resolve Instagram or YouTube media preview when URL changes
    LaunchedEffect(cleanedUrl, targetWidth) {
        if (cleanedUrl.isBlank() || isStory) {
            media = null
            ytDetails = null
            ytOptions = null
            pendingAutoDownload = false
            return@LaunchedEffect
        }
        if (isYouTube) {
            val videoId = InstagramDownloader.extractYouTubeId(cleanedUrl) ?: return@LaunchedEffect
            delay(200)
            isLoading = true
            urlError = null
            fullError = null
            media = null
            ytDetails = null
            ytOptions = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val details = YouTube.details(videoId)
                    val opts = runCatching { Downloads.options(videoId) }.getOrDefault(emptyList())
                    details to opts
                }
            }
            isLoading = false
            if (result.isFailure) {
                pendingAutoDownload = false
                fullError = result.exceptionOrNull()?.message ?: genericError
                return@LaunchedEffect
            }
            val (details, opts) = result.getOrThrow()
            hapticStart(context, settings.hapticsEnabled)
            ytDetails = details
            ytOptions = opts
            val preferredHeight = when (settings.effectiveQuality()) {
                DownloadQuality.DATA_SAVER -> opts.firstOrNull { it.height <= 480 }?.height ?: opts.firstOrNull()?.height
                DownloadQuality.CUSTOM -> opts.minByOrNull { kotlin.math.abs(it.height - settings.customWidth) }?.height
                else -> opts.firstOrNull { it.height == 1080 }?.height
                    ?: opts.firstOrNull { it.height == 720 }?.height
                    ?: opts.firstOrNull()?.height
            }
            selectedYtHeight = preferredHeight
            if (pendingAutoDownload) {
                pendingAutoDownload = false
                val h = preferredHeight ?: 720
                val added = Downloads.enqueue(details.item, h)
                if (added) onShowNotice("Auto-downloading ${h}p · ${details.item.title}")
            }
            return@LaunchedEffect
        }
        if (isInstagram && !isProfile) {
            delay(220)
            isLoading = true
            urlError = null
            fullError = null
            media = null
            ytDetails = null
            ytOptions = null
            deselectedIndices = emptySet()
            val items = runCatching {
                withContext(Dispatchers.IO) {
                    InstagramDownloader.getMediaItems(cleanedUrl, targetWidth)
                }
            }
            isLoading = false
            if (items.isFailure) {
                pendingAutoDownload = false
                fullError = items.exceptionOrNull()?.message ?: genericError
                return@LaunchedEffect
            }
            hapticStart(context, settings.hapticsEnabled)
            val resolvedItems = items.getOrThrow()
            media = resolvedItems
            if (pendingAutoDownload && resolvedItems.isNotEmpty()) {
                pendingAutoDownload = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || checkStoragePermission()) {
                    isSaving = true
                    savingActionLabel = "Auto-saving media…"
                    val progressNotification = InlineDownloadNotifications.start(context, resolvedItems.size, "Saving Instagram media")
                    var savedCount = 0
                    val dlRes = runCatching {
                        withContext(Dispatchers.IO) {
                            resolvedItems.forEachIndexed { index, item ->
                                progressNotification.update(savedCount, "Saving item ${index + 1} of ${resolvedItems.size}")
                                saveToDownloads(item, context, settings)
                                savedCount++
                                progressNotification.update(savedCount, "Saved $savedCount of ${resolvedItems.size} items")
                            }
                        }
                    }
                    isSaving = false
                    savingActionLabel = null
                    if (dlRes.isSuccess) {
                        progressNotification.complete("Saved ${resolvedItems.size} item(s) to Downloads/$selectedFolderName")
                        hapticComplete(context, settings.hapticsEnabled)
                        onShowNotice("Saved ${resolvedItems.size} item(s) to Downloads/$selectedFolderName")
                    } else {
                        progressNotification.fail("Saved $savedCount of ${resolvedItems.size} items. ${dlRes.exceptionOrNull()?.message ?: downloadFailedError}")
                        fullError = dlRes.exceptionOrNull()?.message ?: downloadFailedError
                    }
                }
            }
            return@LaunchedEffect
        }
        media = null
        ytDetails = null
        ytOptions = null
    }

    viewerIndex?.let { index ->
        media?.let { MediaViewer(it, null, index) { viewerIndex = null } }
    }

    val activeYtTasks = Downloads.tasks.filter {
        it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued
    }

    val cardBg = if (dark) Color(0xFF131620) else Color.White
    val cardBorder = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val recessedBg = if (dark) Color(0xFF0B0D14) else Color(0xFFF1F3F8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .imePadding()
    ) {
        // Subtle ambient top glow that shifts with active link platform
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = if (dark) 0.16f else 0.10f),
                            Color.Transparent
                        ),
                        radius = 700f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp)
        ) {
            // ── Top Studio Header ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioBrandMark(
                        size = 36.dp,
                        isBusy = isLoading || isSaving || activeYtTasks.isNotEmpty()
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "InstaTube",
                                fontFamily = RoundedHeavy,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = c.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(activeAccent.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = activeAccent
                                )
                            }
                        }
                        Text(
                            text = "Media Extraction Engine",
                            fontSize = 11.5.sp,
                            color = c.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeYtTasks.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StudioCrimson.copy(alpha = 0.16f))
                                .border(1.dp, StudioCrimson.copy(alpha = 0.35f), CircleShape)
                                .clickable { onOpenLibrary() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { Downloads.progress },
                                color = StudioCrimson,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${(Downloads.progress * 100).roundToLong()}%",
                                color = StudioCrimson,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (autoDetectEnabled) activeAccent.copy(alpha = 0.14f)
                                else cardBg
                            )
                            .border(
                                1.dp,
                                if (autoDetectEnabled) activeAccent.copy(alpha = 0.35f) else cardBorder,
                                CircleShape
                            )
                            .clickable {
                                val next = !autoDetectEnabled
                                autoDetectEnabled = next
                                settings.autoDetectClipboard = next
                                onShowNotice(
                                    if (next) "Clipboard Auto-Detect ON"
                                    else "Clipboard Auto-Detect OFF"
                                )
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Sparkle,
                            contentDescription = null,
                            tint = if (autoDetectEnabled) activeAccent else c.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (autoDetectEnabled) "Auto-Paste" else "Manual",
                            color = if (autoDetectEnabled) activeAccent else c.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Smart Clipboard Suggestion Banner ────────────────────────
            val cleanClipboardCandidate = remember(clipboardCandidateUrl) {
                InstagramDownloader.extractCleanUrl(clipboardCandidateUrl)
            }
            AnimatedVisibility(
                visible = cleanClipboardCandidate.isNotBlank() && cleanClipboardCandidate != cleanedUrl,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, activeAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable {
                            url = cleanClipboardCandidate
                            settings.lastHandledClipboardUrl = cleanClipboardCandidate
                            urlError = null
                            fullError = null
                            media = null
                            ytDetails = null
                            ytOptions = null
                            if (settings.autoDownloadOnPaste) pendingAutoDownload = true
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(activeAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.ContentPaste,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clipboard Link Detected",
                            color = c.onSurface,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = cleanClipboardCandidate,
                            color = c.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(activeAccent)
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Load",
                            color = activeAccentContent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Minimal Inset Command Bar Dock ───────────────────────────
            val hasLoadedPreview = media != null || ytDetails != null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(activeAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isYouTube -> "YouTube Stream"
                                isProfile -> "Instagram Profile"
                                isInstagram -> "Instagram Media"
                                else -> "Source URL"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isYouTube || isInstagram) activeAccent else c.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (url.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(recessedBg)
                                    .clickable {
                                        url = ""
                                        urlError = null
                                        fullError = null
                                        media = null
                                        ytDetails = null
                                        ytOptions = null
                                        pendingAutoDownload = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Close,
                                    contentDescription = "Clear",
                                    tint = c.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clear",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.onSurfaceVariant
                                )
                            }
                        }

                        val pasteDescription = stringResource(R.string.paste_clipboard_description)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(activeAccent.copy(alpha = 0.14f))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (pasted.isNotEmpty()) {
                                        val clean = InstagramDownloader.extractCleanUrl(pasted)
                                        url = clean
                                        settings.lastHandledClipboardUrl = clean
                                        urlError = null
                                        media = null
                                        ytDetails = null
                                        ytOptions = null
                                        fullError = null
                                        if (settings.autoDownloadOnPaste) pendingAutoDownload = true
                                    } else {
                                        onShowNotice("Clipboard is empty")
                                    }
                                }
                                .semantics { contentDescription = pasteDescription }
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.ContentPaste,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Paste",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeAccent
                            )
                        }
                    }
                }

                // Recessed borderless URL input box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(recessedBg)
                        .border(
                            1.dp,
                            if (urlError != null) c.error else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    if (url.isEmpty()) {
                        Text(
                            text = "https://instagram.com/reel/... or youtu.be/...",
                            color = c.onSurfaceVariant.copy(alpha = 0.55f),
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            if (urlError != null) urlError = null
                            media = null
                            ytDetails = null
                            ytOptions = null
                            fullError = null
                        },
                        enabled = !isSaving,
                        textStyle = TextStyle(
                            color = c.onSurface,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(activeAccent),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (urlError != null) {
                    Text(
                        text = urlError!!,
                        color = c.error,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isStory,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(150))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background( Color(0xFFF59E0B).copy(alpha = 0.14f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.story_not_supported_title),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                            Text(
                                text = stringResource(R.string.story_not_supported_body),
                                fontSize = 11.5.sp,
                                color = c.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Inline resolving progress indicator
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = activeAccent
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isYouTube) "Inspecting YouTube streams & bitrates…"
                            else "Inspecting Instagram media & metadata…",
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Smart Context-Aware Button: only visible when no media card is loaded (or for @profile links)
                val showPrimaryInputButton = !isLoading && !isStory && (isProfile || !hasLoadedPreview)
                AnimatedVisibility(
                    visible = showPrimaryInputButton,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(activeAccent)
                                .clickable(enabled = !isSaving) {
                                    val trimmed = InstagramDownloader.extractCleanUrl(url)
                                    when {
                                        trimmed.isBlank() -> {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                            val clean = InstagramDownloader.extractCleanUrl(pasted)
                                            if (InstagramDownloader.isInstagramUrl(clean) || InstagramDownloader.isYouTubeUrl(clean)) {
                                                url = clean
                                                settings.lastHandledClipboardUrl = clean
                                                urlError = null
                                            } else {
                                                urlError = emptyUrlError
                                            }
                                        }
                                        !InstagramDownloader.isInstagramUrl(trimmed) && !InstagramDownloader.isYouTubeUrl(trimmed) ->
                                            urlError = invalidUrlError
                                        isProfile ->
                                            profileToBrowse = InstagramDownloader.extractProfileUsername(trimmed)
                                        isYouTube -> coroutineScope.launch {
                                            fullError = null
                                            val videoId = InstagramDownloader.extractYouTubeId(trimmed) ?: run {
                                                urlError = invalidUrlError
                                                return@launch
                                            }
                                            isLoading = true
                                            val fetched = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    val d = YouTube.details(videoId)
                                                    val o = runCatching { Downloads.options(videoId) }.getOrDefault(emptyList())
                                                    d to o
                                                }
                                            }
                                            isLoading = false
                                            if (fetched.isFailure) {
                                                fullError = fetched.exceptionOrNull()?.message ?: genericError
                                                return@launch
                                            }
                                            val (d, o) = fetched.getOrThrow()
                                            ytDetails = d
                                            ytOptions = o
                                            selectedYtHeight = o.firstOrNull { it.height == 1080 }?.height
                                                ?: o.firstOrNull { it.height == 720 }?.height
                                                ?: o.firstOrNull()?.height
                                        }
                                        else -> coroutineScope.launch {
                                            fullError = null
                                            isLoading = true
                                            val fetched = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    InstagramDownloader.getMediaItems(trimmed, settings.targetWidth())
                                                }
                                            }
                                            isLoading = false
                                            if (fetched.isFailure) {
                                                fullError = fetched.exceptionOrNull()?.message ?: genericError
                                                return@launch
                                            }
                                            media = fetched.getOrThrow()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        isProfile -> AppIcons.Image
                                        url.isBlank() -> AppIcons.ContentPaste
                                        else -> AppIcons.Download
                                    },
                                    contentDescription = null,
                                    tint = activeAccentContent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isProfile -> stringResource(R.string.open_profile_label)
                                        url.isBlank() -> "Paste & Inspect Link"
                                        else -> "Inspect Media"
                                    },
                                    color = activeAccentContent,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Idle Capabilities Strip (Shown only when no preview is active) ──
            AnimatedVisibility(
                visible = !hasLoadedPreview && !isLoading && fullError == null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudioCapabilityTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.Movie,
                            accent = StudioViolet,
                            title = "Instagram",
                            subtitle = "Reels, Carousels, Profiles & HD Covers",
                            cardBg = cardBg,
                            cardBorder = cardBorder
                        )
                        StudioCapabilityTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.PlayArrow,
                            accent = StudioCrimson,
                            title = "YouTube",
                            subtitle = "4K / 1080p MP4, Shorts & Streams",
                            cardBg = cardBg,
                            cardBorder = cardBorder
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudioCapabilityTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.AudioWave,
                            accent = StudioBlue,
                            title = "Original Audio",
                            subtitle = "Direct M4A demuxer without re-encoding",
                            cardBg = cardBg,
                            cardBorder = cardBorder
                        )
                        StudioCapabilityTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.Folder,
                            accent = StudioEmerald,
                            title = "Vault Directory",
                            subtitle = "Downloads / $selectedFolderName",
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            onClick = onChooseFolder
                        )
                    }
                }
            }

            // ── Instagram Media Inspector & 2x2 Bento Action Grid ────────
            AnimatedVisibility(
                visible = media != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160))
            ) {
                val items = media ?: emptyList()
                val firstMeta = items.firstOrNull()?.meta
                val hasVideo = items.any { it.isVideo }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(StudioViolet.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (items.size > 1) "CAROUSEL · ${items.size}" else if (hasVideo) "REEL / VIDEO" else "PHOTO",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StudioViolet
                                )
                            }
                            items.firstOrNull { it.reduced }?.let {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${it.width}px",
                                    fontSize = 11.sp,
                                    color = c.onSurfaceVariant
                                )
                            }
                        }

                        firstMeta?.username?.takeIf { it.isNotBlank() }?.let { uname ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(recessedBg)
                                    .clickable {
                                        copyDownloadText(
                                            context = context,
                                            label = context.getString(R.string.handle_clipboard_label),
                                            text = "@$uname",
                                            confirmation = R.string.handle_copied
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "@$uname",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.onSurface
                                )
                            }
                        }
                    }

                    firstMeta?.song?.takeIf { it.isNotBlank() }?.let { songName ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.AudioWave,
                                contentDescription = null,
                                tint = StudioViolet,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = songName,
                                fontSize = 11.5.sp,
                                color = c.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items.forEachIndexed { index, item ->
                            MediaThumbnail(
                                item = item,
                                isCarousel = items.size > 1,
                                isSelected = index !in deselectedIndices,
                                onToggleSelected = {
                                    deselectedIndices = if (index in deselectedIndices)
                                        deselectedIndices - index else deselectedIndices + index
                                },
                                onOpen = { viewerIndex = index }
                            )
                        }
                    }

                    firstMeta?.caption?.takeIf { it.isNotBlank() }?.let { cap ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(recessedBg)
                                .clickable { captionExpanded = !captionExpanded }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = cap,
                                fontSize = 12.sp,
                                color = c.onSurfaceVariant,
                                maxLines = if (captionExpanded) 14 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isSaving && savingActionLabel != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = StudioViolet
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = savingActionLabel!!,
                                fontSize = 12.sp,
                                color = StudioViolet,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val selectedItems = items.filterIndexed { i, _ -> i !in deselectedIndices }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoActionTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.Download,
                            title = when {
                                items.size > 1 -> "Save Selected (${selectedItems.size})"
                                hasVideo -> "Save Video"
                                else -> "Save Photo"
                            },
                            subtitle = if (hasVideo) "MP4 · Full HD" else "JPG · Original",
                            primary = true,
                            accent = StudioViolet,
                            recessedBg = recessedBg,
                            enabled = !isSaving && selectedItems.isNotEmpty(),
                            onClick = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                    requestStoragePermission()
                                    return@BentoActionTile
                                }
                                coroutineScope.launch {
                                    hapticStart(context, settings.hapticsEnabled)
                                    isSaving = true
                                    savingActionLabel = "Saving media to Vault…"
                                    val progressNotification = InlineDownloadNotifications.start(context, selectedItems.size, "Saving Instagram media")
                                    var savedCount = 0
                                    var fellBack = false
                                    val res = runCatching {
                                        withContext(Dispatchers.IO) {
                                            selectedItems.forEachIndexed { index, item ->
                                                progressNotification.update(savedCount, "Saving item ${index + 1} of ${selectedItems.size}")
                                                if (saveToDownloads(item, context, settings).fellBackToDefaultFolder) fellBack = true
                                                savedCount++
                                                progressNotification.update(savedCount, "Saved $savedCount of ${selectedItems.size} items")
                                            }
                                        }
                                    }
                                    isSaving = false
                                    savingActionLabel = null
                                    if (fellBack) onFolderAccessLost()
                                    if (res.isSuccess) {
                                        progressNotification.complete("Saved ${selectedItems.size} item(s) to Downloads/$selectedFolderName")
                                        hapticComplete(context, settings.hapticsEnabled)
                                        onShowNotice("Saved to Downloads/$selectedFolderName")
                                    } else {
                                        progressNotification.fail("Saved $savedCount of ${selectedItems.size} items. ${res.exceptionOrNull()?.message ?: downloadFailedError}")
                                        fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                    }
                                }
                            }
                        )

                        BentoActionTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.AudioWave,
                            title = "Audio Track",
                            subtitle = "M4A · Demuxed",
                            primary = false,
                            accent = StudioViolet,
                            recessedBg = recessedBg,
                            enabled = !isSaving && hasVideo,
                            onClick = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                    requestStoragePermission()
                                    return@BentoActionTile
                                }
                                val videoItem = selectedItems.firstOrNull { it.isVideo } ?: items.firstOrNull { it.isVideo }
                                if (videoItem == null) {
                                    onShowNotice("No audio stream in this post")
                                    return@BentoActionTile
                                }
                                coroutineScope.launch {
                                    hapticStart(context, settings.hapticsEnabled)
                                    isSaving = true
                                    savingActionLabel = "Extracting M4A audio…"
                                    val progressNotification = InlineDownloadNotifications.start(context, 1, "Saving audio")
                                    progressNotification.update(0, "Extracting an M4A audio track")
                                    val res = runCatching {
                                        withContext(Dispatchers.IO) { saveAudioToDownloads(videoItem, context, settings) }
                                    }
                                    isSaving = false
                                    savingActionLabel = null
                                    if (res.isSuccess) {
                                        progressNotification.complete("Audio (.m4a) saved to Downloads/$selectedFolderName")
                                        hapticComplete(context, settings.hapticsEnabled)
                                        onShowNotice("Audio (.m4a) saved to Downloads/$selectedFolderName")
                                    } else {
                                        progressNotification.fail(res.exceptionOrNull()?.message ?: downloadFailedError)
                                        fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoActionTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.Image,
                            title = "Save Cover",
                            subtitle = "JPG · Artwork",
                            primary = false,
                            accent = StudioViolet,
                            recessedBg = recessedBg,
                            enabled = !isSaving && items.isNotEmpty(),
                            onClick = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                    requestStoragePermission()
                                    return@BentoActionTile
                                }
                                coroutineScope.launch {
                                    hapticStart(context, settings.hapticsEnabled)
                                    isSaving = true
                                    savingActionLabel = "Saving cover artwork…"
                                    val targets = selectedItems.ifEmpty { items }
                                    val progressNotification = InlineDownloadNotifications.start(context, targets.size, "Saving cover artwork")
                                    var savedCount = 0
                                    val res = runCatching {
                                        withContext(Dispatchers.IO) {
                                            targets.forEachIndexed { index, item ->
                                                progressNotification.update(savedCount, "Saving cover ${index + 1} of ${targets.size}")
                                                saveCoverToDownloads(item, context, settings)
                                                savedCount++
                                                progressNotification.update(savedCount, "Saved $savedCount of ${targets.size} covers")
                                            }
                                        }
                                    }
                                    isSaving = false
                                    savingActionLabel = null
                                    if (res.isSuccess) {
                                        progressNotification.complete("Saved ${targets.size} cover(s) to Downloads/$selectedFolderName")
                                        hapticComplete(context, settings.hapticsEnabled)
                                        onShowNotice("Cover saved to Downloads/$selectedFolderName")
                                    } else {
                                        progressNotification.fail("Saved $savedCount of ${targets.size} covers. ${res.exceptionOrNull()?.message ?: downloadFailedError}")
                                        fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                    }
                                }
                            }
                        )

                        BentoActionTile(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.ContentCopy,
                            title = "Copy Caption",
                            subtitle = "Text & Hashtags",
                            primary = false,
                            accent = StudioViolet,
                            recessedBg = recessedBg,
                            enabled = true,
                            onClick = {
                                val captionText = firstMeta?.caption?.takeIf { it.isNotBlank() }
                                if (captionText != null) {
                                    copyDownloadText(
                                        context = context,
                                        label = context.getString(R.string.caption_clipboard_label),
                                        text = captionText,
                                        confirmation = R.string.caption_copied
                                    )
                                } else {
                                    Toast.makeText(context, R.string.no_caption, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // ── YouTube Media Inspector & 2x2 Bento Action Grid ──────────
            AnimatedVisibility(
                visible = ytDetails != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160))
            ) {
                val details = ytDetails
                if (details != null) {
                    val item = details.item
                    val opts = ytOptions ?: emptyList()
                    val matchingTasks = Downloads.tasks.filter { it.videoId == item.id }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                            .padding(16.dp)
                    ) {
                        // Guaranteed HD YouTube Thumbnail Frame
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(recessedBg),
                            contentAlignment = Alignment.Center
                        ) {
                            YouTubeThumbnailBox(
                                videoId = item.id,
                                fallbackUrl = item.thumbnail,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (cleanedUrl.contains("/shorts/")) "YOUTUBE SHORT" else "YOUTUBE STREAM",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            if (item.durationSec > 0) {
                                DurationPill(
                                    seconds = item.durationSec,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = item.title,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = listOfNotNull(
                                item.channel.takeIf { it.isNotBlank() },
                                item.views.takeIf { it > 0 }?.let { "${formatCount(it)} views" },
                                item.uploaded
                            ).joinToString(" · "),
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (details.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(recessedBg)
                                    .clickable { captionExpanded = !captionExpanded }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = details.description,
                                    fontSize = 12.sp,
                                    color = c.onSurfaceVariant,
                                    maxLines = if (captionExpanded) 14 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Custom Inset Segmented Quality Bar
                        if (opts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Stream Resolution",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.onSurfaceVariant
                                )
                                selectedYtHeight?.let { h ->
                                    opts.firstOrNull { it.height == h }?.let { selOpt ->
                                        if (selOpt.estimatedBytes > 0) {
                                            Text(
                                                text = "~${formatBytes(selOpt.estimatedBytes)}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = StudioCrimson
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(recessedBg)
                                    .padding(4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                opts.forEach { opt ->
                                    val isSel = selectedYtHeight == opt.height
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) StudioCrimson else Color.Transparent)
                                            .clickable { selectedYtHeight = opt.height }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = opt.label + (if (opt.estimatedBytes > 0) " · ${formatBytes(opt.estimatedBytes)}" else ""),
                                            color = if (isSel) readableContentColor(StudioCrimson) else c.onSurfaceVariant,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Live inline transfer progress strip
                        if (matchingTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            matchingTasks.forEach { task ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(recessedBg)
                                        .clickable { onOpenLibrary() }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = (if (task.height == 0) "Audio (M4A)" else "Video (${task.height}p)") + " · " + when (task.status) {
                                                DownloadStatus.Queued -> "Queued…"
                                                DownloadStatus.Downloading -> "Downloading ${(task.progress * 100).roundToLong()}%"
                                                DownloadStatus.Paused -> "Paused"
                                                DownloadStatus.Complete -> "Saved in Vault"
                                                DownloadStatus.Failed -> task.error ?: "Failed"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (task.status == DownloadStatus.Complete) StudioEmerald else c.onSurface
                                        )
                                        Text(
                                            text = if (task.total > 0) "${formatBytes(task.downloaded)} / ${formatBytes(task.total)}" else "VIEW",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StudioCrimson
                                        )
                                    }
                                    if (task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Queued) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { task.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape),
                                            color = StudioCrimson
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val chosenHeight = selectedYtHeight ?: opts.firstOrNull()?.height ?: 720
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoActionTile(
                                modifier = Modifier.weight(1f),
                                icon = AppIcons.Download,
                                title = "Save Video",
                                subtitle = "MP4 · ${chosenHeight}p Stream",
                                primary = true,
                                accent = StudioCrimson,
                                recessedBg = recessedBg,
                                enabled = true,
                                onClick = {
                                    hapticStart(context, settings.hapticsEnabled)
                                    val added = Downloads.enqueue(item, chosenHeight)
                                    onShowNotice(
                                        if (added) "Downloading ${chosenHeight}p · ${item.title}"
                                        else "Stream already in Downloads"
                                    )
                                }
                            )

                            BentoActionTile(
                                modifier = Modifier.weight(1f),
                                icon = AppIcons.AudioWave,
                                title = "Audio Track",
                                subtitle = "M4A · Best Bitrate",
                                primary = false,
                                accent = StudioCrimson,
                                recessedBg = recessedBg,
                                enabled = true,
                                onClick = {
                                    hapticStart(context, settings.hapticsEnabled)
                                    val added = Downloads.enqueueAudio(item)
                                    onShowNotice(
                                        if (added) "Downloading Audio (M4A) · ${item.title}"
                                        else "Audio already in Downloads"
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoActionTile(
                                modifier = Modifier.weight(1f),
                                icon = AppIcons.Image,
                                title = "Save Cover",
                                subtitle = "JPG · Max-Res Frame",
                                primary = false,
                                accent = StudioCrimson,
                                recessedBg = recessedBg,
                                enabled = !isSaving,
                                onClick = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                        requestStoragePermission()
                                        return@BentoActionTile
                                    }
                                    coroutineScope.launch {
                                        hapticStart(context, settings.hapticsEnabled)
                                        isSaving = true
                                        savingActionLabel = "Saving HD frame…"
                                        val progressNotification = InlineDownloadNotifications.start(context, 1, "Saving YouTube cover")
                                        progressNotification.update(0, "Fetching the highest-resolution cover")
                                        val res = runCatching {
                                            withContext(Dispatchers.IO) {
                                                saveYouTubeCoverToDownloads(
                                                    videoId = item.id,
                                                    title = item.title,
                                                    channel = item.channel,
                                                    thumbnailUrl = item.thumbnail,
                                                    context = context,
                                                    settings = settings
                                                )
                                            }
                                        }
                                        isSaving = false
                                        savingActionLabel = null
                                        if (res.isSuccess) {
                                            progressNotification.complete("HD cover saved to Downloads/$selectedFolderName")
                                            hapticComplete(context, settings.hapticsEnabled)
                                            onShowNotice("HD cover saved to Downloads/$selectedFolderName")
                                        } else {
                                            progressNotification.fail(res.exceptionOrNull()?.message ?: downloadFailedError)
                                            fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                        }
                                    }
                                }
                            )

                            BentoActionTile(
                                modifier = Modifier.weight(1f),
                                icon = AppIcons.ContentCopy,
                                title = "Copy Caption",
                                subtitle = "Title & Description",
                                primary = false,
                                accent = StudioCrimson,
                                recessedBg = recessedBg,
                                enabled = true,
                                onClick = {
                                    val fullCaption = listOfNotNull(
                                        item.title.takeIf { it.isNotBlank() },
                                        details.description.takeIf { it.isNotBlank() }
                                    ).joinToString("\n\n")
                                    copyDownloadText(
                                        context = context,
                                        label = context.getString(R.string.caption_clipboard_label),
                                        text = fullCaption,
                                        confirmation = R.string.caption_copied
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ── Error Diagnostic Card ────────────────────────────────────
            AnimatedVisibility(
                visible = fullError != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.errorContainer)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val errorLabel = stringResource(R.string.error_title)
                        Text(
                            text = errorLabel,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.onErrorContainer
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(errorLabel, fullError)
                                    clipboard.setPrimaryClip(clip)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.ContentCopy,
                                    contentDescription = "Copy error",
                                    tint = c.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            TextButton(onClick = { fullError = null }) {
                                Text(
                                    text = stringResource(R.string.dismiss_label),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.onErrorContainer
                                )
                            }
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = fullError ?: "",
                            fontSize = 12.sp,
                            color = c.onErrorContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    profileToBrowse?.let { username ->
        AnimatedVisibility(
            visibleState = remember { MutableTransitionState(false) }.apply { targetState = true },
            enter = slideInVertically(tween(250)) { it / 4 } + fadeIn(tween(250))
        ) {
            ProfileBrowser(
                username = username,
                settings = settings,
                onFolderAccessLost = onFolderAccessLost,
                onClose = { profileToBrowse = null }
            )
        }
    }
}

@Composable
private fun StudioCapabilityTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    cardBg: Color,
    cardBorder: Color,
    onClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = c.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = c.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun BentoActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean,
    accent: Color,
    recessedBg: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> recessedBg.copy(alpha = 0.5f)
        primary -> accent
        else -> recessedBg
    }
    val contentColor = when {
        !enabled -> c.onSurfaceVariant.copy(alpha = 0.45f)
        primary -> readableContentColor(accent)
        else -> c.onSurface
    }
    val subColor = when {
        !enabled -> c.onSurfaceVariant.copy(alpha = 0.35f)
        primary -> readableContentColor(accent)
        else -> c.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (primary) Color.White.copy(alpha = 0.2f)
                    else accent.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) readableContentColor(accent) else accent,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Guaranteed HD YouTube Thumbnail Loader with multi-tier fallback (maxresdefault -> hqdefault -> mqdefault).
 * Prevents grey boxes on YouTube Shorts and regional streams.
 */
@Composable
private fun YouTubeThumbnailBox(
    videoId: String,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = videoId, key2 = fallbackUrl) {
        value = withContext(Dispatchers.IO) {
            val candidates = listOfNotNull(
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                fallbackUrl?.takeIf { it.isNotBlank() },
                "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
            ).distinct()
            var loaded: ImageBitmap? = null
            for (candidate in candidates) {
                loaded = runCatching {
                    val req = Request.Builder()
                        .url(candidate)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    Net.client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val bytes = resp.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }.getOrNull()
                if (loaded != null) break
            }
            loaded
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = StudioCrimson
            )
        }
    }
}

@Composable
private fun MediaThumbnail(
    item: MediaResult,
    isCarousel: Boolean = false,
    isSelected: Boolean = true,
    onToggleSelected: () -> Unit = {},
    onOpen: () -> Unit = {}
) {
    val previewUrl = item.previewUrl
    var loadFailed by remember(previewUrl) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, previewUrl) {
        if (previewUrl == null) {
            value = null
            return@produceState
        }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = InstagramDownloader.fetchBytes(previewUrl)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (bmp == null) loadFailed = true
        value = bmp
    }
    val context = LocalContext.current
    val sizeBytes by produceState(-1L, item.url) {
        value = withContext(Dispatchers.IO) {
            runCatching { InstagramDownloader.contentLength(item.url) }.getOrDefault(-1L)
        }
    }
    val meta = listOfNotNull(
        item.takeIf { it.width in 1 until Int.MAX_VALUE && it.height > 0 }?.let { "${it.width}×${it.height}" },
        item.durationSec.takeIf { it > 0 }?.let { DateUtils.formatElapsedTime(it.roundToLong()) },
        sizeBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(width = 120.dp, height = 158.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 120.dp, height = 150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                when {
                    bmp != null -> Image(
                        bitmap = bmp,
                        contentDescription = stringResource(
                            if (item.isVideo) R.string.video_preview_description else R.string.image_preview_description
                        ),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isCarousel && !isSelected) 0.35f else 1f)
                    )
                    previewUrl == null || loadFailed -> Icon(
                        imageVector = if (item.isVideo) AppIcons.Movie else AppIcons.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                    )
                    else -> CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (item.isVideo && bmp != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (isCarousel) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelected() },
                    colors = CheckboxDefaults.colors(checkedColor = IgPink),
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.width(120.dp).padding(top = 4.dp)
            )
        }
    }
}

private fun hapticStart(context: Context, enabled: Boolean = true) {
    if (!enabled) return
    val v = vibrator(context)
    if (!v.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        v.vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f)
                .compose()
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(40, 140))
    } else {
        @Suppress("DEPRECATION") v.vibrate(40)
    }
}

private fun hapticComplete(context: Context, enabled: Boolean = true) {
    if (!enabled) return
    val v = vibrator(context)
    if (!v.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        v.vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 60)
                .compose()
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 30, 60, 80),
                intArrayOf(0, 80, 0, 220),
                -1
            )
        )
    } else {
        @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 30, 60, 80), -1)
    }
}

@Suppress("DEPRECATION")
private fun vibrator(context: Context): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

