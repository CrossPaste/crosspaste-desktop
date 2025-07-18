package com.crosspaste.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val AppName: String = "CrossPaste"

@Serializable
data class AppInfo(
    val appInstanceId: String,
    val appVersion: String,
    val appRevision: String,
    val userName: String,
) {

    fun displayVersion(): String = "$appVersion${if (appRevision == "Unknown") "" else " ($appRevision)"}"

    override fun toString(): String = Json.encodeToString(this)
}
