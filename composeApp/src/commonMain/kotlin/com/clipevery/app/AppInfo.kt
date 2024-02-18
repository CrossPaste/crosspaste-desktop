package com.clipevery.app

import com.clipevery.utils.JsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val AppName: String = "Clipevery"

@Serializable
data class AppInfo(
    val appInstanceId: String,
    val appVersion: String,
    val userName: String
) {

    override fun toString(): String {
        return JsonUtils.JSON.encodeToString(this)
    }
}
