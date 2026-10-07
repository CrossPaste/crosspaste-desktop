package com.crosspaste.cli.platform

import com.crosspaste.cli.withEnv
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserDataDirOverrideTest {
    private val env = NativePlatformPathProvider.USER_DATA_DIR_ENV

    @Test
    fun envOverrideReplacesThePlatformDefault() {
        withEnv(env, "/tmp/crosspaste-dev-user") {
            assertEquals(
                "/tmp/crosspaste-dev-user".toPath(),
                createNativePlatformPathProvider().getDefaultUserDataPath(),
            )
        }
    }

    @Test
    fun blankOverrideIsIgnored() {
        withEnv(env, "  ") {
            assertNotEquals(
                "  ".toPath(),
                createNativePlatformPathProvider().getDefaultUserDataPath(),
            )
        }
    }

    @Test
    fun withoutTheEnvThePlatformDefaultIsUsed() {
        withEnv(env, null) {
            val path = createNativePlatformPathProvider().getDefaultUserDataPath().toString()
            assertEquals(true, path.contains("CrossPaste") || path.contains(".crosspaste"))
        }
    }
}
