package com.crosspaste.utils

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppEnv.BETA
import com.crosspaste.app.AppEnv.DEVELOPMENT
import com.crosspaste.app.AppEnv.PRODUCTION
import com.crosspaste.app.AppEnv.TEST

expect fun getAppEnvUtils(): AppEnvUtils

interface AppEnvUtils {

    fun getCurrentAppEnv(): AppEnv

    fun isProduction(): Boolean {
        val appEnv = getCurrentAppEnv()
        return appEnv == PRODUCTION || appEnv == BETA
    }

    // use in mobile app
    fun isBeta(): Boolean = getCurrentAppEnv() == BETA

    fun isDevelopment(): Boolean = getCurrentAppEnv() == DEVELOPMENT

    fun isTest(): Boolean = getCurrentAppEnv() == TEST
}
