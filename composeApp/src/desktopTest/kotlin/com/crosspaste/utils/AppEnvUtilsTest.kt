package com.crosspaste.utils

import com.crosspaste.app.AppEnv
import kotlin.test.Test
import kotlin.test.assertTrue

class AppEnvUtilsTest {

    @Test
    fun testGetAppEnv() {
        assertTrue { getAppEnvUtils().getCurrentAppEnv() == AppEnv.TEST }
    }
}
