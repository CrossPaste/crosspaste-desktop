package com.crosspaste.config

interface ReadWriteConfig<T> {

    fun getValue(): T

    fun setValue(value: T)
}

class ReadWritePort(private val configManager: ConfigManager) : ReadWriteConfig<Int> {

    override fun getValue(): Int {
        return configManager.getCurrentConfig().port
    }

    override fun setValue(value: Int) {
        configManager.updateConfig("port", value)
    }
}
