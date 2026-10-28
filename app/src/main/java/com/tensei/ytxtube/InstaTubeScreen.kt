package com.tensei.ytxtube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

private val IgPurple = Color(0xFF833AB4)
private val IgOrange = Color(0xFFF77737)
private val YtRed = Color(0xFFFF0033)

private val IgPurpleDark = Color(0xFF231328)
private val IgPinkDark = Color(0xFF3E1023)
private val IgOrangeDark = Color(0xFF2D1409)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InstaTubeDownloaderScreen(
    initialUrl: String = "",
    initialUrlKey: Any = initialUrl,
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
    onPlayYouTubeVideo: (VideoItem) -> Unit,
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
    var downloadComplete by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var targetWidth by remember { mutableIntStateOf(settings.targetWidth()) }
    var profileToBrowse by remember { mutableStateOf<String?>(null) }
    var captionExpanded by remember { mutableStateOf(false) }

    val useDarkTheme = isDarkTheme()

    LaunchedEffect(initialUrlKey) {
        if (initialUrl.isNotBlank()) {
            url = initialUrl
            media = null
            ytDetails = null
            ytOptions = null
            urlError = null
            fullError = null
            downloadComplete = false
        }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val genericError = stringResource(R.string.error_generic)
    val emptyUrlError = stringResource(R.string.error_empty_url)
    val invalidUrlError = stringResource(R.string.error_invalid_url)
    val folderAccessLostError = stringResource(R.string.error_folder_access_lost)
    val downloadFailedError = stringResource(R.string.error_download_failed)

    val cleanedUrl = remember(url) { InstagramDownloader.extractCleanUrl(url) }
    val isStory = remember(cleanedUrl) { InstagramDownloader.isStoryUrl(cleanedUrl) }
    val isYouTube = remember(cleanedUrl) { InstagramDownloader.isYouTubeUrl(cleanedUrl) }
    val isInstagram = remember(cleanedUrl) { InstagramDownloader.isInstagramUrl(cleanedUrl) }

    val bgGradient = Brush.verticalGradient(
        colors = if (useDarkTheme) {
            listOf(IgPurpleDark, IgPinkDark, IgOrangeDark)
        } else {
            listOf(IgPurple, IgPink, IgOrange)
        }
    )

    // Resolve Instagram or YouTube preview automatically when URL changes
    LaunchedEffect(cleanedUrl, targetWidth) {
        if (cleanedUrl.isBlank() || isStory) {
            media = null
            ytDetails = null
            ytOptions = null
            return@LaunchedEffect
        }
        if (isYouTube) {
            val videoId = InstagramDownloader.extractYouTubeId(cleanedUrl) ?: return@LaunchedEffect
            delay(300)
            isLoading = true
            urlError = null
            fullError = null
            media = null
            ytDetails = null
            ytOptions = null
            downloadComplete = false
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val details = YouTube.details(videoId)
                    val opts = runCatching { Downloads.options(videoId) }.getOrDefault(emptyList())
                    details to opts
                }
            }
            isLoading = false
            if (result.isFailure) {
                fullError = result.exceptionOrNull()?.message ?: genericError
                return@LaunchedEffect
            }
            val (details, opts) = result.getOrThrow()
            hapticStart(context, settings.hapticsEnabled)
            ytDetails = details
            ytOptions = opts
            selectedYtHeight = opts.firstOrNull { it.height == 1080 }?.height
                ?: opts.firstOrNull { it.height == 720 }?.height
                ?: opts.firstOrNull()?.height
            return@LaunchedEffect
        }
        if (isInstagram && !InstagramDownloader.isProfileUrl(cleanedUrl)) {
            delay(350)
            isLoading = true
            urlError = null
            fullError = null
            media = null
            ytDetails = null
            ytOptions = null
            deselectedIndices = emptySet()
            downloadComplete = false
            val items = runCatching {
                withContext(Dispatchers.IO) {
                    InstagramDownloader.getMediaItems(cleanedUrl, targetWidth)
                }
            }
            isLoading = false
            if (items.isFailure) {
                fullError = items.exceptionOrNull()?.message ?: genericError
                return@LaunchedEffect
            }
            hapticStart(context, settings.hapticsEnabled)
            media = items.getOrThrow()
            return@LaunchedEffect
        }
        media = null
        ytDetails = null
        ytOptions = null
    }

    viewerIndex?.let { index ->
        media?.let { MediaViewer(it, null, index) { viewerIndex = null } }
    }

    if (showSettings) {
        InstaTubeSettingsDialog(
            settings = settings,
            selectedFolderName = selectedFolderName,
            onChooseFolder = onChooseFolder,
            onThemeChanged = onThemeChanged,
            onOpenFullSettings = {
                showSettings = false
                onOpenFullSettings()
            },
            onDismiss = {
                showSettings = false
                targetWidth = settings.targetWidth()
            }
        )
    }

    val bgBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Sibling background layer captured for Liquid Glass cards (never parent of liquidGlass!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(bgBackdrop)
                .background(bgGradient)
        )

        // Loading bar at top of screen
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // ── Liquid Glass Hero Badge ──────────────────────────────────
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .liquidGlass(
                        bgBackdrop,
                        glassSpec(
                            shape = RoundedCornerShape(26.dp),
                            tint = Color.White.copy(alpha = 0.22f),
                            blur = 20.dp,
                            refractionHeight = 14.dp,
                            refractionAmount = 22.dp
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.app_name),
                fontFamily = RoundedHeavy,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.85f)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Universal Input Card (Instagram + YouTube) ───────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.94f)
                )
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isYouTube -> "YouTube Link Detected"
                                isInstagram -> "Instagram Link Detected"
                                else -> "Instagram & YouTube Downloader"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isYouTube -> YtRed
                                    isInstagram -> IgPink
                                    else -> colorScheme.onSurfaceVariant
                                }
                            )
                        )
                        if (url.isNotBlank()) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        url = ""
                                        urlError = null
                                        fullError = null
                                        media = null
                                        ytDetails = null
                                        ytOptions = null
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val pasteDescription = stringResource(R.string.paste_clipboard_description)
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            if (urlError != null) urlError = null
                            media = null
                            ytDetails = null
                            ytOptions = null
                            fullError = null
                        },
                        label = { Text(stringResource(R.string.url_field_label)) },
                        placeholder = { Text(stringResource(R.string.url_field_placeholder)) },
                        isError = urlError != null,
                        supportingText = if (urlError != null) {
                            { Text(urlError!!, color = colorScheme.error) }
                        } else null,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (pasted.isNotEmpty()) {
                                        url = InstagramDownloader.extractCleanUrl(pasted)
                                        urlError = null
                                        media = null
                                        ytDetails = null
                                        ytOptions = null
                                        fullError = null
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = pasteDescription }
                            ) {
                                Icon(
                                    imageVector = AppIcons.ContentPaste,
                                    contentDescription = null,
                                    tint = if (isYouTube) YtRed else IgPink
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isYouTube) YtRed else IgPink,
                            focusedLabelColor = if (isYouTube) YtRed else IgPink,
                            cursorColor = if (isYouTube) YtRed else IgPink,
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    AnimatedVisibility(
                        visible = isStory,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 18.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = IgOrange.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.story_not_supported_title),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = IgOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    stringResource(R.string.story_not_supported_body),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // Primary Download CTA Button
                    Button(
                        onClick = {
                            val trimmed = InstagramDownloader.extractCleanUrl(url)
                            when {
                                trimmed.isBlank() -> urlError = emptyUrlError
                                !InstagramDownloader.isInstagramUrl(trimmed) && !InstagramDownloader.isYouTubeUrl(trimmed) ->
                                    urlError = invalidUrlError
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission() ->
                                    requestStoragePermission()
                                InstagramDownloader.isProfileUrl(trimmed) ->
                                    profileToBrowse = InstagramDownloader.extractProfileUsername(trimmed)
                                InstagramDownloader.isYouTubeUrl(trimmed) -> coroutineScope.launch {
                                    fullError = null
                                    val videoId = InstagramDownloader.extractYouTubeId(trimmed) ?: run {
                                        urlError = invalidUrlError
                                        return@launch
                                    }
                                    val details = ytDetails ?: run {
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
                                        d
                                    }
                                    hapticStart(context, settings.hapticsEnabled)
                                    val height = selectedYtHeight ?: ytOptions?.firstOrNull()?.height ?: 720
                                    val added = Downloads.enqueue(details.item, height)
                                    hapticComplete(context, settings.hapticsEnabled)
                                    downloadComplete = true
                                    onShowNotice(
                                        if (added) "Downloading ${height}p • ${details.item.title}"
                                        else "Already in downloads"
                                    )
                                    delay(2000)
                                    downloadComplete = false
                                }
                                else -> coroutineScope.launch {
                                    fullError = null
                                    val items = media ?: run {
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
                                        fetched.getOrThrow().also { media = it }
                                    }
                                    val itemsToSave = items.filterIndexed { i, _ -> i !in deselectedIndices }
                                    hapticStart(context, settings.hapticsEnabled)
                                    isSaving = true
                                    savingActionLabel = context.getString(R.string.saving_label)
                                    var fellBackToDefaultFolder = false
                                    val dlResult = runCatching {
                                        withContext(Dispatchers.IO) {
                                            itemsToSave.forEach { item ->
                                                if (saveToDownloads(item, context, settings).fellBackToDefaultFolder) {
                                                    fellBackToDefaultFolder = true
                                                }
                                            }
                                        }
                                    }
                                    isSaving = false
                                    savingActionLabel = null
                                    if (fellBackToDefaultFolder) onFolderAccessLost()
                                    if (dlResult.isSuccess) {
                                        hapticComplete(context, settings.hapticsEnabled)
                                        downloadComplete = true
                                        if (fellBackToDefaultFolder) fullError = folderAccessLostError
                                        delay(2000)
                                        downloadComplete = false
                                        onDownloadsSaved()
                                    } else {
                                        fullError = dlResult.exceptionOrNull()?.message ?: downloadFailedError
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !isSaving && !isStory && (media == null || media?.size != deselectedIndices.size),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isYouTube) YtRed else IgPink,
                            contentColor = Color.White,
                            disabledContainerColor = (if (isYouTube) YtRed else IgPink).copy(alpha = 0.5f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
                    ) {
                        when {
                            isSaving -> {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    savingActionLabel ?: stringResource(R.string.saving_label),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            downloadComplete -> {
                                Icon(
                                    imageVector = AppIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.saved_label),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            else -> {
                                val isProfile = InstagramDownloader.isProfileUrl(cleanedUrl)
                                Icon(
                                    imageVector = if (isProfile) AppIcons.Image else AppIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isProfile -> stringResource(R.string.open_profile_label)
                                        isYouTube && selectedYtHeight != null -> "Download Video (${selectedYtHeight}p)"
                                        else -> stringResource(R.string.download_label)
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Instagram Inline Preview + 4 Feature Buttons (Video, Audio, Cover, Copy Caption) ──
            AnimatedVisibility(
                visible = media != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                val items = media ?: emptyList()
                val firstMeta = items.firstOrNull()?.meta
                val hasVideo = items.any { it.isVideo }

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (if (items.size > 1) stringResource(R.string.items_count_format, items.size)
                                    else stringResource(R.string.preview_label)) +
                                    (items.firstOrNull { it.reduced }
                                        ?.let { stringResource(R.string.data_saver_suffix_format, it.width) } ?: ""),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            )
                            firstMeta?.username?.takeIf { it.isNotBlank() }?.let { uname ->
                                Text(
                                    text = "@$uname",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = IgPink
                                    ),
                                    modifier = Modifier.clickable {
                                        copyDownloadText(
                                            context = context,
                                            label = context.getString(R.string.handle_clipboard_label),
                                            text = "@$uname",
                                            confirmation = R.string.handle_copied
                                        )
                                    }
                                )
                            }
                        }

                        firstMeta?.song?.takeIf { it.isNotBlank() }?.let { songName ->
                            Text(
                                text = "♪ $songName",
                                style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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

                        // Caption preview box
                        firstMeta?.caption?.takeIf { it.isNotBlank() }?.let { cap ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { captionExpanded = !captionExpanded }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = cap,
                                        style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant),
                                        maxLines = if (captionExpanded) 12 else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2x2 Action Grid: Video Download, Audio Download, Cover Download, Copy Caption
                        val selectedItems = items.filterIndexed { i, _ -> i !in deselectedIndices }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                        requestStoragePermission()
                                        return@FilledTonalButton
                                    }
                                    coroutineScope.launch {
                                        hapticStart(context, settings.hapticsEnabled)
                                        isSaving = true
                                        savingActionLabel = "Saving Video…"
                                        val res = runCatching {
                                            withContext(Dispatchers.IO) {
                                                selectedItems.forEach { saveToDownloads(it, context, settings) }
                                            }
                                        }
                                        isSaving = false
                                        savingActionLabel = null
                                        if (res.isSuccess) {
                                            hapticComplete(context, settings.hapticsEnabled)
                                            onShowNotice("Saved to Downloads/InstaTube")
                                        } else {
                                            fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                        }
                                    }
                                },
                                enabled = !isSaving && selectedItems.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (hasVideo) stringResource(R.string.download_video_label) else "Photo Download",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                        requestStoragePermission()
                                        return@FilledTonalButton
                                    }
                                    val videoItem = selectedItems.firstOrNull { it.isVideo } ?: items.firstOrNull { it.isVideo }
                                    if (videoItem == null) {
                                        onShowNotice("This post does not contain an audio/video stream")
                                        return@FilledTonalButton
                                    }
                                    coroutineScope.launch {
                                        hapticStart(context, settings.hapticsEnabled)
                                        isSaving = true
                                        savingActionLabel = "Extracting Audio…"
                                        val res = runCatching {
                                            withContext(Dispatchers.IO) {
                                                saveAudioToDownloads(videoItem, context, settings)
                                            }
                                        }
                                        isSaving = false
                                        savingActionLabel = null
                                        if (res.isSuccess) {
                                            hapticComplete(context, settings.hapticsEnabled)
                                            onShowNotice("Audio (.m4a) saved to Downloads/InstaTube")
                                        } else {
                                            fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                        }
                                    }
                                },
                                enabled = !isSaving && hasVideo,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.download_audio_label),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                        requestStoragePermission()
                                        return@OutlinedButton
                                    }
                                    coroutineScope.launch {
                                        hapticStart(context, settings.hapticsEnabled)
                                        isSaving = true
                                        savingActionLabel = "Saving Cover…"
                                        val targets = selectedItems.ifEmpty { items }
                                        val res = runCatching {
                                            withContext(Dispatchers.IO) {
                                                targets.forEach { saveCoverToDownloads(it, context, settings) }
                                            }
                                        }
                                        isSaving = false
                                        savingActionLabel = null
                                        if (res.isSuccess) {
                                            hapticComplete(context, settings.hapticsEnabled)
                                            onShowNotice("Cover image saved to Downloads/InstaTube")
                                        } else {
                                            fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                        }
                                    }
                                },
                                enabled = !isSaving && items.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.download_cover_label),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
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
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.copy_caption_label),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // ── YouTube Inline Preview + Quality Selector + 4 Feature Buttons + Ad-Free Player ──
            AnimatedVisibility(
                visible = ytDetails != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                val details = ytDetails
                if (details != null) {
                    val item = details.item
                    val opts = ytOptions ?: emptyList()

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = colorScheme.surface.copy(alpha = 0.95f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onPlayYouTubeVideo(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Thumbnail(
                                    url = item.thumbnail,
                                    modifier = Modifier.fillMaxSize(),
                                    radius = 16,
                                    videoId = item.id
                                )
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.58f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play video",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
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
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                ),
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
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (details.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { captionExpanded = !captionExpanded }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = details.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = if (captionExpanded) 12 else 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (opts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Select Video Quality",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    opts.forEach { opt ->
                                        val isSel = selectedYtHeight == opt.height
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { selectedYtHeight = opt.height },
                                            label = {
                                                Text(
                                                    text = opt.label + (if (opt.estimatedBytes > 0) " · ${formatBytes(opt.estimatedBytes)}" else ""),
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Row 1: Video Download + Audio Download
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val h = selectedYtHeight ?: opts.firstOrNull()?.height ?: 720
                                        hapticStart(context, settings.hapticsEnabled)
                                        val added = Downloads.enqueue(item, h)
                                        onShowNotice(
                                            if (added) "Downloading ${h}p • ${item.title}"
                                            else "Already in downloads"
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = selectedYtHeight?.let { "Video (${it}p)" } ?: stringResource(R.string.download_video_label),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        hapticStart(context, settings.hapticsEnabled)
                                        val added = Downloads.enqueueAudio(item)
                                        onShowNotice(
                                            if (added) "Downloading Audio (M4A) • ${item.title}"
                                            else "Audio already in downloads"
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.download_audio_label),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Cover Download + Copy Caption
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
                                            requestStoragePermission()
                                            return@OutlinedButton
                                        }
                                        coroutineScope.launch {
                                            hapticStart(context, settings.hapticsEnabled)
                                            isSaving = true
                                            savingActionLabel = "Saving Cover…"
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
                                                hapticComplete(context, settings.hapticsEnabled)
                                                onShowNotice("YouTube cover saved to Downloads/InstaTube")
                                            } else {
                                                fullError = res.exceptionOrNull()?.message ?: downloadFailedError
                                            }
                                        }
                                    },
                                    enabled = !isSaving,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.download_cover_label),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
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
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.copy_caption_label),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 3: Open in Ad-Free Liquid Glass Player
                            Button(
                                onClick = { onPlayYouTubeVideo(item) },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.secondaryContainer,
                                    contentColor = colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.watch_player_label),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── Error Card (copyable) ────────────────────────────────────
            AnimatedVisibility(
                visible = fullError != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val errorLabel = stringResource(R.string.error_title)
                            Text(
                                errorLabel,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Row {
                                val copyErrorDescription = stringResource(R.string.copy_error_description)
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(errorLabel, fullError)
                                    clipboard.setPrimaryClip(clip)
                                }) {
                                    Icon(
                                        imageVector = AppIcons.ContentCopy,
                                        contentDescription = copyErrorDescription,
                                        tint = colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                TextButton(onClick = { fullError = null }) {
                                    Text(
                                        stringResource(R.string.dismiss_label),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = colorScheme.onErrorContainer
                                        )
                                    )
                                }
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = fullError ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            stringResource(R.string.update_notice),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                uriHandler.openUri("https://github.com/xboyrex/InstaTube/releases/latest")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.error,
                                contentColor = colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = AppIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.update_button),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── GitHub & Author Credit ───────────────────────────────────
            InstaTubeFooterCredit()

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Top Bar Actions (Downloads Dialog + Settings)
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenLibrary) {
                Text(
                    stringResource(R.string.downloads_label),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { showSettings = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.22f),
                    contentColor = Color.White
                )
            ) {
                Icon(AppIcons.Settings, contentDescription = stringResource(R.string.settings_content_description))
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
private fun InstaTubeFooterCredit() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        version?.let {
            Text(
                stringResource(R.string.version_label_format, it),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.65f)
                )
            )
        }
        TextButton(
            onClick = { uriHandler.openUri("https://github.com/xboyrex/InstaTube") },
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.github),
                    contentDescription = stringResource(R.string.github_content_description),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.made_by_label),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        TextButton(onClick = { uriHandler.openUri("https://github.com/xboyrex/InstaTube/blob/main/PRIVACY.md") }) {
            Text(
                stringResource(R.string.privacy_policy_label),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.65f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstaTubeSettingsDialog(
    settings: AppSettings,
    selectedFolderName: String,
    onChooseFolder: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onOpenFullSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var quality by remember { mutableStateOf(settings.quality) }
    var customWidth by remember { mutableIntStateOf(settings.customWidth) }
    var haptics by remember { mutableStateOf(settings.hapticsEnabled) }
    var theme by remember { mutableStateOf(settings.theme) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsHeading(stringResource(R.string.download_location_heading))
                Text(selectedFolderName, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(
                    onClick = onChooseFolder,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(stringResource(R.string.choose_folder_button)) }

                SettingsHeading(stringResource(R.string.download_quality_heading))
                DownloadQuality.entries.forEach { option ->
                    SettingsRadio(
                        stringResource(option.labelRes),
                        stringResource(option.descriptionRes),
                        quality == option
                    ) {
                        quality = option
                        settings.quality = option
                    }
                }
                if (quality == DownloadQuality.CUSTOM) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1080, 720, 640, 480, 320, 240).forEach { width ->
                            FilterChip(
                                selected = customWidth == width,
                                onClick = {
                                    customWidth = width
                                    settings.customWidth = width
                                },
                                label = { Text(stringResource(R.string.resolution_px_format, width)) }
                            )
                        }
                    }
                }

                SettingsHeading(stringResource(R.string.appearance_heading))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.haptic_feedback_label), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.haptic_feedback_description), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = haptics,
                        onCheckedChange = {
                            haptics = it
                            settings.hapticsEnabled = it
                        }
                    )
                }
                AppTheme.entries.forEach { option ->
                    SettingsRadio(stringResource(option.labelRes), null, theme == option) {
                        theme = option
                        settings.theme = option
                        onThemeChanged(option)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onOpenFullSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("YouTube, Themes & SponsorBlock Settings")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done_button)) } }
    )
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRadio(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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

