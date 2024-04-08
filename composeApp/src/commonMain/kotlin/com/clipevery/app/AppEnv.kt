package com.clipevery.app

enum class AppEnv {
    PRODUCTION,
    DEVELOPMENT,
    TEST,
    ;

    companion object {
        fun getAppEnv(): AppEnv {
            return System.getProperty("appEnv")?.let {
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
