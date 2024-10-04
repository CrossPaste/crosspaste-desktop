package com.crosspaste.utils

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppEnv.DEVELOPMENT
import com.crosspaste.app.AppEnv.PRODUCTION
import com.crosspaste.app.AppEnv.TEST

expect fun getAppEnvUtils(): AppEnvUtils

interface AppEnvUtils {

    fun getCurrentAppEnv(): AppEnv

    fun isProduction(): Boolean {
        return getCurrentAppEnv() == PRODUCTION
    }

    fun isDevelopment(): Boolean {
        return getCurrentAppEnv() == DEVELOPMENT
    }

    fun isTest(): Boolean {
        return getCurrentAppEnv() == TEST
    }
}
