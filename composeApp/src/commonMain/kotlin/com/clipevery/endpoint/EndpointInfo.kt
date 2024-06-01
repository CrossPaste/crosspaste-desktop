package com.clipevery.endpoint

import com.clipevery.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EndpointInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val hostList: List<String>,
    val port: Int,
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}
