package io.github.osamaliaqat.nodedependencyupdater.model

import java.time.Instant

data class VersionInfo(val version: String, val releaseDate: Instant?)

data class OutdatedDependency(
    val name: String,
    val current: String,
    val wanted: String,
    val latest: String,
    val workspace: String?,
    val isDev: Boolean,
    val isPeer: Boolean,
    val versionPrefix: String,
    val currentReleaseDate: Instant? = null,
    val latestReleaseDate: Instant? = null,
    /** All stable versions newer than [current] (and optionally older than min-age cutoff), sorted descending. */
    val availableVersions: List<VersionInfo> = emptyList(),
    /** The version the user picked from the dropdown; defaults to [latest]. */
    var selectedVersion: String = latest,
    /** False until registry metadata (dates, intermediate versions) has been fetched for this dep. */
    val enriched: Boolean = false,
    var ignored: Boolean = false,
)
