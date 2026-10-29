package com.roshan.instatube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressMathTest {
    @Test
    fun computesWeightedPercentageFromKnownSizes() {
        val result = DownloadProgressMath.calculate(listOf(40L to 100L, 10L to 100L))
        assertEquals(50L, result.downloadedBytes)
        assertEquals(200L, result.totalBytes)
        assertEquals(25, result.percent)
        assertTrue(result.isDeterminate)
    }

    @Test
    fun staysIndeterminateWhileAnyActiveItemHasUnknownSize() {
        val result = DownloadProgressMath.calculate(listOf(30L to 100L, 0L to -1L))
        assertEquals(0, result.percent)
        assertFalse(result.isDeterminate)
    }

    @Test
    fun clampsInvalidAndOvershootingCounters() {
        val result = DownloadProgressMath.calculate(listOf(-5L to 100L, 200L to 100L))
        assertEquals(100L, result.downloadedBytes)
        // Only one of the two 100-byte items is complete: aggregate progress is 100 / 200 = 50%.
        assertEquals(50, result.percent)
        assertTrue(result.isDeterminate)
    }

    @Test
    fun emptyQueueIsNotDeterminate() {
        val result = DownloadProgressMath.calculate(emptyList())
        assertFalse(result.isDeterminate)
        assertEquals(0, result.percent)
    }

    @Test
    fun zeroTotalRemainsIndeterminateAndNeverDividesByZero() {
        val result = DownloadProgressMath.calculate(listOf(0L to 0L))
        assertEquals(0L, result.downloadedBytes)
        assertEquals(0L, result.totalBytes)
        assertEquals(0, result.percent)
        assertFalse(result.isDeterminate)
    }

    @Test
    fun negativeTotalsAreExcludedFromAggregateTotal() {
        val result = DownloadProgressMath.calculate(listOf(5L to -7L))
        assertEquals(5L, result.downloadedBytes)
        assertEquals(0L, result.totalBytes)
        assertEquals(0, result.percent)
        assertFalse(result.isDeterminate)
    }

    @Test
    fun aggregateCountersSaturateInsteadOfOverflowing() {
        val result = DownloadProgressMath.calculate(
            listOf(Long.MAX_VALUE to Long.MAX_VALUE, Long.MAX_VALUE to Long.MAX_VALUE)
        )
        assertEquals(Long.MAX_VALUE, result.downloadedBytes)
        assertEquals(Long.MAX_VALUE, result.totalBytes)
        assertEquals(100, result.percent)
        assertTrue(result.isDeterminate)
    }

    @Test
    fun exactPercentageDoesNotOverflowForLargeAggregatedDownloads() {
        val total = 6_000_000_000_000_000_000L
        val downloaded = 3_000_000_000_000_000_000L
        val result = DownloadProgressMath.calculate(listOf(downloaded to total, downloaded to total))
        assertEquals(50, result.percent)
        assertTrue(result.isDeterminate)
    }
}
