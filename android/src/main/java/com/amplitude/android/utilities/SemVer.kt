package com.amplitude.android.utilities

internal data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    companion object {
        fun create(versionString: String): SemVer? {
            val numbers =
                versionString
                    .removePrefix("v")
                    .substringBefore("-")
                    .split(".")
                    .map { it.toIntOrNull() ?: return null }
            if (numbers.isEmpty()) return null
            return SemVer(
                major = numbers.getOrElse(0) { 0 },
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
            )
        }
    }
}
