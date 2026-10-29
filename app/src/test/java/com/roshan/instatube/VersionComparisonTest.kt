package com.roshan.instatube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test
    fun offersUpdateOnlyWhenRemoteStableVersionIsNewer() {
        assertTrue(isNewerVersion("v3.0.1", "3.0.0"))
        assertTrue(isNewerVersion("3.1.0", "3.0.9"))
        assertTrue(isNewerVersion("4.0.0", "3.99.99"))
        assertFalse(isNewerVersion("3.0.0", "3.0.0"))
        assertFalse(isNewerVersion("2.9.9", "3.0.0"))
    }

    @Test
    fun normalizesVersionPrefixesMissingZeroesAndBuildMetadata() {
        assertFalse(isNewerVersion("v3.0", "3.0.0"))
        assertFalse(isNewerVersion("3.0.0+build.42", "3.0.0"))
        assertTrue(isNewerVersion("V3.0.2", "v3.0.1"))
    }

    @Test
    fun stableReleaseOutranksPrereleaseAndPrereleasesUseSemverOrdering() {
        assertTrue(isNewerVersion("3.0.0", "3.0.0-rc.2"))
        assertFalse(isNewerVersion("3.0.0-beta.1", "3.0.0"))
        assertTrue(isNewerVersion("3.0.0-rc.2", "3.0.0-rc.1"))
        assertTrue(isNewerVersion("3.0.0-rc.10", "3.0.0-rc.2"))
    }

    @Test
    fun malformedVersionsNeverTriggerUpdatePrompts() {
        assertFalse(isNewerVersion("latest", "3.0.0"))
        assertFalse(isNewerVersion("3.x.1", "3.0.0"))
        assertEquals(null, compareAppVersions("3.0.0", "current"))
    }

    @Test
    fun cleansReleaseMarkdownForDisplay() {
        val notes = cleanReleaseNotes("## What's new\n\n**Fixes**: queue and UI\n\n**Full Changelog**: https://github.com/xboyrex/InstaTube/commits/v3.0.1")
        assertTrue(notes.contains("What's new"))
        assertTrue(notes.contains("Fixes: queue and UI"))
        assertFalse(notes.contains("**"))
        assertFalse(notes.contains("Full Changelog"))
    }
}