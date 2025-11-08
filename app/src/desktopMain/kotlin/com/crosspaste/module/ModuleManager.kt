package com.crosspaste.module

class ModuleManager(
    private val modules: Map<String, ServiceModule>,
) {

    fun getModuleById(moduleId: String): ServiceModule? = modules[moduleId]
}
