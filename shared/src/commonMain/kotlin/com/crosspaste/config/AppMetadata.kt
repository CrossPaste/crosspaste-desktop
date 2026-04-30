package com.crosspaste.config

import kotlinx.serialization.Serializable

@Serializable
data class AppMetadata(
    val appInstanceId: String,
)
