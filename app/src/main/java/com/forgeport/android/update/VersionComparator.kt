package com.forgeport.android.update

object VersionComparator {
    fun compare(left: String, right: String): Int {
        val a = ParsedVersion.parse(left)
        val b = ParsedVersion.parse(right)
        val max = maxOf(a.numbers.size, b.numbers.size)
        repeat(max) { index ->
            val av = a.numbers.getOrElse(index) { 0 }
            val bv = b.numbers.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return comparePreRelease(a.preRelease, b.preRelease)
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun comparePreRelease(left: List<String>, right: List<String>): Int {
        if (left.isEmpty() && right.isEmpty()) return 0
        if (left.isEmpty()) return 1
        if (right.isEmpty()) return -1

        val max = maxOf(left.size, right.size)
        repeat(max) { index ->
            val a = left.getOrNull(index) ?: return -1
            val b = right.getOrNull(index) ?: return 1
            val an = a.toIntOrNull()
            val bn = b.toIntOrNull()
            val result = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> qualifierRank(a).compareTo(qualifierRank(b)).takeIf { it != 0 }
                    ?: a.compareTo(b, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return 0
    }

    private fun qualifierRank(value: String): Int = when (value.lowercase()) {
        "dev", "snapshot" -> 0
        "alpha", "a" -> 1
        "beta", "b" -> 2
        "rc" -> 3
        else -> 4
    }

    private data class ParsedVersion(val numbers: List<Int>, val preRelease: List<String>) {
        companion object {
            fun parse(raw: String): ParsedVersion {
                val clean = raw.trim().removePrefix("v").removePrefix("V")
                val withoutBuild = clean.substringBefore('+')
                val main = withoutBuild.substringBefore('-')
                val suffix = withoutBuild.substringAfter('-', missingDelimiterValue = "")
                val numbers = main.split('.').map { token -> token.filter(Char::isDigit).toIntOrNull() ?: 0 }
                val pre = if (suffix.isBlank()) {
                    emptyList()
                } else {
                    Regex("[.-]").split(suffix).filter { it.isNotBlank() }.flatMap { token ->
                        val match = Regex("^([A-Za-z]+)(\\d+)$").matchEntire(token)
                        if (match != null) listOf(match.groupValues[1], match.groupValues[2]) else listOf(token)
                    }
                }
                return ParsedVersion(numbers, pre)
            }
        }
    }
}
