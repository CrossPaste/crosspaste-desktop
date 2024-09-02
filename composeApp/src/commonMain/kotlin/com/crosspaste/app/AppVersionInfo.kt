package com.crosspaste.app

import io.github.z4kn4fein.semver.Version
import kotlin.math.max
import kotlin.math.min

fun createAppVersionInfo(
    version: String,
    config: String,
): AppVersionInfo {
    val splits = config.split(",")
    check(splits.size == 2)
    return AppVersionInfo(
        version = version,
        hasStorageChanges = splits[0].toBoolean(),
        hasApiCompatibilityChanges = splits[1].toBoolean(),
    )
}

data class AppVersionInfo(
    val version: String,
    val hasStorageChanges: Boolean,
    val hasApiCompatibilityChanges: Boolean,
)

class VersionCompatibilityChecker(private val versionInfos: Map<String, AppVersionInfo>) {
    private val sortedVersions: List<String>
    private val versionIndexMap: Map<String, Int>
    private val apiCompatibilityChanges: List<Boolean>

    init {
        sortedVersions = versionInfos.keys.sortedBy { Version.parse(it) }
        versionIndexMap = sortedVersions.withIndex().associate { it.value to it.index }
        apiCompatibilityChanges = sortedVersions.map { versionInfos[it]?.hasApiCompatibilityChanges ?: false }
    }

    fun hasApiCompatibilityChangesBetween(
        version1: String,
        version2: String,
    ): Boolean {
        if (version1 == version2) {
            return false
        }
        val semverVersion1: Version = Version.parse(version1)
        val semverVersion2: Version = Version.parse(version2)

        val (fromVersion, toVersion) =
            if (semverVersion1 >= semverVersion2) {
                getVersionString(semverVersion2) to getVersionString(semverVersion1)
            } else {
                getVersionString(semverVersion1) to getVersionString(semverVersion2)
            }

        val fromIndex = versionIndexMap[fromVersion] ?: return false
        val toIndex = versionIndexMap[toVersion] ?: return false

        val startIndex = min(fromIndex + 1, toIndex)
        val endIndex = max(fromIndex + 1, toIndex)

        for (i in startIndex..endIndex) {
            if (apiCompatibilityChanges[i]) {
                return true
            }
        }
        return false
    }

    private fun getVersionString(version: Version): String {
        return "${version.major}.${version.minor}.${version.patch}"
    }
}
