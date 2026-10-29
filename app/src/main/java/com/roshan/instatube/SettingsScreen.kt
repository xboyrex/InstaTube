package com.roshan.instatube

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    selectedFolderName: String,
    onChooseFolder: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onCheckUpdate: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var autoDetect by remember { mutableStateOf(settings.autoDetectClipboard) }
    var autoDownload by remember { mutableStateOf(settings.autoDownloadOnPaste) }
    var haptics by remember { mutableStateOf(settings.hapticsEnabled) }
    var selectedQuality by remember { mutableStateOf(settings.quality) }
    var customWidth by remember { mutableIntStateOf(settings.customWidth) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var overlayPermissionGranted by remember { mutableStateOf(AutomationAccess.canDrawOverlays(context)) }
    var usageAccessGranted by remember { mutableStateOf(AutomationAccess.hasUsageAccess(context)) }
    var notificationsEnabled by remember { mutableStateOf(AutomationAccess.notificationsEnabled(context)) }
    var overlayEnabled by remember {
        mutableStateOf(settings.overlayAssistantEnabled &&
            AutomationAccess.canDrawOverlays(context) && AutomationAccess.hasUsageAccess(context))
    }
    var overlayPermissionStage by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = AutomationAccess.notificationsEnabled(context) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted)
    }

    val activateOverlayAssistant: () -> Unit = {
        settings.overlayAssistantEnabled = true
        overlayEnabled = true
        overlayPermissionStage = 0
        runCatching { LinkAssistantService.start(context) }.onFailure {
            settings.overlayAssistantEnabled = false
            overlayEnabled = false
            android.widget.Toast.makeText(context, "Could not start the floating assistant", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val requestOverlayAssistant: () -> Unit = {
        when {
            !AutomationAccess.canDrawOverlays(context) -> {
                overlayPermissionStage = 1
                runCatching { AutomationAccess.openOverlaySettings(context) }
            }
            !AutomationAccess.hasUsageAccess(context) -> {
                overlayPermissionStage = 2
                runCatching { AutomationAccess.openUsageAccessSettings(context) }
            }
            else -> activateOverlayAssistant()
        }
    }

    val onResumeAction by rememberUpdatedState(newValue = {
        val overlayNow = AutomationAccess.canDrawOverlays(context)
        val usageNow = AutomationAccess.hasUsageAccess(context)
        overlayPermissionGranted = overlayNow
        usageAccessGranted = usageNow
        notificationsEnabled = AutomationAccess.notificationsEnabled(context)

        when (overlayPermissionStage) {
            1 -> {
                if (overlayNow) {
                    if (usageNow) activateOverlayAssistant()
                    else {
                        overlayPermissionStage = 2
                        runCatching { AutomationAccess.openUsageAccessSettings(context) }
                    }
                } else {
                    overlayPermissionStage = 0
                    overlayEnabled = false
                }
            }
            2 -> {
                if (overlayNow && usageNow) activateOverlayAssistant()
                else {
                    overlayPermissionStage = 0
                    overlayEnabled = false
                }
            }
            else -> {
                overlayEnabled = settings.overlayAssistantEnabled && overlayNow && usageNow
                if (settings.overlayAssistantEnabled && !overlayEnabled) {
                    settings.overlayAssistantEnabled = false
                    LinkAssistantService.stop(context)
                }
            }
        }
    })

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResumeAction()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cardBg = if (dark) Color(0xFF131620) else Color.White
    val cardBorder = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val recessedBg = if (dark) Color(0xFF0B0D14) else Color(0xFFF1F3F8)
    val dividerColor = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Settings",
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = c.onSurface,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = "Engine, storage, interface & developer links",
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant
                )
            }
            StudioBrandMark(size = 32.dp, isBusy = false)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Group 1: Automation & Clipboard ──────────────────────────
            InsetSettingsCard(
                header = "AUTOMATION",
                cardBg = cardBg,
                cardBorder = cardBorder
            ) {
                InsetToggleRow(
                    icon = AppIcons.Sparkle,
                    badgeColor = Color(0xFF0A84FF),
                    title = "Clipboard Auto-Detection",
                    subtitle = "Detect & load copied Instagram or YouTube links on launch",
                    checked = autoDetect,
                    onCheckedChange = {
                        autoDetect = it
                        settings.autoDetectClipboard = it
                    }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetToggleRow(
                    icon = AppIcons.Download,
                    badgeColor = Color(0xFF10B981),
                    title = "Instant Auto-Download",
                    subtitle = "Start saving immediately when a valid link is pasted",
                    checked = autoDownload,
                    onCheckedChange = {
                        autoDownload = it
                        settings.autoDownloadOnPaste = it
                    }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetActionRow(
                    icon = AppIcons.Download,
                    badgeColor = Color(0xFF0A84FF),
                    title = "Download notifications",
                    subtitle = if (notificationsEnabled) {
                        "Enabled · active downloads show progress in the notification shade"
                    } else {
                        "Disabled · allow notifications to see download progress"
                    },
                    actionLabel = if (notificationsEnabled) "Manage" else "Enable",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            runCatching { AutomationAccess.openAppNotificationSettings(context) }
                        }
                    }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetToggleRow(
                    icon = AppIcons.Sparkle,
                    badgeColor = Color(0xFFFF2D75),
                    title = "Floating Instagram assistant",
                    subtitle = when {
                        overlayEnabled -> "Shows while Instagram is open · tap to check the copied link"
                        !overlayPermissionGranted -> "Needs Draw over other apps permission"
                        !usageAccessGranted -> "Needs Usage Access to detect when Instagram is open"
                        else -> "Show a small shortcut over Instagram; clipboard contents are checked only after you tap it"
                    },
                    checked = overlayEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            requestOverlayAssistant()
                        } else {
                            overlayPermissionStage = 0
                            settings.overlayAssistantEnabled = false
                            overlayEnabled = false
                            LinkAssistantService.stop(context)
                        }
                    }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetToggleRow(
                    icon = Icons.Rounded.Vibration,
                    badgeColor = Color(0xFF8B5CF6),
                    title = "Tactile Haptics",
                    subtitle = "Subtle haptic response when streams resolve or finish",
                    checked = haptics,
                    onCheckedChange = {
                        haptics = it
                        settings.hapticsEnabled = it
                    }
                )
            }

            // ── Group 2: Media Quality & Storage ─────────────────────────
            InsetSettingsCard(
                header = "MEDIA & STORAGE",
                cardBg = cardBg,
                cardBorder = cardBorder
            ) {
                InsetActionRow(
                    icon = AppIcons.Folder,
                    badgeColor = Color(0xFFF59E0B),
                    title = "Storage Directory",
                    subtitle = "Downloads / $selectedFolderName",
                    actionLabel = "Change",
                    onClick = onChooseFolder
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SquircleBadge(icon = Icons.Rounded.HighQuality, color = Color(0xFFFF2D55))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Preferred Stream Quality",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = c.onSurface
                            )
                            Text(
                                text = stringResource(selectedQuality.descriptionRes),
                                fontSize = 11.5.sp,
                                color = c.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 4-Way Segmented Quality Bar
                    val qualities = listOf(
                        DownloadQuality.BEST to "Best",
                        DownloadQuality.AUTO to "Auto",
                        DownloadQuality.DATA_SAVER to "Saver",
                        DownloadQuality.CUSTOM to "Custom"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(recessedBg)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        qualities.forEach { (q, shortLabel) ->
                            val isSel = selectedQuality == q
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSel) c.primary else Color.Transparent)
                                    .clickable {
                                        selectedQuality = q
                                        settings.quality = q
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortLabel,
                                    color = if (isSel) c.onPrimary else c.onSurfaceVariant,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (selectedQuality == DownloadQuality.CUSTOM) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Target Resolution Ceiling",
                                fontSize = 12.sp,
                                color = c.onSurfaceVariant
                            )
                            Text(
                                text = "${customWidth}p",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.primary
                            )
                        }
                        Slider(
                            value = customWidth.toFloat(),
                            onValueChange = {
                                val snapped = ((it / 60f).roundToInt() * 60).coerceIn(360, 1080)
                                customWidth = snapped
                                settings.customWidth = snapped
                            },
                            valueRange = 360f..1080f,
                            steps = 11
                        )
                    }
                }

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                InsetActionRow(
                    icon = AppIcons.Refresh,
                    badgeColor = Color(0xFF64748B),
                    title = "Purge Stream Cache",
                    subtitle = cacheMessage ?: "Clear temporary extractor cache and finished queue",
                    actionLabel = "Clear",
                    onClick = {
                        StreamCache.invalidateAll()
                        Downloads.clearFinished()
                        cacheMessage = "Cache & completed queue cleared"
                    }
                )
            }

            // ── Group 3: Interface & Theme ───────────────────────────────
            InsetSettingsCard(
                header = "INTERFACE & THEME",
                cardBg = cardBg,
                cardBorder = cardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Appearance Mode",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = c.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(recessedBg)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf("system" to "Auto", "light" to "Light", "dark" to "Dark", "amoled" to "OLED").forEach { (key, label) ->
                            val on = ThemeSettings.mode == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (on) c.primary else Color.Transparent)
                                    .pressable { ThemeSettings.chooseMode(key) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (on) c.onPrimary else c.onSurfaceVariant,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Studio Accent",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = c.onSurface
                    )
                    Spacer(Modifier.height(10.dp))

                    val choices = buildList {
                        if (ThemeSettings.wallpaperAvailable) add(null to "Dynamic")
                        STUDIO_THEMES.forEach { add(it.hex to it.name) }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        choices.forEach { (hex, name) ->
                            val selected = ThemeSettings.seed?.lowercase() == hex?.lowercase()
                            val scheme = schemeFor(context, hex, dark)
                            Column(
                                modifier = Modifier
                                    .width(58.dp)
                                    .pressable { ThemeSettings.choose(hex) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(scheme.primary, scheme.primaryContainer)
                                            )
                                        )
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) c.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = scheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) c.onSurface else c.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                InsetToggleRow(
                    icon = AppIcons.Settings,
                    badgeColor = Color(0xFF0A84FF),
                    title = "System Typography",
                    subtitle = "Use device default typeface across all screens",
                    checked = ThemeSettings.useSystemFont,
                    onCheckedChange = { ThemeSettings.updateUseSystemFont(it) }
                )
            }

            // ── Group 4: Developer & Ecosystem Links ─────────────────────
            InsetSettingsCard(
                header = "DEVELOPER & ECOSYSTEM",
                cardBg = cardBg,
                cardBorder = cardBorder
            ) {
                InsetLinkRow(
                    icon = Icons.Rounded.Person,
                    badgeColor = Color(0xFF0A84FF),
                    title = "Lazy Mayank · XBOREX",
                    subtitle = "@xboyrex · Developer & Maintainer",
                    onClick = { openUrl("https://github.com/xboyrex") }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetLinkRow(
                    icon = Icons.Rounded.Code,
                    badgeColor = Color(0xFF6366F1),
                    title = "InstaTube Source",
                    subtitle = "github.com/xboyrex/InstaTube",
                    onClick = { openUrl("https://github.com/xboyrex/InstaTube") }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetLinkRow(
                    icon = AppIcons.Telegram,
                    badgeColor = Color(0xFF0088CC),
                    title = "Telegram Channel",
                    subtitle = "t.me/RoranoaxZORO",
                    onClick = { openUrl("https://t.me/RoranoaxZORO") }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetLinkRow(
                    icon = AppIcons.Globe,
                    badgeColor = Color(0xFF10B981),
                    title = "Mayank Droid",
                    subtitle = "mayank-droid.pages.dev",
                    onClick = { openUrl("https://mayank-droid.pages.dev") }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetLinkRow(
                    icon = Icons.Rounded.SmartToy,
                    badgeColor = Color(0xFF8B5CF6),
                    title = "Onyx Services",
                    subtitle = "onyx-services.pages.dev",
                    onClick = { openUrl("https://onyx-services.pages.dev") }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetLinkRow(
                    icon = AppIcons.Sparkle,
                    badgeColor = Color(0xFFFF2D55),
                    title = "Otaku Hub Pro",
                    subtitle = "otaku-hub-pro.pages.dev",
                    onClick = { openUrl("https://otaku-hub-pro.pages.dev") }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetLinkRow(
                    icon = Icons.Rounded.WorkOutline,
                    badgeColor = Color(0xFFF59E0B),
                    title = "Portfolio HBO",
                    subtitle = "portfolio-hbo.pages.dev",
                    onClick = { openUrl("https://portfolio-hbo.pages.dev") }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))
                InsetLinkRow(
                    icon = Icons.Rounded.LockOpen,
                    badgeColor = Color(0xFFEC4899),
                    title = "Mi Unlock",
                    subtitle = "mi-unlock.pages.dev",
                    onClick = { openUrl("https://mi-unlock.pages.dev") }
                )
            }

            // ── Group 5: Build & OTA Updates ─────────────────────────────
            InsetSettingsCard(
                header = "BUILD & UPDATES",
                cardBg = cardBg,
                cardBorder = cardBorder
            ) {
                InsetActionRow(
                    icon = Icons.Rounded.SystemUpdate,
                    badgeColor = Color(0xFF10B981),
                    title = "Check for Updates",
                    subtitle = "InstaTube v${BuildConfig.VERSION_NAME} · Android 7.0 – 17",
                    actionLabel = "Check",
                    onClick = onCheckUpdate
                )
            }
        }
    }
}

@Composable
private fun InsetSettingsCard(
    header: String,
    cardBg: Color,
    cardBorder: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = header,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = c.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(20.dp)),
            content = content
        )
    }
}

@Composable
private fun SquircleBadge(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun InsetToggleRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquircleBadge(icon = icon, color = badgeColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface)
            Text(subtitle, fontSize = 11.5.sp, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary)
        )
    }
}

@Composable
private fun InsetActionRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquircleBadge(icon = icon, color = badgeColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface)
            Text(subtitle, fontSize = 11.5.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(c.primary.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = actionLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = c.primary
            )
        }
    }
}

@Composable
private fun InsetLinkRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquircleBadge(icon = icon, color = badgeColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface)
            Text(subtitle, fontSize = 11.5.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = AppIcons.ExternalLink,
            contentDescription = "Open",
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
