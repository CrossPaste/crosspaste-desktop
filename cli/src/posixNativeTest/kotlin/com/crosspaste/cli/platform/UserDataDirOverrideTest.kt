package com.crosspaste.cli.platform

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserDataDirOverrideTest {

    private val env = NativePlatformPathProvider.USER_DATA_DIR_ENV

    private fun withEnv(
        value: String?,
        block: () -> Unit,
    ) {
        if (value == null) {
            platform.posix.unsetenv(env)
        } else {
            platform.posix.setenv(env, value, 1)
        }
        try {
            block()
        } finally {
            platform.posix.unsetenv(env)
        }
    }

    @Test
    fun envOverrideReplacesThePlatformDefault() {
        withEnv("/tmp/crosspaste-dev-user") {
            assertEquals(
                "/tmp/crosspaste-dev-user".toPath(),
                createNativePlatformPathProvider().getDefaultUserDataPath(),
            )
        }
    }

    @Test
    fun blankOverrideIsIgnored() {
        withEnv("  ") {
            assertNotEquals(
                "  ".toPath(),
                createNativePlatformPathProvider().getDefaultUserDataPath(),
            )
        }
    }

    @Test
    fun withoutTheEnvThePlatformDefaultIsUsed() {
        withEnv(null) {
            val path = createNativePlatformPathProvider().getDefaultUserDataPath().toString()
            assertEquals(true, path.contains("CrossPaste") || path.contains(".crosspaste"))
        }
    }
}
