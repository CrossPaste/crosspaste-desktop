package com.crosspaste.config

/**
 * Every key a platform's configuration consists of, addressable by name.
 */
class ConfigSchema(
    val keys: List<ConfigKey<*>>,
) {

    private val byName: Map<String, ConfigKey<*>> = keys.associateBy { it.name }

    init {
        require(byName.size == keys.size) {
            val duplicates = keys.groupBy { it.name }.filterValues { it.size > 1 }.keys
            "Duplicate config key names: $duplicates"
        }
    }

    /** @throws IllegalArgumentException when no key is registered under [name]. */
    fun key(name: String): ConfigKey<*> = byName[name] ?: throw IllegalArgumentException("Unknown config key: $name")

    fun findKey(name: String): ConfigKey<*>? = byName[name]
}
