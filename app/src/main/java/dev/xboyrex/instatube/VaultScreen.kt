package dev.xboyrex.instatube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualitySheet(video: VideoItem, onDownloadStarted: ((String) -> Unit)? = null, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember(video.id) { mutableStateOf<List<DownloadOption>?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var selected by remember(video.id) { mutableStateOf<Int?>(null) }
    var savingCover by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        runCatching { Downloads.options(video.id) }
            .onSuccess { list ->
                options = list
                selected = list.firstOrNull { it.height == 720 }?.height ?: list.firstOrNull()?.height ?: 0
            }
            .onFailure { error = it.message ?: "Could not load the available qualities" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Text(
                "Download Media",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(video.thumbnail, Modifier.size(width = 112.dp, height = 63.dp), radius = 12, videoId = video.id)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        video.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        video.channel,
                        color = c.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!savingCover) {
                            savingCover = true
                            scope.launch {
                                val res = runCatching {
                                    withContext(Dispatchers.IO) {
                                        saveYouTubeCoverToDownloads(video.id, video.title, video.channel, video.thumbnail, context)
                                    }
                                }
                                savingCover = false
                                onDownloadStarted?.invoke(
                                    if (res.isSuccess) "Cover saved to Downloads/InstaTube"
                                    else "Cover download failed"
                                )
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (savingCover) "Saving…" else "Cover Download", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("YouTube Caption", "${video.title}\nhttps://youtu.be/${video.id}"))
                        Toast.makeText(context, "Caption copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Caption", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))

            when {
                error != null -> Text(
                    error!!,
                    color = c.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                options == null -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                }
                else -> {
                    options!!.forEach { option ->
                        QualityChoiceRow(option, selected == option.height) { selected = option.height }
                    }
                    AudioChoiceRow(selected == 0) { selected = 0 }
                    Spacer(Modifier.height(18.dp))
                    val choice = options!!.firstOrNull { it.height == selected }
                    val isAudioSelected = selected == 0
                    Button(
                        onClick = {
                            if (isAudioSelected) {
                                val added = Downloads.enqueueAudio(video)
                                onDownloadStarted?.invoke(if (added) "Downloading Audio • ${video.title}" else "Already in downloads")
                                onDismiss()
                            } else if (choice != null) {
                                val added = Downloads.enqueue(video, choice.height)
                                onDownloadStarted?.invoke(if (added) "Downloading ${choice.label} • ${video.title}" else "Already in downloads")
                                onDismiss()
                            }
                        },
                        enabled = choice != null || isAudioSelected,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    ) {
                        Icon(if (isAudioSelected) Icons.Rounded.MusicNote else Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                isAudioSelected -> "Download Audio (M4A)"
                                choice == null -> "Download"
                                else -> "Download ${choice.label}${sizeSuffix(choice.estimatedBytes)}"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioChoiceRow(selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.primaryContainer.copy(alpha = 0.55f) else c.surfaceContainerHigh.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) c.primary else c.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Audio Only (M4A)", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Text("High-bitrate original audio stream", color = c.onSurfaceVariant, fontSize = 11.5.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun QualityChoiceRow(option: DownloadOption, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.primaryContainer.copy(alpha = 0.55f) else c.surfaceContainerHigh.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) c.primary else c.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(option.label, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            val subtitle = buildString {
                qualityTag(option.height)?.let { append(it) }
                if (option.estimatedBytes > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatBytes(option.estimatedBytes))
                }
                append(if (option.needsMux) " · video + audio" else " · with audio")
            }
            Text(subtitle, color = c.onSurfaceVariant, fontSize = 11.5.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    selectedFolderName: String = AppSettings.DEFAULT_FOLDER_NAME,
    onBrowse: () -> Unit,
    onChooseFolder: () -> Unit = {},
    scrollToTop: Int = 0,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val tasks = Downloads.tasks
    var savedRecords by remember { mutableStateOf(DownloadHistory.load(context)) }
    var previewRecordDialog by remember { mutableStateOf<DownloadRecord?>(null) }
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }

    val completedCount = tasks.count { it.status == DownloadStatus.Complete }
    LaunchedEffect(section, tasks.size, completedCount) {
        savedRecords = DownloadHistory.load(context)
    }

    previewRecordDialog?.let { rec ->
        DownloadsDialog(
            initialPreviewRecord = rec,
            onDismiss = {
                previewRecordDialog = null
                savedRecords = DownloadHistory.load(context)
            }
        )
    }

    val activeTasks = tasks.filter { it.status != DownloadStatus.Complete }

    val videoRecords = remember(savedRecords) {
        savedRecords.filter {
            it.mimeType.startsWith("video/") || it.fileName.endsWith(".mp4", ignoreCase = true)
        }
    }
    val audioRecords = remember(savedRecords) {
        savedRecords.filter {
            it.mimeType.startsWith("audio/") ||
                it.fileName.endsWith(".m4a", ignoreCase = true) ||
                it.fileName.endsWith(".mp3", ignoreCase = true)
        }
    }
    val imageRecords = remember(savedRecords) {
        savedRecords.filter {
            it.mimeType.startsWith("image/") ||
                it.fileName.endsWith(".jpg", ignoreCase = true) ||
                it.fileName.endsWith(".png", ignoreCase = true)
        }
    }

    val visibleRecords = when (section) {
        1 -> videoRecords
        2 -> audioRecords
        3 -> imageRecords
        else -> savedRecords
    }

    val segContainer = if (dark) Color(0xFF131620) else Color(0xFFE9ECF4)
    val shortFolder = selectedFolderName.substringAfterLast('/').ifBlank { "InstaTube" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
    ) {
        // Non-truncating Studio Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Downloads",
                    fontFamily = RoundedHeavy,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.onSurface,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${savedRecords.size} saved items in Vault",
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(segContainer)
                        .pressable(onClick = onChooseFolder)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.Folder,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = shortFolder.take(14),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.onSurface,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(segContainer)
                        .pressable { savedRecords = DownloadHistory.load(context) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = "Refresh",
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Inset Segmented Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(segContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DownloadSegmentItem("All", savedRecords.size, section == 0, Modifier.weight(1f)) { section = 0 }
            DownloadSegmentItem("Video", videoRecords.size, section == 1, Modifier.weight(1f)) { section = 1 }
            DownloadSegmentItem("Audio", audioRecords.size, section == 2, Modifier.weight(1f)) { section = 2 }
            DownloadSegmentItem("Artwork", imageRecords.size, section == 3, Modifier.weight(1f)) { section = 3 }
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 6.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp
            )
        ) {
            if (activeTasks.isNotEmpty()) {
                item(key = "active_header") {
                    SectionTitle("Active Transfers (${activeTasks.size})")
                }
                items(activeTasks, key = { "active-" + it.key }) { task ->
                    DownloadRow(task, onOpen = null)
                }
                item(key = "active_spacer") {
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (visibleRecords.isEmpty()) {
                item(key = "empty_saved") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp)) {
                        EmptyState(
                            icon = AppIcons.Download,
                            title = when (section) {
                                1 -> "No saved videos yet"
                                2 -> "No saved audio tracks yet"
                                3 -> "No saved artwork yet"
                                else -> "Your Vault is empty"
                            },
                            body = "Paste any Instagram or YouTube link in the Downloader tab to save full-resolution videos, audio tracks, and covers.",
                            action = {
                                PillButton("Open Downloader", onBrowse)
                            },
                        )
                    }
                }
            } else {
                items(visibleRecords, key = { "saved-" + it.uri }) { record ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        SavedDownloadRow(
                            record = record,
                            onPreview = { previewRecordDialog = record },
                            onDeleted = {
                                DownloadHistory.remove(context, record)
                                savedRecords = savedRecords.filterNot { it.uri == record.uri }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSegmentItem(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.primary else Color.Transparent)
            .pressable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (selected) c.onPrimary else c.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
            )
            if (count > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = count.toString(),
                    color = if (selected) c.onPrimary.copy(alpha = 0.85f) else c.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, onOpen: (() -> Unit)?) {
    val c = MaterialTheme.colorScheme
    var menu by remember(task.key) { mutableStateOf(false) }
    val running = task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Queued

    Column(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = onOpen != null, pressedScale = 0.985f) { onOpen?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box {
                Thumbnail(
                    task.thumbnail,
                    Modifier.size(width = 132.dp, height = 74.dp),
                    radius = 12,
                    videoId = task.videoId,
                )
                DurationPill(task.durationSec, Modifier.align(Alignment.BottomEnd).padding(5.dp), small = true)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (task.height > 0) "${task.channel} · ${task.height}p" else "${task.channel} · Audio",
                    color = c.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    statusText(task),
                    color = if (task.status == DownloadStatus.Failed) c.error else c.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, "More options", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    when (task.status) {
                        DownloadStatus.Downloading, DownloadStatus.Queued -> DropdownMenuItem(
                            text = { Text("Pause") },
                            onClick = { menu = false; Downloads.pause(task) },
                        )
                        DownloadStatus.Paused, DownloadStatus.Failed -> DropdownMenuItem(
                            text = { Text("Resume") },
                            onClick = { menu = false; Downloads.resume(task) },
                        )
                        DownloadStatus.Complete -> DropdownMenuItem(
                            text = { Text("Play offline") },
                            onClick = { menu = false; onOpen?.invoke() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = c.error) },
                        onClick = { menu = false; Downloads.remove(task) },
                    )
                }
            }
        }

        if (running || task.status == DownloadStatus.Paused || task.status == DownloadStatus.Failed) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = if (task.status == DownloadStatus.Failed) c.error else c.primary,
                trackColor = c.onSurfaceVariant.copy(alpha = 0.18f),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    progressText(task),
                    color = c.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when (task.status) {
                        DownloadStatus.Downloading -> "Downloading"
                        DownloadStatus.Queued -> "Waiting"
                        DownloadStatus.Paused -> "Paused"
                        DownloadStatus.Failed -> "Failed"
                        DownloadStatus.Complete -> "Downloaded"
                    },
                    color = c.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun statusText(task: DownloadTask): String = when (task.status) {
    DownloadStatus.Complete -> "Downloaded · ${formatBytes(task.file.length())}"
    DownloadStatus.Downloading -> "Downloading ${(task.progress * 100).toInt()}%"
    DownloadStatus.Queued -> "Waiting to download"
    DownloadStatus.Paused -> "Paused · ${formatBytes(task.downloaded)} downloaded"
    DownloadStatus.Failed -> task.error ?: "Download failed"
}

private fun progressText(task: DownloadTask): String {
    val total = task.total
    return if (total > 0) {
        "${formatBytes(task.downloaded)} of ${formatBytes(total)}"
    } else {
        formatBytes(task.downloaded)
    }
}

internal fun qualityTag(height: Int): String? = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "2K"
    height >= 1080 -> "Full HD"
    height >= 720 -> "HD"
    height <= 240 -> "Data saver"
    else -> null
}

internal fun sizeSuffix(bytes: Long): String =
    if (bytes > 0) " · ${formatBytes(bytes)}" else ""

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        mb >= 10 -> String.format(Locale.US, "%.0f MB", mb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

