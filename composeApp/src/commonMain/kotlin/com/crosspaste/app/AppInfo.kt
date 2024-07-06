package com.crosspaste.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val AppName: String = "CrossPaste"

@Serializable
data class AppInfo(
    val appInstanceId: String,
    val appVersion: String,
    val appRevision: String,
    val userName: String,
) {

    override fun toString(): String {
        return Json.encodeToString(this)
    }
}
