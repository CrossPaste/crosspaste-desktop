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
    private val sortedVersions: List<String> = versionInfos.keys.sortedBy { Version.parse(it) }
    private val versionIndexMap: Map<String, Int> =
        sortedVersions.withIndex()
            .associate { it.value to it.index }
    private val apiCompatibilityChanges: List<Boolean> =
        sortedVersions.map {
            versionInfos[it]?.hasApiCompatibilityChanges == true
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

        if (fromVersion == toVersion) {
            return false
        }

        val fromIndex = versionIndexMap[fromVersion] ?: return false
        val toIndex = versionIndexMap[toVersion] ?: return false

        val startIndex = min(min(fromIndex + 1, versionIndexMap.size - 1), toIndex)
        val endIndex = max(min(fromIndex + 1, versionIndexMap.size - 1), toIndex)

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
