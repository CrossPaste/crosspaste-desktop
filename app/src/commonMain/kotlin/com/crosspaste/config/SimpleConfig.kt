package com.crosspaste.config

interface SimpleConfig {

    fun getString(key: String): String?

    fun getBoolean(key: String): Boolean?

    fun getInt(key: String): Int?

    fun getLong(key: String): Long?

    fun getFloat(key: String): Float?

    fun getDouble(key: String): Double?

    fun setString(
        key: String,
        value: String,
    )

    fun setBoolean(
        key: String,
        value: Boolean,
    )

    fun setInt(
        key: String,
        value: Int,
    )

    fun setLong(
        key: String,
        value: Long,
    )

    fun setFloat(
        key: String,
        value: Float,
    )

    fun setDouble(
        key: String,
        value: Double,
    )

    fun remove(key: String)

    fun clear()
}
