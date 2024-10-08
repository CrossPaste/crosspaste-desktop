package com.crosspaste.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCompatibilityCheckerTest {

    private val versionInfos =
        mapOf(
            "0.0.9" to
                AppVersionInfo(
                    "0.0.9",
                    hasStorageChanges = false,
                    hasApiCompatibilityChanges = false,
                ),
            "1.0.0" to
                AppVersionInfo(
                    "1.0.0",
                    hasStorageChanges = false,
                    hasApiCompatibilityChanges = false,
                ),
            "1.1.0" to
                AppVersionInfo(
                    "1.1.0",
                    hasStorageChanges = false,
                    hasApiCompatibilityChanges = true,
                ),
            "1.2.0" to
                AppVersionInfo(
                    "1.2.0",
                    hasStorageChanges = false,
                    hasApiCompatibilityChanges = false,
                ),
            "1.3.0" to
                AppVersionInfo(
                    "1.3.0",
                    hasStorageChanges = false,
                    hasApiCompatibilityChanges = false,
                ),
            "2.0.0" to
                AppVersionInfo(
                    "2.0.0",
                    hasStorageChanges = true,
                    hasApiCompatibilityChanges = true,
                ),
        )

    private val checker = VersionCompatibilityChecker(versionInfos)

    @Test
    fun testNoApiCompatibilityChangesBetweenSameVersion() {
        assertFalse(checker.hasApiCompatibilityChangesBetween("1.0.0", "1.0.0"))
        assertFalse(checker.hasApiCompatibilityChangesBetween("2.0.0", "2.0.0"))
    }

    @Test
    fun testApiCompatibilityChangesBetweenDifferentVersions() {
        assertFalse(checker.hasApiCompatibilityChangesBetween("0.0.9", "1.0.0"))
        assertTrue(checker.hasApiCompatibilityChangesBetween("1.0.0", "1.1.0"))
        assertTrue(checker.hasApiCompatibilityChangesBetween("1.0.0", "1.2.0"))
        assertFalse(checker.hasApiCompatibilityChangesBetween("1.2.0", "1.3.0"))
        assertTrue(checker.hasApiCompatibilityChangesBetween("1.0.0", "2.0.0"))
    }

    @Test
    fun testApiCompatibilityChangesBetweenReversedVersions() {
        assertFalse(checker.hasApiCompatibilityChangesBetween("1.0.0", "0.0.9"))
        assertTrue(checker.hasApiCompatibilityChangesBetween("1.1.0", "1.0.0"))
        assertTrue(checker.hasApiCompatibilityChangesBetween("1.2.0", "1.0.0"))
        assertFalse(checker.hasApiCompatibilityChangesBetween("1.3.0", "1.2.0"))
        assertTrue(checker.hasApiCompatibilityChangesBetween("2.0.0", "1.0.0"))
    }
}
