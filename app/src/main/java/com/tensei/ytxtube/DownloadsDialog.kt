package com.tensei.ytxtube

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
fun DownloadsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var records by remember { mutableStateOf(DownloadHistory.load(context)) }
    var previewRecord by remember { mutableStateOf<DownloadRecord?>(null) }
    var selectedKind by remember { mutableStateOf(DownloadKind.ALL) }
    var fullscreen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (fullscreen) fullscreen = false
            else if (previewRecord != null) previewRecord = null
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
                        if (previewRecord == null) onDismiss() else previewRecord = null
                    }, modifier = Modifier.padding(top = 8.dp, end = 12.dp)) {
                        Text(stringResource(if (previewRecord == null) R.string.close_label else R.string.back_label))
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
    val handleClipboardLabel = stringResource(R.string.handle_clipboard_label)
    val captionClipboardLabel = stringResource(R.string.caption_clipboard_label)
    var captionExpanded by remember(record.uri) { mutableStateOf(false) }
    var captionHasOverflow by remember(record.uri) { mutableStateOf(false) }
    val captionScrollState = rememberScrollState()
    val exists by produceState<Boolean?>(null, record.uri) {
        value = withContext(Dispatchers.IO) { DownloadedFiles.exists(context, record) }
    }
    val size by produceState(-1L, record.uri) {
        value = withContext(Dispatchers.IO) { DownloadedFiles.sizeBytes(context, record) }
    }
    val meta = record.meta
    val username = meta?.username?.takeIf { it.isNotBlank() }
    val caption = meta?.caption?.takeIf { it.isNotBlank() }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                DownloadThumbnail(
                    record = record,
                    exists = exists,
                    onClick = onPreview
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        username?.let { if (it.startsWith("@")) it else "@$it" } ?: stringResource(R.string.unknown_account),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.then(
                            if (username == null) Modifier else Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    val handle = if (username.startsWith("@")) username else "@$username"
                                    copyDownloadText(
                                        context = context,
                                        label = handleClipboardLabel,
                                        text = handle,
                                        confirmation = R.string.handle_copied
                                    )
                                }
                            )
                        )
                    )
                    Text(
                        caption ?: record.fileName,
                        maxLines = if (captionExpanded) Int.MAX_VALUE else 3,
                        overflow = if (captionExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onTextLayout = { captionHasOverflow = it.hasVisualOverflow },
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .then(
                                if (captionExpanded) {
                                    Modifier.heightIn(max = 160.dp).verticalScroll(captionScrollState)
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (caption == null) Modifier else Modifier.combinedClickable(
                                    onClick = { captionExpanded = !captionExpanded },
                                    onLongClick = {
                                        copyDownloadText(
                                            context = context,
                                            label = captionClipboardLabel,
                                            text = caption,
                                            confirmation = R.string.caption_copied
                                        )
                                    }
                                )
                            )
                    )
                    if (caption != null && (captionHasOverflow || captionExpanded)) {
                        TextButton(
                            onClick = { captionExpanded = !captionExpanded },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 28.dp)
                        ) {
                            Text(
                                stringResource(
                                    if (captionExpanded) R.string.show_less_label else R.string.show_more_label
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    val details = listOfNotNull(
                        meta?.song,
                        meta?.takenAtSec?.takeIf { it > 0 }
                            ?.let { stringResource(R.string.posted_date_format, formatDate(it * 1000)) },
                        record.takeIf { it.width > 0 && it.height > 0 }
                            ?.let { "${it.width}×${it.height}" },
                        record.durationSec.takeIf { it > 0 }
                            ?.let { DateUtils.formatElapsedTime(it.roundToLong()) },
                        size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                        stringResource(R.string.downloaded_date_format, formatDate(record.downloadedAt))
                    )
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    meta?.url?.let { postUrl ->
                        Text(
                            postUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .clickable { uriHandler.openUri(postUrl) }
                        )
                    } ?: Text(
                        stringResource(R.string.no_post_link),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            if (exists == false) {
                Text(
                    stringResource(R.string.file_missing),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (caption != null) {
                    TextButton(
                        onClick = {
                            copyDownloadText(
                                context = context,
                                label = captionClipboardLabel,
                                text = caption,
                                confirmation = R.string.caption_copied
                            )
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text(stringResource(R.string.copy_caption_label), maxLines = 1) }
                }
                TextButton(
                    onClick = onPreview,
                    enabled = exists != false && !record.mimeType.startsWith("audio/"),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(stringResource(R.string.preview_label), maxLines = 1) }
                TextButton(
                    onClick = { startFileAction(context) { DownloadedFiles.openIntent(context, record) } },
                    enabled = exists != false,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(stringResource(R.string.open_label), maxLines = 1) }
                TextButton(
                    onClick = { startFileAction(context) { DownloadedFiles.shareIntent(context, record) } },
                    enabled = exists != false,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(stringResource(R.string.share_label), maxLines = 1) }
                TextButton(
                    onClick = {
                        if (DownloadedFiles.delete(context, record)) onDeleted()
                        else Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(stringResource(R.string.delete_label), maxLines = 1) }
            }
        }
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

