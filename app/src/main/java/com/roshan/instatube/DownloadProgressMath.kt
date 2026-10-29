package com.roshan.instatube

import java.math.BigInteger

/** Deterministic byte aggregation used by the progress notification and unit tests. */
internal data class DownloadProgressSnapshot(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isDeterminate: Boolean,
    val percent: Int,
)

internal object DownloadProgressMath {
    private val HUNDRED = BigInteger.valueOf(100L)

    /** Each pair is (downloadedBytes, totalBytes); non-positive totals mean unknown size. */
    fun calculate(items: List<Pair<Long, Long>>): DownloadProgressSnapshot {
        val determinate = items.isNotEmpty() && items.all { it.second > 0L }
        val safeTotals = items.map { (_, total) -> total.coerceAtLeast(0L) }
        val safeDownloads = items.map { (downloaded, total) ->
            val nonNegative = downloaded.coerceAtLeast(0L)
            if (total > 0L) nonNegative.coerceAtMost(total) else nonNegative
        }
        // Snapshot fields are Long, so saturate aggregate counters rather than allowing wraparound.
        val totalBytes = saturatedSum(safeTotals)
        val downloadedBytes = saturatedSum(safeDownloads)

        // Use exact integer arithmetic for the ratio: aggregate sums and downloaded * 100 can
        // overflow Long even when every individual file counter is valid.
        val percent = if (determinate) {
            val exactTotal = items.fold(BigInteger.ZERO) { sum, item ->
                sum.add(BigInteger.valueOf(item.second))
            }
            val exactDownloaded = items.fold(BigInteger.ZERO) { sum, item ->
                val downloaded = item.first.coerceAtLeast(0L).coerceAtMost(item.second)
                sum.add(BigInteger.valueOf(downloaded))
            }
            if (exactTotal.signum() <= 0) 0 else {
                exactDownloaded.multiply(HUNDRED)
                    .divide(exactTotal)
                    .toInt()
                    .coerceIn(0, 100)
            }
        } else {
            0
        }

        return DownloadProgressSnapshot(downloadedBytes, totalBytes, determinate, percent)
    }

    private fun saturatedSum(values: List<Long>): Long = values.fold(0L) { sum, value ->
        if (Long.MAX_VALUE - sum < value) Long.MAX_VALUE else sum + value
    }
}
