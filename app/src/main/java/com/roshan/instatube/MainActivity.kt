package com.roshan.instatube

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

class MainActivity : ComponentActivity() {
    private val appSettings by lazy { AppSettings(this) }
    private val selectedFolderName = mutableStateOf(AppSettings.DEFAULT_FOLDER_NAME)
    private val showLibrary = mutableStateOf(false)
    class SharedUrl(val url: String)
    private val sharedUrl = mutableStateOf(SharedUrl(""))
    private val clipboardCandidateUrl = mutableStateOf("")
    private val clipboardEventCounter = mutableIntStateOf(0)

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
            !appSettings.notificationPermissionAsked &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            appSettings.notificationPermissionAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            InstaTubeTheme {
                InstaTubeApp(
                    initialUrl = sharedUrl.value.url,
                    initialUrlKey = sharedUrl.value,
                    clipboardCandidateUrl = clipboardCandidateUrl.value,
                    clipboardEventKey = clipboardEventCounter.intValue,
                    settings = appSettings,
                    selectedFolderName = selectedFolderName.value,
                    openDownloadsInitially = showLibrary.value,
                    onConsumeOpenDownloads = { showLibrary.value = false },
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

    override fun onResume() {
        super.onResume()
        val forceClipboardRead = intent?.getBooleanExtra(EXTRA_READ_CLIPBOARD, false) == true
        val detected = inspectClipboardForSupportedLink(force = forceClipboardRead)
        if (forceClipboardRead && detected) intent?.removeExtra(EXTRA_READ_CLIPBOARD)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val forceClipboardRead = intent?.getBooleanExtra(EXTRA_READ_CLIPBOARD, false) == true
            inspectClipboardForSupportedLink(force = forceClipboardRead)
            // Once the activity owns focus, consume the one-shot overlay request even if the
            // clipboard no longer contains a supported link. Avoid repeatedly forcing reads.
            if (forceClipboardRead) intent?.removeExtra(EXTRA_READ_CLIPBOARD)
        }
    }

    private fun inspectClipboardForSupportedLink(force: Boolean = false): Boolean {
        if (!force && !appSettings.autoDetectClipboard) return false
        return runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@runCatching false
            val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (raw.isBlank()) return@runCatching false
            val clean = InstagramDownloader.extractCleanUrl(raw)
            if (InstagramDownloader.isInstagramUrl(clean) || InstagramDownloader.isYouTubeUrl(clean)) {
                clipboardCandidateUrl.value = clean
                clipboardEventCounter.intValue++
                true
            } else false
        }.getOrDefault(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingUrl = handleSharedIntent(intent)
        if (incomingUrl.isNotBlank()) sharedUrl.value = SharedUrl(incomingUrl)
        // A notification tap may only request the Downloads tab; don't clear an active input URL.
        if (intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false)) showLibrary.value = true

        // A singleTask activity can receive the overlay intent without another onResume callback.
        // Defer the one-shot read until the window has focus so Android's foreground clipboard
        // restrictions permit it; onWindowFocusChanged remains the fallback if focus arrives later.
        if (intent.getBooleanExtra(EXTRA_READ_CLIPBOARD, false)) {
            window.decorView.post {
                if (!isFinishing && window.decorView.hasWindowFocus() &&
                    intent.getBooleanExtra(EXTRA_READ_CLIPBOARD, false)
                ) {
                    inspectClipboardForSupportedLink(force = true)
                    intent.removeExtra(EXTRA_READ_CLIPBOARD)
                    this.intent?.removeExtra(EXTRA_READ_CLIPBOARD)
                }
            }
        }
    }

    private fun handleSharedIntent(intent: Intent?): String {
        val explicitSharedUrl = intent?.getStringExtra(EXTRA_SHARED_URL).orEmpty()
        val sharedText = when {
            explicitSharedUrl.isNotBlank() -> explicitSharedUrl
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            else -> ""
        }
        val clean = InstagramDownloader.extractCleanUrl(sharedText)
        return clean.takeIf {
            InstagramDownloader.isInstagramUrl(it) || InstagramDownloader.isYouTubeUrl(it)
        }.orEmpty()
    }

    companion object {
        const val EXTRA_OPEN_LIBRARY = "com.roshan.instatube.extra.OPEN_LIBRARY"
        const val EXTRA_READ_CLIPBOARD = "com.roshan.instatube.extra.READ_CLIPBOARD"
        const val EXTRA_SHARED_URL = "com.roshan.instatube.extra.SHARED_URL"
    }
}

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    InstaTube("Extract", AppIcons.NavDownloaderOutline, AppIcons.NavDownloaderFilled),
    Downloads("Vault", AppIcons.NavVaultOutline, AppIcons.NavVaultFilled),
    Settings("Settings", AppIcons.NavTuneOutline, AppIcons.NavTuneFilled),
}

