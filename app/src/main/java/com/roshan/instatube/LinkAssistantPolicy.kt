package com.roshan.instatube

/** Deterministic policy for the optional floating link shortcut. */
internal object LinkAssistantPolicy {
    const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"
    const val INSTAGRAM_LITE_PACKAGE_NAME = "com.instagram.lite"

    fun isInstagramPackage(packageName: String?): Boolean =
        packageName == INSTAGRAM_PACKAGE_NAME || packageName == INSTAGRAM_LITE_PACKAGE_NAME

    /**
     * Once explicitly enabled and permissions are granted, keep the shortcut visible while an
     * Instagram app is foreground. Do not gate visibility on clipboard callbacks: some OEMs limit
     * background clipboard listeners, which previously made the feature appear broken.
     */
    fun shouldShowShortcut(enabled: Boolean, foregroundPackage: String?): Boolean =
        enabled && isInstagramPackage(foregroundPackage)

    fun subtitleForClipboardState(recentClipboardChange: Boolean): String =
        if (recentClipboardChange) "Clipboard changed · tap to check"
        else "Copy a link, then tap to check"
}
