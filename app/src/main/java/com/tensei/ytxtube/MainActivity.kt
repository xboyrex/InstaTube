package com.tensei.ytxtube

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appSettings by lazy { AppSettings(this) }
    private val selectedFolderName = mutableStateOf(AppSettings.DEFAULT_FOLDER_NAME)
    private val showLibrary = mutableStateOf(false)
    class SharedUrl(val url: String)
    private val sharedUrl = mutableStateOf(SharedUrl(""))

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val name = runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':') }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: getString(R.string.selected_folder_default)
        appSettings.downloadTreeUri = uri.toString()
        appSettings.downloadFolderName = name
        selectedFolderName.value = name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        selectedFolderName.value = appSettings.downloadFolderName
        sharedUrl.value = SharedUrl(handleSharedIntent(intent))
        if (savedInstanceState == null) {
            showLibrary.value = intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            YtxTubeTheme {
                InstaTubeApp(
                    initialUrl = sharedUrl.value.url,
                    initialUrlKey = sharedUrl.value,
                    settings = appSettings,
                    selectedFolderName = selectedFolderName.value,
                    showLibraryDialog = showLibrary.value,
                    onShowLibraryChange = { showLibrary.value = it },
                    checkStoragePermission = {
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    },
                    requestStoragePermission = {
                        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    },
                    onChooseFolder = { folderPickerLauncher.launch(null) },
                    onFolderAccessLost = { selectedFolderName.value = AppSettings.DEFAULT_FOLDER_NAME }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl.value = SharedUrl(handleSharedIntent(intent))
        if (intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false)) showLibrary.value = true
    }

    private fun handleSharedIntent(intent: Intent?): String {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val clean = InstagramDownloader.extractCleanUrl(text)
            if (InstagramDownloader.isInstagramUrl(clean) || InstagramDownloader.isYouTubeUrl(clean)) {
                return clean
            }
        }
        return ""
    }

    companion object {
        const val EXTRA_OPEN_LIBRARY = "com.roshan.instatube.extra.OPEN_LIBRARY"
    }
}

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    InstaTube("InstaTube", Icons.Outlined.Download, Icons.Rounded.Download),
    Home("YouTube", Icons.Outlined.Home, Icons.Rounded.Home),
    Shorts("Shorts", Icons.Outlined.PlayCircle, Icons.Rounded.PlayCircle),
    Music("Music", Icons.Outlined.MusicNote, Icons.Rounded.MusicNote),
    Library("Library", Icons.Outlined.Subscriptions, Icons.Rounded.Subscriptions),
    Downloads("Saved", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
}

private val NavHeight = 64.dp
private val NavWidth = 368.dp
private val NavLift = 10.dp
private val MiniWidth = 368.dp
private val MiniGap = 8.dp

