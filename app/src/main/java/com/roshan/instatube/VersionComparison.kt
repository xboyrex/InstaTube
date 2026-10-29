package com.roshan.instatube

import java.math.BigInteger

/**
 * Semantic version comparison for GitHub release tags and the installed app version.
 *
 * Accepts an optional leading v/V, missing trailing zero components, prerelease labels,
 * and build metadata. Invalid versions never trigger an update prompt.
 */
fun isNewerVersion(remote: String, current: String): Boolean =
    compareAppVersions(remote, current)?.let { it > 0 } ?: false

internal fun compareAppVersions(first: String, second: String): Int? {
    val a = parseVersion(first) ?: return null
    val b = parseVersion(second) ?: return null

    for (index in 0 until maxOf(a.core.size, b.core.size)) {
        val left = a.core.getOrElse(index) { BigInteger.ZERO }
        val right = b.core.getOrElse(index) { BigInteger.ZERO }
        val comparison = left.compareTo(right)
        if (comparison != 0) return comparison
    }

    val aPre = a.prerelease
    val bPre = b.prerelease
    if (aPre == null && bPre == null) return 0
    if (aPre == null) return 1 // a stable release outranks its prereleases
    if (bPre == null) return -1

    for (index in 0 until minOf(aPre.size, bPre.size)) {
        val left = aPre[index]
        val right = bPre[index]
        val leftNumeric = left.all(Char::isDigit)
        val rightNumeric = right.all(Char::isDigit)
        val comparison = when {
            leftNumeric && rightNumeric -> left.toBigInteger().compareTo(right.toBigInteger())
            leftNumeric -> -1
            rightNumeric -> 1
            else -> left.compareTo(right)
        }
        if (comparison != 0) return comparison
    }
    return aPre.size.compareTo(bPre.size)
}

private data class ParsedVersion(
    val core: List<BigInteger>,
    val prerelease: List<String>?,
)

private fun parseVersion(raw: String): ParsedVersion? {
    var value = raw.trim().removePrefix("v").removePrefix("V")
    if (value.isBlank()) return null
    value = value.substringBefore('+') // build metadata does not affect precedence
    val split = value.split('-', limit = 2)
    val coreParts = split[0].split('.')
    if (coreParts.isEmpty() || coreParts.any { it.isEmpty() || it.any { ch -> !ch.isDigit() } }) {
        return null
    }
    val prerelease = if (split.size == 1) null else {
        split[1].split('.').takeIf { parts ->
            parts.isNotEmpty() && parts.all { part ->
                part.isNotEmpty() && part.all { ch -> ch.isLetterOrDigit() || ch == '-' }
            }
        } ?: return null
    }
    return runCatching {
        ParsedVersion(coreParts.map { it.toBigInteger() }, prerelease)
    }.getOrNull()
}

/** Removes common GitHub Markdown syntax so release notes read cleanly in the app dialog. */
internal fun cleanReleaseNotes(markdown: String): String {
    return markdown
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        .replace(Regex("\\[([^\\]]+)]\\((https?://[^)]+)\\)")) { it.groupValues[1] }
        .replace(Regex("[*_`]{1,3}"), "")
        .replace(Regex("(?m)^\\s*(?:full\\s+)?changelog\\s*:?\\s*https?://\\S+\\s*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(?m)^\\s*https?://\\S+\\s*$"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}