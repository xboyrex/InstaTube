package com.roshan.instatube

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAssistantPolicyTest {
    @Test
    fun shortcutAppearsWhileInstagramIsForegroundEvenIfClipboardListenerDidNotFire() {
        assertTrue(LinkAssistantPolicy.shouldShowShortcut(true, "com.instagram.android"))
        assertTrue(LinkAssistantPolicy.shouldShowShortcut(true, "com.instagram.lite"))
        assertFalse(LinkAssistantPolicy.shouldShowShortcut(false, "com.instagram.android"))
        assertFalse(LinkAssistantPolicy.shouldShowShortcut(true, "com.google.android.youtube"))
        assertFalse(LinkAssistantPolicy.shouldShowShortcut(true, null))
    }

    @Test
    fun clipboardStateOnlyChangesTheAssistantHint() {
        assertEquals("Clipboard changed · tap to check", LinkAssistantPolicy.subtitleForClipboardState(true))
        assertEquals("Copy a link, then tap to check", LinkAssistantPolicy.subtitleForClipboardState(false))
    }

    @Test
    fun vividAccentUsesDarkTextWhenWhiteTextWouldBeLowContrast() {
        assertEquals(Color(0xFF160A16), readableContentColor(Color(0xFFFF2D55)))
        assertEquals(Color.White, readableContentColor(Color(0xFF0B0D14)))
    }

    @Test
    fun accessibleTextColorMeetsWcagAaForLightThemeAccentSamples() {
        listOf(Color(0xFFC87A18), Color(0xFFFF2D55), Color(0xFF127C62)).forEach { background ->
            assertTrue(contrastRatio(background, readableContentColor(background)) >= 4.5)
        }
    }

    private fun contrastRatio(background: Color, foreground: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        fun luminance(color: Color): Double =
            0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        val a = luminance(background)
        val b = luminance(foreground)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
