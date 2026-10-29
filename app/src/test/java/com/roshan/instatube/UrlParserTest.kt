package com.roshan.instatube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlParserTest {
    @Test
    fun detectsInstagramAndYouTubeUrls() {
        val ig = InstagramDownloader.extractCleanUrl("Check this https://www.instagram.com/reel/DfAEYMLuN6n/?igsh=abc")
        assertTrue(InstagramDownloader.isInstagramUrl(ig))
        val yt = InstagramDownloader.extractCleanUrl("https://youtube.com/shorts/s9dUjaDCniA?si=test")
        assertTrue(InstagramDownloader.isYouTubeUrl(yt))
        assertEquals("s9dUjaDCniA", InstagramDownloader.extractYouTubeId(yt))
    }
}
