package dev.xboyrex.instatube

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToLong

private enum class DownloadKind(val labelRes: Int) {
    ALL(R.string.downloads_all),
    REELS(R.string.downloads_reels),
    POSTS(R.string.downloads_posts),
    AUDIO(R.string.downloads_audio),
    STORIES(R.string.downloads_stories)
}

private val DownloadRecord.kind: DownloadKind
    get() = when {
        mimeType.startsWith("audio/") || fileName.endsWith(".m4a", ignoreCase = true) || fileName.endsWith(".mp3", ignoreCase = true) -> DownloadKind.AUDIO
        meta?.url?.contains("/stories/") == true -> DownloadKind.STORIES
        mimeType.startsWith("video/") || meta?.url?.contains("/reel/") == true || meta?.url?.contains("/reels/") == true || meta?.url?.contains("youtu") == true -> DownloadKind.REELS
        else -> DownloadKind.POSTS
    }

@Composable
fun DownloadsDialog(initialPreviewRecord: DownloadRecord? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var records by remember { mutableStateOf(DownloadHistory.load(context)) }
    var previewRecord by remember { mutableStateOf<DownloadRecord?>(initialPreviewRecord) }
    var selectedKind by remember { mutableStateOf(DownloadKind.ALL) }
    var fullscreen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (fullscreen) fullscreen = false
            else if (previewRecord != null && initialPreviewRecord == null) previewRecord = null
            else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        LaunchedEffect(fullscreen) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).run {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) hide(WindowInsetsCompat.Type.systemBars())
                else show(WindowInsetsCompat.Type.systemBars())
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize()
                .then(if (fullscreen) Modifier else Modifier.statusBarsPadding().navigationBarsPadding()),
            color = if (fullscreen) Color.Black else MaterialTheme.colorScheme.background
        ) {
            Column {
                if (!fullscreen) Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(if (previewRecord == null) R.string.downloads_title else R.string.preview_label),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
                    )
                    if (previewRecord == null) {
                        IconButton(
                            onClick = { records = DownloadHistory.load(context) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Refresh,
                                contentDescription = stringResource(R.string.refresh_downloads_label)
                            )
                        }
                    }
                    TextButton(onClick = {
                        if (previewRecord == null || initialPreviewRecord != null) onDismiss() else previewRecord = null
                    }, modifier = Modifier.padding(top = 8.dp, end = 12.dp)) {
                        Text(stringResource(if (previewRecord == null || initialPreviewRecord != null) R.string.close_label else R.string.back_label))
                    }
                }
                val record = previewRecord
                if (record != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(if (fullscreen) Modifier else Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
                    ) { MediaPreview(record, fullscreen) { fullscreen = it } }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = selectedKind.ordinal,
                        edgePadding = 12.dp
                    ) {
                        DownloadKind.entries.forEach { kind ->
                            Tab(
                                selected = selectedKind == kind,
                                onClick = { selectedKind = kind },
                                text = { Text(stringResource(kind.labelRes)) }
                            )
                        }
                    }
                    val visibleRecords = records.filter {
                        selectedKind == DownloadKind.ALL || it.kind == selectedKind
                    }
                    if (visibleRecords.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    if (records.isEmpty()) stringResource(R.string.downloads_empty)
                                    else stringResource(
                                        R.string.downloads_empty_kind,
                                        stringResource(selectedKind.labelRes)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                if (records.isEmpty()) {
                                    Text(
                                        stringResource(R.string.downloads_empty_hint),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visibleRecords, key = { it.uri }) { rec ->
                                SavedDownloadRow(
                                    record = rec,
                                    onPreview = { previewRecord = rec },
                                    onDeleted = {
                                        DownloadHistory.remove(context, rec)
                                        records = records.filterNot { it.uri == rec.uri }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SavedDownloadRow(
    record: DownloadRecord,
    onPreview: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val c = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val handleClipboardLabel = stringResource(R.string.handle_clipboard_label)
    val captionClipboardLabel = stringResource(R.string.caption_clipboard_label)
    var captionExpanded by remember(record.uri) { mutableStateOf(false) }

    val exists by produceState<Boolean?>(null, record.uri) {
        value = withContext(Dispatchers.IO) { DownloadedFiles.exists(context, record) }
    }
    val size by produceState(-1L, record.uri) {
        value = withContext(Dispatchers.IO) { DownloadedFiles.sizeBytes(context, record) }
    }
    val meta = record.meta
    val username = meta?.username?.takeIf { it.isNotBlank() }
    val caption = meta?.caption?.takeIf { it.isNotBlank() }

    val cardBg = if (dark) Color(0xFF131620) else Color.White
    val cardBorder = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val pillBg = if (dark) Color(0xFF1C2030) else Color(0xFFF1F3F8)

    val formatBadge = when {
        record.mimeType.startsWith("audio/") || record.fileName.endsWith(".m4a", true) -> "M4A AUDIO"
        record.mimeType.startsWith("video/") || record.fileName.endsWith(".mp4", true) -> "MP4 VIDEO"
        else -> "JPG ARTWORK"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            DownloadThumbnail(
                record = record,
                exists = exists,
                onClick = onPreview
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatBadge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.primary
                        )
                    }

                    username?.let { uname ->
                        val handle = if (uname.startsWith("@")) uname else "@$uname"
                        Text(
                            text = handle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                copyDownloadText(
                                    context = context,
                                    label = handleClipboardLabel,
                                    text = handle,
                                    confirmation = R.string.handle_copied
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = caption ?: record.fileName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurface,
                    maxLines = if (captionExpanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        if (caption != null) captionExpanded = !captionExpanded
                    }
                )

                val details = listOfNotNull(
                    record.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}×${it.height}" },
                    record.durationSec.takeIf { it > 0 }?.let { DateUtils.formatElapsedTime(it.roundToLong()) },
                    size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                    formatDate(record.downloadedAt)
                )
                Text(
                    text = details.joinToString(" · "),
                    fontSize = 11.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )

                meta?.url?.takeIf { it.isNotBlank() }?.let { postUrl ->
                    Text(
                        text = postUrl.removePrefix("https://").removePrefix("www."),
                        fontSize = 11.sp,
                        color = c.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .clickable { uriHandler.openUri(postUrl) }
                    )
                }
            }
        }

        if (exists == false) {
            Text(
                text = stringResource(R.string.file_missing),
                color = c.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Modern Studio Action Pill Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!record.mimeType.startsWith("audio/")) {
                VaultActionPill(
                    icon = AppIcons.PlayArrow,
                    label = "Preview",
                    pillBg = pillBg,
                    contentColor = c.onSurface,
                    enabled = exists != false,
                    modifier = Modifier.weight(1f),
                    onClick = onPreview
                )
            }
            if (caption != null) {
                VaultActionPill(
                    icon = AppIcons.ContentCopy,
                    label = "Caption",
                    pillBg = pillBg,
                    contentColor = c.onSurface,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        copyDownloadText(
                            context = context,
                            label = captionClipboardLabel,
                            text = caption,
                            confirmation = R.string.caption_copied
                        )
                    }
                )
            }
            VaultActionPill(
                icon = AppIcons.ExternalLink,
                label = "Open",
                pillBg = pillBg,
                contentColor = c.onSurface,
                enabled = exists != false,
                modifier = Modifier.weight(1f),
                onClick = { startFileAction(context) { DownloadedFiles.openIntent(context, record) } }
            )
            VaultActionPill(
                icon = AppIcons.Share,
                label = "Share",
                pillBg = pillBg,
                contentColor = c.onSurface,
                enabled = exists != false,
                modifier = Modifier.weight(1f),
                onClick = { startFileAction(context) { DownloadedFiles.shareIntent(context, record) } }
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.errorContainer.copy(alpha = 0.45f))
                    .clickable {
                        if (DownloadedFiles.delete(context, record)) onDeleted()
                        else Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Trash,
                    contentDescription = "Delete",
                    tint = c.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun VaultActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    pillBg: Color,
    contentColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(pillBg)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun DownloadThumbnail(record: DownloadRecord, exists: Boolean?, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, record.uri, exists) {
        if (exists != false) {
            value = withContext(Dispatchers.IO) {
                DownloadedFiles.previewBitmap(context, record, 320)?.asImageBitmap()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 122.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(enabled = exists != false && !record.mimeType.startsWith("audio/"), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(
                    if (record.mimeType.startsWith("video/")) {
                        R.string.video_preview_description
                    } else {
                        R.string.image_preview_description
                    }
                ),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: if (exists == null) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                imageVector = when {
                    record.mimeType.startsWith("audio/") -> AppIcons.MusicNote
                    record.mimeType.startsWith("video/") -> AppIcons.Movie
                    else -> AppIcons.Image
                },
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        }
        if (record.mimeType.startsWith("video/") && bitmap != null) {
            Icon(
                imageVector = AppIcons.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ColumnScope.MediaFrame(fullscreen: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .then(
                if (fullscreen) Modifier.weight(1f).fillMaxWidth()
                else Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
            )
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPreview(record: DownloadRecord, fullscreen: Boolean, onFullscreenChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val handleClipboardLabel = stringResource(R.string.handle_clipboard_label)
    val captionClipboardLabel = stringResource(R.string.caption_clipboard_label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (record.mimeType.startsWith("video/")) VideoPreview(record, fullscreen, onFullscreenChange)
        else ImagePreview(record)
        if (fullscreen) return@Column
        val meta = record.meta
        val username = meta?.username?.takeIf { it.isNotBlank() }
        val caption = meta?.caption?.takeIf { it.isNotBlank() }
        username?.let { handle ->
            Text(
                if (handle.startsWith("@")) handle else "@$handle",
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            copyDownloadText(
                                context = context,
                                label = handleClipboardLabel,
                                text = if (handle.startsWith("@")) handle else "@$handle",
                                confirmation = R.string.handle_copied
                            )
                        }
                    )
            )
        }
        caption?.let { text ->
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            copyDownloadText(
                                context = context,
                                label = captionClipboardLabel,
                                text = text,
                                confirmation = R.string.caption_copied
                            )
                        }
                    )
                    .padding(top = 4.dp)
            )
        }
        meta?.url?.let { postUrl ->
            Text(
                postUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickable { uriHandler.openUri(postUrl) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (caption != null) {
                TextButton(onClick = {
                    copyDownloadText(context, captionClipboardLabel, caption, R.string.caption_copied)
                }) { Text(stringResource(R.string.copy_caption_label)) }
            }
            TextButton(onClick = {
                startFileAction(context) { DownloadedFiles.shareIntent(context, record) }
            }) { Text(stringResource(R.string.share_label)) }
        }
    }
}

@Composable
private fun ColumnScope.ImagePreview(record: DownloadRecord) {
    val context = LocalContext.current
    val result by produceState<Result<ImageBitmap>?>(null, record.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                DownloadedFiles.previewBitmap(context, record, 1200)?.asImageBitmap()
                    ?: error("Preview unavailable")
            }
        }
    }

    MediaFrame {
        when {
            result == null -> CircularProgressIndicator(color = Color.White)
            result?.isSuccess == true -> ZoomableBitmap(result!!.getOrThrow())
            else -> Text(
                stringResource(R.string.preview_unavailable),
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun ColumnScope.VideoPreview(
    record: DownloadRecord,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var failed by remember(record.uri) { mutableStateOf(false) }
    var playing by remember(record.uri) { mutableStateOf(false) }
    var durationMs by remember(record.uri) { mutableIntStateOf(0) }
    var positionMs by remember(record.uri) { mutableFloatStateOf(0f) }
    var seeking by remember(record.uri) { mutableStateOf(false) }
    var controlsVisible by remember(record.uri) { mutableStateOf(true) }
    val videoView = remember(record.uri) {
        VideoView(context).apply {
            setVideoURI(Uri.parse(record.uri))
            setOnPreparedListener {
                durationMs = it.duration
                it.start()
                playing = true
            }
            setOnCompletionListener { playing = false }
            setOnErrorListener { _, _, _ ->
                failed = true
                true
            }
        }
    }
    val togglePlay = {
        if (playing) videoView.pause() else videoView.start()
        playing = !playing
    }
    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
    }
    LaunchedEffect(fullscreen) { controlsVisible = true }
    LaunchedEffect(controlsVisible, playing, seeking, fullscreen) {
        if (fullscreen && controlsVisible && playing && !seeking) {
            delay(3000)
            controlsVisible = false
        }
    }
    LaunchedEffect(videoView, playing) {
        while (playing) {
            if (!seeking) positionMs = videoView.currentPosition.toFloat()
            delay(200)
        }
    }

    val controls: @Composable (Modifier, Color) -> Unit = { modifier, textColor ->
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = togglePlay,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = if (playing) AppIcons.Pause else AppIcons.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.pause_label else R.string.play_label),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                val progressFraction = if (durationMs > 0) (positionMs / durationMs).coerceIn(0f, 1f) else 0f
                Slider(
                    value = progressFraction,
                    onValueChange = {
                        seeking = true
                        positionMs = it * durationMs
                        videoView.seekTo(positionMs.toInt())
                    },
                    onValueChangeFinished = { seeking = false },
                    enabled = durationMs > 0,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    DateUtils.formatElapsedTime((positionMs / 1000).toLong()) + " / " +
                        DateUtils.formatElapsedTime(durationMs / 1000L),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor
                )
            }
            IconButton(onClick = { onFullscreenChange(!fullscreen) }) {
                Icon(
                    imageVector = if (fullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                    contentDescription = stringResource(
                        if (fullscreen) R.string.exit_fullscreen_label else R.string.fullscreen_label
                    ),
                    tint = textColor
                )
            }
        }
    }

    MediaFrame(fullscreen) {
        if (failed) {
            Text(stringResource(R.string.preview_unavailable), color = Color.White)
            return@MediaFrame
        }
        AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null) {
                    if (!fullscreen) togglePlay() else controlsVisible = !controlsVisible
                }
        )
        if (fullscreen) {
            val shown = controlsVisible || !playing
            val controlsAlpha by animateFloatAsState(if (shown) 1f else 0f, label = "controls")
            if (shown || controlsAlpha > 0f) {
                controls(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .alpha(controlsAlpha)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(start = 12.dp, end = 4.dp, top = 24.dp, bottom = 24.dp),
                    Color.White
                )
            }
        }
    }
    if (!failed && !fullscreen) {
        controls(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun copyDownloadText(
    context: Context,
    label: String,
    text: String,
    @androidx.annotation.StringRes confirmation: Int
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (AppSettings(context).hapticsEnabled) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, 120))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(35)
            }
        }
    }
    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}

internal fun startFileAction(context: Context, intent: () -> android.content.Intent) {
    try {
        context.startActivity(intent())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, R.string.file_action_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun formatDate(timeMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timeMs))