@Composable
fun InstaTubeApp(
    initialUrl: String,
    initialUrlKey: Any,
    settings: AppSettings,
    selectedFolderName: String,
    showLibraryDialog: Boolean,
    onShowLibraryChange: (Boolean) -> Unit,
    checkStoragePermission: () -> Boolean,
    requestStoragePermission: () -> Unit,
    onChooseFolder: () -> Unit,
    onFolderAccessLost: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val player = rememberPlayerState()
    val scope = rememberCoroutineScope()
    val search = remember { SearchState(scope) }
    val lib = remember { LibraryData(scope) }
    var signingIn by remember { mutableStateOf(false) }
    var openSettings by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<PlaylistRef?>(null) }
    var openChannel by remember { mutableStateOf<String?>(null) }
    var shortsEntry by remember { mutableStateOf<VideoItem?>(null) }
    var openAllSubscriptions by remember { mutableStateOf(false) }
    var openSearch by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.InstaTube) }

    LaunchedEffect(initialUrlKey) {
        if (initialUrl.isNotBlank()) {
            tab = Tab.InstaTube
        }
    }

    LaunchedEffect(tab) { if (tab == Tab.Shorts && player.isPlaying) player.togglePlay() }
    LaunchedEffect(openSearch) { if (openSearch) search.reset() }

    var channelState by remember { mutableStateOf<ChannelState?>(null) }
    val playlistFeed = remember(openPlaylist?.id) { openPlaylist?.let { lib.playlistFeed(it.id) } }
    val updateState = remember { UpdateState(scope) }
    val appContext = LocalContext.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_400)
        updateState.check()
    }
    LaunchedEffect(updateState.message) {
        updateState.message?.let { message ->
            android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            updateState.message = null
        }
    }

    val scrollToTop = remember { mutableStateMapOf<Tab, Int>() }
    val reduced = rememberReducedMotion()
    val morph = remember { PlayerMorph(scope) { expanded -> player.expanded = expanded } }

    val pageBackdrop = rememberLayerBackdrop()
    val playerBackdrop = rememberLayerBackdrop()
    val glassBackdrop = rememberCombinedBackdrop(pageBackdrop, playerBackdrop)

    val rootView = LocalView.current
    DisposableEffect(player.isPlaying) {
        rootView.keepScreenOn = player.isPlaying
        onDispose { rootView.keepScreenOn = false }
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(player.expanded) {
        if (player.expanded) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.setBackground(true)
                androidx.lifecycle.Lifecycle.Event.ON_START -> player.setBackground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stackBottom = navBottom.coerceAtLeast(8.dp) +
        if (WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding() > 0.dp) 10.dp else 0.dp
    val navTop = stackBottom + NavLift + NavHeight
    var optionsFor by remember { mutableStateOf<VideoItem?>(null) }
    var downloadNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(downloadNotice) {
        if (downloadNotice != null) {
            kotlinx.coroutines.delay(4000)
            downloadNotice = null
        }
    }
    val hasMini = player.current != null

    val contentPadding = PaddingValues(
        bottom = navTop + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 16.dp,
    )

    val activity = LocalContext.current as Activity
    val orientation = rememberOrientationController(activity)
    val playerOpen = player.current != null && player.expanded

    orientation.Bind(playerOpen && !AudioOnly.enabled && !player.isMusicMode)
    val pip = remember(activity) { PipController(activity as ComponentActivity) }

    pip.Bind(onClosed = { player.controller?.pause() })
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val fullscreenBeforePip = remember { mutableStateOf(false) }
    val showFullscreen = if (pip.active) fullscreenBeforePip.value
        else (playerOpen && (landscape || player.portraitFullscreen) && !player.isMusicMode)
    SideEffect { if (!pip.active) fullscreenBeforePip.value = showFullscreen }
    LaunchedEffect(showFullscreen) { player.fullscreen = showFullscreen }
    SystemBarsHidden(showFullscreen)

    fun exitFullscreen() {
        player.portraitFullscreen = false
        orientation.exitFullscreen()
    }

    fun toggleFullscreen() {
        when {
            showFullscreen -> exitFullscreen()
            player.portraitVideo -> player.portraitFullscreen = true
            else -> orientation.enterFullscreen()
        }
    }

    BackHandler(enabled = player.expanded) { player.expanded = false }
    BackHandler(enabled = !player.expanded && openSettings) { openSettings = false }
    BackHandler(enabled = !player.expanded && openAllSubscriptions && openChannel == null) { openAllSubscriptions = false }
    BackHandler(enabled = !player.expanded && tab != Tab.InstaTube && openPlaylist == null && openChannel == null && !openAllSubscriptions) {
        tab = Tab.InstaTube
    }
    BackHandler(enabled = !player.expanded && openSearch) { openSearch = false }
    BackHandler(enabled = !player.expanded && openPlaylist != null) { openPlaylist = null }
    BackHandler(enabled = signingIn) { signingIn = false }
    BackHandler(enabled = !player.expanded && openChannel != null) { openChannel = null }

    BackHandler(enabled = showFullscreen) { exitFullscreen() }
    BackHandler(enabled = shortsEntry != null) { shortsEntry = null }
    BackHandler(enabled = updateState.visible) { updateState.dismiss() }

    val density = LocalDensity.current

    LaunchedEffect(player.expanded, player.current?.id) {
        if (player.current == null) morph.progress.snapTo(0f) else morph.animateTo(player.expanded, reduced)
    }
    val showPlayer by remember {
        derivedStateOf { player.current != null && (player.expanded || morph.dragging || morph.progress.isRunning || morph.progress.value > 0f) }
    }

    if (showLibraryDialog) {
        DownloadsDialog(onDismiss = { onShowLibraryChange(false) })
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
        val stageAspect = player.stageAspect
        val stageTopPx = with(density) {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
        }
        val geometry = remember(constraints.maxWidth, constraints.maxHeight, stackBottom, density, stageAspect, stageTopPx) {
            with(density) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val miniBottom = h - (navTop + MiniGap).toPx()
                val miniW = minOf(w - 32.dp.toPx(), MiniWidth.toPx())
                val miniLeft = (w - miniW) / 2f
                val miniCard = Rect(miniLeft, miniBottom - MiniHeight.toPx(), miniLeft + miniW, miniBottom)
                val thumb = Rect(
                    Offset(miniCard.left + MiniThumbStart.toPx(), miniCard.top + (MiniHeight - MiniThumbHeight).toPx() / 2f),
                    Size(MiniThumbWidth.toPx(), MiniThumbHeight.toPx()),
                )
                val side = StageSideGap.toPx()
                val stageTop = stageTopPx
                val stage = Rect(side, stageTop, w - side, stageTop + (w - 2 * side) / stageAspect)
                MorphGeometry(
                    screen = Size(w, h),
                    miniCard = miniCard,
                    miniThumb = thumb,
                    stage = stage,
                    miniRadius = MiniRadius.toPx(),
                    thumbRadius = MiniThumbRadius.toPx(),
                    stageRadius = StageRadius.toPx(),
                )
            }
        }
        SideEffect { morph.geometry = geometry }

        val sharedSurface = remember(player) {
            androidx.compose.runtime.movableContentOf { m: Modifier -> VideoSurface(player.controller, m) }
        }
        CompositionLocalProvider(
            LocalVideoSurface provides if (pip.active) NoVideoSurface else sharedSurface,
            LocalPictureInPicture provides pip,
            LocalVideoMenu provides { optionsFor = it },
            LocalChannelOpener provides { url ->
                player.expanded = false
                if (channelState?.url != url) channelState = ChannelState(url, scope)
                openChannel = url
            },
        ) {
            Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(c.surface)) {
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = if (TvMode.isTv) 1280.dp else Dp.Unspecified)
                        .fillMaxHeight(),
                    transitionSpec = {
                        if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                        else (fadeIn(tween(Motion.PageEnterMs, easing = Motion.Emphasized)) +
                            slideInVertically(tween(Motion.PageRiseMs, easing = Motion.Emphasized)) { (it / 35).coerceIn(8, 24) }) togetherWith
                            fadeOut(tween(Motion.PageExitMs))
                    },
                    label = "tabs",
                ) { t ->
                    when (t) {
                        Tab.InstaTube -> InstaTubeDownloaderScreen(
                            initialUrl = initialUrl,
                            initialUrlKey = initialUrlKey,
                            contentPadding = contentPadding,
                            settings = settings,
                            selectedFolderName = selectedFolderName,
                            checkStoragePermission = checkStoragePermission,
                            requestStoragePermission = requestStoragePermission,
                            onChooseFolder = onChooseFolder,
                            onThemeChanged = { _ -> },
                            onFolderAccessLost = onFolderAccessLost,
                            onOpenLibrary = { onShowLibraryChange(true) },
                            onDownloadsSaved = { onShowLibraryChange(true) },
                            onOpenFullSettings = { openSettings = true },
                            onPlayYouTubeVideo = { videoItem -> player.open(videoItem) },
                            onShowNotice = { msg -> downloadNotice = msg }
                        )
                        Tab.Home -> HomeScreen(
                            lib, contentPadding, onOpen = player::open, onSearch = { openSearch = true },
                            onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                            scrollToTop = scrollToTop[Tab.Home] ?: 0,
                        )
                        Tab.Music -> MusicScreen(
                            lib, contentPadding, onOpen = player::openMusic, onSearch = { openSearch = true },
                            onAccount = { openSettings = true },
                            onPlayNext = player::playNextMusic,
                            onOpenQueue = { items, index -> player.openMusicQueue(items, index) },
                            scrollToTop = scrollToTop[Tab.Music] ?: 0,
                        )
                        Tab.Shorts -> ShortsScreen(
                            onBack = { tab = Tab.InstaTube },
                            onSignIn = { signingIn = true },
                            onSearch = { openSearch = true },
                            bottomInset = navTop + 12.dp,
                            active = openChannel == null && !openSearch && !openSettings,
                            onOpenChannel = { url ->
                                player.expanded = false
                                if (channelState?.url != url) channelState = ChannelState(url, scope)
                                openChannel = url
                            },
                        )
                        Tab.Library -> LibraryScreen(
                            lib, contentPadding, onOpen = player::open,
                            onOpenPlaylist = { ref -> if (ref.id == "DL") tab = Tab.Downloads else openPlaylist = ref },
                            onOpenChannel = { url ->
                                player.expanded = false
                                if (channelState?.url != url) channelState = ChannelState(url, scope)
                                openChannel = url
                            },
                            onOpenAllSubscriptions = { openAllSubscriptions = true },
                            onSearch = { openSearch = true },
                            onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                            scrollToTop = scrollToTop[Tab.Library] ?: 0,
                        )
                        Tab.Library -> LibraryScreen(
                            lib, contentPadding, onOpen = player::open,
                            onOpenPlaylist = { ref -> if (ref.id == "DL") tab = Tab.Downloads else openPlaylist = ref },
                            onOpenChannel = { url ->
                                player.expanded = false
                                if (channelState?.url != url) channelState = ChannelState(url, scope)
                                openChannel = url
                            },
                            onOpenAllSubscriptions = { openAllSubscriptions = true },
                            onSearch = { openSearch = true },
                            onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                            scrollToTop = scrollToTop[Tab.Library] ?: 0,
                        )
                        Tab.Library -> LibraryScreen(
                            lib, contentPadding, onOpen = player::open,
                            onOpenPlaylist = { ref -> if (ref.id == "DL") tab = Tab.Downloads else openPlaylist = ref },
                            onOpenChannel = { url ->
                                player.expanded = false
                                if (channelState?.url != url) channelState = ChannelState(url, scope)
                                openChannel = url
                            },
                            onOpenAllSubscriptions = { openAllSubscriptions = true },
                            onSearch = { openSearch = true },
                            onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                            scrollToTop = scrollToTop[Tab.Library] ?: 0,
                        )
                        Tab.Library -> LibraryScreen(
                            lib, contentPadding, onOpen = player::open,
                            onOpenPlaylist = { ref -> if (ref.id == "DL") tab = Tab.Downloads else openPlaylist = ref },
                            onOpenChannel = { url ->
                                player.expanded = false
                                if (channelState?.url != url) channelState = ChannelState(url, scope)
                                openChannel = url
                            },
                            onOpenAllSubscriptions = { openAllSubscriptions = true },
                            onSearch = { openSearch = true },
                            onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                            scrollToTop = scrollToTop[Tab.Library] ?: 0,
                        )
                        Tab.Downloads -> DownloadsScreen(
                            contentPadding,
                            onOpen = player::open,
                            onOpenMusic = player::openMusic,
                            onBrowse = { tab = Tab.InstaTube },
                            onBrowseMusic = { tab = Tab.Music },
                            onSearch = { openSearch = true },
                            onAccount = { openSettings = true },
                            scrollToTop = scrollToTop[Tab.Downloads] ?: 0,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = openSearch,
                    enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
                    exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
                ) {
                    SearchScreen(
                        search,
                        contentPadding,
                        onBack = { openSearch = false },
                        onOpen = player::open,
                    )
                }

                AnimatedVisibility(
                    visible = openPlaylist != null,
                    enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
                    exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
                ) {
                    val pl = openPlaylist
                    val feed = playlistFeed
                    if (pl != null && feed != null) {
                        PlaylistScreen(
                            pl, feed, contentPadding,
                            onBack = { openPlaylist = null },
                            onOpen = { video ->
                                if (pl.id == "MDL") player.openMusic(video)
                                else player.open(video)
                            },
                            channelAvatarSeed = lib.channels.associate { it.id to it.avatar },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = openAllSubscriptions,
                    enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
                    exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
                ) {
                    AllSubscriptionsScreen(
                        channels = lib.channels,
                        contentPadding = contentPadding,
                        onBack = { openAllSubscriptions = false },
                    )
                }

                AnimatedVisibility(
                    visible = openChannel != null,
                    enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
                    exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
                ) {
                    channelState?.let { ch ->
                        ChannelScreen(
                            ch,
                            contentPadding,
                            onBack = { openChannel = null },
                            onOpen = player::open,
                            onOpenShort = { video ->
                                player.expanded = false
                                shortsEntry = video
                            },
                            onMessage = { player.actionMessage = it },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = openSettings,
                    enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
                    exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
                ) {
                    SettingsScreen(
                        onBack = { openSettings = false },
                        onSignIn = { signingIn = true },
                        onSignedOut = { lib.reset(); player.refreshAccountState() },
                        contentPadding = contentPadding,
                        onCheckUpdate = { updateState.check(manual = true) },
                    )
                }

                BottomGestureGuard(Modifier.align(Alignment.BottomCenter))
            }

            if (hasMini && !openSettings && tab != Tab.Shorts && shortsEntry == null) {
                val dismiss = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(player.current?.id) { dismiss.snapTo(0f) }
                val dismissDistance = with(density) { 56.dp.toPx() }
                MiniPlayer(
                    player,
                    onExpand = { player.expanded = true },
                    showVideo = !showPlayer && !showFullscreen,
                    backdrop = pageBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = navTop + MiniGap)
                        .widthIn(max = MiniWidth)
                        .graphicsLayer {
                            translationY = dismiss.value
                            alpha = (if (showPlayer) 0f else 1f) * (1f - (dismiss.value / (dismissDistance * 1.6f)).coerceIn(0f, 1f))
                        }
                        .draggable(
                            state = rememberDraggableState { delta ->
                                if (morph.progress.value <= 0f && (delta > 0f || dismiss.value > 0f)) {
                                    scope.launch { dismiss.snapTo((dismiss.value + delta).coerceAtLeast(0f)) }
                                } else {
                                    morph.dragBy(delta)
                                }
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (dismiss.value > 0f) {
                                    if (dismiss.value > dismissDistance || velocity > 800f) {
                                        scope.launch {
                                            if (!reduced) dismiss.animateTo(dismissDistance * 2.2f, tween(200, easing = Motion.Emphasized))
                                            player.close()
                                        }
                                    } else {
                                        scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 600f)) }
                                    }
                                } else {
                                    morph.settle(velocity, reduced)
                                }
                            },
                        ),
                )
            }

            val selectTab: (Tab) -> Unit = { selected ->
                when {
                    selected == tab && openSearch -> openSearch = false
                    selected == tab && openChannel != null -> openChannel = null
                    selected == tab && openPlaylist != null -> openPlaylist = null
                    selected == tab && openAllSubscriptions -> openAllSubscriptions = false
                    selected == tab -> scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
                    else -> {
                        openPlaylist = null
                        openChannel = null
                        openAllSubscriptions = false
                        openSearch = false
                        tab = selected
                    }
                }
            }

            val showBottomBar = !openSettings && !openSearch && shortsEntry == null
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(260, easing = Motion.Emphasized)) { it } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(200, easing = Motion.Emphasized)) { it } + fadeOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = stackBottom + NavLift)
                    .widthIn(max = NavWidth)
                    .fillMaxWidth(),
            ) {
                LiquidBottomBar(
                    selectedTabIndex = { Tab.entries.indexOf(tab) },
                    onTabSelected = { index -> Tab.entries.getOrNull(index)?.let(selectTab) },
                    backdrop = pageBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab.entries.forEach { t ->
                        LiquidNavTab(t, t == tab) { selectTab(t) }
                    }
                }
            }

            shortsEntry?.let { entry ->
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ShortsScreen(
                        onBack = { shortsEntry = null },
                        onSignIn = { signingIn = true },
                        onSearch = { openSearch = true },
                        bottomInset = 12.dp,
                        startItem = entry,
                        active = openChannel == null && !openSearch,
                        onOpenChannel = { url ->
                            shortsEntry = null
                            player.expanded = false
                            if (channelState?.url != url) channelState = ChannelState(url, scope)
                            openChannel = url
                        },
                    )
                }
            }

            if (showFullscreen) {
                Box(Modifier.fillMaxSize().layerBackdrop(playerBackdrop)) {
                    FullscreenPlayer(player, onExit = { exitFullscreen() })
                }
            } else if (showPlayer) {
                Box(Modifier.fillMaxSize().layerBackdrop(playerBackdrop)) {
                    PlayerScreen(
                        player, morph,
                        onOpen = player::open,
                        onCollapse = { player.expanded = false },
                        onFullscreen = { toggleFullscreen() },
                    )
                }
            }

            if (signingIn) {
                SignInScreen(
                    onDone = { signingIn = false; lib.reset(); player.refreshAccountState() },
                    onCancel = { signingIn = false },
                )
            }

            when (player.sheet) {
                PlayerSheet.Quality -> QualitySheet(player, onDismiss = { player.sheet = null })
                PlayerSheet.Settings -> Unit
                PlayerSheet.Comments -> Unit
                PlayerSheet.Queue -> QueueSheet(player, onDismiss = { player.sheet = null })
                PlayerSheet.Speed -> Unit
                PlayerSheet.Audio -> Unit
                null -> Unit
            }

            AnimatedVisibility(
                visible = player.sheet == PlayerSheet.Settings,
                enter = fadeIn(tween(180, easing = Motion.Emphasized)),
                exit = fadeOut(tween(150)),
            ) {
                PlayerSettingsSheet(player, glassBackdrop, onDismiss = { player.sheet = null })
            }

            AnimatedVisibility(
                visible = player.sheet == PlayerSheet.Speed,
                enter = fadeIn(tween(180, easing = Motion.Emphasized)),
                exit = fadeOut(tween(150)),
            ) {
                SpeedSheet(player, glassBackdrop, onDismiss = { player.sheet = null })
            }

            AnimatedVisibility(
                visible = player.sheet == PlayerSheet.Audio,
                enter = fadeIn(tween(180, easing = Motion.Emphasized)),
                exit = fadeOut(tween(150)),
            ) {
                AudioTrackSheet(player, glassBackdrop, onDismiss = { player.sheet = null })
            }

            player.downloadFor?.let { video ->
                DownloadQualitySheet(
                    video = video,
                    onDownloadStarted = { msg -> downloadNotice = msg },
                    onDismiss = { player.downloadFor = null },
                )
            }

            AnimatedVisibility(
                visible = downloadNotice != null,
                enter = slideInVertically(tween(280, easing = Motion.Emphasized)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200, easing = Motion.Emphasized)) { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navTop + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp)
                    .padding(horizontal = 20.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = c.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = 420.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            downloadNotice.orEmpty(),
                            color = c.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                tab = Tab.Downloads
                                downloadNotice = null
                                if (showPlayer) player.expanded = false
                            },
                        ) {
                            Text("VIEW", fontWeight = FontWeight.Bold, color = c.primary, fontSize = 13.sp)
                        }
                    }
                }
            }
            optionsFor?.let { video -> VideoOptionsSheet(video, player, onDismiss = { optionsFor = null }) }

            if (!showPlayer) {
                QueueMessage(player, Modifier.align(Alignment.BottomCenter).padding(bottom = navTop + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp))
            }

            if (pip.active) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    sharedSurface(Modifier.fillMaxSize().aspectFit(player.videoAspect))
                }
            }

            UpdateOverlay(updateState, pageBackdrop)
        }
    }
}

@Composable
private fun QueueMessage(player: PlayerState, modifier: Modifier) {
    val message = player.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_000)
        player.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(50)).background(c.inverseSurface).padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

