package com.clipevery.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val AppName: String = "Clipevery"

@Serializable
data class AppInfo(
    val appInstanceId: String,
    val appVersion: String,
    val userName: String,
) {

    override fun toString(): String {
        return Json.encodeToString(this)
    }
}
