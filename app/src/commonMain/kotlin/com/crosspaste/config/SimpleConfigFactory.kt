package com.crosspaste.config

interface SimpleConfigFactory {

    fun createConfig(configName: String): SimpleConfig
}
