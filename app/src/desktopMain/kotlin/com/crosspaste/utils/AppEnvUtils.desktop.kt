package com.crosspaste.utils

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppEnv.PRODUCTION

actual fun getAppEnvUtils(): AppEnvUtils {
    return DesktopAppEnvUtils
}

object DesktopAppEnvUtils : AppEnvUtils {

    private val SYSTEM_PROPERTY = getSystemProperty()

    private val CURRENT_APP_ENV = getAppEnv()

    private fun getAppEnv(): AppEnv {
        return SYSTEM_PROPERTY.getOption("appEnv")?.let {
            runCatching {
                AppEnv.valueOf(it)
            }.getOrElse {
                PRODUCTION
            }
        } ?: PRODUCTION
    }

    override fun getCurrentAppEnv(): AppEnv {
        return CURRENT_APP_ENV
    }
}
