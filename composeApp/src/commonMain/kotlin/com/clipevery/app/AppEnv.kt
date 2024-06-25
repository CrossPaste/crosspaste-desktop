package com.clipevery.app

import com.clipevery.utils.getSystemProperty

enum class AppEnv {
    PRODUCTION,
    DEVELOPMENT,
    TEST,
    ;

    fun isProduction(): Boolean {
        return this == PRODUCTION
    }

    fun isDevelopment(): Boolean {
        return this == DEVELOPMENT
    }

    fun isTest(): Boolean {
        return this == TEST
    }

    companion object {

        private val SYSTEM_PROPERTY = getSystemProperty()

        val CURRENT = getAppEnv()

        private fun getAppEnv(): AppEnv {
            return SYSTEM_PROPERTY.getOption("appEnv")?.let {
                try {
                    AppEnv.valueOf(it)
                } catch (e: Throwable) {
                    PRODUCTION
                }
            } ?: PRODUCTION
        }
    }
}
