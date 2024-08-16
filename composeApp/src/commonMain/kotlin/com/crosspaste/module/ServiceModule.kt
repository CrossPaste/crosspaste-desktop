package com.crosspaste.module

interface ServiceModule {

    val serviceName: String

    val moduleNames: List<String>

    fun getModuleLoaderConfigs(): Map<String, ModuleLoaderConfig>
}
