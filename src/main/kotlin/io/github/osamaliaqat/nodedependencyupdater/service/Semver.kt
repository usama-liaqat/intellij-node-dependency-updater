package io.github.osamaliaqat.nodedependencyupdater.service

/** Tiny semver helper: parse MAJOR.MINOR.PATCH, ignore prereleases (versions containing '-'). */
object Semver {
    data class Parsed(val major: Int, val minor: Int, val patch: Int) : Comparable<Parsed> {
        override fun compareTo(other: Parsed): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }
    }

    fun parse(v: String): Parsed? {
        val core = v.removePrefix("v").substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.size < 3) return null
        return try {
            Parsed(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun isStable(v: String): Boolean = !v.contains('-')

    fun isGreater(a: String, b: String): Boolean {
        val pa = parse(a) ?: return false
        val pb = parse(b) ?: return false
        return pa > pb
    }
}
