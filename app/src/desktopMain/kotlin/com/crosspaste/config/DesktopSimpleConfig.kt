package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.createPlatformLock
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class DesktopSimpleConfig(
    private val oneFilePersist: OneFilePersist,
) : SimpleConfig {

    private val jsonUtils = getJsonUtils()

    private val lock = createPlatformLock()

    private var jsonObject: JsonObject =
        oneFilePersist.readBytes()?.decodeToString()?.let {
            jsonUtils.JSON.parseToJsonElement(it).jsonObject
        } ?: JsonObject(mapOf())

    override fun getString(key: String): String? {
        return lock.withLock {
            jsonObject[key]?.jsonPrimitive?.content
        }
    }

    override fun getBoolean(key: String): Boolean? {
        return lock.withLock {
            jsonObject[key]?.jsonPrimitive?.boolean
        }
    }

    override fun getInt(key: String): Int? {
        return lock.withLock {
            jsonObject[key]?.jsonPrimitive?.int
        }
    }

    override fun getLong(key: String): Long? {
        return lock.withLock {
            jsonObject[key]?.jsonPrimitive?.long
        }
    }

    override fun getFloat(key: String): Float? {
        return lock.withLock {
            jsonObject[key]?.jsonPrimitive?.floatOrNull
        }
    }

    override fun getDouble(key: String): Double? {
        return lock.withLock {
            jsonObject[key]?.jsonPrimitive?.doubleOrNull
        }
    }

    private fun set(
        key: String,
        jsonElement: JsonElement,
    ) {
        lock.withLock {
            jsonObject =
                jsonObject.toMutableMap().apply {
                    this[key] = jsonElement
                }.let { JsonObject(it) }

            oneFilePersist.saveBytes(
                jsonUtils.JSON.encodeToString(jsonObject).encodeToByteArray(),
            )
        }
    }

    override fun setString(
        key: String,
        value: String,
    ) {
        set(key, JsonPrimitive(value))
    }

    override fun setBoolean(
        key: String,
        value: Boolean,
    ) {
        set(key, JsonPrimitive(value))
    }

    override fun setInt(
        key: String,
        value: Int,
    ) {
        set(key, JsonPrimitive(value))
    }

    override fun setLong(
        key: String,
        value: Long,
    ) {
        set(key, JsonPrimitive(value))
    }

    override fun setFloat(
        key: String,
        value: Float,
    ) {
        set(key, JsonPrimitive(value))
    }

    override fun setDouble(
        key: String,
        value: Double,
    ) {
        set(key, JsonPrimitive(value))
    }

    override fun remove(key: String) {
        lock.withLock {
            jsonObject =
                jsonObject.toMutableMap().apply {
                    this.remove(key)
                }.let { JsonObject(it) }

            oneFilePersist.saveBytes(
                jsonUtils.JSON.encodeToString(jsonObject).encodeToByteArray(),
            )
        }
    }

    override fun clear() {
        lock.withLock {
            jsonObject = JsonObject(mapOf())

            oneFilePersist.saveBytes(
                jsonUtils.JSON.encodeToString(jsonObject).encodeToByteArray(),
            )
        }
    }
}
