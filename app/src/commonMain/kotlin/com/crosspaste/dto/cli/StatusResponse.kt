package com.crosspaste.dto.cli

import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val appVersion: String,
    val appInstanceId: String,
    val port: Int,
    val pasteboardListening: Boolean,
    val deviceCount: Int,
    val pasteCount: Long,
)
