package com.crosspaste.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.unsetenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins that an isolated HOME cannot leak into later tests in the same
 * native process: the temp directory is real, and both a normal return and
 * a thrown block put the previous HOME back.
 */
@OptIn(ExperimentalForeignApi::class)
class IsolatedHomeTest {
    @Test
    fun isolatedHomeIsAnExistingTempDirectoryDifferentFromTheOriginal() {
        val original = getenv("HOME")?.toKString()
        withIsolatedHome {
            val home = assertNotNull(getenv("HOME")?.toKString())
            assertNotEquals(original, home)
            assertTrue(home.startsWith(FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString()))
            assertTrue(FileSystem.SYSTEM.metadata(home.toPath()).isDirectory)
        }
        assertEquals(original, getenv("HOME")?.toKString())
    }

    @Test
    fun homeIsRestoredWhenTheBlockThrows() {
        val original = getenv("HOME")?.toKString()
        assertFailsWith<IllegalStateException> {
            withIsolatedHome { throw IllegalStateException("boom") }
        }
        assertEquals(original, getenv("HOME")?.toKString())
    }

    @Test
    fun homeStaysUnsetWhenTheBlockThrowsAndHomeWasUnset() {
        val original = getenv("HOME")?.toKString()
        unsetenv("HOME")
        try {
            assertFailsWith<IllegalStateException> {
                withIsolatedHome { throw IllegalStateException("boom") }
            }
            assertEquals(null, getenv("HOME")?.toKString())
        } finally {
            if (original == null) {
                unsetenv("HOME")
            } else {
                setenv("HOME", original, 1)
            }
        }
    }

    @Test
    fun withEnvNullUnsetsAVariableThenRestoresIt() {
        val name = "CROSSPASTE_WITH_ENV_TEST"
        val original = getenv(name)?.toKString()
        setenv(name, "scratch-value", 1)
        try {
            withEnv(name, null) {
                assertEquals(null, getenv(name)?.toKString())
            }
            assertEquals("scratch-value", getenv(name)?.toKString())
        } finally {
            if (original == null) {
                unsetenv(name)
            } else {
                setenv(name, original, 1)
            }
        }
    }
}
