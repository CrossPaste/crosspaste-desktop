package com.clipevery.app

import com.clipevery.utils.getSystemProperty

enum class AppEnv {
    PRODUCTION,
    DEVELOPMENT,
    TEST,
    ;

    companion object {

        private val SYSTEM_PROPERTY = getSystemProperty()

        fun getAppEnv(): AppEnv {
            return SYSTEM_PROPERTY.getOption("appEnv")?.let {
                try {
                    AppEnv.valueOf(it)
                } catch (e: Throwable) {
                    PRODUCTION
                }
            } ?: PRODUCTION
        }

        fun isProduction(): Boolean {
            return getAppEnv() == PRODUCTION
        }

        fun isDevelopment(): Boolean {
            return getAppEnv() == DEVELOPMENT
        }

        fun isTest(): Boolean {
            return getAppEnv() == TEST
        }
    }
}
