package com.clipevery.app

import kotlin.test.Test
import kotlin.test.assertTrue

class AppEnvTest {

    @Test
    fun testGetAppEnv() {
        assertTrue { AppEnv.CURRENT == AppEnv.TEST }
    }
}
