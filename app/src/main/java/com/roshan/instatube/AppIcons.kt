package com.roshan.instatube

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppIcons {
    // ── Navigation Bar Custom SVG Vectors ───────────────────────────
    val NavDownloaderOutline: ImageVector by lazy {
        svgIcon(
            "NavDownloaderOutline",
            "M12,3c0.55,0 1,0.45 1,1v8.17l2.59,-2.58c0.39,-0.39 1.02,-0.39 1.41,0c0.39,0.39 0.39,1.02 0,1.41l-4.3,4.3c-0.39,0.39 -1.02,0.39 -1.41,0l-4.3,-4.3c-0.39,-0.39 -0.39,-1.02 0,-1.41c0.39,-0.39 1.02,-0.39 1.41,0L11,12.17V4C11,3.45 11.45,3 12,3z" +
                "M5,16c0.55,0 1,0.45 1,1v1c0,0.55 0.45,1 1,1h10c0.55,0 1,-0.45 1,-1v-1c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v1c0,1.66 -1.34,3 -3,3H7c-1.66,0 -3,-1.34 -3,-3v-1C4,16.45 4.45,16 5,16z"
        )
    }

    val NavDownloaderFilled: ImageVector by lazy {
        svgIcon(
            "NavDownloaderFilled",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10s10,-4.48 10,-10S17.52,2 12,2zM11,7c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v4.59l1.79,-1.79c0.39,-0.39 1.02,-0.39 1.41,0c0.39,0.39 0.39,1.02 0,1.41l-3.5,3.5c-0.39,0.39 -1.02,0.39 -1.41,0l-3.5,-3.5c-0.39,-0.39 -0.39,-1.02 0,-1.41c0.39,-0.39 1.02,-0.39 1.41,0L11,11.59V7zM16,17H8c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h8c0.55,0 1,0.45 1,1S16.55,17 16,17z"
        )
    }

    val NavVaultOutline: ImageVector by lazy {
        svgIcon(
            "NavVaultOutline",
            "M18,4H6C4.34,4 3,5.34 3,7v10c0,1.66 1.34,3 3,3h12c1.66,0 3,-1.34 3,-3V7C21,5.34 19.66,4 18,4zM6,6h12c0.55,0 1,0.45 1,1v10c0,0.55 -0.45,1 -1,1H6c-0.55,0 -1,-0.45 -1,-1V7C5,6.45 5.45,6 6,6zM10.5,9.2v5.6c0,0.41 0.47,0.65 0.8,0.4l3.8,-2.8c0.27,-0.2 0.27,-0.6 0,-0.8l-3.8,-2.8C10.97,8.55 10.5,8.79 10.5,9.2z"
        )
    }

    val NavVaultFilled: ImageVector by lazy {
        svgIcon(
            "NavVaultFilled",
            "M18,4H6C4.34,4 3,5.34 3,7v10c0,1.66 1.34,3 3,3h12c1.66,0 3,-1.34 3,-3V7C21,5.34 19.66,4 18,4zM10.2,14.8V9.2c0,-0.45 0.52,-0.71 0.88,-0.44l3.8,2.8c0.3,0.22 0.3,0.66 0,0.88l-3.8,2.8C10.72,15.51 10.2,15.25 10.2,14.8z"
        )
    }

    val NavTuneOutline: ImageVector by lazy {
        svgIcon(
            "NavTuneOutline",
            "M4,7c0,-0.55 0.45,-1 1,-1h4.18C9.6,4.84 10.7,4 12,4s2.4,0.84 2.82,2H19c0.55,0 1,0.45 1,1s-0.45,1 -1,1h-4.18C14.4,9.16 13.3,10 12,10s-2.4,-0.84 -2.82,-2H5C4.45,8 4,7.55 4,7zM12,8c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1s-1,0.45 -1,1S11.45,8 12,8z" +
                "M4,17c0,-0.55 0.45,-1 1,-1h8.18c0.42,-1.16 1.52,-2 2.82,-2s2.4,0.84 2.82,2H19c0.55,0 1,0.45 1,1s-0.45,1 -1,1h-0.18c-0.42,1.16 -1.52,2 -2.82,2s-2.4,-0.84 -2.82,-2H5C4.45,18 4,17.55 4,17zM16,18c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1s-1,0.45 -1,1S15.45,18 16,18z"
        )
    }

    val NavTuneFilled: ImageVector by lazy {
        svgIcon(
            "NavTuneFilled",
            "M3,7c0,-0.55 0.45,-1 1,-1h5.18C9.6,4.84 10.7,4 12,4s2.4,0.84 2.82,2H20c0.55,0 1,0.45 1,1s-0.45,1 -1,1h-5.18C14.4,9.16 13.3,10 12,10s-2.4,-0.84 -2.82,-2H4C3.45,8 3,7.55 3,7z" +
                "M3,17c0,-0.55 0.45,-1 1,-1h9.18c0.42,-1.16 1.52,-2 2.82,-2s2.4,0.84 2.82,2H20c0.55,0 1,0.45 1,1s-0.45,1 -1,1h-1.18c-0.42,1.16 -1.52,2 -2.82,2s-2.4,-0.84 -2.82,-2H4C3.45,18 3,17.55 3,17z"
        )
    }

    // ── Core Action & Studio SVG Vectors ────────────────────────────
    val Download: ImageVector by lazy { NavDownloaderOutline }

    val ContentCopy: ImageVector by lazy {
        svgIcon(
            "ContentCopy",
            "M15,3H6C4.34,3 3,4.34 3,6v10c0,0.55 0.45,1 1,1s1,-0.45 1,-1V6c0,-0.55 0.45,-1 1,-1h9c0.55,0 1,-0.45 1,-1S15.55,3 15,3z" +
                "M18,7H9C7.34,7 6,8.34 6,10v9c0,1.66 1.34,3 3,3h9c1.66,0 3,-1.34 3,-3v-9C21,8.34 19.66,7 18,7zM19,19c0,0.55 -0.45,1 -1,1H9c-0.55,0 -1,-0.45 -1,-1v-9c0,-0.55 0.45,-1 1,-1h9c0.55,0 1,0.45 1,1V19z"
        )
    }

    val ContentPaste: ImageVector by lazy {
        svgIcon(
            "ContentPaste",
            "M17,4h-2.18C14.4,2.84 13.3,2 12,2S9.6,2.84 9.18,4H7C5.34,4 4,5.34 4,7v12c0,1.66 1.34,3 3,3h10c1.66,0 3,-1.34 3,-3V7C20,5.34 18.66,4 17,4zM12,4c0.55,0 1,0.45 1,1s-0.45,1 -1,1s-1,-0.45 -1,-1S11.45,4 12,4zM18,19c0,0.55 -0.45,1 -1,1H7c-0.55,0 -1,-0.45 -1,-1V7c0,-0.55 0.45,-1 1,-1h1v1c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1V6h1c0.55,0 1,0.45 1,1V19z"
        )
    }

    val Close: ImageVector by lazy {
        svgIcon(
            "Close",
            "M18.3,5.71c-0.39,-0.39 -1.02,-0.39 -1.41,0L12,10.59 7.11,5.7c-0.39,-0.39 -1.02,-0.39 -1.41,0c-0.39,0.39 -0.39,1.02 0,1.41L10.59,12 5.7,16.89c-0.39,0.39 -0.39,1.02 0,1.41c0.39,0.39 1.02,0.39 1.41,0L12,13.41l4.89,4.89c0.39,0.39 1.02,0.39 1.41,0c0.39,-0.39 0.39,-1.02 0,-1.41L13.41,12l4.89,-4.89C18.68,6.73 18.68,6.09 18.3,5.71z"
        )
    }

    val Image: ImageVector by lazy {
        svgIcon(
            "Image",
            "M19,4H5C3.34,4 2,5.34 2,7v10c0,1.66 1.34,3 3,3h14c1.66,0 3,-1.34 3,-3V7C22,5.34 20.66,4 19,4zM5,6h14c0.55,0 1,0.45 1,1v6.59l-3.29,-3.3c-0.39,-0.39 -1.02,-0.39 -1.41,0L11,14.59l-1.79,-1.8c-0.39,-0.39 -1.02,-0.39 -1.41,0L4,16.59V7C4,6.45 4.45,6 5,6zM8.5,11C9.33,11 10,10.33 10,9.5S9.33,8 8.5,8S7,8.67 7,9.5S7.67,11 8.5,11z"
        )
    }

    val Movie: ImageVector by lazy {
        svgIcon(
            "Movie",
            "M18,4H6C4.34,4 3,5.34 3,7v10c0,1.66 1.34,3 3,3h12c1.66,0 3,-1.34 3,-3V7C21,5.34 19.66,4 18,4zM10,15V9l5,3L10,15z"
        )
    }

    val MusicNote: ImageVector by lazy {
        svgIcon(
            "MusicNote",
            "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55c-2.21,0 -4,1.79 -4,4s1.79,4 4,4s4,-1.79 4,-4V7h3c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2H12z"
        )
    }

    val AudioWave: ImageVector by lazy {
        svgIcon(
            "AudioWave",
            "M7,9c0.55,0 1,0.45 1,1v4c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1v-4C6,9.45 6.45,9 7,9z" +
                "M11,5c0.55,0 1,0.45 1,1v12c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V6C10,5.45 10.45,5 11,5z" +
                "M15,7c0.55,0 1,0.45 1,1v8c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V8C14,7.45 14.45,7 15,7z" +
                "M19,10c0.55,0 1,0.45 1,1v2c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1v-2C18,10.45 18.45,10 19,10z" +
                "M3,10c0.55,0 1,0.45 1,1v2c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1v-2C2,10.45 2.45,10 3,10z"
        )
    }

    val Sparkle: ImageVector by lazy {
        svgIcon(
            "Sparkle",
            "M12,2L14.4,8.6L21,11L14.4,13.4L12,20L9.6,13.4L3,11L9.6,8.6L12,2zM19,15l1.1,2.9L23,19l-2.9,1.1L19,23l-1.1,-2.9L15,19l2.9,-1.1L19,15z"
        )
    }

    val PlayArrow: ImageVector by lazy {
        svgIcon("PlayArrow", "M8,6.82v10.36c0,0.79 0.87,1.27 1.54,0.84l8.14,-5.18c0.62,-0.39 0.62,-1.29 0,-1.69L9.54,5.98C8.87,5.55 8,6.03 8,6.82z")
    }

    val Pause: ImageVector by lazy {
        svgIcon("Pause", "M8,19c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2S6,5.9 6,7v10C6,18.1 6.9,19 8,19zM16,5c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2s2,-0.9 2,-2V7C18,5.9 17.1,5 16,5z")
    }

    val Refresh: ImageVector by lazy {
        svgIcon(
            "Refresh",
            "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
        )
    }

    val Folder: ImageVector by lazy {
        svgIcon(
            "Folder",
            "M10.59,4.59C10.21,4.21 9.7,4 9.17,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8L10.59,4.59z"
        )
    }

    val Share: ImageVector by lazy {
        svgIcon(
            "Share",
            "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81c1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3s-3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65c0,1.61 1.31,2.92 2.92,2.92s2.92,-1.31 2.92,-2.92S19.61,16.08 18,16.08z"
        )
    }

    val Trash: ImageVector by lazy {
        svgIcon(
            "Trash",
            "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6V19zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"
        )
    }

    val ExternalLink: ImageVector by lazy {
        svgIcon(
            "ExternalLink",
            "M19,19H5V5h7V3H5C3.89,3 3,3.9 3,5v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2V19zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3H14z"
        )
    }

    val Telegram: ImageVector by lazy {
        svgIcon(
            "Telegram",
            "M20.66,3.72L3.32,10.41c-1.18,0.47 -1.17,1.13 -0.22,1.42l4.45,1.39 10.3,-6.5c0.49,-0.3 0.93,-0.14 0.57,0.19l-8.35,7.53 -0.31,4.62c0.45,0 0.65,-0.21 0.9,-0.45l2.16,-2.1 4.49,3.32c0.83,0.46 1.42,0.22 1.63,-0.77l2.95,-13.89C22.19,3.99 21.43,3.38 20.66,3.72z"
        )
    }

    val Globe: ImageVector by lazy {
        svgIcon(
            "Globe",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10s10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93c0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2V19.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1H8v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41C20,14.08 19.2,15.97 17.9,17.39z"
        )
    }

    val Fullscreen: ImageVector by lazy {
        svgIcon(
            "Fullscreen",
            "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z"
        )
    }

    val FullscreenExit: ImageVector by lazy {
        svgIcon(
            "FullscreenExit",
            "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,5h-2v5h5V8h-3V5z"
        )
    }

    val Settings: ImageVector by lazy { NavTuneOutline }
    val VideoLibrary: ImageVector by lazy { NavVaultOutline }
}

private fun svgIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

/** Animated presentation of the exact IconKitchen launcher asset used by Android. */
@Composable
fun StudioBrandMark(
    size: Dp = 34.dp,
    isBusy: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "brandPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBusy) 750 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Image(
        painter = painterResource(R.drawable.instatube_brand_logo),
        contentDescription = "InstaTube app icon",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                if (isBusy) {
                    scaleX = pulse
                    scaleY = pulse
                }
            }
            .clip(RoundedCornerShape(percent = 24)),
    )
}

/**
 * Animated tab vector icon that scales and tilts smoothly when selected.
 */
@Composable
fun AnimatedTabVectorIcon(
    icon: ImageVector,
    contentDescription: String?,
    selected: Boolean,
    tint: Color,
    size: Dp = 22.dp,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 0.96f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "tabScale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (selected) 0f else -4f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "tabRot"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotation
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
