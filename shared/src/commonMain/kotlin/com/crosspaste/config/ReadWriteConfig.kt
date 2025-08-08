package com.crosspaste.config

interface ReadWriteConfig<T> {

    fun getValue(): T

    fun setValue(value: T)
}

class ReadWritePort(
    private val configManager: CommonConfigManager,
) : ReadWriteConfig<Int> {

    override fun getValue(): Int = configManager.getCurrentConfig().port

    override fun setValue(value: Int) {
        configManager.updateConfig("port", value)
    }
}
