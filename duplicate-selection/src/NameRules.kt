package org.yonicomponents.duplicateselection

import java.util.Locale

/** Rules deliberately describe copy markers, not arbitrary similar-looking names. */
object NameRules {
    private val prefix = Regex("^(?:copy of |עותק של )", RegexOption.IGNORE_CASE)
    private val parenthesized = Regex(" *\\([0-9]{1,3}\\)$")
    private val bracketed = Regex(" +\\[[0-9]{1,3}\\]$")
    // Consume the separator run only when it belongs to an actual copy keyword.
    // In particular, "report - Copy" becomes "report", but "report -" is unchanged.
    private val keyword = Regex(
        "[ _-]+(?:copy|עותק|kopie|copie|kopia)(?:[ _-]*(?:[0-9]+|\\([0-9]+\\)))?$",
        RegexOption.IGNORE_CASE,
    )
    private val weak = Regex("[ _~\\-][0-9]$")

    fun normalize(fileName: String): NormalizedName {
        val dot = fileName.lastIndexOf('.')
        val hasExtension = dot > 0 && dot < fileName.lastIndex
        var stem = if (hasExtension) fileName.substring(0, dot) else fileName
        val extension = if (hasExtension) fileName.substring(dot + 1) else ""
        var hadStrong = false
        var hadWeak = false

        fun stripStrong() {
            while (true) {
                val next = removeStrong(stem) ?: break
                if (next == stem) break
                stem = next
                hadStrong = true
            }
        }

        stripStrong()
        weak.find(stem)?.let {
            stem = stem.substring(0, it.range.first)
            hadWeak = true
        }
        // There is intentionally no second weak pass, even if this exposes one.
        stripStrong()
        val marker = when {
            hadStrong -> Marker.STRONG
            hadWeak -> Marker.WEAK
            else -> Marker.NONE
        }
        return NormalizedName(stem.lowercase(Locale.ROOT), extension.lowercase(Locale.ROOT), marker)
    }

    private fun removeStrong(stem: String): String? {
        prefix.find(stem)?.let { return stem.substring(it.range.last + 1) }
        for (pattern in listOf(keyword, parenthesized, bracketed)) {
            pattern.find(stem)?.let { return stem.substring(0, it.range.first) }
        }
        return null
    }
}