private val NavHeight = 64.dp
private val NavWidth = 340.dp
private val NavLift = 10.dp

@Composable
fun InstaTubeApp(
    initialUrl: String,
    initialUrlKey: Any,
    clipboardCandidateUrl: String,
    clipboardEventKey: Int,
    settings: AppSettings,
    selectedFolderName: String,
    openDownloadsInitially: Boolean,
    onConsumeOpenDownloads: () -> Unit,
    checkStoragePermission: () -> Boolean,
    requestStoragePermission: () -> Unit,
    onChooseFolder: () -> Unit,
    onFolderAccessLost: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.InstaTube) }

    LaunchedEffect(initialUrlKey) {
        if (initialUrl.isNotBlank()) {
            tab = Tab.InstaTube
        }
    }

    LaunchedEffect(openDownloadsInitially) {
        if (openDownloadsInitially) {
            tab = Tab.Downloads
            onConsumeOpenDownloads()
        }
    }

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
    val pageBackdrop = rememberLayerBackdrop()

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stackBottom = navBottom.coerceAtLeast(8.dp) +
        if (WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding() > 0.dp) 10.dp else 0.dp
    val navTop = stackBottom + NavLift + NavHeight

    var downloadNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(downloadNotice) {
        if (downloadNotice != null) {
            kotlinx.coroutines.delay(3800)
            downloadNotice = null
        }
    }

    val contentPadding = PaddingValues(
        bottom = navTop + 16.dp,
    )

    BackHandler(enabled = tab != Tab.InstaTube) {
        tab = Tab.InstaTube
    }
    BackHandler(enabled = updateState.visible) { updateState.dismiss() }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
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
                        clipboardCandidateUrl = clipboardCandidateUrl,
                        clipboardEventKey = clipboardEventKey,
                        contentPadding = contentPadding,
                        settings = settings,
                        selectedFolderName = selectedFolderName,
                        checkStoragePermission = checkStoragePermission,
                        requestStoragePermission = requestStoragePermission,
                        onChooseFolder = onChooseFolder,
                        onThemeChanged = { _ -> },
                        onFolderAccessLost = onFolderAccessLost,
                        onOpenLibrary = { tab = Tab.Downloads },
                        onDownloadsSaved = { tab = Tab.Downloads },
                        onOpenFullSettings = { tab = Tab.Settings },
                        onShowNotice = { msg -> downloadNotice = msg }
                    )
                    Tab.Downloads -> DownloadsScreen(
                        contentPadding = contentPadding,
                        selectedFolderName = selectedFolderName,
                        onBrowse = { tab = Tab.InstaTube },
                        onChooseFolder = onChooseFolder,
                        scrollToTop = scrollToTop[Tab.Downloads] ?: 0,
                    )
                    Tab.Settings -> SettingsScreen(
                        settings = settings,
                        selectedFolderName = selectedFolderName,
                        onChooseFolder = onChooseFolder,
                        contentPadding = contentPadding,
                        onCheckUpdate = { updateState.check(manual = true) },
                    )
                }
            }

            BottomGestureGuard(Modifier.align(Alignment.BottomCenter))
        }

        val selectTab: (Tab) -> Unit = { selected ->
            if (selected == tab) {
                scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
            } else {
                tab = selected
            }
        }

        LiquidBottomBar(
            selectedTabIndex = { Tab.entries.indexOf(tab) },
            onTabSelected = { index -> Tab.entries.getOrNull(index)?.let(selectTab) },
            backdrop = pageBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = stackBottom + NavLift)
                .widthIn(max = NavWidth)
                .fillMaxWidth(),
        ) {
            Tab.entries.forEach { t ->
                LiquidNavTab(t, t == tab) { selectTab(t) }
            }
        }

        AnimatedVisibility(
            visible = downloadNotice != null,
            enter = slideInVertically(tween(280, easing = Motion.Emphasized)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200, easing = Motion.Emphasized)) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navTop + 12.dp)
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
                        },
                    ) {
                        Text("VIEW", fontWeight = FontWeight.Bold, color = c.primary, fontSize = 13.sp)
                    }
                }
            }
        }

        UpdateOverlay(updateState, pageBackdrop)
    }
}
